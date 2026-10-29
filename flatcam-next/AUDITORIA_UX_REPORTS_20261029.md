# Auditoria de UX a partir dos relatos da Beta — 2026-10-29

> Atualização posterior: os ajustes implementados, regressões e limites estão
> em [CORRECOES_UX_REPORTS_20261029.md](CORRECOES_UX_REPORTS_20261029.md).
> As observações abaixo descrevem a Beta 2 antes dessas correções.

Base: FlatCAM FX **0.1.0-beta.2**, commit `aa23fa1579c841a3b49800b3f1757ad82345f939`.
Objetivo: encontrar situações semelhantes aos relatos de perda de configuração,
edição do diâmetro e dificuldade para corrigir apenas um parâmetro e gerar novamente.

## Conclusão

A Beta 2 corrigiu manter o painel aberto após gerar e editar a célula de diâmetro.
Esses caminhos não abrangem todas as formas de entrar, sair ou retornar à operação.
Há inconsistências adicionais confirmadas nas entradas numéricas, na reabertura dos
painéis e nos valores iniciais do Rest. Há também lacunas de UX entre o rascunho
visível, os parâmetros do cálculo em andamento e as versões de Geometry criadas.

Prioridade sugerida: **entrada numérica → continuidade do rascunho → identificação
do resultado → Rest e consistência das ferramentas → comparação/persistência**.
A limitação de atualização do instalador continua exigindo o cuidado já documentado.

## Evidência e limites

- Revisão do código e probes programáticos em controles/cenas JavaFX reais.
- Localidades numéricas **pt-BR e en-US**, unidades **MM e IN**, ponto e vírgula.
- **33 registros de observação** na execução final: 20 pt-BR e 13 en-US.
  São observações de uma auditoria, não 33 testes aprovados nem uma declaração de ausência de bugs.
- Entradas e projetos sintéticos; preferências em memória. Nenhum projeto privado foi usado.
- As 104 classes/nested classes auditadas de MainWindow, NCC, Isolation, editor
  de diâmetro, ToolDefaults e AppPreferences coincidem por SHA-256 com o JAR da
  distribuição Beta 2. O recurso build-info do build de desenvolvimento informa
  `sourceRevision=development`; a correspondência dos bytes foi conferida separadamente.
- Evidência final: `target/ux-reports-audit-20261029-125207-377/`.
  A comparação de classes está em `target/ux-audit-20261029/release-class-comparison.json`.
- Não houve nova rodada física de mouse/teclado, instalação MSI, CNC, FPS,
  monitores/DPI ou auditoria de todos os 24 painéis. A evidência MSI citada é da entrega de 28/10.
- Esta etapa acrescenta relatório e ferramentas de auditoria; não corrige o comportamento do aplicativo.

## Achados prioritários

| ID | Prioridade | Classificação | Efeito para o usuário |
| --- | --- | --- | --- |
| UX-01 | P1 | Defeito numérico reproduzido | Confirmar o campo Adicionar pode mudar o diâmetro efetivamente adicionado |
| UX-02 | P1 | Perda de rascunho reproduzida | Reabrir pelo botão reconstrói o formulário com os padrões |
| UX-03 | P1 | Ambiguidade de resultado reproduzida | O painel pode mostrar Standard e a saída ter sido calculada com Seed |
| UX-04 | P2 | Preferência não respeitada | Connect/Contour do Rest começam ligados mesmo com os padrões desligados |
| UX-05 | P2 | Lacuna de comparação | Saídas sucessivas ficam visíveis juntas sem identificação clara da receita |
| UX-06 | P2 | Lacuna de persistência | Salvar o projeto preserva os objetos, mas não recupera o rascunho da operação |
| UX-07 | P2 | Decisão de UX/validação | Uma linha inválida não selecionada bloqueia a geração de outra linha válida |
| UX-08 | P2 | Inconsistência entre ferramentas | Paint usa outra gramática para diâmetros e não oferece a mesma edição por célula |
| UX-09 | P1 condicional | Limitação conhecida do instalador | Arquivos pessoais dentro da pasta instalada podem ser apagados ao atualizar/remover |

P1 indica prioridade de correção pelo impacto no dado usado ou no retrabalho;
não é uma afirmação de acidente físico ou de erro em todo G-code gerado.

### UX-01 — Precisão e localidade não são consistentes na entrada

**Reprodução no componente:** abrir NCC/Isolation, digitar o valor no campo de
nova fresa, confirmar o Spinner e então acionar Adicionar. O probe usa
`Spinner.commitValue()`, o caminho de confirmação do controle, antes do botão.
Não estabelece que todas as sequências de Enter/clique físico produzem o mesmo resultado.

| Controle / localidade | Entrada | Após confirmar | Resultado ao adicionar |
| --- | --- | --- | --- |
| NCC / pt-BR e en-US / MM | `0.1981234567` ou `0,1981234567` | `0.1981` | Fresa `0.1981` |
| NCC / pt-BR e en-US / IN | `0.00781234567` ou vírgula | `0.0078` | Fresa `0.0078` |
| Isolation / pt-BR / MM | `0,1981234567` | `0,2` | Fresa `0.2` |
| Isolation / pt-BR / MM | `0.1981234567` | `0` | Nenhuma fresa adicionada |
| Isolation / en-US / MM | `0.1981234567` | `0.2` | Fresa `0.2` |
| Isolation / en-US / MM | `0,1981234567` | `0` | Nenhuma fresa adicionada |
| Isolation / separador aceito / IN | `0.00781234567` ou vírgula | `0.01` ou `0,01` | Fresa `0.01` |

No NCC, a ValueFactory ainda contém o valor completo; o conversor arredonda
o texto e Adicionar lê esse texto arredondado. O Isolation usa o conversor
padrão do JavaFX: o separador depende da localidade e o texto perde precisão.
Com o separador não aceito, o parse retorna zero, o modelo é limitado ao mínimo
`0.0001`, o texto exibido vira `0` e a adição é recusada. Não foi adicionada uma fresa de diâmetro zero.

Outros controles NCC reproduzidos: margem `1.23456789 → 1.2346` e sobreposição
`37.5123456 → 37.5123`. A importação da DB também usa `format()` ao transferir
sobreposição/offset para strings; esse último caminho foi verificado no código,
não por uma nova interação com o diálogo da DB nesta auditoria.

**Ajuste:** separar valor numérico de formatação de leitura. Entradas editáveis
devem aceitar ponto/vírgula sem arredondar o dado e recusar valores fora do domínio
com mensagem explícita. O formato visual de labels não deve voltar para o cálculo.
Padronizar a política em outros Spinners após inventariá-los, sem assumir que todos têm o defeito.

**Fonte:** `NccToolPanel.java` (spinner/format, adição, importação da DB),
`IsolationToolPanel.java` (spinner/adição). Evidências `ADD_PRECISION` e `OTHER_NUMERIC_INPUT`.

**Regressão necessária:** confirmar/perder foco, Enter, setas e clicar Adicionar;
MM/IN × pt-BR/en-US × ponto/vírgula. Conferir o diâmetro entregue ao gerador,
não apenas o texto da célula. A edição pela célula da Beta 2 continua sendo um caminho distinto e preciso.

### UX-02 — Retornar pela aba funciona; reabrir pelo botão descarta o trabalho

Configurar NCC com margem `3.14159` e método Lines; acionar novamente o mesmo
caminho de abertura usado pelo botão. A instância do painel muda, a margem volta
para `1` e o método para Seed, os padrões dessa configuração. Trocar para outra
ferramenta também substitui o único painel Tool. Não há cache de rascunho nesse caminho.

Uma variante importante: após gerar, o objeto selecionado passa a ser a Geometry
`board_ncc`. Se o usuário estiver em Propriedades e acionar o botão global NCC,
o comando tenta usar essa saída linear como fonte. Não retorna ao rascunho anterior;
na reprodução a aba ativa continuou em Properties, embora o rascunho ainda existisse.

**Ajuste:** distinguir Retomar ferramenta de Abrir para o objeto selecionado.
O comando global pode reativar o painel existente; a ação contextual do objeto
pode abrir/revincular uma operação. Cache de rascunho por ferramenta/origem/unidade
deve preservar os guardas de origem alterada/removida e projeto trocado.

**Fonte:** `MainWindow.openToolPanel`, `openSelectedNccTool`, `finishCamPanel`.
Evidências `REOPEN_RESETS_DRAFT` e `TOOLBAR_AFTER_GENERATION`.

### UX-03 — O resultado e o rascunho visível podem representar configurações diferentes

Iniciar NCC com Seed; manter o worker temporariamente na fila; mudar o campo para
Standard; liberar o cálculo. O editor permanece habilitado. O resultado coincide
com Seed e não com Standard, enquanto o campo visível mostra Standard.

O cálculo usou corretamente o snapshot recebido. A lacuna está em permitir que
o usuário confunda o rascunho novo com os parâmetros da saída concluída. Não é
evidência de que o worker leu parâmetros incorretos nem um benchmark de lentidão.

**Ajuste:** guardar e apresentar a receita do resultado: origem, fresas, método,
Rest/Connect, margem e horário. Sinalizar Rascunho alterado — gere novamente
quando os campos divergirem. Manter a interface responsiva e preservar alterações
feitas durante o cálculo; informar o estado ocupado junto do botão de geração.

**Fonte:** `captureCamGeneration`, `runNccGeneration`, `finishCamPanel`.
Evidência `EDIT_DURING_GENERATION`: `matchesSeed=true`, `matchesStandard=false`, `visibleMethod=STANDARD`.

### UX-04 — Rest ignora os padrões de Connect e Contour

Definir `ncc.connect=false` e `ncc.contour=false` nas preferências isoladas;
abrir NCC. Os controles individuais vêm desligados; os equivalentes Rest vêm
ligados. O construtor e o reset do Rest usam `setSelected(true)`.

Os dois Connect têm funções de escopo distintas e devem continuar existindo:
individual por fresa versus comum ao Rest. O Python inicializa ambos com os padrões
de configuração; não foi feita nova interação com a janela Python nesta sessão.

**Ajuste:** respeitar os padrões também no Rest e esclarecer o escopo com textos
como Parâmetros desta fresa e Parâmetros comuns do Rest. Explicar que o segundo
substitui o individual nesse modo, inclusive nos tooltips.

**Fonte:** `NccToolPanel.java:681` e reset; referência `appTools/ToolNCC.py:533`.
Evidência `REST_DEFAULTS`: main/rest `[false,true]` para ambos os controles.

### UX-05 — Comparar saídas sucessivas exige trabalho manual

Duas gerações produziram `board_ncc` e `board_ncc_2`, ambas visíveis e com
`sourceName=board`. O nome ordinal não informa o que mudou. Isso favorece
sobreposição de desenhos e seleção/avaliação da versão errada; não foi medido impacto em FPS.

**Ajuste:** mostrar qual saída pertence à geração concluída, suas diferenças
em relação à anterior e ações Mostrar só esta, Alternar anterior/atual e
Renomear. Não apagar ou ocultar outras saídas sem uma ação clara do usuário.
Reutilizar a receita/snapshot de UX-03 para esse resumo.

**Fonte:** `uniqueDerivedName`, publicação NCC, dados Geometry/ProjectFile.
Evidência `GENERATED_VARIANTS`.

### UX-06 — O projeto guarda a geometria, mas não a receita da operação

Configurar NCC com margem `3.14` e Lines, gerar, salvar `.fcnproj`, reabrir e
abrir NCC na mesma origem. A Geometry foi restaurada; o formulário voltou a
margem `1` e Seed. Isso é uma lacuna de continuidade, não corrupção do projeto.
Não se afirma aqui que o Python já preserve todos esses rascunhos de forma equivalente.

**Ajuste:** primeiro cache de sessão; depois receitas/presets explícitos por
objeto no projeto nativo, com origem/unidades e validação de compatibilidade.
Separar os escopos: rascunho da operação, padrões globais e cadastro da Tools Database.
Tratar a compatibilidade `.FlatPrj` antes de prometer persistência nesse formato.

**Fonte:** `ProjectFile`, `snapshotProject`, reconstrução dos painéis.
Evidência `PROJECT_DRAFT`, com geração antes do salvamento e duas Geometries restauradas.

### UX-07 — Validação de linha não selecionada precisa de contexto melhor

Deixar um rascunho inválido na fresa da linha 1; selecionar apenas a linha 2,
válida; gerar. Nenhuma requisição é emitida, pois o editor valida toda a tabela.
Esse comportamento é intencional no código atual e não foi classificado como erro de cálculo.

**Ajuste:** destacar a linha/célula com problema e permitir corrigir/descartar
o rascunho sem alterar silenciosamente quais fresas serão usadas. Decidir
explicitamente se a validação da geração deve bloquear também linhas não utilizadas;
não remover os guardas numéricos ou de duplicidade para contornar a mensagem.

**Fonte:** `EditableToolDiameterColumn.commit`. Evidência `UNSELECTED_INVALID`.

### UX-08 — Paint e tabelas equivalentes têm contratos diferentes

`PaintToolPanel.parseDiameters("0,198")` trata a vírgula como separador de lista
e recusa o item zero; `0.198;0.25` é aceito. Isso contrasta com campos individuais
que aceitam vírgula decimal. A tabela Paint também não oferece o mesmo editor
de diâmetro. Seu mapa de parâmetros é indexado pelo diâmetro, o que exige cuidado
para não perder a associação ao substituir valores na lista.

**Ajuste:** definir uma gramática explícita para listas e preferir a edição por
linha, preservando a identidade e os parâmetros da ferramenta. Não estender
cegamente essa edição ao Geometry → CNC: ali os diâmetros podem estar associados
a trajetos já calculados, e a restrição atual tem uma razão técnica.

**Fonte:** `PaintToolPanel.java:74`, tabela e sincronização; `GeometryCncToolPanel`.
Evidência `PAINT_DECIMAL_LIST`. A perda de associação ao editar listas precisa
de ensaio adicional; não é um defeito reproduzido desta rodada.

### UX-09 — Proteger o local de trabalho na distribuição

O ensaio MSI da entrega Beta 2 confirmou que a remoção da Beta 1 apagou um arquivo
extra dentro da pasta instalada. A atualização validada preservou um projeto
externo. Esse achado histórico já está nas notas da Beta 2; não foi repetido nesta auditoria.

**Ajuste:** usar uma pasta de projetos em Documentos como local sugerido,
detectar tentativas de salvar dados dentro da instalação e tornar o cuidado
visível antes da atualização. Revisar a remoção de arquivos desconhecidos em
instaladores futuros; não prometer alterar retroativamente o MSI antigo.

**Fonte:** `release/BETA-0.1.0-beta.2.md` e `release-evidence.json` da distribuição.

## Sequência de implementação proposta

1. **Precisão e localidade:** corrigir os conversores e a leitura de valores;
   regressões cobrindo a confirmação do campo antes de Adicionar/Gerar e importação da DB.
2. **Retomar sem refazer:** preservar o painel ao acionar o comando global e
   guardar o rascunho por ferramenta/origem; testar troca de aba, botão, fonte e projeto.
3. **Resultado identificável:** snapshot da receita e indicação de rascunho
   alterado, com feedback local de execução e comparação explícita das versões.
4. **Rest e seleção:** corrigir padrões/rotulagem; tornar o erro de célula
   acionável sem mudar a seleção de ferramentas de modo inesperado.
5. **Consistência e persistência:** Paint/listas, presets/receitas no projeto e
   resumo copiável da operação para os próximos relatos do testador.

O resumo copiável pode incluir versão/commit, unidades, origem, diâmetros,
método, Rest/Connect e nome do resultado. Geometria e arquivos do usuário não
precisam ser enviados automaticamente. Os diagnósticos técnicos existentes
continuam úteis para crash/travamento; esse resumo cobre o contexto da operação.

## Repetir a auditoria

Requisitos: Windows, JDK 25 em JAVA_HOME, dependências e fixtures de teste compiladas.

```powershell
# Na pasta flatcam-next, caso ainda não exista o classpath do Surefire:
.\mvnw.cmd -pl flatcam-fx -am test -Dtest=MainToolPersistenceTest -Dsurefire.failIfNoSpecifiedTests=false
.\tools\audit-ux-reports.ps1
```

O runner cria uma pasta única em `target/ux-reports-audit-<UTC>/`, compila o probe
contra o classpath de teste e grava `session.json`, `pt-BR/observations.json`,
`en-US/observations.json` e logs. O probe exige PreferencesFactory em memória.
`exit 0` significa **observações coletadas**, não que os problemas foram corrigidos.
Os achados devem virar regressões com expectativa corrigida quando cada ajuste for implementado.
