# Plano de paridade: FlatCAM FX e FlatCAM Python

Atualizado em **2026-10-03**. Base inicial: revisão `91d3ce50` do FX e o checkout
Python deste repositório. Este documento registra a sequência futura e as
entregas explicitamente verificadas; não declara todas as etapas concluídas.

## Objetivo e escopo

Consolidar primeiro o fluxo normal **Gerber/Excellon → Geometry → CNC Job →
salvar/reabrir**, com resultados comparáveis, interação previsível e limitações
explícitas. Depois, completar opções avançadas, preferências e automação.

Paridade envolve quatro dimensões: função, resultado CAM/CNC, persistência e
UI/UX. Ter uma ferramenta no menu ou um teste headless aprovado não comprova
que seu fluxo completo equivale ao Python. Diferenças deliberadas de segurança
devem ser documentadas, não removidas apenas para imitar o legado.

## Ponto de partida (antes da etapa 1)

- O FX já possui importação Gerber/Excellon, editores, operações CAM, geração
  CNC, banco de ferramentas, conversões/junções e compatibilidade de projetos.
  As 24 ferramentas do menu Ferramentas têm implementações, com cobertura
  parcial das opções e dos fluxos avançados.
- A última comparação no projeto real autorizado resultou em **7
  `MATCH_SAMPLED`, 3 `DIFFERENT` e nenhum `ORACLE_ERROR`**, entre dez casos.
  Isso não significa 70% de paridade do aplicativo nem igualdade geométrica.
- NCC Standard, NCC Seed e Paint Standard ainda divergem. As maiores distâncias
  amostradas nesse exemplo são aproximadamente 0,02059 mm, 0,12928 mm e
  0,02159 mm, respectivamente. A causa definitiva das diferenças restantes
  ainda não foi estabelecida.
- A regressão funcional registrou 787 testes: 776 aprovados e 11 opcionais
  ignorados, **com limpeza de TempDir desativada somente naquela execução**.
  A suíte normal ainda apresentou falhas de limpeza no Windows; não está
  declarada aprovada.
- Os probes de renderização D3D na GTX 1650, software e Java/Maven passaram.
  Isso verifica inicialização/renderização básica, não fluidez em placas
  grandes ou desempenho dos cálculos CAM.

Detalhes e ressalvas: [estado do projeto](CONTEXTO_E_PROGRESSO.md),
[comparação CAM](COMPARACAO_CAM.md) e [inventário de UI](UI_INVENTORY.md).

## Sequência de implementação

### 1. Estabilizar a infraestrutura de testes — prioridade imediata

Investigar as falhas intermitentes de limpeza dos diretórios JUnit no Windows,
isolando recursos abertos, comportamento do sistema de arquivos e versões das
dependências. Fixar/documentar o ambiente de comparação Python, inclusive a
dependência isolada necessária para executar os algoritmos legados.

**Critério de conclusão:** suíte normal aprovada em execuções repetidas, com
limpeza ativa; comparação reproduzível sem modificar o algoritmo Python nem
o ambiente principal do usuário. Não resolver desativando permanentemente
limpeza, testes ou verificações.

**Entrega local em 2026-10-03:** suporte de testes compartilhado, JUnit 6.1.3
via BOM e Surefire 3.5.4; repetição limitada para diretórios Windows já vazios,
sem ocultar falhas persistentes. Três execuções finais da suíte normal passaram
com limpeza ativa: 807 registrados, 796 aprovados, 11 opcionais ignorados.
Os 20 testes novos incluem controles de arquivo bloqueado e junctions.
Detalhes em [TESTES.md](TESTES.md). A causa exata da falha histórica e a
validação em outros sistemas continuam abertas; os casos CAM divergentes não
foram reclassificados nem alterados nesta entrega.

### 2. Resolver ou caracterizar Standard/Paint/Seed — prioridade imediata

Criar reproduções sintéticas mínimas a partir das divergências já localizadas.
Comparar erosões sucessivas em Standard/Paint e ponto interior, arcos e término
dos anéis em Seed. Separar defeitos de implementação de diferenças dos kernels
JTS/GEOS ou limitações do próprio método legado.

**Critério de conclusão:** cada divergência reproduzida recebe correção com
regressão, ou explicação demonstrada com limite explícito e orientação ao
usuário. Manter os critérios numéricos existentes; cobertura próxima não
autoriza classificar trajetos diferentes como iguais.

**Investigação em 2026-10-03:** diferenças dos buffers Standard/Paint e
instabilidade da scan-line Seed reproduzidas em casos sintéticos. Protótipo
isolado passa Standard/Paint na placa real, mas Seed ainda difere com entrada
decodificada independentemente.

**Correção em 2026-10-03:** as três divergências da investigação foram
tratadas. Standard/Paint - e, por consequência, Lines, que usa a mesma
rotina - ganharam um buffer próprio do projeto (`org.flatcam.cam.ncc.
geosbuffer`) alinhado às duas regras que divergiam do GEOS 3.10.3, sem tocar
na dependência JTS global; no projeto real autorizado, `ncc-standard`,
`paint-standard` e `ncc-lines` passaram de `DIFFERENT`/não medido para
`MATCH_SAMPLED` (~0,0006 mm, ~0,0000011 mm e ~0,0006 mm). Seed recebeu uma
escolha de ponto inicial deliberadamente diferente da legada
(`StableInteriorPoint`, "pole of inaccessibility" em vez do ponto por
scan-line instável que Python e JTS compartilham) - ela **não** reduz a
distância amostrada contra o Python (continua `DIFFERENT`, ~0,15 mm, mesma
ordem de grandeza de antes) porque agora compara dois algoritmos diferentes
de propósito, não duas instâncias ruidosas do mesmo algoritmo; o que ela
resolve é a própria instabilidade da FX (a mesma entrada, com ruído de ponto
flutuante típico de decodificação independente, não move mais o ponto por
ordens de grandeza). Documentado como diferença deliberada, não paridade.
Nenhuma outra etapa foi avançada. Detalhes, testes e limites em
[INVESTIGACAO_CAM.md](INVESTIGACAO_CAM.md).

### 3. Ampliar a validação e completar opções CAM

Adicionar casos para Isolation/NCC com Rest, múltiplas ferramentas, Connect,
referências e áreas; Isolation Follow e exceções; Cutout Free-form, Thin,
M-Bites, gaps manuais e margem negativa; operações em MM e IN.
Implementar ou corrigir as lacunas demonstradas por esses casos.

**Critério de conclusão:** comparar caminhos e pegada da ferramenta, além de
pontes de fixação, regiões não alcançadas e associação dos parâmetros por
ferramenta. Conferir também o fluxo visual dos painéis. Preservar as recusas
de geração quando um padrão Cutout perde pontes solicitadas.

Referências: [comparação CAM](COMPARACAO_CAM.md) e [Cutout](CUTOUT.md).

**Entregas em 2026-10-03:**

- Cutout Free-form com margem negativa somava o raio da fresa/broca
  independentemente do sinal da margem; `cutout_handler` do Python subtrai o
  raio quando a margem é negativa, aprofundando o corte para dentro do
  contorno em vez de encolher a compensação. Corrigido em
  `CutoutGenerator.signedOffset` (contorno e M-Bites Free-form); dois testes
  de regressão fixam o deslocamento esperado. Ver [Cutout](CUTOUT.md).
- Isolation com Rest Machining não tinha o equivalente do checkbox "Forced
  Rest" do Python (`tools_iso_force`, marcado por padrão): quando uma
  ferramenta funde um furo com o contorno já na primeira passada de um
  polígono com mais de um furo, o Python descarta o polígono inteiro para
  essa ferramenta e tenta de novo com a próxima menor, em vez de aceitar o
  resultado com o furo perdido. Adicionado em
  `IsolationGenerator.generateRest` (parâmetro `forcedRest`, exposto como
  checkbox na ferramenta, marcado por padrão) com teste de regressão
  cobrindo os dois comportamentos (marcado/desmarcado).
- 538 testes em `flatcam-cam` aprovados (3 novos: duas margens negativas de
  Cutout, uma de Forced Rest), 824 no reactor completo. Build completo e
  abertura do FX verificados depois do `install`.
- NCC/Paint "Connect" (`paint_connect` do Python) tinha duas lacunas: (1) o
  Python testa se o segmento de ligação, já expandido pelo raio da
  ferramenta, cabe na área segura (`walk_cut.buffer(tooldia/2).within(...)`),
  mas a FX só testava a linha fina do segmento, aceitando ligações que uma
  ferramenta real não atravessaria; (2) o Python limita a distância de
  ligação a `max_walk = 10 * tooldia` por padrão, e a FX não tinha limite
  algum. Corrigido em `NccGenerator.connectSafePaths`, que agora expande o
  conector pelo raio da ferramenta antes de testar contenção e aplica o
  teto de `10 * toolDiameter`. Quatro testes de regressão cobrem o corredor
  estreito demais para a ferramenta, o corredor largo o suficiente, a
  travessia maior que `max_walk` e a travessia dentro do limite.
- 542 testes em `flatcam-cam` aprovados (4 novos, de Connect), 828 no reactor
  completo. Build completo e abertura do FX verificados depois do `install`.
- Revisão do código (não do comportamento) mostrou que Isolation Follow e
  Exceptions (`IsolationGenerator.generateFollow`/`excludeArea`, incluindo a
  checkbox e o combo "Excluir area" no `IsolationToolPanel`, ligados em
  `MainWindow`) já estavam implementados e testados de ponta a ponta antes
  desta sessão; a observação anterior nesta seção estava desatualizada e foi
  corrigida. Da mesma forma, NCC com Rest Machining já resolve o
  `NccBoundary` (Itself/Area/Reference) uma única vez, antes do laço por
  ferramenta, então um limite customizado (Area ou Reference) já valia para
  toda ferramenta sob Rest - só faltava um teste de regressão cobrindo a
  combinação. Adicionado
  `restMachiningUsesTheSelectedAreaBoundaryNotTheCoppersOwnHull`, que passou
  de primeira sem alterar `NccGenerator`, confirmando o comportamento em vez
  de corrigi-lo.
- 543 testes em `flatcam-cam` aprovados (1 novo), 829 no reactor completo.
  Build completo e abertura do FX verificados depois do `install`.
- Operações em IN: nenhum gerador de CAM (`CutoutGenerator`, `IsolationGenerator`,
  `NccGenerator`) faz conversão MM<->IN internamente - cada um recebe
  distâncias já nas unidades do objeto e a string `units` só é carregada como
  metadado no resultado (confirmado lendo os três; nenhuma tolerância
  absoluta em escala mm encontrada, só relativas como `toolRadius * 0.01` ou
  infinitesimais como `1e-10`/`1e-12`, negligíveis em qualquer unidade). Isso
  espelha o Python: `camlib.py`/`ToolCutOut.py`/`ToolIsolation.py` também
  operam nas unidades já carregadas pelo objeto, sem reescalar. Para fechar a
  lacuna de cobertura (não de implementação) nos três casos entregues nesta
  etapa, três testes em polegadas replicam as fixtures MM já existentes
  escaladas por `1/25.4`
  (`negativeMarginSubtractsTheToolRadiusInInchesToo`,
  `forcedRestRejectsAToolThatMergesAwayAHoleOnTheFirstPassInInchesToo`,
  `restMachiningUsesTheSelectedAreaBoundaryInInchesToo`); os três passaram de
  primeira, sem alteração de produção.
- 546 testes em `flatcam-cam` aprovados (3 novos, de IN), 832 no reactor
  completo. Build completo e abertura do FX verificados depois do `install`.

Ainda não abordado: Cutout Thin/M-Bites/concavidades com um projeto real -
depende de um projeto real não-retangular, fora do escopo testável apenas
com fixtures sintéticas.

### 4. Completar Drilling e validar CNC além de XY

Priorizar exclusões em Drilling, parâmetros avançados por ferramenta e seu
consumo a partir da Tools Database. Revisar os limites dos pós-processadores
já portados e as opções ainda sem equivalente, incluindo compensação e
posições de troca/finalização quando aplicáveis.

**Critério de conclusão:** verificar alturas Z, movimentos de aproximação,
retração e deslocamento, avanços, spindle, troca de ferramenta e posições
finais, incluindo combinações incompatíveis. Testar prévia e limites de
tamanho sem retirar proteções. Parser de G-code e desenho XY não comprovam
segurança física; teste a seco na máquina exige validação própria.

Referências: [Geometry/CNC](GEOMETRY_CNC.md),
[exclusões CNC](CNC_EXCLUSIONS.md) e [pós-processadores](PREPROCESSADORES.md).

### 5. Consolidar compatibilidade e persistência de projetos

Ampliar round-trips FX → Python → FX para dados avançados, macros Gerber,
parâmetros por ferramenta e configurações de objetos. Preservar rascunhos no
formato FX quando necessário e avaliar separadamente o que o legado consegue
representar. Incluir abrir/salvar pela interface Python, além dos testes de
serialização.

**Critério de conclusão:** nenhuma perda silenciosa. Dados não representáveis
devem produzir aviso ou recusa explícita conforme o risco. Manter o arquivo
original intacto nos ensaios e testar os formatos FX e legado.

Referência: [compatibilidade FlatPrj](COMPATIBILIDADE_FLATPRJ.md).

### 6. Fechar lacunas dos editores e da UI/UX

Comparar os editores Gerber, Geometry e Excellon existentes, sem recomeçá-los.
Inventariar ferramentas e gestos ausentes; conferir seleção visual/tabelas,
Delete, cancelamento, desfazer/refazer, foco e atalhos. Revisar organização
dos painéis, estados habilitados, feedback de progresso e tooltips.

**Critério de conclusão:** executar roteiros manuais equivalentes nos dois
aplicativos, com temas claros/escuros e escalas de tela relevantes. Registrar
diferenças intencionais de UX e impedir perda de seleção ou alterações por
ações inesperadas. Testes headless complementam, mas não substituem essa etapa.

### 7. Completar preferências e automação

Inventariar preferências globais, defaults por ferramenta, persistência e
menus parciais. Depois, definir o escopo de scripts/CLI e a compatibilidade
pretendida com a automação Python antes de implementar novos comandos.

**Critério de conclusão:** preferências sobrevivem ao reinício e afetam os
fluxos correspondentes; comandos cobertos têm argumentos, erros e resultados
documentados e testados. Recursos sem equivalente ficam identificados.

**Entregas em 2026-10-03 (Terminal/Tcl):**

O Python embute um interpretador Tcl de verdade (`tkinter.Tcl()`, Tcl 8.6) e
registra ~69 comandos próprios (`tclCommands/TclCommand*.py`) como procs Tcl.
Não existe Tcl puro-Java mantido hoje; a única opção no Maven Central
(`jacl:jacl:1.2.6`) é um jar do ano 2000 (Tcl 8.0/8.2), abandonado há ~25
anos, de procedência incerta - destoando das demais dependências do projeto
(todas mantidas). Decisão (com o usuário, ver histórico da sessão): construir
um interpretador próprio e deliberadamente reduzido em vez de depender do
Jacl, cobrindo exatamente o que os scripts reais do FlatCAM usam
(`assets/examples/*.FlatScript`): variáveis, substituição `$var`/`[cmd]`, e
`set/unset/incr/append/puts/if-elseif-else/while/foreach/expr/list/llength/
lindex`. Sem `proc`, sem o formato real de lista do Tcl, sem `catch`/
`switch`/`string`/`array` - um script que dependa de mais Tcl do que isso não
roda aqui. É uma divergência deliberada e documentada do oráculo Python
(ver javadoc de `TclInterpreter`), não uma lacuna de paridade.

Entregue nesta etapa:
- `org.flatcam.cam.tcl.TclInterpreter`/`TclExpr`/`TclArgs` (`flatcam-cam`):
  o motor (parsing de palavras com chaves/aspas/colchetes, substituição,
  controle de fluxo, gramática de expressão) e o parsing `-opcao valor`
  compartilhado por todo comando Tcl do FlatCAM (mesmo algoritmo do
  `TclCommand.parse_arguments` do Python). 46 testes.
- `org.flatcam.fx.TerminalPanel` (`flatcam-fx`): a porta do `TermWidget` do
  Python - saída somente leitura + entrada de linha com histórico
  (Seta Cima/Baixo). `help`/`version`/`clear_shell` registrados diretamente
  no painel, como os utilitários de shell do Python
  (`TclCommandHelp`/`TclCommandVersion`/`TclCommandClearShell`). As duas
  entradas de UI que eram stubs há tempos ("Linha de Comando Tcl" no menu
  Ferramentas e "Linha de Comando" na toolbar Shell) agora abrem/selecionam
  uma aba persistente "Terminal" nas abas centrais. 5 testes com o toolkit
  JavaFX real.
- 592 testes em `flatcam-cam` aprovados (46 novos), 883 no reactor completo.
  Build completo e abertura do FX verificados depois do `install`.

- `org.flatcam.fx.TclFlatcamHost`/`TclFlatcamCommands` (`flatcam-fx`): o
  registro de objetos por nome que faltava (`MainWindow` implementa a
  interface, resolvendo por nome nos seus mapas por `TreeItem` existentes -
  o mesmo que o `collection.get_by_name` único do Python faz entre tipos) e
  13 comandos próprios, cada um espelhando um `TclCommand*.py`:
  `open_gerber`, `open_excellon`, `new_geometry`, `delete`/`del`,
  `get_names`, `bbox`/`bounding_box`, `bounds`/`get_bounds`, `isolate`,
  `cutout` (só retangular, como o próprio comando Tcl do Python),
  `ncc`/`ncc_clear` (`-all`/`-box`, métodos Standard/Seed), `cncjob`
  (subconjunto reduzido de flags), `export_gcode`, `write_gcode`. Opções sem
  fallback razoável fora de um sistema de preferências ainda não exposto ao
  Tcl (`-dia`, `-gapsize`, `-tooldia`, os sinalizadores obrigatórios de
  `cncjob`) são exigidas explicitamente em vez de usar um padrão
  inventado, com erro claro se ausentes. A interface é testável com um host
  falso, sem depender de JavaFX. `TclArgs` ganhou `isPresent` para
  sinalizadores sem valor (`-all`), distinto de `has` (exige valor real) -
  um bug real pego ao ligar `-all`, documentado no commit.
  32 testes (`TclFlatcamCommandsTest`) + 2 (`TclArgsTest.isPresent`).
- 593 testes em `flatcam-cam` aprovados, 916 no reactor completo. Build
  completo e abertura do FX verificados depois do `install`.

Ainda não abordado: `open_project` (recriar todos os objetos de um arquivo
de projeto via Tcl - exigiria extrair o fluxo assíncrono existente de
"aplicar projeto carregado" para algo que um comando síncrono possa chamar,
sem arriscar esse caminho já ajustado); os demais ~55 comandos do Python
(`offset`/`scale`/`mirror`/`skew`, `options`, `plot_all`/`plot_objects`,
`set_sys`/`get_sys`/`list_sys`, `panelize`, `join_*`, `subtract_*`,
`align_drill*`, export para DXF/SVG/Excellon/Gerber, etc.); `-combine
False` (um objeto por passada em `isolate`), `-follow`, `-order` do NCC, e
os métodos Lines/Combo do NCC.

## Desempenho: trabalho transversal

Medir tempo CAM, memória, carregamento, latência de seleção e navegação nas
mesmas placas e condições. Distinguir custo de geometria, preparação do plot
e renderização; usar a GPU dedicada não acelera automaticamente operações JTS.

Otimizações devem preservar precisão, cancelamento e responsividade. Uma nova
arquitetura GPU/nativa depende de gargalos medidos e de comparação com a
solução atual; não é pré-requisito para corrigir paridade funcional.
Referências: [desempenho do plot](PLOT_PERFORMANCE.md),
[launcher/GPU](NATIVE_GPU.md) e
[arquitetura futura](ARQUITETURA_RENDERIZACAO_FUTURA.md).

## Entregas e acompanhamento

Executar **as etapas 1 e 2 primeiro, em commits separados**. As demais etapas
podem exigir vários incrementos pequenos; um commit deve representar uma
mudança coerente e verificada, não uma etapa artificialmente completa.

Para cada incremento, registrar:

1. Lacuna observada e comportamento esperado no Python.
2. Implementação ou diferença deliberada, com teste de regressão.
3. Comandos, ambiente e resultados da verificação; pendências manuais à parte.
4. Atualização dos documentos afetados e identificação do commit.

Manter projetos privados, relatórios, WKT/SVG e G-code de comparação fora do
Git, em `target/`. Não alterar o oráculo Python para obter aprovação.
Este plano não estabelece prazo nem porcentagem global de paridade: acompanhar
cenários cobertos e pendências demonstráveis por etapa.
