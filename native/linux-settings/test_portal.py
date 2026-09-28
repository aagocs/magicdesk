"""Exercise the packaged static helper against a test-only, installed Linux session bus."""
import contextlib
import json
import os
import pathlib
import queue
import select
import shutil
import socket
import subprocess
import sys
import threading
import unittest
import uuid

HELPER = pathlib.Path(os.environ.get("MAGICDESK_SETTINGS_HELPER",
                                    "build/linux-settings/libmagicdesk_linux_settings.so")).resolve()
TOKEN = "ab" * 32


@contextlib.contextmanager
def preferences(initial=1):
    name = "md-appearance-test-" + uuid.uuid4().hex
    server = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
    server.bind("\0" + name)
    server.listen(4)
    peers = queue.Queue()
    def accept():
        try:
            while True:
                peer, _ = server.accept()
                peer.settimeout(5)
                token = bytearray()
                while len(token) < 64:
                    data = peer.recv(64 - len(token))
                    if not data:
                        break
                    token.extend(data)
                if token != TOKEN.encode():
                    peer.close()
                    continue
                peer.sendall(bytes([initial]))
                peers.put(peer)
        except OSError:
            pass
    threading.Thread(target=accept, daemon=True).start()
    env = dict(os.environ, MAGICDESK_APPEARANCE_SOCKET=name,
               MAGICDESK_APPEARANCE_TOKEN=TOKEN, WAYLAND_DISPLAY="wayland-test")
    env.pop("DBUS_SESSION_BUS_ADDRESS", None)
    env.pop("GTK_USE_PORTAL", None)
    try:
        yield env, peers
    finally:
        server.close()
        while not peers.empty():
            peers.get_nowait().close()


CLIENT = r'''
import json, os, subprocess, sys
assert not any(k in os.environ for k in ["MAGICDESK_APPEARANCE_TOKEN", "MAGICDESK_APPEARANCE_SOCKET"])
print(json.dumps({"portal": os.environ.get("GTK_USE_PORTAL")}), flush=True)
for line in sys.stdin:
    args=json.loads(line)
    p=subprocess.run(["gdbus", "call", "--session", "--dest", "org.freedesktop.portal.Desktop",
        "--object-path", "/org/freedesktop/portal/desktop", "--timeout", "3", "--method", *args],
        text=True,capture_output=True,timeout=5)
    print(json.dumps({"code":p.returncode,"out":p.stdout,"err":p.stderr}),flush=True)
'''


class PortalTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not HELPER.is_file() or not shutil.which("gdbus") or not shutil.which("dbus-run-session"):
            raise RuntimeError("Build the helper and install gdbus/dbus-run-session for these tests")

    def read(self, process):
        # EVENT_WAIT: fixture response; timeout fails the test, never substitutes success.
        self.assertTrue(select.select([process.stdout], [], [], 8)[0], "fixture response timeout")
        return json.loads(process.stdout.readline())

    @contextlib.contextmanager
    def running(self, env):
        p = subprocess.Popen([str(HELPER), "--", sys.executable, "-u", "-c", CLIENT],
                             env=env, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                             stderr=subprocess.PIPE, text=True)
        try:
            yield p
        finally:
            p.stdin.close()
            try:
                p.wait(timeout=8)
            except subprocess.TimeoutExpired:
                p.kill()
                p.wait()
            errors = p.stderr.read()
            p.stdout.close()
            p.stderr.close()
            self.assertEqual(0, p.returncode, errors)

    def call(self, p, method, *args):
        p.stdin.write(json.dumps([method, *args]) + "\n")
        p.stdin.flush()
        return self.read(p)

    def test_settings_protocol_live_change_and_lifetime(self):
        with preferences() as (env, peers), self.running(env) as p:
            self.assertEqual({"portal": None}, self.read(p))
            peer = peers.get(timeout=5)
            try:
                one = self.call(p, "org.freedesktop.portal.Settings.ReadOne", "org.freedesktop.appearance", "color-scheme")
                self.assertEqual(0, one["code"], one)
                self.assertIn("<uint32 1>", one["out"])
                old = self.call(p, "org.freedesktop.portal.Settings.Read", "org.freedesktop.appearance", "color-scheme")
                self.assertIn("<<uint32 1>>", old["out"])
                all_settings = self.call(p, "org.freedesktop.portal.Settings.ReadAll", "[]")
                self.assertIn("Adwaita-dark", all_settings["out"])
                self.assertIn("text-scaling-factor", all_settings["out"])
                other = self.call(p, "org.freedesktop.portal.Settings.ReadAll", "['unknown.*']")
                self.assertNotIn("color-scheme", other["out"])
                error = self.call(p, "org.freedesktop.DBus.Properties.Get", "org.freedesktop.portal.FileChooser", "version")
                self.assertNotEqual(0, error["code"])
                self.assertIn("UnknownProperty", error["err"])
                peer.sendall(bytes([2]))
                updated = self.call(p, "org.freedesktop.portal.Settings.ReadAll", "[]")
                self.assertIn("<uint32 2>", updated["out"])
                self.assertNotIn("Adwaita-dark", updated["out"])
            finally:
                peer.close()

    def test_absent_bus_runner_falls_back_without_publishing_secrets(self):
        with preferences() as (env, _):
            env["PATH"] = "/unavailable"
            p = subprocess.run([str(HELPER), "--", sys.executable, "-c",
                                "import os;assert 'MAGICDESK_APPEARANCE_TOKEN' not in os.environ"],
                               env=env, capture_output=True, timeout=8)
            self.assertEqual(0, p.returncode, p.stderr)
            self.assertIn(b"no session bus", p.stderr)

    def test_application_portal_policy_is_never_overridden(self):
        for policy in ("0", "1"):
            with preferences() as (env, _):
                env["GTK_USE_PORTAL"] = policy
                with self.running(env) as p:
                    self.assertEqual({"portal": policy}, self.read(p))

    def test_existing_provider_is_not_replaced(self):
        with preferences(1) as (first, _), preferences(2) as (second, _):
            command = r'''
import json,os,subprocess,sys
env=json.loads(sys.argv[1]);env["DBUS_SESSION_BUS_ADDRESS"]=os.environ["DBUS_SESSION_BUS_ADDRESS"]
p=subprocess.run([sys.argv[2],"--","gdbus","call","--session","--dest","org.freedesktop.portal.Desktop",
 "--object-path","/org/freedesktop/portal/desktop","--timeout","3","--method",
 "org.freedesktop.portal.Settings.ReadOne","org.freedesktop.appearance","color-scheme"],
 env=env,capture_output=True,text=True,timeout=8)
assert p.returncode==0,(p.stdout,p.stderr)
assert "<uint32 1>" in p.stdout,p.stdout
'''
            result = subprocess.run([str(HELPER), "--", sys.executable, "-c", command, json.dumps(second), str(HELPER)],
                                    env=first, capture_output=True, text=True, timeout=12)
            self.assertEqual(0, result.returncode, result.stderr)

    def test_build_excludes_bus_daemon_and_cli_tools(self):
        build = HELPER.parent
        options = json.loads((build / "dbus-prefix/src/dbus-build/meson-info/intro-buildoptions.json").read_text())
        options = {item["name"]: item["value"] for item in options}
        self.assertFalse(options["message_bus"])
        self.assertFalse(options["tools"])
        targets = json.loads((build / "dbus-prefix/src/dbus-build/meson-info/intro-targets.json").read_text())
        self.assertNotIn("dbus-daemon", [item["name"] for item in targets])
        self.assertFalse(any("/test/" in item["defined_in"].replace("\\", "/") for item in targets))
        self.assertFalse((build / "prefix/bin").exists())


if __name__ == "__main__":
    unittest.main()
