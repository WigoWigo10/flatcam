"""Strict before/after clearing audit using the SAME corrected footprint/access analyzer on both sides."""
import argparse
import html
import json
from pathlib import Path


def read_cases(folder):
    return {p.stem: json.loads(p.read_text(encoding="utf-8")) for p in folder.glob("*-c1-e1.json")} | {
        p.stem: json.loads(p.read_text(encoding="utf-8")) for p in folder.glob("*-rest-*.json")}


def summarize(before, after, cnc, expected=48, expected_cnc=16):
    missing = sorted(before.keys() ^ after.keys())
    failures = [{"id": name, "reason": "missing counterpart"} for name in missing]
    coverage = []
    for name in sorted(before.keys() & after.keys()):
        a, b = before[name], after[name]
        if a["status"] != "MEASURED" or b["status"] != "MEASURED":
            failures.append({"id": name, "reason": "coverage error/budget", "before": a.get("error"), "after": b.get("error")})
            continue
        if a["units"] != b["units"]:
            failures.append({"id": name, "reason": "unit mismatch"})
            continue
        if abs(a["targetArea"] - b["targetArea"]) > max(a["targetArea"] * 1e-10, 1e-12):
            failures.append({"id": name, "reason": "target area changed"})
        if [t["diameter"] for t in a["tools"]] != [t["diameter"] for t in b["tools"]]:
            failures.append({"id": name, "reason": "cutter diameters changed"})
        factor = 25.4 ** 2 if b["units"] == "IN" else 1
        def measure(c):
            return {"nominalUncut": c["geometry"]["reachableUncutArea"] * factor,
                    "stableUncut": c["geometry"]["stableReachableUncutArea"] * factor,
                    "stableCore": c["geometry"]["stableCoreArea"] * factor,
                    "precisionSensitiveUncut": c["geometry"]["precisionSensitiveUncutArea"] * factor,
                    "gcodeStableCore": c["gcode"]["stableCoreArea"] * factor}
        row = {"id": name, "rest": b["rest"], "sourceUnits": b["units"], "stableReachableAreaMM2": b["stableReachableArea"] * factor,
               "beforeMM2": measure(a), "afterMM2": measure(b)}
        coverage.append(row)
        if row["afterMM2"]["stableCore"] > row["beforeMM2"]["stableCore"] + 1e-7:
            failures.append({"id": name, "reason": "stable coverage core increased"})
        if row["afterMM2"]["gcodeStableCore"] > row["beforeMM2"]["gcodeStableCore"] + 1e-7:
            failures.append({"id": name, "reason": "exported stable coverage core increased"})
    routes = [c for c in cnc.values()]
    for c in routes:
        if c.get("status") != "MEASURED" or "uncoveredRequiredLength" not in c:
            failures.append({"id": c["id"], "reason": "missing continuous source-cut check or CNC error"})
    complete = len(before) == expected and len(after) == expected and len(routes) == expected_cnc
    return {"schema": 1, "matrixComplete": complete, "expectedCoverageCases": expected, "expectedCncCases": expected_cnc,
            "coverageCases": coverage, "cncCases": routes, "failures": failures,
            "policy": "Areas normalized to mm2. Both sides use corrected closed-line cutter sweeps and the same nominal/stable-access classifier. "
                      "Precision-sensitive access remains in raw nominal residuals. CNC compares actual GRBL exports: ordinary NEAREST vs regional SOURCE. "
                      "Estimated minutes exclude acceleration/controller behavior; no physical machine-time, full cleanup, or FPS certification."}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("before", "after", "cnc", "output"):
        parser.add_argument(name, type=Path)
    args = parser.parse_args()
    report = summarize(read_cases(args.before), read_cases(args.after), read_cases(args.cnc))
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / "comparison.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    def tr(values):
        return "<tr>" + "".join(f"<td>{html.escape(str(v))}</td>" for v in values) + "</tr>"
    coverage = []
    for c in report["coverageCases"]:
        a, b = c["beforeMM2"], c["afterMM2"]
        coverage.append(tr([c["id"], f'{a["stableCore"]:.7f}', f'{b["stableCore"]:.7f}',
                            f'{b["gcodeStableCore"]:.7f}', f'{b["stableUncut"]:.5f}', f'{b["nominalUncut"]:.5f}', f'{b["precisionSensitiveUncut"]:.5f}']))
    routes = []
    for c in report["cncCases"]:
        if c["status"] != "MEASURED":
            routes.append(tr([c["id"], c["status"], c.get("error")]))
            continue
        a, b = c["baseline"], c["regional"]
        routes.append(tr([c["id"], f'{a["estimatedMinutes"]:.3f}', f'{b["estimatedMinutes"]:.3f}',
                          a["retractCommands"], b["retractCommands"], f'{a["travelXY"]:.2f}', f'{b["travelXY"]:.2f}',
                          f'{a["cutLength"]:.2f}', f'{b["cutLength"]:.2f}', c.get("uncoveredRequiredLength", "não verificado")]))
    document = '''<!doctype html><meta charset="utf-8"><title>Correções Lines/Rest e Connect regional</title>
<style>body{font:15px system-ui;margin:24px;color:#17283b;background:#fafbfc}table{border-collapse:collapse;width:100%}td,th{padding:7px;border:1px solid #bcc8d4;text-align:right}td:first-child{text-align:left}thead{position:sticky;top:0;background:#dde9f2}tr:nth-child(even){background:#edf2f6}p{max-width:1150px}</style>
<h1>Lines/Rest: cobertura e Connect por regiões</h1>
<p>Comparação executada com o mesmo analisador corrigido nos dois lados. O buffer antigo de contornos fechados minúsculos subestimava o disco da fresa. Acesso nominal e acesso com interior numérico estável permanecem separados; canais de encaixe exato não são declarados limpos nem impossíveis por conveniência. Áreas em mm².</p>
<h2>Cobertura</h2><p>Núcleo = sobra após descontar a tolerância de discretização declarada. As sobras nominais brutas continuam registradas. O Rest agora pode sobrepor material já removido, mantendo os limites originais de cobre e borda. As caixas de cobre usadas neste ensaio não substituem o Outline real de uma PCB.</p>
<table><thead><tr><th>Caso</th><th>Núcleo estável antes</th><th>Depois</th><th>G-code depois</th><th>Sobra estável bruta depois</th><th>Sobra nominal depois</th><th>Sobra sensível à precisão</th></tr></thead><tbody>''' + "\n".join(coverage) + '''</tbody></table>
<h2>Connect: programas exportados</h2><p>GRBL_11_no_M6: Lines comum/Proximidade versus regional/ordem preservada. Safe Z 3 mm, corte -0,1 mm, avanço XY 300 mm/min e avanço Z padrão. Os tempos são estimativas do parser, com rápidos de 1500 mm/min; não incluem aceleração, fixação, comportamento do controlador ou ensaio físico. Retrações incluem os comandos de início/fim comuns aos dois lados. Deslocamentos/cortes em mm.</p>
<table><thead><tr><th>Caso</th><th>Minutos antes</th><th>Depois</th><th>Retrações antes</th><th>Depois</th><th>XY rápido antes</th><th>Depois</th><th>Corte antes</th><th>Depois</th><th>Comprimento requerido ausente</th></tr></thead><tbody>''' + "\n".join(routes) + "</tbody></table>"
    (args.output / "index.html").write_text(document, encoding="utf-8")
    print(json.dumps({"matrixComplete": report["matrixComplete"], "coverage": len(report["coverageCases"]), "cnc": len(routes), "failures": report["failures"]}, ensure_ascii=False))
    return bool(report["failures"]) or not report["matrixComplete"]


if __name__ == "__main__":
    raise SystemExit(main())
