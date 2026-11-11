# Espiral adaptativa experimental — 2026-11-11

**Atualização integrada:** a estratégia agora acompanha bordas por offsets e
processa sub-bolsões, preservando a radial em regiões quase circulares ou como
retorno explícito por limite. Nome na interface: **Espiral adaptativa
(experimental)**. Medições, limites, resíduos e critérios atuais estão em
[SPIRAL_POCKETS.md](SPIRAL_POCKETS.md). Nos três exemplos principais, a estimativa
do programa caiu de 21,94/8,84/18,08 para 7,41/5,06/9,68 min. Não é promessa de
tempo físico de máquina, cobertura universal ou controle de engajamento.

**Histórico da radial e do Connect abaixo:** descreve a baseline anterior;
a indicação de perda para Lines e os números de testes são daquela etapa.

**Refinamento dos itens 1 e 2:** Paint e NCC agora têm prévia de cortes,
deslocamentos e entradas, com legenda, resumo por fresa e estimativa XY
explicitamente nominal. Connect tenta percursos curtos pela borda permitida e
integra o acabamento sem adiar contornos para depois de outro bolsão. Nos três
exemplos, os contadores G0 Z caíram de 935/240/484 para 830/234/475, preservando
todos os intervalos de corte exigidos. Continua experimental e atrás de Lines
nesses exemplos. Ver [escopo, testes e comparação final](CLEARING_PREVIEW_REFINEMENTS.md).

**O inventário de testes e os números abaixo registram a primeira implementação**
(`fc981a5b`), preservados como baseline. A validação final do refinamento tem
2.537 casos: 2.518 aprovados e 19 opcionais ignorados, sem falhas/erros.

Implementação: `fc981a5b`. Correção dos frames de prévia densos: `26d15170`.

Disponível em **Paint** e **NCC**, inclusive Rest. Os padrões atuais não mudam.
Não é um port de uma estratégia do Python. Mantida a Beta 7: sem bump ou
instalador nesta rodada, conforme a entrega conjunta solicitada.

## Usar e visualizar

No Paint, escolha o objeto/área, configure cada fresa e selecione
**Espiral adaptativa (experimental)**. Use **Prévia dos trajetos**: calcula com
os mesmos parâmetros de Gerar, em segundo plano, com progresso/cancelamento,
sem adicionar objetos. Alterar parâmetros, seleção, fonte ou fechar a ferramenta
descarta a prévia e cancela a publicação pendente. Gerar continua criando Geometry.
A prévia vale também para os outros métodos de Paint.

Os trajetos mostram **centros da fresa**, não a largura removida. O cobre mantém
sua cor; os trajetos usam a paleta de Geometry das prévias, adaptada ao tema.
Tooltips e mensagens novas estão em PT-BR, inglês, espanhol e francês.

O NCC oferece a mesma estratégia por fresa. O ângulo de varredura fica inativo:
a versão atual processa bolsões e voltas por offset; o ângulo raster não se aplica.
Connect liga somente trechos autorizados pelos limites originais; Contour adiciona
as bordas de acabamento. Rest permite passar por espaço já limpo para alcançar
a sobra, mantendo a área autorizada e os offsets individuais de cada fresa.

Geometry gerada por Espiral começa o CNC Job em **ordem da fonte e direção
preservada**; configurações explícitas de ordenação já existentes são respeitadas.
Proximidade pode reorganizar os trechos e perder a progressão radial.

## Qual espiral

A variante radial histórica (ainda usada em regiões quase circulares e no retorno por limite) é uma espiral de Arquimedes, com `r = passo × θ / (2π)` e
`passo = diâmetro × (1 − overlap)`. Uma fresa de 0,4 mm com overlap de 40%
produz passo radial de 0,24 mm. O diâmetro informado é mantido, sem arredondar
ou reduzir a fresa para fazer trajetos caberem.

Não usa Fibonacci nem o ângulo de ouro. Uma espiral áurea aumenta o afastamento
entre voltas; o afastamento fixo é útil para controlar a cobertura de usinagem.
Referências matemáticas: [Archimedean Spiral](https://mathworld.wolfram.com/ArchimedeanSpiral.html)
e [Golden Angle](https://mathworld.wolfram.com/GoldenAngle.html).

Cada componente da área de centros tem um ponto interior estável. A curva é
amostrada adaptativamente (erro de corda limitado ao menor de 0,1% do diâmetro
e 1% do passo) e recortada contra essa área. Um índice espacial recupera a
ordem e a direção dos fragmentos, também após a seleção do material Rest.
O último ponto recortado pertence a uma corda, não exatamente à curva analítica.
O limite explícito é um milhão de amostras **por fresa**, compartilhado entre
todos os componentes; excedê-lo produz mensagem localizada. Há cancelamento
cooperativo durante amostragem e preparação dos trajetos.

**Continuidade é local.** Pads, ilhas, passagens estreitas e componentes separados
podem dividir uma espiral em muitos trechos e exigir levantamentos. O acabamento
também pode exigir uma entrada separada. Não há controle de engajamento,
continuidade C1/C2 ou garantia de cobertura total/menor tempo. Não implementa
a estratégia de deformação pelo eixo medial descrita por
[Held e Spielberger](https://www.sciencedirect.com/science/article/pii/S0010448509001031).

## Evidência e limites

`SpiralClearingTest`: bolsão circular com um único caminho radial; passo conforme
diâmetro/overlap; MM/IN; pads redondos/retângulos arredondados; ilhas; componentes
separados; Rest 0,396→0,198; cancelamento/limite; IDs Python antigos preservados;
ângulo raster sem efeito; exportação real GRBL dos dois cortadores Rest mantendo
os inícios/finais ordenados e os vértices dentro do arredondamento. Nove casos.

`SpiralPreviewUiTest`: quatro casos de UI real, sem janela visível: prévia não cria
objetos; edição cancela publicação; Gerar após prévia cria Geometry com SOURCE;
NCC oferece Espiral e desativa o ângulo. Teste adicional protege a publicação de
frames densos apenas enquanto a camada de prévia existe.

`mvnw verify` completo: **2.526 casos, 2.507 aprovados, 19 opcionais ignorados**,
sem falhas/erros (20 suporte, 1.052 CAM, 185 aplicação, 1.269 FX).
A captura comunitária opt-in passou separadamente; não inflar o total com
relatórios XML antigos que continuam em `target` após execuções seletivas.

**32/32 controles de distância passaram**, NCC/Paint × MM/IN × Rest × Connect ×
Contour, com duas fresas 0,8/0,4 mm. Folga de análise de 0,5% do diâmetro para
aproximações dos buffers: não é uma prova de colisão física exata. Espiral não
repete os três ângulos de raster; a independência do ângulo tem teste próprio.

**Nove medições comunitárias**, três placas × Lines/Seed/Espiral. Mesmo alvo,
fresa 0,4 mm, overlap 40%, Connect/Contour ligados, sem Rest; Paint sobre caixa
do cobre menos cobre. A caixa é diagnóstica, **não é o Outline físico da placa**.
GRBL_11_no_M6 exportado e relido, Lines/Seed em NEAREST padrão e Espiral em SOURCE.

| Placa | Levantamentos Lines / Seed / Espiral | Minutos estimados Lines / Seed / Espiral |
|---|---:|---:|
| Breadboard Power Supply v2 | 235 / 378 / 935 | 9,83 / 12,01 / 22,84 |
| Modxo Adapter | 124 / 126 / 240 | 6,51 / 6,67 / 8,93 |
| Single-sided 5V | 140 / 208 / 484 | 11,43 / 12,58 / 18,19 |

**Não houve ganho nesses três exemplos.** A espiral radial recortada fragmenta
bastante em áreas alongadas e cheias de obstáculos. A etapa seguinte para esse
objetivo seria uma espiral que se adapte às bordas, não promover este protótipo
ao padrão. Não inferir FPS, tempo real de máquina ou desempenho universal.

Todos os núcleos estáveis de sobra ficaram zerados após a tolerância declarada
de 0,000712 mm. A sobra nominal bruta continua no relatório: de 0,00411 a
0,01548 mm²; não foi eliminada da medição nem convertida em promessa de limpeza
completa. O maior desvio amostrado de exportação foi 0,000069875 mm, dentro da
resolução de coordenadas. A menor distância dos centros ao cobre/borda foi
0,1999814 mm (raio nominal 0,2 mm, dentro da aproximação declarada).
Contagens históricas de polígonos sem caminho (4/10/0 por placa, em todos os
métodos) incluem áreas pequenas demais para essa fresa; alcance e sobra são
registrados separadamente. Geração observada variou de 42 a 5390 ms; execução
sequencial, JVM aquecendo e sem repetições: não é benchmark isolado.

Snapshot compacto: `validation/spiral-community-20261111.json` e
`validation/spiral-distance-20261111.json`. Dados brutos, G-code e capturas ficam
em `target/spiral-community-final-20261111/` e
`flatcam-fx/target/spiral-preview-review/`. As fontes comunitárias não são alteradas.

## Repetir

Depois de compilar as classes atuais, na raiz `flatcam-next`, PowerShell:

```powershell
& "$env:JAVA_HOME/bin/java.exe" -Xmx3g --class-path 'flatcam-cam/target/classes;flatcam-fx/target/dependency/*' tools/SpiralCommunityProbe.java samples/community/validation/fx-import.json target/spiral-new-measurement
.\tools\audit-cam-related.ps1 -Lines -Method SPIRAL -ClassOverrides .\flatcam-cam\target\classes -OutputDirectory .\target\spiral-new-distance
cmd /c ".\mvnw.cmd -pl flatcam-fx -am -Dtest=SpiralCommunityPreviewTest -Dflatcam.spiral.visual=true -Dsurefire.failIfNoSpecifiedTests=false test"
```

Use diretório novo para novas medições; o probe retoma arquivos existentes e
conserva erros registrados. A captura opt-in requer o inventário público salvo
em `samples/community/validation/fx-import.json`; não é exigida no CI sem amostras.
Ela usa Paint, MainWindow e PlotAreaView reais, sem Stage visível ou gravação das
preferências do usuário. A verificação visual identificou e corrigiu um defeito
preexistente: frames densos de camadas auxiliares eram descartados pelo filtro
de publicação. O teste falhava sem pixels da prévia e passou com a correção.
