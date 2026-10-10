# Avaliação da arquitetura futura de renderização — FlatCAM FX

## Incremento em 2026-10-10

A proposta histórica abaixo continua sem backend GPU próprio. A primeira
fronteira foi implementada: PlotCamera + PlotRenderSnapshot + CanvasPlotRenderer,
preservando o Canvas, LOD/densidade, picking e CAM. Benchmark visível reproduzível
em `benchmark-plot.ps1`, com fontes somente lidas, ensaios reais de CNC/Geometry,
controle software e JFR. Escopo, métricas, resultados e limitações em
[PLOT_RENDER_BENCHMARK.md](PLOT_RENDER_BENCHMARK.md).

As observações antigas sobre retenção de `DenseRenderer.latest` abaixo são
históricas: o código atual remove chaves em suspend/forget, limpa mapas em clear
e remove tarefas canceladas da fila. Não reaplicar a correção como se ainda
estivesse pendente. A camada de raster/apresentação ainda fica em PlotAreaView;
extraí-la com ownership explícito é o próximo incremento antes do spike GPU.

No baseline CNC, comandos Canvas rápidos coexistem com pausas na UI e amostras
JFR no rasterizador Marlin da QuantumRenderer. Medir a apresentação real continua
pendente; não confundir os novos percentis com FPS GPU ou ganho já comprovado.

Data: 2026-10-02. Base de código examinada: `676c7516`.

Status: proposta avaliada, favorável à experimentação incremental. **Não é uma decisão
de substituir o viewport atual, nem uma implementação já entregue.** Este registro
complementa [o contexto arquitetural original](../CONTEXTO_FLATCAM_FX.md), sem revogar
suas exigências de profiling, equivalência e migração incremental.

Origem: texto enviado pelo usuário sobre JavaFX para a interface, Java para o núcleo,
LWJGL/OpenGL ou Vulkan para o viewport, memória compacta/off-heap e uso nativo pontual.
As conclusões abaixo distinguem inspeção do código, documentação externa e recomendações.
Não foram executados benchmarks ou um protótipo GPU nesta avaliação.

## 1. Parecer

A direção é tecnicamente adequada para um CAM desktop: preservar a aplicação em Java,
separar o desenho massivo dos controles e manter buffers gráficos persistentes. Java
pode acessar APIs gráficas nativas; reescrever o aplicativo em C++ ou Rust não é um
pré-requisito para usar a GPU. LWJGL fornece bindings, não um renderer CAD pronto.
[Referência: APIs do LWJGL](https://javadoc.lwjgl.org/).

Recomendação: **investigar primeiro um backend OpenGL moderno, com o Canvas atual como
referência e reserva**, mas decidir sua adoção somente depois de medir a integração com
JavaFX e a apresentação real dos quadros. Vulkan fica como alternativa posterior, não
como dependência da primeira melhoria. Código nativo para operações geométricas é uma
avaliação independente: acelerar o desenho não acelera automaticamente NCC, Isolation,
parsing ou operações JTS.

O ganho esperado vem de menos trabalho por interação, dados compactos, reutilização de
buffers e controle das filas. Não há evidência nesta avaliação para prometer uma taxa
de quadros ou um multiplicador de desempenho.

## 2. O que já existe no FX

Inspeção dos arquivos locais, não novas medições de desempenho:

| Área | Estado atual | Consequência para a proposta |
| --- | --- | --- |
| Separação do núcleo | `flatcam-application` e `flatcam-cam` não dependem de JavaFX | Manter; não reescrever parsers/CAM para trocar o desenho |
| Viewport | `PlotAreaView` usa poucos Canvas, não um Node por segmento | A proposta não corrige uma árvore de milhões de Nodes; o problema é outro |
| Visibilidade | `PlotDrawableIndex` usa STRtree para camadas grandes e preserva ordem de desenho | Reaproveitar o conceito e os IDs; adaptar o índice ao snapshot gráfico |
| LOD | Linhas centrais de CNC e rasterização por densidade | Já há degradação visual controlada; não começar isso do zero |
| Trabalho em fundo | `DenseRenderer` rasteriza depois de 60 ms de estabilização, cancela vistas antigas e reutiliza o quadro anterior | Preservar a política “a vista mais recente vence” no backend novo |
| Paralelismo | `JobExecutor` tem pool fixo; `DensityRaster` também usa faixas paralelas | Coordenar os orçamentos; pools independentes podem competir pela CPU |
| Interação | Pan/zoom são agrupados no próximo pulso; o timer de interação para após desenhar | O viewport não possui um loop contínuo de desenho em repouso |
| Preferência de GPU | Launcher C++ exporta pistas NVIDIA/AMD e configura Prism D3D/SW | É preferência de driver, não seleção explícita da GPU nem renderer nativo próprio |
| Medição | `[UI-FLUIDITY]`, `[PLOT-PROFILE]` e scripts comparativos | São a base inicial, mas não medem o quadro apresentado pela GPU |

Arquivos: [PlotAreaView](flatcam-fx/src/main/java/org/flatcam/fx/PlotAreaView.java),
[PlotDrawableIndex](flatcam-fx/src/main/java/org/flatcam/fx/PlotDrawableIndex.java),
[DenseRenderer](flatcam-fx/src/main/java/org/flatcam/fx/DenseRenderer.java),
[DensityRaster](flatcam-fx/src/main/java/org/flatcam/fx/DensityRaster.java),
[JobExecutor](flatcam-application/src/main/java/org/flatcam/app/job/JobExecutor.java).
Diagnóstico existente: [PLOT_PERFORMANCE.md](PLOT_PERFORMANCE.md) e [NATIVE_GPU.md](NATIVE_GPU.md).

Um candidato concreto à revisão de memória: `DenseRenderer.forget()` mantém a chave em
`latest`, e `clear()` atualiza suas entradas sem removê-las. Essa retenção merece um teste
de ciclo de vida antes de ampliar caches. É uma observação estática do código nesta tarefa,
não uma medição nova de crescimento do processo. Também verificar a fila de tarefas
agendadas: descartar resultados antigos não equivale a limitar o número de pedidos enfileirados.

## 3. Ajustes necessários na proposta

### 3.1 Integração JavaFX/GPU é o maior risco inicial

LWJGL/GLFW não transforma automaticamente uma janela OpenGL em um Node JavaFX. Será
necessária uma estratégia de superfície, composição, eventos e sincronização. Um teste
rápido numa janela separada mede o renderer, mas **não prova** a solução integrada.

| Estratégia de apresentação | Utilidade | Riscos a medir |
| --- | --- | --- |
| FBO OpenGL → leitura para RAM → PixelBuffer/ImageView | Protótipo de correção e integração por imagem | Readback, sincronização e upload para o compositor podem anular o ganho |
| Textura compartilhada com a composição JavaFX | Candidata para evitar a passagem dos quadros pela RAM | Dependência de GPU, driver, plataforma, versão do JavaFX e biblioteca de interop |
| Superfície/janela nativa associada à área do viewport | Permite apresentação própria | Foco, popups sobrepostos, recorte, resize, HiDPI, abas e ordem das janelas |

`PixelBuffer` permite que `WritableImage` use o buffer fornecido sem uma cópia adicional
dos pixels para essa imagem. Isso **não significa** compartilhar uma textura OpenGL ou
eliminar o percurso GPU → RAM → GPU. Sua atualização é coordenada na thread JavaFX.
[Referência: PixelBuffer no JavaFX 25](https://openjfx.io/javadoc/25/javafx.graphics/javafx/scene/image/PixelBuffer.html).

Como estimativa aritmética, um quadro RGBA de 3840 × 2160 tem aproximadamente 33,2 MB;
60 quadros/s representam aproximadamente 1,99 GB/s de pixels **em um único sentido**,
antes de cópias extras e sincronização. Não é um benchmark nem prova de inviabilidade,
mas explica por que testar somente os draw calls é insuficiente.

DriftFX é um candidato a investigar, não uma dependência escolhida. O projeto documenta
interop de texturas com JavaFX, mas também oferece transferência por memória principal.
É preciso validar a modalidade realmente utilizada, JavaFX 25.0.4, os drivers e notebooks
híbridos; não presumir zero-copy ou compatibilidade só pelo nome da biblioteca.
[Referência: DriftFX](https://github.com/eclipse-efx/efxclipse-drift).

### 3.2 GPU mais capaz não é sinônimo de GPU dedicada

Separar: GPU disponível, GPU do contexto de renderização, GPU de composição/apresentação
e política do sistema operacional. Uma dedicada pode perder o ganho quando precisa
transferir quadros para uma integrada que apresenta a janela. Uma integrada recente
também pode atender melhor a este workload que uma dedicada antiga.

No OpenGL, a escolha depende do contexto e dos mecanismos da plataforma/driver; criar
um renderer LWJGL não garante uma enumeração portável para forçar o melhor adaptador.
Vulkan permite enumerar dispositivos físicos, mas ainda exige verificar recursos,
filas e compatibilidade com a superfície de apresentação.
[Referência: dispositivos e filas Vulkan](https://docs.vulkan.org/spec/latest/chapters/devsandqueues.html).

Política futura proposta: Auto, preferência por desempenho, economia e escolha manual
quando suportada. Auto deve combinar capacidades com medições representativas e registrar
o adaptador realmente usado. Manter fallback operacional e sua causa no diagnóstico.
Não chamar um backend de “hardware” só porque uma API gráfica inicializou: identificar
também implementações de software. Não mudar de adaptador no meio de uma operação sem
um procedimento de recriação de recursos.

### 3.3 Não confundir comandos Canvas com draw calls da GPU

O Canvas já participa do pipeline Prism. Uma chamada Java de desenho não corresponde
necessariamente a uma chamada individual ao driver: há processamento e agrupamento
internos. A oportunidade do backend próprio é controlar os dados, os lotes e sua
reutilização, evitando percorrer/reconstruir a geometria inteira em cada pan ou zoom.

Preparar buffers uma vez por versão da camada. Nas mudanças de câmera, atualizar somente
transformações e a lista de lotes visíveis. Agrupar por estado/material sem perder a
ordem necessária para transparência e sobreposição. Instancing é adequado a pads/furos
repetidos; não substitui a malha de polígonos arbitrários.

### 3.4 CPU e memória precisam de limites, não de ocupação máxima

`availableProcessors()` informa processadores disponíveis à JVM, não uma identificação
portável de P-Cores/E-Cores. Reservar um ou dois workers é uma heurística inicial, não
uma regra universal. Medir concorrência total entre CAM, tesselação, rasterização,
exportação e coleta de lixo; evitar pools aninhados saturando todos os núcleos.

GPU buffers podem exigir memória nativa, mas colocar o modelo inteiro off-heap não é
automaticamente mais rápido. Primeiro medir alocações, cópias, retenção e GC. Depois
definir orçamento conjunto para heap, buffers nativos, staging, VRAM, imagens, índices
e undo. Não dimensionar o cache apenas por RAM instalada ou VRAM anunciada: considerar
pressão de memória, outros processos, reserva e configurações do usuário.

A FFM API já é final desde o JDK 22. `MemorySegment`/`Arena` permitem interoperabilidade
e memória com ciclo de vida explícito; isso não converte automaticamente estruturas JTS
nem elimina bugs de ponteiros em código nativo. A compatibilidade de thread/lifetime de
cada arena precisa fazer parte do contrato.
[Referências: JEP 454](https://openjdk.org/jeps/454) e [FFM no JDK 25](https://docs.oracle.com/en/java/javase/25/core/foreign-function-and-memory-api.html).

### 3.5 Refresh do monitor não garante FPS apresentado

Desenhar sob demanda em repouso; durante interação, limitar a fila e apresentar o estado
mais recente. Oferecer limite de quadros e perfil econômico. A frequência do monitor é
uma referência/teto, não obrigação de produzir 240 FPS em qualquer projeto. A composição
JavaFX, o vsync, o driver e a transferência entre GPUs também limitam a apresentação.
Não usar a quantidade de callbacks `AnimationTimer` como prova de FPS real.

O trecho sobre Metal no JavaFX 27 está documentado oficialmente, mas o projeto examinado
usa JavaFX **25.0.4**, e Metal/macOS não resolve o notebook Windows em discussão. Uma
atualização de runtime deve ser avaliada separadamente, não embutida na troca de renderer.
[Referência: release notes do JavaFX 27](https://docs.oracle.com/en/java/java-components/javafx/27/release-notes/).

## 4. Arquitetura candidata

```text
JavaFX: comandos, ferramentas, temas, eventos, overlays e acessibilidade
    ↓ alterações de projeto/câmera/seleção
Java CAM + JTS: dados canônicos e resultados de usinagem
    ↓ snapshot versionado, sem modificar o modelo durante a leitura
Preparação em workers: índice, tesselação, lotes e buffers compactos
    ↓ fila limitada; versões antigas descartadas
RenderBackend + estratégia explícita de apresentação
    ├─ Canvas atual: referência e fallback
    └─ OpenGL experimental: buffers persistentes, shaders e instancing
         └─ Vulkan: alternativa futura, ainda sem compromisso de implementação
```

Não criar todos os módulos da arquitetura-alvo antecipadamente. Primeiro extrair uma
fronteira pequena no código atual; separar `flatcam-renderer` quando houver um consumidor
e uma implementação que justifiquem a fronteira.

Contratos mínimos a experimentar:

- Snapshot com versão de projeto/camada, IDs estáveis e ownership definido. JTS é o
  dado CAM; a malha de exibição é um produto derivado, descartável.
- Estado de câmera separado dos buffers; tamanho físico do framebuffer separado de
  coordenadas lógicas da interface, com conversão HiDPI e unidade mm/in explícita.
- Upload/liberação por camada, atualização parcial, render por demanda e descarte de
  versões antigas. Evitar uma API reduzida a `draw()` que esconda todas as cópias.
- Capacidades e diagnóstico: backend, adaptador, caminho de apresentação, bytes em
  cada cache, tempos de upload/render/apresentação e motivo de fallback.
- Lifecycle: resize, ocultar/minimizar, trocar tema, remover camada, fechar projeto,
  perder contexto/dispositivo e encerrar a aplicação devem liberar recursos com segurança.

A thread JavaFX fica com tarefas curtas. Workers preparam snapshots/malhas. A thread
que possui o contexto gráfico faz upload/desenho e libera seus recursos; respeitar
também as restrições de thread da biblioteca de janelas. Transferência de buffers
exige ownership explícito: nunca sobrescrever/liberar dados ainda consumidos pelo
renderer ou pelo compositor. [Referência: contextos GLFW](https://www.glfw.org/docs/latest/context_guide.html).

## 5. Invariantes de correção CAD/CAM

- LOD, shaders e simplificações afetam somente a exibição, nunca o toolpath ou G-code.
- Manter coordenadas canônicas com a precisão do modelo. Para buffers float, avaliar
  origem local/rebase para evitar perda de precisão em coordenadas grandes e zoom extremo.
- Preservar furos, ilhas, contornos internos, aperturas macro e composição Gerber já
  resolvida pelo núcleo. Não preencher polígonos com furos como uma simples lista de vértices.
- Validar espessuras de linha, junções, finais de traço e antialiasing. Não pressupor
  suporte uniforme a linhas largas do OpenGL; considerar geometria/shader de traços.
- Validar cores, alpha premultiplicado, ordem de camadas, overlays, tracejados e temas.
- Picking GPU é opcional. Começar pelo índice/seleção CPU com IDs estáveis; se acrescentar
  picking por buffer, testar atraso, resultados de versões antigas e readback.
- Raster provisório durante interação não pode ser a fonte de seleção ou medição exata.
- Compare saídas CAM antes/depois; uma imagem parecida não prova equivalência geométrica.

Compute shaders, tesselação GPU e indirect drawing são opções posteriores, condicionadas
às capacidades e a gargalos medidos. Ray tracing não é prioridade para este viewport 2D.
Nenhum requisito inicial deve depender de CUDA, de uma marca de GPU ou de uma RTX.

Clipper2/C++ ou Rust via FFM/JNI só entram num hotspot CPU comprovado, atrás de um contrato
geométrico próprio. A biblioteca dispõe de funções exportadas para acesso por outras
linguagens; definir ABI, alocação/liberação, erros, unidades, regras de preenchimento e
tolerâncias. Fazer benchmarks incluindo conversão/cópias e testes diferenciais com JTS;
não presumir equivalência automática entre motores.
[Referência: exportação do Clipper2](https://angusj.com/clipper2/Docs/Units/Clipper.Export/_Body.htm).

## 6. Sequência de implementação sugerida

1. **Baseline reproduzível.** Usar os scripts atuais e JFR; identificar se o limite é
   CPU na UI, rasterização, memória, Prism/driver ou apresentação. Revisar lifecycle
   dos caches/filas antes de introduzir novas cópias nativas.
2. **Fronteira pequena.** Separar câmera, snapshot/camadas e apresentação, mantendo
   Canvas e todos os editores funcionando. Não alterar geometria CAM nesta fase.
3. **Spike OpenGL.** Começar por linhas de Geometry/CNC e pads repetidos. Medir janela
   isolada para diagnosticar o desenho, e superfície integrada para avaliar o produto.
   Comparar transporte por imagem e interop quando viável; testar integrada/dedicada.
4. **Cobertura e segurança.** Polígonos com furos, overlays, seleção, HiDPI, temas,
   capturas/exportação, cancelamento, recuperação de contexto e fallback. Renderer
   experimental deve ser selecionável e reversível, sem mudar o formato do projeto.
5. **Decisão registrada.** Promover a backend padrão somente com ganho consistente
   de latência/tempo de quadro e ausência de regressões nos cenários acordados.
   Se o custo de integração vencer o benefício, manter Canvas e aplicar os ganhos
   de estruturas/caches no caminho atual.
6. **Otimizações seguintes.** HardwareProfiler adaptativo, orçamento global de recursos,
   chunks/tiles e LOD por erro visual. Avaliar Vulkan e motor geométrico nativo em
   decisões independentes, com métricas e testes próprios.

Não existe compromisso de prazo nesta avaliação. A superfície integrada é uma incerteza
maior que o exemplo de desenhar alguns segmentos numa janela OpenGL.

## 7. Benchmark e critérios para decidir

Corpus inicial: o `.FlatPrj` real já autorizado pelo usuário
(`Project_20260917_160328.FlatPrj`), Gerbers preenchidos e Geometry/CNC produzidas por
Isolation, NCC e Paint. Acrescentar casos sintéticos de poucos e muitos segmentos,
polígonos com furos, aperturas macro, transparência, coordenadas grandes e zoom extremo.
O exemplo i5/GTX 1650/24 GB do texto não substitui inventário medido do computador.

Registrar commit, JDK/JavaFX, backend e transporte, CPU, adaptadores efetivamente usados,
driver, RAM/VRAM, resolução física/escala, energia/bateria, camadas e sequência de ações.
Manter o mesmo corpus e repetir execuções; separar preparação inicial de interação
com cache quente. Incluir sessão longa com abrir/fechar projeto e editar/desfazer.

Métricas separadas:

- Parsing, restauração do projeto, tesselação, uploads e primeira imagem útil/exata.
- Latência de entrada/resposta e tempo de quadro apresentado, p50/p95/p99 e pausas.
- Tempos GPU por instrumentação apropriada; FPS apresentado por observação/ferramenta
  de apresentação. Nenhum desses é inferido apenas de `redraw()` ou callbacks.
- Heap, memória nativa/residente, caches gráficos, crescimento após ciclos repetidos,
  alocações/GC, concorrência e cancelamento.
- Trabalho/consumo em repouso e sob interação; estabilidade em integrada, dedicada,
  SW, drivers não ideais e mudanças de tamanho/escala da janela.

Metas iniciais propostas, ainda não resultados: interação abaixo de aproximadamente
50 ms no hardware/corpus acordados, pan/zoom próximos de 60 FPS num caso representativo,
memória estabilizada após ciclos de edição, ausência de filas crescentes e nenhuma
mudança das saídas CAM. Monitores mais rápidos são um teste adicional, não garantia.

**Próximo passo recomendado:** baseline + spike de apresentação OpenGL integrada,
mantendo o renderer atual. Não começar por Vulkan, substituição do JTS ou migração
generalizada para off-heap/C++/Rust.
