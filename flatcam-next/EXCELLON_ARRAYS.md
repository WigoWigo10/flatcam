# Arrays do Editor Excellon — 2026-10-27

LINEAR e CIRCULAR criam novos furos ou slots a partir do diâmetro do formulário,
sem exigir seleção. GRID mantém a replicação retangular anterior da seleção,
com aplicação direta. A quantidade linear/circular é TOTAL, incluindo o primeiro.

- Linear: eixo X, Y ou ângulo anti-horário; passo entre centros em mm/in.
- Circular: centro, raio positivo, ângulo inicial, passo positivo e CW/CCW.
  Quantidade × passo <= 360° impede fechar com elemento duplicado.
- Slots: comprimento total inclui tampas redondas e deve exceder o diâmetro.
  Ângulo de orientação e opção de acompanhar o círculo ou manter paralelos.
- Escolher no plot: linear recebe o primeiro centro; circular recebe centro e
  primeiro elemento em dois cliques com Snap. atan2 funciona nos quatro quadrantes.

Prévia em worker cancelável, sem criar ferramenta ou mudar o rascunho; usa overlay
indexado e cruz pequena de referência. Confirmar (botão/clique) aplica exatamente
a prévia, sem transladá-la. Ctrl+Z desfaz array e eventual ferramenta nova em uma
transação; Ctrl+Y refaz. Reutiliza diâmetro existente sem alterar as ferramentas
anteriores. Fechar/cancelar/mudar parâmetros invalida resultados pendentes. ESC
invalida o resultado em cálculo; Cancelar no progresso interrompe o worker.
Limite de 10.000 elementos e recusa de não finitos/overflow antes da publicação.

Geometria preparada é reaproveitada ao confirmar e em undo/redo, evitando refazer
todos os buffers na thread FX. Dados de furos e endpoints dos slots são mantidos
explicitamente; exportação não reconstrói furos a partir de polígonos.

## Evidência e limites

6 testes de arrays CAM +5 do editor anterior e 40 UI/preferências/tooltips passaram
(`target/excellon-arrays-final.log`); a última revisão de controles dependentes
está em `target/excellon-arrays-commit-check.log`.

```powershell
.\mvnw.cmd -pl flatcam-fx -am -Dtest=ExcellonArrayTest,ExcellonEditSessionTest,ExcellonArrayPanelTest,TooltipAuditTest -Dsurefire.failIfNoSpecifiedTests=false package
& ..\..\flatcam-8994\.venv312\Scripts\python.exe tools/compare_excellon_arrays.py --legacy-root ..
```

256 controles MM/IN com as rotinas ORIGINAIS de distribuição linear/circular do
Python, incluindo slots, CW/CCW e vários ângulos: zero divergências, maior distância
6,41e-15 mm. `tools/compare_excellon_arrays.py` compila métodos por AST sem mudar
seus corpos. Somente o molde util_shape é uma primitiva independente (ponto ou
linha central de slot): a prova cobre posições/orientação, não tesselação dos
polígonos Python nem seus campos points_edit ou cliques reais. Ordem dos endpoints
de slots é desconsiderada na comparação geométrica, pois representa o mesmo rasgo.
Arquivo Python fonte SHA-256: 11cfe0319d85b56c9392345cfcf57ca8325f8d9afcaa8f8fc2183a27033cc151.
Relatório público em `target/excellon-array-python.json`; CSV sintético em
`flatcam-cam/target/excellon-array-comparison.csv`. Nenhuma placa privada incluída.
O modo de manter slots paralelos e GRID são extensões FX. Validação visual na
janela real será feita no item 4 da série. Nenhuma validação de máquina CNC.
