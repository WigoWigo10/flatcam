#!/usr/bin/env python3
"""What a FlatCAM Python project lost or changed after the FX opened and saved it.

Plain JSON comparison of two .FlatPrj files (XZ or not), value by value: no FlatCAM code is needed. The FX's own
metadata ("_fx_format" and "_java") is ignored. Exit code 1 with --strict when anything of the original is missing
or different outside the keys given with --expect (written as kind.key, e.g. excellon.tools).
"""
import argparse
import json
import lzma
import sys
from pathlib import Path

FX_KEYS = {"_fx_format", "_java"}


def load(path):
    raw = path.read_bytes()
    if raw.startswith(b"\xfd7zXZ"):
        raw = lzma.decompress(raw)
    return json.loads(raw.decode("utf-8-sig"))


def differences(original, saved, prefix, depth, found):
    """Keys of `original` that `saved` lacks or holds differently; dicts are followed `depth` levels down."""
    for key, value in original.items():
        if key in FX_KEYS:
            continue
        where = prefix + key
        if key not in saved:
            found.append((where, "missing"))
        elif value != saved[key]:
            if depth > 0 and isinstance(value, dict) and isinstance(saved[key], dict):
                differences(value, saved[key], where + ".", depth - 1, found)
            else:
                found.append((where, "changed"))
    return found


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("original", type=Path)
    parser.add_argument("saved", type=Path)
    parser.add_argument("--expect", default="", help="Comma-separated kind.key prefixes that are meant to differ")
    parser.add_argument("--removed", default="", help="Comma-separated names of objects deleted on purpose")
    parser.add_argument("--strict", action="store_true")
    args = parser.parse_args()
    original, saved = load(args.original), load(args.saved)
    expected = [item for item in args.expect.split(",") if item]
    removed = {item for item in args.removed.split(",") if item}

    report = {"root": [], "objects": {}, "missingObjects": [], "addedObjects": [], "orderKept": True}
    root_original = {k: v for k, v in original.items() if k != "objs"}
    root_saved = {k: v for k, v in saved.items() if k != "objs"}
    report["root"] = differences(root_original, root_saved, "", 1, [])
    by_name = {obj["options"]["name"]: obj for obj in saved["objs"]}
    names = [obj["options"]["name"] for obj in original["objs"] if obj["options"]["name"] not in removed]
    kept = [name for name in (obj["options"]["name"] for obj in saved["objs"]) if name in set(names)]
    report["orderKept"] = kept == [name for name in names if name in by_name]
    report["addedObjects"] = [name for name in by_name if name not in {o["options"]["name"] for o in original["objs"]}]
    unexpected = len(report["root"])
    compared = values = 0
    for obj in original["objs"]:
        name = obj["options"]["name"]
        if name in removed:
            continue
        if name not in by_name:
            report["missingObjects"].append(name)
            unexpected += 1
            continue
        compared += 1
        values += len(obj) + len(obj.get("options", {}))
        found = differences(obj, by_name[name], "", 3, [])
        if found:
            report["objects"][name] = {"kind": obj["kind"], "differences": found}
            for where, _ in found:
                if not any((obj["kind"] + "." + where).startswith(prefix) for prefix in expected):
                    unexpected += 1
    report.update(objectsCompared=compared, topLevelAndOptionValuesCompared=values,
                  applicationOptions=len(original.get("options", {})), unexpectedDifferences=unexpected)
    print(json.dumps(report, indent=1))
    return 1 if args.strict and (unexpected or not report["orderKept"]) else 0


if __name__ == "__main__":
    sys.exit(main())
