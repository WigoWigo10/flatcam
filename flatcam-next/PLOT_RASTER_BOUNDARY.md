# Fronteira de rasterização e apresentação Canvas

2026-10-10, branch `flatcam-next`, base `a8116d0f`. Incremento ainda sem commit.

## Responsabilidades e ownership

- `PlotAreaView`: eventos, câmera, ordem dos objetos, índices, LOD, overlays e
  readiness global. Não mantém mais os mapas de imagens/overviews nem cria
  imagens a partir de pixels. Decide quais bindings ainda podem publicar.
- `PlotDensityRenderer`: densidade/histerese, pedidos do worker, caches e escolha
  entre frame atual, overview e preview provisório. Estado e imagens pertencem
  à thread FX; mutações fora dela são recusadas. Retém no máximo latest + overview
  por binding, como antes (não é um orçamento global de RAM/VRAM).
- `DenseRenderer` / `DensityRaster`: cálculo CPU em background, estabilização de
  60 ms, um pedido pendente por binding e descarte de versões obsoletas. Pixels
  ARGB_PRE publicados são somente leitura e nunca reciclados pelo worker.
- `CanvasRasterPresenter`: conversão PixelBuffer ou PixelWriter e comandos de
  desenho/clipping/smoothing. Não mantém fila, modelo ou cache. Ainda utiliza
  Canvas/Prism; não há interop de textura nem backend OpenGL nesta entrega.

O vetor permanece em `CanvasPlotRenderer`/`PlotRenderSnapshot`. Os critérios
de densidade/LOD, ordem, cores, precisão geométrica e CAM não foram alterados.
Rasters e previews jamais são fonte para picking, seleção, edição ou G-code.

## Regras de publicação e ciclo de vida

- `CURRENT`: reutiliza o frame da câmera/versão/tinta atual; nearest-neighbour
  preserva a cobertura 1:1. Um hit no overview cancela pedidos intermediários.
- `PREVIEW`: a imagem anterior é transformada e recortada enquanto chega a nova.
  A câmera inalterada durante edição não acrescenta blur por smoothing.
- `PREPARING`: primeira imagem ainda ausente, sem voltar à varredura vetorial
  maciça que bloquearia a UI. Readiness global continua acompanhando o preparo.
- `VECTOR`: mantém o caminho vetorial e suspende o worker, mas conserva o cache
  para retornar do zoom vetorial sem esperar uma rasterização desnecessária.

`invalidate` cancela versões antigas mas preserva pixels somente como preview
de edição. `forget` remove o binding e suas imagens. `clear` cancela o projeto e
descarta caches/scratch síncrono. `close` é idempotente, encerra o worker e recusa
publicações tardias. Um frame não aceito pelo viewport é descartado também no
worker, sem criar uma imagem UI.

O worker libera scratch ao limpar/fechar. Se estiver dentro do escopo de render,
a liberação é adiada até seu `finally`: nunca trocar/liberar um array enquanto
o owner ainda o usa. Uma publicação UI já enfileirada pode manter seus pixels
vivos até ser atendida e descartada pela verificação de versão; não há readback
ou buffer nativo liberado prematuramente.

PixelBuffer envolve o array final do worker. A imagem é read-only para o
consumidor e mantém a referência aos pixels. PixelWriter assíncrono cria uma
**nova** imagem, pois um overview pode ainda apontar para a anterior. Só o modo
síncrono reutiliza imagem e scratch, copiando os pixels e sem publicar aliases
de overview. Não presumir zero-copy para a GPU ou medir upload com esse tempo.

## Métricas

`PlotAreaView.rasterStats()` fornece um snapshot; o benchmark agora registra
`rasterBefore` e `rasterAfter` em cada fase:

- `latestFrames`/`overviewFrames`/`denseBindings` e `queuedRequests` (fila do
  worker, não backlog do compositor/GPU nem callbacks já entregues à UI).
- `estimatedImageBytes`: width × height × 4 de imagens únicas, contando apenas
  uma vez quando latest/overview compartilham a imagem. É estimativa do backing
  UI, não uso total do processo/Prism/VRAM; PixelWriter ainda pode manter um
  array separado no worker.
- `syncScratchBytes` e `workerScratchBytes`: capacidades atuais dos buffers CPU;
  o snapshot do worker é observacional, não reserva/budget de memória.
- `publicationsCumulative`, `imagePrepareMsCumulative` e
  `presentationCommandsMsCumulative`. Usar diferenças entre before/after;
  tempos só são coletados com profiling/benchmark ligado. Preparar imagem e
  emitir drawImage não medem upload nem apresentação efetiva na GPU.

Heap/GC e métricas de comandos/filas/pulsos anteriores continuam disponíveis.
Comandos e limites do ensaio em [PLOT_RENDER_BENCHMARK.md](PLOT_RENDER_BENCHMARK.md).

## Verificação e baseline desta extração

Testes dedicados:

- Pixels do presenter atual/provisório contra a implementação anterior, com
  zoom/translação inteiros e fracionários, alpha, clipping e estado GC restaurado.
- Publicação por PixelBuffer e PixelWriter não sobrescreve overview; scratch
  síncrono é copiado e pode ser reutilizado apenas nesse caminho.
- Cache hit ao retornar de zoom vetorial; invalidate durante edição; troca de
  tinta não exibe pixels na cor antiga; forget/clear/close e rejeição pelo host.
- Estimativa conta aliases uma vez e mantém no máximo dois frames por binding.
- Buffers liberados em clear/close, incluindo teste controlado com o worker
  ainda dentro de seu escopo; pixels finais anteriores permanecem intactos.
- Permanecem os testes visuais de edição sem piscar, overview amplo após crop,
  undo, tema, remoção sem ghost, seleção/Snap e vetores.

Ensaios visíveis sequenciais no mesmo projeto, Steps 120, três repetições, JFR,
Scene 1280×800 lógicos, escala 2×2, D3D/Intel Arc/driver 32.0.101.8991:

- `isolation_bottom`: COMPLETED, hash original preservado. Delete/undo pronto
  p95 89,09–94,59 ms, pulso máximo ~17,78 ms. Imagens estimadas 3.856.320 bytes
  e worker scratch 1.928.160 bytes ao fim dessas fases. Relatório privado em
  `target/plot-benchmark-20261010-163658-531`.
- `Cobre_Morto_Bottom_cnc`: COMPLETED, original preservado; edição explicitamente
  não contada pelo filtro só CNC. Zoom command p95 0,98–1,42 ms, queue p95
  19,02–24,58 ms. Pulso máximo 432,58 ms na primeira repetição; 48,46/64,08 ms
  nas seguintes. `target/plot-benchmark-20261010-163755-133`.
- Público sintético SW/ICE_LIGHT, 24 passos/uma repetição: COMPLETED, incluindo
  delete/undo, fila zero ao fim das fases, pipeline SW consultado.
  `target/plot-benchmark-20261010-163846-775`.

**Não houve ganho de velocidade demonstrado.** As pausas do CNC continuam;
variabilidade entre sessões/aquecimento não é prova de melhoria ou regressão.
Esta entrega isola responsabilidades, ownership e diagnóstico, sem substituir
o rasterizador/Prism. Não medir benchmarks junto de build/oráculos concorrentes.
Dados privados e JFR permanecem ignorados em target.

Build integral: **1542 registrados, 1526 aprovados, 16 opcionais ignorados**,
zero falhas/erros. O benchmark opt-in é um dos opcionais; os três reensaios
visíveis acima foram executados separadamente e concluíram. Log de verificação:
`target/raster-boundary-full-verify-20261010.log`. `git diff --check` passou.

## Próximo incremento

Spike de apresentação GPU integrada, opcional/reversível, começando por linhas
Geometry/CNC e usando este Canvas como referência/fallback. Validar transporte
real, resize/HiDPI, ciclo de vida, temas e sobreposição dos controles; só promover
com equivalência e ganho medidos. Interface, parsers, CAM e projetos continuam
em Java/JavaFX. Nenhuma biblioteca gráfica ou troca automática foi adicionada.
