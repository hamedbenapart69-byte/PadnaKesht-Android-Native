#!/usr/bin/env python3
"""Turn instrumented-test results into GitHub annotations and a job summary.

Annotations are readable through the Checks API, so a failure can be diagnosed
without downloading logs or artifacts.
"""
import glob
import os
import re
import sys
import xml.etree.ElementTree as ET

MAX = 3000


def esc(text: str) -> str:
    return text.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def error(title: str, message: str) -> None:
    print(f"::error title={esc(title)}::{esc(message[:MAX])}")


def notice(title: str, message: str) -> None:
    print(f"::notice title={esc(title)}::{esc(message[:MAX])}")


def report_xml() -> bool:
    files = glob.glob("app/build/outputs/androidTest-results/**/*.xml", recursive=True)
    if not files:
        return False
    total = failed = 0
    for path in files:
        try:
            suite = ET.parse(path).getroot()
        except ET.ParseError:
            continue
        for case in suite.iter("testcase"):
            total += 1
            bad = case.find("failure")
            if bad is None:
                bad = case.find("error")
            if bad is not None:
                failed += 1
                body = (bad.get("message") or "") + "\n" + (bad.text or "")
                error(f"FAILED {case.get('classname')}.{case.get('name')}", body.strip())
    notice("Instrumented test totals", f"tests={total} failed={failed} xml_files={len(files)}")
    return True


def report_gradle_log(path: str = "gradle-connected.log") -> None:
    if not os.path.exists(path):
        notice("gradle-connected.log", "missing")
        return
    lines = open(path, encoding="utf-8", errors="replace").read().splitlines()
    compile_errors = [l for l in lines if re.match(r"^e: ", l)]
    if compile_errors:
        error("Kotlin compile errors", "\n".join(compile_errors[:25]))
    for i, line in enumerate(lines):
        if line.startswith("* What went wrong"):
            error("Gradle: What went wrong", "\n".join(lines[i + 1:i + 14]))
            break
    for i, line in enumerate(lines):
        if re.search(r"(FAILED|Process crashed|INSTRUMENTATION_FAILED|No tests found|Test run failed)", line):
            error("Gradle log hit", "\n".join(lines[max(0, i - 2):i + 10]))
            break
    tail = "\n".join(lines[-25:])
    notice("Gradle log tail", tail)


def report_logcat() -> None:
    path = "screenshots/emulator-logcat.txt"
    if not os.path.exists(path):
        return
    lines = open(path, encoding="utf-8", errors="replace").read().splitlines()
    for i, line in enumerate(lines):
        if "FATAL EXCEPTION" in line or "am_crash" in line:
            error("Logcat crash", "\n".join(lines[i:i + 18]))
            return
    tree = [l.split("SMOKE_TREE:", 1)[1].strip() for l in lines if "SMOKE_TREE" in l]
    if tree:
        error("Compose semantics tree at smoke failure", "\n".join(tree[:70]))
    hits = [l for l in lines if re.search(r"(padnakasht|TestRunner|AndroidRuntime).*(Exception|Error|fail)", l, re.I)]
    if hits:
        error("Logcat exceptions", "\n".join(hits[:12]))


if __name__ == "__main__":
    if len(sys.argv) == 3 and sys.argv[1] == "--gradle-log":
        report_gradle_log(sys.argv[2])
        sys.exit(0)
    found = report_xml()
    if not found:
        notice("androidTest XML", "no result files were produced")
    report_gradle_log()
    report_logcat()
    sys.exit(0)
