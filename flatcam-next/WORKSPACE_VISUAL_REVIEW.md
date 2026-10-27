# Fluxos integrados e inspeção visual — 2026-10-26

Terceira etapa da série, sobre `9831e3d5`. 121 testes aprovados em 16 suítes,
zero falhas/erros. Logs `target/session-review-20261026/visual-flows.log` e
`visual-flows-final.log`; a contagem considera cada teste uma vez, mesmo quando
reexecutado. Inclui as novas quatro combinações de tema do workspace e uma
regressão da lista de atalhos.

## Verificado

- Capturas JavaFX reais, não mockups: Branco, Preto, Branco gelo e Preto gelo.
  Workspace com editor Tcl, Lista de Atalhos e Medir Distância; revisão CNC;
  tabelas compactas Isolation/NCC/Geometry, exclusões Drilling e cobertura NCC.
- Inspeção direta das capturas de workspace nos quatro temas, revisão CNC nos
  temas gelo e Branco, TT de NCC gelo claro e Isolation preto. Contraste e dados
  permaneceram legíveis nesses exemplos. Demais combinações têm verificação
  automatizada; não se afirma inspeção humana de cada imagem/pixel.
- Workspace redimensionado em 1024/1280 × 800 sem escrever preferências. Abrir
  painéis não elimina script não salvo; Lista de Atalhos é reutilizada.
- 44 testes MainCamFlow, 11 GeometryCncGeneration, cinco DrillingLiveJob e dez
  MainPanelizeFlow exercitam publicação/descartes de resultados, cancelamento,
  parâmetros e arquivos. Os demais cobrem painéis, tabelas, medidas, scripts,
  objetos novos e comandos de visualização.

## Ajuste de UX

A Lista de Atalhos truncava descrições/combinações longas em células estreitas.
As três colunas agora quebram linhas, com largura vinculada à coluna e altura
variável, mantendo cores do tema e virtualização. Teste verifica gráficos de
célula com wrap em painel de 420 px. Não foram introduzidas cores fixas.

## Limites

Capturas são opt-in (`-Dflatcam.tests.snapshots=true`) e ficam em
`flatcam-fx/target/workspace-*.png`, `cnc-review-*.png`, `compact-tools-*.png`,
entre outras. Scenes offscreen não certificam escala física, múltiplos monitores,
foco real, acessibilidade nem todos os cliques/FileChoosers. Conteúdo da aba é
atualizado antes de alguns cabeçalhos de TabPane nos pulsos offscreen; não usar
uma captura isolada como prova de ordem física dos frames.

Dados das capturas são públicos/sintéticos. Não houve nova comparação visual
lado a lado com o aplicativo Python, ensaio CNC físico ou benchmark nessa etapa.
Próxima etapa: medir o Plot neste notebook e registrar o adaptador realmente usado.
