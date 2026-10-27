# Arcos, Texto e Borracha no Editor Geometry

## Jump geral no editor e rotação por arrasto — 2026-10-27

**J**, fora de campos de texto, agora abre coordenadas em todo o Editor Geometry:
seleção, círculo, retângulo, caminho/polígono, mover/copiar e prévias. O comando
da barra/menu usa o mesmo fluxo. X/Y separados aceitam vírgula decimal; relativo
usa o cursor capturado ao abrir. A escolha abs/rel é compartilhada com o Jump
fora do editor e salva somente ao confirmar. Posicionar centraliza mantendo
zoom, respeita Snap e atualiza a prévia sem inserir âncoras nem confirmar formas.
Adicionar ponto exato continua exclusivo do arco. Respostas antigas são ignoradas
se gesto, rascunho, seleção ou sessão mudou; durante cálculo o comando aguarda.

Em **Transformações > Rotação > Girar por arrasto**, use o pivô do formulário,
pressione longe dele e arraste ao redor. O ângulo atravessa o eixo negativo
sem saltar 360°; positivo é horário. Pontos do mouse respeitam Snap. Durante o
arrasto gira apenas a caixa da seleção: custo independente dos vértices selecionados.
Ao soltar, um pedido calcula a geometria exata no worker e mostra a prévia fixa.
Só **Confirmar transformação** ou outro clique aplica, em uma transação de
undo/redo, conservando ferramentas/IDs/seleção. Clique no pivô, ângulo zero e
volta completa não alteram o rascunho. Esc/botão direito, troca de seleção ou
parâmetros descartam o gesto; Esc durante o worker impede publicação atrasada.

Também corrigida a seleção da tabela ao cancelar prévias: callbacks de cancelamento
não podem restaurar a linha anterior enquanto a nova seleção está sendo aplicada.
São testes automatizados de controlador, eventos e matemática; não houve nova
validação física do mouse/DPI. O arrasto com caixa e confirmação é uma escolha
de UX do FX, não uma alegação de gesto idêntico no Qt.

Validação integral final: **2014 aprovações, 18 skips opcionais, zero falhas/erros**
(2032 registros), `target/geometry-gestures-final-verify.log`.

Disponíveis na barra e no menu **Geo Editor**. As alterações continuam sendo
rascunho até **Salvar e sair do editor**; Descartar não modifica o objeto.

## Arcos: modos e prévia — 2026-10-24

O botão **Arco** abre a seção **Arcos e curvas**. Selecione a ordem dos três cliques:

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

O padrão continua **64 segmentos por círculo**, agora configurável (ver abaixo).
Coordenadas não finitas, raio zero, extremos repetidos nos modos 12C/132 e ponto
colinear em 132 são recusados. O cálculo usa até 4097 vértices e não executa
operações sobre a geometria densa do projeto na prévia.

`EditorArcTest` compara todos os vértices de **40 controles sintéticos públicos
MM/IN** com `FCArc.make()`/`camlib.arc()` originais (tolerância 1e-11 nas unidades
do controle). Há também testes de pontos inválidos, deslocamentos grandes,
histórico, Milling Type e eventos de clique/teclas no controlador. Isso não
valida os gestos numa sessão Qt real nem a janela FX com mouse físico.
Mais 18 controles MM/IN comparam as resoluções 8, 65 e 256 com a matemática
Python original, sem mudar a tolerância.

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

### Coordenadas pelo J — 2026-10-24

Durante o desenho do arco, **J** abre X/Y nas unidades do objeto. Use referência
absoluta ou marque **Relativas ao cursor atual** para somar deslocamentos ao
cursor capturado na abertura. Alternar a referência reinicia os campos.
Aceita ponto/vírgula decimal, mas não expressões ou separadores de milhares.

- **Posicionar cursor** centraliza a vista, mantém o zoom e mostra a prévia,
  sem adicionar um ponto. Respeita o Snap ativo. Na aplicação, o cursor físico
  também é levado à posição. Confirme depois por clique.
- **Adicionar ponto** usa as coordenadas exatas como o próximo ponto, sem
  arredondar à grade. Pode fornecer os três pontos por J; o terceiro válido
  conclui. Essa ação explícita é uma extensão do FX ao Jump Python.
- **Cancelar/Esc no diálogo** mantém os pontos e a vista anteriores. Esc no
  plot continua cancelando o arco inteiro. Trocar modo/gesto invalida uma
  resposta de diálogo anterior.

Se o sistema não permitir mover o cursor físico, a prévia fica posicionada e
o terminal informa para usar **J > Adicionar ponto**. Não há leitura/execução
de expressões da área de transferência. A referência absoluta/relativa agora é compartilhada com o Jump geral e lembrada
após confirmar; cancelar não a altera. No Editor Geometry, J também funciona
sem arco ativo, conforme a atualização abaixo. Outros editores não estão incluídos
nesse novo gesto.

### Resolução de novas curvas — 2026-10-24

**Segmentos/volta** em Arcos e curvas ajusta os próximos arcos e círculos de
**4 a 4096**, com padrão 64. A prévia usa a mesma segmentação da forma final.
Arcos podem atualizar a prévia sem perder pontos ao mudar o valor; mudar o
valor durante um círculo cancela sua prévia para recomeçar com a nova resolução.
Formas existentes não são reaproximadas. Como `FCCircle` Python, círculos usam
`4 × int(valor/4)` segmentos: 65 gera 64. Arcos usam o valor nominal por volta.

**Salvar resolução padrão** persiste somente esse valor para editores abertos
depois. O mesmo ajuste aparece em **Preferências > Padrões das ferramentas >
Editor Geometry**, com validação e exportação/importação dos padrões. É uma
configuração do FX independente de mm/in; não importa automaticamente a
preferência `geometry_circle_steps` de um `.FlatPrj`. O limite 4096 mantém o
trabalho da prévia limitado e pode ser revisto; não é equivalência de toda a
faixa de preferências Python.

Testes de diálogo/controlador são feitos sem mover o mouse da área de trabalho:
cobrem entradas absolutas/relativas, valores inválidos, preview sem inserção,
inserção exata com Snap ativo, respostas atrasadas, resolução, padrão salvo e
reinício do círculo. O movimento físico e a apresentação em monitores/DPI
ainda precisam da avaliação manual do usuário.

## Transformações com pivô e prévia — atualização 2026-10-27

Abra **Transformações das selecionadas** pela barra ou **Geo Editor >
Transformações**. Escolha a operação e selecione as formas. Na rotação, informe o ângulo: **positivo gira no
sentido horário**, como `on_rotate_action` Python; negativo é anti-horário.
O campo substitui a aplicação imediata anterior do botão Girar no FX.

Escolha o pivô na lista: origem `(0,0)`, centro da caixa da seleção, um dos
quatro cantos dessa caixa ou ponto absoluto X/Y nas unidades do objeto.
**Escolher pivô no plot** recebe um clique com o Snap atual e calcula a prévia
em seguida, usando os parâmetros já informados. **Pré-visualizar transformação** calcula
com a referência do formulário.

A prévia desenha o contorno transformado e uma cruz no pivô. O rascunho fica
intacto até **Confirmar transformação** ou um clique no plot; o lugar desse clique
não desloca a prévia. Esc/botão direito/Cancelar prévia descartam sem alterar
o rascunho. Mudar ângulo, pivô, seleção ou ferramenta invalida uma prévia antiga.
Durante o cálculo, Esc descarta a publicação pendente; Cancelar na barra de
status solicita interrupção do trabalho. Uma falha não publica resultado parcial.

Depois de confirmar, Ctrl+Z/Ctrl+Y desfaz/refaz a transformação inteira. Conserva os
IDs, a ordem de seleção, a associação às ferramentas e o perfil delas; formas
não selecionadas permanecem como estavam. O objeto do projeto só muda ao
Salvar e sair do editor. Ângulo zero/voltas completas e coordenadas não finitas
são recusados, sem histórico novo.

O cálculo das coordenadas usa worker e verifica cancelamento durante a travessia
de uma forma densa. A prévia usa o overlay indexado do plot; só a pequena cruz
do pivô usa o desenho imediato de posicionamento. Não houve medição de ganho
de FPS nesta etapa. Testes MM/IN, Z preservado, ferramenta/IDs/histórico, overflow,
cancelamento em uma forma de 20.000 vértices, cliques e referências são automatizados.
Ainda falta avaliar visualmente na janela real.

Além do ângulo digitado, existe escolha angular por arrasto (atualização abaixo).
Atalhos globais de transformação continuam pendentes. Escala X/Y, espelhar X ou Y e inclinação
agora compartilham o pivô e o fluxo de prévia. Deslocamento X/Y também tem prévia,
mas soma os valores nas unidades do objeto, independentemente do pivô.
A API antiga `rotateSelected` continua anti-horária para os chamadores existentes;
o novo pedido de rotação do painel é explicitamente horário, como o Python.

### Escala, espelho, inclinação e deslocamento

- Escala X/Y independente: 1 mantém o tamanho; negativos também espelham;
  fatores zero são recusados. O centro/canto/ponto escolhido fica fixo.
- Espelhar X inverte esquerda/direita; espelhar Y inverte cima/baixo.
- Inclinação X/Y usa graus entre -90 e 90, sem os extremos; eixos são
  inclinados simultaneamente, como a matriz de skew do Python. Combinações
  degeneradas (como 45/45) são recusadas para não achatar a geometria.
- Deslocamento X/Y soma distâncias mm/in. A referência não altera esse delta.

Cada operação calcula em worker com cancelamento por vértices, mostra o contorno
sem apagar o rascunho e só publica após confirmar. Trocar tipo, parâmetros,
seleção ou ferramenta invalida a prévia, inclusive um resultado ainda em cálculo.
Mantém Z/M, buracos, ferramentas, IDs e seleção. Valores não finitos e overflow
são recusados sem aplicação parcial. Sem escolha angular por arrasto.

Validação focada: 31 testes CAM e 30 UI passaram, depois 25 UI com o novo teste
de largura de 360 px nos quatro temas. Logs `target/editor-affine-tests.log` e
`target/editor-affine-layout.log`; capturas opt-in
`flatcam-fx/target/editor-transforms-<tema>.png`. Contagens são rodadas sobrepostas.
Sem medição de FPS nova ou afirmação de paridade visual Qt integral.

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
Arcos agora têm os três modos, M/D/Backspace, J e resolução configurável.
Jump fora do arco e referência absoluta/relativa persistida foram ligados ao
Editor Geometry, e a rotação ganhou arrasto angular. A comparação interativa
integral dos gestos Python continua pendente.
Estas entregas não tornam o editor 100% equivalente.

Testes de núcleo, controles, cliques no plot, cancelamento, undo/redo, 10.000 formas
não relacionadas e persistência nativa/legada são automatizados. A janela não foi
validada visualmente nem houve execução na máquina.


Fechamento da série em 2026-10-27: última rodada de transformações/ajuda/tooltip
aprovou 37 testes (8 CAM, 29 UI) e empacotou com sucesso, incluindo o modo offset
ignorando/ocultando pivô. `target/editor-affine-final.log`. Integral da série
registrou 2006 casos: 1987 aprovados, 18 skips e somente a falha End Z conhecida
de CncNativePrecisionTest; log `target/parity-preferences-series-verify.log`.
