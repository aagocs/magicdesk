from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


VERIFY = Path(__file__).resolve().parents[1] / "verify-development-version.py"


class DevelopmentVersionTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.repo = Path(self.directory.name)
        self.git("init", "--quiet")

    def git(self, *arguments):
        return subprocess.run(["git", "-C", str(self.repo), *arguments], check=True,
                              capture_output=True, text=True)

    def properties(self, version="1.13.1", code="215"):
        (self.repo / "gradle.properties").write_text(
            f"magicDeskVersionName={version}\nmagicDeskVersionCode={code}\n", encoding="utf-8")

    def release(self, version="1.13", code="214", legacy=False):
        if legacy:
            (self.repo / "app").mkdir()
            (self.repo / "app/build.gradle").write_text(f"versionCode {code}\n", encoding="utf-8")
        else:
            self.properties(version, code)
        self.git("add", ".")
        self.git("-c", "user.name=MagicDesk test", "-c", "user.email=test@example.com",
                 "-c", "commit.gpgsign=false", "commit", "--quiet", "-m", "Release fixture")
        self.git("tag", f"v{version}")

    def verify(self):
        return subprocess.run([sys.executable, str(VERIFY), "--repo", str(self.repo)],
                              capture_output=True, text=True)

    def test_tagless_fork_accepts_valid_first_development_version(self):
        self.properties()
        result = self.verify()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("no release tags yet", result.stdout)

    def test_tagless_fork_still_rejects_invalid_properties(self):
        for version, code in [("invalid", "215"), ("1.13.1", "0"), ("1.13.1", "-1"),
                              ("1.13.1", "2147483648"), ("1.13.1", "not-a-number")]:
            with self.subTest(version=version, code=code):
                self.properties(version, code)
                self.assertNotEqual(0, self.verify().returncode)

    def test_tagged_repository_accepts_newer_name_and_code(self):
        self.release()
        self.properties()
        result = self.verify()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("follows 1.13 (214)", result.stdout)

    def test_tagged_repository_rejects_equal_or_older_names(self):
        self.release()
        for version in ["1.13", "1.13.0", "1.12.9"]:
            with self.subTest(version=version):
                self.properties(version)
                result = self.verify()
                self.assertNotEqual(0, result.returncode)
                self.assertIn("must be newer", result.stderr)

    def test_tagged_repository_rejects_equal_or_older_codes(self):
        self.release()
        for code in ["214", "213"]:
            with self.subTest(code=code):
                self.properties(code=code)
                result = self.verify()
                self.assertNotEqual(0, result.returncode)
                self.assertIn("must be greater", result.stderr)

    def test_legacy_release_reads_code_from_app_build_file(self):
        self.release("1.12", "200", legacy=True)
        self.properties()
        result = self.verify()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("follows 1.12 (200)", result.stdout)

    def test_tagged_repository_does_not_silently_ignore_a_bad_release_code(self):
        self.release(code="invalid")
        self.properties()
        result = self.verify()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Invalid versionCode", result.stderr)


if __name__ == "__main__":
    unittest.main()
