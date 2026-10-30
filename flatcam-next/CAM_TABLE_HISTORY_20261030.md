# Histórico das tabelas CAM e proteção de rascunhos NCC — 2026-10-30

Continuação dos refinamentos da Beta 3, ainda em desenvolvimento. A versão,
tag e instaladores entregues não foram alterados nesta etapa.

## Comportamento

- Com foco na tabela de fresas de NCC, Isolation ou Paint, **Ctrl+Z** desfaz e
  **Ctrl+Y** ou **Ctrl+Shift+Z** refaz. O menu de contexto oferece as mesmas
  ações e a ajuda da tabela explica o alcance.
- Inclusão, remoção e alterações de diâmetro, perfil e parâmetros próprios
  da fresa preservam ordem, seleção e configurações de usinagem importadas.
  Uma nova alteração descarta o ramo de Refazer. Até 100 passos por tabela.
- Dentro de um campo de texto, Ctrl+Z continua desfazendo a digitação.
  Um diâmetro inválido pendente bloqueia o histórico da tabela até ser
  corrigido ou descartado; não é substituído silenciosamente pelo valor anterior.
- O histórico é local à sessão da ferramenta. Trocar a origem, usar Reset Tool,
  fechar explicitamente ou restaurar o formulário pelo projeto inicia outro
  histórico. Parâmetros comuns, como margem geral e Rest, ficam fora desse
  histórico. Não foi criado histórico de exclusão na Tools Database.
- Ajustes do formulário NCC, inclusive texto incompleto, agora contam como
  alterações não salvas. Fechar o aplicativo, criar ou abrir outro projeto
  usa a proteção existente de Salvar/Descartar/Cancelar. O diálogo informa
  quando há ajustes NCC e que salvar preserva esse rascunho.
- Abrir a ferramenta apenas para consultar os valores padrão ou um rascunho
  já salvo não torna o projeto alterado. Desfazer até o conteúdo salvo também
  volta ao estado limpo.
- A gravação assíncrona marca somente o conteúdo efetivamente escrito.
  Ajustes feitos durante a gravação continuam pendentes. Uma edição NCC
  durante o carregamento impede a substituição do projeto atual.
- Alterar TT após importar uma fresa da base pode deixar os parâmetros de
  usinagem aguardando revisão. Esse estado agora pode ser salvo e restaurado
  como rascunho, sem perder o formulário. Gerar continua recusando a
  incompatibilidade de perfil até ela ser resolvida.
- **Fechar a própria ferramenta continua sendo descarte explícito do
  formulário não salvo.** Caso exista rascunho salvo, a próxima abertura o
  recupera; caso contrário, abre os padrões. Gerar e alternar ferramentas
  continuam mantendo o formulário em memória.

## Implementação e limites

`CamTableHistory` armazena estados imutáveis dos dados das linhas, incluindo
parâmetros inválidos de texto que ainda precisam ser corrigidos. O editor de
diâmetro valida a tabela inteira antes de registrar a mudança como uma ação.
Carregar controles para outra seleção não cria passos intermediários.

`CamToolState` separa a captura para persistência da comparação leve de
rascunhos. A detecção de alterações não escreve WKT nem calcula SHA-256 da
geometria de origem/referência. A validação das assinaturas continua na
gravação/restauração do rascunho, sem alterar os algoritmos CAM.

A proteção/persistência de formulário entre sessões continua limitada ao
NCC. Isolation e Paint têm histórico local de tabela e retenção de sessão,
mas ainda precisam de codecs próprios para salvar seus formulários no projeto.
O histórico de Undo/Redo não é persistido em arquivo.

## Verificação

- Regressão vermelha confirmada em NCC e Isolation: editar para `0.198` e
  pressionar Ctrl+Z mantinha `0.198`, em vez do diâmetro anterior.
  Evidência: `target/cam-history-red.log`.
- Regressão vermelha do aviso: editar somente a margem NCC para um texto
  incompleto não marcava o projeto alterado (`target/cam-draft-red.log`).
- Integração: 74 casos aprovados, zero falhas/erros,
  `target/cam-history-integration.log`.
- Rodada focada final: 50 casos aprovados, zero falhas/erros,
  `target/cam-history-profile-green.log`. Inclui remoção múltipla, seleção,
  machining importado, precisão, novo ramo de edição, Delete/Undo de texto,
  Paint após restauração, rascunho inválido salvo/reaberto, edição durante
  salvar/carregar e correspondência entre Undo e estado salvo.
- A revisão encontrou uma recusa de restauração do NCC ao alterar TT após
  importar parâmetros. Regressão vermelha em `target/cam-profile-draft-red.log`;
  verde acima, comprovando também que Gerar continua bloqueado enquanto
  o perfil importado e TT não corresponderem.
- Catálogos PT/EN: 3.840 entradas por idioma, nenhuma pendente;
  `python tools/write_ui_catalogs.py --strict --check`.
- `mvnw.cmd verify` final aprovado: **2.279 registros, 2.261 aprovados,
  18 opcionais ignorados, zero falhas/erros**, em 4 min 52 s.
  Log: `target/cam-history-verify-final.log`; contagens por módulo em
  `target/cam-history-verification.json`. A primeira rodada completa também
  passou (`target/cam-history-verify.log`); a final inclui a correção e o
  teste da restauração de TT.

Testes programáticos com controles JavaFX e dados sintéticos. A avaliação
manual do usuário e uma nova distribuição MSI continuam etapas separadas.

## Próxima etapa

Portar a persistência dos formulários de Isolation e Paint, reaproveitando
o controle de alterações e a comparação leve, antes de ampliar para os outros
painéis principais. Depois, oferecer retomada das receitas dos resultados.
