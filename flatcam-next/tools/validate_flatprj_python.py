"""Headless compatibility check against the real legacy decoders/object serializer.

Run using the FlatCAM Python environment:
  python tools/validate_flatprj_python.py --legacy-root .. path.FlatPrj
No GUI is started and no preferences are changed. This is not a visual UI test.
"""
import argparse
import ast
import json
import lzma
import sys
from pathlib import Path


def serialized_fields(source):
    """Read the actual object's literal ser_attrs additions, without building its UI."""
    fields = []
    for node in ast.walk(ast.parse(source.read_text(encoding="utf-8-sig"))):
        if isinstance(node, ast.AugAssign) and isinstance(node.target, ast.Attribute) and node.target.attr == "ser_attrs":
            try:
                fields.extend(ast.literal_eval(node.value))
            except (ValueError, TypeError):
                pass
    return fields


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--legacy-root", type=Path, required=True)
    parser.add_argument("--resaved", type=Path, help="Optional Python-reserialized artifact (without private FX root metadata).")
    parser.add_argument("project", type=Path)
    args = parser.parse_args()
    root = args.legacy_root.resolve()
    sys.path.insert(0, str(root))
    sys.path.insert(0, str(root / "tests"))
    import camlib
    from parser_baseline import install_stub_app
    from appObjects.FlatCAMObj import FlatCAMObj
    from appParsers.ParseGerber import Gerber
    from appParsers.ParseExcellon import Excellon
    from preprocessors.default import default
    from shapely.geometry.base import BaseGeometry

    app = install_stub_app()
    app.preprocessors = {"default": default()}
    raw = args.project.read_bytes()
    if raw.startswith(b"\xfd7zXZ"):
        raw = lzma.decompress(raw)
    project = json.loads(raw.decode("utf-8"), object_hook=camlib.dict2obj)
    assert 8.9 <= project["version"] < 9
    classes = {"gerber": (Gerber, "FlatCAMGerber.py"), "excellon": (Excellon, "FlatCAMExcellon.py"),
               "geometry": (camlib.Geometry, "FlatCAMGeometry.py"), "cncjob": (camlib.CNCjob, "FlatCAMCNCJob.py")}
    restored = []
    counts = {}
    def flat(value):
        if isinstance(value, (list, tuple)):
            return [leaf for item in value for leaf in flat(item)]
        return [] if value is None else [value]

    def same_data(expected, actual):
        if isinstance(expected, BaseGeometry):
            return isinstance(actual, BaseGeometry) and expected.equals_exact(actual, 1e-9)
        if hasattr(expected, "to_dict") and not isinstance(expected, dict):
            # Legacy value objects (ApertureMacro) have no equality of their own: compare what they serialize.
            return type(expected) is type(actual) and same_data(expected.to_dict(), actual.to_dict())
        if isinstance(expected, dict):
            return all(k in actual and same_data(v,actual[k]) for k,v in expected.items())
        if isinstance(expected, list):
            return len(expected) == len(actual) and all(same_data(a,b) for a,b in zip(expected,actual))
        return expected == actual
    for encoded in project["objs"]:
        kind = encoded["kind"]
        cls, source = classes[kind]
        obj = cls()
        obj.options = {}
        obj.ser_attrs = list(dict.fromkeys(obj.ser_attrs + serialized_fields(root / "appObjects" / source)))
        # Exactly the serializer used by MenuFileHandlers.open_project().
        FlatCAMObj.from_dict(obj, encoded)
        assert obj.options["name"] == encoded["options"]["name"]
        assert obj.units == encoded["units"]
        obj.bounds()  # AppObject.new_object() calculates this after restoring an object.
        if kind == "geometry":
            for tool in obj.tools.values():
                # Python itself stores either a list or one geometry here; both must be accepted.
                assert all(isinstance(g, BaseGeometry) for g in flat(tool["solid_geometry"]))
                assert float(tool["tooldia"]) > 0
        if kind == "excellon":
            for tool in obj.tools.values():
                assert len(tool["solid_geometry"]) == len(tool.get("drills", [])) + len(tool.get("slots", []))
                assert all(isinstance(g, BaseGeometry) for g in tool["solid_geometry"])
        if kind == "cncjob":
            assert obj.gcode == encoded["gcode"]
            assert all(isinstance(g["geom"], BaseGeometry) for g in obj.gcode_parsed)
            # Exercise the actual Python G-code parser too, not just JSON restoration.
            original_parsed = obj.gcode_parsed
            obj.gcode_parse()
            obj.gcode_parsed = original_parsed
            assert obj.gcode == encoded["gcode"]
        data = obj.to_dict()
        # Actual Python save serialization, followed by its actual load hook.
        again = json.loads(json.dumps(data, default=camlib.to_dict), object_hook=camlib.dict2obj)
        for key in encoded:
            if key in obj.ser_attrs:
                assert same_data(encoded[key],again[key]), (kind,key)
        assert again["options"]["name"] == obj.options["name"]
        if kind == "cncjob":
            assert again["gcode"] == encoded["gcode"]
        restored.append(again)
        counts[kind] = counts.get(kind, 0) + 1
    if args.resaved:
        payload = {"version": project["version"], "options": project["options"], "objs": restored}
        args.resaved.write_text(json.dumps(payload, default=camlib.to_dict), encoding="utf-8")
    print(json.dumps({"status": "PASS", "python": sys.version.split()[0], "objects": counts,
                      "validation": "real legacy dict2obj/from_dict/to_dict and CNC parser; headless, not visual"}))


if __name__ == "__main__":
    main()
