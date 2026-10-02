# Texto e Borracha no Editor Geometry

Disponíveis na barra e no menu **Geo Editor**. As alterações continuam sendo
rascunho até **Salvar e sair do editor**; Descartar não modifica o objeto.

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

Esc/botão direito cancela antes do segundo clique; durante o cálculo use o
cancelamento de trabalho na barra de status. Cancelamento/falha não aplica
resultado parcial. Para repetir, selecione o molde e acione Borracha novamente:
o FX encerra cada gesto, enquanto o Python mantém a ferramenta ativa.

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
Modos/atalhos avançados de arco e transformações interativas não reproduzem
integralmente os gestos Python. Texto/Borracha não tornam o editor 100% equivalente.

Testes de núcleo, controles, cliques no plot, cancelamento, undo/redo, 10.000 formas
não relacionadas e persistência nativa/legada são automatizados. A janela não foi
validada visualmente nem houve execução na máquina.
