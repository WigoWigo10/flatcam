# Inventário de janelas e telas (FlatCAM Python/PyQt5, branch `python312-compat`)

Levantamento do que existe hoje no app Python, para servir de referência ao
recriar (e onde fizer sentido, melhorar) a interface em JavaFX. Não é uma
especificação de como o JavaFX deve ficar - é o que a versão legada
realmente tem, campo a campo de estrutura (não campo a campo de UI).

Fontes: `appGUI/MainGUI.py`, `appGUI/ObjectUI.py`, `appEditors/*.py`,
`appGUI/preferences/**`, `appTools/*.py`, `app_Main.py`, `Bookmark.py`,
`appDatabase.py`.

## Achado arquitetural central

FlatCAM **não abre janelas separadas** para quase nada. É uma única
`QMainWindow` que reaproveita duas tab-strips para tudo:

- **Notebook esquerdo** (`self.notebook`): 3 abas fixas - **Project**,
  **Properties**, **Tool** - que se alternam no mesmo espaço (não são 3
  painéis lado a lado).
- **Área de plot à direita** (`self.plot_tab_area`): a aba **Plot Area**
  (viewport) é fixa/não fechável; Preferências, Tools Database, editor de
  G-Code, editor de texto e lista de atalhos entram como **abas adicionais
  nesse mesmo tab-strip**, não como janelas/diálogos modais separadas.

Isso é diferente do esboço da seção 6 do `CONTEXTO_FLATCAM_FX.md` (que
desenha Tool/Properties como um painel fixo à direita) e do que já construí
no skeleton (`MainWindow.java` atual: esquerda=árvore, centro=viewport,
direita=propriedades fixas). Ver seção "Implicações" no final.

---

## 1. Janela principal (`appGUI/MainGUI.py`)

- Splitter horizontal: **notebook esquerdo** (Project/Properties/Tool) +
  **área de plot** (Plot Area + abas auxiliares).
- **Barra de status**: área de mensagens (`FlatCAMInfoBar`) + toolbars
  embutidas (Delta Coordinates, Coordinates, Grid, Status) + label de
  unidades + indicador de atividade/progresso. Menu de contexto no próprio
  status bar liga/desliga cada sub-toolbar.
- **Dock**: "TCL Shell" (`FCDock`, fechável/flutuante/arrastável, padrão
  embaixo) - console de comandos Tcl.

### Menu principal (nível 1 completo)

1. **Arquivo** - Novo Projeto; Novo (Geometry/Excellon/Document); Abrir
   (Projeto, Gerber, Excellon, G-Code, Config); Projetos/Arquivos recentes;
   Salvar (Projeto/Salvar Como); Scripting (Novo/Abrir/Exemplo/Rodar);
   Importar (SVG, DXF, HPGL2 - como Geometry ou Gerber); Exportar (SVG, DXF,
   PNG, Excellon, Gerber); Backup (Importar/Exportar/Salvar preferências);
   Imprimir (PDF); Sair.
2. **Editar** - Editar Objeto/Sair do Editor; Conversão (Single↔Multi-Geo,
   Any→Geo, Outline→Area, Any→Gerber, Any→Excellon); Juntar Objetos;
   Copiar; Excluir; Set Origin, Move to Origin, Jump to Location, Locate in
   Object; Toggle Units; Selecionar Tudo; Preferências.
3. **Opções** - Rotate Selection; Skew X/Y; Flip X/Y; View Source; Tools
   Database.
4. **Exibir** - Enable/Disable all (+variantes "non-selected"); Zoom
   Fit/In/Out; Redraw All; Toggle Code Editor; Toggle FullScreen; Toggle
   Plot Area; Toggle Project/Properties/Tool; Toggle Grid Snap/Lines/Axis/
   Workspace/HUD.
5. **Objetos** - Selecionar Tudo, Desmarcar Tudo (age na árvore).
6. **Ferramenta** - Command Line (abre o shell Tcl).
7. **Ajuda** - Online Help; Bookmarks (Manager + itens dinâmicos); Report a
   bug; Excellon/Gerber Spec (PDF); Shortcuts List; YouTube; How To; About.
- **Menus contextuais de editor** (`>Geo Editor<`, `>Excellon Editor<`,
  `>Gerber Editor<`): réplicas em menu de cada toolbar de editor, visíveis
  só durante a edição correspondente.
- **Menu de contexto da árvore de projeto**: Enable/Disable Plot, Set Color
  (paleta + custom + opacidade + default), Create CNCJob, View Source,
  Edit, Copy, Delete, Save, Properties.

### Toolbars

| Toolbar | Ações |
|---|---|
| File | Open Gerber, Open Excellon — Open Project, Save Project |
| Edit | Editor, Save&Close Editor — Copy, Delete — Distance, Distance Min, Set Origin, Move to Origin, Jump to Location, Locate in Object |
| View | Replot, Clear Plot, Zoom In/Out/Fit |
| Shell | Command Line, New/Open/Run Script |
| Tools | 2-Sided, Align Objects, Extract Drills — Cutout, NCC, Paint, Isolation, Drilling — Panelize, Film, SolderPaste, Subtract, Rules Check, Optimal — Calculators, Transform, QRCode, Copper Thieving, Fiducials, Calibration, Punch Gerber, Invert Gerber, Corner Markers, Etch Compensation |
| Excellon Editor | Select, Add Drill/Array, Add Slot/Array, Resize — Copy, Delete — Move |
| Geometry Editor | Select, Circle, Arc, Rectangle — Path, Polygon — Text, Buffer, Paint Shape, Eraser — Union, Explode, Intersection, Subtraction — Cut Path, Copy, Delete, Transformations — Move |
| Gerber Editor | Select, Pad/Array, Track, Region, Poligonize, SemiDisc, Disc — Buffer, Scale, Mark Area, Eraser — Copy, Delete, Transformations — Move |

As 3 toolbars de editor ficam ocultas/desabilitadas até entrar no editor
correspondente.

---

## 2. Painéis de propriedade por objeto (`appGUI/ObjectUI.py`)

Aparecem na aba **Properties** do notebook esquerdo (não são janelas).
Base comum: título, Plot Options, Name.

- **Gerber** - Plot Options, Name, Apertures (tabela), Tools, Non-copper
  regions, Bounding Box, Transformations.
- **Excellon** - Plot Options, Name, Tools Table, Tools, Milling Geometry,
  Transformations.
- **Geometry** - Plot Options, Name, Tools Table, Add from DB, Common
  Parameters, Tools, Transformations.
- **CNCJob** - Plot Options, Name, CNC Tools Table, Probe Points Table,
  Probe GCode Generation, **Mode/Controller** (COM port, baud, jog
  step/feedrate, Send Command, Get Config - um painel de controle de
  máquina embutido), Export CNC Code.
- **Script** - só Name (embrulha o editor de texto Tcl).
- **Document** - Name, Font Type/Size/Alignment/Color, Selection Color, Tab
  Size (bloco de notas simples).

---

## 3. Editores (`appEditors/`)

Nenhum abre janela própria - substituem/aumentam o conteúdo da aba Plot
Area, cada um com sua toolbar+menu dedicados (seção 1).

- **Gerber Editor** - apertures/pads/tracks/regions. Sub-ferramentas: Pad,
  PadArray, Poligonize, Region, Track, Disc, DiscSemi, Scale, Buffer, Mark,
  Move, Copy, Eraser, Select, Transform.
- **Geometry Editor** - formas (círculo, arco, retângulo, polígono, path,
  texto). Sub-painéis: BufferSelectionTool, TextInputTool, PaintOptionsTool,
  TransformEditorTool (aparecem na aba Tool durante a edição).
- **Excellon Editor** - furos/slots: Select, DrillAdd/Array, SlotAdd/Array,
  Resize, Move, Copy.
- **Text Editor** (`AppTextEditor`) - editor de texto genérico reaproveitado
  pelo objeto Script e por outras views; sem toolbar própria além de
  botões embutidos (salvar/abrir/imprimir PDF).
- **G-Code Editor** (`appGCodeEditor`) - visualização/edição de G-Code de um
  CNCJob; embrulha um `AppTextEditor`; Name, "Update G-Code", "Exit Editor".

---

## 4. Preferências (`appGUI/preferences/`)

Não é diálogo modal - é a aba **Preferences** dentro da área de plot, com
sub-abas: **General / GERBER / EXCELLON / GEOMETRY / CNC-JOB / TOOLS /
TOOLS 2 / UTILITIES**. Cada sub-aba agrega vários "grupos" (~40 arquivos
`*PrefGroupUI.py` no total):

- **General**: App Preferences, App Settings, GUI Preferences.
- **Gerber/Excellon/Geometry/CNCJob**: cada um com General, Options,
  Advanced Options, Export (Gerber/Excellon), Editor.
- **Tools** (1 grupo por plugin "clássico"): Cutout, NCC, Paint, ISO,
  Drilling, Film, Panelize, Calculators, Solderpaste, Sub, Transform,
  Corners.
- **Tools 2** (plugins mais novos): Calibration, Copper Thieving, Extract
  Drills, Fiducials, Invert Gerber, Optimal, Punch Gerber, QRCode, Rules
  Check, 2-Sided.
- **Utilities**: AutoComplete, associações de arquivo (.exc/.gco/.grb).

---

## 5. Painéis de ferramenta/plugin (`appTools/*.py`, 34 arquivos)

Todos entram na aba **Tool** do notebook esquerdo (nenhum é `QDialog`
modal). Agrupados por função:

**Geração de toolpath / fabricação**
- `ToolIsolation` - roteamento de isolamento em cobre.
- `ToolNCC` (Non-Copper Clear) - limpa cobre fora de trilhas/pads.
- `ToolPaint` - "pinta" (limpa cobre dentro de) áreas fechadas.
- `ToolDrilling` - toolpath de furação a partir de um Excellon.
- `ToolMilling` - fresagem de furos (alternativa à furação).
- `ToolSolderPaste` - dispensação de pasta de solda.
- `ToolCutOut` - contorno de corte da placa (com gaps/pontes).

**Preparação de Gerber**
- `ToolCopperThieving` - preenchimento de cobre (thieving) + fiduciais.
- `ToolFiducials` - marcadores fiduciais.
- `ToolCorners` - marcadores de canto.
- `ToolEtchCompensation` - compensação de sub/sobre-corrosão.
- `ToolInvertGerber` - inverte polaridade.
- `ToolPunchGerber` - fura o Gerber nos centros de pad/furo.
- `ToolExtractDrills` - detecta centros de pad/via e gera Excellon.

**PCB dupla-face / painelização**
- `ToolDblSided` - espelhamento para fluxo de dupla face.
- `ToolAlignObjects` - alinha dois objetos por pontos de referência.
- `ToolPanelize` - replica um objeto numa grade.
- `ToolFilm` - exporta filme/negativo.

**Medição / cálculo / propriedades**
- `ToolDistance`, `ToolDistanceMin` - medição de distância no canvas.
- `ToolOptimal` - menor distância entre todos os objetos Gerber.
- `ToolCalculators` - calculadoras de conversão.
- `ToolProperties` - propriedades computadas do objeto selecionado.
- `ToolRulesCheck` - DRC (design rule check).

**Transformação / edição geral**
- `ToolMove`, `ToolTransform`, `ToolSub` (subtrai geometria de um objeto de
  outro).

**Importação / geração**
- `ToolImage` (raster → Geometry/Gerber), `ToolPDF` (import PDF),
  `ToolPcbWizard` (import PcbWizard), `ToolQRCode` (gera QR code),
  `ToolCalibration` (calibração via pontos de referência).

**Infra** (não é `AppTool`)
- `ToolShell` (`FCShell`/`TermWidget`) - o widget de terminal usado dentro
  do dock TCL Shell.

---

## 6. Outras janelas/diálogos avulsos

- **About** (`app_Main.py`, `AboutDialog`) - modal, versão/data/créditos.
- **How To** (`HowtoDialog`) - modal, links (open source, novidades, bug
  tracker, doações).
- **DialogBoxChoice** - modal genérico com `RadioSet` (ex.: escolher canto
  de referência bottom-left/top-left/etc.).
- **Splash screen** - `QSplashScreen` com `splash.png` na inicialização.
- **Tools Database** (`appDatabase.py`, `ToolsDB2`) - aba na área de plot
  (não modal), gerencia biblioteca de ferramentas de corte reutilizáveis.
- **Bookmark Manager** (`Bookmark.py`) - `QWidget` para gerenciar links
  salvos, acessível via Ajuda → Bookmarks.
- **Shortcuts List** - aba HTML/texto na área de plot (não janela própria).
- **Inputs genéricos reutilizáveis** (`appGUI/GUIElements.py`):
  `FCInputDoubleSpinner`, `FCInputSpinner`, `FCInputDialogSlider`,
  `FCInputDialogSpinnerButton`, `DialogBoxRadio` - popups pequenos "digite
  um valor", usados em dezenas de lugares.
- `QMessageBox` ad hoc espalhados pelo código (confirmações de
  salvar/sobrescrever/sair) - não catalogados individualmente.

---

## Implicações para o JavaFX

**1. O shell atual (`MainWindow.java`) precisa mudar de forma, não só de
cor.** Hoje tenho 4 quadrantes fixos (árvore / viewport / propriedades /
console). O legado usa 2 tab-strips: uma à esquerda alternando
Project/Properties/Tool, e uma à direita/centro onde o viewport é só mais
uma aba entre várias (Preferências, Tools DB, editores, atalhos). Isso
economiza espaço de tela e evita diálogos modais para telas grandes
(Preferências, por exemplo, é uma página cheia, não um popup). Recomendo
migrar o centro do skeleton para um `TabPane` (viewport como primeira aba
fixa, demais abas entram sob demanda) e trocar o painel direito fixo por
um `TabPane` esquerdo com 3 abas (Project/Properties/Tool) no lugar da
`TreeView` solta que está lá agora.

**2. Gap real de biblioteca: abas destacáveis.** O `FCDetachableTab` deixa
o usuário arrastar uma aba para virar janela própria. `TabPane` do JavaFX
não faz isso nativamente - precisaria de uma implementação própria ou uma
lib de terceiros. Vale registrar como decisão em aberto (mesmo espírito da
seção 11 do `CONTEXTO_FLATCAM_FX.md`) em vez de assumir que "dá pra
replicar depois fácil".

**3. Onde dá pra melhorar sem inventar escopo novo:**
- Os ~40 grupos de Preferências são só formulários empilhados em abas -
  JavaFX + `ComboBox`/busca incremental permitiria um campo de busca
  filtrando por nome da opção (o Python não tem isso), reduzindo a
  navegação por 8 sub-abas.
- Os 34 painéis de `Tool` competem pelo mesmo espaço (aba Tool); um
  `Popover`/painel flutuante ancorável (em vez de sempre substituir o
  conteúdo da aba) evitaria perder o contexto ao trocar de ferramenta.
  Requer decisão de UX, não é grátis.
- Diálogos de input genérico (`FCInputDoubleSpinner` etc.) hoje são
  bem crus visualmente; com CSS (seção anterior) isso é resolvido de graça.
- A barra de status tem 5+ toolbars embutidas competindo por espaço
  horizontal; um `StatusBar` mais hierárquico (grid indicators com
  ícones + tooltip, unidades como toggle) reduz ruído visual sem perder
  função.

**4. O que NÃO precisa mudar:** a divisão conceitual em Project/Properties/
Tool/Plot já é sólida e é exatamente o que a seção 6 do documento de
contexto pedia em espírito (só a disposição geométrica dos painéis muda).
Os nomes de menu/toolbar podem ser copiados quase 1:1 como ponto de partida
de paridade funcional (seção 9 do documento de contexto).

## Escopo não coberto aqui

Este documento lista *estrutura de tela* (o que existe, onde vive), não
*campo a campo* de cada painel de preferências ou de cada uma das 34
ferramentas - isso é grande demais para uma passada e só vale a pena
detalhar tela por tela conforme cada uma for realmente portada (Fase 5/6).
