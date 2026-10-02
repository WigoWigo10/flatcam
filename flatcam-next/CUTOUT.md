# Cutout: gaps, Thin e M-Bites

Cutout gera Geometry; a configuração e geração CNC são etapas posteriores.
Os valores usam as unidades do objeto de origem. Confira os caminhos, as alturas
e as profundidades antes de executar na máquina.

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

Free-form conserva o posicionamento anterior no FX, baseado na caixa do contorno
ampliado. Seu handler Python possui diferenças de referência; essa parte ainda
requer corpus específico, inclusive margem negativa, concavidades, Thin e
M-Bites. Esta entrega não declara paridade completa de Cutout nem validação
física da CNC.
