"""SkCine parse/diagnostics/reload tests against a running test server.

Usage: python run_parse_tests.py <server-dir> [rcon-port] [rcon-password]

The server must have SkCine + Skript installed and skcine-syntax-test.sk loaded.
Each negative script must fail to load with a specific, helpful error message.
"""

import os
import re
import shutil
import sys
import time

sys.path.insert(0, os.path.dirname(__file__))
import rcon  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
NEG = os.path.join(HERE, "scripts", "negative")

CASES = [
    ("neg-keyframe-outside", r"must be used inside a cine scene timeline/sequence"),
    ("neg-bad-id", r"Invalid cine scene id"),
    ("neg-wrong-type", r"(not a player|Can't understand)"),
    ("neg-missing-arg", r"Can't understand this condition/effect: add cine marker at 2 seconds"),
    ("neg-at-no-section", r"needs an indented section"),
    ("neg-skip-mode", r"Unknown skip mode 'sideways'"),
    ("neg-speed", r"Scene speed must be between 0 and 10"),
    ("neg-choice-outside", r"'cine choice' can only be used inside"),
    ("neg-playback-no-context", r"'cine viewers' needs 'of {_playback}' outside a cine scene"),
    ("neg-unknown-entry", r"Unexpected entry 'colour"),
    ("neg-sequence-outside", r"'play cine sequence' must be used inside"),
    ("neg-display-nothing", r"Say what to animate"),
]

results = []


def check(name, ok, detail=""):
    results.append((name, ok))
    print(("PASS " if ok else "FAIL ") + name + ((" — " + detail) if detail and not ok else ""))


def strip(s):
    return re.sub(r"§[0-9a-fk-orx]", "", re.sub(r"\x1b\[[0-9;]*m", "", s))


def main():
    server = sys.argv[1]
    port = int(sys.argv[2]) if len(sys.argv) > 2 else 25598
    password = sys.argv[3] if len(sys.argv) > 3 else "skcinetest"
    scripts = os.path.join(server, "plugins", "Skript", "scripts")
    log = os.path.join(server, "server.log")

    import io
    import contextlib

    def cmd(c):
        buf = io.StringIO()
        with contextlib.redirect_stdout(buf):
            rcon.run("127.0.0.1", port, password, [c])
        return strip(buf.getvalue())

    def install(name):
        shutil.copy(os.path.join(NEG, name + ".sk"), os.path.join(scripts, name + ".sk"))

    def remove(name):
        for n in (name + ".sk", "-" + name + ".sk"):
            p = os.path.join(scripts, n)
            if os.path.exists(p):
                os.remove(p)

    # 1. Negative parse cases.
    for name, pattern in CASES:
        install(name)
        out = cmd("sk reload " + name)
        check("parse-error " + name, re.search(pattern, out) is not None, out.strip().replace("\n", " | ")[:300])
        remove(name)
        cmd("sk reload scripts")

    # 2. Duplicate ids across scripts are rejected, naming the first script.
    install("neg-dup-a")
    cmd("sk reload neg-dup-a")
    install("neg-dup-b")
    out = cmd("sk reload neg-dup-b")
    check("duplicate-id rejected", "Duplicate cine scene id 'neg:duplicate'" in out and "neg-dup-a.sk" in out, out[:300])
    # Unloading the owner frees the id for the other script.
    remove("neg-dup-a")
    cmd("sk reload scripts")
    out = cmd("sk reload neg-dup-b")
    check("duplicate-id freed after owner unload", "Duplicate" not in out, out[:300])
    remove("neg-dup-b")
    cmd("sk reload scripts")

    # 3. Runtime diagnostics name the script and line, and are rate limited.
    size = os.path.getsize(log)
    install("rt-diagnostics")
    cmd("sk reload rt-diagnostics")
    cmd("sk reload rt-diagnostics")
    pattern = r"\[SkCine\] \[rt-diagnostics\.sk, line 2\] unknown easing 'wobble'"
    # Paper writes its log asynchronously: poll briefly instead of assuming a fixed delay.
    tail, hits = "", []
    for _ in range(16):
        time.sleep(0.5)
        with open(log, encoding="utf-8", errors="replace") as f:
            f.seek(size)
            tail = strip(f.read())
        hits = re.findall(pattern, tail)
        if hits and "SKCINE-NEG runtime-done" in tail and tail.count("SKCINE-NEG runtime-done") >= 2:
            break
    check("runtime error names script + line", len(hits) >= 1, tail[-400:])
    check("repeated runtime error is rate limited", len(hits) == 1, "occurrences=%d" % len(hits))
    remove("rt-diagnostics")
    cmd("sk reload scripts")

    # 4. Reload stress: definitions are replaced, never duplicated, and no errors appear.
    before = cmd("skcine info")
    m0 = re.search(r"Registered scenes: (\d+)", before)
    time.sleep(2)  # let output from earlier reloads reach the log before counting
    size = os.path.getsize(log)
    for _ in range(25):
        cmd("sk reload skcine-syntax-test")
    time.sleep(2)
    after = cmd("skcine info")
    m1 = re.search(r"Registered scenes: (\d+)", after)
    check("reload x25 keeps registry size stable", m0 and m1 and m0.group(1) == m1.group(1), "%s -> %s" % (m0 and m0.group(1), m1 and m1.group(1)))
    with open(log, encoding="utf-8", errors="replace") as f:
        f.seek(size)
        tail = strip(f.read())
    errs = [line for line in tail.splitlines() if "SEVERE" in line or "Exception" in line or "SKCINE-TEST FAIL" in line]
    check("reload x25 produces no errors", not errs, " | ".join(errs[:3]))
    passes = tail.count("SKCINE-TEST DONE load")
    check("reload x25 re-ran load assertions each time", passes == 25, "count=%d" % passes)
    health = cmd("skcine health")
    check("health ok after tests", "Healthy" in health or "error(s) logged" in health, health[:200])

    failed = [n for n, ok in results if not ok]
    print("\n%d/%d checks passed" % (len(results) - len(failed), len(results)))
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
