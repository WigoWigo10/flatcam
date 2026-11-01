# Salvar e Salvar como no editor G-code — 2026-11-01

O painel do editor agora distingue as ações pedidas no relato do testador:

| Ação | Efeito |
| --- | --- |
| Salvar / Ctrl+S | Grava o texto no arquivo original indicado no painel. |
| Salvar, sem original disponível | Pede um destino; após sucesso, esse passa a ser o arquivo de Salvar. |
| Salvar como / Ctrl+Shift+S | Escolhe outro destino e grava uma cópia; mantém a associação original. |
| Aplicar ao CNC Job | Atualiza o texto e a prévia do objeto em memória, mantendo o contrato anterior. |
| Cancelar edição | Descarta o rascunho em memória; não desfaz arquivos já gravados. |

O caminho original aparece no painel. Quando o CNC Job veio embutido em um
projeto Python, o nome relativo como `job.nc` é apenas um placeholder: não é
tratado como autorização para sobrescrever um arquivo do diretório atual.
Só um arquivo existente, com caminho absoluto e extensão de código de máquina
reconhecida, é associado automaticamente ao abrir o editor. Caminhos de projeto
`.FlatPrj`/`.fcnproj` não são associados como arquivos de código.

Uma primeira gravação por Salvar associa o destino também ao CNC Job do
projeto, para persistir esse caminho quando o projeto for salvo. Não aplica
implicitamente o texto editado ao objeto. Salvar como é uma cópia, inclusive
quando não há original associado; para estabelecer um arquivo de trabalho,
use Salvar. A cópia recebe sugestão `nome_copy.ext` quando existe original.
Escolher o próprio original em Salvar como é recusado, inclusive aliases
identificados por `Files.isSameFile`; a mensagem orienta usar Salvar.

## Gravação e interface

- Ambos usam a revisão CNC existente antes da publicação. Salvar não abre
  FileChooser quando já tem destino; Salvar como sempre escolhe destino.
- Análise e gravação usam worker, progressão e cancelamento existentes. O texto
  é bloqueado enquanto salva para que o programa revisado seja o gravado.
- Origem/identidade e época do projeto são revalidadas antes da publicação.
  Recusa, cancelamento e invalidação antes de publicar preservam o destino.
- O arquivo é escrito em temporário no mesmo diretório e substituído por
  movimento atômico quando suportado, com o fallback já existente do exportador.
- Cancelamento/falha não troca a associação de arquivo nem remove o rascunho.
- Ícones claro/escuro usam o mecanismo comum. Tooltips formatados e guia
  PT-BR/EN explicam os dois botões, atalhos e a diferença para Aplicar.
- O editor Tcl já possuía Salvar e Salvar como e não foi alterado. Editores
  visuais Gerber/Excellon/Geometry continuam aplicando objetos ao projeto;
  este incremento trata os arquivos de código de máquina do editor G-code.

## Validação

`GCodeEditorControllerTest`: destinos original/cópia, associação somente após
sucesso, atalhos, bloqueio durante gravação, rascunho preservado após falha,
cancelamento e separação de Aplicar.

`MainReviewedExportTest`: host real com arquivos temporários, revisão antes do
destino, substituição do original, cópia preservando original, primeira
associação, recusa de cópia no mesmo arquivo e placeholders/projetos sem
associação automática. Testes existentes cobrem invalidação e recusa da revisão.
`CncReviewedFileTest` cobre texto exato, cancelamento e limpeza de temporários;
`ToolPanelIconsTest` verifica os recursos nos dois temas.

Rodada focada: **18 testes aprovados**, zero falhas/erros,
`target/gcode-save-focused2.log`. A primeira rodada focada registrou um teste
que ainda chamava por reflexão a assinatura removida; o teste foi migrado para
a entrada real com objeto e controller ativos.

Verify integral: **2.334 casos, 2.316 aprovados, 18 opcionais ignorados,
zero falhas/erros**, `target/gcode-save-verify.log`. Contagens em
`target/gcode-save-verification.json`. Catálogos PT-BR/EN: 3.859 chaves por
idioma, sem pendências. Os seletores nativos de arquivo e o diálogo de
revisão tiveram decisão/destino injetados nos ensaios, sem interação manual.
Nenhum projeto pessoal ou arquivo CNC do usuário foi modificado.

As políticas Z anteriores foram commitadas em `77bc29b8` antes desta alteração.
Este incremento não altera a versão nem gera instalador.

Reforço posterior: Cancelar edição recebeu tooltip próprio, em PT-BR/EN,
destacando que **não desfaz arquivos já gravados** por Salvar ou Salvar como.
Rodada focada após esse reforço: 19 aprovados, zero falhas/erros,
`target/gcode-cancel-tooltip.log`. A distribuição será atualizada separadamente.
