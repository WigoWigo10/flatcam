# FlatCAM Next — contexto, progresso e próximos passos

Este é o documento operacional de continuidade do **FlatCAM Next**. Ele foi
escrito para que uma nova sessão de IA (Codex, Claude ou equivalente) consiga
entender o estado real do projeto, tomar decisões compatíveis com as já feitas
e continuar a migração sem recomeçar a investigação.

> Atualizado em **2026-09-22**. A base anterior a esta revisão é o commit
> `6a4146ed` (`docs(flatcam-next): add project handoff and progress guide`),
> seguido nesta mesma data por três incrementos: NCC multi-tool com Rest
> Machining, boundary por objeto de referência + Check validity (seção 5), e
> Transformations (seção 4). Antes de trabalhar, confirme o `HEAD`, o
> `git status` e os testes: este arquivo é um ponto de passagem, não substitui
> o código como fonte final da verdade.

## 1. Objetivo do projeto

O FlatCAM Next é uma reimplementação gradual do FlatCAM Python/PyQt5 em
**Java 21 + JavaFX**, mantida no mesmo repositório enquanto ainda não substitui
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

- Java 21.
- Maven Wrapper; Maven global não é necessário.
- JavaFX 21.0.12.
- AtlantaFX 2.1.0, além dos temas CSS próprios.
- JTS 1.20.0 para geometria.
- JUnit 5 para testes.

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

Após NCC multi-tool + boundary por referência + Check validity + Transformations
+ persistência embutida de Gerber/Excellon (seções 5, 4 e 9.3), `clean test`
passa com **112 testes executados**, sem falhas, erros ou testes ignorados
(79 antes do NCC multi-tool, 82 após ele, 86 após boundary/validity, 109 após
Transformations, 112 após a persistência embutida). `JobExecutorTest` registra
intencionalmente uma `IllegalStateException: boom` ao testar propagação de erro;
esse log, isoladamente, não representa falha da suíte.

O smoke test de inicialização também chegou a `MainApp started`.

Sempre refaça essas verificações depois de mudanças relevantes; números e
resultados podem mudar.

## 4. O que já funciona

### Shell, temas e área de plotagem

- Janela JavaFX com menus, barras de ferramentas, painel lateral, console,
  status e progresso.
- Temas claro/escuro próprios e variantes AtlantaFX.
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
  Geometry e CNC Job **ainda não** fazem parte desse formato embutido -
  continuam como antes (Geometry nem é persistido; CNC Job guarda só o path
  do G-code e o texto, sem geometria de plot ao reabrir).

### Gerber

- Parser RS-274X com unidades, formato de coordenadas, modos de coordenada,
  apertures, aperture macros, polaridade, regiões e operações usuais cobertas
  por testes.
- Geometria sólida e geometria `follow`.
- Exibição, tabela de apertures, propriedades e visualização da fonte.
- Operações auxiliares de região não-cobre e bounding box.
- Isolation Routing com parâmetros, preview/resultado e geração de G-code.
- Cutout Tool com forma, tipo, margens, gaps/bridges e geração de G-code.
- NCC Tool em primeira fatia funcional.

### Excellon

- Parser, ferramentas, furos e slots.
- Exibição e propriedades/tabela de ferramentas.
- Geração de G-code de furação.

### Geometry e CNC Job

- Objetos Geometry podem ser produzidos pelo NCC.
- Geometry pode gerar CNC Job com `safe Z`, profundidade, multi-depth,
  profundidade por passe, feed rate e spindle.
- CNC Job separa e desenha trajetos de viagem e corte.
- Visualização, ativação/desativação e salvamento de G-code.

### Ferramentas adicionais

- Calculadoras de unidades, ferramenta V e galvanoplastia.
- Isolation, Cutout e NCC executam como jobs canceláveis, sem bloquear a thread
  JavaFX.

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
  um único `double`); overlap/margem/método/connect/contour/copperOffset
  continuam **compartilhados entre todas as ferramentas** - Python permite
  variar isso por ferramenta quando Rest Machining está desligado, mas força
  um único valor global quando está ligado. Este port sempre compartilha, nos
  dois modos - simplificação deliberada de v1, documentada no Javadoc do
  record.
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
  "Area Selection" (retângulo desenhado no canvas) continua fora de escopo,
  por depender de infraestrutura de seleção no canvas que ainda não existe.
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
  seleção, lista de diâmetros se auto-ordena, tabela com altura dinâmica e
  coluna ocupando 100% da largura (`CONSTRAINED_RESIZE_POLICY`), e tooltips
  explicando Method/Connect/Contour/Copper offset/Rest Machining/Order com
  `showDuration` estendido (`Duration.INDEFINITE`) - o padrão do JavaFX
  esconde tooltips após ~5s mesmo com o mouse parado em cima, cedo demais
  para textos multi-linha. Evite usar `Button.setDefaultButton`/
  `setCancelButton` nos painéis desta app: nenhum dos 4 temas estiliza o
  pseudo-estado `:default` do JavaFX, e o botão fica com a aparência pálida
  do Modena por baixo do tema (foi tentado e revertido nesta mesma revisão).

O botão NCC já deve abrir o painel da ferramenta. Se voltar a “não fazer nada”,
primeiro suspeite de snapshots internos desatualizados no repositório Maven
local e execute `install` no reactor completo, conforme a seção de comandos.

Ainda falta para paridade NCC:

- seleção de área (retângulo desenhado no canvas) - depende de infraestrutura
  de interação no canvas que ainda não existe;
- parâmetros por ferramenta (overlap/método/margem/connect/contour/offset
  individuais - hoje compartilhados, ver acima);
- integração com Tools Database ("Pick from DB");
- comparação diferencial mais ampla com resultados do Python (incl. Rest
  Machining, boundary por referência e "Check validity" num board real).

Fechados nesta revisão: boundary por objeto de referência (Gerber ou
Geometry) e validação/sugestão de diâmetro ("Check validity").

## 6. Matriz honesta de paridade

Os rótulos abaixo são deliberadamente conservadores.

| Área | Estado | Observação principal |
| --- | --- | --- |
| Shell, temas e layout principal | forte/parcial | base utilizável; vários menus ainda não têm fluxo completo |
| Plot 2D e interação | forte/parcial | Canvas funcional; ainda não é o renderer final nem foi perfilado para placas enormes |
| Árvore lateral Gerber | forte/parcial | aparência e ações principais implementadas; editor ausente |
| Importação Gerber | forte/parcial | boa cobertura do subconjunto real testado; ampliar corpus de compatibilidade |
| Ferramentas Gerber | parcial | Isolation, Cutout e NCC existem; NCC agora é multi-tool com Rest Machining, boundary por referência e "Check validity" - falta seleção de área no canvas e Tools DB |
| Editor Gerber | ausente | maior lacuna funcional da área Gerber |
| Importação/plot Excellon | parcial | parser, plot e drill G-code existem; editor e opções avançadas faltam |
| Geometry | inicial/parcial | multi-tool ("multigeo") via NCC, com Geometry -> CNC preservando a ferramenta de cada trajeto; edição e outras operações (Paint, Sub, Panelize) faltam |
| CNC Job | parcial | geração/plot/save básicos; painel e opções avançadas do legado faltam |
| Persistência de projeto | forte/parcial (Gerber/Excellon), inicial (Geometry/CNC Job) | Gerber/Excellon embutem geometria (WKT) no mesmo formato do `.FlatPrj` do Python; Geometry não é persistido, CNC Job só guarda o G-code |
| Calculadoras | parcial | três calculadoras implementadas |
| Transformations | forte/parcial | Rotate/Skew/Scale/Flip/Offset completos para Gerber/Excellon/Geometry; falta Buffer e referência "Object" |
| Tools Database | ausente | necessário para paridade de ferramentas |
| Preferências globais | inicial/parcial | tema e algumas opções; longe da cobertura do Python |
| Automação/CLI/scripts | ausente | não é a prioridade imediata |

“Forte/parcial” não significa compatibilidade certificada. Significa que o
fluxo principal existe e tem testes, mas ainda há casos e opções do legado a
cobrir.

## 7. Limitações e dívida técnica conhecidas

### Persistência ainda não é um modelo editável

`ProjectFile` salva caminhos de fontes e referências de saída. Ao reabrir, os
arquivos de fabricação são parseados novamente. Geometry gerada, geometria de
plot de CNC Job, propriedades por ferramenta e futuras edições não são
serializadas como snapshot.

Isso era aceitável antes de existirem editores, mas deixa de ser suficiente
assim que o usuário puder alterar um Gerber/Excellon. Antes do Gerber Editor, o
formato deve ganhar versão e persistência de objetos editados sem quebrar os
projetos atuais.

**Transformations (seção 4) já é a primeira fatia afetada por essa lacuna**:
girar/espelhar/escalar/mover um objeto altera seu estado em memória, mas
salvar e reabrir o projeto reparseia o arquivo de origem do zero, perdendo a
transformação. Não há workaround hoje além de reexportar/salvar o objeto já
transformado como novo arquivo antes de fechar - reforça a prioridade de 9.3.

### `MainWindow` concentra responsabilidades demais

`flatcam-fx/.../MainWindow.java` tem hoje cerca de 2.650 linhas e concentra
estado, menus, árvore, diálogos e orquestração de jobs. Não é necessário
reescrever a tela agora, mas novas áreas grandes devem extrair controladores ou
serviços coesos. A implementação do editor não deve aumentar indefinidamente
essa classe.

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

### Diferenças intencionais já aceitas

- Operações pesadas rodam em background e são canceláveis.
- Progresso deve refletir trabalho real sempre que for mensurável.
- Ícones legados podem receber tratamento de contraste no tema escuro.
- Controles e amostras de cor podem ser modernizados sem mudar sua semântica.
- JTS substitui Shapely/GEOS na implementação Java; compare resultados por
  tolerância geométrica, não por igualdade textual ou ordem de coordenadas.

## 8. Próximo passo recomendado

**NCC multi-tool com Rest Machining, boundary por objeto de referência,
"Check validity", Transformations, e persistência embutida de Gerber/Excellon
(compatível com o `.FlatPrj` do Python) foram concluídos** (seções 5, 4 e
9.3) - critérios de aceite verificados via testes do reactor (112 testes) e
smoke test do app; validação visual dos painéis feita ao vivo com o usuário
a cada rodada. A compatibilidade Python é verificada por leitura de código +
round-trip Java, não contra uma instalação Python real (indisponível neste
ambiente) - ver seção 9.3 para o aviso completo.

O que resta de 9.3 (Geometry e CNC Job com o mesmo tratamento) precisa de
mudanças de modelo reais antes de qualquer serialização (Geometry precisa de
um dict de parâmetros CAM persistente por ferramenta; CNC Job precisa reter
uma lista por segmento durante a geração) - cada um é essencialmente seu
próprio projeto, no mesmo espírito de "Excellon+Gerber primeiro" que guiou
essa fase. Alternativamente, dado que a lacuna mais visível agora é a
ausência total de um editor, faz sentido também considerar avançar direto
para o **Gerber Editor (9.4)** usando o que já existe (Gerber com geometria
embutida já é compatível com a ideia de "objeto editável que sobrevive a
save/reload"), adiando Geometry/CNC Job para quando algo realmente força a
mão (ex.: um NCC resultado precisar sobreviver a um reload).

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

### 9.1 Completar a paridade NCC (o que resta)

- área selecionada (retângulo desenhado no canvas) - depende de infraestrutura
  de seleção no canvas ainda inexistente;
- Tools Database;
- opções restantes do painel Python (parâmetros por ferramenta individuais);
- fixtures diferenciais e casos de desempenho (incl. Rest Machining e
  boundary por referência num board real).

Concluído nesta revisão: boundary por objeto de referência e
validação/sugestão de diâmetro ("Check validity") - ver seção 5.

### 9.2 Transformations - concluído nesta revisão (ver seção 4)

Rotate/Skew/Scale/Flip/Offset reutilizáveis para Gerber, Excellon e Geometry,
com motor no núcleo (`org.flatcam.cam.transform`), independente de JavaFX.
Falta apenas Buffer (distância/fator) e a referência "Object" - ambos
pequenos, adicionáveis quando houver demanda real.

### 9.3 Modelo de objetos e projeto versionado

**Fase 1 concluída nesta revisão (2026-09-22): Gerber + Excellon com
compatibilidade real de arquivo com o `.FlatPrj` do Python.** O usuário pediu
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
- 6 testes de round-trip (`ProjectFileIOTest`), incluindo um que verifica
  que o arquivo salvo por padrão **é XZ de verdade** (bytes não são JSON
  puro) e outro que carrega um projeto v1 legado reparseando o arquivo.

**Simplificações documentadas** (por falta do dado na camada de parsing
atual, não por atalho de serialização):
- `apertures[code]['geometry']` é uma lista de **um item** (a união já
  calculada), não uma entrada por flash/stroke como o Python faz - o
  `GerberParser` não retém geometria por flash individualmente hoje. Não
  afeta CAM/plot (mesma união), só afetaria um editor futuro que quisesse
  selecionar um flash específico.
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

**Importante**: a compatibilidade Python foi verificada **lendo o
código-fonte** e testada de ponta a ponta **do lado Java** (round-trip via
`ProjectFileIOTest`). Não foi verificada abrindo um arquivo salvo por este
app numa instalação real do FlatCAM Python + Shapely - este ambiente de
desenvolvimento não tem Python com as dependências do FlatCAM instaladas
(só Python puro, sem `shapely`/`PyQt5`, e sem acesso à internet para
instalar). Se o usuário tiver uma instalação Python funcional, vale testar
abrir um `.fcnproj` salvo por este port lá antes de confiar cegamente na
compatibilidade.

**O que falta para completar 9.3**:
- Geometry: precisa de um dict `data` persistente por ferramenta (parâmetros
  CAM) que este port simplesmente não tem hoje - é uma mudança de modelo,
  não só de serialização.
- CNC Job: precisa reter uma lista por segmento com "kind" (`gcode_parsed`
  do Python) durante a geração - `GCodeGenerator` hoje só produz duas
  geometrias já unidas (viagem/corte), não uma lista ordenada por segmento.
- Extensão do arquivo continua `.fcnproj` (não `.FlatPrj`) por convenção -
  deixa claro qual app salvou, mesmo os dois lendo/escrevendo formatos
  equivalentes para Gerber/Excellon.
- Comandos reversíveis/undo-redo (bullet original do roadmap) continuam sem
  desenhar - dependem do editor existir primeiro.

### 9.4 Gerber Editor

Implementar por fatias verticais, não como um bloco único:

1. sessão de edição com Aplicar/Cancelar;
2. seleção e hit testing;
3. command stack com undo/redo;
4. mover, copiar e excluir;
5. pads, tracks, regions e apertures;
6. operações avançadas do editor legado;
7. persistência e reabertura do resultado editado.

Depois disso, repetir a estratégia para o Editor Excellon e ampliar Geometry e
CNC Job até a paridade necessária.

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
.\mvnw.cmd -q clean test
.\mvnw.cmd -q install -DskipTests
.\mvnw.cmd -q -pl flatcam-fx org.openjfx:javafx-maven-plugin:0.0.8:run
```

### Linux/macOS

```bash
./mvnw -q clean test
./mvnw -q install -DskipTests
./mvnw -q -pl flatcam-fx org.openjfx:javafx-maven-plugin:0.0.8:run
```

O `install` antes do `javafx:run` é importante. Ao executar apenas
`flatcam-fx`, Maven pode resolver `flatcam-cam` e `flatcam-application` a partir
de snapshots antigos em `~/.m2`. O app pode abrir e falhar somente ao clicar
numa ferramenta cuja classe nova não esteja instalada.

Use o goal JavaFX totalmente qualificado; `javafx:run` curto pode não ser
resolvido sem `pluginGroups` no `settings.xml`.

## 12. Mapa de arquivos

| Caminho | Papel |
| --- | --- |
| `flatcam-application/.../job/` | executor, contexto, handle, progresso e cancelamento de jobs |
| `flatcam-application/.../project/` | schema e IO do `.fcnproj` (v2: Gerber/Excellon com geometria embutida) |
| `flatcam-application/.../project/flatprj/` | codecs compatíveis com o `.FlatPrj` do Python - `WktJson`, `GerberFlatPrjCodec`, `ExcellonFlatPrjCodec` |
| `flatcam-cam/.../gerber/` | parser e geração de geometrias Gerber |
| `flatcam-cam/.../excellon/` | parser e modelo Excellon |
| `flatcam-cam/.../isolation/` | Isolation Routing |
| `flatcam-cam/.../cutout/` | Board Cutout |
| `flatcam-cam/.../ncc/` | Non-Copper Clearing (multi-tool + Rest Machining) |
| `flatcam-cam/.../geometry/ToolGeometry.java` | par diâmetro+geometria de uma ferramenta dentro de um objeto Geometry multi-tool ("multigeo") |
| `flatcam-cam/.../transform/` | motor de Transformations (Rotate/Scale/Skew/Mirror/Offset) - `TransformOp` (sealed) e `TransformReference` |
| `flatcam-cam/.../gcode/` | parâmetros, geração e resultado de G-code |
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
2. Rodar `git status --short` e `git log -5 --oneline`.
3. Rodar `clean test` para estabelecer baseline.
4. Confirmar no código Python o próximo comportamento a portar.
5. Trabalhar primeiro no núcleo e nos testes, depois ligar a UI.
6. Instalar o reactor e executar o smoke test JavaFX.
7. Atualizar este documento se o estado ou a prioridade mudou.
8. Mostrar `git diff --check`, revisar o diff e só então commitar.

## 15. Resumo executivo

O FlatCAM Next já deixou de ser um esqueleto: carrega e plota Gerber/Excellon,
tem uma árvore lateral próxima do legado, jobs com progresso/cancelamento,
Isolation, Cutout, NCC multi-tool com Rest Machining e boundary por
referência, Geometry -> CNC, G-code, e Transformations (Rotate/Skew/Scale/
Flip/Offset) completas para os três tipos de objeto. O próximo trabalho
recomendado é evoluir o modelo de objetos/projeto para algo versionado e
editável (seção 9.3) - pré-requisito da persistência de transformações entre
sessões e do Gerber Editor, a maior lacuna funcional ainda aberta.
