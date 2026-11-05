# Cutout: gaps, Thin e M-Bites

Cutout gera Geometry; a configuração e geração CNC são etapas posteriores.
Os valores usam as unidades do objeto de origem. Confira os caminhos, as alturas
e as profundidades antes de executar na máquina.

## Somente recortes internos — 2026-11-05

Na **Cutout Tool**, abra **Recortes internos…**. Use a área preenchida do
contorno físico, convertida do Outline/Edge_Cuts (veja
[OUTLINE_TO_AREA.md](OUTLINE_TO_AREA.md)). O painel lista as aberturas existentes,
sem selecionar nenhuma automaticamente. Escolha na tabela ou use **Escolher no
Plot** para alternar aberturas pelo clique. Também é possível desenhar:

- **Retângulo:** dois cantos opostos.
- **Círculo:** centro e um ponto do raio.
- **Polígono:** vértices por clique e botão direito para concluir.

Esc ou **Encerrar escolha/desenho** encerra a interação. Na escolha pelo Plot,
botão direito também encerra. A seleção já confirmada permanece; um hit ainda
aguardando publicação é descartado. **Remover desenhadas** ou Delete na tabela
remove apenas os desenhos selecionados do rascunho; aberturas detectadas e o
objeto original não são apagados.

Defina diâmetro, margem, Cut Z e passadas. A prévia é calculada em worker e mostra
o caminho compensado para dentro em `margem + diâmetro / 2`. Diâmetro usa a
precisão digitada; a formatação da tabela não arredonda o cálculo. Margem deve
ser não negativa. O perfil e os parâmetros de usinagem do Cutout são transferidos.

### Prévia e revisão — refinamento UX de 2026-11-05

A prévia distingue **abertura desejada** (tracejado turquesa), **centro da fresa**
(linha contínua) e **faixa removida** (preenchimento laranja translúcido). A faixa
é o buffer do trajeto pelo raio informado; o restante do miolo não aparece como
desbastado. Cálculo do caminho e dessa faixa continuam em worker. A legenda e o
contraste acompanham os quatro temas, inclusive ao trocar de tema com o painel
aberto. São geometrias temporárias, sem alterar os objetos ou o G-code.

**Enquadrar seleção** aproxima as aberturas escolhidas. A seleção é compartilhada
entre tabela e Plot. Um erro geométrico destaca a abertura no Plot e marca sua
linha com `!`, borda vermelha e mensagem com o ID da tabela, sem depender só de cor.
Campos numéricos inválidos recebem borda e mensagem. **Gerar** permanece
desabilitado enquanto a prévia está pendente, há erro, não existe seleção ou um
gesto de escolha/desenho está ativo. Alterar diâmetro, margem ou seleção invalida
imediatamente o resultado anterior antes de iniciar o debounce.

O resumo mostra quantidade selecionada, diâmetro, margem, Cut Z após a política
Standard/Machinist e passo do Multi-Depth ou passada única. O comprimento mostrado
é **por passada**, sem rápidos, mergulhos ou estimativa de tempo. A prévia é plana:
não é uma simulação de profundidade, colisões ou perfil 3D da ferramenta.

Tooltips específicos de diâmetro, margem, legenda, enquadramento e resumo usam
parágrafos e ênfase/cor semântica. A margem tem exemplo de abertura de 10 mm com
0,2 mm de material por lado; as instruções essenciais continuam visíveis. As
mensagens de erro sem conteúdo não ocupam espaço na coluna.

**Gerar recortes internos** cria uma Geometry `_internal_cutout`, contendo
somente os contornos escolhidos. Não gera o perímetro externo, pontes, Thin ou
M-Bites. O painel continua aberto, seguindo a política das ferramentas CAM.
Abra a Geometry para gerar CNC, revisar e exportar pelo fluxo normal. O guia
offline da Cutout explica as duas alternativas.

A geração inteira é recusada se uma abertura tocar/cruzar o perímetro externo,
se sobrepor a outra selecionada ou a uma abertura existente diferente, ou ficar
vazia/dividida após a compensação. Não há saída parcial silenciosa. Origem
removida/alterada, troca de projeto e cancelamento invalidam a geração pendente.
Fechar o painel encerra gestos e prévias, seguindo a política CAM existente para
uma geração já solicitada.

É perfilamento fechado: **não esvazia um bolso**, não corrige cantos inacessíveis
e o miolo pode se soltar. Confira a fixação e a ordem CNC. O rascunho de desenhos
e seleção existe enquanto o painel está aberto; a Geometry gerada, ferramentas e
parâmetros são preservados ao salvar/reabrir `.fcnproj` ou `.FlatPrj`.

Testes cobrem MM/IN, seleção parcial em painel, formas desenhadas, compensação,
geometria inviável, cancelamento, gestos no Plot, Delete, quatro temas e o fluxo
MainWindow → Geometry → CNC/G-code → salvar/reabrir. Isso não substitui ensaio
físico na máquina. A opção antiga **Incluir recortes internos** continua no
fluxo externo e é descrita abaixo.

## Pontes pelo cursor — 2026-10-24

Use **Posicionar gaps (Free-form)** ou **Posicionar gaps (Rectangular)**.
O FX prepara o contorno externo compensado em segundo plano e o mostra no Plot
Area. A área quadrada na ponta do cursor tem lado `Gap size + Tool Dia`, como
`ToolCutOut.cutting_geo` do Python. O clique aplica exatamente essa área, com
o Snap da grade ativo ou desligado conforme a barra inferior.

Clique sobre o contorno e aguarde a prévia atualizar antes do próximo clique.
É possível adicionar várias pontes sem rearmar a ferramenta. Cliques fora do
contorno, inteiramente dentro de gaps anteriores ou que eliminariam todo o
caminho externo são recusados com uma mensagem. **Botão direito ou Esc** encerra
o gesto e mantém as pontes confirmadas; um cálculo ainda pendente é cancelado.
**Gerar** publica o resultado no projeto e exige concluir um cálculo pendente.
Não há alteração de objetos do projeto durante a prévia.

Os gaps manuais substituem o padrão automático e podem ser combinados com as
máscaras por retângulo/polígono existentes. Gere com o mesmo tipo de contorno
usado pelo cursor; uma troca Free-form/Rectangular exige limpar os gaps primeiro.
Alterar diâmetro, margem, largura, Convex Shape, Single/Panel ou recortes internos
cancela a interação e limpa somente os gaps do cursor; máscaras desenhadas
continuam nas suas coordenadas. **Limpar gaps manuais** remove ambos.

A prévia é do caminho externo e das máscaras. Thin e furos M-Bites são criados
pela geração existente e devem ser conferidos depois de gerar. Os recortes
internos continuam fechados e não recebem pontes. Uma máscara pode alcançar
mais de um trecho próximo: confira os contornos de painéis com espaçamento
pequeno. Nas diagonais e cantos, a ponte resultante pode diferir da largura
nominal. Não há rotação automática: a máscara da prévia e a aplicada têm sempre
a mesma orientação. O Python pode girar só a prévia durante o movimento; esse
comportamento não foi copiado.

Testes automatizados cobrem cliques repetidos, conclusão por botão direito,
Snap ligado/desligado, cancelamento usado pelo Esc, misses/duplicatas,
mudanças de parâmetros, ferramenta fechada/origem alterada antes da publicação,
largura MM/IN e preservação de recortes internos e de outra placa do painel.
A avaliação visual com mouse na janela real permanece para o usuário; isso
não declara equivalência integral dos gestos Python nem validação física.

## Recortes internos fechados (FX, 2026-10-10)

Marque **Incluir recortes internos** para acrescentar os anéis internos da
área da placa ao Cutout Free-form. Desmarcado por padrão: as chamadas antigas
e a Tools Database continuam gerando somente o perímetro externo.

Use uma **Geometry preenchida do contorno físico**, convertida a partir do
Edge_Cuts antes **ou depois** de panelizar (veja [OUTLINE_TO_AREA.md](OUTLINE_TO_AREA.md)).
Não use ilhas de cobre: todos os anéis internos
da origem serão interpretados como aberturas a recortar. Se houver várias
placas/componentes, use **Panel**. Cada abertura recebe compensação para
dentro de `margem + raio da fresa`, preservando o posicionamento das cópias.

Exige Free-form, Convex Shape desligado e margem não negativa. Rectangular é
bloqueado enquanto a opção está marcada. Se uma abertura desaparecer ou se
dividir após a compensação, a geração inteira falha: não omite esse recorte
nem publica caminhos parciais. Reduza fresa/margem ou usine separadamente.

Os caminhos internos são **fechados, sem pontes**, inclusive quando há gaps
manuais. Thin e M-Bites continuam exclusivamente no perímetro externo. Trata-se
de perfilamento, não desbaste de bolso nem compensação de cantos inacessíveis.
Revise a fixação, peças que podem se soltar e a sequência CNC na prévia.

Extensão opt-in do FX, não equivalência literal ao Cutout exterior do Python.
Testes MM/IN cobrem compensação, cópias, gaps manuais, Thin/M-Bites externos,
recusa de aberturas inviáveis e publicação -> CNC/export -> salvar/reabrir.
O projeto privado real tem zero anéis internos; não valida este caso sozinho.

## Posicionamento automático retangular

O centro dos gaps é o centro da caixa **original** da origem mais a margem
em X e Y, como no handler `cutout_rect_handler` do Python. Os padrões duplos
usam espaçamento `(dimensão original + 2 × margem) / 4`; o raio da fresa não
participa desse espaçamento. Panel calcula essas posições para cada parte.

- LR/TB: duas pontes.
- FOUR: quatro pontes.
- TWO_LR/TWO_TB: quatro pontes em duas posições por lado.
- EIGHT: oito pontes.
- NONE: contorno sem pontes automáticas.

O caminho externo compensa margem + raio da fresa. O trecho retirado do caminho
tem largura de gap + diâmetro da fresa; a ponte de material é mais estreita que
esse trecho. **Gap size zero desativa os gaps automáticos no FX**; o handler
legado ainda pode recortar pelo diâmetro nessa situação. Não é paridade literal
nesse caso limite.

As bandas sempre atravessam o contorno ampliado. O Python pode perder os gaps
quando sua banda baseada na origem não alcança esse contorno com margem alta;
o FX não reproduz esse resultado. Se a posição/largura fizer desaparecer uma
ponte solicitada ou consumir todo o caminho, a geração retangular é recusada.
Reduza margem/largura ou desenhe gaps manuais. Isso verifica interrupções do
caminho, não resistência física das pontes ou fixação adequada da placa.

## Thin, M-Bites e gaps manuais

Thin extrai os trechos do contorno que estão dentro das máscaras dos gaps.
Não deriva esses trechos pela diferença entre contornos já recortados: pequenos
erros de noding nos cantos arredondados podem produzir segmentos indevidos.
A Geometry Thin recebe a profundidade rasa configurada; seu CNC Job é separado.

M-Bites usa, no retangular, as mesmas posições dos gaps, mas uma linha externa
compensada pela margem + raio da **broca**. O espaçamento dos centros é diâmetro
da broca + espaçamento configurado. A faixa de furos tem a largura nominal do
gap, sem somar o diâmetro da fresa. Todos os gaps automáticos solicitados devem
interromper essa linha também; uma configuração inviável gera erro, sem publicar
objetos parciais. O Excellon precisa de configuração própria de furação.

Máscaras manuais substituem o padrão automático em Bridge/Thin/M-Bites. Seu
conteúdo e posicionamento não são deslocados pela margem. A validação da quantidade
automática de pontes não se aplica a essas máscaras; confira a saída no plot.

## Verificação e limites

Testes sintéticos cobrem posição, quartos, margem alta, Thin, furos nos quatro
lados, padrões inviáveis e valores não finitos. Há teste de controles/tooltips
na thread JavaFX, sem teste manual de todos os cliques. A comparação headless
com o projeto real autorizado está descrita em [COMPARACAO_CAM.md](COMPARACAO_CAM.md).

Free-form, margem negativa (2026-10-03): corrigido um erro onde o deslocamento
do caminho sempre somava o raio da fresa (ou, em M-Bites, o raio da broca),
mesmo com margem negativa. O `cutout_handler` do Python soma o raio só quando
a margem é `>= 0`; com margem negativa ele **subtrai** o raio, aprofundando a
erosão (cortar para dentro do contorno original). A versão anterior do FX
somava sempre, ficando um diâmetro de fresa inteiro mais raso que o Python
nesse caso. Corrigido em `CutoutGenerator.signedOffset`, usado tanto no
contorno Free-form quanto em M-Bites Free-form. Veja
`CutoutGeneratorTest.negativeMarginSubtractsTheToolRadiusLikeCutoutHandlerDoes`
e `...mouseBitesWithNegativeMarginSubtractsTheDrillRadiusTooLikePython`.

Fora disso, Free-form ainda conserva o posicionamento anterior no FX, baseado
na caixa do contorno ampliado - que corresponde ao `recursive_bounds` do
Python sobre o contorno já expandido, não à caixa original do objeto. Objetos
com múltiplas partes desconexas (Gerber Multi-Polygon) em modo `SINGLE` usam a
caixa delimitadora do conjunto, como o Python faz (`object_geo = box(...)`
quando o resultado é `MultiPolygon`); em modo `PANEL`, cada parte recebe seu
próprio contorno. Concavidades, Thin e M-Bites com formas não retangulares
ainda não têm comparação headless com um projeto real; esta entrega não
declara paridade completa de Cutout nem validação física da CNC.
