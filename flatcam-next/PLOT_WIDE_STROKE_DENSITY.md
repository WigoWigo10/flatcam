# Corpos largos CNC pela imagem de densidade — 2026-10-10

Continuação de [PLOT_CNC_ABLATION.md](PLOT_CNC_ABLATION.md). Worktree dirty sobre `a8116d0f`,
sem commit/push.

## Decisão

O ensaio de omissão mostrou que os corpos largos (> 2,5 px) das passadas CNC, desenhados como
strokes vetoriais no Canvas, eram o gargalo do zoom. Em vez de um backend GPU novo (sem
biblioteca/dependência extra, sem interop de textura), esses corpos passam a usar o **mesmo
caminho de imagem de densidade** que já servia as linhas finas: o `DensityRaster` calcula a
cobertura exata do stroke na CPU, em background, e a UI só desenha uma imagem. O Canvas
continua como fallback e como caminho de tudo que não é largo/denso.

Isso atinge o objetivo do spike GPU (tirar o custo do Marlin/Prism da thread da UI, sem
omitir pixels) por um caminho que já tem versionamento, cancelamento, preview durante o zoom,
histerese e testes de ciclo de vida.

## O que mudou

- `DensityRaster`: traços acima de `ROUND_FROM_WIDTH` (2,5 px) ganham **pontas e juntas
  redondas**, como o Canvas desenha os corpos CNC (`StrokeLineCap/Join.ROUND`): um disco por
  extremidade de caminho e por vértice em que a curva vira o bastante para o entalhe aparecer
  (largura da cunha > 0,12 px). O disco é combinado com `max` (não soma) para não engrossar a
  borda do corpo. Ponto isolado vira disco. Largura máxima atendida: `MAX_WIDTH` = 96 px.
- `DensityRaster.shouldRasterize(..., lineWidth)`: um traço largo vira imagem com poucos
  segmentos visíveis (`WIDE_MIN_SEGMENTS` = 300; com histerese 0,6 ao sair), pois o Canvas
  paga a área do stroke, não só o comprimento.
- `PlotDensityRenderer`: o limite de 2,5 px virou `MAX_WIDTH`.
- `PlotAreaView`: as linhas finas de passada (1,25 px, ≥ 7 px de corpo) são desenhadas
  também sobre um corpo em imagem (antes só quando o corpo era vetorial).
- Chaves de desativação para comparar: `-Dflatcam.plot.density.wide=false` mantém os corpos
  largos no Canvas; `-Dflatcam.plot.density.wideMin=N` muda o limite de segmentos;
  `-Dflatcam.plot.density=false` desliga toda a densidade (já existia).

CAM, JTS, geometria, picking, edição, cores e LOD (`1,5 px`, `7 px`) não foram alterados.
Rasters continuam nunca sendo fonte de seleção, edição ou G-code.

## Equivalência visual (Canvas contra imagem de densidade)

`WideStrokeRasterTest` desenha caminhos com curvas, passadas paralelas e cruzadas no Canvas
(cap/join redondos, cor opaca do corte) e na imagem de densidade, e compara o alfa pixel a
pixel (larguras 3, 7, 9, 15 e 30 px):

| Largura (px) | Diferença média de alfa | Pixels do traço com diferença > 25% |
| --- | --- | --- |
| 3 | 0,00023 | 0,10% |
| 7 | 0,00032 | 0,18% |
| 9 (passadas) | 0,00034 | 0,30% |
| 15 | 0,00062 | 0,37% |
| 30 | 0,00149 | 0,60% |

As diferenças ficam na borda anti-aliased (o Canvas usa outro rasterizador de bordas).
Cor translúcida: a tabela de alfa do `DensityRaster` empilha cobertura sobreposta como o
Canvas empilha strokes separados; o corpo de corte do CNC é opaco (`#5E6CFF`), o de viagem é
fino (< 2,5 px) e continua como era.

## Desempenho no projeto real

Mesmo arquivo (SHA-256 `c41580f1…`, preservado), `Cobre_Morto_Bottom_cnc`, 120 passos × 3
repetições, **com JFR** (mesma condição dos ensaios de omissão), D3D/PixelBuffer, Intel Arc,
ICE_DARK, 1280 × 800. Zoom, repetições aquecidas 2 e 3, em milissegundos; intervalo máximo
de pulso não é a duração do frame apresentado pela GPU.

| Configuração | Intervalo máximo de pulso (2 / 3) | p95 do intervalo (2 / 3) | Fila até primeiros comandos p95 (2 / 3) | Corpos largos em imagem / vetor |
| --- | --- | --- | --- | --- |
| Canvas (wideMin muito alto) | 48,2 / 48,5 | 45,7 / 32,6 | 32,2 / 31,9 | 31 / 211 |
| Imagem, todos os largos (wideMin = 1) | 17,1 / 17,0 | 16,1 / 16,3 | 14,7 / 14,2 | 211 / 31 |
| Imagem, padrão (wideMin = 300) | 17,3 / 16,9 | 16,2 / 16,0 | 13,5 / 13,7 | 121 / 121 |

O intervalo máximo caiu de ~48 ms para ~17 ms (o piso de um pulso a 60 Hz) **sem omitir pixels**,
ao contrário do controle OMIT_WIDE_BODY. O padrão (300) já alcança isso: os grupos largos com
menos de 300 segmentos visíveis continuam vetoriais e baratos.

Limites: uma sessão por configuração, uma máquina e um projeto; os picos frios continuam
(primeira repetição: 111–128 ms com a densidade, 449 ms no Canvas nesta sessão; JIT e
primeiros frames). Sem JFR, a execução de referência já mostrou ~17 ms mesmo com o corpo
vetorial: a pausa de 48 ms apareceu com o JFR ligado, então o ganho vale principalmente quando
o sistema está sob carga de amostragem; usar a mesma condição para comparar. Durante o settle
de 60 ms do zoom, o corpo largo aparece como a imagem anterior transformada (como já era para
as linhas finas), e não como o vetor nítido.

## Verificação

`mvnw install`: BUILD SUCCESS; 711 + 152 + 670 testes sem falhas/erros (`WideStrokeRasterTest`
e um caso novo em `DensityRasterTest` incluídos). Os ensaios de benchmark abrem a janela
visível do Plot por cerca de um minuto cada.

## Spike GPU: malha de triângulos num SubScene (descartado como padrão)

Para saber se vale integrar uma apresentação GPU além da imagem de densidade, testei o único
caminho GPU real do JavaFX puro: os corpos largos como `TriangleMesh` (quads por segmento, discos
nas pontas/juntas) num `SubScene` com `ParallelCamera`, `AmbientLight` branca e MSAA. Zoom e pan
seriam só uma `Affine` no nó, sem retesselar. O código está em
[spikes/MeshSpike.java](spikes/MeshSpike.java) (fora do build Maven).

Resultados neste equipamento (Intel Arc, D3D, janela de 1280 × 800 fora da tela, 200 quadros de
zoom, `-Dprism.order=d3d,sw`):

| Cenário | Intervalo médio entre pulsos | Máximo |
| --- | --- | --- |
| Cena vazia (referência do pulso) | 16,0 ms | 17,2 ms |
| Canvas redesenhando 5 mil segmentos, 12 px | 45,5 ms | 319,5 ms |
| Canvas redesenhando 50 mil segmentos | 143,3 ms | 558,5 ms |
| Malha GPU, 200 segmentos | 31,0 ms | 33,4 ms |
| Malha GPU, 5 mil / 50 mil segmentos | 31,2 / 31,1 ms | 48,5 / 33,6 ms |
| Malha GPU, 250 mil segmentos (4,3 milhões de vértices) | 34,1–34,9 ms | 64,6 ms |
| Malha GPU sem MSAA, 50 mil / 250 mil | 30,9 / 30,9 ms | 48,0 / 33,2 ms |

- A cor é exata (diferença zero nos pixels sólidos) e o MSAA aproxima a borda do Canvas
  (diferença média de alfa 0,004–0,011; 0,8–7% dos pixels do traço com diferença > 25%, contra
  ≤ 0,6% da imagem de densidade).
- O custo da malha é **fixo**: ~31 ms (dois pulsos) com 200 ou com 250 mil segmentos, com ou sem
  MSAA. Escala bem com a geometria, mas o `SubScene` 3D sozinho já custa o dobro do pulso aqui.
  A imagem de densidade entrega ~17 ms (um pulso) no mesmo projeto real.
- Integrar exigiria dividir o desenho em duas camadas (Canvas abaixo, SubScene, Canvas acima com
  detalhes/setas/anotações), tratar HiDPI/resize e alfa translúcido (a malha sobrepõe triângulos).

**Decisão:** não integrar o backend de malha. Ele não supera a densidade (~17 ms) nem é mais
fiel, e custa uma reestruturação do empilhamento do Plot. Reavaliar se um próximo JavaFX reduzir
o custo fixo do `SubScene` ou se surgir um caso com muito mais geometria que a imagem de densidade
não atenda (a imagem custa CPU em background proporcional ao tamanho da tela, não à geometria).
Uma ponte nativa (OpenGL/Vulkan, ver `NATIVE_GPU.md`) continua sendo outro projeto, não um spike.

