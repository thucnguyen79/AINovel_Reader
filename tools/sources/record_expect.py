"""Records the reviewed output of RuleDumpTest as fixtures/<id>/expect.json (consumed by SiteFixturesTest).

Usage (from the repository root, after `.\\gradlew.bat :data:source:testDebugUnitTest --tests "*RuleDumpTest"`):
    python tools/sources/record_expect.py            # record every rule in the dump
    python tools/sources/record_expect.py truyenhoan # record only the listed ids
Review build/rule-dump.txt before recording: the file freezes whatever the parser currently extracts.
"""
import io
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DUMP = os.path.join(ROOT, "data", "source", "build", "rule-dump.txt")
FIXTURES = os.path.join(ROOT, "data", "source", "src", "test", "resources", "fixtures")


def parse_dump(path):
    sites, current = {}, None
    for line in io.open(path, encoding="utf-8"):
        line = line.rstrip("\n")
        if line.startswith("===== "):
            current = line[6:].strip()
            sites[current] = {}
        elif current and "=" in line:
            key, value = line.split("=", 1)
            sites[current][key] = value
    return sites


def main():
    only = set(sys.argv[1:])
    for site, values in parse_dump(DUMP).items():
        if only and site not in only:
            continue
        if any(k.endswith(".error") or k == "error" for k in values):
            print(f"{site}: skipped, dump contains errors: {[k for k in values if 'error' in k]}")
            continue
        target_dir = os.path.join(FIXTURES, site)
        if not os.path.isdir(target_dir):
            print(f"{site}: skipped, no fixture directory")
            continue
        with io.open(os.path.join(target_dir, "expect.json"), "w", encoding="utf-8", newline="\n") as f:
            json.dump(values, f, ensure_ascii=False, indent=1)
            f.write("\n")
        print(f"{site}: recorded {len(values)} keys")


if __name__ == "__main__":
    main()
