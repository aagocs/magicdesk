#!/usr/bin/env python3
"""Type-check the app and run its JVM unit tests without the Android SDK.

Android framework classes come from Robolectric's android-all on Maven Central;
AIDL interfaces, R and BuildConfig are generated as compile-only stubs. This is
fast feedback for agents without an SDK, not a substitute for Gradle CI: there is
no Lint, resource/manifest processing, native build or APK.
"""

import argparse
import hashlib
import importlib.util
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

REPO = Path(__file__).resolve().parents[1]
PACKAGE = "io.github.mekhontsev.magicdesk"
STORE = REPO / "build/host-tests/dependencies"
MAVEN = "https://repo.maven.apache.org/maven2/"
# Source sets compiled together, matching the debug unit-test classpath.
MAIN_SOURCES = ["app/src/main/java", "app/src/debug/java", "terminal-emulator/src/main/java",
                "hosted-runtime/src/main/java", "x11-runtime/src/main/java",
                "wayland-runtime/src/main/java", "hidden-api-stubs/src/main/java"]
AIDL_MODULES = ["app", "hosted-runtime", "x11-runtime", "wayland-runtime"]
TEST_SOURCES = ["app/src/test/java", "app/src/testSupport/java"]
BUILD_CONFIG_PACKAGES = [PACKAGE, PACKAGE + ".hosted", PACKAGE + ".wayland", PACKAGE + ".x11"]
# path, sha256; framework classes match compileSdk 37 (Android 17).
FRAMEWORK = [
    ("org/robolectric/android-all/17-robolectric-15733970/android-all-17-robolectric-15733970.jar",
     "f6a41ad548bb45cccd3b1d4774cb50d57826dd319b6e5accd6b6269876e12d71"),
]
AARS = [
    ("dev/rikka/shizuku/api/13.1.5/api-13.1.5.aar",
     "4def9bde498ef8626614c2fc5db9af4749c86f16f6c33e3f5658d35e70bab59b"),
    ("dev/rikka/shizuku/provider/13.1.5/provider-13.1.5.aar",
     "b0f18cd9812464ec171c53cac93a819fe411718a3965c311f01eb4de265381b3"),
    ("dev/rikka/shizuku/aidl/13.1.5/aidl-13.1.5.aar",
     "33fe7191cdd69fcb66d649264f3b0c47acb2f3d6343afc05b98dbbff6f221963"),
    ("dev/rikka/shizuku/shared/13.1.5/shared-13.1.5.aar",
     "4659642c9339be0a26e9c65bb8648f7ad6d8f4a465f557993ccbc78802381635"),
]
# org.json precedes the framework so tests use the declared library, as in Gradle.
TEST_LIBRARIES = [
    ("org/json/json/20240303/json-20240303.jar",
     "3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed"),
    ("com/google/jimfs/jimfs/1.3.2/jimfs-1.3.2.jar",
     "30f4c0d36508d794b0107286eae7a43e9ad8ad593148002acac941196f619c59"),
    ("com/google/guava/guava/33.4.0-jre/guava-33.4.0-jre.jar",
     "b918c98a7e44dbe94ebd9fe3e40cddaadb5a93e6a78eb6008b42df237241e538"),
    ("com/google/guava/failureaccess/1.0.2/failureaccess-1.0.2.jar",
     "8a8f81cf9b359e3f6dfa691a1e776985c061ef2f223c9b2c80753e1b458e8064"),
]


def load_verify_host():
    spec = importlib.util.spec_from_file_location("verify_host", REPO / "scripts/verify-host.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def fetch(url, attempts=4):
    for attempt in range(attempts):
        try:
            return urllib.request.urlopen(url, timeout=300).read()
        except urllib.error.HTTPError as error:
            # Maven Central rate-limits bursts; retry only throttling and server errors.
            if attempt + 1 == attempts or (error.code != 429 and error.code < 500):
                raise
        time.sleep(2 ** (attempt + 1))


def artifact(path, expected, download):
    local = STORE / Path(path).name
    if not local.is_file():
        if not download:
            raise ValueError(f"Missing {local.name}; use --download-deps once")
        print(f"Downloading {local.name}", flush=True)
        data = fetch(MAVEN + path)
        if hashlib.sha256(data).hexdigest() != expected:
            raise ValueError(f"Downloaded {local.name} checksum does not match")
        local.parent.mkdir(parents=True, exist_ok=True)
        local.write_bytes(data)
    elif hashlib.sha256(local.read_bytes()).hexdigest() != expected:
        raise ValueError(f"Unexpected {local.name} checksum: {local}")
    if local.suffix != ".aar":
        return local
    classes = local.with_suffix(".classes.jar")
    if not classes.is_file():
        with zipfile.ZipFile(local) as aar:
            classes.write_bytes(aar.read("classes.jar"))
    return classes


def write_java(root, package, name, body):
    path = root / Path(*package.split(".")) / (name + ".java")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(f"package {package};\n{body}\n", encoding="utf-8")


def strip_comments(text):
    return re.sub(r"//[^\n]*", "", re.sub(r"/\*.*?\*/", "", text, flags=re.S))


DEFAULT_RETURNS = {"void": "", "boolean": "return false;", "long": "return 0L;", "int": "return 0;",
                   "float": "return 0f;", "double": "return 0d;", "byte": "return 0;",
                   "char": "return 0;", "short": "return 0;"}
# Types AIDL resolves without an import.
AIDL_BUILTINS = ["android.os.IBinder", "android.os.ParcelFileDescriptor", "java.util.List", "java.util.Map"]


def aidl_stub(root, path):
    text = strip_comments(path.read_text(encoding="utf-8"))
    package = re.search(r"package\s+([\w.]+)\s*;", text).group(1)
    interface = re.search(r"(?:oneway\s+)?interface\s+(\w+)\s*\{(.*)\}", text, re.S)
    if not interface:
        return  # A parcelable declaration names an existing Java class.
    name, body = interface.group(1), re.sub(r"@\w+(\([^)]*\))?", "", interface.group(2))
    imports = re.findall(r"import\s+([\w.]+)\s*;", text)
    simple_imports = {value.rsplit(".", 1)[1] for value in imports}
    imports += [value for value in AIDL_BUILTINS
                if value.rsplit(".", 1)[1] not in simple_imports | {name}]
    methods, constants = [], []
    for declaration in (" ".join(part.split()) for part in body.split(";")):
        if not declaration:
            continue
        if declaration.startswith("const "):
            constants.append(f"    {declaration[len('const '):]};")
            continue
        declaration = re.sub(r"^oneway\s+", "", declaration)
        declaration = re.sub(r"\b(in|out|inout)\s+", "", declaration)
        methods.append(re.sub(r"\s*=\s*\d+\s*$", "", declaration))
    signatures = "".join(f"    {method} throws android.os.RemoteException;\n" for method in methods)
    defaults = "".join(
        f"        @Override public {method} {{ "
        f"{DEFAULT_RETURNS.get(method.split('(')[0].rsplit(' ', 1)[0].strip(), 'return null;')} }}\n"
        for method in methods)
    write_java(root, package, name, "".join(f"import {value};\n" for value in imports) + f"""
public interface {name} extends android.os.IInterface {{
    String DESCRIPTOR = "{package}.{name}";
{chr(10).join(constants)}
{signatures}
    abstract class Stub extends android.os.Binder implements {name} {{
        public static {name} asInterface(android.os.IBinder obj) {{ return null; }}
        @Override public android.os.IBinder asBinder() {{ return this; }}
    }}
    class Default implements {name} {{
        @Override public android.os.IBinder asBinder() {{ return null; }}
{defaults}    }}
}}""")


def resource_class(root):
    fields, styleables = {}, {}

    def add(kind, name):
        fields.setdefault(kind, set()).add(re.sub(r"[.\-:]", "_", name))

    for directory in sorted((REPO / "app/src/main/res").iterdir()):
        kind = directory.name.split("-")[0]
        for path in sorted(directory.iterdir()):
            if kind != "values":
                add(kind, path.name.split(".")[0])
                if path.suffix == ".xml":
                    for identifier in re.findall(r"@\+id/([\w.]+)", path.read_text(errors="ignore")):
                        add("id", identifier)
                continue
            for element in ET.parse(path).getroot():
                tag, name = element.tag, element.get("name")
                if not name:
                    continue
                if tag == "item":
                    tag = element.get("type") or "item"
                tag = {"string-array": "array", "integer-array": "array",
                       "declare-styleable": "styleable"}.get(tag, tag)
                if tag != "styleable":
                    add(tag, name)
                    continue
                attributes = [child.get("name") for child in element if child.tag == "attr"]
                styleables[name] = attributes
                for attribute in attributes:
                    if not attribute.startswith("android:"):
                        add("attr", attribute)
    next_id = iter(range(0x7f000001, 0x7fffffff))
    classes = [f"    public static final class {kind} {{\n" + "".join(
        f"        public static final int {name} = {next(next_id)};\n" for name in sorted(names)) + "    }"
        for kind, names in sorted(fields.items())]
    styleable = []
    for name, attributes in sorted(styleables.items()):
        styleable.append(f"        public static final int[] {name} = new int[{len(attributes)}];")
        styleable += [f"        public static final int {name}_{re.sub(r'[.:]', '_', attribute)} = {index};"
                      for index, attribute in enumerate(attributes)]
    classes.append("    public static final class styleable {\n" + "\n".join(styleable) + "\n    }")
    write_java(root, PACKAGE, "R", "public final class R {\n" + "\n".join(classes) + "\n}")


def generate_stubs(root):
    for module in AIDL_MODULES:
        for path in sorted((REPO / module / "src/main/aidl").rglob("*.aidl")):
            aidl_stub(root, path)
    resource_class(root)
    for package in BUILD_CONFIG_PACKAGES:
        write_java(root, package, "BuildConfig", f"""public final class BuildConfig {{
    public static final boolean DEBUG = Boolean.parseBoolean("true");
    public static final String APPLICATION_ID = "{PACKAGE}";
    public static final String BUILD_TYPE = "debug";
    public static final int VERSION_CODE = 1;
    public static final String VERSION_NAME = "0";
    public static final String SOURCE_ID = "";
    public static final String PLATFORM_OVERRIDE = "";
    public static final String FRAMEWORK_OVERRIDE = "";
}}""")
    # Only @RequiresApi is used; Google Maven is not required for one annotation.
    write_java(root, "androidx.annotation", "RequiresApi", """import java.lang.annotation.*;
@Retention(RetentionPolicy.CLASS) public @interface RequiresApi { int value() default 1; int api() default 1; }""")


def java_files(*roots):
    return [str(path) for root in roots if Path(root).is_dir() for path in sorted(Path(root).rglob("*.java"))]


def compile_sources(javac, classpath, output, sources):
    argfile = Path(output).with_suffix(".sources")
    # Quoted javac argument files treat backslashes as escapes; Windows accepts '/'.
    argfile.write_text("\n".join(f'"{Path(source).as_posix()}"' for source in sources),
                       encoding="utf-8")
    result = subprocess.run([javac, "-J-Xmx3g", "-nowarn", "-proc:none", "--release", "17",
                             "-encoding", "UTF-8", "-Xmaxerrs", "200", "-cp", classpath,
                             "-d", output, "@" + str(argfile)], cwd=REPO)
    return result.returncode


def test_classes(selected):
    root = REPO / "app/src/test/java"
    if selected:
        # Accept fully qualified names or names relative to the app package.
        return [name if (root / Path(*name.split("."))).with_suffix(".java").is_file()
                else f"{PACKAGE}.{name}" for name in selected]
    return sorted(str(path.relative_to(root).with_suffix("")).replace(os.sep, ".")
                  for path in root.rglob("*Test.java"))


def failures(output):
    return sorted(set(re.findall(r"^\d+\) (\S+\([\w.$]+\))$", output, re.M)))


def run(options):
    started = time.monotonic()
    host = load_verify_host()
    java, javac = host.java_tools(options.java_home)
    junit = [host.dependency("junit", None, options.download_deps),
             host.dependency("hamcrest", None, options.download_deps)]
    framework = [artifact(path, digest, options.download_deps) for path, digest in FRAMEWORK + AARS]
    libraries = [artifact(path, digest, options.download_deps) for path, digest in TEST_LIBRARIES]
    with tempfile.TemporaryDirectory(prefix="magicdesk-sdkless-") as work:
        work = Path(work)
        stubs, main, tests = work / "stubs", work / "main", work / "tests"
        for directory in (stubs, main, tests):
            directory.mkdir()
        generate_stubs(stubs)
        main_classpath = os.pathsep.join(map(str, framework))
        # Fresh output every run: stale classes must never turn a failed edit into a pass.
        if compile_sources(javac, main_classpath, main,
                           java_files(stubs, *(REPO / source for source in MAIN_SOURCES))):
            return 1
        print(f"Main sources type-check: {time.monotonic() - started:.1f}s", flush=True)
        if options.compile_only:
            return 0
        test_classpath = os.pathsep.join([str(main), *map(str, libraries + framework + junit)])
        if compile_sources(javac, test_classpath, tests,
                           java_files(*(REPO / source for source in TEST_SOURCES))):
            return 1
        result = subprocess.run([java, "-cp", str(tests) + os.pathsep + test_classpath,
                                 "org.junit.runner.JUnitCore", *test_classes(options.tests)],
                                cwd=REPO / "app", capture_output=True, text=True)
    output = result.stdout + result.stderr
    found = failures(output)
    summary = re.findall(r"^(OK \(\d+ tests?\)|Tests run: \d+,\s+Failures: \d+)$", output, re.M)
    print(output if options.verbose else "\n".join(summary), flush=True)
    print(f"SDK-less checks: {time.monotonic() - started:.1f}s. "
          "No Lint, resources, native build or device coverage.", flush=True)
    if options.baseline_out:
        Path(options.baseline_out).write_text("".join(f"{name}\n" for name in found), encoding="utf-8")
        print(f"Recorded {len(found)} failures as baseline", flush=True)
        return 0
    if options.baseline:
        selected = set(test_classes(options.tests))
        # Compare only classes that ran; a filtered run says nothing about the rest.
        known = {name for name in Path(options.baseline).read_text(encoding="utf-8").split()
                 if name[name.index("(") + 1:-1] in selected}
        added, fixed = sorted(set(found) - known), sorted(known - set(found))
        for name in fixed:
            print(f"no longer failing: {name}")
        for name in added:
            print(f"NEW FAILURE: {name}")
        if added and not options.verbose:
            print("Re-run with --verbose --tests <class> for failure details.")
        return 1 if added else 0
    for name in found:
        print(f"failed: {name}")
    return result.returncode


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--tests", nargs="*", default=[],
                        help="Test classes (simple or fully qualified); default: every *Test")
    parser.add_argument("--compile-only", action="store_true", help="Only type-check main sources")
    parser.add_argument("--baseline-out", help="Record current failures (run on unchanged code)")
    parser.add_argument("--baseline", help="Fail only on failures absent from this baseline")
    parser.add_argument("--verbose", action="store_true", help="Print complete JUnit output")
    parser.add_argument("--java-home", default=os.environ.get("JAVA_HOME"))
    parser.add_argument("--download-deps", action="store_true",
                        help="Download missing pinned/checksummed artifacts to ignored build storage")
    args = parser.parse_args()
    try:
        sys.exit(run(args))
    except (OSError, ValueError) as error:
        print(f"SDK-less verification failed: {error}", file=sys.stderr)
        sys.exit(1)
