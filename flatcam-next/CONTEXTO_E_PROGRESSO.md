# FlatCAM Next — contexto, progresso e próximos passos

Este é o documento operacional de continuidade do **FlatCAM Next**. Ele foi
escrito para que uma nova sessão de IA (Codex, Claude ou equivalente) consiga
entender o estado real do projeto, tomar decisões compatíveis com as já feitas
e continuar a migração sem recomeçar a investigação.

> Atualizado em **2026-09-22**. A base anterior a este documento é o commit
> `9f6c7463` (`feat(flatcam-next): add NCC and geometry CNC workflow`). Antes de
> trabalhar, confirme o `HEAD`, o `git status` e os testes: este arquivo é um
> ponto de passagem, não substitui o código como fonte final da verdade.

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
| `flatcam-application` | modelo leve de projeto, jobs, progresso e cancelamento | não depende de JavaFX |
| `flatcam-cam` | parsing, geometria, operações CAM e geração de G-code | não depende de JavaFX |
| `flatcam-fx` | janela, árvore do projeto, painéis de ferramentas, temas e renderização | depende dos dois módulos anteriores |

Não existem ainda módulos separados de renderer, CLI, scheduler, compat ou
native. Só devem ser criados quando houver uma fronteira real que justifique a
separação.

### Verificação mais recente

Antes deste documento, `clean test` passou com **79 testes executados**, sem
falhas, erros ou testes ignorados. Existem 72 métodos anotados com `@Test`;
testes parametrizados explicam a diferença. `JobExecutorTest` registra
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
- Formato `.fcnproj` inicial para caminhos de Gerber/Excellon e referências de
  CNC Jobs.

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

## 5. NCC: estado exato da implementação atual

O commit-base `9f6c7463` adicionou a primeira fatia vertical do Non-Copper
Clearing:

- `NccMethod`: `STANDARD`, `SEED`, `LINES` e `COMBO`.
- Parâmetros: diâmetro, sobreposição, margem, método, conectar trajetos,
  contorno e offset do cobre.
- Boundary pelo próprio Gerber ou pelo convex hull.
- Geração com uma ferramenta, feedback de progresso e cancelamento.
- Resultado criado como objeto Geometry.
- Fluxo completo Geometry -> CNC Job -> G-code.
- Testes sintéticos e teste com o fixture Gerber `simple1.gbr`.

O botão NCC já deve abrir o painel da ferramenta. Se voltar a “não fazer nada”,
primeiro suspeite de snapshots internos desatualizados no repositório Maven
local e execute `install` no reactor completo, conforme a seção de comandos.

Ainda falta para paridade NCC:

- múltiplas ferramentas;
- Rest Machining entre ferramentas;
- seleção de área;
- objeto de referência como boundary;
- integração com Tools Database;
- validações e sugestões de diâmetro compatíveis com o legado;
- comparação diferencial mais ampla com resultados do Python.

## 6. Matriz honesta de paridade

Os rótulos abaixo são deliberadamente conservadores.

| Área | Estado | Observação principal |
| --- | --- | --- |
| Shell, temas e layout principal | forte/parcial | base utilizável; vários menus ainda não têm fluxo completo |
| Plot 2D e interação | forte/parcial | Canvas funcional; ainda não é o renderer final nem foi perfilado para placas enormes |
| Árvore lateral Gerber | forte/parcial | aparência e ações principais implementadas; editor ausente |
| Importação Gerber | forte/parcial | boa cobertura do subconjunto real testado; ampliar corpus de compatibilidade |
| Ferramentas Gerber | parcial | Isolation, Cutout e NCC existem; NCC ainda é fatia inicial |
| Editor Gerber | ausente | maior lacuna funcional da área Gerber |
| Importação/plot Excellon | parcial | parser, plot e drill G-code existem; editor e opções avançadas faltam |
| Geometry | inicial/parcial | NCC cria Geometry e há Geometry -> CNC; edição e operações faltam |
| CNC Job | parcial | geração/plot/save básicos; painel e opções avançadas do legado faltam |
| Persistência de projeto | inicial | guarda principalmente caminhos, não snapshots editáveis |
| Calculadoras | parcial | três calculadoras implementadas |
| Transformations | ausente | planejado após completar a próxima etapa NCC |
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

O próximo incremento pesado deve ser **NCC multi-tool com Rest Machining**.
Ele continua a fatia já aberta, testa o modelo de Geometry por ferramenta e
evita iniciar o editor sobre estruturas ainda imaturas.

### Ordem recomendada dentro desse incremento

1. Inspecionar no Python o modelo de ferramentas, ordenação, Rest Machining,
   boundary e mensagens de validação.
2. Evoluir primeiro os tipos do `flatcam-cam`: lista ordenada de ferramentas,
   parâmetros por ferramenta e resultado segmentado por ferramenta.
3. Implementar o cálculo da área remanescente entre ferramentas, com
   cancelamento e progresso monotônico.
4. Adicionar testes geométricos para ilhas, canais estreitos, áreas vazias,
   diâmetros inválidos/repetidos e um fixture Gerber real.
5. Adaptar Geometry e Geometry -> CNC para preservar a associação dos paths
   com cada ferramenta.
6. Só então criar a tabela/edição multi-tool no painel JavaFX.
7. Fazer smoke test manual do fluxo Gerber -> NCC -> Geometry -> CNC -> Save.

### Critérios de aceite

- O usuário consegue configurar ao menos duas ferramentas e sua ordem.
- A ferramenta menor processa apenas o material que permaneceu após a maior
  quando Rest Machining está ativo.
- Resultado por ferramenta é identificável e chega corretamente ao G-code.
- Parâmetros impossíveis falham com mensagem útil antes de iniciar o job.
- Cancelamento não publica resultado parcial como se fosse concluído.
- Progresso não regride e termina em 100% no sucesso.
- Testes do reactor passam e o app inicia com os módulos recém-instalados.

Não decida silenciosamente se várias ferramentas devem produzir troca de
ferramenta num único CNC Job ou jobs separados. Primeiro confirme o comportamento
do Python e registre qualquer divergência deliberada.

## 9. Roadmap depois do próximo incremento

Esta é a sequência recomendada, sujeita a revisão com evidência do legado:

### 9.1 Completar a paridade NCC

- área selecionada;
- boundary por objeto de referência;
- Tools Database;
- validação/sugestão de ferramentas;
- opções restantes do painel Python;
- fixtures diferenciais e casos de desempenho.

### 9.2 Transformations

Criar transformações geométricas reutilizáveis para Gerber, Excellon e
Geometry: mover, rotacionar, espelhar, escalar, skew e offset conforme o legado.
Manter a matemática no núcleo, independente de JavaFX, e definir claramente
unidade, pivô e tolerância.

### 9.3 Modelo de objetos e projeto versionado

- separar identidade, metadados, geometria e origem do arquivo;
- preservar objetos derivados e parâmetros CAM;
- suportar migração do `.fcnproj` atual;
- preparar comandos reversíveis e snapshots necessários para edição;
- definir política para arquivos fonte movidos ou ausentes.

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
| `flatcam-application/.../project/` | schema e IO do `.fcnproj` |
| `flatcam-cam/.../gerber/` | parser e geração de geometrias Gerber |
| `flatcam-cam/.../excellon/` | parser e modelo Excellon |
| `flatcam-cam/.../isolation/` | Isolation Routing |
| `flatcam-cam/.../cutout/` | Board Cutout |
| `flatcam-cam/.../ncc/` | Non-Copper Clearing |
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
Isolation, Cutout, NCC inicial, Geometry -> CNC e G-code. A maior fatia recém
aberta é NCC. O próximo trabalho recomendado é torná-lo multi-tool com Rest
Machining; depois completar NCC, implementar Transformations, evoluir o modelo
persistente e só então iniciar o Gerber Editor sobre uma fundação adequada.
