#!/usr/bin/env python3
"""Check independent Start paging through semantic MCP actions and clean up owned resources."""

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


def verify(options):
    token_text = options.token_file.read_text(encoding="utf-8-sig").strip()
    match = re.search(r"Authorization: Bearer ([A-Za-z0-9_-]+)", token_text)
    token = match.group(1) if match else token_text
    client = mcp.Client(options.endpoint, token)
    report = options.output / uuid.uuid4().hex
    report.mkdir(parents=True)
    started = time.monotonic()
    sequence = 0

    def call(tool, args=None):
        nonlocal sequence
        result = client.call(tool, args or {})
        sequence += 1
        (report / f"{sequence:02}-{tool.replace('.', '-')}.json").write_text(
            json.dumps(result, indent=2, ensure_ascii=False), encoding="utf-8")
        return result

    state = call("get_state")
    if state["app"]["buildId"] != options.expected_build:
        raise ValueError("Installed build differs from --expected-build; no test actions were sent")
    if not state["readiness"]["selfTestReady"]:
        raise ValueError("Phone must be awake and unlocked before this interactive check")
    display_id = options.display_id
    owned = None
    lease = None
    task_id = None
    task_closed = False
    cleanup_errors = []
    try:
        lease = call("device.keep_awake", {"displayId": 0, "durationMillis": 120000})
        if options.virtual:
            owned = call("create_display", {"type": "virtual", "width": 1280,
                                            "height": 720, "densityDpi": 160})
            display_id = owned["id"]
        call("launch_intent", {"placement": "display", "displayId": display_id,
                               "component": PACKAGE + "/.StartActivity",
                               "mode": "fullscreen", "instance": "new"})
        search = call("ui.wait", {"displayId": display_id, "selector": {
            "package": PACKAGE, "editable": True, "visible": True}, "timeoutMillis": 5000})
        if not search["matched"] or len(search["matches"]) != 1:
            raise ValueError("Expected one independent Start search field")
        search = search["matches"][0]
        task_id = search["taskId"]
        call("ui.perform", {"elementId": search["elementId"], "action": "set_text", "text": options.query})
        search = call("ui.wait", {"taskId": task_id, "selector": {
            "editable": True, "visible": True}, "timeoutMillis": 5000})["matches"][0]
        if not search["focused"]:
            call("ui.perform", {"elementId": search["elementId"], "action": "focus"})
        call("ui.wait", {"taskId": task_id, "selector": {
            "editable": True, "focused": True, "text": options.query}, "timeoutMillis": 5000})
        # Application enumeration is asynchronous, especially just after installation.
        ready = call("ui.wait", {"taskId": task_id, "selector": {
            "selected": True, "visible": True}, "timeoutMillis": 5000})
        if not ready["matched"]:
            raise ValueError("Search results did not become available")

        def selection():
            # Bounded fresh observations if UI events advance during snapshot traversal.
            for attempt in range(3):
                snapshot = call("ui.inspect", {"taskId": task_id, "maxNodes": 256})
                if snapshot["stable"]:
                    break
                call("ui.wait", {"taskId": task_id, "selector": {
                    "editable": True, "focused": True, "text": options.query}, "timeoutMillis": 5000})
            if not snapshot["complete"] or not snapshot["stable"]:
                raise ValueError("A complete stable UI snapshot is required")
            nodes = snapshot["nodes"]
            rows = [n for n in nodes if n.get("selected") and "click" in n.get("actions", [])]
            scrolls = [n for n in nodes if n["className"] == "android.widget.ScrollView"]
            focused = [n for n in nodes if n.get("editable") and n.get("focused")]
            if len(rows) != 1 or len(scrolls) != 1 or len(focused) != 1 or focused[0]["text"] != options.query:
                raise ValueError("Expected a selected result viewport and unchanged focused search")
            row, viewport = rows[0]["bounds"], scrolls[0]["bounds"]
            if not rows[0]["visible"] or not (viewport["top"] <= row["top"] < row["bottom"] <= viewport["bottom"]):
                raise ValueError("Selected result is not fully visible")
            by_id = {n["elementId"]: n for n in nodes}
            labels = []
            for node in nodes:
                if not node.get("text"):
                    continue
                parent = node
                while parent.get("parentId") in by_id:
                    parent = by_id[parent["parentId"]]
                    if parent["elementId"] == rows[0]["elementId"]:
                        labels.append(node["text"])
                        break
            if not labels:
                raise ValueError("Selected row identity is unavailable")
            return labels

        before = selection()
        call("send_key", {"displayId": display_id, "keyCode": "PAGE_DOWN"})
        if (selection() != before) != options.expect_paging:
            raise ValueError("Page Down does not match the expected baseline/changed behavior; use a query with enough results")
        call("send_key", {"displayId": display_id, "keyCode": "PAGE_UP"})
        if selection() != before:
            raise ValueError("Page Up did not return to the original selection")
        call("send_key", {"displayId": display_id, "keyCode": "ESCAPE"})
        if not call("wait_for_state", {"condition": "task_absent", "taskId": task_id, "timeoutMillis": 5000})["matched"]:
            raise ValueError("Escape did not close the exact Start task")
        task_closed = True
    finally:
        actions = []
        if task_id is not None and not task_closed:
            try:
                absent = call("wait_for_state", {"condition": "task_absent", "taskId": task_id, "timeoutMillis": 0})
                if not absent["matched"]:
                    focused = call("ui.wait", {"taskId": task_id, "selector": {
                        "editable": True, "focused": True}, "timeoutMillis": 0})
                    if focused["matched"]:
                        call("send_key", {"displayId": display_id, "keyCode": "ESCAPE"})
                    else:
                        call("close_task", {"taskId": task_id})
                    closed = call("wait_for_state", {"condition": "task_absent", "taskId": task_id, "timeoutMillis": 5000})
                    if not closed["matched"]:
                        raise ValueError("Start cleanup observation expired")
            except Exception as error:
                cleanup_errors.append("Start task: " + str(error))
        if owned:
            actions += [("remove_display", {"displayId": owned["id"], "uniqueId": owned["uniqueId"]}),
                        ("wait_for_state", {"condition": "display_absent", "displayId": owned["id"], "timeoutMillis": 5000})]
        if lease:
            actions += [("device.release_awake", {"leaseId": lease["leaseId"]})]
        for tool, arguments in actions:
            try:
                result = call(tool, arguments)
                if tool == "wait_for_state" and not result["matched"]:
                    raise ValueError("Cleanup observation expired")
            except Exception as error:
                cleanup_errors.append(tool + ": " + str(error))
        if cleanup_errors:
            raise ValueError("Owned cleanup incomplete: " + "; ".join(cleanup_errors))
    print(f"Start paging {'changed-build' if options.expect_paging else 'baseline'} check passed "
          f"on {'owned virtual display' if options.virtual else 'display ' + str(display_id)} "
          f"in {time.monotonic() - started:.2f}s; task/display/awake cleanup confirmed. Private evidence: {report}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--endpoint", default="http://127.0.0.1:8765/mcp")
    parser.add_argument("--token-file", type=Path, required=True)
    parser.add_argument("--expected-build", required=True)
    target = parser.add_mutually_exclusive_group(required=True)
    target.add_argument("--display-id", type=int)
    target.add_argument("--virtual", action="store_true")
    parser.add_argument("--query", default="a", help="A query with more results than the viewport")
    parser.add_argument("--expect-paging", action="store_true", help="Expect the changed build to move selection")
    parser.add_argument("--output", type=Path, default=Path("build/reports/start-device"))
    try:
        verify(parser.parse_args())
    except (OSError, ValueError, mcp.ToolError) as error:
        print(f"Start device check failed: {error}", file=sys.stderr)
        sys.exit(1)
