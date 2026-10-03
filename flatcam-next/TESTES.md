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

Repetições locais não provam ausência de toda falha intermitente nem substituem
CI em outros sistemas. Casos opcionais ignorados devem ser executados quando
seus fixtures estiverem disponíveis. Testes headless não comprovam paridade
de interação, fluidez visual ou execução segura de uma CNC.
