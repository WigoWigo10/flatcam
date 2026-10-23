#!/usr/bin/env python3
"""Open a .FlatPrj in the REAL FlatCAM Python application and save it again, without showing a window.

This is the application itself (app_Main.App, its object collection, its open_project/save_project), not only its
serializers. It runs off-screen (Qt "offscreen" platform, --headless=1) and isolated: the settings folder and the Qt
settings are redirected to a temporary directory, so the user's FlatCAM preferences and recent-file lists are not
read or written.

  python tools/open_in_python_app.py --legacy-root .. project.FlatPrj --resave out.FlatPrj --report report.json
"""
import argparse
import json
import lzma
import math
import os
import sys
import tempfile
import traceback
from pathlib import Path
from collections import Counter


def application_errors(messages):
    return [text for text in messages if "[ERROR" in text]


def read_project(path):
    raw = path.read_bytes()
    if raw.startswith(b"\xfd7zXZ"):
        raw = lzma.decompress(raw)
    root = json.loads(raw.decode("utf-8-sig"))
    if not isinstance(root, dict) or not isinstance(root.get("objs"), list) or "version" not in root:
        raise RuntimeError("The application did not write a valid project")
    return root


def object_inventory(objects):
    return Counter((obj.kind, obj.options["name"]) for obj in objects)


def save_and_validate(fc, target, messages):
    """Save to a fresh staging file, validate it, then atomically publish. An old target is not proof of success."""
    target = Path(target)
    target.parent.mkdir(parents=True, exist_ok=True)
    fd, name = tempfile.mkstemp(prefix=".flatcam-python-validation-", suffix=".FlatPrj", dir=target.parent)
    os.close(fd)
    stage = Path(name)
    try:
        fc.f_handlers.save_project(str(stage), silent=True)
        errors = application_errors(messages)  # includes errors emitted DURING saving, not only loading
        if errors:
            raise RuntimeError("The Python application reported errors: " + "; ".join(errors))
        root = read_project(stage)  # empty/partial/old unrelated output cannot pass
        saved = Counter((obj["kind"], obj["options"]["name"]) for obj in root["objs"])
        if saved != object_inventory(fc.collection.get_list()):
            raise RuntimeError("The saved object's inventory differs from the open project")
        stage.replace(target)
        return target.name
    finally:
        stage.unlink(missing_ok=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--legacy-root", type=Path, required=True)
    parser.add_argument("project", type=Path)
    parser.add_argument("--resave", type=Path, help="Where the Python application saves the project it opened")
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--timeout", type=float, default=300.0)
    parser.add_argument("--initial-units", choices=("MM", "IN"),
                        help="Explicit isolated app unit preference before opening; legacy set_ui overwrites object units from defaults")
    parser.add_argument("--new-empty-geometry", action="store_true",
                        help="Control run: also create a blank Geometry with the application's own command")
    args = parser.parse_args()
    root = args.legacy_root.resolve()
    project = args.project.resolve()
    # Resolve every path now: the application needs its own folder as the working directory.
    args.report = args.report.resolve()
    if args.resave:
        args.resave = args.resave.resolve()
        if args.resave == project:
            parser.error("--resave must be a copy, not the source project")
    if args.report in {project, args.resave}:
        parser.error("--report must not overwrite a project")
    report = {"status": "ERROR", "project": project.name}

    os.environ["QT_QPA_PLATFORM"] = "offscreen"
    isolated = Path(tempfile.mkdtemp(prefix="flatcam-python-app-"))
    os.chdir(root)
    sys.path.insert(0, str(root))
    sys.argv = [str(root / "FlatCAM.py"), "--headless=1"]

    # Isolation: the application asks the Windows shell for %APPDATA%; give it the temporary folder instead.
    # A stand-in for the pywin32 shell module does it, whether or not pywin32 is installed.
    import types
    shell = types.SimpleNamespace(SHGetFolderPath=lambda *a, **k: str(isolated))
    shellcon = types.SimpleNamespace(CSIDL_APPDATA=26)
    package = types.ModuleType("win32comext")
    module = types.ModuleType("win32comext.shell")
    module.shell, module.shellcon = shell, shellcon
    package.shell = module
    sys.modules["win32comext"] = package
    sys.modules["win32comext.shell"] = module
    os.environ["HOME"] = str(isolated)
    from PyQt5 import QtCore, QtWidgets
    QtCore.QSettings.setDefaultFormat(QtCore.QSettings.IniFormat)
    QtCore.QSettings.setPath(QtCore.QSettings.IniFormat, QtCore.QSettings.UserScope, str(isolated))
    QtCore.QSettings.setPath(QtCore.QSettings.NativeFormat, QtCore.QSettings.UserScope, str(isolated))

    qapp = QtWidgets.QApplication(sys.argv)
    from app_Main import App
    messages = []

    def finish(code):
        args.report.parent.mkdir(parents=True, exist_ok=True)
        report["messages"] = messages[-40:]
        args.report.write_text(json.dumps(report, indent=1, default=str), encoding="utf-8")
        print(json.dumps({k: v for k, v in report.items() if k != "messages"}, default=str))
        sys.stdout.flush()
        os._exit(code)      # the application keeps worker threads and pools alive; do not wait for them

    def run():
        try:
            fc.inform.connect(lambda text, *a: messages.append(str(text)))
            report["dataPath"] = str(fc.data_path)
            if not str(fc.data_path).startswith(str(isolated)):
                raise RuntimeError("The application is not using the isolated settings folder: %s" % fc.data_path)
            fc.f_handlers.open_project(str(project), cli=True, plot=False)
            objects = fc.collection.get_list()
            expected = Counter((obj["kind"], obj["options"]["name"]) for obj in read_project(project)["objs"])
            if object_inventory(objects) != expected:
                raise RuntimeError("The loaded object's inventory differs from the source project")
            report["objects"] = [{"name": o.options["name"], "kind": o.kind} for o in objects]
            counts = {}
            for o in objects:
                counts[o.kind] = counts.get(o.kind, 0) + 1
                bounds = o.bounds()                 # every object must be usable, not merely listed
                if not all(math.isfinite(value) for value in bounds):
                    raise RuntimeError("Non-finite bounds: " + o.options["name"])
            report["counts"] = counts
            report["units"] = fc.defaults["units"]
            if args.new_empty_geometry:
                # Control: what the Python application does with a blank Geometry of its own.
                fc.app_obj.new_geometry_object()
                report["ownEmptyGeometryBounds"] = [repr(v) for v in fc.collection.get_list()[-1].bounds()]
            if args.resave:
                report["resaved"] = save_and_validate(fc, args.resave, messages)
            errors = application_errors(messages)
            report["applicationErrors"] = errors
            report["status"] = "PASS" if not errors else "APPLICATION_ERRORS"
            finish(0 if not errors else 1)
        except Exception as error:
            report.update(status="ERROR", error="%s: %s" % (type(error).__name__, error),
                          applicationErrors=application_errors(messages),
                          trace=traceback.format_exc(limit=8))
            finish(2)

    def timed_out():
        report.update(status="TIMEOUT")
        finish(3)

    fc = App(qapp=qapp)
    report["initialUnitPreference"] = fc.defaults["units"]
    if args.initial_units:
        fc.defaults["units"] = args.initial_units
        fc.options["units"] = args.initial_units
        report["initialUnitPreference"] = args.initial_units
    QtCore.QTimer.singleShot(int(args.timeout * 1000), timed_out)
    QtCore.QTimer.singleShot(1500, run)
    qapp.exec_()


if __name__ == "__main__":
    main()
