# Contorno → Área: placas e painéis

Em **Editar > Converter > Contorno → Área**, selecione um Gerber de contorno
(por exemplo, Edge_Cuts) ou uma Geometry de linhas. É criada uma nova Geometry
preenchida `<nome>_area`; o objeto original não é alterado.

## Antes ou depois de panelizar

As duas ordens são suportadas:

1. Converter o contorno original para área e panelizar essa área junto com
   cobre, Excellons e contorno, usando a mesma referência/layout.
2. Panelizar cobre, Excellons e contorno primeiro; selecionar o **contorno
   panelizado** e convertê-lo para área. Todas as regiões separadas são
   mantidas, em vez de escolher somente a maior placa.

No NCC, use essa área como **Reference Geometry** se quiser limitar a limpeza
às placas, sem limpar os espaços entre cópias nem os recortes internos.
No Cutout, use a área como origem, **Panel**, Free-form, Convex Shape desligado.
Para usinar aberturas internas, marque **Incluir recortes internos** e confira
a compatibilidade da fresa/margem, conforme [CUTOUT.md](CUTOUT.md).

## Como os contornos são interpretados

- Contornos separados preservam todas as áreas, inclusive placas menores.
- Anéis aninhados alternam material, abertura, ilha e abertura da ilha.
  Não depende da orientação dos anéis, da ordem de entrada ou do tamanho
  relativo da área preenchida e da abertura.
- Faces adjacentes são unidas. Placas encostadas por uma borda tornam-se uma
  área contínua: a conversão não inventa espaçamento ou uma linha de corte
  entre elas. Para cortar cada placa separadamente, planeje separação suficiente
  para a fresa e os gaps.
- Linhas duplicadas são dissolvidas e interseções são nodadas antes de
  reconhecer as regiões. Uma grade numérica fina fecha apenas diferenças de
  arredondamento; não é uma ferramenta para fechar lacunas reais.
- Trechos abertos/pendurados, arestas sem face ou anéis inválidos detectados
  recusam a conversão **do objeto inteiro**, sem publicar uma placa parcial.
  Seleções de outros objetos válidos ainda podem ser convertidas.

Gerbers usam a `followGeometry` quando disponível, evitando interpretar a
largura da aperture como dimensão da placa. Não use cobre ou trajetórias de
usinagem como substitutos do contorno físico. Reveja a saída antes de gerar CNC.

## Interface e validação

Cálculo em worker, fora da thread JavaFX, com cancelamento cooperativo.
Cancelamento, renomeação/remoção/substituição da origem ou troca do projeto
impedem publicação de resultados antigos. O console informa quantidade de
áreas e recortes internos preservados; nenhuma placa é descartada por tamanho.
A prévia de panelização também usa a conversão multipartes.

`OutlineToAreaTest`: MM/IN, seis placas e doze aberturas, ilhas aninhadas,
abertura maior que a área material, bordas comuns, duplicatas, trechos abertos,
coordenadas inválidas e cancelamento. `MainOutlineConversionTest`: menu real,
NCC/Cutout na área convertida após panelizar, CNC/export e persistência em
MM/IN, guards e fixture privada opcional somente leitura. No projeto real,
conversão pós-panelização confere quatro placas frente à replicação da área
convertida antes; há pequenas diferenças da grade numérica, não placas perdidas.

Testes JavaFX sem Stage/cliques ou validação física. A conversão multipartes
é uma melhoria deliberada do FX: o `convert_outline2area` do Python neste
repositório continua escolhendo a maior região. O oráculo CAM não foi alterado
para esconder essa diferença, nem suas tolerâncias foram relaxadas.
