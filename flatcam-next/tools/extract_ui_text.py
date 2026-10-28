"""Plan/review UI catalog extraction using the JDK AST inventory, never regex over Java code.

Run the Java inventory first. This first migration deliberately retains canonical
metadata and registry keys. Display sites translate that metadata with I18n.caption.
"""
from pathlib import Path
import argparse
import hashlib
import json
import re

ROOT = Path(__file__).resolve().parents[1]
SOURCES = ROOT / "flatcam-fx/src/main/java/org/flatcam/fx"
KEEP = {"I18n.java", "UiLanguage.java", "ToolDefaults.java", "LegacyUiManifest.java",
        "ThemeOption.java", "CodeSyntax.java", "AboutInfo.java", "TooltipContent.java",
        "AppPreferences.java"}
WORDS = ("arquivo salvar abrir ferramenta objeto projeto selecione selecionar cancelar fechar editar exibir "
         "objetos ferramentas ajuda sobre propriedades idioma idiomas limpar aplicar restaurar fonte carregando "
         "sim tipo nome copiar renomear remover excluir desfazer refazer deslocar espelhar reiniciar tamanho "
         "comprimento largura altura raio linhas colunas espelho enquadrar cor cores azul verde branco preto "
         "amarelo vermelho roxo marrom transparente iniciar parar continuar voltar erro aviso nenhum nenhuma "
         "selecionados selecionadas todos todas novo nova mudar valor fator graus unidades centro ponto pontos "
         "positivo negativo retrato paisagem inteiro inteira fundo imagem limite limites para com sem uma pelo "
         "pela ao nas dos das desenho geração politica politica calculo gravar girar furar recorte caminho "
         "esquerdo direito esquerda direita superior inferior inicio fim fora dentro campo campos inalterado "
         "ocultar mostrar falha falhou concluido criar permite inclui usa numero marca desmarca use informe "
         "confira aguarde impossível origem destino padrao padroes teste diagnostico diagnosticos controles "
         "borda placa original originalidade largura passo ordem propriedades recentes opções comandos ajuda").split()
PORTUGUESE = re.compile(r"[áàâãéêíóôõúçÁÀÂÃÉÊÍÓÔÕÚÇ]|\b(?:" + "|".join(WORDS) + r")\b", re.I)


def identifier(text):
    return "ui." + hashlib.sha256(text.encode("utf-8")).hexdigest()[:12]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("inventory", type=Path)
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    data = json.loads(args.inventory.read_text(encoding="utf-8"))
    plan = {}
    replacements = {}
    for literal in data:
        value, name = literal["value"], literal["file"]
        if not PORTUGUESE.search(value) or literal["caseLabel"]:
            continue
        if name in {"I18n.java", "UiLanguage.java", "TooltipContent.java", "AppPreferences.java"}:
            continue
        if literal["call"].startswith("I18n."):
            continue
        # Regex definitions, filenames and internal names are data, not UI messages.
        if literal["call"].endswith((".compile", ".replaceAll", ".replaceFirst")):
            continue
        if value.startswith(("http:", "https:", "-D", "org.", "com.", "java.", "javax.", "fx.", "preferences.", "tool-default-")):
            continue
        if value.startswith("passo,tipo,marca_inicio,"):
            continue
        if re.fullmatch(r"[a-z0-9_.\-/]+", value) and ("_" in value or "." in value):
            continue
        key = identifier(value)
        entry = plan.setdefault(key, {"source": value, "locations": [], "canonical": False})
        assert entry["source"] == value
        entry["locations"].append(name)
        keep = name in KEEP or literal["mapKey"]
        entry["canonical"] |= keep
        if not keep:
            replacements.setdefault(name, []).append((literal["start"], literal["end"], key))
    output = ROOT / "target/i18n-plan.json"
    output.write_text(json.dumps(plan, ensure_ascii=False, indent=2), encoding="utf-8")
    if args.apply:
        rewritten = {}
        for name, spans in replacements.items():
            path = SOURCES / name
            # AST offsets refer to the original CR/LF characters, not universal-newline text.
            source = path.read_bytes().decode("utf-8")
            previous = len(source) + 1
            for start, end, key in sorted(spans, reverse=True):
                assert end <= previous, (name, start, end)
                assert source[start] == '"', (name, start, source[start:start+30])
                source = source[:start] + 'I18n.text("' + key + '")' + source[end:]
                previous = start
            rewritten[path] = source.encode("utf-8")
        # Validate every file before publishing any edits.
        for path, encoded in rewritten.items():
            path.write_bytes(encoded)
    print(json.dumps({"keys": len(plan), "words": sum(len(e["source"].split()) for e in plan.values()),
                      "files": len(replacements), "displayExpressions": sum(map(len, replacements.values())),
                      "plan": str(output)}))


if __name__ == "__main__":
    main()
