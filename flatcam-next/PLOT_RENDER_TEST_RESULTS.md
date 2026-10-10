# Ensaios comparativos do Plot — 2026-10-10

Branch `flatcam-next`, worktree dirty sobre `a8116d0f`; sem novo commit/push.
Continuação dos testes, não adoção de outro backend nem otimização CAM.

Esta bateria usa schema 2. A continuação posterior usa schema 3 e isola os
componentes CNC: [PLOT_CNC_ABLATION.md](PLOT_CNC_ABLATION.md).

## Instrumentação

O benchmark opt-in agora marca no JFR abertura/preparo inicial, warm-up,
pan, zoom, seleção e apagar/desfazer, com repetição e estado completed.
Intervalos de pulso >= 50 ms recebem marcador separado. Tudo fica no código
de teste; a aplicação normal não carrega nem emite esses eventos.

Com `-Jfr`, após as fases medidas, salva `phase-profile.jfr` e
`jfr-summary.json`, além do `recording.jfr` final e `report.json`. O snapshot
não interrompe a gravação original. A análise faz duas leituras sequenciais,
sem reter todas as pilhas. Registros privados permanecem ignorados em target.

O resumo por fase contém amostras `jdk.ExecutionSample` por thread, top frames,
presença de Marlin/Prism/DensityRaster, amostras dentro de intervalos de pulso
longos e duração de sobreposição de GC/ThreadPark registrados. Categorias de
pilha podem se sobrepor. Amostras são estatísticas CPU, **não percentuais de
tempo GPU/FPS nem prova causal**. Limites de fase são temporais: trabalho
assíncrono iniciado antes pode terminar dentro de outra fase. Zero amostras
numa fase curta não prova custo zero; ausência de waits/GC pode decorrer dos
limiares do JFR. O fim do intervalo de pulso é aproximado pelo commit do evento.

O JSON principal passa ao schema 2 e registra densityEnabled, pixelBuffer e
jfrRequested. O novo parâmetro `-PixelWriter` desliga apenas o PixelBuffer do
raster para o processo de teste, sem mudar defaults/launcher do aplicativo.

APIs oficiais consultadas: [Event](https://docs.oracle.com/en/java/javase/25/docs/api/jdk.jfr/jdk/jfr/Event.html),
[Recording](https://docs.oracle.com/en/java/javase/25/docs/api/jdk.jfr/jdk/jfr/Recording.html)
e [RecordingFile](https://docs.oracle.com/en/java/javase/25/docs/api/jdk.jfr/jdk/jfr/consumer/RecordingFile.html).

## CNC real: três configurações

Mesmo projeto/objeto `Cobre_Morto_Bottom_cnc`, tema ICE_DARK, Scene 1280×800
lógicos, escala física 2×2, 120 passos e três repetições em JVMs distintas,
todas com JFR. Execuções sequenciais, sem build/oráculos concorrentes.
Pipeline consultado: D3D/Intel Arc/driver 32.0.101.8991 nos dois primeiros;
SWPipeline confirmado no terceiro. Fonte preservada por SHA-256 em todos.
Edição é explicitamente ignorada nesses três filtros somente CNC.

| Configuração | Queue p95 do zoom, repetições 2–3 | Pulso máximo do zoom, repetições 2–3 | Pulso máximo no primeiro zoom |
| --- | --- | --- | --- |
| D3D + PixelBuffer | 18,32–24,62 ms | 48,60–48,66 ms | 592,18 ms |
| D3D + PixelWriter | 19,67–20,11 ms | 48,26–48,45 ms | 479,58 ms |
| Software + PixelBuffer | 48,77–49,03 ms | 79,91–81,13 ms | 98,57 ms |

A primeira repetição fica separada, não é apagada da comparação: há efeitos
de aquecimento/alocação diferentes por pipeline. **Não** concluir que software
é mais rápido pelo seu máximo frio menor, nem que PixelWriter eliminou o
gargalo pela diferença entre duas execuções frias. São três repetições numa
execução por configuração, não uma bateria intercalada de sessões independentes
com significância estatística ou comparação contra um backend novo.

Na inspeção adicional do primeiro zoom D3D/PixelBuffer, `jdk.Compilation`
também registrou compilações OSR de `Renderer._endRendering` (exemplos de
104–194 ms). São tarefas do compilador em background: não somar suas durações
como bloqueio da UI nem atribuir a elas, sozinhas, o máximo observado.
Há aquecimento real a separar da fluidez sustentada.

Pan quente: D3D/PixelBuffer teve queue p95 0,18–0,34 ms e nenhum pulso >= 50 ms;
software teve queue p95 57,67–58,04 ms e 117 pulsos >= 50 ms em cada repetição
2–3. Neste corpus a aceleração D3D já ajuda a fluidez sustentada.

JFR **somente durante zoom**, não somando abertura/warm-up:

- D3D/PixelBuffer: 264/160/153 amostras CPU por repetição; 250/153/145 na
  QuantumRenderer-0 e 236/140/132 com Marlin. DensityRaster: 0/0/1.
  Nenhuma pausa GC registrada sobrepôs essas três fases.
- D3D/PixelWriter: 264/147/149 amostras; 246/144/146 na QuantumRenderer-0,
  225/135/129 com Marlin; DensityRaster 0/0/0. Primeira fase sem pausa GC
  registrada; segunda com 12,89 ms de sobreposição registrada.
- Software: 105/69/84 amostras; 89/66/82 na QuantumRenderer-0,
  83/61/79 com Marlin; DensityRaster 5/0/0.

O sinal persistente é trabalho CPU de Marlin/Prism no zoom; preparar as imagens
por outra API não removeu as pausas. Isso reforça investigar desenho/rasterização
vetorial e apresentação, em vez de promover uma simples troca PixelBuffer→PixelWriter.
Não exclui upload/sincronização/driver nem prova qual comando individual é culpado.

Relatórios locais, privados:

- D3D/PixelBuffer: `target/plot-benchmark-20261010-165718-776`.
- D3D/PixelWriter: `target/plot-benchmark-20261010-165756-555`.
- Software: `target/plot-benchmark-20261010-165840-161`.

## Edição de Geometry e regressão

Reensaio isolado de `isolation_bottom`: COMPLETED, SHA original intacto,
120 passos × três repetições, D3D/PixelBuffer, mesmas Scene/escala/tema.
Quatro ciclos de apagar/desfazer por repetição (24 ações no total), todos
concluídos com restauração da identidade da geometria original após undo.

- Pedido→comandos prontos p95: 90,92/95,99/92,81 ms; inclui estabilização e
  preparo assíncrono. Queue p95 0,09 ms; não confundir demora até pronto com
  bloqueio da UI.
- Pulso máximo: 16,82/17,42/17,10 ms; nenhum intervalo >= 50 ms nessas fases.
- Um latest + um overview ao final de cada fase; backing de imagens únicas
  estimado em 3.856.320 bytes, oito publicações por fase. Não é medição de VRAM
  nem prova de ausência de vazamento de memória nativa.
- JFR dessas fases: 7/19/7 amostras; 5/16/5 com DensityRaster, nos workers;
  nenhuma com Marlin. Amostragem pequena, não inferir percentuais precisos.

Relatório: `target/plot-benchmark-20261010-170115-416`.
O ensaio anterior `target/plot-benchmark-20261010-165949-134` concluiu
funcionalmente com original intacto, mas foi descartado da medição: a inspeção
adicional de outro JFR foi iniciada durante a sessão. O reensaio acima rodou
sem essa concorrência. Os testes de pixels/edição sem piscar continuam sendo
a referência de correção; estes tempos não substituem inspeção manual do UI.

Quatro testes novos verificam JFR real: fases concluídas/falhas, intervalo de
pulso e espera registrada, arquivo sem marcadores/inexistente, fases
sobrepostas recusadas, snapshot sem interromper/sobrescrever gravação CLI.
Primeira verificação integral: uma falha em
`TclLiveHostTest.saveProjectRoundTripsAllKindsInNativeAndPythonFormats`,
assertion `save stages leaked`, fora do renderer. O teste e a produção de
salvamento não foram alterados por esta etapa. Reexecução isolada desse teste
junto dos quatro regressores JFR: aprovada. Não concluir que a ocorrência foi
corrigida ou que sua causa é conhecida. Log inicial:
`target/plot-phase-comparison-full-verify-20261010.log`; rechecagem isolada:
`target/plot-phase-save-recheck-20261010.log`.

Segunda execução integral: **1546 registrados, 1530 aprovados, 16 opcionais
ignorados, zero falhas/erros**, exit 0. Inclui 74 testes TclLiveHostTest,
os quatro JFR novos e os regressores de pixels/overview/undo/edição/seleção/Snap.
Log: `target/plot-phase-comparison-full-recheck-20261010.log`.
Os benchmarks visíveis foram executados separadamente; são opt-in e ficam
ignorados no build normal. Parser PowerShell e `git diff --check` aprovados.
**Pendência de confiabilidade:** investigar a ocorrência intermitente de
temporário no teste Tcl; o sucesso da repetição não é uma correção dela.

## Reproduzir

```powershell
.\benchmark-plot.ps1 -Project 'C:\caminho\Projeto.FlatPrj' -ObjectName 'Cobre_Morto_Bottom_cnc' -Steps 120 -Repeats 3 -Jfr
.\benchmark-plot.ps1 -Project 'C:\caminho\Projeto.FlatPrj' -ObjectName 'Cobre_Morto_Bottom_cnc' -Steps 120 -Repeats 3 -PixelWriter -Jfr
.\benchmark-plot.ps1 -Project 'C:\caminho\Projeto.FlatPrj' -ObjectName 'Cobre_Morto_Bottom_cnc' -Steps 120 -Repeats 3 -Software -Jfr
.\benchmark-plot.ps1 -Project 'C:\caminho\Projeto.FlatPrj' -ObjectName 'isolation_bottom' -Steps 120 -Repeats 3 -Jfr
```

Não minimize/interaja com a janela nem execute build/análise de JFR/oráculos
ao mesmo tempo. Conferir COMPLETED, sourceUnchanged, pipeline, escala e corpus
antes de comparar. Limites gerais em [PLOT_RENDER_BENCHMARK.md](PLOT_RENDER_BENCHMARK.md).

## Decisão

Manter o default PixelBuffer + D3D e o fallback Canvas. Não há speedup de um
backend novo demonstrado. Próximo experimento: isolar o caminho vetorial mais
caro e/ou testar apresentação GPU integrada opcional com transporte real,
HiDPI/resize, lifecycle e equivalência de traços/cores/cortes antes de promover.
Nenhum parser, projeto, tolerância geométrica ou operação CAM foi alterado.
