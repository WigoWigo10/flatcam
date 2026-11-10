"""Compare the opt-in region plan with unchanged Lines, using raw paths and SOURCE CNC order."""
import argparse
import html
import json
from collections import Counter
from pathlib import Path
from shapely import wkt


def paths(geometry):
    if geometry.is_empty:
        return []
    if geometry.geom_type in ("LineString", "LinearRing"):
        return [list(geometry.coords)]
    return [p for g in geometry.geoms for p in paths(g)]


def edges(lines):
    return Counter(tuple(sorted((a, b))) for p in lines for a, b in zip(p, p[1:]) if a != b)


def metrics(lines):
    cursor = (0, 0)
    travel = 0
    for path in lines:
        travel += ((path[0][0] - cursor[0]) ** 2 + (path[0][1] - cursor[1]) ** 2) ** .5
        cursor = path[-1]
    return {"cutPaths": len(lines), "travelLength": travel,
            "cutLength": sum(((a[0] - b[0]) ** 2 + (a[1] - b[1]) ** 2) ** .5
                             for path in lines for a, b in zip(path, path[1:]))}


def svg(document, title):
    copper = wkt.loads(document["copperWkt"])
    x, y, xx, yy = copper.bounds
    width, height = xx - x, yy - y
    lines = paths(wkt.loads(document["rawPathsWkt"]))
    colors = ("#007c9d", "#a72d87", "#17802d", "#6544c9")
    result = [f'<svg viewBox="{x - 1} {-yy - 1} {width + 2} {height + 2}" role="img" aria-label="{html.escape(title)}">']
    # Dashed rapid XY moves and colored cutting paths. Z/tool-change moves are not represented here.
    cursor = (0, 0)
    for path in lines:
        if cursor != (0, 0):
            result.append(f'<path d="M {cursor[0]} {-cursor[1]} L {path[0][0]} {-path[0][1]}" fill="none" stroke="#e27614" stroke-width=".07" stroke-dasharray=".3 .3" opacity=".5"/>')
        cursor = path[-1]
    for i, path in enumerate(lines):
        d = "M " + " L ".join(f"{a} {-b}" for a, b in path)
        result.append(f'<path d="{d}" fill="none" stroke="{colors[i % len(colors)]}" stroke-width=".08"/>')
    result.append("</svg>")
    return "".join(result)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("baseline", type=Path)
    parser.add_argument("regional", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--expected-cases", type=int, default=32)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    results, pictures = [], []
    for file in sorted(args.regional.glob("*.json")):
        new = json.loads(file.read_text(encoding="utf-8"))
        old = json.loads((args.baseline / file.name).read_text(encoding="utf-8"))
        control_name = file.name.replace("-c1-", "-c0-")
        control = json.loads((args.baseline / control_name).read_text(encoding="utf-8"))
        row = {"id": new["id"], "connect": new["connect"], "status": "ERROR"}
        if new["status"] == "GENERATED" and old["status"] == "GENERATED":
            a, b, cuts = (paths(wkt.loads(d["rawPathsWkt"])) for d in (old, new, control))
            wanted, actual = edges(cuts), edges(b)
            missing = sum((wanted - actual).values())
            # No-connect must preserve multiplicity exactly. With Connect, bridges may add edges.
            extras = sum((actual - wanted).values())
            row.update(status="PRESERVED" if missing == 0 and (new["connect"] or extras == 0) else "CUTS_CHANGED",
                       missingEdges=missing, addedEdges=extras, baseline=metrics(a), regional=metrics(b))
            row["travelDelta"] = row["regional"]["travelLength"] - row["baseline"]["travelLength"]
            row["pathDelta"] = row["regional"]["cutPaths"] - row["baseline"]["cutPaths"]
            if new["operation"] == "ncc" and new["board"] in ("breadboard-power-supply", "horizon-choc", "single-sided-5v") and new["connect"]:
                pictures.append(f'<h2>{html.escape(new["board"])}</h2><div class="pair"><section><h3>Lines</h3>{svg(old,"Lines")}</section><section><h3>Por regiões</h3>{svg(new,"Por regiões")}</section></div>')
        results.append(row)
    summary = {"schema": 1, "counts": dict(Counter(r["status"] for r in results)), "cases": results,
               "expectedCases": args.expected_cases, "matrixComplete": len(results) == args.expected_cases and args.expected_cases > 0,
               "policy": "Exact undirected edge multiplicity against unconnected Lines. Connect may only add bridges. "
                         "XY travel assumes SOURCE routing, origin (0,0), no final return, tool changes or machine timing. "
                         "These are route comparisons, not performance benchmarks or proof of physical coverage."}
    (args.output / "comparison.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
    rows = []
    for r in results:
        values = [r["id"], r["status"], r.get("missingEdges", "—"), r.get("addedEdges", "—")]
        values += [round(r.get(k, {}).get("travelLength", 0), 3) for k in ("baseline", "regional")]
        values += [r.get("pathDelta", "—")]
        rows.append("<tr>" + "".join(f"<td>{html.escape(str(v))}</td>" for v in values) + "</tr>")
    document = '''<!doctype html><meta charset="utf-8"><title>Lines por regiões — comparação</title>
<style>body{font:15px system-ui;margin:24px;background:#fafbfc;color:#17283b}table{border-collapse:collapse}td,th{padding:7px;border:1px solid #bcc8d4}svg{width:100%;height:650px;background:white}.pair{display:flex;gap:20px}.pair section{width:50%}p{max-width:1100px}</style>
<h1>Lines por regiões — protótipo</h1><p>Cortes coloridos; deslocamentos XY tracejados em laranja. A ordem CNC é SOURCE, com direção preservada. O ensaio não mede tempo real de máquina. Valores negativos na diferença de trajetos significam menos sequências separadas de corte; não são uma contagem simulada de todos os movimentos Z.</p>
<p>Os cortes básicos do Lines são preservados exatamente, inclusive o acabamento. Connect pode acrescentar ligações seguras. O planejamento pode melhorar uma placa e piorar outra; não substitui o método padrão.</p>
<table><tr><th>Caso</th><th>Cortes</th><th>Arestas ausentes</th><th>Ligações acrescentadas</th><th>Deslocamento Lines</th><th>Por regiões</th><th>Diferença de trajetos</th></tr>''' + "\n".join(rows) + "</table>" + "\n".join(pictures)
    (args.output / "index.html").write_text(document, encoding="utf-8")
    print(json.dumps({"counts": summary["counts"], "report": str(args.output / "index.html")}))
    return any(r["status"] != "PRESERVED" for r in results) or not summary["matrixComplete"]


if __name__ == "__main__":
    raise SystemExit(main())
