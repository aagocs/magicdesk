# SDK-less compile and unit-test check

For agents whose sandbox cannot download the Android SDK (for example when
`dl.google.com` is blocked) but can reach Maven Central. It type-checks the
whole app plus runtime modules and runs the JVM unit tests locally, so a
cross-file refactor gets feedback in minutes instead of a full CI build.
Tracked in [#14](https://github.com/aagocs/magicdesk/issues/14), which owns
turning it into a repository script. Use it alongside [fast testing](testing-workflow.md).

**Status:** used manually for [#6](https://github.com/aagocs/magicdesk/issues/6).
Not a substitute for CI: no Lint, no native build, no resource/manifest
processing, no APK.

## Inputs (all from Maven Central)

| Purpose | Artifact |
| --- | --- |
| Android framework classes for `compileSdk = 37` | `org.robolectric:android-all:17-robolectric-15733970` |
| Shizuku | `dev.rikka.shizuku:{api,provider,aidl,shared}:13.1.5` (`classes.jar` from each AAR) |
| Unit-test dependencies | `junit:junit:4.13.2`, `org.hamcrest:hamcrest-core:1.3`, `org.json:json:20240303`, `com.google.jimfs:jimfs:1.3.2`, `com.google.guava:guava`, `com.google.guava:failureaccess` |

`androidx.annotation` is only used for `@RequiresApi`; stub it (Google Maven
redirects to the blocked host).

## Generated stubs

A short Python generator produces, into a temporary source directory:

- **AIDL interfaces** from `*/src/main/aidl`: an interface extending
  `IInterface` with each method `throws RemoteException` (strip `oneway`,
  `in/out/inout`, annotations), plus `DESCRIPTOR`, `Stub.asInterface` and a
  `Default` implementation. Add imports for AIDL built-ins (`IBinder`,
  `ParcelFileDescriptor`, `List`, `Map`). Skip `parcelable` declarations; the
  Java classes exist.
- **`R`** for the app namespace from `app/src/main/res`: file names per resource
  directory, `<item>`/typed entries from `values*`, `@+id/` references, and
  `styleable` arrays with index fields. Values only need to be unique ints.
- **`BuildConfig`** for the app and runtime-module namespaces, with the fields
  the sources use (`APPLICATION_ID`, `DEBUG`, `VERSION_CODE`, `VERSION_NAME`,
  `SOURCE_ID`, `PLATFORM_OVERRIDE`, `FRAMEWORK_OVERRIDE`).

## Compile and run

1. `javac --release 17 -proc:none` over the generated stubs plus `src/main/java`
   of `app` (and `app/src/debug/java`), `terminal-emulator`, `hosted-runtime`,
   `x11-runtime`, `wayland-runtime` and `hidden-api-stubs`, with the framework
   and Shizuku JARs on the classpath. About 15 seconds for roughly 1,000 files.
2. Compile `app/src/test/java` and `app/src/testSupport/java` against that
   output plus the test JARs.
3. Run JUnit **from the `app` directory** (source-guard tests read
   `src/main/java/...` relatively). Put `org.json` before the framework JAR.

## Known differences from Gradle unit tests

Robolectric's `android-all` contains real framework implementations, whereas
Gradle unit tests compile against stub `android.jar`. About 38 of ~3,300 tests
fail locally only because real framework code calls native methods (for
example `SystemProperties.native_get`). Record the failure list on unchanged
code first and compare against it; CI remains the authority.
