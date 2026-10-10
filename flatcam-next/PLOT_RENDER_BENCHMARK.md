# Baseline e primeira fronteira do renderer

2026-10-10, branch `flatcam-next`, base `780728b5`. Consultar `git log` para o
commit desta entrega; sem push.

## Escopo entregue

O Canvas continua sendo o único backend. Não foram alterados JTS, cálculo CAM,
toolpaths, G-code, formatos do projeto, picking ou políticas de LOD/densidade.

- `PlotCamera`: snapshot imutável da câmera em pixels lógicos, transformação
  mundo/tela e limites com a mesma margem de traço. Insets das réguas ficam
  fora das coordenadas CAM; escala física pertence à apresentação.
- `PlotRenderSnapshot`: versão da geometria **por identidade**, índice, estilo e
  cópia defensiva dos limites. JTS é emprestado somente para leitura, não uma
  cópia profunda nem uma estrutura magicamente imutável. O caller deve substituir
  a versão em vez de mutá-la. O índice deve pertencer à versão exibida.
- `CanvasPlotRenderer`: desenho vetorial separado do viewport, sem manter
  câmera/modelo/cache. Preserva holes, ordem, Multi-Color, alpha, linhas abertas,
  endpoints e simplificação exclusivamente visual dos movimentos subpixel.
  Também atende os desenhos vetoriais dos editores/overlays.
- `PlotAreaView`: ainda coordena eventos, caches, rasterização densa, grade,
  réguas e apresentação. Durante preparo da edição a versão visual anterior
  é explicitamente emprestada ao backend; seleção continua na versão atual.

Não é uma abstração completa de apresentação ou um backend OpenGL. Não foi
criado um módulo/dependência sem consumidor. A extração de raster/apresentação
é um próximo incremento, com ownership e transporte explícitos.

## Ensaio reproduzível

Na pasta `flatcam-next`, sem outra instância/processamento pesado concorrente:

```powershell
# Corpus público determinístico: 20 mil linhas, 60 mil pontos.
.\benchmark-plot.ps1

# Mesmo projeto, isolar um objeto que pode estar salvo com Plot desativado.
.\benchmark-plot.ps1 -Project 'C:\caminho\Projeto.FlatPrj' -ObjectName 'Cobre_Morto_Bottom_cnc' -Jfr
.\benchmark-plot.ps1 -Project 'C:\caminho\Projeto.FlatPrj' -ObjectName 'isolation_bottom' -Jfr

# Controle de software e tema. Não representa outra marca/modelo de GPU.
.\benchmark-plot.ps1 -Software -Theme ICE_LIGHT
```

Aceita `.FlatPrj` e `.fcnproj`; sem ObjectName preserva a visibilidade salva.
O filtro é pelo nome **exato** e ativa somente o objeto, incluindo os dois
sublayers de um CNC. Nome inexistente ou nenhum conteúdo visível reprova o
ensaio, não vira amostra de tela vazia. O layout anterior do usuário não é
alterado: o teste não executa MainApp, salva preferências ou grava a fonte.

Abre **uma janela visível somente do Plot**. Não minimize, redimensione ou
interaja durante o ensaio. Fechar/minimizar ou falhar no preparo reprova a
execução. Para comparação, mantenha resolução, escala, energia/bateria,
driver, tema, layers e corpus iguais. Faça ao menos três repetições.
Não execute benchmark junto do build completo ou de oráculos CAM.

Parâmetros: `-Steps` (240 padrão), `-Repeats` (3), `-Width` (1280), `-Height`
(800), `-TimeoutSeconds` (60), `-Theme`, `-Software` e `-Jfr` opcional.
Largura/altura são da Scene em pixels lógicos, não do framebuffer físico.

O script compila o reactor e executa somente `MainPlotRenderBenchmarkTest`.
O teste é ignorado no build normal; abrir janela exige a opção explícita.
Saída exclusiva em `target/plot-benchmark-<data>`: `report.json`, `maven.log`
e `recording.jfr` quando solicitado. Nada disso deve ir ao Git; o relatório
pode conter nomes privados de objetos. Não há envio/telemetria externa.

## O que as métricas significam

A restauração usa os métodos de produção de MainWindow, incluindo parsing
do G-code e configuração dos centerlines/LOD. A shell é offscreen, sem os
controles completos: o que apresenta quadros é o Plot anexado à janela.

Depois do carregamento/preparo inicial e warm-up não contabilizado, executa:

1. Pan em trajetória determinística e zoom in/out até 8×, agrupados nos pulsos
   pela mesma entrada de câmera do viewport. São pedidos programáticos, não
   eventos nativos de mouse. Há no máximo um input enfileirado por vez.
2. Seleção por bounds dos objetos visíveis; não picking ou seleção da tabela.
3. Havendo Geometry visível, a mais densa é editada com `GeometryEditSession`:
   quatro ciclos de apagar/desfazer por repetição, publicados pelo método de
   atualização do Plot. Não certifica cliques/sidebar/toolbar do editor inteiro.
   Sem Geometry, `editSkipped` fica explícito, sem contar como teste aprovado.

Cada fase contém amostras brutas e p50/p95/p99/max:

| Campo | Significado |
| --- | --- |
| `uiQueueMs` | Pedido no worker até atendimento na thread FX |
| `requestToFirstCommandsMs` | Pedido até emissão do primeiro desenho, possivelmente provisório |
| `requestToReadyCommandsMs` | Pedido até desenho sem preparo assíncrono pendente; inclui estabilização de 60 ms quando necessária |
| `redrawCommandsMs` | Tempo CPU de emissão dos comandos Canvas/overlays na UI |
| `uiPulseIntervalMs` | Intervalos entre callbacks na UI; pausas de 50/100/250 ms, **não FPS** |
| `supersededBeforeReady` | Pedidos trocados por outros antes de ficarem prontos; esperados durante pan/zoom contínuo |
| `coalescedBeforeCommands` | Pedidos substituídos antes do primeiro desenho; não são sucessos de latência zero |

As distribuições de primeiro desenho e pronto têm **contagens diferentes**:
não comparar seus percentis como se fossem pares. O estado mais recente vence;
não esperar cada raster intermediário para simular artificialmente um pan lento.
Seleção e apagar/desfazer aguardam preparo entre ações. As pausas são no worker,
nunca um sleep na thread FX. Timeout/erro não relaxam critérios de sucesso.

Memória por fase: heap usado/reservado/máximo e contagem/tempo acumulados de GC
(calcule diferenças entre before/after). Heap usado oscila com GC; não prova
vazamento, RAM residente, VRAM ou uso nativo. Metadata registra commit, worktree
dirty/clean, runtime, hardware, adaptador/pipeline consultado, escala física e
camadas/contagens. A consulta gráfica pode retornar indisponível; não inventa GPU.

`loadAndInitialReadyMs` separa abertura/previews/publicação/preparo inicial da
interação quente. **Pronto significa comandos atualizados emitidos**, não um
quadro efetivamente apresentado pelo compositor/monitor. Nem estes tempos nem
AnimationTimer certificam FPS GPU. Não mede geração NCC ou salvar projeto.

## Controle de correção e decisão seguinte

### Baseline executada nesta entrega

Ensaios sequenciais, sem o build/oráculos concorrentes, Scene 1280×800 lógicos,
escala 2×2, D3D na Intel Arc, driver 32.0.101.8991. Worktree dirty sobre
`780728b5`; estes são valores observados, não uma comparação antes/depois:

- Público sintético: Steps 24, uma repetição; status COMPLETED e ciclo de
  apagar/desfazer concluído. `target/plot-benchmark-20261010-162053-393`.
- Real `Cobre_Morto_Bottom_cnc`: Steps 120, três repetições e JFR; COMPLETED,
  SHA-256 original preservado. Zoom command p95 caiu de 1,63 para 0,98 ms
  entre a primeira/terceira repetição (aquecimento), mas queue p95 foi
  19,70–23,35 ms e os intervalos de pulso atingiram 335,94 ms na primeira
  repetição; ~48,85 ms nas seguintes. **Não dizer que o Plot custa só 1 ms.**
  `target/plot-benchmark-20261010-162130-529`. Edição foi explicitamente
  ignorada por este filtro conter somente CNC, não Geometry.
- Real `isolation_bottom`: Steps 120, três repetições e JFR; COMPLETED,
  original preservado. Apagar/desfazer teve command p95 0,15–0,24 ms,
  pedido→pronto p95 91,43–95,60 ms e pulso máximo 17,76 ms. O preparo
  assíncrono e estabilização fazem parte desse tempo até pronto.
  `target/plot-benchmark-20261010-162223-711`.
- Controle público SW/ICE_LIGHT: Steps 24, uma repetição; COMPLETED,
  pipeline consultado `com.sun.prism.sw.SWPipeline`, edição concluída.
  `target/plot-benchmark-20261010-162345-150`. Corpus/tempo curto não são
  comparação de desempenho SW contra o projeto CNC real.

JFR do ensaio CNC inteiro: 893 ExecutionSamples, dos quais 637 na
QuantumRenderer-0 e 476 com uma chamada Marlin na pilha. Predominam
`Renderer._endRendering` e `MaskMarlinAlphaConsumer`. São amostras CPU,
incluem abertura/warm-up e não são percentuais de tempo GPU nem atribuição
isolada a uma fase. Sustentam investigar rasterização/apresentação Prism,
em vez de tomar `redrawCommandsMs` como custo total do quadro. O pipeline
D3D ativo não implica que toda tesselação/rasterização seja feita pela GPU.

### Regressores

`CanvasPlotRendererTest` compara todos os pixels contra uma implementação
congelada do desenho anterior (`780728b5`), em 32 combinações de escala,
stroke/filled/Multi-Color/índice, com furos, coleções aninhadas, pontos e caminhos
abertos. Outros testes cobrem câmera MM/IN, ownership dos limites/versões,
semântica dos percentis, pedidos obsoletos e ciclo de vida do observador.
Continuam os testes existentes de seleção, Snap, edição sem piscar e caches.

Build integral desta entrega: 1535 registrados, **1519 aprovados**, 16 opcionais
ignorados, zero falhas/erros (`target/render-boundary-full-verify-20261010.log`).
O benchmark opt-in é um dos ignorados; as quatro execuções visíveis acima foram
separadas e concluíram. Parser PowerShell e `git diff --check` também passaram.

Baseline nesta etapa **não é demonstração de ganho de desempenho** nem comparação
controlada do renderer antigo com outro backend. Primeiro guardar esses relatórios
e usar JFR para separar UI, rasterização e Prism/Quantum. Depois extrair a fronteira
de apresentação/raster e experimentar um backend GPU integrado, opcional e
reversível; manter Canvas como referência e fallback. Nenhuma adoção automática.
