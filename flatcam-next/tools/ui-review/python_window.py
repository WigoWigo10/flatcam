"""Run the ORIGINAL Python GUI with settings isolated to a review directory; no UI automation."""
import argparse
import os
import runpy
import sys
import types
from pathlib import Path
from urllib.parse import quote


def isolate_qsettings(settings):
    from PyQt5 import QtCore
    original = QtCore.QSettings

    class ReviewSettings(original):
        def __init__(self, *args, **kwargs):
            # QSettings(org, app) ignores setDefaultFormat on this PyQt build.
            # Route the constructors used by FlatCAM to an explicit INI file.
            if kwargs:
                raise RuntimeError("Unsupported QSettings keywords in isolated review")
            if not args:
                organization, application = "default", "default"
            elif len(args) == 2 and all(isinstance(value, str) for value in args):
                organization, application = args
            else:
                raise RuntimeError("Unsupported QSettings constructor in isolated review")
            directory = settings / "qt" / quote(organization, safe="")
            directory.mkdir(parents=True, exist_ok=True)
            super().__init__(str(directory / (quote(application, safe="") + ".ini")), original.IniFormat)

    QtCore.QSettings = ReviewSettings
    probe = QtCore.QSettings("Open Source", "FlatCAM")
    if not Path(probe.fileName()).resolve().is_relative_to(settings):
        raise AssertionError("QSettings escaped review directory")
    return probe


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--legacy-root",type=Path,required=True)
    parser.add_argument("--settings",type=Path,required=True)
    parser.add_argument("--probe",action="store_true")
    args=parser.parse_args()
    root=args.legacy_root.resolve(); settings=args.settings.resolve()
    settings.mkdir(parents=True,exist_ok=True)
    log=(settings/"python-window.log").open("w",encoding="utf-8",buffering=1)
    sys.stdout=log; sys.stderr=log
    os.environ["APPDATA"]=str(settings)
    shell=types.SimpleNamespace(SHGetFolderPath=lambda *a,**k:str(settings))
    shellcon=types.SimpleNamespace(CSIDL_APPDATA=26)
    package=types.ModuleType("win32comext"); module=types.ModuleType("win32comext.shell")
    module.shell= shell; module.shellcon=shellcon; package.shell=module
    sys.modules["win32comext"]=package; sys.modules["win32comext.shell"]=module
    probe = isolate_qsettings(settings)
    print("QSettings isolated:", probe.fileName())
    if args.probe:
        probe.setValue("review-probe", "isolated")
        probe.sync()
        if probe.value("review-probe") != "isolated":
            raise AssertionError("Review QSettings round-trip failed")
        print("PASS: QSettings explicit INI round-trip")
        return
    os.chdir(root); sys.path.insert(0,str(root)); sys.argv=[str(root/"FlatCAM.py")]
    runpy.run_path(str(root/"FlatCAM.py"),run_name="__main__")


if __name__=="__main__":
    from multiprocessing import freeze_support
    freeze_support()
    main()
