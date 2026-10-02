# FlatCAM FX — contexto, progresso e próximos passos

Este é o documento operacional de continuidade do **FlatCAM FX**. Ele foi
escrito para que uma nova sessão de IA (Codex, Claude ou equivalente) consiga
entender o estado real do projeto, tomar decisões compatíveis com as já feitas
e continuar a migração sem recomeçar a investigação.

> Atualizado em **2026-10-02**. Desde a última revisão completa deste arquivo (2026-09-27) o projeto
> migrou para **Java 25 + JavaFX 25.0.4**, portou **todas as 24 ferramentas do menu Ferramentas do Python**
> (nenhuma resta), ganhou Conversion/Join Objects parciais,
> barras de ferramentas com paridade, e um **LOD por densidade assíncrono** no Plot Area para geometrias
> muito densas. O estado detalhado de cada entrega está em ordem cronológica na seção 9.1; a fila atual,
> na seção 9.0 e nas entregas de 2026-10-02 ao final do documento. A suíte tem **739 testes**
> (491 `flatcam-cam`, 110 `flatcam-application`, 138 `flatcam-fx`), sem falhas; 11 ficam ignorados
> porque dependem de fixtures/artefatos opcionais indicados por propriedades ou
> variável de ambiente (por exemplo `FLATCAM_PARITY_PROJECT`).
> Antes de trabalhar, confirme o `HEAD`, o `git status` e os testes: este arquivo é um ponto de passagem,
> não substitui o código como fonte final da verdade. Os trechos mais antigos das seções 4, 5 e 8
> descrevem fases anteriores e ficam como histórico; onde divergirem das seções 6 e 9, valem as seções 6 e 9.

## 1. Objetivo do projeto

O FlatCAM FX é uma reimplementação gradual do FlatCAM Python/PyQt5 em
**Java 25 + JavaFX**, mantida no mesmo repositório enquanto ainda não substitui
o aplicativo legado.

A meta solicitada é obter paridade tão completa quanto for razoável com o
FlatCAM Python, preservando seus fluxos e formatos, mas melhorando arquitetura,
responsividade, feedback de progresso e apresentação quando isso não quebrar a
expectativa do usuário.

Paridade significa reproduzir o comportamento observável e os resultados CAM;
não significa traduzir o Python linha por linha. O aplicativo Python é o
oráculo funcional. O código Java deve continuar idiomático, testável e sem
dependências da interface no núcleo CAM.

## 2. Documentos que devem ser lidos

Leia estes arquivos antes de uma mudança ampla:

1. `CONTEXTO_E_PROGRESSO.md` — este estado operacional e a fila atual.
2. `../CONTEXTO_FLATCAM_FX.md` — estratégia, princípios arquiteturais e plano
   de migração original. Algumas fases descritas ali já foram superadas.
3. `UI_INVENTORY.md` — inventário detalhado da interface Python usado como
   referência de paridade.
4. `README.md` — requisitos e comandos confiáveis de build/teste/execução.

Quando um documento antigo disser que o JavaFX ainda é apenas um esqueleto,
prefira o estado descrito aqui e confirme no código.

## 3. Estado técnico verificável

### Stack e módulos

- Java 25 (LTS).
- Maven Wrapper; Maven global não é necessário.
- JavaFX 25.0.4.
- Quatro temas próprios sobre JavaFX Modena: original branco/preto e gelo branco/preto; sem AtlantaFX.
- JTS 1.20.0 para geometria.
- ZXing `core` 3.5.4 (Apache 2.0) só para gerar a matriz do QRCode Tool.
- JUnit 5 para testes.
- `JAVA_HOME` precisa apontar para um JDK 25 (o `release` do compilador é 25 e o JavaFX 25 exige Java 23+).
  Um terminal/VS Code aberto antes de mudar a variável continua com o valor antigo: reabra-o.
- Launcher nativo opcional (`native-launcher/FlatCAMFX.cpp`, `run-native.cmd`): cria a JVM dentro de um `.exe`
  próprio que exporta os sinais `NvOptimusEnablement`/`AmdPowerXpressRequestHighPerformance`. Exige o `g++` do
  MSYS2 UCRT64 (`pacman -S mingw-w64-ucrt-x86_64-gcc`); sem ele, `run.cmd` usa o `javafx:run` do Maven. Ver
  `NATIVE_GPU.md`.

O reactor Maven contém três módulos:

| Módulo | Responsabilidade atual | Regra de dependência |
| --- | --- | --- |
| `flatcam-application` | modelo leve de projeto, jobs, progresso e cancelamento | não depende de JavaFX; depende de `flatcam-cam` desde a persistência embutida de Gerber/Excellon (seção 9.3) - `ProjectFile` guarda `GerberImage`/`ExcellonImage` de verdade, não paths |
| `flatcam-cam` | parsing, geometria, operações CAM e geração de G-code | não depende de JavaFX nem de `flatcam-application` |
| `flatcam-fx` | janela, árvore do projeto, painéis de ferramentas, temas e renderização | depende dos dois módulos anteriores |

Não existem ainda módulos separados de renderer, CLI, scheduler, compat ou
native. Só devem ser criados quando houver uma fronteira real que justifique a
separação.

### Verificação mais recente

Em 2026-10-02, `mvnw.cmd -q install` completo (três módulos com JDK 25.0.4.1 e JavaFX 25.0.4) passa com
**739 testes registrados**: 491 em `flatcam-cam`, 110 em `flatcam-application` e 138 em `flatcam-fx`;
728 executados, 0 falhas, 0 erros e
11 ignorados (fixtures opcionais de `PythonProjectWriterTest`, `NccPythonParityTest`, `PythonProjectCamSmokeTest`, `PythonProjectIOTest` e `PlotAreaNestedGeometryTest`,
que só rodam com um projeto real do Python indicado por variável de ambiente, como `FLATCAM_PARITY_PROJECT`). `JobExecutorTest`
registra intencionalmente uma `IllegalStateException: boom` ao testar propagação de erro, e um teste de jobs
imprime "Job failed"; esses logs, isoladamente, não representam falha da suíte. O smoke visual anterior
com `javafx:run` no Java 25 passou (20 s sem exceções). No incremento de pós-processadores, controles
de Geometry foram verificados na thread FX sem abrir janela; teste manual da tela completa permanece pendente.

Há testes automatizados de controles JavaFX na thread FX, incluindo persistência CNC e Tools Database,
mas não uma suíte end-to-end completa da janela. Painéis, `MainWindow` e `PlotAreaView` também são validados por
harnesses fora da tela (capturas por `Node.snapshot`, sem janela visível) e pelo usuário no app real.
Vários painéis recentes (2-Sided, Paint, Panelize, Invert, Subtract, Extract Drills, Punch, Etch, Film,
Fiducials, Corner Markers, QRCode, SolderPaste, Align Objects) têm a **lógica** testada, mas o fluxo de
cliques e janelas ainda não foi confirmado por uso manual.

Sempre refaça essas verificações depois de mudanças relevantes; números e resultados podem mudar.

## 4. O que já funciona

### Shell, temas e área de plotagem

- Janela JavaFX com menus, barras de ferramentas, painel lateral, console,
  status e progresso.
- O chrome principal agora expõe os menus e barras de Arquivo, Editar, Exibir,
  Shell e Ferramentas com os ícones PNG originais do Python. Comandos ainda não
  portados aparecem desabilitados; ações já funcionais continuam ligadas aos
  fluxos reais. Os ícones claro/escuro trocam ao vivo com o tema. A barra de
  ferramentas Gerber também aparece dentro do editor, com operações existentes
  ativas e ferramentas futuras desabilitadas. Os comandos dos editores
  Excellon/Geometry estão catalogados em Editar → Ferramentas dos editores,
  desabilitados até esses editores existirem.
- Temas Original (branco/preto) e Gelo (branco/preto), todos próprios e alternáveis no menu Tema.
- Tools Database tem tooltips com parágrafos, opções em linhas separadas, termos em negrito e destaques de unidades
  e integração CAM legíveis nos quatro temas. Adicionar/Copiar/Excluir não têm tooltip redundante; campos desabilitados
  continuam com ajuda no rótulo. Em 2026-10-02: 40 testes direcionados passaram e capturas fora da tela conferiram os
  quatro temas; a suíte completa não foi repetida neste incremento.
- A Plot Area acompanha o tema ativo, inclusive fundo, grade, eixos e textos.
- Canvas com pan, zoom, enquadramento, réguas, grade adaptativa, origem, posição
  e delta do cursor.
- Ordem de desenho por categoria e visibilidade por objeto.
- Plot preenchido ou em contorno, multicolor, cor personalizada, opacidade e
  restauração da cor padrão.

### Árvore do projeto e menus de contexto

- Categorias `Gerbers`, `Excellon`, `Geometry` e `CNC Jobs`.
- Alinhamento compacto semelhante ao legado.
- Ícones de objeto e de ações reutilizados do FlatCAM Python.
- Em tema escuro, ícones predominantemente pretos recebem contorno/halo claro
  para continuarem legíveis.
- Amostras de cor arredondadas nos menus.
- Seleção múltipla e ações contextuais pertinentes ao tipo do objeto.
- Ações de plot, fonte, renomear, copiar, remover, salvar e propriedades já
  existem onde aplicáveis.

### Carregamento e projetos

- Abertura de Gerber e Excellon em background.
- Cancelamento e progresso real, monotônico, calculado sobre o trabalho de
  leitura/parsing em vez de uma animação fictícia.
- Abertura de projeto de forma atômica: o estado anterior não fica parcialmente
  substituído se o carregamento falhar.
- **Formato `.fcnproj` v2 (2026-09-22): Gerber e Excellon embutem sua própria
  geometria**, no mesmo formato que o `.FlatPrj` do Python usa de verdade -
  pesquisado diretamente em `camlib.py`/`app_Main.py` antes de implementar
  (ver seção 9.3). Um objeto Gerber/Excellon salvo por este app pode, em
  princípio, ser aberto por uma instalação real do FlatCAM Python (verificado
  contra o código-fonte Python; **não verificado contra uma instalação
  Python+Shapely rodando de verdade**, que não está disponível neste
  ambiente de desenvolvimento). Detalhes técnicos completos na seção 9.3.
  Geometry agora é persistido em `_java.geometries` (WKT e associação por ferramenta),
  uma extensão do FX não interpretada pelo Python. CNC Job embute o texto G-code em `_java`,
  além do path e nome; na reabertura, reconstrói uma prévia limitada a G0-G3 em XY,
  sem a largura original da ferramenta.

### Gerber

- Parser RS-274X com unidades, formato de coordenadas, modos de coordenada,
  apertures, aperture macros, polaridade, regiões e operações usuais cobertas
  por testes.
- Geometria sólida e geometria `follow`.
- Exibição, tabela de apertures, propriedades e visualização da fonte.
- Operações auxiliares de região não-cobre e bounding box.
- Isolation Routing com parâmetros e resultado em Geometry editável; o G-code é
  gerado depois pelo fluxo Geometry -> CNC Job.
- Cutout Tool com forma, tipo, margens e gaps/bridges; produz Geometry editável,
  que depois gera CNC Job/G-code pelo fluxo Geometry -> CNC Job.
- NCC Tool em primeira fatia funcional.

### Excellon

- Parser, ferramentas, furos e slots.
- Exibição e propriedades/tabela de ferramentas.
- Geração de G-code de furação.

### Geometry e CNC Job

- Objetos Geometry podem ser produzidos por Isolation Routing, Cutout e NCC.
- Geometry pode gerar CNC Job com `safe Z`, profundidade, multi-depth,
  profundidade por passe, feed rate e spindle.
- CNC Job separa e desenha trajetos de viagem e corte.
- Visualização, ativação/desativação e salvamento de G-code.

### Ferramentas adicionais

- Calculadoras de unidades, ferramenta V e galvanoplastia.
- Isolation, Cutout e NCC executam como jobs canceláveis, sem bloquear a thread
  JavaFX.

### Ferramentas do menu Ferramentas já portadas do Python (2026-09-30 a 2026-10-01)

Cada uma segue o Python correspondente (defaults, fluxo, nomes dos objetos) e tem uma entrada detalhada,
com as diferenças deliberadas, na seção 9.1. Lógica em `flatcam-cam` (testada), painel em `flatcam-fx`.

| Ferramenta | Classes principais | Resumo |
| --- | --- | --- |
| 2-Sided | `DoubleSidedToolPanel` | espelha objetos por X/Y e eixo por caixa ou ponto; furos de alinhamento; pré-visualização no plot |
| Align Objects | `AlignObjects` | alinha por 1 ponto (translada) ou 2 (translada e gira), clicando em pads ou furos |
| Calibration | `Calibration` | quatro pontos, G-code de verificação, fatores de escala/inclinação e objetos calibrados |
| Copper Thieving | `CopperThieving` | preenchimento sólido/pontos/quadrados/linhas, robber bar e máscara de galvanoplastia |
| Rules Check | `RulesCheck` | 10 regras de projeto (trilha, cobre, seda, máscara, contorno, anel anular, furos), violações localizáveis no plot |
| Optimal | `MinimumDistance` | menor distância entre os elementos de cobre de um Gerber, pares, locais e demais distâncias |
| Extract Drills | `ExtractDrills` | Gerber → Excellon pelos flashes: fixo, proporcional ou anel anular |
| Cutout, NCC, Isolation, Drilling | (anteriores) | ver seção 5 e 9.7 |
| Paint | `NccGenerator.paint`, `PaintToolPanel` | Standard, Seed, Lines e Combo sobre polígonos |
| Panelize | `Panelize` | grade de cópias de Gerber, Excellon ou Geometry |
| Film | `FilmExporter` | filme positivo/negativo em SVG, PNG ou PDF, com escala, inclinação, espelho e punch |
| SolderPaste | `SolderPaste` | geometria de dispensa por bico e G-code `Paste_1` |
| Subtract | `Subtract` | Gerber ou Geometry menos outro |
| Transform, Calculators | (anteriores) | seção 4 |
| QRCode | `QrCodeMarker` | QR de quadrados de cobre num Gerber (ZXing) |
| Fiducials, Corner Markers | `Fiducials`, `CornerMarkers` | marcas circulares/cruz/xadrez e marcadores de canto, com furos opcionais |
| Punch Gerber | `Punch` | furos nos pads por Excellon ou por tamanho |
| Invert Gerber | `InvertGerber` | inverte cobre e vazio dentro de uma caixa com margem |
| Etch Compensation | `EtchCompensation` | cresce ou encolhe o cobre pela espessura e fator de corrosão |

Todas as ferramentas do menu estão portadas (aparecem no menu, desabilitados).
Conversion e Join Objects: portados Outline→Area, Convert Any→Geo/Gerber/Excellon, Single↔MultiGeo e Join
Gerber/Excellon/Geo.

### Plot Area com geometrias muito densas (2026-10-01)

Camadas de traços (Geometry, caminho central de CNC Job) e os overlays do editor (realce azul e contorno de
referência) com milhares de segmentos visíveis não são mais desenhados como milhares de strokes no `Canvas`:
`DensityRaster` conta a cobertura de área por pixel na CPU (faixas paralelas, antialiasing igual ao vetor) e
`DenseRenderer` faz isso numa thread de fundo, com a imagem anterior movida/escalada enquanto a nova não
chega. Um quadro de pan em 100–500 mil traços passou de ~0,2–1,1 s para 1–3 ms na thread da interface. Detalhes,
medidas e os quatro defeitos de fidelidade corrigidos: `PLOT_PERFORMANCE.md`. `-Dflatcam.plot.density=false`
desliga o modo; `-Dflatcam.plot.density.async=false` volta à rasterização síncrona.

### Transformations

Implementado nesta revisão (2026-09-22), pesquisado diretamente em
`appTools/ToolTransform.py`, `appGUI/ObjectUI.py` (o "mini-painel" comum) e
`app_Main.py` (ações rápidas do menu Options) antes de codificar:

- `org.flatcam.cam.transform.TransformOp` (sealed: `Rotate`, `Scale`, `Skew`,
  `MirrorX`, `MirrorY`, `Offset`) - motor puro de transformação afim via
  `AffineTransformation` do JTS, aplicável tanto a `Geometry` quanto a um
  `Coordinate` isolado (necessário para Excellon, que guarda furos/slots como
  pontos, não só como geometria agregada). `Rotate` usa a convenção
  matemática crua (positivo = anti-horário, igual ao `camlib.Geometry.rotate`
  do Python); as UIs que apresentam "positivo = horário" negam o ângulo antes
  de construir o op, exatamente como o Python faz em seus dois pontos de
  chamada (`obj.rotate(-num, point)`).
- `GerberImage.transformed(op)` / `ExcellonImage.transformed(op)` /
  `ToolGeometry.transformed(op)` - cada tipo de objeto transforma toda a
  geometria que carrega (Gerber: solid + follow + geometria por aperture;
  Excellon: cada furo/slot individualmente, não só o solid agregado;
  Geometry: cada ferramenta do multigeo). Os campos de diâmetro/largura de
  aperture NÃO são recalculados após a transformação (só alimentam a tabela
  de apertures, nenhum cálculo de CAM os lê diretamente) - simplificação
  deliberada de v1, documentada no Javadoc de `GerberImage`.
- **Ações rápidas no menu Opções**: Girar Selecao/Inclinar em X/Inclinar em
  Y/Espelhar em X/Espelhar em Y, com os mesmos ícones do Python
  (`rotate.png`/`skewX.png`/`skewY.png`/`flipx.png`/`flipy.png`, reaproveitados
  via `pom.xml` como os demais ícones legados). Referência sempre "Selection"
  (centro da caixa delimitadora combinada dos objetos selecionados), igual ao
  `app_Main.py`. Sem atalhos de teclado Shift+R/X/Y/bare X/bare Y do Python -
  um acelerador global sem modificador em X/Y sequestraria a digitação normal
  em qualquer campo de texto da aplicação.
- **Ferramenta completa** (`TransformToolPanel`, aberta pelo botão
  "Transformations" do mini-painel, ícone `transform.png`): Reference
  (Origin/Selection/Point - a quarta opção do Python, "Object", fica de fora,
  ver "falta" abaixo), Rotate, Skew X/Y (com Link), Scale X/Y (com Link),
  Flip X/Y, Offset X/Y, Reset Tool (ícone `reset32.png`). Cada botão aplica
  de imediato à seleção atual da árvore, sem passo de "Gerar" - igual ao
  Python. Botões sem ícone (igual ao Python - só Reset e o combo de tipo de
  objeto de referência têm ícone lá).
- **Mini-painel "Transformations"** (`MainWindow.transformationsSection`):
  igual nos três tipos de objeto (Scale uniforme sobre a Origem + Offset
  (dx,dy) + botão que abre a ferramenta completa), replicando o
  `ObjectUI.py` comum do Python (que já era compartilhado entre
  Gerber/Excellon/Geometry, não três painéis distintos).
- CNC Job recusa transformação (`CNC Job nao pode ser transformado`), igual
  ao Python.

Falta (fora de escopo por ora, ver seção 9.2): Buffer (distância/fator) e a
referência "Object" (centro de outro objeto escolhido) - ambos triviais de
adicionar depois reaproveitando a infraestrutura já criada.

## 5. NCC: estado exato da implementação atual

O commit-base `9f6c7463` adicionou a primeira fatia vertical do Non-Copper
Clearing (uma ferramenta). Nesta revisão (2026-09-22) o NCC evoluiu para
**multi-tool com Rest Machining**, seguindo a ordem recomendada na revisão
anterior deste documento. Pesquisa em `appTools/ToolNCC.py` confirmou o
algoritmo exato antes da implementação (ver `NccGenerator`'s class doc para a
citação completa):

- `NccParameters.toolDiameters()` é uma lista ordenada de diâmetros (não mais
  um único `double`). `NccToolSettings` guarda overlap/método/connect/contour/
  copperOffset por ferramenta CLEAR; a margem e o boundary são comuns. Com
  Rest Machining, overlap/método continuam por ferramenta, enquanto
  connect/contour/offset vêm dos controles comuns, como no Python.
- `NccOrder` (`NONE`/`FORWARD`/`REVERSE`) replica o `ncc_order_radio` do
  Python; ignorado quando `restMachining=true`, que sempre processa da maior
  para a menor ferramenta (mesmo comportamento do Python, que desabilita o
  radio nesse caso).
- **Sem Rest Machining**: cada ferramenta limpa a área não-cobre inteira de
  forma independente (mesmo resultado, diâmetros diferentes) - não há
  coordenação entre ferramentas, replicando `gen_clear_area`.
- **Com Rest Machining**: ferramentas processadas da maior para a menor; a
  área restante para a próxima ferramenta é a área anterior menos a pegada
  física (o caminho já limpo, re-inflado pelo próprio raio da ferramenta,
  com um encolhimento de 1e-6 para evitar que ruído de ponto flutuante "coma"
  área que a ferramenta não varreu de fato) de cada ferramenta maior que já
  rodou - replica `gen_clear_area_rest`. Um polígono que uma ferramenta não
  consegue limpar simplesmente permanece disponível para a próxima (menor),
  sem precisar reproduzir a lista `rest_geo` separada do Python.
- `NccResult.toolResults()` guarda a contribuição de cada ferramenta
  (diâmetro + geometria própria + polígonos que falharam), preservando a
  associação ferramenta -> caminho; `geometry()` continua expondo a união de
  todas as ferramentas para plotagem/compatibilidade.
- O objeto Geometry resultante agora carrega `List<ToolGeometry>` (par
  diâmetro+geometria por ferramenta) em vez de um único diâmetro opcional;
  "Geometry -> CNC Job" (`GeometryCncToolPanel`/`GCodeGenerator.generateGeometryCncJob`)
  detecta esse caso e gera **um único G-code** com troca de ferramenta (M0
  opcional + comentário, mesmo padrão já usado no G-code de furação) entre
  seções, em vez de pedir um diâmetro ao usuário - mesma abordagem do
  `mtool_gen_cncjob` do Python (um CNCJob, não um por ferramenta).
- **Boundary** (`NccBoundary`, sealed interface): `Itself` (convex hull do
  próprio Gerber - default, igual antes) ou `ReferenceGerber`/`ReferenceGeometry`
  (objeto de referência já carregado no projeto). Réplica de
  `calculate_bounding_box()`/`ncc_select==2` do Python: para um Gerber de
  referência, o boundary é a interseção dos dois convex hulls (fonte ∩
  referência); para uma Geometry de referência, a forma é usada **como está**,
  sem convex hull (confirmado no código Python - só o caso Gerber tira hull).
  "Area Selection" permite escolher um retângulo com dois cliques ou um
  polígono por vértices no Plot Area (Enter/botão direito conclui, Esc cancela).
  Uma referência Geometry linear é preservada até a aplicação da margem, para
  não perder o contorno antes da operação de buffer.
- **Verificar validade dos diâmetros** (`NccGenerator.minimumCopperClearance`):
  réplica de `find_safe_tooldia_multiprocessing`/`find_optim_mp` do Python -
  calcula a menor distância entre quaisquer duas partes de cobre disjuntas do
  Gerber e informa no console se pelo menos uma ferramenta selecionada é fina
  o bastante para um isolamento completo. Puramente informativo, não bloqueia
  a geração (mesmo comportamento do Python); painel tem um checkbox
  "Verificar validade dos diâmetros", marcado por padrão (mesmo default do
  Python).
- Painel `NccToolPanel` ganhou uma tabela editável de diâmetros
  (adicionar/remover) e os controles "Rest Machining"/"Order". Passou por uma
  rodada de polimento de UI/UX após feedback visual direto do usuário: seções
  com título (FERRAMENTAS/PARAMETROS DE LIMPEZA/MULTI-FERRAMENTA), Enter no
  campo de diâmetro adiciona a ferramenta, botão Remover desabilita sem
  seleção, lista de diâmetros preserva a ordem de entrada (para Order = None),
  tabela com altura dinâmica e
  coluna ocupando 100% da largura (`CONSTRAINED_RESIZE_POLICY`), e tooltips
  explicando Method/Connect/Contour/Copper offset/Rest Machining/Order com
  `showDuration` estendido (`Duration.INDEFINITE`) - o padrão do JavaFX
  esconde tooltips após ~5s mesmo com o mouse parado em cima, cedo demais
  para textos multi-linha. Evite usar `Button.setDefaultButton`/
  `setCancelButton` nos painéis desta app: nenhum dos 4 temas estiliza o
  pseudo-estado `:default` do JavaFX, e o botão fica com a aparência pálida
  do Modena por baixo do tema (foi tentado e revertido nesta mesma revisão).
- A tabela NCC agora distingue `ISO` e `CLEAR` por linha e executa apenas as
  ferramentas selecionadas. `ISO` só aparece para origem Gerber: cria contornos
  próprios, recortados pelo boundary, antes do clearing. Pelo menos uma
  ferramenta `CLEAR` é obrigatória, como no fluxo Python. O maior diâmetro ISO
  define o envelope de cobre a preservar na limpeza; isso evita deixar a
  ferramenta CLEAR invadir uma faixa reservada ao isolamento. O painel oferece
  Climb/Conventional para inverter o sentido do contorno externo ISO; o sentido
  dos anéis internos permanece como gerado, seguindo `generate_envelope()`.

O botão NCC já deve abrir o painel da ferramenta. Se voltar a “não fazer nada”,
primeiro suspeite de snapshots internos desatualizados no repositório Maven
local e execute `install` no reactor completo, conforme a seção de comandos.

Ainda falta para paridade NCC:

- comparação visual dos contornos ISO e resultados NCC com o Python;
- teste manual dos parâmetros por ferramenta e comparação diferencial com
  resultados do Python;
- integração com Tools Database ("Pick from DB");
- comparação diferencial mais ampla com resultados do Python (incl. Rest
  Machining, boundary por referência e "Check validity" num board real).

Fechados nesta revisão: boundary por objeto de referência (Gerber ou
Geometry), seleção retangular de área, ordem manual das ferramentas,
seleção de subconjunto e operação ISO/CLEAR por linha e
validação/sugestão de diâmetro ("Check validity"). Uma Geometry usada como
origem precisa conter área preenchida; contornos puros são rejeitados, como
no Python.

## 6. Matriz honesta de paridade

Os rótulos abaixo são deliberadamente conservadores.

| Área | Estado | Observação principal |
| --- | --- | --- |
| Shell, temas e layout principal | forte/parcial | base utilizável; barra lateral recolhível, barra inferior com coordenadas, snap X/Y, eixos, HUD, A4, console, unidades e atividade; aba básica de Preferências para tema e Plot Area; vários menus ainda não têm fluxo completo |
| Plot 2D e interação | forte/parcial | Canvas com seleção por clique/retângulo, menu contextual e mover/copiar objetos com prévia; snap configurável atua no posicionamento, grade visual pode ser ocultada independentemente do snap, eixos/HUD/A4 alternáveis; faltam estilos avançados da grade e perfilamento para placas enormes |
| Árvore lateral Gerber | forte/parcial | aparência e ações principais implementadas; editor inicial (menu "Editar") |
| Importação Gerber | forte/parcial | boa cobertura do subconjunto real testado; ampliar corpus de compatibilidade |
| Ferramentas Gerber/Geometry | parcial | Isolation tem Follow, Rest Machining, saídas separadas e áreas de exceção; Cutout aceita Gerber ou Geometry preenchida e tem Bridge, Thin, M-Bites e gaps manuais por área, mas não o gesto exato do cursor Python; NCC é multi-tool com Rest Machining, ISO/CLEAR, boundary, validação e leitura de `.FlatDB`; faltam comparação visual com projetos reais e opções avançadas |
| Editor Gerber | funcional, paridade parcial | todos os comandos da paleta têm ação: seleção, desenho, edição de aberturas, operações geométricas e undo/redo; várias ferramentas avançadas usam parâmetros numéricos no painel em vez dos gestos/controles exatos do Python; falta validação manual da interação completa e corpus amplo de Gerbers |
| Importação/plot Excellon | parcial | parser, plot, editor de furos/slots, exportação `.drl` do estado editado, Drilling Tool com Multi-Depth/Dwell/Offset Z e `.FlatDB`; projetos Python importam valores básicos de furação por ferramenta; Milling Tool cria Geometry para furos/slots; faltam opções avançadas e validação manual ampla |
| Geometry | parcial | multi-tool e conversões Single↔Multi; CNC por ferramenta, Feed XY/Z, Dwell, Extra Cut, V-Tip e compensação Path/In/Out/Custom; posições comuns de início/fim/troca com validação/persistência, somente fresagem sem sonda; editor com seleção/exclusão/desenho/transformações/undo, Texto vetorial e Borracha; faltam Paint Shape e gestos avançados; ver GEOMETRY_CNC.md e GEOMETRY_EDITOR.md |
| CNC Job | parcial | geração, plot (com numeração, setas e navegação passo a passo, além do Python), abertura e edição de G-code, Aplicar/Cancelar e Salvar; prévia G0-G3 em XY, laser por estado de emissão, ICP/HPGL/RML lineares; 19 perfis Python no seletor e `Paste_1` embutido no SolderPaste, total 20 de 20 ports parciais; Mach3 com sonda gera G31/G92 e exige confirmação manual, sem prévia; Roland inicialmente só MM/uma ferramenta e sem metadados de diâmetro na reabertura; ver `PREPROCESSADORES.md` |
| Persistência de projeto | parcial | `.fcnproj` embute objetos/G-code e parâmetros CNC individuais, compensação/posições e seleção/ordem de Drilling; confirmação da sonda não persiste; abertura e exportação `.FlatPrj` JSON/XZ validadas com serializadores Python e 18 objetos reais; não é round-trip universal, ver COMPATIBILIDADE_FLATPRJ.md |
| Calculadoras | parcial | três calculadoras implementadas |
| Ferramentas do menu Ferramentas | forte/parcial | 24 de 24 portadas (seção 4); vários painéis têm só a lógica testada e ainda precisam de validação manual no app |
| Plot Area com geometria densa | forte | LOD por densidade assíncrono (seção 4 e `PLOT_PERFORMANCE.md`); faltam margem em volta da vista e fidelidade total em diagonais de 45° |
| Plataforma (Java/launcher) | forte | Java 25 + JavaFX 25.0.4; launcher nativo opcional para pedir a GPU de alto desempenho (exige `g++`); opções de arquitetura futuras guardadas na memória do projeto (ver 9.0) |
| Transformations | forte/parcial | Rotate/Skew/Scale/Flip/Offset completos para Gerber/Excellon/Geometry; falta Buffer e referência "Object" |
| Tools Database | parcial | editor com 63 campos Python, busca/filtro, `.FlatDB`, backup e campos desconhecidos preservados; integrado a NCC/Isolation/Drilling/Geometry CNC/Milling/Paint/Cutout; Offset e profundidades Cutout/Thin transferidos, Paint individual; falta consumo integral de opções avançadas |
| Preferências globais | inicial/parcial | aba funcional para tema, snap, grade visual e visibilidade do Plot Area; ainda longe da cobertura do Python |
| Automação/CLI/scripts | ausente | não é a prioridade imediata |

“Forte/parcial” não significa compatibilidade certificada. Significa que o
fluxo principal existe e tem testes, mas ainda há casos e opções do legado a
cobrir.

## 7. Limitações e dívida técnica conhecidas

### Persistência parcial

Gerber/Excellon são salvos como geometria embutida. Gerbers salvos após este
incremento também preservam cada forma e sua ordem, permitindo reabrir e
continuar a edição. Projetos `.fcnproj` antigos, salvos com uma única união
por abertura, ainda carregam e plotam, mas o editor os mantém somente para
inspeção: reconstruir uma edição a partir dessas uniões poderia perder regiões
ou fundir pads/trilhas que se tocam. Reabra o arquivo Gerber original para
editar e salve um projeto novo.

Geometry gerada, geometria de plot de CNC Job e parâmetros por ferramenta
continuam sem snapshot completo. Ver seção 9.3.

### `MainWindow` concentra responsabilidades demais

`flatcam-fx/.../MainWindow.java` tem mais de 3.000 linhas e concentra
estado, menus, árvore, diálogos e orquestração de jobs. Não é necessário
reescrever a tela agora, mas novas áreas grandes devem extrair controladores ou
serviços coesos. A implementação do editor não deve aumentar indefinidamente
essa classe - por isso o Gerber Editor vive em `GerberEditorController`
(sessão, painel, camadas de plot e handler de seleção do canvas); o
`MainWindow` só expõe a ele uma interface `Host` estreita. Novas fatias do
editor devem crescer o controlador, não o `MainWindow`.

### Renderer atual é uma etapa, não um compromisso definitivo

O Canvas resolve a fatia vertical atual. Não migre prematuramente para GPU ou
código nativo. Primeiro meça arquivos representativos, tempo de frame, memória
e latência de seleção. Uma troca de renderer só é justificada por perfil real.

### Cobertura de interface

O módulo `flatcam-fx` ainda não possui uma suíte automatizada de interação de
UI. Fluxos críticos precisam de smoke test manual; lógica extraível deve ficar
nos módulos testáveis ou em classes sem dependência forte da janela.

### Localização e consistência

Ainda há textos fixos e mistura de idiomas em pontos da UI. Não espalhe novas
strings sem necessidade. A internacionalização completa pode vir depois, mas
novos painéis devem manter terminologia consistente com o produto.

### Temas disponíveis

Original restaura o CSS e a paleta anteriores à revisão visual de gelo, com
branco e grafite. Gelo mantém a interface recente em branco azulado e azul
escuro. Ambos usam o Modena do JavaFX; a dependência e o menu AtlantaFX foram
removidos. Preferências antigas `CUSTOM_*` migram para Gelo e `ATLANTAFX_*`
para Original, preservando claro/escuro. A limitação anterior de troca do
user-agent stylesheet do AtlantaFX deixou de se aplicar.

### Corrigido: menu de contexto herdando texto branco da célula selecionada

Achado e corrigido na revisão de 2026-09-23, causado pela própria correção
de contraste da árvore descrita mais abaixo. Ao clicar com o botão direito num item **já
selecionado**, `MainWindow` chamava `menu.show(cell, ...)` - ancorando o
popup na `TreeCell` em si. Uma célula selecionada+focada tem
`-fx-fill: -fc-selection-text-focused` (branco); `-fx-fill` é uma
propriedade herdável, e um `ContextMenu` mostrado via `show(Node, ...)`
herda essa propriedade do nó-âncora. O valor herdado do nó-âncora **vence
qualquer contra-regra CSS** de prioridade normal (confirmado tentando três
seletores diferentes em `components.css`, todos perderam) - não é algo
corrigível só com CSS. O texto de todo o menu ficava branco, invisível
contra o fundo claro do popup, exceto a linha em hover (que recebe estilo
próprio). Corrigido trocando a âncora para `projectTree` (a árvore inteira,
que não carrega essa cor sobrescrita) em vez da célula - sem efeito na
posição do popup, já que o código já usa coordenadas de tela absolutas
(`event.getScreenX()/getScreenY()`). Confirmado isolado (harness fora da
tela) antes/depois da correção.

### Múltiplos monitores: DPI e largura da barra lateral (2026-09-29)

Bug do JavaFX/Glass: ao **arrastar** a janela maximizada entre monitores com
escalas diferentes (ex.: 200% e 100%), a geometria do Stage e o `outputScale`
ficam inconsistentes e a interface distorce; Win+Seta não dispara o bug.
Contorno em `MainApp`: um poll (400 ms) detecta a transição
`isMaximized()` false -> true; após 3 leituras estáveis (`STABILITY_THRESHOLD`)
e 1 s de armamento (`ARM_DELAY`), o Stage é **recriado sobre a mesma Scene**
no monitor atual. Detalhes que importam: o monitor é capturado pela posição do
mouse no primeiro poll maximizado, `setX/setY` usam os bounds do `Screen` do
JavaFX (não os do AWT), `Platform.setImplicitExit(false)` evita o
encerramento ao fechar o Stage antigo (o fechamento explícito chama
`Platform.exit()`) e o Timeline anterior é parado a cada recriação.

Largura da sidebar por monitor: `AppPreferences.loadSplitHorizontalForScreen`
/ `saveSplitHorizontalForScreen` (chave por id AWT do monitor, com fallback
para o valor compartilhado). `MainWindow.setCurrentScreenId()` reaplica o
divisor via `Platform.runLater` após cada recriação.

### Desempenho do parse do Gerber (2026-09-29)

Medido com JFR na placa STM32F4-spindle (139 KB): 80% do parse estava no overlay
legado do JTS (`SnapIfNeededOverlayOp`, via `UnaryUnionOp`/`Geometry.union`).
Todas as uniões em `flatcam-cam` passaram a usar `OverlayNGRobust` (união e
`overlay` UNION/DIFFERENCE) e a geometria por abertura (`apertureGeometry()`,
usada só por Mark, editor e persistência) é calculada sob demanda por
`LazyApertureGeometry`. Parse: ~10-14 s -> ~1 s (aquecido). A geometria fica
equivalente (o solid tem alguns vértices a menos por causa do snapping) e os 329
testes passam. `ProjectFileIO.load` tinha o mesmo problema (16 s): o codec Gerber ainda usava
`UnaryUnionOp` e unia cada abertura ao abrir. Agora usa `LazyApertureGeometry`
(pública) e o `MainWindow` usa `OverlayNGRobust`; abrir levou ~0,65 s. Salvar
leva ~2,2 s, quase tudo compressão XZ. O buffer da isolação em
painéis grandes (4x4: ~24 s) era o `BufferOp` do JTS num multipolígono enorme
(`SubgraphDepthLocater`). `ParallelGeometry.separateGroups` agora separa o cobre em
grupos que não podem se tocar depois do offset (caixas envolventes alargadas), e
cada (grupo, passe) roda em paralelo: 4x4 caiu para ~3 s, com anéis idênticos
(comprimento e contagem iguais ao buffer único). A união das pegadas do G-code
(`ParallelGeometry.union`, por grupos de caixas) caiu de ~31 s para ~15 s no 4x4;
depois, jobs com mais de 2000 deslocamentos não unem a pegada dos deslocamentos
(coleção de polígonos), os buffers dos cortes rodam em paralelo e a formatação de
coordenadas/linhas deixou de usar `String.format` (texto idêntico, com teste):
G-code do 4x4 de ~31 s para ~8-10 s. Sem código nativo.

### Pendências abertas (2026-10-01)

- **Validação manual** dos painéis recentes no app real (lista na seção 3); só a lógica tem teste.
- **Plot Area densa:** a imagem de densidade cobre só a vista em que foi feita (num arraste longo a borda que
  entra fica vazia até parar); o custo cresce com o comprimento total dos traços em pixels (500 mil traços ≈
  1 s para a imagem exata); polígonos preenchidos muito densos ainda são vetoriais; diagonais de 45° têm
  até meio pixel de erro de cobertura nas bordas.
- **SolderPaste:** o preview do job é montado da geometria (o parser de G-code trata Z positivo como
  deslocamento) e o job não tem a tabela de passos; parâmetros valem para todos os bicos.
- **QRCode:** falta exportar o QR como SVG/PNG e as cores de preenchimento (só afetam a exportação).
- **Align Objects:** sem o realce em cor do objeto durante os cliques.
- **Film:** PDF escrito direto em vetores (sem biblioteca), PNG renderizado no DPI pedido.
- **`flatcam-fx`:** `MainWindow` passa de 7 mil linhas; os `Host` dos painéis continuam dentro dele.
- **Terminais antigos:** depois de mudar o `JAVA_HOME`, reabra o terminal e o VS Code; o `PATH` pode ainda listar
  o JDK 21 antes do 25 (afeta só scripts que chamam `java` direto).

### Diferenças intencionais já aceitas

- Operações pesadas rodam em background e são canceláveis.
- Progresso deve refletir trabalho real sempre que for mensurável.
- Ícones legados podem receber tratamento de contraste no tema escuro.
- Controles e amostras de cor podem ser modernizados sem mudar sua semântica.
- JTS substitui Shapely/GEOS na implementação Java; compare resultados por
  tolerância geométrica, não por igualdade textual ou ordem de coordenadas.

## 8. Histórico de progresso (2026-09-23; estado atual nas seções 9.5-9.6 e 15)

**NCC multi-tool com Rest Machining, boundary por objeto de referência,
"Check validity", Transformations, e persistência embutida de Gerber/Excellon
(compatível com o `.FlatPrj` do Python) foram concluídos** (seções 5, 4 e
9.3) - critérios de aceite verificados via testes do reactor (112 testes) e
smoke test do app; validação visual dos painéis feita ao vivo com o usuário
a cada rodada. A compatibilidade Python é verificada por leitura de código +
round-trip Java, não contra uma instalação Python real (indisponível neste
ambiente) - ver seção 9.3 para o aviso completo.

Na época, o que restava de 9.3 (Geometry e CNC Job com o mesmo tratamento) precisava de
mudanças de modelo reais antes de qualquer serialização (Geometry precisa de
um dict de parâmetros CAM persistente por ferramenta; CNC Job precisa reter
uma lista por segmento durante a geração) - cada um é essencialmente seu
próprio projeto, no mesmo espírito de "Excellon+Gerber primeiro" que guiou
essa fase.

**Gerber Editor - fatia 1/7 concluída (2026-09-23, opinião do Claude
registrada em 22/09 acima aplicada: avançar o editor em vez de Geometry/CNC
Job persistence, já que a lacuna mais visível é a ausência total de editor e
a persistência Gerber/Excellon necessária para "objeto editável que sobrevive
a save/reload" já existia).**

- `org.flatcam.cam.gerber.edit.GerberEditSession` (núcleo, `flatcam-cam`,
  testado): sessão de edição com `apply()`/`nextEditedName()`, ported de
  `AppGerberEditor.py`'s `edit_fcgerber()`/`update_fcgerber()`. Sem operações
  de edição ainda - `workingImage()` é sempre igual ao objeto de origem.
- `GerberEditToolPanel`/`MainWindow` (fatia de UI): menu "Editar" no Gerber
  abre a sessão no painel "Ferramenta" (oculta o objeto original no plot,
  igual ao Python's `orig_grb_obj.visible = False`); "Aplicar" cria um NOVO
  objeto `<nome>_edit` (nunca sobrescreve o original, igual ao Python);
  "Cancelar" descarta a sessão sem criar nada, restaura a visibilidade.
- Deliberadamente **sem**: seleção/hit-testing no canvas, command stack
  undo/redo, qualquer ferramenta de desenho (pad/track/region/disc/buffer/
  scale/etc.), tabela de apertures editável. Ver seção 9.4 para a ordem das
  próximas fatias (2: seleção/hit-testing; 3: undo/redo; 4: mover/copiar/
  excluir; 5: pads/tracks/regions/apertures).
- Critério de aceite desta fatia: usuário consegue entrar no editor, ver o
  objeto original ocultado, e Aplicar/Cancelar produzem exatamente o
  resultado acima - verificado por `GerberEditSessionTest` (4 testes) e smoke
  test manual (`MainApp started`/`stop` limpo). Nenhuma edição de geometria é
  possível ainda; não anunciar isso como "editor funcional" para o usuário
  final até pelo menos a fatia 2 (seleção) e 5 (ferramentas básicas)
  existirem.

**Gerber Editor - fatia 2/7 concluída (2026-09-23): seleção e hit-testing.**
Portado de `AppGerberEditor.py`'s `SelectEditorGrb`,
`draw_selection_area_handler()` e `plot_all()`, mais o `selection_type` de
`app_Main.py`:

- Formas individuais: `GerberShape` (abertura, geometria, polaridade) e
  `GerberImage.shapes()` - o parser agora guarda cada flash/trilha/região antes
  da união (regiões sob a abertura `"0"`, como o `'REG'` do Python).
  `transformed()` transforma as formas também.
- `GerberEditSession`: `clickSelect`/`boxSelect`/`selectedApertures`, com as
  regras do Python - só formas escuras são selecionáveis (o Python só testa
  `'solid'`); clique simples substitui a seleção, Ctrl+clique alterna;
  caixa arrastada para a direita seleciona formas **envolvidas**, para a
  esquerda formas **tocadas**. Divergência deliberada: o Python alterna no
  máximo um acerto por bloco de 77 formas (artefato do seu multiprocessing);
  aqui todos os acertos sob o ponto são alternados.
- Limitação conhecida: objeto restaurado de projeto (`.fcnproj` v2 guarda um
  agregado por abertura) não tem as formas individuais; a sessão as
  reconstrói dividindo cada agregado em partes disjuntas
  (`shapesApproximated()`), e o painel avisa que pads/trilhas que se tocam
  viram uma forma só e que regiões ficam de fora. Resolver isso exige
  persistir as formas individuais no codec (lista por abertura, como o
  `.FlatPrj` real do Python já faz) - candidato natural para a fatia 7.
- UI: `GerberEditorController` (novo, fora do `MainWindow`) desenha as formas
  em `#FF0000AF` e as selecionadas em `#0000FFAF` (cores e alfa de
  `global_draw_color`/`global_sel_draw_color` + `plot_shape()`), e liga o
  `PlotAreaView.SelectionHandler`: com o editor ativo, botão esquerdo
  seleciona (retângulo azul/verde de `global_sel_*`/`global_alt_sel_*`) e
  direito/meio fazem pan; sem editor, o canvas se comporta como antes. O
  painel mostra quantas formas e quais aberturas estão selecionadas (no lugar
  do destaque de linhas da tabela de aberturas do Python, que ainda não
  existe).
- Testes: `GerberEditSessionTest` (15, cobrindo clique, alternância, caixa
  nos dois sentidos, polaridade clara, fallback e transformação). O
  comportamento interativo no canvas foi verificado só até a inicialização do
  app - falta validação visual manual.
- **Corrigido nesta revisão (2026-09-23):** a pendência acima era, na
  prática, bloqueante - o usuário reportou "clicar não faz nada" ao testar
  esta fatia, e a causa raiz era exatamente esse bug. A árvore tem um
  listener em `getSelectedItems()` (linha ~625 do `MainWindow`) que remove
  reativamente linhas de categoria (Gerbers/Excellon/...) da seleção, já que
  elas são só agrupamento visual. Esse listener chamava
  `clearSelection(row)` **sincronamente**, de dentro da própria notificação
  de mudança - e o `TreeViewBehavior` interno do JavaFX (que também escuta a
  mesma lista, para implementar o clique) não tolera a lista mudar de
  tamanho enquanto ele ainda está lendo a mudança corrente, e lança
  `IndexOutOfBoundsException` no PRÓXIMO clique em qualquer linha da árvore.
  Na prática isso deixava a árvore (e portanto todo menu de contexto,
  inclusive "Editar") morta para cliques depois da primeira interação com
  uma linha de categoria. Corrigido adiando a chamada com
  `Platform.runLater(...)`. Reproduzido e confirmado antes/depois com um
  harness isolado (Stage fora da tela, eventos de mouse sintéticos reais
  contra `TreeCell`s reais) - a versão antiga reproduz o
  `IndexOutOfBoundsException` byte a byte igual ao relatado; a versão com
  `Platform.runLater` não lança nada.
- **Também corrigido nesta revisão: contraste de texto na árvore do
  projeto** (`components.css` + as 4 paletas `vars-*.css`). Dois bugs
  distintos, achados durante o teste manual desta fatia:
  1. Linha selecionada que perde o foco (usuário clica em Propriedades,
     Ferramenta ou no Plot Area) usava a cor padrão do JavaFX/Modena para
     esse estado, que em tema escuro fica cinza-claro com texto quase
     invisível. Corrigido com cores próprias por estado
     (`-fc-selection-bg-focused/-unfocused`,
     `-fc-selection-text-focused/-unfocused`).
  2. Ao trocar a seleção para outra linha, a linha desselecionada ficava com
     texto **preto puro**, mesmo com a paleta escura carregada - bug
     separado do Modena (o fill padrão de `.tree-cell .text` deriva de
     `-fx-text-background-color`, que não respeita bem `-fx-text-base-color`
     nesse caso). Corrigido fixando `-fx-fill: -fc-panel-text` para todo
     `.tree-cell:filled .text`, servindo de base para as regras de seleção
     acima.
  Achado (2): a correção inicial do item 1 usava `.tree-cell:selected:focused`
  para distinguir os dois estados - mas o pseudo-estado `:focused` de uma
  `TreeCell` reflete o índice de foco interno do `FocusModel` da árvore, não
  se a árvore em si tem foco de verdade, e nunca é desligado quando o foco
  sai da árvore para outro controle. Trocado para `.tree-view:focused
  .tree-cell:selected`, que usa o foco real do controle. Os dois bugs foram
  confirmados e corrigidos verificando o estado computado real (pseudo-classes
  e `text.getFill()`) num harness isolado antes/depois, não só lendo o CSS.

**Gerber Editor - primeiro fluxo de edição concluído no código (2026-09-24;
posicionamento visual validado pelo usuário em 2026-09-25).** As fatias 3 e 4 e a persistência das
formas da fatia 7 foram implementadas juntas:

- `GerberEditSession` mantém uma lista de formas por revisão e até 100 estados
  de undo/redo. Excluir, mover e copiar operam sobre a seleção; mover/copiar
  aplicam deslocamentos X/Y informados no painel e também movem a geometria
  `follow`. A cópia nova fica selecionada.
- `GerberImage.withEditedShapes()` recompõe cobre sólido em ordem de
  polaridade dark/clear, geometria `follow` e uniões por abertura. O cálculo de
  Apply roda num job cancelável com progresso; Cancelar o job conserva a sessão
  aberta. Aplicar cria um novo Gerber e enquadra o resultado no Plot Area.
- `GerberFlatPrjCodec` escreve cada forma na lista de geometria da sua
  abertura, no shape do `.FlatPrj` Python. `_java.shape_order` guarda a ordem
  global entre aberturas e polaridades para a reconstrução Java. Regiões
  ficam sob a abertura `0` (`REG`). Os tipos e campos de aberturas gravados
  agora usam os nomes do Python (`C`, `R`, `O`, `P`, `size`, `diam`, `nVertices`),
  mantendo a leitura dos nomes usados pelos projetos Java anteriores.
  `ProjectFileIO` publica o arquivo salvo
  por substituição atômica quando o sistema suporta e o save roda fora da
  thread JavaFX.
- Projetos Java antigos, que continham apenas a união por abertura, continuam
  abrindo; suas formas individuais são irrecuperáveis e o editor bloqueia
  operações nelas para não destruir geometria. Projetos Python sem
  `_java.shape_order` aceitam edição quando todas as formas são dark; se houver
  formas clear, a ordem relativa é incerta e a edição fica bloqueada.
- O painel mantém deslocamento numérico X/Y e agora os botões/ícones Mover e
  Copiar iniciam um posicionamento com dois cliques no canvas: origem e destino,
  com prévia entre eles. Esc ou clique direito curto cancela. Ctrl+Z/Y acionam o
  histórico da sessão sem capturar atalhos dos campos de texto. Naquele
  incremento ainda faltavam ferramentas de desenho e tabela editável de aberturas.
  Compatibilidade com um FlatCAM Python executável continua sem validação cruzada real.
- Verificação automática: `test`, 137 testes, sem falhas; teste de
  inicialização chegou a `MainApp started`. Ainda executar manualmente o fluxo
  Abrir Gerber -> Editar -> selecionar -> mover/copiar/excluir -> undo/redo ->
  Aplicar -> Salvar Projeto -> reabrir.

**Exportação Gerber da imagem atual (2026-09-25).** `GerberExporter` em
`flatcam-cam` escreve o cobre sólido resolvido em regiões G36/G37, com unidades,
coordenadas absolutas e polaridade dark/clear. A UI usa esse resultado em
"Salvar como..." para qualquer Gerber, inclusive um `_edit` sem `sourcePath`;
antes ela copiava o arquivo original, perdendo alterações, ou recusava objetos
sem origem. O arquivo é publicado via temporário e substituição atômica quando
suportada, evitando truncar um destino existente em caso de falha. Testes cobrem
edição, transformação, vazios com ilhas, imagem vazia,
limites de coordenadas e exportação/reabertura de uma placa real. O arquivo
preserva a imagem final, **não** os comandos/apertures/atributos X2 originais;
essa reconstrução semântica é um passo posterior. Coordenadas usam seis casas
decimais; regiões que colapsam nessa precisão são recusadas em vez de
silenciosamente perdidas. Ainda validar visualmente o arquivo exportado no
FlatCAM Python ou em visualizador Gerber independente.

**Trilhas multiponto no Gerber Editor (2026-09-25).** A ferramenta Track não
termina mais obrigatoriamente no segundo clique: cada clique adiciona um trecho
e Enter, duplo clique ou botão direito curto conclui a trilha. Backspace volta
um ponto; T/R percorre nos dois sentidos os cinco modos do legado (45 graus,
45 invertido, 90 graus, 90 invertido e ângulo livre). Com grid snap desligado,
o segmento é livre, como em `TrackEditorGrb.utility_geometry()` do Python. O
preview mostra o caminho confirmado e o trecho até o cursor. A trilha completa
é publicada como uma única `GerberShape`, com `LineString` em `follow` e cobre
bufferizado pela abertura C; por isso todo o gesto é um único passo de
undo/redo. `TrackBendModeTest` cobre os cinco roteamentos e
`GerberEditSessionTest` cobre criação, seleção e histórico da polilinha.

**Interação de objetos no Plot Area (2026-09-25).** Fora do Editor Gerber, clique
esquerdo seleciona o objeto visível no topo (cliques repetidos alternam entre
objetos sobrepostos); arrasto esquerda→direita exige
enquadramento completo e direita→esquerda seleciona por interseção do retângulo
com os limites do objeto. Ctrl alterna a seleção múltipla. A seleção sincroniza
com a árvore Projeto e recebe um contorno no canvas. Clique direito curto abre
o mesmo menu funcional da árvore para o objeto atingido; arrasto direito/meio
continua deslocando a vista. Clicar numa área vazia com o botão direito mostra
Enquadrar tudo/Limpar seleção. O Editor Gerber mantém seu próprio handler de
seleção sem herdar o menu global. `PlotObjectSelection` cobre hit-testing e
seleção por caixa em testes sem JavaFX. Os gestos básicos foram validados pelo
usuário; ainda faltam ações próprias de editor no menu do canvas.
O menu do Plot Area recebeu cores explícitas para texto normal e item focado
nos quatro temas; o destaque escuro usa azul mais profundo para manter
contraste de texto de pelo menos 4,5:1 (teste automático do CSS).

**Posicionamento no Plot Area (2026-09-25).** O menu contextual de um Gerber,
Excellon ou Geometry visível agora oferece "Mover no Plot Area" e "Copiar no Plot
Area". Um fantasma da geometria acompanha o cursor; clique esquerdo confirma o
deslocamento entre o ponto do menu e o destino, enquanto Esc ou clique direito
curto cancela. Multisseleção move/copia o grupo com o mesmo deslocamento.
CNC Jobs não entram nesse fluxo: mudar apenas o desenho deixaria o G-code
incoerente. Os gestos foram validados pelo usuário; ainda verificar a
persistência após salvar e reabrir um projeto. Não há undo/redo global para
cópias ou outras operações fora do histórico de movimentos.
Movimentos confirmados no Plot Area têm histórico próprio: Ctrl+Z desfaz e
Ctrl+Y refaz, inclusive movimentos de grupos; cópias e transformações feitas
por outros caminhos invalidam esse histórico. O menu contextual do Plot Area
agora fecha ao clicar em qualquer ponto do canvas. A janela maximizada tenta
reabrir no último monitor usado enquanto ele estiver conectado, aplicando a
largura lateral já salva para aquele monitor; sem ele, abre no principal. O
usuário confirmou o comportamento em dois monitores em 2026-09-25.

Alternativa não escolhida agora, mas ainda válida como próximo passo depois
das próximas fatias do editor: completar 9.3 (Geometry/CNC Job persistence),
adiando para quando algo realmente força a mão (ex.: um NCC resultado
precisar sobreviver a um reload).

### Critérios de aceite do incremento concluído (referência)

- O usuário consegue configurar ao menos duas ferramentas e sua ordem. ✅
  (`NccToolPanel`: tabela de diâmetros + `NccOrder`)
- A ferramenta menor processa apenas o material que permaneceu após a maior
  quando Rest Machining está ativo. ✅ (`NccGenerator`, testado)
- Resultado por ferramenta é identificável e chega corretamente ao G-code. ✅
  (`NccResult.toolResults()` -> `List<ToolGeometry>` -> G-code com troca de
  ferramenta)
- Parâmetros impossíveis falham com mensagem útil antes de iniciar o job. ✅
  (diâmetro duplicado/não-positivo, lista vazia)
- Cancelamento não publica resultado parcial como se fosse concluído. ✅
  (reutiliza `CancellationToken` já existente)
- Progresso não regride e termina em 100% no sucesso. ✅ (testado)
- Testes do reactor passam e o app inicia com os módulos recém-instalados. ✅
  (112 testes, `MainApp started`)

Decisão registrada: várias ferramentas produzem troca de ferramenta **num
único CNC Job** (G-code concatenado com M0 opcional entre seções), não jobs
separados - confirmado que é assim que `mtool_gen_cncjob` do Python funciona
(ver seção 5).

### Nota de divergência (opinião do Claude, 2026-09-22)

A recomendação acima (NCC multi-tool com Rest Machining) foi escrita por uma
sessão anterior (Codex). Uma sessão Claude, ao revisar o projeto neste mesmo
ponto, discorda da ordem e recomenda **resolver primeiro a persistência de
projeto** (seção 7, "Persistência ainda não é um modelo editável") antes de
abrir o incremento NCC multi-tool. Motivo:

- NCC multi-tool é trabalho horizontal: adiciona um novo tipo de resultado
  (segmentado por ferramenta, com ordem e possivelmente Rest Machining) que
  também não seria persistido pelo `ProjectFile` atual, que hoje só guarda
  caminhos de Gerber/Excellon e referências de CNC Job.
- Cada fatia nova que roda em cima desse formato (Isolation, Cutout, NCC,
  Geometry, CNC Job) é mais um caso que a futura reforma de persistência
  (já prevista na seção 9.3) vai ter que migrar depois. Resolver isso agora,
  com a superfície de objetos ainda pequena, custa menos do que esperar
  crescer mais.
- A seção 9.3 já lista o modelo de projeto versionado como pré-requisito do
  Gerber Editor; adiantar uma fatia mínima dele agora evita que o NCC
  multi-tool vire mais uma dependência a desembaraçar nessa reforma.

Isso não invalida o plano do Codex nem a ordem descrita nas seções 8 e 9 -
é uma divergência de julgamento sobre sequenciamento, registrada para que a
próxima sessão (humana ou IA) escolha com o argumento explícito, em vez de
herdar uma prioridade sem saber que houve debate sobre ela. Se a próxima
sessão seguir o plano original do Codex, não é necessário reverter esta nota;
só marque aqui qual caminho foi escolhido e por quê.

Escopo mínimo proposto pelo Claude para essa fatia de persistência, caso seja
adotada antes do NCC multi-tool:

1. Dar versão ao `.fcnproj` (campo `version`), pensando em migração futura.
2. Persistir parâmetros CAM por objeto (Isolation/Cutout/NCC), não só o
   G-code de saída - o suficiente para reabrir e reexecutar/reeditar sem
   reconfigurar do zero.
3. Persistir a associação Geometry -> ferramentas/parâmetros que a geraram,
   e CNC Job -> parâmetros que o geraram.
4. Manter compatibilidade de leitura com o `.fcnproj` atual (arquivo sem
   `version` é tratado como v1).
5. Testes: salvar -> reabrir -> objetos e parâmetros idênticos; e leitura de
   um `.fcnproj` "antigo" (sem os campos novos) continua funcionando.

Isto não é o modelo de objeto versionado completo da seção 9.3 (identidade,
metadados e origem separados) - é o mínimo para parar de perder estado a
cada ferramenta nova, sem bloquear o NCC depois.

## 9. Roadmap depois do próximo incremento

Esta é a sequência recomendada, sujeita a revisão com evidência do legado:

### 9.0 Fila atual (2026-10-01)

Em ordem aproximada de valor para o usuário; qualquer ordem é aceitável desde que alinhada ao Python:

1. **Ferramentas do menu:** todas portadas; resta validar manualmente os painéis novos. As conversões (Convert Any e Single↔MultiGeo) já foram feitas.
2. **Tools Database**: editor/salvamento entregues; completar integração com Milling/Paint/Cutout,
   **salvar `.FlatPrj`**, e completar parâmetros/validar os
   **pós-processadores** (hoje 20 ports parciais de 20 perfis Python, incluindo `Paste_1`;
   FX portable não entra nessa contagem). Perfil e campos globais CNC já persistem após gerar;
   priorizar transferência dos parâmetros da Tools Database, suplemento de metadados Roland, compensações de mesa
   e parâmetros individuais/laser. Rascunhos de formulários ainda não têm snapshot completo.
3. **Validação manual** dos painéis novos e, se houver divergência, correção guiada por captura (harness fora
   da tela, sem capturar a tela inteira).
4. **Plot Area:** margem em volta da vista para a imagem de densidade e, se um profile mostrar necessidade,
   rasterização incremental.
5. **Arquitetura/desempenho (decisão em aberto, não iniciada):** a avaliação de um núcleo de geometria nativo
   (Clipper2 via FFM, atrás de uma interface `GeometryEngine`, com o JTS como reserva), de viewport na GPU e das
   alternativas C++/Rust está registrada na memória do projeto (`architecture-options-native-gpu`) e, para continuidade
   independente dessa memória, em [ARQUITETURA_RENDERIZACAO_FUTURA.md](ARQUITETURA_RENDERIZACAO_FUTURA.md), avaliação de
   2026-10-02. A direção candidata é JavaFX + core Java/JTS + backend OpenGL experimental, preservando Canvas;
   Vulkan e motor geométrico nativo são avaliações posteriores e independentes. O primeiro passo recomendado é
   baseline reproduzível e protótipo de apresentação GPU **integrada** ao JavaFX, incluindo custo de transporte,
   GPUs híbridas, precisão CAD e fallback. Não há adoção definitiva nem prazo de ganho garantido.

### 9.1 Completar a paridade NCC (o que resta)

- validação manual dos parâmetros por ferramenta e contornos ISO;
- Tools Database;
- fixtures diferenciais e casos de desempenho (incl. Rest Machining e
  boundary por referência num board real).

Concluído: boundary por objeto de referência, seleção retangular/poligonal de área,
preservação de Order = None, seleção de ferramentas, ISO/CLEAR e
validação/sugestão de diâmetro ("Check validity")
- ver seção 5.

**Fixture diferencial do NCC com um projeto real (2026-09-30).** O projeto
`Dados_Ambientais_C6_V2` (`.FlatPrj` do Python) traz o `Cobre_MortoFino_Top_cnc`, gerado
pelo NCC do Python a partir de `F_Cu` com a área da placa (`Edge_Cuts.gm1_area`) como
limite. Comparando com o NCC do FX (cortador 0,1829 mm, Standard, connect, limite =
`NccBoundary.Area` da placa, margem 0): área limpa idêntica (IoU 0,9987; a sobra de
1,1 mm² é o arredondamento das pontas) e comprimento de percurso 8955 mm contra 8962 mm
do Python com sobreposição 0,54 (o espaçamento medido no G-code do Python dá ~0,53).
Com o limite "Itself" (casco convexo do cobre) o FX limpa 310 mm² a mais, na reentrância
da borda superior: era só uma diferença de parâmetro, não de algoritmo. O método Seed
gera ~10x mais caminhos e é bem mais lento (15-30 s). `NccPythonParityTest` repete a
comparação e só roda com `FLATCAM_PARITY_PROJECT` apontando para o `.FlatPrj`, porque o
projeto é privado. Ficam sem oráculo: Rest Machining, Lines e Combo.

**Conversion e Join Objects do Python (2026-09-30), em andamento.** Feitos: Editar >
Converter > "Contorno → Area" (`OutlineToArea`, o `convert_outline2area` do Python: fecha o
contorno com `Polygonizer` e usa a maior região; com o `Edge_Cuts` do projeto real o
resultado é idêntico ao `gm1_area` do Python, diferença simétrica 0) e Editar > Juntar
Objetos > "Gerber(s) → Gerber" (`GerberJoin`, o `GerberObject.merge`: junta aberturas,
formas, sólido e follow; código de abertura em conflito recebe o próximo livre,
aberturas idênticas são compartilhadas; Gerbers com unidades diferentes são recusados; se
um Gerber posterior tem formas clear, o resultado fica sem formas editáveis para não
apagar o cobre anterior no Gerber Editor). Novo objeto "Combo_Gerber". Também feitos:
Join Excellon(s) → Excellon (`ExcellonJoin`: ferramentas são identificadas pelo diâmetro, e com
`fuse_tools`, o padrão do Python, as de mesmo diâmetro a 4 casas viram uma; furos e slots são
renumerados; `Combo_Excellon`) e Join Geo/Gerber/Exc → Geo (`GeometryJoin`: objetos simples
viram um Geometry só; Geometrys multi-ferramenta fundem as ferramentas de mesmo diâmetro e
perfil; misturar simples com multi-ferramenta é recusado como no Python; `Combo_SingleGeo`/
`Combo_MultiGeo`). Com o projeto real, os 3 Excellons somam 80 furos em 4 ferramentas (as duas
de 0,8 mm do PTH viram uma). Convert Any to Geo/Gerber/Excellon e Single↔MultiGeo foram feitos depois (entrada abaixo).

**Barra superior com paridade ao Python (2026-09-30).** As barras File, Edit, View e Shell do
Python (mesmos botões, mesma ordem) e a barra Tools podem ser ligadas e desligadas em Exibir >
Barras de ferramentas, com o estado lembrado como o `global_toolbar_view` do Python (bits 1, 2,
4, 8 e 256); por escolha do usuário o padrão mostra só File e Edit (o padrão de fábrica do
Python mostra todas). Saíram da barra os botões que o Python não tem: Abrir G-Code (continua em
Arquivo), o job de demonstração (agora em Ajuda) e o botão Cancelar, que passou para a barra de
status e só aparece enquanto uma operação está em andamento. "Salvar e Fechar Editor" da barra
agora está ligado.

**2-Sided Tool (2026-09-30; "Centro da placa" adicionado depois e é a referência inicial).** Ferramentas > 2-Sided Tool (`DoubleSidedToolPanel`), o
`ToolDblSided.py` do Python com os mesmos padrões (eixo X, referência Ponto, furo de 3,125).
Além do Python: espelha vários objetos de uma vez; a linha de espelhamento pode passar pelo
centro ou por uma borda da caixa de um objeto, dos próprios objetos marcados, ou por um ponto
digitado, pego no plot (clique; Esc cancela) ou a origem; modo "criar cópia espelhada" além do
"no lugar"; pré-visualização no plot da linha, do contorno da placa já espelhado (o objeto Edge_Cuts/
Outline/Profile com as próprias linhas, ou a caixa de todos os objetos; desligável), dos
contornos espelhados dos objetos marcados e dos furos de alinhamento. O contorno original
(placa e objetos marcados) aparece bem fraco e tracejado como referência
(`PlotAreaView.setEditorReference`), o interior da placa pode ser preenchido de forma
translúcida (`setEditorFills`) e o conteúdo dos objetos marcados (cobre, furos, caminhos) aparece
já espelhado, preenchido em ciano com as trilhas visíveis (`setEditorContent`), para ver como
a outra face vai ficar. Furos de alinhamento: lista de "X, Y" (um por linha ou "(X, Y), (X, Y)") ou pegos
no plot; cria o Excellon "Alignment Drills" com cada furo e o seu espelho, pelo mesmo eixo e
linha. Eixo X inverte Y (linha horizontal) e eixo Y inverte X, como no Python.
"Pegar no plot" e "Adicionar furo no plot" prendem o clique ao centro exato do furo (ou ponta de
slot) de um Excellon num raio de 14 px (caixa "Prender ao centro de furos existentes", ligada por
padrão), o equivalente ao "Pick hole" do Python, e vale também para os furos de alinhamento.

**Paint Tool (2026-09-30).** Ferramentas > Paint Tool (`PaintToolPanel`, `NccGenerator.paint`,
`PaintParameters`): preenche polígonos de um Gerber ou Geometry com caminhos, reaproveitando as
estratégias do NCC (Standard, Seed, Lines, Combo), com sobreposição, margem (encolhe cada
polígono), conectar, contorno, ordem das ferramentas e rest machining, tudo com os padrões do
Python (0,3 mm, 20%, ordem reversa). Seleção dos polígonos: todos, um por clique (Esc
termina), área por retângulo ou polígono desenhados, ou os que tocam um objeto de
referência; a seleção aparece destacada no plot. Gera um Geometry `<nome>_paint` multi-
ferramenta. O Laser Lines do Python não é oferecido (depende das aberturas do Gerber). Sem
oráculo do Python para comparar caminhos; verificado por propriedades (cobertura ~100% da placa
do projeto real em Standard, Seed e Lines, em menos de 1 s) e testes.

**Panelize Tool (2026-09-30).** Ferramentas > Panelize Tool (`PanelizeToolPanel`, `Panelize`):
repete um Gerber, Excellon ou Geometry em colunas x linhas, as cópias afastadas pela caixa de
referência (a do próprio objeto ou a de outro, como o contorno) mais o espaçamento; "limitar o
tamanho" reduz a grade até caber, como o Python; um Gerber também pode virar painel Geometry. O painel
é feito transladando cada cópia e juntando com as regras do Juntar Objetos (Gerber: aberturas
compartilhadas e formas de todas as cópias; Excellon: ferramentas de mesmo diâmetro fundidas;
Geometry: caminhos por ferramenta). O plot mostra o contorno de cada cópia e o tamanho do painel
antes de criar; o resultado é `<nome>_panelized`. Padrão inicial 2 x 2 (o Python começa em 1 x 1).
Com o projeto real, o F_Cu em 6 x 6 leva ~80 ms e a área é exatamente 36 vezes a original. Corrigido
de passagem: a união por grupos descartava polígonos dentro de coleções aninhadas.

**Trocar de ferramenta no meio do uso (2026-09-30).** As ferramentas que desenham no plot (2-Sided,
Paint, Panelize) registram uma limpeza (`activeToolCleanup`) que roda quando outra ferramenta
assume a aba ou o painel fecha: cancela o clique armado e a seleção de área e apaga previews,
preenchimentos, conteúdo e referência. Antes, o que a ferramenta anterior deixava no plot ficava até
o painel seguinte fechar. A limpeza não toca o destaque dos editores.

**Invert Gerber Tool (2026-09-30).** Ferramentas > Invert Gerber Tool (`InvertGerber`,
`InvertGerberToolPanel`), o `ToolInvertGerber.py`: a caixa do Gerber, acrescida da margem (padrão 0,1;
cantos Redondo, Chanfrado ou Quadrado = mitre, padrão do Python), perde todo o cobre e o que sobra vira
um novo Gerber `<nome>_inverted` feito de regiões (abertura "0"). Com o F_Cu do projeto real a área
confere (caixa - cobre = 1122,64 mm²) e leva ~150 ms.

**Subtract Tool (2026-09-30).** Ferramentas > Subtract Tool (`Subtract`, `SubtractToolPanel`), o
`ToolSub.py`: para dois Gerbers ou dois Geometrys cria `<alvo>_sub`. Gerber: as formas do alvo que
tocam o subtraendo são cortadas e arquivadas na abertura de regiões "0" (como o Python), as demais
mantêm a abertura, e o cobre é refeito na ordem das formas. Geometry: "Fechar caminhos" (padrão
ligado) corta o alvo como uma forma fechada; desligado, cada polígono vira seus anéis e cada linha
é cortada separadamente; subtraendo multi-ferramenta é recusado, como no Python; alvo
multi-ferramenta mantém as ferramentas. "Excluir os objetos de origem" remove os dois depois.
Diferença deliberada: o Python une as diferenças do alvo contra cada forma do subtraendo, o que
deixa cobre coberto por só uma de várias formas sobrepostas; aqui o alvo perde a união de todas.
Com o projeto real, F_Cu menos B_Cu deixa 404,2 mm² dos 3092,3 mm² em 1,7 s.

**Extract Drills Tool (2026-09-30).** Ferramentas > Extract Drills Tool (`ExtractDrills`,
`ExtractDrillsToolPanel`), o `ToolExtractDrills.py`: cada flash (follow = ponto) dos tipos de pad marcados
(circular, oblongo, quadrado, retangular, outros) vira um furo no centro; diametro fixo (0.5), proporcional
(80% do menor lado) ou anel anular (menor lado - 2 x anel, 0.2). Furos de mesmo diametro (4 casas) dividem a
ferramenta; nada extraido = aviso. Gera um Excellon `<nome>_drills`. No projeto real: 31 pads redondos em
F_Cu e B_Cu (modo anel). Testes em `ExtractDrillsTest`.

**Punch Gerber Tool (2026-09-30).** Ferramentas > Punch Gerber Tool (`Punch`, `PunchGerberToolPanel`), o
`ToolPunchGerber.py`: fura os pads dos apertures escolhidos (lista multipla, todos por padrao). Origem dos
furos: Excellon (brocas que caem dentro de um flash dos apertures escolhidos, com o diametro da ferramenta)
ou o mesmo dimensionamento do Extract Drills (fixo 0.5, anel 0.2, proporcional 80%; `ExtractDrills.holes`
foi extraido para ser compartilhado). No modo fixo, furo >= pad falha como no Python. Cada furo entra como
shape "clear" (circulo) no Gerber `<nome>_punched`, entao o cobre mantem as formas individuais.
Testes em `PunchTest`.

**Etch Compensation Tool (2026-09-30).** Ferramentas > Etch Compensation Tool (`EtchCompensation`,
`EtchCompensationToolPanel`), o `ToolEtchCompensation.py`: cria `<nome>_comp` com o cobre crescido (ou
encolhido, offset negativo) pelo deslocamento = espessura do cobre (padrao 18 um) / (1/fator) pelo fator de
corrosao, por corrosivo (CuCl2 0.33, Fe3Cl e alcalinos 0.25) ou manual (um). Inclui os conversores oz->um
(x34.798) e mils->um (x25.4). Diferencas deliberadas: o Python trata todo Gerber como mm; aqui os microns sao
convertidos para as unidades do Gerber, e os shapes "clear" (furos) encolhem o mesmo deslocamento para ficarem
coerentes com o cobre crescido. Os tamanhos dos apertures ficam como estavam. Testes em `EtchCompensationTest`.

**Film Tool (2026-09-30).** Ferramentas > Film Tool (`FilmExporter`, `FilmToolPanel`), o `ToolFilm.py`: filme
imprimivel de um Gerber ou Geometry em SVG, PNG ou PDF, enquadrado pela caixa de outro objeto (Gerber, Geometry
ou Excellon). Negativo (folha preta, feicoes brancas, borda padrao 1.0) ou positivo (cor, margem 1 mm);
escala X/Y (a partir do canto inferior esquerdo da caixa), inclinacao em graus com referencia (4 cantos ou
centro), espelho X/Y/ambos (em torno do centro da caixa), espessura do traco ("Scale Stroke": contorno de 2x o
fator, padrao 0.01), e furar o positivo por Excellon ou centro dos pads (reusa `Punch`). PDF: "Bounds" ou
A0-A6/Letter/Legal/Tabloid, retrato/paisagem; PNG: DPI escolhido. Diferencas deliberadas: escala/inclinacao/
espelho agem na geometria das feicoes (no Python sao repassados ao export_svg do objeto), o PNG e renderizado
no DPI pedido (Java2D) em vez do ajuste estranho do Python, e o PDF e escrito direto em vetores (sem
svglib/reportlab). No projeto real: F_Cu negativo com moldura do Edge_Cuts gera SVG 58 ms, PNG 275 ms, PDF
151 ms. Testes em `FilmExporterTest`.

**Fiducials Tool e Corner Markers Tool (2026-09-30).** Ferramentas > Fiducials Tool (`Fiducials`,
`FiducialsToolPanel`), o `ToolFiducials.py`: marcas de alinhamento num Gerber de cobre, gerando `<nome>_fid`.
Tipos: circular, cruz (duas linhas com espessura) e xadrez (dois quadrados). Posicoes: automatico (cantos da
caixa do objeto + margem; terceiro ponto acima/abaixo/nenhum) ou manual (cliques no plot: inferior esquerdo,
superior direito e o segundo). "Aberturas na mascara" cria os mesmos pontos com o dobro do tamanho num Gerber
de mascara de solda. Reaproveita um aperture de mesmo tipo/tamanho, senao cria o proximo codigo livre.
Ferramentas > Corner Markers Tool (`CornerMarkers`, `CornerMarkersToolPanel`), o `ToolCorners.py`: marcadores
em L ("safe") ou cruz nos 4 cantos da caixa (fora por margem + meia espessura; padroes 0.1 / 3.0 / 0.0),
gerando `<nome>_corners`, e "Criar furos nos cantos" gera um Excellon `<nome>_corner_drills` (padrao 0.5) nos
mesmos pontos. Testes em `MarkersTest`.

**Migracao para Java 25 + JavaFX 25.0.4 (2026-10-01; branch `java-25` já mesclado no `flatcam-next`).** `maven.compiler.release` 25 e `javafx.version` 25.0.4; build e todos os testes passam, e os temas renderizam igual no JavaFX 25. Comparacao Java 21 x 25 no F_Cu real (isolacao 3 passes): primeira execucao 312 ms -> 93 ms e execucao aquecida 55 ms -> 26 ms; painel 3x3: 460-600 ms -> 164-206 ms frio; carga do projeto sem diferenca (~60 ms). O `JAVA_HOME` precisa apontar para um JDK 25 para compilar e rodar.

**QRCode Tool (2026-10-01).** Ferramentas > QRCode Tool (`QrCodeMarker`, `QrCodeToolPanel`), o `ToolQRCode.py`:
QR Code de quadrados de cobre num Gerber, gerando `<nome>_qrcode`. A matriz vem do ZXing (`com.google.zxing:core`
3.5.4, Apache 2.0, nova dependencia de runtime do `flatcam-cam`). Padroes do Python: versao 1 (cresce sozinha ate 40
se o texto nao couber), correcao L (L/M/Q/H), caixa 3 (modulo = caixa/10 unidades), borda 4 modulos, polaridade
positiva (negativa = mascara menos os modulos) e mascara quadrada (ou arredondada). Clique no plot define o destino.
Diferencas deliberadas: o QR fica centrado no ponto clicado (o Python ancora um canto) e a mascara limpa todo cobre
sob ela, nao so os poligonos que a contem por inteiro. Nao ha ainda exportar o QR como SVG/PNG nem a cor de preenchimento
do Python (so afetam a exportacao). Teste de ida e volta: a geometria colocada e rasterizada e decodificada de volta ao
texto. `build-native.cmd` agora apaga `flatcam-fx\target\dependency` antes de copiar, senao jars de um JavaFX antigo
ficavam junto dos novos no launcher nativo. Testes em `QrCodeMarkerTest`.

**SolderPaste Tool (2026-10-01).** Ferramentas > SolderPaste Tool (`SolderPaste`, `SolderPasteToolPanel`), o
`ToolSolderPaste.py` + `generate_gcode_from_solderpaste_geo` + pre-processador `Paste_1`. Passo 1: tabela de bicos
(padrao 1.0 e 0.3) e um Gerber de mascara de pasta geram a Geometry `<nome>_solderpaste` (multi-ferramenta): cada pad
recebe uma linha, do maior bico que cabe nele (linha pelo meio do lado maior se as diagonais empatam, senao pela
diagonal mais longa, encolhida por meia largura do bico; pads sem bico que caiba sao contados e avisados). Passo 2:
a Geometry de pasta vira um CNC Job (diálogo de salvar; `<nome>_cnc_solderpaste.nc`): cabecalho, troca de bico
`T n / M6 / M0` e, por caminho (vizinho mais proximo a partir de 0,0): G00 ate o ponto, Z de deslocamento, Z de inicio,
M03 + espera, Z de dispensa, avanco XY, M05, M04, Z de parada, M05, espera, Z de deslocamento — igual ao Paste_1.
Parametros com os padroes do Python (Z 0.05/0.1/0.05, deslocamento 0.1, troca 1.0 em 0,0, avancos 150/150/1.0, 300 rpm
1 s frente e 200 rpm 1 s reversa). Diferencas deliberadas: um pad pequeno demais nao herda a linha do pad anterior (bug do
Python: a variavel `geo` vaza entre iteracoes); os parametros valem para todos os bicos (o Python guarda um conjunto por
bico); velocidade e espera sao escritas quando maiores que zero; o programa termina so subindo para a altura de troca
(sem X,Y final). O preview do CNC Job e montado da geometria (o parser de G-code trata Z positivo como deslocamento, e
a dispensa usa Z positivo), por isso o job de pasta nao tem a tabela de passos. Com o F_Cu do projeto como mascara de
teste: 44 pads, 2 bicos, 53 caminhos em ~25 ms. Testes em `SolderPasteTest`.

**LOD por densidade no Plot Area (2026-10-01).** Camada 1 da proposta de desempenho para Geometry muito densa:
`DensityRaster` + integracao no `PlotAreaView` (ver `PLOT_PERFORMANCE.md`, secao "LOD por densidade"). Medido com
geometria sintetica em pan: 100 mil tracos 745 ms -> ~200 ms; 500 mil 4,4 s -> ~1,1 s (cobertura de area com antialiasing, para
ficar igual ao vetor; 4 defeitos de fidelidade corrigidos, ver PLOT_PERFORMANCE.md). Testes em `DensityRasterTest`.
Camada 2 feita (cache de interacao + rasterizacao fora da thread JavaFX, `DenseRenderer`): quadro de pan 1-3 ms, imagem exata
chega ~145 ms (20 mil), ~350 ms (100 mil), ~1,1 s (500 mil) apos o pan parar. Pendente: cobrir uma margem em volta da vista.

**Align Objects Tool (2026-10-01).** Ferramentas > Align Objects Tool (`AlignObjects`, `AlignObjectsToolPanel`), o
`ToolAlignObjects.py`: alinha um Gerber ou Excellon (objeto a alinhar) a outro (referencia) clicando em pads ou furos.
Um ponto (padrao do Python): clique num pad/furo do objeto a alinhar e no correspondente da referencia, e o objeto e
transladado. Dois pontos: repete o par e, alem da translacao, gira em torno do primeiro destino para que o segundo
ponto caia no segundo destino. O clique e resolvido para o centro do pad (flash de Gerber que contem o ponto) ou do
furo/ranhura (raio da broca + 6 px). Aplica `TransformOp.Offset` e `TransformOp.Rotate` pelo mesmo caminho do Transform
Tool. Diferenca deliberada: o angulo vem de `atan2` (o `atan(dy/dx)` do Python falha com dx = 0 e erra de quadrante em
giros acima de 90 graus), e a rotacao so e pulada quando o angulo e ~0 (a regra do Python tambem pulava giros
legitimos). Nao ha o realce em cor azul do objeto durante os cliques (o status do painel diz qual clique e o
proximo). Testes em `AlignObjectsTest`.

**Convert Any→Geo/Gerber/Excellon e Single↔MultiGeo (2026-10-01).** Editar > Converter (`ObjectConversion` em
`flatcam-cam/.../convert/`, ligações no `MainWindow`), do `app_Main.py`. *Objeto → Geometry*: Gerber ou Excellon
viram um Geometry de polígonos preenchidos (`<nome>_conv`); um Geometry é copiado com suas ferramentas. *Objeto →
Gerber*: Excellon vira um Gerber com uma abertura redonda por ferramenta (códigos a partir de 10), furos como
flashes e slots como traços; Geometry vira regiões (polígonos) e traços (linhas) com a largura da ferramenta a que
pertencem. *Objeto → Excellon*: cada forma fechada de um Geometry, ou cada flash de um Gerber, vira um furo no
centro com o menor lado da caixa como diâmetro; um traço Gerber de exatamente 2 pontos vira slot com a largura da
abertura (como no Python, isso inclui trilhas retas de 2 pontos); ferramentas de mesmo diâmetro a 4 casas
compartilham o id. *Single → Multi*: pergunta o diâmetro e põe a geometria sob uma ferramenta; *Multi → Single*:
une a geometria de todas as ferramentas e descarta a informação de ferramenta, como o Python. As duas últimas
alteram o objeto no lugar. Diferenças deliberadas: um Geometry só de linhas vira cobre por traço da largura da
ferramenta (o Python grava as linhas nuas como "sólido", sem área), as comparações de diâmetro são sempre a 4
casas (o Python mistura exata e arredondada) e o Single → Multi pede o diâmetro porque um Geometry de uma
ferramenta no FX não o guarda. Com o projeto real: Excellon→Gerber preserva a área exatamente e o F_Cu → Excellon
dá 72 furos, os mesmos do Excellon PTH; as isolações viram 99 e 111 formas (~0,3 s). Testes em
`ObjectConversionTest`.

**Tooltips animados e descrições das ferramentas (2026-10-01).** `FluidTooltips` substitui o `Tooltip` nativo do
JavaFX em toda a janela: atraso de 0,45 s (60 ms se outro tooltip fechou há menos de 0,7 s), entrada com fade e
deslize de 150 ms, saída com fade de 90 ms e, com um tooltip aberto, **deslize de 170 ms até o controle vizinho** em
vez de fechar e reabrir (com cruzamento de opacidade). Posição abaixo do controle (acima se faltar espaço; ao lado
nos itens de menu), dentro do monitor, com a paleta de cada tema (`ThemeOption.tooltipPalette`: cinza neutro #3d3d3d no Preto, azul-acinzentado no Preto
gelo, branco com borda cinza ou azulada nos claros), com título opcional em negrito. Ele **adota os tooltips nativos que já existem**: ao entrar num controle com `Tooltip`, o texto passa para o
gerenciador e o nativo é removido, então nenhum painel precisou mudar; células de tabela e árvore mantêm o nativo (se
atualizam a cada linha). Itens de menu (janelas à parte, que o filtro da cena não vê) ganham os handlers quando o
menu abre (`attachMenu`). `ToolDescriptions` traz título e texto (baseados nos tooltips do Python) das 24 ferramentas
do menu Ferramentas, usados nos itens do menu e nos botões da barra; as quatro ainda não portadas dizem isso. Os
itens de Converter e Juntar Objetos também têm descrição. O popup fecha junto com a janela e antes do "Sair" (um
tooltip aberto num `Platform.exit()` direto derrubava o toolkit nativo). Limitações: controles desabilitados não
recebem eventos do mouse, então os botões das ferramentas ainda não portadas não mostram o tooltip na barra; a
navegação dentro dos menus por tooltip só foi validada por simulação (harness fora da tela), não com o mouse.
Testes: `ToolDescriptionsTest` (toda ferramenta do menu tem descrição).

**Optimal Tool (2026-10-01).** Ferramentas > Optimal Tool (`MinimumDistance` em `flatcam-cam/.../analysis/`,
`OptimalToolPanel`), o `ToolOptimal.py`: une o cobre do Gerber em peças separadas, mede a distância entre todo par
de peças (arredondada à precisão, padrão 4 casas) e mostra a menor distância, quantos pares estão nela, onde (os dois
pontos mais próximos) e as demais distâncias em ordem crescente, cada uma com seus pares. Só uma peça: mensagem como
a do Python. Diferenças deliberadas: os pares são medidos em paralelo com `IndexedFacetDistance` (o Python mede um a
um), roda como job cancelável com progresso, e escolher um local na lista já leva o plot até o meio do vão e o marca com
um anel e o segmento entre os dois pontos (o Python exige clicar em "Locate"). Com o projeto real: F_Cu tem 44 peças e
o vão mínimo é 0,3505 mm em 16 pares (B_Cu: 47 peças, 0,3505 mm em 18), em ~70 ms. Mais memória cresce com o quadrado
do número de peças (todas as distâncias são guardadas, como no Python). Testes em `MinimumDistanceTest`.

**Rules Check Tool (2026-10-01).** Ferramentas > Rules Check Tool (`RulesCheck` em `flatcam-cam/.../analysis/`,
`RulesCheckToolPanel`), o `ToolRulesCheck.py`: dez regras (tamanho da trilha, cobre-cobre, cobre-contorno, seda-seda,
seda-máscara, seda-contorno, lasca da máscara, anel anular, furo-furo, tamanho do furo) com os limites padrão do Python
(0,25 / 0,25 / 1,0 / 0,25 / 0,25 / 1,0 / 0,25 / 0,3 / 0,3 / 0,3). Cada regra vira um resultado OK/FALHOU/NÃO EXECUTADA
(faltou escolher objeto, valor inválido); regras com violação listam os pontos e clicar num ponto leva o plot até ele
(todos ficam marcados com anéis). Diferenças deliberadas em relação ao Python: os vãos são buscados com `STRtree` +
`IndexedFacetDistance` em paralelo (o Python compara todos os pares); a regra da lasca da máscara tem o próprio
checkbox (no Python ficava atrelada ao de seda-seda); seda-contorno usa o próprio valor (o Python lia o de
cobre-contorno); o tamanho do furo é verificado por Excellon; uma peça única de cobre passa com uma nota (o Python a
relatava como FALHA); vãos de até 2e-6 mm contam como encostados. Roda como job cancelável. Com o projeto real
(F_Cu, B_Cu, Edge_Cuts, PTH, NPTH): cobre-cobre OK (vão mínimo 0,35), cobre-contorno falha em 4 pontos com o limite 1,0,
tudo em ~340 ms. Testes em `RulesCheckTest`.

**Copper Thieving Tool (2026-10-01).** Ferramentas > Copper Thieving Tool (`CopperThieving` em
`flatcam-cam/.../convert/`, `CopperThievingToolPanel`), o `ToolCopperThieving.py`: (1) preenche o cobre vazio
(diferença entre a caixa e o cobre afastado pela distância) como sólido, pontos, quadrados ou grade de linhas, com
referência ao próprio objeto (caixa retangular ou casco convexo), a áreas desenhadas (dois cliques por retângulo) ou a
outro Gerber/Geometry; gera o objeto `_thief`; (2) robber bar: anel de espessura dada ao redor da caixa do cobre
(`_robber`); (3) máscara de galvanoplastia a partir da máscara de solda, com o thieving e/ou a robber bar da última
execução, distância (negativa encolhe as aberturas) e área galvanizada (`_plating_mask`). Padrões do Python (distância
0,25, margem 1,0, área mínima 0,1, 64 passos de círculo, pontos/quadrados 1,0 e espaço 2,0, linhas 0,25 e 2,0, robber
1,0/1,0). Diferenças deliberadas: a distância é um único buffer do cobre inteiro (mesmo resultado que bufferizar e unir
cada polígono, em um terço do tempo); pontos e quadrados só são construídos onde a envoltória de uma área livre os
comporta e testados com geometria preparada; a grade de linhas é cortada pelo cobre numa só operação (o Python corta
linha por linha e deixa peças sobrepostas, que a união final do Gerber elimina: a área unida bate com a do Python em
0,004%). Comparação com o Python (shapely) no F_Cu do projeto real, mesmos parâmetros: sólido 21 ms contra 34 ms,
pontos 42 ms contra 79 ms, quadrados 25 ms contra 75 ms, linhas 75-120 ms contra 207 ms (FX depois de aquecido; 1ª
execução com JVM fria é 2 a 4 vezes maior); mesmas contagens de polígonos e áreas (sólido, pontos, quadrados).
Testes em `CopperThievingTest`.

**Calibration Tool (2026-10-01).** Ferramentas > Calibration Tool (`Calibration` em `flatcam-cam/.../transform/`,
`CalibrationToolPanel`), o `ToolCalibration.py`, o que fecha as 24 ferramentas do menu: (1) quatro pontos (inferior
esquerdo = origem, inferior direito, superior esquerdo, superior direito) clicados sobre furos ou pads flashados de um
objeto (o ponto vira o centro do furo/pad) ou livres, arredondados a 4 casas; (2) G-code de verificação que visita a
origem, o ponto de alinhamento (superior esquerdo ou inferior direito, à escolha), o de checagem e o de verificação, com
Z de deslocamento 2,0, Z de verificação 0,1, Z de troca 15 e etapa opcional de zerar Z; (3) fatores a partir dos deltas
medidos nos pontos 2 e 3 (escala X = dx/(x2-x1)+1, escala Y = dy/(y3-y1)+1, inclinação X = atan(dx3/(y3-y1)), inclinação Y
= atan(dy2/(x2-x1))), aplicáveis aos próprios pontos (escala e inclinação pela origem); (4) objeto calibrado
(`_calibrated`): escala e depois inclinação pela origem em Gerber, Excellon ou Geometry. Diferenças deliberadas: o Python
compara o delta com a coordenada do ponto (campo vazio = sem desvio) e soma a Y da origem ao delta da inclinação Y; aqui
um delta vazio ou zero não corrige nada e a inclinação Y usa só o delta; ponto fora de furo/pad no modo "objeto" é recusado
e o clique repetido (o Python ignora em silêncio); o G-code é salvo num arquivo (o Python abre um editor de código).
Comparação com o Python (shapely) no F_Cu real, escala 1,002/0,998 e inclinação 0,1/0,05: ~2 ms nos dois (FX: 2 ms depois
de aquecido, 33 ms na 1ª execução; a área resultante é igual, 3092,1585). A ferramenta é só aritmética de poucos pontos,
então não há ganho a mostrar. Testes em `CalibrationTest`.

### 9.2 Transformations - concluído nesta revisão (ver seção 4)

Rotate/Skew/Scale/Flip/Offset reutilizáveis para Gerber, Excellon e Geometry,
com motor no núcleo (`org.flatcam.cam.transform`), independente de JavaFX.
Falta apenas Buffer (distância/fator) e a referência "Object" - ambos
pequenos, adicionáveis quando houver demanda real.

### 9.3 Modelo de objetos e projeto versionado

**Fase 1 concluída nesta revisão (2026-09-22): Gerber + Excellon com
estrutura de dados inspirada no `.FlatPrj` do Python, mas sem suporte completo
de importação/exportação desse formato.** O usuário pediu
explicitamente para usar o modelo real do Python em vez de inventar um
formato próprio mais leve - decisão registrada e pesquisada a fundo em
`camlib.py`/`app_Main.py` antes de implementar.

**O que o Python realmente faz** (confirmado lendo o código-fonte, não
suposto):
- `save_project()`/`open_project()` (`app_Main.py` ~10605-10809) escrevem
  `{"objs": [obj.to_dict() para cada objeto], "options": {...}, "version":
  ...}` como JSON (`json.dumps(..., indent=2, sort_keys=True)`), opcionalmente
  comprimido em XZ de verdade (`lzma.open(..., preset=3)` - default
  `global_save_compressed=True`). O carregamento tenta JSON puro primeiro e
  cai para XZ se falhar - sem cabeçalho/flag, é auto-detecção por tentativa.
- Cada geometria Shapely vira `{"__class__":"Shply","__inst__":"<WKT>"}` via
  `shapely.wkt.dumps`/`loads` (`camlib.py:8144-8186`) - **WKT bruto, não
  GeoJSON**. JTS fala WKT nativamente (`WKTWriter`/`WKTReader`), então isso
  interopera sem nenhum dos dois lados conhecer a biblioteca de geometria do
  outro.
- Gerber/Excellon **não guardam path do arquivo-fonte** - só a geometria já
  resolvida (e o texto bruto do arquivo original, como metadado inerte). É
  exatamente essa propriedade que resolve o problema que motivou essa
  mudança (Transformations/edições se perdiam ao reabrir).
- O campo `"version"` é escrito mas **nunca lido de volta** no carregamento -
  é write-only. Compatibilidade retroativa é por tolerância a chave ausente
  (cada atributo é lido num `try/except KeyError`, mantendo o default do
  construtor se faltar), não por migração versionada.

**O que foi implementado** (`org.flatcam.app.project.flatprj`,
`GerberFlatPrjCodec`/`ExcellonFlatPrjCodec`/`WktJson`):
- Mesma estrutura de arquivo (`objs`/`options`/`version`), mesmo wrapper WKT,
  XZ real via `org.tukaani:xz` (preset 3, igual ao default do Python),
  mesma auto-detecção JSON-puro-depois-XZ no carregamento.
- Gerber: `kind`/`units`/`solid_geometry`/`follow_geometry`/`tools`/
  `apertures`/`options.name`/`fill_color`/`outline_color`/`alpha_level`.
- Excellon: furos/slots reagrupados por ferramenta em `tools[id] =
  {tooldia, drills, slots, solid_geometry, data}`, igual ao shape do Python,
  em vez das listas próprias deste port (indexadas por campo `toolId`).
- `ProjectFile.GerberEntry`/`ExcellonEntry` também guardam cor de
  preenchimento/contorno, visibilidade, "Solid"/"Multi-Color" e modo
  "Follow" (Gerber) - reabrir um projeto restaura a aparência inteira, não
  só a geometria.
- Fallback para o formato v1 antigo (só paths) - se o path ainda existir,
  reparseia; se não, pula esse objeto sem falhar o carregamento inteiro.
- Testes de round-trip (`ProjectFileIOTest`), incluindo um que verifica
  que o arquivo salvo por padrão **é XZ de verdade** (bytes não são JSON
  puro) e outro que carrega um projeto v1 legado reparseando o arquivo.

**Simplificações e limites documentados**:
- Projetos novos escrevem uma entrada por flash/stroke/região em
  `apertures[code]['geometry']` e preservam a ordem em `_java.shape_order`.
  Projetos Java anteriores a essa mudança tinham só a união por abertura;
  carregam para plot/CAM, mas são somente leitura no editor.
- Aperture do tipo MACRO volta como um círculo placeholder ao recarregar (o
  texto bruto da macro não é retido) - só afeta a exibição daquela linha na
  Apertures Table; a geometria real (`apertureGeometry()`/`solidGeometry()`)
  é independente e volta exatamente como salva.
- `tools[id]['solid_geometry']` e `['data']` do Excellon voltam vazios (este
  port só mantém uma geometria agregada por todas as ferramentas, e nenhum
  dict de parâmetros CAM persistente por ferramenta) - inofensivo para
  visualizar/gerar G-code, só afetaria um fluxo Python que precisasse da
  geometria isolada de uma ferramenta específica.
- `int_digits`/`frac_digits`/`aperture_macros`/`source_file`/`zeros`/campos
  de formato Excellon não são escritos - o Python tolera a ausência (usa o
  default do construtor); nenhum é necessário para visualizar/gerar CAM.

**Importante**: a importação foi verificada com um `.FlatPrj` 8.994 real e
com conversão/reabertura do `.fcnproj` resultante do lado Java. Ainda não
houve validação visual da abertura no FX nem escrita de `.FlatPrj` pelo FX.
O `.fcnproj` é formato nativo do FX e não deve ser apresentado como projeto
diretamente abrível pelo FlatCAM Python.

**O que falta para completar 9.3**:
- Geometry: parâmetros básicos compartilhados de corte já são importados e
  persistidos, mas ainda falta um dict `data` completo por ferramenta, inclusive
  configurações diferentes entre ferramentas.
- CNC Job: precisa reter uma lista por segmento com "kind" (`gcode_parsed`
  do Python) durante a geração - `GCodeGenerator` hoje só produz duas
  geometrias já unidas (viagem/corte), não uma lista ordenada por segmento.
- O leitor nativo `ProjectFileIO.load()` continua restrito às versões internas
  1 e 2; `PythonProjectIO.load()` importa `.FlatPrj` Python 8.9xx em modo
  somente leitura. O seletor da interface oferece ambos, mas salva apenas
  `.fcnproj`. A conversão de um projeto real 8.994 para `.fcnproj` foi testada
  com os quatro tipos de objeto; a abertura visual ainda requer teste manual.
- O FX grava Geometry e CNC Job em `_java`, não em `objs`; o Python ignora essa
  extensão e não restaura esses objetos. `options` é gravado vazio, portanto
  preferências de projeto do Python também não fazem ida e volta.
- O futuro suporte dual deve preservar `.fcnproj` como formato nativo e ter
  um exportador `.FlatPrj` separado, validado na instalação Python real e com
  aviso explícito quando uma conversão perder objetos ou metadados.
- O undo/redo do Gerber Editor cobre a sessão em memória; histórico de
  comandos entre sessões não é serializado (o resultado aplicado é salvo).

### 9.4 Gerber Editor

Implementar por fatias verticais, não como um bloco único:

1. sessão de edição com Aplicar/Cancelar; ✅ concluída (seção 8);
2. seleção e hit testing; ✅ concluída (seção 8) - `GerberShape`,
   `GerberEditSession.clickSelect/boxSelect`, `GerberEditorController`;
3. command stack com undo/redo; ✅ concluída para operações em memória;
4. mover, copiar e excluir; ✅ deslocamento X/Y e gesto de origem/destino no canvas;
5. pads, tracks, regions e apertures; ✅ pads C/R/O/P/AM, arrays lineares/circulares, trilhas multiponto com cinco modos de dobra, regiões e criação/renomeação/redimensionamento/exclusão de aberturas (macro pode ser usada em pads, mas não redimensionada);
6. operações avançadas do editor legado; 🟡 poligonizar, disco/semidisco, escala, buffer, marcação por área, borracha e transformações têm ação no painel e undo/redo quando alteram formas. Array, disco/semidisco e transformações usam entrada numérica em vez do gesto original no canvas; marcação por área usa o destaque da seleção sem anotações de área. Ainda falta teste manual de ponta a ponta desses controles;
7. persistência e reabertura do resultado editado; ✅ concluída para projetos
   novos; projetos antigos só têm uniões por abertura e não podem recuperar
   as formas individuais.

Depois disso, repetir a estratégia para o Editor Excellon e ampliar Geometry e
CNC Job até a paridade necessária.

Ordem acordada em 2026-09-25: trilhas multiponto/modos de dobra, regiões e edição
de aberturas implementados; o Editor de G-Code agora tem um fluxo inicial de
edição/aplicação/exportação. Próximo passo: validar manualmente os dois editores
no JavaFX com arquivos reais e ampliar a prévia G-code conforme corpus.

### 9.5 Editor de G-Code

Arquivos `.nc`, `.gcode`, `.tap`, `.cnc`, `.txt` e ICP `.imf` podem ser abertos diretamente
como CNC Jobs, com leitura/análise em background, progresso e cancelamento. O
Editor de G-Code abre o texto de um CNC Job em aba central protegida, com
Aplicar, Cancelar e Salvar arquivo no painel lateral. Aplicar substitui o
texto do objeto em memória, sem sobrescrever automaticamente o arquivo de
máquina. O processamento roda em job cancelável, com progresso; falhas mantêm
o rascunho aberto. A prévia é reconstruída para G0/G1 e arcos G2/G3 em XY
(centro I/J incremental ou absoluto G90.1, raio R positivo/negativo, círculo
completo), incluindo G90/G91 e G20/G21. Planos G18/G19, trocas de unidades
durante movimentos e comandos de movimento não suportados deixam
a prévia indisponível, nunca conservam a geometria antiga. Isso não é um
validador de segurança CNC nem reconstrói a largura original da ferramenta.

O projeto salva o nome e texto G-code em `_java.cncJobs`, mantendo leitura dos projetos
antigos que referenciam somente o arquivo externo. Na reabertura, a análise
da prévia também roda em background; se não for possível interpretá-la, o
texto ainda é carregado. Falta validar a UI com arquivos reais e ampliar o
subconjunto modal/planos do parser antes de alegar paridade com o Python.

### 9.6 Editor de Geometry (tres fatias)

A interface foi reorganizada para corresponder ao Python: a aba lateral
"Editor Geometry" mostra a tabela virtualizada `ID | Type | Name` e os
controles de sair/descartar. Os IDs sao estaveis durante mover/desfazer; a
selecao da tabela e do desenho e sincronizada nos dois sentidos, inclusive
para Delete. As ferramentas agora ficam em uma barra superior dedicada, que
substitui a barra geral de ferramentas enquanto o editor esta aberto. O menu
de topo "Geo Editor" so aparece durante a edicao. Funcoes ainda nao portadas
(texto, paint, borracha, transformacoes) aparecem
desabilitadas na barra, sem sugerir que ja funcionam. A escolha de ferramenta
de corte aparece na barra apenas para Geometry multi-tool.

Objetos Geometry abrem pelo botão em Propriedades, menu contextual ou Editar
Objeto. O editor permite seleção por clique ou retângulo (esquerda-direita
inclui formas inteiras, direita-esquerda inclui formas tocadas), Ctrl para
múltipla seleção, Excluir pelo botão ou tecla Delete, Desfazer/Refazer e
Aplicar/Cancelar. Delete funciona enquanto o editor está ativo mesmo com foco
no painel lateral, mas não interfere com campos de texto. Aplicar altera
o objeto em memória, preservando suas ferramentas por forma; o projeto salva
essa Geometry e os caminhos de cada ferramenta em `_java.geometries`. Cancelar
mantém o objeto original. O modelo de seleção usa índice espacial; o destaque
usa uma camada Canvas separada para não repintar todos os objetos a cada
clique. Acima de 2.000 formas selecionadas, só o contorno agregado é exibido,
mas Excluir ainda opera sobre todas as formas selecionadas.

A segunda fatia inclui Caminho/Polígono por múltiplos cliques (Enter, duplo
clique ou botão direito
conclui; Backspace remove o último ponto), Retângulo/Círculo por dois
cliques e Mover/Copiar pela origem e destino no Plot Area. As formas novas são
editáveis, participam do undo/redo e entram no projeto salvo. Em Geometry
multi-tool, o painel permite escolher a ferramenta que receberá a nova forma;
mover/copiar mantém a ferramenta das formas de origem. A prévia de desenho e
movimento usa a camada Canvas leve, sem repintar o projeto inteiro em cada
movimento do mouse. O menu Editor Geometry ativa essas seis ferramentas.

A terceira fatia acrescenta Uniao, Intersecao, Subtracao (a primeira forma
selecionada e o alvo), Buffer arredondado completo/interior/exterior e Explodir
poligonos em segmentos editaveis. As operacoes booleanas exigem pelo menos
duas formas da mesma ferramenta. Buffer cria novas formas sem remover as
originais e preserva a ferramenta de cada uma; os modos interior/exterior
exigem poligonos. Explodir substitui poligonos selecionados por arestas,
incluindo contornos de furos, limitado a 10.000 segmentos por acionamento.
Todas as mudancas participam do undo/redo. Uniao, Intersecao, Subtracao e
Buffer calculam em background, com cancelamento pelo controle geral da tarefa;
em erro ou cancelamento, o rascunho permanece intacto. Durante o calculo, o
editor bloqueia outras alteracoes. O menu Editor Geometry aciona essas funcoes.

Ainda faltam texto, paint, borracha e transformacoes,
teste manual com arquivos reais grandes e perfilamento da renderização inicial
de Geometry muito extensa. A persistência `_java.geometries` é do FX e não
garante abertura de objetos Geometry no FlatCAM Python. O sentido de fresagem
CL/CV do Python (`geometry_editor_milling_type`, padrão Climb) foi portado em
2026-09-29: `GeometryEditSession.setClimbMilling` inverte a direção das formas
novas (linha, polígono, retângulo no anel do Python, círculo, arco; só o anel
exterior de polígonos), com botões Climb/Conventional no painel do editor e
preferência lembrada. Não altera formas já existentes; confira o trajeto antes
de usar o G-code.

### 9.7 CNC Job de furação e Importar/Exportar (2026-09-29)

**CNC Job de Excellon.** O gerador grava em cada troca de ferramenta um
comentário inofensivo para a máquina, `FCFX TOOL T<n> D<diâmetro>`, e o
`GCodeToolpathParser` o usa para desenhar furos e slots no diâmetro real (mesmo
depois de reabrir o projeto, abrir o `.nc` ou editar o G-code) e para montar
`ToolpathStats`: tabela de ferramentas (#, Dia, Drills, Slots, Cut Z) com Plot
por ferramenta, ordem de usinagem numerada no plot ("Display Annotation", uma
numeração igual à do Python desde 2026-09-29: início e fim de cada deslocamento,
incluindo a origem da troca de ferramenta como nº 1), distância percorrida e tempo estimado (descidas no avanço, deslocamentos
a 1500 mm/min como no Python; "—" quando falta F). Jobs de fresagem ainda não
têm o marcador e continuam com traço fino.

**Numeração em jobs de Python e de fresagem (2026-09-29).** O parser também
entende o G-code de Excellon do Python (`T1` + `(MSG, Change to Tool Dia = ...)`,
só quando o cabeçalho diz "G-code from Excellon"), então projetos `.FlatPrj`
mostram furos e números. Jobs de fresagem numeram como o Python: início e fim de
cada deslocamento G0, pulando posições já numeradas (`ToolpathStats.pathMarks`;
no projeto de teste STM32/Dados_Ambientais a Isolação dá 100 números, igual ao
Python). Usam o mesmo "Display Annotation" e o desenho com anti-colisão.

**Setas de direção (melhoria além do Python).** Jobs de fresagem guardam o meio
e o sentido de cada movimento de corte (`ToolpathStats.cutArrows`) e o
`PlotAreaView` desenha um triângulo por célula de tela de 46 px, só em
movimentos com pelo menos 14 px na tela, então mais setas aparecem ao aproximar.
Checkbox "Display Direction Arrows" (ligado por padrão) nas propriedades do job.
Não verificado visualmente em execução.

**Desempenho e desenho do preview de fresagem (2026-09-29).** Jobs que declaram a
largura do cortador (`(TOOL DIAMETER: x)` do Python) passam a ser desenhados como
linhas de centro traçadas na largura real em qualquer zoom (`setLayerCenterlineLod`
com `stroked`), em vez de um polígono bufferizado por segmento, que travava ao dar
zoom. As setas usam níveis pré-calculados por zoom (`ArrowLevels`): posições fixas no
mundo, sem recalcular a cada movimento, e o desenho só percorre a lista do nível
atual. Os números viraram etiquetas com fundo e ponto de ancoragem, e o
deslocamento fino fica tracejado e mais visível.

**Trajeto passo a passo (novo, o Python não tem).** Clique num número ou numa linha
de rota de um CNC Job (ou "Percorrer" nas propriedades do job): o trecho clicado
fica em destaque (amarelo/âmbar), o anterior em azul e o seguinte em laranja/vermelho,
com setas de direção e as passadas finas sobre o corpo largo; o resto do plot
esmaece e os números dos três trechos ganham crachá na cor do papel. Um resumo
("Passo 12/325 - Corte (5 > 6) - 3.9 mm") aparece no plot e nas propriedades.
A faixa do rodapé tem botões clicáveis (anterior, próximo, limpar); as setas do
teclado (esq/dir, cima/baixo) também andam pelos trechos e Esc limpa; clicar no
vazio também limpa. O parser gera `ToolpathStats.steps` (`PathStep`: deslocamento,
corte ou furo, encadeados na ordem do programa, com o número de cada ponta) e
`CncStepView` cuida da seleção, do clique e do desenho. Verificado por captura
fora da tela no projeto STM32/Dados_Ambientais (Furos_Alinhamento e Cobre_MortoFino).

**Jobs de fresagem gerados pelo FX (2026-09-29).** O G-code de Geometry, isolamento
e recorte agora escreve `FCFX MILL D<largura>` (um por ferramenta), que o parser
lê como largura do cortador; jobs de uma só largura ganham o mesmo preview rápido
(linhas de centro traçadas na largura real) dos importados do Python, e os
passos/setas/numeração usam o parser. Jobs com várias larguras seguem no desenho
por polígonos.

**Reprodução, tempo e CSV do trajeto (novo, o Python não tem).** A faixa do passo a
passo ganhou reproduzir/pausar (também com Espaço) e velocidade (0.5x a 8x; cada
passo dura 0,7 s / velocidade). A reprodução acompanha o trecho movendo a vista
(sem mudar o zoom), para ao chegar no fim e ao navegar ou clicar manualmente. A
faixa mostra o tempo estimado decorrido até o fim do trecho e o total
(`PathStep.endMinutes`, mesma base do "Estimated time"; some quando falta F). Nas
propriedades do job, "Reproduzir" e "Exportar CSV" (`CncStepCsv`: passo, tipo,
marcas, coordenadas, comprimento, unidade, duração e tempo acumulado). O tempo de
um furo só aparece no fim do deslocamento seguinte, porque o mergulho é lido junto
com ele.

**Exportar** (menu Arquivo > Exportar):

- Gerber/Excellon: diálogo com as opções `gerber_exp_*`/`excellon_exp_*` do
  Python (unidades com conversão, dígitos, zeros L/T ou LZ/TZ, decimal ou sem
  ponto, slots roteados G00/M15/G01/M16 ou G85), lembrado entre sessões. O
  Gerber continua como regiões do cobre resolvido. O Python exportava slots
  roteados com início = fim e trocava a supressão de zeros; aqui não.
- SVG: Gerber/Excellon/Geometry no visual do shapely; CNC Job com deslocamentos
  (#F0E24D) sob cortes (#5E6CFF) na largura da ferramenta. viewBox corrigido.
- DXF: R12 POLYLINE/VERTEX com `$INSUNITS` e anéis fechados; aceita também
  contornos de Gerber/Excellon (o Python só Geometry).
- PNG: a área de plotagem como está, na escala de saída da tela.

**Importar** (menu Arquivo > Importar), em múltiplos arquivos num job:

- SVG como Geometry/Gerber: unidades reais (px = 1/96 in; 1/72 em arquivos do
  Illustrator e 1/90 no Inkscape < 0.92), origem do viewBox, aspecto, `<use>`,
  transformações compostas, `defs` só quando usados, furos even-odd; no Gerber,
  linhas com traço ganham a largura do traço. Texto é contado, não importado.
- DXF como Geometry/Gerber: `$INSUNITS`, bulges como arcos, SPLINE NURBS,
  INSERT com arrays/rotação, pontas de linha que quase se tocam são unidas; no
  Gerber, contornos fechados viram cobre com furos (even-odd).
- HPGL2 como Geometry: fluxo de comandos HPGL real (vários por linha, PD/PR com
  pontos, CI/AA/AR/AT/RT, retângulos); uma Geometry por caneta.
- O parser Excellon agora lê slots roteados e ferramentas `T01F00S00C...`, o
  formato que o Python exporta.
- PDF: só o estilo que o Python também suporta (um "print" vetorial de
  artes tipo Gerber pelos operadores de caminho do content stream) - sem
  xref/object streams, criptografia, imagens ou rotação/inclinação. Cada troca
  de cor de traço (`RG`) vira um objeto Gerber (`_1`, `_2`, ...); um
  preenchimento branco em forma de curva vira furo (`_0`, Excellon). Ao
  contrário do Python, os operadores são lidos como o fluxo de tokens que um
  content stream realmente é, não um por linha - a suposição de "um operador
  por linha" do Python faz com que ele descarte silenciosamente qualquer
  operador que divida a linha com outro, o que geradores reais fazem (o
  reportlab, por exemplo, escreve `n` seguido de `re S` inteiro na mesma
  linha); sem esse ajuste, PDFs reais simplesmente não importavam nada.
  Também corrigido: um preenchimento de Bezier encadeado gerava polígonos
  extras errados (Python nunca zerava os pontos entre os segmentos); o último
  ponto de cada Bezier era descartado; a largura/altura de um retângulo levava
  o deslocamento em dobro; o operador de curva `y` nunca marcava seu subcaminho
  como curva; e `s`/`b`/`b*` nunca eram reconhecidos, mesmo com os padrões já
  compilados no Python. Os valores geométricos foram conferidos rodando o
  próprio `ParsePDF.PdfParser` do Python lado a lado (stub só da dependência
  Qt) com o mesmo content stream, e por fim contra um PDF real gerado pelo
  reportlab.

Ainda não portado: Imprimir PDF e backup de preferências.
Posições no SVG e no PDF importados são relativas à página (canto inferior
esquerdo na origem), como no Python e no Inkscape.

## 10. Regras de implementação para qualquer IA

### Use o Python como oráculo

Antes de portar uma função, localize o fluxo real em `appGUI`, `appTools`,
`appEditors`, `camlib.py` e arquivos relacionados. Consulte também
`UI_INVENTORY.md`. Registre:

- entradas, defaults e unidades;
- validações e mensagens;
- objetos produzidos e seus metadados;
- comportamento de plot e seleção;
- efeito de cancelamento;
- formato da saída.

Se o ambiente Python não puder executar por dependência ausente, ainda é
possível inspecionar o código e criar fixtures. Não invente paridade apenas a
partir do nome de um controle.

### Preserve as fronteiras

- Nada de JavaFX em `flatcam-cam` ou `flatcam-application`.
- Parsing e geometria não devem conhecer controles de tela.
- Trabalho pesado não roda na JavaFX Application Thread.
- Todo loop potencialmente longo deve ter checkpoints de cancelamento.
- O progresso deve ser monotônico, limitado a `0.0..1.0` e baseado em unidades
  de trabalho conhecidas; use indeterminado somente quando necessário.
- A UI só publica objetos completos. Falha/cancelamento não deixa metade de um
  resultado na árvore.

### Compare geometria corretamente

- Normalize ou compare com tolerância.
- Não dependa da ordem de componentes/pontos quando ela não tem semântica.
- Teste área, envelope, comprimento, quantidade de elementos e validade.
- Guarde fixtures reais pequenos; não dependa apenas de exemplos sintéticos.
- Adicione regressão para todo bug de parser ou CAM corrigido.

### Não esconda simplificações

Uma fatia menor é aceitável quando termina em um fluxo utilizável. Marque no
código/documento o que ficou faltando e não apresente como paridade completa.

### Preserve o trabalho existente

Antes de editar, execute `git status --short`. Mudanças preexistentes são do
usuário até prova em contrário. Não faça reset destrutivo, não reformate o
repositório inteiro e mantenha commits focados.

## 11. Comandos confiáveis

Execute a partir de `flatcam-next`.

### Windows PowerShell

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-25.0.4.1"   # ou o JDK 25 instalado; reabra o terminal se mudar a variável do usuário
.\mvnw.cmd -q clean test
.\run.cmd                      # usa o launcher nativo se achar o g++, senão o javafx:run
.\run-native.cmd --verbose-gpu # compila e abre pelo FlatCAMFX.exe; mostra o adaptador D3D usado
```

O launcher nativo exige o `g++` do MSYS2 UCRT64 em `C:\msys64\ucrt64\bin`. `build-native.cmd` apaga
`flatcam-fx\target\dependency` antes de copiar os jars, para não misturar JavaFX de versões diferentes.

### Linux/macOS

```bash
./mvnw -q clean test
./mvnw -q install -DskipTests
./mvnw -q -pl flatcam-fx org.openjfx:javafx-maven-plugin:0.0.8:run
```

No Windows, `run.cmd` executa `install -DskipTests` antes do `javafx:run`.
Ao executar apenas `flatcam-fx`, Maven pode resolver `flatcam-cam` e
`flatcam-application` a partir de snapshots antigos em `~/.m2`. O app pode
abrir e falhar somente ao clicar numa ferramenta cuja classe nova não esteja
instalada. Se não usar o script, execute esses dois comandos em sequência.

Use o goal JavaFX totalmente qualificado; `javafx:run` curto pode não ser
resolvido sem `pluginGroups` no `settings.xml`.

## 12. Mapa de arquivos

| Caminho | Papel |
| --- | --- |
| `flatcam-application/.../job/` | executor, contexto, handle, progresso e cancelamento de jobs |
| `flatcam-application/.../project/` | schema e IO do `.fcnproj` (v2: Gerber/Excellon e Geometry com geometria embutida) |
| `flatcam-application/.../project/flatprj/` | codecs compatíveis com o `.FlatPrj` do Python - `WktJson`, `GerberFlatPrjCodec`, `ExcellonFlatPrjCodec` |
| `flatcam-cam/.../gerber/` | parser e geração de geometrias Gerber |
| `flatcam-cam/.../excellon/` | parser e modelo Excellon |
| `flatcam-cam/.../isolation/` | Isolation Routing |
| `flatcam-cam/.../cutout/` | Board Cutout |
| `flatcam-cam/.../ncc/` | Non-Copper Clearing (multi-tool + Rest Machining) |
| `flatcam-cam/.../geometry/` | modelo por ferramenta e sessão de seleção/exclusão do Editor Geometry |
| `flatcam-cam/.../transform/` | motor de Transformations (Rotate/Scale/Skew/Mirror/Offset) - `TransformOp` (sealed), `TransformReference` e `AlignObjects` |
| `flatcam-cam/.../convert/` | ferramentas Gerber→Gerber/Excellon: `OutlineToArea`, `InvertGerber`, `Subtract`, `ExtractDrills`, `Punch`, `EtchCompensation`, `Fiducials`, `CornerMarkers`, `QrCodeMarker` |
| `flatcam-cam/.../merge/`, `.../panel/`, `.../solderpaste/` | Join Objects (`GerberJoin`, `ExcellonJoin`, `GeometryJoin`), `Panelize`, e `SolderPaste` (geometria de dispensa + G-code Paste_1) |
| `flatcam-cam/.../svg/FilmExporter.java` | Film Tool: SVG, PNG (Java2D) e PDF vetorial |
| `flatcam-fx/.../DensityRaster.java`, `DenseRenderer.java`, `PlotDrawableIndex.java` | LOD por densidade do Plot Area: rasterização por cobertura de área, thread de fundo e índice de partes visíveis |
| `native-launcher/`, `build-native.cmd`, `run-native.cmd` | launcher nativo (C++ + JNI) que cria a JVM no próprio processo; ver `NATIVE_GPU.md` |
| `flatcam-cam/.../gcode/` | parâmetros, geração e resultado de G-code; `GCodeToolpathParser` também extrai `ToolpathStats` |
| `flatcam-cam/.../svg/`, `.../dxf/`, `.../hpgl/`, `.../pdf/` | importadores/exportadores SVG, DXF, HPGL2 e importador de PDF (ver seção 9.7) |
| `flatcam-fx/.../CamExportDialog.java`, `PlotPngExporter.java`, `CncJobToolsTable.java` | diálogo de formato Gerber/Excellon, exportação PNG e tabela de ferramentas do CNC Job |
| `flatcam-fx/.../MainWindow.java` | integração principal da UI; atualmente grande demais |
| `flatcam-fx/.../PlotAreaView.java` | Canvas, viewport e desenho das camadas |
| `flatcam-fx/.../*ToolPanel.java` | painéis JavaFX por ferramenta |
| `flatcam-fx/.../Icons.java` | resolução/tratamento de ícones claro/escuro |
| `flatcam-fx/src/main/resources/.../theme/` | tokens e componentes CSS dos temas |
| `flatcam-cam/src/test/` | testes unitários, baselines e fixtures CAM |

Os caminhos acima omitem o prefixo comum
`flatcam-next/<modulo>/src/main/java/org/flatcam/` para facilitar a leitura.

## 13. Definition of Done de uma fatia funcional

Uma funcionalidade não está pronta apenas porque o botão aparece. Para marcar
uma fatia como concluída:

- comportamento e defaults foram comparados com o Python;
- cálculo principal está fora da UI;
- erros de entrada têm mensagens úteis;
- operação pesada tem progresso/cancelamento quando aplicável;
- há testes positivos, limites e regressões relevantes;
- falha/cancelamento não corrompe o estado do projeto;
- UI completa o fluxo até um resultado observável/salvável;
- temas claro e escuro foram verificados quando há mudança visual;
- `clean test` passa;
- app inicia depois de `install` do reactor;
- documentação de progresso/limitações foi atualizada;
- commit é focado e descreve o resultado.

## 14. Checklist de início para a próxima IA

1. Ler este arquivo, o contexto arquitetural e o inventário de UI.
2. Rodar `git status --short` e `git log -5 --oneline`; conferir `echo $env:JAVA_HOME` (JDK 25).
3. Rodar `clean test` para estabelecer baseline (462 testes em 2026-10-01).
4. Confirmar no código Python o próximo comportamento a portar.
5. Trabalhar primeiro no núcleo e nos testes, depois ligar a UI.
6. Instalar o reactor e executar o smoke test JavaFX.
7. Atualizar este documento se o estado ou a prioridade mudou.
8. Mostrar `git diff --check`, revisar o diff e só então commitar.

## 15. Resumo executivo

**Persistência das configurações CNC (2026-10-01, após `77140231`).**
Geometry e Excellon agora preservam o perfil escolhido no `.fcnproj` v2, com
`GeometryCncSettings` e `DrillCncSettings` opcionais, mantendo construtores/arquivos
anteriores compatíveis. Geometry salva diâmetro informado (quando não tem ferramentas
associadas), V-Tip Dia/Angle por índice e os parâmetros usados na última geração;
o callback antes não atualizava esses defaults na origem. Drilling salva Tool change,
altura de troca, End Z/XY, Feed rapids, sondagem, seleção e ordem das ferramentas,
além dos defaults individuais já existentes. Os dados são registrados somente
após gerar/salvar o programa com sucesso; rascunhos não enviados não são salvos.
Trocar a origem restaura dados daquele objeto; copiar mantém os registros, remover
ou limpar o projeto os elimina. Confirmação de sondagem nunca persiste e deve ser
refeita no painel. IDs excluídos não selecionam outras ferramentas e são avisados.
Perfil novo desconhecido/configuração inválida recusa a abertura sem fallback
silencioso. A importação `.FlatPrj` continua com o subconjunto anterior; parâmetros
globais/perfis Python não foram mapeados aqui. Metadados Roland nos CNC Jobs e
opções CAM adicionais continuam pendentes. Testes regeneram código idêntico para
20 perfis Geometry e 15 Drilling em JSON/XZ, e exercitam formulários na thread FX,
precisão, V-tip, subconjunto/ordem, confirmação, troca de origem/reset e versões
sem os novos campos. Fluxo completo na janela principal aguarda teste manual.

**Sexto incremento de pós-processadores (2026-10-01, após `d87cc986`).**
Adicionado Toolchange_Probe_MACH3 em Geometry e Drilling: troca Tn/M6 obrigatória,
duas sondagens G31 (a segunda com metade do avanço), G92 Z de contato e XY de troca
opcional. Exige RPM positivo, números finitos/representáveis e alturas coerentes.
O painel exige confirmação dos cuidados; em cada G31 há M0 antes de aplicar G92
para confirmar contato real ou abortar, diferença deliberada do legado. Depois
há pausa para remover placa/clips antes de ligar spindle. G92 permanece ativo;
sensor, curso físico, offsets e macro M6 precisam ser validados pelo operador.
Não há conexão com hardware nem detecção automática de falha da sonda.
Prévia/estatísticas ficam indisponíveis para G31/G92, inclusive abrir, Aplicar e
reabrir projeto; o código continua editável/exportável. A geração não calcula
footprints inutilizados nesse perfil. Defaults de Geometry persistem opcionalmente
no `.fcnproj`; configuração global de Drilling ainda não persiste. Geradores
diretos de Isolation/Cutout recusam sondagem sem parâmetros e orientam usar Geometry.
Testes do ciclo, falhas/configuração, furos/slots/Multi-Depth, MM/IN, cancelamento,
persistência e controles FX passaram no build completo. Teste manual e físico pendentes.
Inventário fechado: 19 perfis Python selecionáveis + Paste_1 separado = **20/20 ports
parciais**, não paridade completa. Detalhes/limites em `PREPROCESSADORES.md`.

**Quinto incremento de pós-processadores (2026-10-01, na sequência do HPGL).**
Adicionado Roland_MDX_20 em Geometry e Drilling, com arquivo RML-1 nativo `.rml`/`.prn`,
motor !MC1/!MC0, XYZ absolutos em 1/40 mm e velocidades V de 0,1..15 mm/s. Por enquanto
somente MM e uma ferramenta por arquivo: IN, múltiplas ferramentas, troca mecânica e dwell
são recusados na API, com campos equivalentes bloqueados no painel sem apagar rascunhos.
RPM não é emitido; o motor liga durante a usinagem mesmo com o valor ignorado de RPM = 0.
Rapid = 0 usa 900 mm/min; avanços fora de 6..900 são recusados, corrigindo o mínimo do Python
sem seu clamp silencioso. O primeiro XYZ assume origem XY; cabe ao operador posicionar e
conferir alturas. A prévia lê ^IN/^PA/Z/V/!MC e aceita o motor sem terminador do legado,
recusa resets/comandos não modelados, preserva furos/slots e estima tempo pela V efetiva.
Arquivos nativos não contêm metadados de ferramenta: ao reabrir/aplicar (também projetos)
o percurso volta, mas com largura fina e sem tabela de ferramentas de furação; a geração
mantém a largura real. Isso permanece como pendência para um suplemento de metadados.
Testes do codec, Geometry/Drilling, V-tools, limites, cancelamento, persistência e controles
FX passaram. Teste manual e no equipamento pendentes. Total: 19 de 20 ports parciais,
incluindo Paste_1; resta Toolchange_Probe_MACH3. HPGL e Roland são entregues nesta rodada.

**Quarto incremento de pós-processadores (2026-10-01, após `d61304e6`).** Adicionado HPGL em
Geometry → CNC Job, com saída nativa `.plt` (IN/PU/PD/PA/SP/CO), canetas por ordem de ferramenta,
quantização de 0,025 mm e conversão de MM/IN. Mantém as larguras/unidades de origem em CO e gera
o plot a partir dos pontos arredondados. Não emite Z, spindle ou feed; a interface desabilita esses
campos, Multi-Depth, V-tip e troca mecânica sem apagar valores. Coordenadas fora de -32767..32768,
pontos isolados e caminhos colapsados são recusados, sem o clipping silencioso do Python. Finaliza
com PU/SP0. A prévia CNC cobre comandos lineares absolutos/relativos, recusa resets/transformações
e outros comandos não modelados e informa tempo indisponível. Importar HPGL2 continua como fluxo
separado para Geometry. Abertura, editor, exportação e reabertura de projeto aceitam HPGL. Testes
de geração/leitura, unidades, limites, cancelamento, persistência embutida e controles FX passaram;
teste manual do painel e do plotter pendentes. Total: 18 de 20 ports parciais, incluindo Paste_1.
Na sequência: Roland_MDX_20 (entregue acima) e depois Toolchange_Probe_MACH3.

**Terceiro incremento de pós-processadores (2026-10-01, após `3d891d08`).** Adicionados line_xyz e
ISEL_ICP_CNC: 16 perfis Python no seletor mais FX portable, total 17 de 20 ports parciais incluindo Paste_1.
line_xyz escreve XYZ em todos os movimentos; o parser identifica mergulhos por deslocamento real,
sem confundir X/Y repetidos com movimento lateral. O codec ICP gera FASTABS/MOVEABS, VEL, GETTOOL,
SPINDLE, WAIT e PROGEND em unidades inteiras; normaliza o subconjunto para a prévia compartilhada,
com cancelamento/progresso. Aceita apenas MM; não copia o WPCLEAR/retorno Z0 final do Python.
GETTOOL automático independe da pausa manual e é seguido de reafirmação da altura livre. UI usa
extensão .imf na geração/abertura/edição; texto nativo embutido reabre sem depender do arquivo externo.
Comandos ICP desconhecidos, resets de origem e movimentos relativos invalidam a prévia. Testes cobrem
conteúdo nativo, unidades, furos/slots, tool changes, limites e os controles JavaFX sem janela visível.
Restam hpgl, Roland_MDX_20 e Toolchange_Probe_MACH3; validação visual e física continuam pendentes.

**Segundo incremento de pós-processadores (2026-10-01, após `c718e582`).** Adicionados ISEL_CNC,
Toolchange_Manual e Toolchange_Custom: 14 perfis Python no seletor (mais FX portable), 15 de 20
ports parciais incluindo Paste_1. ISEL emite G71, aceita somente MM e troca por M06/M01; o parser
interpreta G71 apenas com identificação do perfil. Manual inclui três M0 e G01 Z0 com feed explícito;
Custom depende da macro M6 da máquina. Após M6/M06 o gerador reafirma G90 e altura livre, sem simular
a macro. Geometry permite ativar a troca inicial também em jobs de ferramenta única. Testes cobrem
geração/reabertura com duas ferramentas, rejeição de IN no ISEL e os controles JavaFX. Pendentes:
ISEL_ICP_CNC, Roland_MDX_20, hpgl, line_xyz e Toolchange_Probe_MACH3. Detalhes em PREPROCESSADORES.md.

**Incremento de pós-processadores (2026-10-01, após `1b67894e`).** Adicionados Marlin, Repetier,
Berta_CNC, GRBL_laser, Marlin_laser_FAN_pin, Marlin_laser_Spindle_pin e Z_laser. Furação oferece
somente fresagem; Geometry oferece os 12 perfis do seletor (11 do Python + FX portable). Laser
tem geração própria, saída desligada antes dos deslocamentos, potência validada e parser baseado
em M/S para conservar a prévia ao reabrir/aplicar. UI desabilita parâmetros de mergulho/troca no
laser. Feed rapids Marlin/Repetier é configurável e o valor de Geometry é persistido, com fallback
automático para projetos antigos. Repetier usa FAN/PWM e @pause pelo host; Marlin mantém M6 do
Python, que exige verificar suporte no firmware. A contagem antiga de 21 era de arquivos: existem
20 classes de perfil, excluindo __init__.py; 12 têm port parcial incluindo Paste_1. SolderPaste
não foi alterado: permanecem as duas falhas identificadas na revisão. Detalhes, diferenças,
limites e testes em `PREPROCESSADORES.md`; validação física não foi feita.

O FlatCAM FX já deixou de ser um esqueleto: carrega e plota Gerber/Excellon,
tem uma árvore lateral próxima do legado, jobs com progresso/cancelamento,
Isolation, Cutout, NCC multi-tool com Rest Machining e boundary por
referência, Geometry -> CNC, G-code, e Transformations (Rotate/Skew/Scale/
Flip/Offset) completas para os três tipos de objeto. O Gerber Editor já
seleciona, exclui, move e copia formas, tem undo/redo, aplica um novo objeto e
salva/reabre suas formas individuais em projetos novos. A paleta inteira tem
ações funcionais: pads C/R/O/P/AM, arrays, trilhas multiponto com cinco modos
de dobra, regiões, discos, semidiscos, poligonização, edição de aberturas,
marcação por área, borracha, escala, buffer e transformações. As operações de
edição participam do undo/redo e da persistência do projeto; ferramentas
avançadas numéricas ainda não reproduzem todos os gestos do Python.
"Salvar como..." exporta
o cobre atual como Gerber válido, embora ainda sem preservar a semântica das
aberturas originais. Os menus Importar (SVG, DXF, HPGL2) e Exportar (SVG, DXF,
PNG, Gerber, Excellon com formato escolhido, e Importar PDF) funcionam; só
"Imprimir PDF" e o backup de preferências faltam (seção 9.7).
CNC Jobs de furação mostram furos no diâmetro real, tabela de ferramentas,
ordem de furação e tempo estimado. O Plot Area seleciona objetos por clique/retângulo e abre
menus funcionais no botão direito e abre Propriedades com duplo clique. O
Editor de Geometry tem seleção/exclusão visual, desenho básico, mover/copiar,
undo/redo, Aplicar/Cancelar e
salvamento no projeto, com índice espacial e destaque isolado para evitar
repintura integral ao clicar. Editor de G-Code agora abre arquivos diretamente, edita o texto do CNC Job, aplica em background com
progresso/cancelamento e salva o rascunho como arquivo. O projeto embute esse
texto para que a edição sobreviva à reabertura; a prévia G0-G3 em XY é reconstruída
sem reutilizar um plot antigo após mudança. O próximo trabalho recomendado é
validar manualmente os dois editores com arquivos reais e conferir Gerber
exportado em um visualizador independente. Ferramentas avançadas do Editor
Geometry, plot CNC com largura de ferramenta e lacunas de NCC seguem no roadmap.

**Estado em 2026-10-01.** O projeto roda em Java 25 + JavaFX 25.0.4 (ganho medido de ~2-3x na primeira
isolação do projeto real) e já porta as 24 ferramentas do menu Ferramentas do Python (2-Sided, Align Objects,
Extract Drills, Paint, Panelize, Film, SolderPaste, Subtract, QRCode, Fiducials, Punch Gerber, Invert Gerber,
Corner Markers e Etch Compensation, além das anteriores), mais Outline→Area e Join Objects. O Plot Area aguenta
geometrias com centenas de milhares de traços com um LOD por densidade assíncrono. Restam
ampliar a integração da Tools Database (editor já entregue), salvar `.FlatPrj` e completar parâmetros/validação dos pós-processadores.
A suíte atual registra 695 testes (686 executados sem falhas, 9 ignorados); a validação manual dos painéis
recentes no app real é a principal pendência de qualidade. A fila detalhada está na seção 9.0.

### Tools Database: editor visual/funcional (2026-10-01, após `9c229bba`)

`Opções > Tools Database` agora abre uma aba reutilizável na área central, não um placeholder.
`ToolsDatabasePanel` reproduz a lista ID/Tool Name e as seções Description, Milling, Drilling,
Isolation, Paint, NCC e Cutout de `appDatabase.py::ToolsDB2`. Os **63 campos** são verificados
contra as chaves do formulário Python em teste automatizado. Os ícones dos comandos são os
mesmos assets Python, com variantes claras/escuras. Colunas de parâmetros agrupadas, rolagem,
cards recolhíveis e botões que quebram linha evitam exigir a largura fixa do Python.

- Adicionar, copiar uma/múltiplas ferramentas, excluir com confirmação, buscar nome/ID/diâmetro
  e filtrar operação. IDs existentes são preservados e novos IDs não sobrescrevem entradas esparsas.
- Apenas uma ferramenta pode ser editada por vez; seleção múltipla permite copiar/excluir.
- Parâmetros dependentes ficam desabilitados conforme checkbox/shape/gap type. V-tool calcula
  o diâmetro efetivo a partir de V-Dia, V-Angle e Cut Z, quando esses campos são alterados.
  `Laser_lines` de Paint fica desabilitado no seletor como no Python; valores importados são preservados.
- Alterações são validadas por **Aplicar parametros**, troca de seleção ou Save DB. Valores inválidos
  bloqueiam a troca/uso sem apagar o rascunho. Campos não modificados e desconhecidos, inclusive
  preprocessadores e configurações avançadas, não são substituídos por defaults.
- Import DB e Save DB rodam via `JobExecutor`; falhas mantêm a base/rascunho atual. Save DB associa
  o arquivo à base e limpa `*`; Export DB escreve uma cópia e mantém a associação/estado de edição
  (exportar sobre o próprio arquivo ativo equivale a salvar). O último arquivo associado é lembrado.
- Escrita JSON UTF-8 compatível com Python, publicação atômica e backup exato `.bak` com nome único
  antes de sobrescrever. Links simbólicos e destinos que não sejam arquivos são recusados.
  Não há migração destrutiva nem gravação automática na base Python.
- Fechar aba/aplicativo com alterações pede confirmação para descartar; Cancelar permite voltar
  ao editor e salvar. Durante I/O, fechamento é bloqueado. Ctrl+S salva e Ctrl+F foca a busca;
  atalhos globais do Plot não são aplicados enquanto a aba da base está ativa.
- Isolation/NCC/Drilling passam a ler o snapshot validado do editor, incluindo alterações ainda
  não salvas. Se a aba nunca foi aberta, preservam o seletor de arquivo já existente. A conversão
  desse fluxo antigo ainda é síncrona; somente Import/Save/Export do editor são assíncronos.

**Limites:** ainda não é paridade total da base. A transferência para Milling/Geometry, Paint e
Cutout e a reprodução do botão contextual “Transfer the Tool” do Python não foram implementadas
nesta etapa. Os 63 campos são editáveis/persistidos, mas os adaptadores CAM existentes só consomem
os parâmetros que já suportavam; editar um campo avançado não garante que o gerador FX o utilize.
Defaults de ferramentas novas são valores portáveis fixos, não as preferências globais completas
do Python. `.FlatDB` não especifica unidades: não há conversão automática de MM/IN. IDs inválidos,
diâmetro não positivo, tolerâncias invertidas e arquivos maiores que 10 MB são recusados.

**Ajuste futuro — tamanho da Tools Database:** o limite fixo de 10 MB é uma proteção
conservadora herdada dos leitores `.FlatDB` do FX, não uma exigência do Python, do formato
ou do JavaFX, nem um valor determinado por benchmark. Deve ser revisado para permitir
bases legítimas maiores: avaliar limite configurável e/ou aviso antes de carregar, mantendo
I/O em segundo plano e medindo memória/tempo de parsing e cópia. Revisar conjuntamente
os leitores CAM, o editor e a gravação, que hoje aplicam esse limite. Ele se refere apenas
à Tools Database; não limita Gerbers, Geometry, CNC Jobs ou projetos. Nesta entrega o
comportamento de recusa permanece inalterado.

**Verificação:** 7 testes de documento/backup/compatibilidade e 15 testes de painel, incluindo os
quatro temas e largura reduzida. Capturas `target/tools-db-*.png` podem ser geradas com
`-Dflatcam.tests.snapshots=true` e foram inspecionadas. `install` completo: 684 registrados,
675 executados, 0 falhas/erros, 9 ignorados. O teste de importação inválida imprime intencionalmente
“Job failed”, sem falha na suíte. Falta validação manual das confirmações, FileChooser, troca de
temas no app e round-trip com a base pessoal do usuário aberta novamente no Python.

**Próximo passo recomendado nesta frente:** seleção/transferência de ferramenta da base para
Geometry/Milling, Paint e Cutout, seguida de mapeamento explícito dos parâmetros ainda ignorados.

### Tools Database: contraste das confirmações e tooltips (2026-10-01, após `358da42c`)

As confirmações da base herdam explicitamente os stylesheets da janela atual. O botão padrão
usa `-fc-accent` e `-fc-on-accent`, com estados hover/pressionado/foco nos dois estilos de
componentes. Quatro testes JavaFX verificam contraste mínimo de 4,5:1 nos quatro temas e
nesses estados; capturas sem janela visível foram inspecionadas.

`ToolsDatabaseDescriptions` adiciona ajuda em português baseada em `ToolsDB2UI` do Python,
cobrindo os 63 campos, seus rótulos, sete seções, oito botões, busca, filtro, lista e arquivo
associado. Explica unidades, opções, dependências e quais campos ainda não são transferidos
para CAM no FX. Rótulos continuam habilitados e permitem consultar a ajuda de inputs opcionais
desabilitados. Aplicar/Salvar/Exportar têm explicações distintas sobre memória, disco e backup.

Os textos usam título e corpo do `FluidTooltips`, com animação, quebra de linha e a paleta
do tema atual, sem um segundo popup nativo concorrente. O menu de botão direito da base
é registrado por `attachContextMenu`, após seus nós estarem disponíveis; fechar o menu
fecha também sua ajuda. Não houve mudança de parâmetros, formato de arquivo ou geração CAM.

Cinco testes verificam cobertura/conteúdo e dois verificam os metadados nos controles,
rótulos (inclusive inputs desabilitados), seções e ações contextuais. `install` completo:
695 registrados, 686 executados sem falhas/erros, 9 ignorados. Os testes cobrem conteúdo e
integração nos nós; hover/animação e menus precisam da confirmação manual no app real.

### Tooltips contextuais em todo o FX (2026-10-02)

O formato estruturado de `TooltipContent` agora é compartilhado por `ToolDescriptions`,
pela Tools Database e pela adoção dos tooltips nativos pelo `FluidTooltips`. Preserva os
textos existentes, com parágrafos, opções em negrito, unidades em destaque azul e notas
`Atenção:`/`Integração FX:` em destaque âmbar, adaptados aos quatro temas. Não interpreta
HTML/Markdown. Mantém os tempos/animações e o fechamento por clique, scroll e teclado.

`PanelTooltips` acrescenta ajuda contextual aos formulários CAM, editores, propriedades
dos objetos e preferências, por meio de `MainWindow.openToolPanel`/`showProperties`.
Descreve parâmetros como profundidades, avanços, ponta V, passes, seleção de área,
rest machining, pontes, espaçamentos, transformações e unidades especiais de calculadoras
e QR Code. Os significados de Margin, Method e outros rótulos ambíguos dependem da ferramenta;
os avisos de transferência da base não são reutilizados nos painéis de operação.

Os rótulos oferecem ajuda mesmo com o input desabilitado. Linhas criadas dinamicamente e
rótulos que mudam conforme o pós-processador atualizam a ajuda; trocar o input de uma linha
não deixa sua descrição vinculada ao controle antigo. O instalador não percorre skins ou
células virtualizadas. Tooltips nativos de células continuam sendo atualizados pelo controle.
Botões textuais óbvios não recebem uma repetição do próprio nome; identificação de comandos
somente com ícone e descrições de ações não óbvias são preservadas.

Os diálogos de exportação Gerber/Excellon e de cor/opacidade também registram o
`FluidTooltips` em suas próprias cenas, herdando o tema da janela quando houver owner e
fechando o popup ao encerrar o diálogo. A ajuda distingue explicitamente L/T do Gerber
e LZ/TZ do Excellon. Nenhuma alteração em geometria, parâmetros CAM ou formatos de projeto.

Validação: nove novos testes cobrem contexto, unidades, as 24 descrições do menu Tools,
rótulos/input desabilitado, linhas dinâmicas, troca de input, preservação de ajuda nativa,
ações óbvias versus ícones, células e instalação em diálogos. Suíte `flatcam-fx`:
133 registrados, 131 aprovados, 2 ignorados, zero falhas/erros. Capturas sem janela visível
de NCC, Cutout, sondagem e exportação nos quatro temas foram inspecionadas. A suíte completa
foi tentada também com diretório temporário isolado, mas falhou na limpeza de `@TempDir`
(`DirectoryNotEmptyException`) em testes de persistência/CAM, sem falhas de asserção;
essa execução não deve ser registrada como uma regressão completa aprovada.
# Série de paridade — 2026-10-02

Etapa 4: exportação Python 8.994 `.FlatPrj` (JSON/XZ) no diálogo Salvar Projeto,
além do formato nativo. Gerber/Excellon/Geometry/CNCJob ficam em `objs`; parâmetros
CNC nativos são complementados por metadados privados. Reimportação de arquivo
reserializado pelo Python recupera parâmetros por ferramenta e ponta V disponíveis.
Corrigida prioridade de unidades Excellon: coordenadas seguem `units`, não o
cabeçalho original `excellon_units`. Fixture autorizada tinha MM/INCH discordantes.
Validação headless com os serializadores/parsers reais Python 3.11 e Shapely 2.1.2:
18 objetos do projeto real aprovados, além de exemplo JSON/XZ e reabertura FX após
reeserialização Python. Original preservado; artefatos privados somente em target.
Limitações e comandos em COMPATIBILIDADE_FLATPRJ.md. Sem declaração de paridade
integral ou validação visual da janela Python.

Regressão final da série: `mvnw.cmd -q test` concluído com sucesso, 727 testes
registrados (716 aprovados, 11 ignorados), zero falhas/erros. CAM: 483; Application:
108 (9 ignorados); FX: 136 (2 ignorados). Houve tentativas anteriores com erro de
limpeza de temporários Windows, sem falha de asserção; a repetição final completa
passou sem desabilitar a limpeza. Testes opcionais com o projeto real e com o
arquivo reserializado pelo Python também executados e aprovados separadamente.

Etapa 3: Geometry/CNC possui parâmetros individuais por linha e Aplicar a todas.
Edições inválidas não desaparecem ao trocar a seleção; todas as linhas são validadas.
Inclui Feedrate Z distinto de XY, dwell após ligar spindle e Extra Cut em caminhos
fechados (no máximo um perímetro adicional). Defaults, campos novos e mapa por ferramenta
persistem no formato nativo; versões antigas usam XY como Feed Z. Tools Database
transfere esses campos em Milling. Troca/probing, avanço rápido e perfil são comuns.
Laser/HPGL/Roland/sondagem continuam com parâmetros comuns e aviso explícito;
diferenças individuais não são aceitas para Roland/sondagem. Testes de gerador,
perfis, persistência e controles FX aprovados. A execução adicional ProjectFileIOTest
teve erros somente na limpeza de temporários Windows, sem falha de asserção.

Etapa 2: Paint/Cutout possuem seleção e aplicação explícita da Tools Database,
incluindo aviso de unidades e campos suportados. Paint aplica uma ferramenta e seus
parâmetros comuns, preservando seleção/ordem/Rest. Combo usa índice Python 4;
Laser Lines (3), padrões inválidos e pontas V são recusados nestes fluxos.
Cutout transfere diâmetro, margem, convexidade, padrão/tipo de gap e M-Bites;
preserva gaps manuais. Cut Z e Thin Depth continuam configurados na Geometry,
com aviso explícito. Testes de adaptadores e controles FX aprovados.

Etapa 1: Tools Database integrada a Geometry/CNC e Milling Excellon. Seleção não
altera valores até Aplicar; filtra Milling/General e valida diâmetro, Cut Z e ponta V.
Geometry com caminhos associados não aceita mudar a largura sem regenerar os caminhos.
Milling preserva perfil, ponta V e parâmetros básicos na Geometry criada. A unidade
da base não é convertida automaticamente; a interface avisa. Offset, dwell, Feed Z
e extra cut ainda não são transferidos nesta etapa. Parâmetros por ferramenta seguem
na etapa 3. Testes direcionados: MillingDatabaseTest, DatabaseTransferTest e
GeometryCncToolPanelTest aprovados.

## Geometry → CNC: compensação e posições — 2026-10-02

Tool Offset Path/In/Out/Custom por ferramenta, sem modificar a Geometry original.
Buffer mitrado compatível com a estratégia Python; linhas fechadas viram polígonos,
linhas abertas usam contorno de buffer positivo e compensações que eliminam um
elemento são recusadas. Custom zero exige Path. Todos os rascunhos são validados.

Painel recolhível com Start Z, End Z/XY e Tool change Z/XY comuns. Retração antes
de XY e pausa/troca, inclusive primeira troca com posição explícita; movimentação
final em altura segura antes de baixar ao End Z (diferença deliberada do Python).
Após M0/M6, G90 e altura de troca são reafirmados antes do próximo movimento XY.
Altura de troca não pode ser menor que o maior Travel Z. Movimentos de troca/fim
constam da prévia; corrigida também a prévia do retorno XY entre passes abertos.
Novos campos só em fresagem sem sonda; perfis incompatíveis recusam configurações
ativas em vez de ignorá-las. A sonda conserva seu painel próprio.

Persistência nativa retrocompatível; `.FlatPrj` guarda offset/valor e posições nos
campos CAM padrão. End XY comum do objeto tem prioridade sobre cópia antiga da
ferramenta, como no Python. Opções globais servem de fallback quando o dado por
ferramenta falta. Reimportação após reserialização Python conserva os novos valores.

Testes de núcleo, persistência e controles JavaFX aprovados. Serializadores/parser
Python validaram exemplo avançado (Geometry + CNC Job) e os 18 objetos do projeto
real autorizado, sem modificar o original. Conferência visual e teste a seco
permanecem pendentes. Limites e instruções em GEOMETRY_CNC.md.

Regressão final: install aprovado, 739 testes registrados, 728 aprovados,
11 ignorados, zero falhas/erros; propriedade opcional advancedResaved verificou
a reabertura do exemplo reserializado pelo Python. Tentativas anteriores tiveram
erros intermitentes ao excluir temporários Windows; ProjectFileIOTest agora limpa
somente seu diretório JUnit, com repetição limitada, fechando a enumeração antes
de apagar. Limpeza não foi desativada e erros persistentes continuam falhando.

## Tools Database: fechamento das transferências — 2026-10-02

Ponto 2: Milling/Geometry CNC recebem Offset/valor da base. Cutout recebe Cut Z,
Multi-Depth, Depth per pass e Thin Depth, com prioridade dos campos gerais como
no callback Python. Geometry principal e Thin recebem parâmetros CNC e perfil;
não reaplicam compensação aos caminhos Cutout. Thin exige profundidade menor.
Paint agora mantém Overlap, margem, método, Connect e Contour por diâmetro,
preserva rascunhos inválidos e aplica a todas somente por ação explícita.
Rest desconta a cobertura anterior da área individual de cada ferramenta.

Install completo aprovado: 743 testes registrados, 732 aprovados, 11 ignorados.
Repetida uma execução que falhou somente na limpeza de temporário Windows em
CncSettingsPersistenceTest, sem falha de asserção. Testes adicionais de adaptadores,
gerador e controles FX aprovados. Validação visual/física permanece pendente.
Limites em TOOLS_DATABASE_TRANSFER.md.

## Editor Geometry: Texto e Borracha — 2026-10-02

Ponto 4: Texto e Borracha habilitados na barra e no menu Geo Editor. Texto usa
contornos de fontes instaladas (Java/AWT), tamanho com escala MM/IN do ParseFont
Python, negrito/itálico e múltiplas linhas; mantém vazios das letras e associação
de ferramenta. Geração não altera o rascunho: prévia e clique confirmam inserção;
Esc/botão direito ou mudança de parâmetros/seleção/ferramenta cancelam a prévia.

Borracha usa as selecionadas como molde preenchido, como no Python. Origem e
destino deslocam a região de recorte, sem mover os originais. Recorta linhas e
áreas de todas as ferramentas, preserva associações e permite apagamento completo.
Formas não atingidas conservam IDs; sem alteração não cria undo. Cada ação é uma
transação undo/redo; cancelamento e falha não aplicam resultado parcial.

Texto, união do molde e recortes usam o executor existente fora da thread FX.
Corrigida a reabilitação de botões após trabalho/cancelamento. O FX encerra cada
gesto de borracha (Python permite destinos repetidos). Paint Shape e outros modos
avançados ainda faltam; não equivale a paridade total. Instruções/limites em
GEOMETRY_EDITOR.md. Contornos/métricas não são idênticos ao parser FreeType Python.

Install aprovado: 756 testes registrados, 745 aprovados, 11 ignorados, zero falhas
ou erros. Testes incluem cliques reais no plot sem janela, cancelamento, undo/redo,
10.000 formas e persistência nativa/legada com regeneração do CNC. Erro intermitente
de limpeza Windows em CncSettingsPersistenceTest motivou reutilizar a limpeza
limitada de ProjectFileIOTest via helper comum, sem desativar limpeza nem ocultar
falhas persistentes. Validação visual e teste na máquina continuam pendentes.

## Revisão dos tooltips após as implementações de paridade — 2026-10-02

Atualizadas as descrições de Tools Database para Offset/Custom Offset, Thin Depth
e parâmetros individuais do Paint, removendo avisos de transferência pendente que
já não correspondiam à implementação. Ajuda adicional no Editor Geometry (Texto,
Borracha, Buffer e operações de seleção), consistente entre barra e menu. Campos
de texto explicam limites, fontes instaladas, prévia, inserção e cancelamento.

Paint explica rascunhos por ferramenta, sincronização da lista e aplicação a todas.
Cutout distingue Free-form/Rectangular, Cut Z/Thin Depth e saídas separadas; gaps
manuais substituem o padrão automático. CNC detalha compensação, posições comuns,
perfis incompatíveis e limites da prévia de macros. Isolation Follow avisa que corta
sobre o cobre; NCC distingue margem externa de afastamento do cobre.

Reutilizado o padrão fluido com parágrafos, negrito e cores semânticas dos temas,
sem novos tooltips para Excluir/Fechar. Corrigida a prioridade de ajuda específica
em relação ao catálogo genérico, inclusive cópia para legendas de campos desabilitados
e atualização posterior do formulário. Ícones receberam identificação acessível.

Install completo aprovado: 762 testes registrados, 751 aprovados, 11 ignorados,
zero falhas/erros. Testes verificam controles reais, metadados, textos, prioridade,
consistência barra/menu e contraste dos temas; validação visual manual pendente.

## Etapa 1: Paint Shape no Editor Geometry — 2026-10-02

Paint acrescenta caminhos de preenchimento às áreas selecionadas, preservando
contornos e associação/diâmetro das ferramentas. Anéis fechados simples aceitos;
linhas abertas, diâmetro incompatível e resultados vazios/incompletos recusados.
Standard/Seed/Lines/Combo, margem, Connect/Contour e sobreposição. Cálculo usa o
executor existente, com cancelamento, prévia fixa e confirmação separada por botão
ou clique; Esc/botão direito cancela. Uma inserção = um undo. Mudança de parâmetros
cancela a prévia; revisão/seleção obsoletas impedem aplicar resultado antigo.
Sem associação de ferramenta, conferir o diâmetro ao gerar CNC. Não altera o
diâmetro global como o patch Python; não reclassifica caminhos anteriores.
Testes direcionados do núcleo e controles FX passaram; validação visual pendente.

## Etapa 2: Transformations Object/Buffer — 2026-10-02

Referência Object usa bounds do objeto escolhido e valida remoção/ausência de
geometria. Point não aceita mais texto inválido como zero. Buffer por distância
e percentual, cantos Rounded/mitrados, mesma unidade entre os objetos. Excellon
altera diâmetros (distância somada ao diâmetro, igual ao Python), não centros ou
extremos de slots. Geometry conserva associação/diâmetro; Gerber conserva Follow.
Buffer executado em worker, com publicação atômica, cancelamento e verificação
de fontes obsoletas. Resultados vazios/inválidos recusados. Limites de Gerber e
ausência de undo global documentados em TRANSFORMATIONS.md. Testes de núcleo e
controles aprovados; comparação visual manual pendente.
