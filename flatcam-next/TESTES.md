# Infraestrutura e execução de testes

## Executar

Na pasta `flatcam-next`, no PowerShell:

```powershell
.\mvnw.cmd -q test
.\mvnw.cmd -q install
```

Os dois comandos executam testes com a limpeza padrão JUnit ativa (`ALWAYS`).
`install` também atualiza os artefatos locais usados ao iniciar somente o módulo
JavaFX. Após mudar módulos/dependências, use o reactor completo ou `-am`; uma
execução isolada pode usar snapshots antigos do repositório Maven local.

O parent fixa JUnit via BOM **6.1.3** e Surefire **3.5.4** para todos os módulos.
A mudança para JUnit 6.1 permite usar a SPI pública de estratégia de remoção
de TempDir, em vez de hooks de limpeza diferentes por classe de teste.
Essa SPI ainda é experimental; revisar seu contrato ao atualizar o JUnit.
Referências oficiais: [TempDir](https://raw.githubusercontent.com/junit-team/junit-framework/r6.1.3/junit-jupiter-api/src/main/java/org/junit/jupiter/api/io/TempDir.java)
e [estratégias de remoção](https://raw.githubusercontent.com/junit-team/junit-framework/r6.1.3/junit-jupiter-api/src/main/java/org/junit/jupiter/api/io/TempDirDeletionStrategy.java).

## Limpeza no Windows

Execuções anteriores falharam com `DirectoryNotEmptyException` na limpeza JUnit
de diferentes testes, sem falha nas asserções. Os diretórios já tinham sumido
quando inspecionados depois. Isso é compatível com uma remoção transitória,
mas **a causa exata no sistema não foi comprovada**. Não foi atribuído o
problema a antivírus, JDK ou vazamento específico do aplicativo.

O módulo `flatcam-test-support`, consumido exclusivamente com escopo `test`,
fornece `WindowsTempDirDeletionStrategy` e sua configuração JUnit compartilhada.
O tratamento:

- Delega a remoção recursiva e o tratamento de links à estratégia padrão JUnit.
- Só no Windows/default filesystem, trata falhas `DirectoryNotEmptyException`
  em diretórios dentro da raiz exata recebida do JUnit.
- Confirma que o diretório está vazio, fecha o stream de enumeração e tenta
  removê-lo individualmente. Não repete uma remoção recursiva de conteúdo novo.
- Faz no máximo seis tentativas adicionais, com esperas de 25/50/75/100/125 ms
  (375 ms por diretório). Não adiciona espera quando a limpeza padrão passa.
- Preserva falhas persistentes, erros de acesso e interrupções. Não usa
  `IgnoreFailures`, GC forçado, reexecução automática de testes ou `NEVER` global.
- Não segue links/junctions para fora da raiz na repetição; valida os caminhos
  normalizado e resolvido. Fora do Windows, mantém o resultado padrão.

Isso substitui o helper anterior usado somente em `ProjectFileIOTest` e
`CncSettingsPersistenceTest`. A estratégia comum cobre também parâmetros
`@TempDir`, e respeita as políticas de limpeza explícitas para diagnóstico.
Nenhum temporário antigo foi varrido globalmente e nenhum projeto do usuário
foi removido. Falhas que persistirem continuam precisando de investigação.

## Proteções verificadas pelos testes de infraestrutura

Testes determinísticos cobrem recuperação transitória, limite de repetição,
diretório não vazio, conteúdo surgindo durante a espera, erros de acesso,
ordem filho/parent, limites da raiz, desaparecimento concorrente e interrupção.

Testes de ciclo de vida usam o launcher JUnit na mesma JVM e verificam que:

- Cem testes internos com recursos fechados terminam sem diretórios restantes,
  antes de sair da JVM.
- Uma asserção intencionalmente incorreta continua sendo falha e tem limpeza.
- `@TempDir(cleanup = NEVER)` explícito é respeitado; o teste depois remove seu
  próprio diretório vazio.
- No Windows, um handle com `NOSHARE_DELETE` causa falha real de limpeza. O
  controle fecha o handle depois e verifica a remoção. Não exigir falha de
  qualquer arquivo aberto: alguns handles permitem compartilhamento de remoção.
- Junctions Windows não apagam o conteúdo do destino fora da raiz em limpeza
  padrão nem são seguidas pela repetição.

As falhas intencionais dos fixtures internos são verificadas pelo teste externo,
não falhas aceitas da suíte. Casos exclusivos do Windows são ignorados em outros
sistemas. A configuração compartilhada funciona também no classpath de testes
da IDE; evitar sobrescrevê-la com outro `junit-platform.properties`.

## JavaFX e dependências de execução

Surefire carrega JavaFX pelo classpath. Apenas a JVM de testes do módulo FX
recebe `--enable-native-access=ALL-UNNAMED`, mantendo argumentos extras via
`argLine`. O launcher do aplicativo continua usando a permissão específica
`javafx.graphics`; essa alteração não muda GPU, renderização ou preferências.

O suporte de testes e JUnit não fazem parte do classpath de execução do FX.
Para conferir:

```powershell
.\mvnw.cmd -pl flatcam-fx -am org.apache.maven.plugins:maven-dependency-plugin:3.8.1:tree -Dscope=runtime
```

Logs de jobs que falham de propósito e o aviso JavaFX sobre classpath/unnamed
module podem aparecer nos testes. Conferir o resultado do Surefire, não apenas
buscar a palavra `WARNING` no console.

## Comparação com Python

O ambiente e os comandos específicos estão em [COMPARACAO_CAM.md](COMPARACAO_CAM.md).
Para o oráculo legado multipart, usar Shapely **1.8.5.post1** em pasta isolada,
sem substituir o Shapely da `.venv` principal. Os relatórios registram versões
Python/Shapely e hashes do fonte legado; conservar esses dados ao comparar runs.

Os sete testes auxiliares do harness não substituem executar os casos CAM reais.
Os três casos numéricos ainda divergentes continuam pendentes; esta mudança de
infraestrutura não altera algoritmos, critérios CAM ou a classificação deles.
Projetos privados e relatórios devem continuar fora do Git, em `target/`.

## Verificação local — 2026-10-03

Windows 11 amd64, Oracle JDK 25.0.4.1 e Maven Wrapper 3.9.12:

- Três execuções finais consecutivas de `mvnw.cmd -q test` aprovadas, com
  limpeza ativa: **807 registrados, 796 aprovados, 11 opcionais ignorados**,
  zero falhas/erros. Suporte: 20; CAM: 521; application: 114 (9 ignorados);
  FX: 152 (2 ignorados). Os cem testes internos de ciclo de vida não são
  somados novamente aos totais do Surefire.
- `mvnw.cmd -q install` também aprovado com limpeza ativa.
- Sete testes Python auxiliares do harness aprovados, sem reexecutar ou
  reclassificar a comparação completa do projeto real nesta etapa.
- Probe Java/Maven de bibliotecas nativas e renderização offscreen aprovado.
- Árvore runtime do FX verificada sem JUnit e sem `flatcam-test-support`.

Nenhum desses comandos precisou de `cleanup.mode.default=NEVER` global.
O fixture interno que pede `NEVER` é um controle de política, limpa sua própria
pasta vazia e não deixa os temporários da suíte preservados.

## Limites da evidência

Verificação de **2026-10-05**: `mvnw.cmd -q install` completo aprovado com
limpeza normal ativa: 984 registrados, 972 aprovados, 12 opcionais ignorados,
zero falhas/erros (602 CAM, 114 application, 248 FX, 20 test-support).
Probes nativos `--probe` e `--probe --software` passaram na entrega anterior.
Contar apenas os XML produzidos pela execução atual: relatórios antigos de
classes removidas podem permanecer em `target/surefire-reports`.

Regressões do Terminal: `TerminalPanelTest` verifica execução no worker,
FX responsiva, entrada serializada, cancelar, histórico, erros, progresso e
saída limitada. `TclExecutionTest` verifica a fila FX cancelada e propagação
de erro. `TclLiveHostTest` usa MainWindow sem janela visível e geradores reais,
verificando Z do G-code, nomes entre tipos, importação/CAM e descarte de
resultados cuja origem/projeto mudou. Os testes FX são Windows-only.
`TclInterpreterTest` verifica cancelamento em loops/substituição/parse;
`StableInteriorPointTest` cobre a grade estreita, cancelamento inicial,
coordenadas grandes, não poligonais e precisão inválida.

`CompactJobProgressTest` cobre percentual/cancelamento compartilhados, fases
indeterminadas, visibilidade/tamanho e quatro temas. Teste de integração do
MainWindow verifica a troca de apresentações, parent do Cancelar junto da
barra longa e cancelamento do handle real sem modificar preferências. Capturas
opcionais (`-Dflatcam.tests.snapshots=true`) vão para
`flatcam-fx/target/compact-progress-TEMA.png`; foram inspecionadas nesta entrega.
O mesmo teste compara os centros do botão e do desenho do Cancelar; a versão
anterior falhou com deslocamento de 6 px nos quatro temas. A regressão passa
com X centralizado e verifica hover/pressionado/foco e o botão expandido.

`AnimatedSplitPanelTest` cobre sete cenários: posições intermediárias do painel
lateral/console, restauração de tamanho mínimo/clip/mouse, inversão com callback
antigo, ciclos repetidos, início recolhido, mudança de destino por monitor,
guardas de persistência e identificação de divisores aninhados. Uma Timeline
real verifica múltiplos frames e processamento de outro evento FX durante a
transição. Os testes não escrevem preferências; fluidez com projeto denso e
troca física de monitor continuam sendo verificações manuais.

`TableSelectionThemeTest`: oito casos parametrizados nos quatro temas verificam
TableView genérica e nos painéis de ferramenta/objeto, cores computadas de
seleção inicial sem foco, foco CSS, hover, seleção múltipla, troca de tema,
desseleção e cores de totais/ComboBox. Contraste do texto selecionado >= 4,5:1.
Falhou antes da correção e passou depois; snapshots opcionais em
`flatcam-fx/target/table-selection-TEMA.png` foram inspecionados nos quatro temas.
O teste de CSS simula a pseudo-classe da tabela; foco real por clique continua
exigindo validação manual. Não grava preferências nem altera seleção funcional.

`CompactToolsTableTest`: quatro casos nos temas, usando tabelas reais de
Isolation/NCC/Geometry→CNC e ciclos de largura 180/240/320/560 px. Cobrem limites
de #/TT, preenchimento do viewport, alinhamento/tamanho dos campos, C1 sem
reticências, seleção/itens preservados e troca de perfil refletida no modelo.
Snapshots opcionais em `flatcam-fx/target/compact-tools-PAINEL-TEMA.png` foram
inspecionados nos quatro temas. Não gera CAM nem escreve preferências.

`PlotStatusControlsTest`: onze casos exercitam botão/rota toggleGrid, estado do
Plot e persistência injetada, passos independentes/vinculados, seis entradas
inválidas e atualização imediata de coordenadas/prévia/cruz. MouseEvent reais
verificam área/movimento de objeto com deslocamento livre confirmado e trilha
sem arredondamento/dobras após desligar Snap. Sete casos iniciais falharam antes
da correção (seis entradas inválidas e coordenadas desatualizadas); os demais
protegem comportamento existente. Não escreve preferências reais. Validação
do relato exato no app completo continua sendo manual.

`PlotSnapMouseTest`: teste opcional de desktop, com JavaFX Robot e uma janela
temporária própria. Executa cliques físicos no botão, saída/retorno ao Plot e
transferência de foco do campo de passo, em três ciclos para cada passo (0,1
e 5,0). Verifica estado efetivo, coordenadas e presença/ausência da cruz.
Os dois casos passaram em 2026-10-05; os onze de `PlotStatusControlsTest`
também passaram. A falha persistente relatada pelo usuário não foi reproduzida
nessa janela; não foi aplicada outra correção de execução nem declarada resolvida.
Não grava preferências. Move o mouse real e restaura sua posição ao terminar;
não interagir com o desktop durante o teste. É ignorado na execução padrão.
Desabilitado, aparece como um caso ignorado no Surefire; habilitado, executa
os dois passos parametrizados.

```powershell
.\mvnw.cmd -q -pl flatcam-fx "-Dtest=PlotSnapMouseTest,PlotStatusControlsTest" "-Dflatcam.test.robot=true" test
```

`ProjectTreeRenameTest`: sete casos exercitam a árvore e células reais da
MainWindow com eventos MouseEvent/KeyEvent. Cliques repetidos não renomeiam,
Ctrl/Shift e duplo clique para Propriedades continuam operantes, F2/menu
iniciam edição explícita, commit/Esc funcionam e nomes inválidos/categorias
não são renomeados. Um controle positivo libera temporariamente a proteção
e confirma que o mesmo clique simples acionaria a edição nativa. Não grava
preferências/projetos; interação física e foco de janela continuam a validar.

A comparação CAM privada e os painéis completos não foram testados manualmente
nesta sessão. Warnings de erros deliberados nos testes de jobs/Tcl/arquivos
inválidos não significam falha; consultar o resumo Surefire.

Repetições locais não provam ausência de toda falha intermitente nem substituem
CI em outros sistemas. Casos opcionais ignorados devem ser executados quando
seus fixtures estiverem disponíveis. Testes headless não comprovam paridade
de interação, fluidez visual ou execução segura de uma CNC.
