#!/usr/bin/env python3
"""Run focused production-body Java fixtures without the Android/native build."""

import argparse
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.request

REPO = Path(__file__).resolve().parents[1]
PACKAGE = "io.github.mekhontsev.magicdesk"
JAVA_PATH = Path(*PACKAGE.split("."))
SUITES = {
    "start": {
        "production": ["StartSearchSelection"],
        "tests": ["StartSearchSelectionTest", "StartSearchNavigationTest",
                  "StartDestinationTest", "ApplicationCatalogTest"],
    },
    "input": {
        "production": ["InputFocusCommitAwaiter", "InputRoutingLease",
                       "FrameworkInputRoutingSnapshot"],
        "tests": ["InputFocusCommitAwaiterTest", "InputRoutingLeaseTest"],
    },
    "parsers": {
        "production": ["SystemUiDesktopRepositoryParser", "WmShellTransitionStateParser",
                       "TaskLocalInsetsSourceParser", "TaskStackParser",
                       "PackageNameValidator"],
        "tests": ["SystemUiDesktopRepositoryParserTest", "WmShellTransitionStateParserTest",
                  "TaskLocalInsetsSourceParserTest", "TaskStackParserTest"],
    },
}
SUITES["taskbar"] = {
    "production": ["TaskbarPins"],
    "tests": ["TaskbarPinsTest"],
}
DEPENDENCIES = {
    "junit": ("junit/junit/4.13.2", "junit-4.13.2.jar",
              "8e495b634469d64fb8acfa3495a065cbacc8a0fff55ce1e31007be4c16dc57d3"),
    "hamcrest": ("org/hamcrest/hamcrest-core/1.3", "hamcrest-core-1.3.jar",
                 "66fdef91e9739348df7a096aa384a5685f4e875584cce89386a7a47251c4d8e9"),
}


def dependency(name, explicit, download):
    coordinate, filename, expected = DEPENDENCIES[name]
    local = REPO / "build/host-tests/dependencies" / filename
    if explicit:
        candidates = [Path(explicit)]
    else:
        gradle = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle"))
        group, artifact, version = coordinate.rsplit("/", 2)
        cached = gradle / "caches/modules-2/files-2.1" / group.replace("/", ".") / artifact / version
        candidates = [local, *sorted(cached.glob("*/" + filename))]
    for candidate in candidates:
        if candidate.is_file():
            if hashlib.sha256(candidate.read_bytes()).hexdigest() != expected:
                raise ValueError(f"Unexpected {name} checksum: {candidate}")
            return candidate.resolve()
    if explicit or not download:
        raise ValueError(f"Missing {filename}; pass --{name}-jar or use --download-deps once")
    data = urllib.request.urlopen(
        f"https://repo.maven.apache.org/maven2/{coordinate}/{filename}", timeout=30).read()
    if hashlib.sha256(data).hexdigest() != expected:
        raise ValueError(f"Downloaded {name} checksum does not match")
    local.parent.mkdir(parents=True, exist_ok=True)
    local.write_bytes(data)
    return local.resolve()


def java_tools(java_home):
    suffix = ".exe" if os.name == "nt" else ""
    if java_home:
        java = Path(java_home) / "bin" / ("java" + suffix)
        javac = Path(java_home) / "bin" / ("javac" + suffix)
    else:
        compiler = shutil.which("javac")
        if not compiler:
            raise ValueError("JDK 17+ required; set JAVA_HOME or pass --java-home")
        javac = Path(compiler).resolve()
        java = javac.with_name("java" + suffix)
    if not java.is_file() or not javac.is_file():
        raise ValueError("Both java and javac must exist in the selected JDK")
    return str(java), str(javac)


def run(options):
    started = time.monotonic()
    java, javac = java_tools(options.java_home)
    jars = [dependency("junit", options.junit_jar, options.download_deps),
            dependency("hamcrest", options.hamcrest_jar, options.download_deps)]
    suite = SUITES[options.suite]
    sources = [REPO / "app/src/testSupport/java" / JAVA_PATH / "RuntimeSourceFixture.java"]
    sources += [REPO / "app/src/main/java" / JAVA_PATH / (name + ".java")
                for name in suite["production"]]
    sources += [REPO / "app/src/test/java" / JAVA_PATH / (name + ".java") for name in suite["tests"]]
    classpath = os.pathsep.join(map(str, jars))
    # Always compile fresh: stale class files must never turn a failed edit into a pass.
    with tempfile.TemporaryDirectory(prefix="magicdesk-host-") as output:
        compile_result = subprocess.run([javac, "--release", "17", "-encoding", "UTF-8",
                                         "-cp", classpath, "-d", output, *map(str, sources)], cwd=REPO)
        if compile_result.returncode:
            return compile_result.returncode
        result = subprocess.run([java, "-cp", output + os.pathsep + classpath,
                                 "org.junit.runner.JUnitCore",
                                 *[PACKAGE + "." + name for name in suite["tests"]]], cwd=REPO / "app")
    print(f"Focused {options.suite} host checks: {time.monotonic() - started:.2f}s. "
          "Android build and device coverage are separate.", flush=True)
    return result.returncode


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--suite", choices=SUITES, required=True)
    parser.add_argument("--java-home", default=os.environ.get("JAVA_HOME"))
    parser.add_argument("--junit-jar", default=os.environ.get("MAGICDESK_JUNIT_JAR"))
    parser.add_argument("--hamcrest-jar", default=os.environ.get("MAGICDESK_HAMCREST_JAR"))
    parser.add_argument("--download-deps", action="store_true",
                        help="Download missing pinned/checksummed test JARs to ignored build storage")
    args = parser.parse_args()
    try:
        sys.exit(run(args))
    except (OSError, ValueError) as error:
        print(f"Host verification failed: {error}", file=sys.stderr)
        sys.exit(1)
