# NCC Connect: união compatível com a referência Windows

## Complemento: margem zero, Rest e panelização

A correção posterior preserva a preparação da referência mesmo em margem zero,
o `MultiPolygon(...).buffer(+1e-7)` original dos resíduos Rest e a lista de
cópias Gerber até a união NCC. Join Objects, Paint e o algoritmo do conector
não foram modificados. As comparações por fresa e os limites da matriz ampliada
estão em [PANELIZED_NCC.md](PANELIZED_NCC.md); os números abaixo são históricos.

Implementação em 2026-10-10, depois do commit base `81208c85`.

O residual de Connect não vinha do conector: a área e os caminhos sem Connect
coincidiam, mas a preparação do cobre mudava os vértices iniciais dos anéis.
JTS usa uma união por subárvores de capacidade 4; GEOS 3.10 usa as folhas STR
de capacidade 10 em uma união binária plana. Com mais de 32 partes, os empates
de ordenação do STL Windows também diferem da ordenação estável Java.

O controle com a ordem produzida pelo compilador MSVC passou no projeto real.
A adaptação Java reproduziu esse controle, sem executar código nativo no app.
Fontes primárias: [união GEOS 3.10.3](https://raw.githubusercontent.com/libgeos/geos/3.10.3/src/operation/union/CascadedPolygonUnion.cpp),
[folhas STR](https://raw.githubusercontent.com/libgeos/geos/3.10.3/include/geos/index/strtree/TemplateSTRtree.h)
e [STL Windows VS 2019](https://raw.githubusercontent.com/microsoft/STL/vs-2019-16.10/stl/inc/algorithm).

## Escopo da alteração

- `NccCopperUnion` prepara somente a lista poligonal representada por uma
  GeometryCollection genérica na geração NCC. Usa folhas planas, capacidade 10,
  ordenação Windows com empates e overlay robusto JTS nas uniões binárias.
- Conserva os membros válidos até a união; repara membros inválidos de forma
  limitada, preserva recortes e combina duplicatas/sobreposições. Não modifica
  a origem, gira anéis por heurística ou arredonda coordenadas.
- `LegacyMsvcSort` adapta a ordenação/heap do STL, com cancelamento cooperativo.
  A licença Apache-2.0 WITH LLVM-exception está no JAR CAM em
  `META-INF/licenses/microsoft-stl.txt`; o código adaptado mantém atribuição.
- Polygon/MultiPolygon diretos mantêm a política anterior de buffer zero.
  Paint, o algoritmo de conexão e os limites de comparação não mudaram.

Esta é compatibilidade com **GEOS 3.10.3 / Shapely 1.8.5.post1 no Windows**,
não um port completo GEOS ou promessa de ordenar igual a qualquer versão,
plataforma ou biblioteca C++. Caminhos próximos não certificam execução CNC.
Cancelamento ocorre entre etapas; um overlay JTS individual não é interrompido.

## Regressões e diagnóstico

`NccCopperUnionTest` cobre dez tamanhos públicos (1 a 160 partes), cruzando
as capacidades STR e os limiares 32/40 do sort, nas unidades MM/IN e posições
positiva/negativa. O golden registra IDs de partes e índices de vértices do
GEOS real, sem normalizar a referência. Há testes de recortes, sobreposição,
duplicatas, entradas inválidas, origem intacta, cancelamento e permutações.

O build integral passou: 1520 registrados, 1505 aprovados, 15 opcionais
ignorados, zero falhas/erros. 37 testes auxiliares Python passaram. Sem nova
medição de desempenho/FPS, validação visual manual ou máquina CNC.

Comparação independente de produção final: **32/32 públicos** aprovados.
No projeto real: **10 MATCH_SAMPLED, zero divergências, 1 ORACLE_ERROR** em
multi-settings (bug original Python com lista [MultiPolygon] e Copper offset).
Connect tem distância amostrada 2,1316282e-14 mm, delta de área zero e G-code
aprovado; Standard/Seed Python, três ordens, Rest/Rest Connect, Area/Reference
também passam. Os onze controles sintéticos IN passam. Strict real ainda
retorna 1 pelo erro Python; ele não foi corrigido nem contado como equivalência.
Relatórios ignorados em `target/ncc-union-production-real-20261010`.
Esses casos não encerram a matriz panelizada Connect/Rest/Itself.

Reproduzir os controles públicos, dentro de `flatcam-next`, escolhendo saídas
novas em `target/`:

```powershell
$unionClasspath = 'flatcam-cam/target/classes;' + (Get-Content target/ncc-connect-classpath.txt -Raw).Trim()
java --class-path $unionClasspath tools/NccUnionOrderInput.java --public target/NEW_UNION_INPUT.json
.\target\oracle-py311\Scripts\python.exe tools/compare_ncc_union_probe.py --public-oracle --trace target/NEW_UNION_INPUT.json --output target/NEW_UNION_GOLDEN.json
java --class-path $unionClasspath tools/NccConnectProbe.java target/NEW_CONNECT_TRACE.json
.\target\oracle-py311\Scripts\python.exe tools/compare_ncc_connect_probe.py --legacy-root .. --trace target/NEW_CONNECT_TRACE.json --output target/NEW_CONNECT_REPORT.json
```

`NccUnionProbe` isola união/reparos e `NccRepresentationProbe` compara variantes.
`NccUnionOrderProbe.cpp` é um controle de ordenação opcional (MSVC), nunca uma
dependência de produção; recebe envelopes gerados por `NccUnionOrderInput` e
grava uma permutação em um arquivo novo. Seus binários/dados ficam em target.

`compare_ncc_union_probe` mostra também união binária e unary union dos mesmos
operandos. São controles diferentes (atalhos/reordenação podem diferir), **não**
a captura das chamadas binárias internas do GEOS nem aprovações de paridade.
O critério decisivo é a comparação independente dos caminhos finais do NCC.

O projeto privado foi somente lido, hash original intacto. Não publicar dados
privados ou classificar o erro original Python com Copper offset como aprovação.
