# Revisão das janelas reais — 2026-10-27

Quarto item da série: conferir os incrementos de arrays e importação no
aplicativo real, corrigir achados e separar execução comprovada de pendências.

## O que foi conferido

Revisão no Windows deste computador, usando cópias do projeto real de 21
objetos. O SHA-256 do original e das duas cópias permaneceu idêntico ao manifesto
de preparação; nenhum projeto foi salvo. Preferências FX ficaram em memória;
o Python recebeu diretório de dados separado. O primeiro launcher ainda lia
QSettings do Registro: o construtor de duas strings ignorou o padrão INI nesse
PyQt. Isso foi detectado por probe de fileName; não há captura anterior que
permita afirmar que essas configurações Qt ficaram intactas na primeira rodada.
O launcher foi corrigido para arquivo INI explícito, com probe de leitura/escrita
aprovado, e a abertura do Python foi repetida. Isso não é uma auditoria de MRU
dos diálogos do Windows.

| Área | Evidência na janela real |
| --- | --- |
| Abrir projeto FX | Árvore e plot carregados; visibilidade original preservada. Avisos de opções Python não restauradas continuam explícitos no terminal. |
| Layout lateral | Coluna redimensionada; editor e formulário com rolagem acessíveis na largura disponível. Nomes longos usam a rolagem existente. |
| Novo array circular | NPTH com quatro furos, cinco novos na prévia sem selecionar formas; confirmar num ponto distante não deslocou o array da origem configurada. |
| Histórico | Ctrl+Z removeu o array inteiro, Ctrl+Y o restaurou. Cancelar editor descartou o rascunho; objeto voltou a mostrar quatro furos. |
| Temas | Branco gelo, Preto gelo, Preto e Branco aplicados pelo formulário real de Preferências; textos dos novos campos legíveis. |
| Importação | Inteiros IN/MM, decimais, LZ/TZ e política de unidades exibidos em Excellon > Importação. A matemática e precedência dos cabeçalhos estão em IMPORT_FORMATS.md; não foi feito novo ensaio de importação pelo seletor de arquivo nesta etapa. |
| Correções finais | Após reconstruir, ajuda nova exposta na barra e no diâmetro; contorno da prévia linear conferido no Branco gelo e no Preto, inclusive trocando tema com a prévia aberta. |
| Python original | FlatCAM 8.994 abriu a outra cópia; árvore preenchida e plot com contorno, furos e anotações visíveis. Log confirmou o fim do carregamento. |

## Correções decorrentes da revisão

- Ajuda da barra ainda descrevia somente replicação da seleção. Agora distingue
  LINEAR/CIRCULAR novos de GRID e explica confirmação, posição e histórico.
- Diâmetro agora explica seu uso nos arrays e a reutilização/criação de ferramenta.
- Contorno de referência era azul com apenas cerca de 22% de opacidade. Agora
  usa azul opaco adaptado ao fundo claro/escuro. Cores dos objetos permanecem
  definidas por seus estilos. Teste cobre contraste mínimo 3:1 nos quatro fundos.

## Limites e próximos ensaios

A ativação da primeira janela Qt falhou duas vezes com `failed to activate captured window`,
inclusive após renovar a seleção. Não é evidência de crash do FlatCAM. Essa
sessão foi encerrada pelos processos identificados pelo script e diretório
exclusivos da revisão. Após corrigir o isolamento QSettings, uma segunda sessão
abriu/renderizou a cópia e encerrou pela interface. O log confirmou a gravação
do estado; o INI apareceu em `python-settings/qt/Open%20Source/FlatCAM.ini`.
A segunda sessão não repetiu uma comparação interativa completa dos editores.

Não foram executados arrays com mouse no Qt, comparação visual de tesselação,
matriz completa de painéis antigos, múltiplas escalas DPI ou troca entre monitores.
Nenhuma configuração de tela do Windows foi alterada. Não há nova medição de FPS.

Para a próxima validação visual: comparar o mesmo array no Qt e FX, repetir em
escalas/monitores disponíveis e conferir os demais formulários estreitos. Os
comparadores numéricos de arrays e de zeros são evidência complementar, não
substituem esses ensaios.

## Build e reprodução

`mvnw.cmd verify`: **2026 registros, 2008 aprovações, 18 skips opcionais,
zero falhas/erros**, em `target/ui-review-final-verify.log`.
O build nativo e seu probe também passaram (`target/ui-review-native-build.log`).
A janela revisada usou MainApp real via javaw, com preferências isoladas;
o probe nativo não equivale a repetir toda a revisão pelo executável nativo.

Preparação e limites do isolamento: [tools/ui-review/README.md](tools/ui-review/README.md).
Manifesto, cópias, configurações e logs ficam em `target/ui-review`, ignorado pelo Git.
