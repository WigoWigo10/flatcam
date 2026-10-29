# Correções dos relatos da Beta — 2026-10-29

Base: `aa23fa15` / 0.1.0-beta.2. Alterações de desenvolvimento após a
[auditoria](AUDITORIA_UX_REPORTS_20261029.md); não são um novo MSI publicado.

## Resultado e escopo

| Achado | Ajuste entregue |
| --- | --- |
| UX-01 | NCC e Isolation usam conversor independente da localidade e sem arredondamento dos campos editáveis. Ponto/vírgula, MM/IN e confirmação antes de Adicionar chegam ao gerador com a precisão de `double`. NCC também mantém overlap/offset importados e Isolation mantém overlap da ferramenta. Entradas inválidas ou fora do intervalo do Spinner permanecem visíveis, sem clamp silencioso ao confirmar; Adicionar explica o intervalo. |
| UX-02 | NCC e Isolation retomam a mesma instância de formulário na sessão, inclusive ao alternar ferramentas. O comando global retoma a última operação; ações do objeto retomam o formulário daquela origem. Origem removida/substituída e troca de projeto invalidam o cache. Fechar explicitamente descarta aquele painel; Reset continua disponível. |
| UX-03 | NCC registra os parâmetros efetivamente enviados, identifica alterações posteriores no rascunho, mostra execução local e permite copiar o resumo. O cálculo continua usando o snapshot original. Resumo disponível também nas Propriedades da Geometry. |
| UX-04 | Connect e Contour comuns do Rest respeitam as preferências NCC na abertura e no Reset. Título e tooltip esclarecem que substituem os parâmetros individuais nesse modo. |
| UX-05 | No formulário NCC, ações explícitas mostram a versão atual ou anterior, ocultando somente outras saídas NCC da mesma origem. O resumo apresenta as linhas de parâmetros alteradas. Nenhuma saída é apagada ou ocultada automaticamente ao gerar. |
| UX-06 | Salvar o projeto guarda o rascunho NCC, ferramentas, seleção, parâmetros locais da DB, área, referência, unidades e resumo original dos resultados. A reabertura valida assinaturas SHA-256 das geometrias de origem/referência antes de restaurar o rascunho. Incompatibilidade abre os padrões com mensagem, sem descartar os objetos. |
| UX-07 | Erro de diâmetro destaca a célula, aponta a linha e oferece Corrigir/Descartar pelo menu da tabela. Mantém seleção e validação de todas as linhas; não usa silenciosamente um valor anterior. Duplicidade aponta o rascunho editado, mesmo quando a outra linha aparece depois na tabela. |
| UX-08 | Paint permite editar o diâmetro por F2/duplo clique e mantém a identidade e os parâmetros da fresa. Lista aceita vírgula decimal com separação por ponto e vírgula/espaço e continua lendo listas antigas com pontos decimais. Tooltip atualizado. |
| UX-09 | Diálogos de salvamento do FX recusam a pasta da distribuição instalada, explicam o motivo e sugerem Documentos. Preferência de pasta externa continua respeitada. Isso não modifica o comportamento do MSI antigo. |

## Limites importantes

- Persistência de rascunho implementada para **NCC**; o cache de sessão também
  cobre **Isolation**. Não é um sistema geral de presets de todas as ferramentas.
- O rascunho é gravado **ao salvar explicitamente o projeto**. Alterar somente
  o formulário CAM não marca os objetos como modificados nem muda o aviso de
  alterações não salvas. Fechar explicitamente a ferramenta descarta seu rascunho.
- `.fcnproj` guarda os metadados opcionais em `_java.camWorkspace`; leitores
  antigos podem ignorá-los e continuam lendo os objetos. `.FlatPrj` exportado
  pelo FX também carrega essa extensão e o leitor FX a reconhece, mas **não se
  promete preservação ao abrir e regravar pelo Python**: o Python pode ignorá-la.
- O resumo registra a geração original; editar/transformar posteriormente a
  Geometry não muda retroativamente sua receita. As ações de comparação no
  formulário usam as versões geradas naquela sessão de ferramenta; depois de
  reabrir o projeto, os resumos estão nas Propriedades dos resultados.
- No Paint, prefira `0,198; 0,25` ou `0.198; 0.25`. Vírgula em um único número
  sem ponto é decimal; `1,2` significa `1.2`. Use `1; 2` para duas ferramentas.
  Listas com várias vírgulas sem separação explícita são recusadas.
- Os diálogos protegem novos salvamentos interativos. Arquivos pessoais já
  dentro da instalação e caminhos explícitos do Terminal/API continuam exigindo
  que o usuário os mova para fora antes de atualizar ou desinstalar.
- Não houve nova instalação MSI, ensaio CNC físico, benchmark ou rodada manual
  com o projeto privado. Não foram alterados os algoritmos de trajetórias.

## Evidências

- `CamNumericInputTest`: matriz NCC/Isolation × MM/IN × pt-BR/en-US ×
  ponto/vírgula × confirmação/adição direta; confere parâmetros entregues ao
  gerador, margem/overlap, valor inválido/fora do intervalo e Rest/Reset.
- `CamDraftResumeTest`, `CamRecipeFeedbackTest`: retomada, origem trocada,
  mudança Seed → Standard durante o worker, versões e visibilidade das camadas.
- `CamWorkspaceTest`: salvar/reabrir projeto nativo após gerar, precisão dos
  parâmetros da DB e recusa de assinatura de origem incompatível.
- `PaintDiameterEditingTest`, `ToolDiameterEditingTest`: alteração de diâmetro
  mantendo método/margem/overlap, erro/duplicidade acionáveis sem mudar seleção.
- `PanelTextContrastTest`: status e texto da receita nos quatro temas. O teste
  encontrou contraste fraco no novo status em Preto Puro; corrigido com texto
  da paleta e opacidade integral. PNGs opcionais em `flatcam-fx/target/ux-feedback-*.png`.
- `UserWorkspacePathsTest`: limites da pasta instalada, subpastas e arquivos
  externos, incluindo caminhos acentuados. Não substitui uma nova instalação MSI.
- Antes das correções: `target/ux-numeric-red.log` (17 erros de asserção em 33
  casos), `ux-draft-red.log` e `ux-recipe-red.log`.
- Controle do teste: foi reintroduzido temporariamente o arredondamento somente
  no conversor. O mesmo teste de precisão falhou em 16 de 32 casos
  (`target/ux-numeric-mutant-red.log`); código restaurado e testes repetidos.
- O primeiro `verify` revelou que a limpeza do projeto precisava funcionar
  também em hosts Tcl sem `toolTab`. Guarda corrigida; os testes de Terminal e
  reabertura continuam exercitando esse caminho, sem enfraquecer as asserções.

Os testes usam dados sintéticos, preferências em memória e cenas JavaFX reais.
Não houve bisect: os defeitos foram reproduzidos na Beta 2, sem um ponto
anterior conhecido em que todos esses contratos já funcionassem.

## Validação final

`mvnw.cmd verify` completo: **2.247 registros, 2.229 aprovados, 18 opcionais
ignorados, zero falhas/erros**. Módulos: test-support 20 aprovados; CAM 948
aprovados e 1 ignorado; application 172 e 9; FX 1.089 e 8, respectivamente.

- Log: `target/ux-reports-fixes-verify-final.log`.
- Contagens XML: `target/ux-reports-fixes-verification.json`.
- Regressões após restaurar o conversor e corrigir o host Tcl:
  `target/ux-guard-restored-green.log`.
- Auditoria repetida: `target/ux-reports-audit-20261029-134823-340/`,
  com as mesmas 33 observações. **Zero diâmetros incorretos** na matriz
  pt-BR/en-US; `samePanel=true`, Rest `[false,false]`, comando global retorna
  a NCC e salvar/reabrir mantém margem `3.14` e método `LINES`.
- Catálogos: `python tools/write_ui_catalogs.py --strict --check`,
  **3.833 entradas por idioma, nenhuma pendente**. `git diff --check` passou.

A repetição programática não substitui a avaliação manual dos novos controles
no aplicativo nem a validação de uma próxima distribuição MSI.
