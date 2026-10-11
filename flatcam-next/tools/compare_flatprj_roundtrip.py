#!/usr/bin/env python3
"""What a FlatCAM Python project lost or changed after the FX opened and saved it.

Plain JSON comparison of two .FlatPrj files (XZ or not), value by value: no FlatCAM code is needed. The FX's own
metadata ("_fx_format" and "_java") is ignored. Strict comparison is symmetric: additions, removals, changes,
duplicate names and reordered objects fail unless explicitly expected. --expect uses kind.key (excellon.tools);
--added and --removed declare intentional object additions/deletions.
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


def clean(value):
    if isinstance(value, dict):
        return {key: clean(item) for key, item in value.items() if key not in FX_KEYS}
    if isinstance(value, list):
        return [clean(item) for item in value]
    return value


def differences(original, saved, prefix=""):
    found = []
    if isinstance(original, dict) and isinstance(saved, dict):
        for key in sorted(original.keys() | saved.keys()):
            where = prefix + key
            if key not in saved:
                found.append((where, "missing"))
            elif key not in original:
                found.append((where, "added"))
            else:
                found.extend(differences(original[key], saved[key], where + "."))
    elif original != saved:
        # Geometry arrays are compared whole, but nested metadata has already been removed by clean().
        found.append((prefix.rstrip("."), "changed"))
    return found


def expected_path(path, expected):
    return any(path == key or path.startswith(key + ".") for key in expected)


def compare(original, saved, expected=(), removed=(), added=()):
    original, saved = clean(original), clean(saved)
    removed, added = set(removed), set(added)
    report = {"root": [], "objects": {}, "missingObjects": [], "addedObjects": [], "orderKept": True}
    report["root"] = differences({k: v for k, v in original.items() if k != "objs"},
                                  {k: v for k, v in saved.items() if k != "objs"})
    old_names = [obj["options"]["name"] for obj in original["objs"]]
    new_names = [obj["options"]["name"] for obj in saved["objs"]]
    report["duplicateObjectNames"] = sorted({name for names in (old_names, new_names)
                                            for name in names if names.count(name) > 1})
    by_name = {obj["options"]["name"]: obj for obj in saved["objs"]}
    kept = [name for name in new_names if name in set(old_names) and name not in removed]
    report["orderKept"] = kept == [name for name in old_names if name not in removed and name in by_name]
    report["addedObjects"] = [name for name in new_names if name not in set(old_names)]
    report["invalidExpectations"] = sorted((removed - set(old_names)) | (added - set(new_names))
                                            | (removed & set(new_names)) | (added & set(old_names)))
    unexpected = sum(not expected_path("root." + where, expected) for where, _ in report["root"])
    unexpected += len(set(report["addedObjects"]) - added) + len(report["duplicateObjectNames"])
    unexpected += len(report["invalidExpectations"])
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
        found = differences(obj, by_name[name])
        if found:
            report["objects"][name] = {"kind": obj["kind"], "differences": found}
            unexpected += sum(not expected_path(obj["kind"] + "." + where, expected) for where, _ in found)
    report.update(objectsCompared=compared, topLevelAndOptionValuesCompared=values,
                  applicationOptions=len(original.get("options", {})), unexpectedDifferences=unexpected)
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("original", type=Path)
    parser.add_argument("saved", type=Path)
    parser.add_argument("--expect", default="", help="Comma-separated kind.key prefixes that are meant to differ")
    parser.add_argument("--removed", default="", help="Comma-separated names of objects deleted on purpose")
    parser.add_argument("--added", default="", help="Comma-separated names of objects added on purpose")
    parser.add_argument("--strict", action="store_true")
    args = parser.parse_args()
    original, saved = load(args.original), load(args.saved)
    expected = [item for item in args.expect.split(",") if item]
    removed = {item for item in args.removed.split(",") if item}

    report = compare(original, saved, expected, removed, [item for item in args.added.split(",") if item])
    print(json.dumps(report, indent=1))
    return 1 if args.strict and (report["unexpectedDifferences"] or not report["orderKept"]) else 0


if __name__ == "__main__":
    sys.exit(main())
