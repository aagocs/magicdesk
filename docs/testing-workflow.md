# Faster desktop development checks

Use this workflow with [CONTRIBUTING](../CONTRIBUTING.md#verification) and
[AGENTS.md](../AGENTS.md). It shortens feedback between edits while retaining
the full verification gate for a final runtime candidate.

## 1. Run the owning host fixtures while editing

For Start selection, paging and destination changes:

```sh
python scripts/verify-host.py --suite start --download-deps
python scripts/verify-host.py --suite start
```

Requires Python 3 and JDK 17 or newer; select a JDK with `JAVA_HOME` or
`--java-home PATH`. No Android SDK, NDK or Gradle configuration is needed.
The first command explicitly permits downloading two pinned, SHA-256-checked
JUnit/Hamcrest JARs into ignored `build/host-tests/dependencies`. Subsequent
runs work offline. Existing Gradle dependency caches can also supply those JARs;
explicit paths are accepted with `--junit-jar` and `--hamcrest-jar`.

The runner compiles fresh classes in temporary storage, extracts the existing
production-body fixtures and propagates compilation/test failures. It does not
reuse stale test classes. The Start suite includes selection, navigation,
destination and application-catalog tests: **24 tests in 2.64 seconds** on the
a Windows/JDK 17 test environment, or 3.26 seconds including initial JAR
downloads. A new paging regression failed before the production fix and passed
after it. These are observed timings, not performance guarantees.

[Desktop host checks](../.github/workflows/desktop-host-checks.yml) runs this
small suite separately for relevant pull requests. The full CI workflow also
runs it immediately after Java setup, before native/Android work. Other changes
need their own owning fixtures; passing the Start suite says nothing about
unrelated task, graphics or framework behavior. With an Android toolchain, use
filtered Gradle unit tests for boundaries not covered by this runner.

Without an Android SDK, the [SDK-less compile check](sdk-less-checks.md) type-checks
the whole app and runs its JVM unit tests against Maven Central inputs.

## 2. Reproduce on an identified device and build

Establish a complete current-main compatibility baseline and exact reproduction
as required by AGENTS.md. Retain its private evidence. Within the same unchanged
build/device/firmware/adapter/privilege environment, reuse that evidence instead
of repeating wireless pairing, authorization, discovery and setup after every
small edit. Refresh the baseline when any of those identities changes.

The semantic Start paging smoke check uses the existing MCP client and credentials:

```sh
python scripts/verify-start-device.py --endpoint http://127.0.0.1:8765/mcp \
  --token-file /private/path/connection.txt --expected-build EXACT_BUILD_ID \
  --display-id 0
python scripts/verify-start-device.py --endpoint http://127.0.0.1:8765/mcp \
  --token-file /private/path/connection.txt --expected-build EXACT_BUILD_ID \
  --virtual
```

These commands expect the unchanged baseline (Page Down does not move selection).
Add `--expect-paging` for the changed build. On PowerShell use one line or its
native continuation syntax. An authorized forwarded local endpoint is supported;
do not enable network MCP merely to use this check. `--query` must match more
results than the viewport. Device/authorization setup is a prerequisite, not
something the script silently creates.

The script rejects the wrong build or a locked/unready phone before test actions.
It opens independent Start, checks selection identity, fully visible row geometry
and retained search focus, sends Page Down/Up, and verifies Escape via global
`task_absent`. It creates only its own optional 1280x720/160-dpi virtual display,
then removes that exact display and releases its exact awake lease, including
on test failure. It does not start Desktop, change HOME, claim input or restart
Shizuku. JSON evidence contains application/UI data and belongs in ignored private
storage (`build/reports/start-device` by default), never a public PR attachment.

The script passed baseline and changed-build checks on phone and owned virtual
displays, including cleanup. Changed-build checks took about 4s and 14s respectively,
including owned resources. Intentional expectation failure also confirms cleanup.
Cold launches await selected results after asynchronous application enumeration.
Device timing varies with Android transitions; retain event/state waits rather than replacing
them with arbitrary sleeps. A stable snapshot may need bounded fresh reads as UI
events advance. `selected` propagates to child views: identify the actionable
result row, rather than counting every selected node. Unknown/absent UI windows
are not proof of task closure; use the exact task identity and global observation.

## 3. Build and verify one final runtime candidate

Run `verifyDevelopment` plus the boundary-specific checks required by AGENTS.md.
If the local Android/native toolchain is unavailable, use full repository CI.
Check whether CI already exists for the exact commit before dispatching another
run. Do not rebuild the APK for every documentation edit or rerun a successful
full pipeline without a new runtime change, failure or unresolved concern.
Record the tested runtime commit separately from later documentation commits.

Standard Gradle build caching is enabled, and CI retains its existing dependency
cache. Task outputs are eligible for reuse only through Gradle's input keys;
do not claim every native Exec task is cacheable or bypass verification. See
[Gradle's build-cache contract](https://docs.gradle.org/current/userguide/build_cache.html).
The full-build speedup has not yet been measured. Full checks remain necessary;
the measured improvement is quick feedback before spending time on them.

Close an active Desktop through production cleanup before installing. Verify APK
integrity and signature. Reuse the same development signing certificate and
update in place to preserve settings, grants and MCP credentials. Do not repeat
uninstall/data reset as routine setup; a signature mismatch needs explicit
approval. Reconnect and verify the installed build ID before changed-build checks.

Independent Start checks cover its shared widget on phone and virtual displays.
They do not establish managed Desktop, physical PC input, wired monitor, OEM HOME
ownership, or gaming coverage. Mark unavailable targets pending. Follow the
[UX acceptance matrix](ux-work-plan.md#acceptance-matrix) for changes to those
boundaries; never reduce assertions to make unavailable hardware look verified.
