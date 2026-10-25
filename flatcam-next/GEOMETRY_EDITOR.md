# Texto e Borracha no Editor Geometry

Disponíveis na barra e no menu **Geo Editor**. As alterações continuam sendo
rascunho até **Salvar e sair do editor**; Descartar não modifica o objeto.

## Arcos: modos e prévia — 2026-10-24

O botão **Arco** abre a seção **Arcos**. Selecione a ordem dos três cliques:

- **C12 — Centro → Início → Fim:** o raio vem do centro/início; o último
  clique fornece a direção angular do fim, projetado nesse raio. Repetir
  a direção do início produz um círculo completo, como no Python.
- **12C — Início → Fim → Centro:** o centro é calculado na mediatriz dos
  extremos. Como no Python, a distância do último clique ao meio da corda
  determina seu afastamento; não é simplesmente o ponto clicado nem sua
  projeção perpendicular. A prévia permite conferir essa interpretação.
- **132 — Início → Fim → Ponto no arco:** o último ponto define a curvatura
  e o sentido é automático. Os dois primeiros cliques são os extremos.

**M** alterna o modo e reinicia os pontos, como no Python. **D** alterna CW/CCW
nos modos com centro; em 132 o sentido permanece automático. **Backspace**
remove o último ponto. O **terceiro clique válido** conclui sem precisar de
Enter; Enter com pontos insuficientes só informa que faltam cliques.
**Esc/botão direito** cancela sem inserir. Trocar seleção/ferramenta cancela
o arco em andamento; os controles preservam o modo escolhido para o próximo.

Com dois pontos, a prévia mostra a curva e uma cruz no centro. Se a combinação
for inválida, mostra somente linhas de referência. O terceiro ponto inválido
é recusado e os dois anteriores permanecem para nova tentativa ou Backspace.
Snap ligado/desligado vale para todos os cliques. Cada arco é uma transação
de Ctrl+Z/Ctrl+Y, conservando a associação da ferramenta. Milling Type Climb
inverte a ordem final dos vértices, assim como o handler global do Python.

São usados **64 segmentos por círculo**, como o padrão anterior do FX e os
controles Python desta etapa; a preferência Python de resolução variável ainda
não foi portada. Coordenadas não finitas, raio zero, extremos repetidos nos
modos 12C/132 e ponto colinear em 132 são recusados. O cálculo usa até 65
vértices e não executa operações sobre a geometria densa do projeto na prévia.

`EditorArcTest` compara todos os vértices de **40 controles sintéticos públicos
MM/IN** com `FCArc.make()`/`camlib.arc()` originais (tolerância 1e-11 nas unidades
do controle). Há também testes de pontos inválidos, deslocamentos grandes,
histórico, Milling Type e eventos de clique/teclas no controlador. Isso não
valida os gestos numa sessão Qt real nem a janela FX com mouse físico.
O atalho **J** para abrir o diálogo de coordenadas continua pendente.

**Diferença deliberada em 132:** no controle início `(5,0)`, fim `(0,5)` e
ponto no arco `(-5,0)`, o `FCArc.make` Python recebe `T[0] = -0.0` e escolhe
CCW, gerando o arco curto que não passa pelo terceiro ponto. O FX escolhe CW
e passa pelo ponto indicado. Esse caso está reproduzido pelo exportador e no
teste, separado dos 40 controles equivalentes, e não conta como paridade literal.

Para regenerar os controles na raiz `flatcam-next` (requer NumPy):

```powershell
python tools/compare_editor_arcs.py --output flatcam-cam/src/test/resources/org/flatcam/cam/geometry/python-editor-arcs.json
```

O exportador extrai as funções originais por AST; substitui só os invólucros de
coordenadas/forma e as notificações da UI, sem iniciar Qt, ler projetos ou
preferências. Os hashes das fontes ficam no JSON. A amostragem em frações
exatas de círculo é estabilizada contra um segmento extra por arredondamento
do solucionador; não há arredondamento das coordenadas de entrada.

## Texto

Clique **Texto**, escolha fonte instalada, tamanho, Negrito/Itálico e conteúdo.
**Gerar e posicionar texto** calcula contornos em segundo plano, mostra a prévia
e espera um clique no plot. A origem é a linha de base da primeira linha; novas
linhas descem. Esc/botão direito cancela, preservando o conteúdo do formulário.
Alterar os parâmetros, seleção ou ferramenta cancela a prévia antiga.

O resultado contém polígonos editáveis, não imagem nem objeto de texto. Preserva
vazios das letras, recebe a ferramenta escolhida e o sentido de fresagem atual.
Ctrl+Z remove a inserção inteira; Ctrl+Y restaura. A geometria salva não depende
mais da instalação da fonte. Pode ser usada em Geometry/CNC ou Paint.

São usados contornos Java/AWT com curvas aproximadas, não o parser FreeType
Python. MM/IN usam os fatores de escala de ParseFont.py, mas contornos, kerning
e métricas podem diferir. Confira as dimensões. Fontes ausentes/caracteres não
suportados são recusados. Limites: 512 caracteres, tamanho 0.1–1000 e 100.000
pontos. Não há texto rico por trecho nem ajuste posterior como texto.

## Borracha

Selecione uma ou mais formas e clique **Borracha**. O molde é calculado em
segundo plano. Clique na origem e depois no destino: a região deslocada apaga
as partes que intercepta, sem mover o molde original. Como no Python, anéis
fechados e exteriores dos polígonos viram regiões preenchidas (vazios internos
não protegem a região apagada); linhas abertas usam espessura mínima de 1e-7
na unidade do projeto. Para área larga, use um retângulo/polígono/círculo.

Todos os elementos atingidos são recortados, inclusive de outras ferramentas.
A associação à ferramenta e o perfil permanecem. Se a região cobrir também o
molde original, ele é apagado como qualquer outra forma. Apagar tudo é permitido.
Uma operação é um passo de undo/redo. Sem alteração não é criado histórico.

### Destinos sucessivos — 2026-10-24

A Borracha permanece ativa depois de cada operação, como no gesto do Python.
Selecione o molde, acione Borracha e clique **uma vez na origem de referência**.
Cada clique seguinte escolhe outro destino; aguarde o cálculo terminar entre
os destinos. A prévia mostra a região deslocada e o botão recebe uma borda de
destaque enquanto o modo está ativo. Nenhum destino é aplicado só por mover o mouse.

O FX conserva uma cópia do molde inicial e a mesma origem, mesmo se a própria
forma usada como molde for apagada. O Python recalcula o molde a partir da
seleção viva, que pode ter sido recortada; essa alteração silenciosa não foi
copiada. Para usar outro molde, encerre e selecione novamente as formas.

Entre destinos, **Esc, botão direito ou clicar novamente em Borracha** encerra
sem desfazer operações confirmadas. Trocar ferramenta ou seleção também encerra.
**Ctrl+Z/Ctrl+Y** desfaz/refaz uma operação e mantém o molde e a origem ativos,
inclusive quando a seleção ficou vazia após um recorte. Cliques sem alteração
não acrescentam histórico.

Durante o cálculo, **Esc descarta o resultado pendente** e impede reativação
atrasada; o worker continua ocupado até devolver o resultado. Para interromper
o trabalho quando possível, use **Cancelar** na barra de status. Falha ou
cancelamento não publica resultado parcial nem remove operações anteriores.
Aplicar/Descartar continuam se referindo ao rascunho inteiro do editor.

Testes de interface com eventos de mouse cobrem destinos sucessivos, undo/redo,
misses, exclusão do molde de origem, fim por botão direito/botão, cancelamento
durante preparação/recorte, falha e troca de seleção/ferramenta. Não houve
validação manual na janela nem execução na máquina.

## Desempenho e limites de paridade

Contornos de texto, união do molde e recorte são trabalhos canceláveis fora da
thread JavaFX. O recorte filtra envelopes antes das operações geométricas;
formas não atingidas conservam IDs. Uma primitiva JTS em andamento não pode ser
interrompida imediatamente. Histórico e reconstrução da tabela/índice permanecem
em memória; placas enormes ainda exigem perfilamento.

Paint Shape acrescenta caminhos aos polígonos/anéis fechados selecionados, sem
substituir contornos. Standard/Seed/Lines/Combo, margem, sobreposição, Connect e
Contour; cálculo cancelável em segundo plano, prévia fixa e confirmação por clique
ou botão. Esc/botão direito cancela; Ctrl+Z desfaz a inserção inteira. Ferramentas
associadas conservam o diâmetro (selecione uma por vez); sem associação, confira
o diâmetro ao gerar CNC. Resultados vazios/incompletos são recusados atomicamente.
Arcos agora têm os três modos e M/D/Backspace; J, resolução configurável e
transformações interativas ainda não reproduzem integralmente os gestos Python.
Estas entregas não tornam o editor 100% equivalente.

Testes de núcleo, controles, cliques no plot, cancelamento, undo/redo, 10.000 formas
não relacionadas e persistência nativa/legada são automatizados. A janela não foi
validada visualmente nem houve execução na máquina.
