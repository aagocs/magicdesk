#!/usr/bin/env python3
"""Validate development versions, including a fork's first build without release tags."""

import argparse
from pathlib import Path
import re
import subprocess
import sys


def git(repo, *arguments):
    return subprocess.run(["git", "-C", str(repo), *arguments], check=True,
                          capture_output=True, text=True).stdout.strip()


def property_value(text, name):
    match = re.search(r"^" + re.escape(name) + r"=([^\r\n]+)$", text, re.MULTILINE)
    if not match:
        raise ValueError(f"Missing {name}")
    return match.group(1).strip()


def version(value):
    if not re.fullmatch(r"[0-9]+\.[0-9]+(?:\.[0-9]+)?", value):
        raise ValueError(f"Invalid version: {value}")
    parts = tuple(int(part) for part in value.split("."))
    return parts + (0,) * (3 - len(parts))


def version_code(value):
    if not re.fullmatch(r"[1-9][0-9]{0,9}", value) or int(value) > 2147483647:
        raise ValueError(f"Invalid versionCode: {value}")
    return int(value)


def verify(repo):
    properties = (repo / "gradle.properties").read_text(encoding="utf-8")
    current = property_value(properties, "magicDeskVersionName")
    current_version = version(current)
    current_code = version_code(property_value(properties, "magicDeskVersionCode"))
    tags = git(repo, "tag", "--list", "v[0-9]*", "--sort=-v:refname").splitlines()
    if not tags:
        print(f"Development {current} ({current_code}) is valid; this repository has no release tags yet.")
        return
    latest_tag = tags[0]
    released = latest_tag[1:]
    released_version = version(released)
    try:
        released_properties = git(repo, "show", f"{latest_tag}:gradle.properties")
    except subprocess.CalledProcessError:
        released_properties = ""
    if "magicDeskVersionCode=" in released_properties:
        released_code = version_code(property_value(released_properties, "magicDeskVersionCode"))
    else:
        legacy = git(repo, "show", f"{latest_tag}:app/build.gradle")
        match = re.search(r"^\s*versionCode\s+(\S+)", legacy, re.MULTILINE)
        if not match:
            raise ValueError(f"Missing versionCode in {latest_tag}")
        released_code = version_code(match.group(1))
    if current_version <= released_version:
        raise ValueError(f"Development version {current} must be newer than {released}")
    if current_code <= released_code:
        raise ValueError(f"Development versionCode {current_code} must be greater than {released_code}")
    print(f"Development {current} ({current_code}) follows {released} ({released_code}).")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[1])
    arguments = parser.parse_args()
    try:
        verify(arguments.repo)
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        print(f"::error::{error}", file=sys.stderr)
        sys.exit(1)
