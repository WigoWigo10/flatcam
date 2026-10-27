# NCC Offset: carregamento no aplicativo Python real — 2026-10-26

Sobre `cfbefb21`, continuação dos dois ORACLE_ERROR preservados em
[NCC_POINT_TOUCH_REVIEW.md](NCC_POINT_TOUCH_REVIEW.md).

## Resultado

O aplicativo original `app_Main.App` abriu o projeto autorizado atual, com seus
18 objetos, em Qt offscreen e diretório/QSettings isolados. O handler original
`fc.ncclear_tool.get_tool_empty_area` recebeu os próprios objetos carregados,
sem converter, achatar, unir ou reparar sua geometria.

Nas duas faces F_Cu/B_Cu, `solid_geometry` é `list[MultiPolygon]`, com um membro;
`gerber_buffering` é `full`. Controle sem Offset prepara a área nas duas faces.
Com Offset de 0,15 mm, ambas falham em `ToolNCC.py:1934`:
`MultiPolygon(sol_geo)`, `ValueError: Sequences of multi-polygons are not valid arguments`.
Portanto, a representação não foi introduzida apenas pelo adaptador do comparador:
ela também chega ao handler depois do carregamento pelo aplicativo original.

São **2 PREPARED e 2 ORACLE_ERROR**, exit 1, não quatro aprovações de paridade.
Não houve geração completa pelo botão GUI, nova matriz panelizada, comparação
dos caminhos FX, teste de máquina nem correção do algoritmo Python. O limite
continua sendo do handler/representação do legado; nenhuma correção FX foi
justificada por esse ensaio. A diferença Connect anteriormente corrigida não
foi reaberta ou reclassificada.

## Reprodução

`tools/open_in_python_app.py --probe-ncc-offset` exige projeto e relatório;
recusa salvar o projeto ou combinar com outra sonda. Registra tipos, parâmetros,
tracebacks, hashes do projeto antes/depois e das fontes originais.

```powershell
# Usar Python 3.11 com as dependências do aplicativo e Shapely 1.8.5.post1/GEOS 3.10.3.
python tools/open_in_python_app.py --legacy-root .. 'C:/pasta/projeto.FlatPrj' --probe-ncc-offset --report target/novo-relatorio.json
```

Ensaio local: Python 3.11.0, Shapely 1.8.5.post1, GEOS 3.10.3-CAPI-1.16.1,
PyQt 5.15.2. Shapely instalado separadamente sob `target/session-review-20261026/python-deps`
e selecionado apenas no processo do ensaio; o ambiente habitual Shapely 2.1.2
não foi modificado. NumPy/OR-Tools do ambiente habitual não foram equiparados ao
corpus anterior; este diagnóstico não os usa para gerar trajetórias.

Relatório privado: `target/session-review-20261026/ncc-offset-live.json` e log
adjacente. Projeto e cinco fontes originais permaneceram com SHA-256 idêntico.
Geometria privada e caminhos locais não entram neste documento nem no Git.
Quatro regressões públicas do diagnóstico verificam passagem da representação
intacta, preservação de erros, continuação das faces e proibição de save.
