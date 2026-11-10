"""Summarize nominal stock measurements. Measured residuals are findings, not passes."""
import argparse
import html
import json
from collections import Counter
from pathlib import Path


def summarize(folder):
    cases = [json.loads(p.read_text(encoding="utf-8")) for p in sorted(folder.glob("*.json"))]
    measured = [c for c in cases if c["status"] == "MEASURED"]
    groups = {}
    for key in ("contour", "rest"):
        groups[key] = {}
        for flag in (False, True):
            subset = [c for c in measured if c[key] == flag]
            percentages = [100 * c["geometry"]["reachableUncutArea"] / (c["targetArea"] - c["nominalUnreachableArea"])
                           for c in subset if c["targetArea"] > c["nominalUnreachableArea"]]
            groups[key][str(flag)] = {"count": len(subset), "reachableUncutPercentRange":
                                      [min(percentages), max(percentages)] if percentages else None}
    summary = {"schema": 1, "counts": dict(Counter(c["status"] for c in cases)), "cases": len(cases),
               "boards": sorted({c["board"] for c in cases}), "groups": groups,
               "policy": "Nominal circular swept footprints, Q16; original allowed-area access Q64. Raw residual retained. "
                         "Core is residual eroded by the declared chord/rounding tolerance; it is not a general numerical error bound. "
                         "Diagnostic copper bounding boxes are not physical PCB outlines or CNC validation.",
               "errors": [{"id": c["id"], "error": c.get("error")} for c in cases if c["status"] != "MEASURED"]}
    return cases, summary


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("folder", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--expected-cases", type=int, default=96)
    args = parser.parse_args()
    cases, summary = summarize(args.folder)
    summary["expectedCases"] = args.expected_cases
    summary["matrixComplete"] = len(cases) == args.expected_cases and args.expected_cases > 0
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    (args.output / "cases.json").write_text(json.dumps({"summary": summary, "cases": cases}, ensure_ascii=False, indent=2), encoding="utf-8")
    rows = []
    for c in cases:
        scale = 25.4 if c["units"] == "IN" else 1
        factor = scale * scale
        g, nc = c.get("geometry", {}), c.get("gcode", {})
        def area(value):
            return f"{value * factor:.5f}" if value is not None else "—"
        rows.append("<tr>" + "".join(f"<td>{html.escape(str(v))}</td>" for v in
                    [c["id"], c["status"], area(c.get("nominalUnreachableArea")),
                     area(g.get("reachableUncutArea")), area(g.get("reachableCoreArea")),
                     area(nc.get("reachableUncutArea")), area(nc.get("reachableCoreArea")),
                     f"{c.get('gcodeTravelLength', 0) * scale:.2f}", c.get("gcodeCutPaths", "—")]) + "</tr>")
    document = """<!doctype html><meta charset="utf-8"><title>Auditoria Lines — cobertura e G-code</title>
<style>body{font:15px system-ui;margin:24px;color:#17283b;background:#fafbfc}table{border-collapse:collapse;width:100%}td,th{padding:7px;border:1px solid #bcc8d4;text-align:right}td:first-child{text-align:left}thead{position:sticky;top:0;background:#dde9f2}tr:nth-child(even){background:#edf2f6}p{max-width:1100px}</style>
<h1>Lines: cobertura nominal e G-code</h1><p>Áreas convertidas para mm²; deslocamentos em mm. “Medido” significa que a análise terminou, não que há cobertura completa.</p>
<p>Acesso calculado na área original, inclusive em Rest: a fresa pode passar por material já removido. A coluna “núcleo” desconta a tolerância declarada de discretização e arredondamento; a sobra bruta permanece visível. O ensaio usa a caixa do cobre, não o Outline físico da placa. Não simula fixação, Z, máquina ou corte real.</p>
<p>Comparação do G-code: GRBL_11_no_M6, cada ferramenta separada e programa combinado. Coordenadas e pontos médios verificados nos dois sentidos; comprimento e deslocamentos registrados. Tolerância de exportação: 4 casas MM / 6 casas IN.</p>
<table><thead><tr><th>Caso</th><th>Estado</th><th>Inalcançável</th><th>Sobra alcançável Geometry</th><th>Núcleo Geometry</th><th>Sobra alcançável G-code</th><th>Núcleo G-code</th><th>Deslocamento</th><th>Partes de corte no parser</th></tr></thead><tbody>""" + "\n".join(rows) + "</tbody></table>"
    (args.output / "index.html").write_text(document, encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False))
    return bool(summary["errors"]) or not summary["matrixComplete"]


if __name__ == "__main__":
    raise SystemExit(main())
