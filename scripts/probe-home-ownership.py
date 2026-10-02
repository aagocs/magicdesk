#!/usr/bin/env python3
"""UX-010a HOME ownership probe (issue #5) through the existing MCP connection.

Answers, without claiming the HOME role or changing any launcher preference:
  H1  Can a shell-UID launch with ACTIVITY_TYPE_HOME create a HOME task on an owned
      virtual display while another package holds ROLE_HOME?
  H2  Which component does Android itself start as HOME on a new decorated display:
      one inside the ROLE_HOME package, or the SECONDARY_HOME preference/fallback?
  P0/P5  Launcher role and preferences before and after are byte-for-byte identical.

Phone Home/Recents/notification behavior (P4) and a run with MagicDesk's
SECONDARY_HOME preference claimed (P3) stay manual; they are reported as such.
Every resource the probe creates is removed by exact identity, including on failure.
Raw evidence (component names, command output) stays in ignored private storage;
the printed summary only classifies components.
"""

import argparse
import importlib.util
import json
from pathlib import Path
import re
import sys
import time
import uuid

spec = importlib.util.spec_from_file_location("mcp_client", Path(__file__).with_name("mcp-client.py"))
mcp = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mcp)
PACKAGE = "io.github.mekhontsev.magicdesk"
HOME_ROLE = "android.app.role.HOME"
ACTIVITY_TYPE_HOME = 2
# The H1 probe needs an ordinary, non-self-finishing MagicDesk Activity.
PROBE_COMPONENT = PACKAGE + "/.StartActivity"


def probe(options):
    token_text = options.token_file.read_text(encoding="utf-8-sig").strip()
    match = re.search(r"Authorization: Bearer ([A-Za-z0-9_-]+)", token_text)
    client = mcp.Client(options.endpoint, match.group(1) if match else token_text)
    report = options.output / uuid.uuid4().hex
    report.mkdir(parents=True)
    sequence = 0
    results = {}

    def call(tool, args=None):
        nonlocal sequence
        result = client.call(tool, args or {})
        sequence += 1
        (report / f"{sequence:02}-{tool.replace('.', '-')}.json").write_text(
            json.dumps(result, indent=2, ensure_ascii=False), encoding="utf-8")
        return result

    state = call("get_state")
    if options.expected_build and state["app"]["buildId"] != options.expected_build:
        raise ValueError("Installed build differs from --expected-build; no probe actions were sent")
    if not state["readiness"]["selfTestReady"]:
        raise ValueError("Phone must be awake and unlocked before this probe")

    console = call("console.open")["sessionId"]
    owned = []
    probe_task = None
    lease = None
    cleanup_errors = []

    def shell(command, check=True):
        result = call("console.execute", {"sessionId": console, "command": command})
        if check and result["exitCode"] != 0:
            raise ValueError(f"Probe shell command failed ({result['exitCode']}): {command}")
        return result.get("output", "").strip()

    def launcher_state(user):
        query = f"-a android.intent.action.MAIN --user {user}"
        return {
            "roleHolders": shell(f"cmd role get-role-holders --user {user} {HOME_ROLE}"),
            "home": shell(f"cmd package resolve-activity --brief {query} -c android.intent.category.HOME"),
            "secondaryHome": shell(f"cmd package resolve-activity --brief {query} "
                                   "-c android.intent.category.SECONDARY_HOME"),
            "secondaryHandlers": shell(f"cmd package query-activities --brief {query} "
                                       "-c android.intent.category.SECONDARY_HOME"),
        }

    def classify(component, role_package, secondary):
        if not component:
            return "none"
        package = component.split("/")[0]
        if package == PACKAGE:
            return "magicdesk"
        if package == role_package:
            return "role-holder package"
        if secondary and component in secondary:
            return "secondary-home preference"
        return "other"

    def home_tasks(display_id):
        tasks = call("list_tasks", {"displayId": display_id, "limit": 200})["tasks"]
        return [task for task in tasks if task.get("home")]

    def wait_for_home(display_id, seconds):
        deadline = time.monotonic() + seconds
        while True:
            tasks = home_tasks(display_id)
            if tasks or time.monotonic() >= deadline:
                return tasks
            # No display-scoped task event exists in MCP; bounded polling of the same snapshot.
            # Absence at the deadline is reported as an observation, never assumed to be success.
            time.sleep(0.5)

    try:
        user = shell("am get-current-user")
        before = launcher_state(user)
        role_package = before["roleHolders"].splitlines()[0].strip() if before["roleHolders"] else ""
        if role_package == PACKAGE:
            raise ValueError("MagicDesk holds HOME; close every Desktop through production cleanup first")
        results["P0"] = {
            "roleHolder": "other package" if role_package else "none",
            "roleHolderExportsSecondaryHome": bool(role_package) and any(
                line.split("/")[0] == role_package for line in before["secondaryHandlers"].splitlines()),
            "secondaryHomeResolution": classify(before["secondaryHome"].splitlines()[-1]
                                                if before["secondaryHome"] else "", role_package, None),
            "forceDesktopModeOnExternalDisplays": shell(
                "settings get global force_desktop_mode_on_external_displays", check=False),
            "sdk": shell("getprop ro.build.version.sdk"),
        }
        lease = call("device.keep_awake", {"displayId": 0, "durationMillis": 180000})

        # H2: Android chooses and starts HOME itself on a decorated display.
        overlay = call("create_display", {"type": "overlay", "width": 1280, "height": 720, "densityDpi": 160})
        owned.append(("overlay", overlay))
        tasks = wait_for_home(overlay["id"], options.home_wait_seconds)
        results["H2"] = {
            "observed": bool(tasks),
            "homeComponent": classify(tasks[0].get("component", "") if tasks else "", role_package,
                                      before["secondaryHome"]),
            "note": "" if tasks else "Android started no HOME on the overlay display; system decorations "
                                     "may be disabled there (see P0 forceDesktopModeOnExternalDisplays)",
        }

        # H1: a shell-UID launch requesting ACTIVITY_TYPE_HOME on an owned virtual display.
        virtual = call("create_display", {"type": "virtual", "width": 1280, "height": 720, "densityDpi": 160})
        owned.append(("virtual", virtual))
        start = shell(f"am start -W --user {user} --display {virtual['id']} "
                      f"--activity-type {ACTIVITY_TYPE_HOME} -n {PROBE_COMPONENT}", check=False)
        tasks = wait_for_home(virtual["id"], 5)
        mine = [task for task in tasks if task.get("package") == PACKAGE]
        probe_task = mine[0]["taskId"] if mine else None
        if probe_task is None:
            others = call("list_tasks", {"displayId": virtual["id"], "package": PACKAGE})["tasks"]
            probe_task = others[0]["taskId"] if others else None
        results["H1"] = {
            "launchAccepted": "Error" not in start and "Exception" not in start,
            "homeTaskCreated": bool(mine),
            "note": "" if mine else "The launch did not produce a HOME-type MagicDesk task",
        }

        after = launcher_state(user)
        results["P5"] = {"launcherStateUnchanged": after == before}
        results["P3"] = "manual: requires claiming MagicDesk's SECONDARY_HOME preference; not changed by this probe"
        results["P4"] = "manual: phone Home, Recents, notification shade and a fullscreen app during H1"
    finally:
        if probe_task is not None:
            try:
                call("close_task", {"taskId": probe_task})
                if not call("wait_for_state", {"condition": "task_absent", "taskId": probe_task,
                                               "timeoutMillis": 5000})["matched"]:
                    raise ValueError("probe task did not close")
            except Exception as error:
                # Removing a display moves its tasks; never move a HOME-type probe task to the phone.
                cleanup_errors.append(f"probe task {probe_task}: {error}; its virtual display was kept")
                owned = [entry for entry in owned if entry[0] != "virtual"]
        for _, display in reversed(owned):
            try:
                call("remove_display", {"displayId": display["id"], "uniqueId": display["uniqueId"]})
                if not call("wait_for_state", {"condition": "display_absent", "displayId": display["id"],
                                               "timeoutMillis": 5000})["matched"]:
                    raise ValueError("display removal observation expired")
            except Exception as error:
                cleanup_errors.append(f"display {display['id']}: {error}")
        releases = [("console.close", {"sessionId": console})]
        if lease:
            releases.insert(0, ("device.release_awake", {"leaseId": lease["leaseId"]}))
        for tool, arguments in releases:
            try:
                call(tool, arguments)
            except Exception as error:
                cleanup_errors.append(f"{tool}: {error}")
        (report / "summary.json").write_text(json.dumps(results, indent=2), encoding="utf-8")
    print(json.dumps(results, indent=2))
    if cleanup_errors:
        raise ValueError("Owned cleanup incomplete: " + "; ".join(cleanup_errors))
    print(f"HOME ownership probe finished; owned cleanup confirmed. Private evidence: {report}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--endpoint", default="http://127.0.0.1:8765/mcp")
    parser.add_argument("--token-file", type=Path, required=True)
    parser.add_argument("--expected-build", help="Refuse to run against any other installed build")
    parser.add_argument("--home-wait-seconds", type=int, default=8,
                        help="How long to observe a framework-started HOME on the overlay display")
    parser.add_argument("--output", type=Path, default=Path("build/reports/home-ownership-probe"))
    try:
        probe(parser.parse_args())
    except (OSError, ValueError, KeyError, mcp.ToolError) as error:
        print(f"HOME ownership probe failed: {error}", file=sys.stderr)
        sys.exit(1)
