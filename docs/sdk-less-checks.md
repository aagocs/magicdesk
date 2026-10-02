# SDK-less compile and unit-test check

For agents and contributors whose environment cannot install the Android SDK
(for example when `dl.google.com` is unreachable) but can reach Maven Central.
`scripts/verify-sdkless.py` type-checks the app and its runtime modules and runs
the JVM unit tests, so a cross-file change gets feedback in minutes instead of a
full CI build. It complements the focused `scripts/verify-host.py` suites.

It is **not** a substitute for CI or device checks: there is no Lint, resource
or manifest processing, native build or APK, and Android behavior is not
exercised. Run `verifyDevelopment` (or repository CI) and the boundary-specific
device gates in `AGENTS.md` before a change is complete.

## Usage

Requires Python 3 and JDK 17 or newer (`JAVA_HOME` or `--java-home`).

```sh
# First run: download pinned artifacts (about 230 MB) into ignored build storage.
python scripts/verify-sdkless.py --compile-only --download-deps

# Record the failures present on unchanged code, then compare after editing.
python scripts/verify-sdkless.py --baseline-out build/host-tests/sdkless-baseline.txt
python scripts/verify-sdkless.py --baseline build/host-tests/sdkless-baseline.txt

# Focused classes while editing; details for one class.
python scripts/verify-sdkless.py --baseline build/host-tests/sdkless-baseline.txt \
  --tests DesktopHomeRoleLeaseTest DesktopSessionTransitionCoordinatorTest
python scripts/verify-sdkless.py --verbose --tests DesktopHomeRoleLeaseTest
```

With `--baseline`, the script exits non-zero only for failures absent from the
baseline, and compares only the classes that ran. Observed timings in a cloud
sandbox: type-check about 16 s; full suite (3,322 tests) about 2 min 45 s; one
or two classes about 25 s including the type-check.

## Why a baseline is required

Robolectric's `android-all` contains real framework implementations, whereas
Gradle unit tests compile against the SDK's stub `android.jar`. On unchanged
`main`, 38 of 3,322 tests fail here only because real framework code calls
native methods (for example `SystemProperties.native_get`). Record the baseline
on the exact base commit you are changing; never treat "same failures as
before" as proof that those tests pass in CI.

## How it works

- **Framework and libraries** come from Maven Central, each pinned by SHA-256:
  `org.robolectric:android-all` for Android 17 (matching `compileSdk = 37`),
  the Shizuku `api`, `provider`, `aidl` and `shared` 13.1.5 AARs, and the unit
  test libraries declared in `app/build.gradle`. JUnit and Hamcrest reuse the
  `verify-host.py` pins. Throttling and server errors are retried.
- **Generated compile-only stubs** replace build outputs: AIDL interfaces from
  every module's `src/main/aidl` (methods, constants, `Stub.asInterface`,
  `Default`), `R` from `app/src/main/res`, `BuildConfig` for each namespace, and
  the single `androidx.annotation.RequiresApi` the sources use.
- **Fresh compilation** in a temporary directory on every run, as in
  `verify-host.py`; stale classes can never turn a failed edit into a pass.
  Tests run from `app/`, because source-guard tests read `src/main/java`
  relatively; `org.json` precedes the framework on the classpath, as in Gradle.

Pinned versions must follow `app/build.gradle` and `compileSdk`. When either
changes, update the paths and checksums together and record a new baseline.
