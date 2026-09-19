# -*- coding: utf-8 -*-
"""
Support module for Fase 0 (baseline) characterization tests of the legacy
Gerber/Excellon parsers (appParsers.ParseGerber.Gerber / appParsers.ParseExcellon.Excellon).

Not a test module itself (no TestCase here) - shared by:
    - generate_baseline.py   (writes tests/baseline/*.json)
    - test_gerber_characterization.py
    - test_excellon_characterization.py

Why this exists: Gerber/Excellon (via the shared Geometry base class in camlib.py)
read `self.app.defaults[...]`, `self.app.decimals`, `self.app.is_legacy`,
`self.app.plotcanvas`, `self.app.inform` and `self.app.proc_container` directly in
__init__ and while parsing. There is no PyQt event loop or real App/GUI here -
only the small stub below, wired in via the `app` class attribute the parser
classes already expect (see Gerber.app / Geometry.app).
"""
import json
import os

import camlib
from defaults import FlatCAMDefaults
from appParsers.ParseGerber import Gerber
from appParsers.ParseExcellon import Excellon

TESTS_DIR = os.path.dirname(os.path.abspath(__file__))
BASELINE_DIR = os.path.join(TESTS_DIR, 'baseline')

# (relative-to-repo-root path, baseline file stem)
GERBER_FIXTURES = [
    'tests/gerber_files/simple1.gbr',
    'tests/gerber_files/detector_contour.gbr',
    'tests/gerber_files/detector_copper_top.gbr',
    'tests/gerber_files/detector_copper_bottom.gbr',
    'tests/gerber_files/STM32F4-spindle.cmp',
]

EXCELLON_FIXTURES = [
    'tests/excellon_files/case1.drl',
    'tests/gerber_files/detector_drill.txt',
]


class _StubShapeCollection:
    """Stands in for VisPy's ShapeCollection; parsing only ever calls .add()."""

    def add(self, *_args, **_kwargs):
        pass


class _StubPlotCanvas:
    def new_shape_collection(self, layers=1):
        return _StubShapeCollection()


class _StubSignal:
    """Stands in for a Qt signal (App.inform, etc.) - parsing only calls .emit()."""

    def emit(self, *_args, **_kwargs):
        pass

    def connect(self, *_args, **_kwargs):
        pass


class _StubProcContainer:
    new_text = ''

    def update_view_text(self, *_args, **_kwargs):
        pass


class StubApp:
    """
    Minimal stand-in for appMain.App, providing only what Geometry/Gerber/Excellon
    touch during parsing. Deliberately NOT a full App: no GUI, no Qt event loop,
    no plugins. Uses the real factory defaults from defaults.FlatCAMDefaults so
    aperture/units/zeros defaults match production behavior.
    """

    def __init__(self):
        self.defaults = dict(FlatCAMDefaults.factory_defaults)
        self.decimals = 4
        self.is_legacy = False
        self.abort_flag = False
        self.plotcanvas = _StubPlotCanvas()
        self.inform = _StubSignal()
        self.proc_container = _StubProcContainer()


def install_stub_app():
    """
    Wire the stub app onto the `app` class attribute shared by Geometry and its
    Gerber/Excellon subclasses. Must run before constructing either parser.
    Returns the StubApp instance (rarely needed by callers).
    """
    stub = StubApp()
    camlib.Geometry.app = stub
    Gerber.app = stub
    Excellon.app = stub
    return stub


def _round(value, places=6):
    if value is None:
        return None
    return round(float(value), places)


def _iter_geoms(solid_geometry):
    """
    Recursively flattens to leaf (non-container) geometries. Gerber.solid_geometry
    can be a plain Shapely geometry, a list of geometries, or - as seen with
    simple1.gbr - a length-1 list wrapping a MultiPolygon: a shallow check
    (list vs geometry) undercounts real disjoint parts in that case.
    """
    if solid_geometry is None:
        return []
    if hasattr(solid_geometry, 'geoms'):  # MultiPolygon / GeometryCollection
        result = []
        for g in solid_geometry.geoms:
            result.extend(_iter_geoms(g))
        return result
    if isinstance(solid_geometry, (list, tuple)):
        result = []
        for g in solid_geometry:
            result.extend(_iter_geoms(g))
        return result
    return [solid_geometry]


def parse_gerber(repo_relative_path):
    """Parse a Gerber fixture with a fresh Gerber() and the stub app installed."""
    install_stub_app()
    gerber = Gerber()
    gerber.parse_file(repo_relative_path)
    return gerber


def parse_excellon(repo_relative_path):
    """Parse an Excellon fixture with a fresh Excellon() and the stub app installed."""
    install_stub_app()
    excellon = Excellon()
    excellon.parse_file(repo_relative_path)
    return excellon


def summarize_gerber(gerber):
    """
    Structural summary of what the Gerber parser extracted from a file - not a
    dump of every coordinate, but enough to catch a parser regression or a
    behavior difference from a future (e.g. Java) reimplementation:
    units, aperture set (type + size/width/height), macro count, resulting
    solid geometry (part count + total area), and overall bounds.
    """
    apertures = {}
    for ap_id, aperture in sorted(gerber.apertures.items(), key=lambda kv: str(kv[0])):
        entry = {'type': aperture.get('type')}
        for key in ('size', 'width', 'height', 'diam', 'nVertices'):
            if aperture.get(key) is not None:
                try:
                    entry[key] = _round(aperture[key])
                except (TypeError, ValueError):
                    entry[key] = aperture[key]
        entry['geometry_count'] = len(aperture.get('geometry') or [])
        apertures[str(ap_id)] = entry

    geoms = _iter_geoms(gerber.solid_geometry)
    total_area = sum(g.area for g in geoms if g is not None and not g.is_empty)

    bounds = None
    if not gerber.is_empty():
        try:
            bounds = [_round(b) for b in gerber.bounds()]
        except Exception:
            bounds = None

    return {
        'units': gerber.units,
        'aperture_count': len(gerber.apertures),
        'aperture_macro_count': len(gerber.aperture_macros),
        'apertures': apertures,
        'solid_geometry_part_count': len(geoms),
        'solid_geometry_total_area': _round(total_area, 8),
        'follow_geometry_count': len(gerber.follow_geometry or []),
        'bounds': bounds,
    }


def _slot_points(slot):
    if isinstance(slot, dict):
        return [p for p in (slot.get('start'), slot.get('stop')) if p is not None]
    if isinstance(slot, (list, tuple)):
        return [p for p in slot if p is not None]
    return []


def summarize_excellon(excellon):
    """
    Structural summary of what the Excellon parser extracted: units, zero
    suppression mode, per-tool diameter/drill-count/slot-count, and bounds
    computed directly from the parsed drill/slot points.

    Deliberately scoped to parsing (not create_geometry()/CNCJob generation) -
    that is a separate, later characterization layer once Fase 4/5 of
    CONTEXTO_FLATCAM_FX.md are underway.
    """
    tools = {}
    total_drills = 0
    total_slots = 0
    xs = []
    ys = []

    for tool_id, tool in sorted(excellon.tools.items(), key=lambda kv: str(kv[0])):
        drills = tool.get('drills') or []
        slots = tool.get('slots') or []
        total_drills += len(drills)
        total_slots += len(slots)

        for point in drills:
            if point is not None:
                xs.append(point.x)
                ys.append(point.y)
        for slot in slots:
            for point in _slot_points(slot):
                xs.append(point.x)
                ys.append(point.y)

        tools[str(tool_id)] = {
            'tooldia': _round(tool.get('tooldia')),
            'drill_count': len(drills),
            'slot_count': len(slots),
        }

    bounds = None
    if xs and ys:
        bounds = [_round(min(xs)), _round(min(ys)), _round(max(xs)), _round(max(ys))]

    return {
        'units': excellon.units,
        'zeros': excellon.zeros,
        'tool_count': len(excellon.tools),
        'total_drills': total_drills,
        'total_slots': total_slots,
        'tools': tools,
        'bounds': bounds,
    }


def baseline_path(kind, fixture_path):
    stem = os.path.splitext(os.path.basename(fixture_path))[0]
    return os.path.join(BASELINE_DIR, kind, stem + '.json')


def load_baseline(kind, fixture_path):
    path = baseline_path(kind, fixture_path)
    with open(path, 'r', encoding='utf-8') as handle:
        return json.load(handle)


def write_baseline(kind, fixture_path, summary):
    path = baseline_path(kind, fixture_path)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as handle:
        json.dump(summary, handle, indent=2, sort_keys=True, ensure_ascii=False)
        handle.write('\n')
