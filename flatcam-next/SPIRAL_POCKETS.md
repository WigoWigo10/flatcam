# Espiral adaptativa por bolsões — 2026-11-11

Escopo autorizado após `8330c5ff`: melhorar áreas alongadas e com ilhas,
comparando cobertura, movimentos, G-code e custo de geração. Integrada em
Paint/NCC/Rest, sem mudar IDs Python, outros métodos, padrões, diâmetros ou
versão. Continua experimental; próxima Beta reúne as alterações.

## Estratégia integrada

A área permitida é erodida pelo raio nominal da fresa. Em componentes quase
circulares, continua a espiral de Arquimedes anterior. Nos demais, offsets
sucessivos criam voltas que acompanham as bordas. Quando um estreitamento
separa o offset, surgem sub-bolsões: uma busca em profundidade conclui cada ramo
antes de visitar o próximo, escolhido por proximidade. São voltas de contorno
ligadas pelo Connect, não uma curva analítica única, suave ou de engajamento
constante. Sem Connect, as voltas permanecem separadas.

O overlap determina o **passo máximo**. Em cantos convexos o passo normal pode
ser menor para que o disco nominal alcance a bissetriz. Um lóbulo pode sumir
antes do restante do bolsão; nesse caso um passe intermediário local cobre o
material que os dois offsets não alcançam. Isso foi necessário em pads e
estreitamentos reais: tratar apenas o último offset global deixava sobra.
O Contour continua solicitando o acabamento físico externo e das ilhas. Sem
Contour, o início é deslocado para dentro; não se promete acabamento de borda.
Rest seleciona trabalho pela sobra, mantendo o espaço original autorizado para
os centros. O diâmetro físico nunca é reduzido para fazer o trajeto caber.

Limites: 4.096 nós, profundidade 2.048 e um milhão de amostras por fresa.
Limites de árvore/não redução usam radial no ramo restante. Se o planejamento
adaptativo estoura as amostras, **a fresa inteira é recalculada pela radial com
orçamento novo**, descartando trajetos e footprints parciais. O painel de
regiões informa a mudança e tem tooltip nos quatro idiomas. Se a radial também
não couber, a operação é recusada pelo limite já existente. Cancelamento não é
capturado como motivo de retry. A tentativa adicional pode aumentar o custo de
geração em casos densos. Diagnóstico técnico para reproduzir a radial anterior:
`-Dflatcam.spiral.adaptive=false`; adaptativa habilitada por padrão.

Não implementa o método de Voronoi/eixo medial de
[Held e Spielberger](https://www.cad-journal.net/files/vol_11/CAD_11%283%29_2014_346-357.pdf).
A referência ajudou a estudar decomposição; não foi portada nem adiciona
promessas de continuidade C1/C2, controle de carga ou usinagem HSM.

## Critérios e validação

Plano: 40% algoritmo, 25% medição/integração, 15% testes/documentação e 20%
reservados à investigação de falhas. Trabalho serial. Prioridade alta para
cobertura, bordas, Rest e unidades; média para custo/limites e leitura da prévia.

| Requisito | Evidência / resultado |
|---|---|
| Alongamentos, ilhas e divisões | `SpiralPocketPlannerTest`: proximidade, ramos, interiores, fonte sem mutação, bolsão fino, cantos e limites; 6 casos |
| Lóbulo que desaparece localmente | `SpiralPocketCoverageTest`: Paint/NCC × MM/IN × Rest × overlap 0/40/80%; 24 casos com ponto testemunha e cobertura nominal |
| Compatibilidade e cancelamento | `SpiralClearingTest` + `SpiralConnectorTest`: círculo preservado, .198 exato, source order, GRBL MM/IN, IDs Python, limite/cancelamento |
| Distâncias por fresa | 32 controles NCC/Paint × MM/IN × Rest/Connect/Contour, duas fresas; aprovados |
| Trajetos reais / exportação | 18 casos de seis métodos nas três placas + 16 casos Lines/SPIRAL nas oito placas públicas, com retry Audio explícito |
| UX | Nome/ajuda PT-BR/EN/ES/FR, aviso radial, prévia nativa Paint escuro e NCC claro, teste do aviso/tooltip |

Critério de cobertura: disco nominal Q16 e alcançabilidade Q64, com resíduos
brutos, sensíveis à precisão e núcleos estáveis publicados separadamente.
Tolerância analítica do corpus: 0,0007116194 mm, incluindo cordas e arredondamento
GRBL. Núcleo zero após essa classificação **não significa sobra bruta zero**.
O alvo do corpus é caixa do cobre menos cobre; não é um Outline físico.
Exportação GRBL real relida; desvio amostrado limitado ao arredondamento XY.
Não se exige preservar os segmentos radiais antigos: este é outro planejamento.
Os 32 controles de distância usam a tolerância de aproximação declarada pelo
harness, sem diminuir o diâmetro nominal.

Build completo: **2.568 casos, 2.549 aprovados e 19 opcionais ignorados**, sem
falhas/erros. Verificação nativa de prévia executada à parte.

## Resultado de trajetória

Mesma fresa 0,4 mm, overlap 40%, Connect/Contour, sem Rest, avanço 300 mm/min,
GRBL. Contador `G0 Z` inclui movimentos do programa, não apenas mergulhos.
Tempo estimado do programa não inclui aceleração nem comportamento real da CNC.

| Placa | Radial anterior (min / G0 Z) | Adaptativa (min / G0 Z) | Lines (min / G0 Z) |
|---|---:|---:|---:|
| Breadboard Power Supply | 21,936 / 830 | 7,412 / 69 | 9,827 / 235 |
| Modxo Adapter | 8,838 / 234 | 5,061 / 22 | 6,510 / 124 |
| Single-sided 5V | 18,075 / 475 | 9,680 / 30 | 11,428 / 140 |

Redução estimada de 43–66% contra a radial anterior e 15–25% contra Lines
nessas três placas, com núcleos estáveis zerados. Em sete das oito placas a
adaptativa terminou, zerou esse núcleo e teve menor estimativa que Lines.
**Audio Watermark** atingiu o orçamento adaptativo; o retry radial gerou WKT e
G-code **idênticos byte a byte à versão anterior**. Conservou também a sobra
anterior: núcleo de 0,0000204773 mm², que não é contado como cobertura completa.
Nesse caso o tempo estimado é 86,410 min contra 55,572 de Lines: não houve ganho
na trajetória. A falha inicial e o retry estão preservados no relatório.

Standard foi ligeiramente mais rápido em três casos, mas deixou núcleos de
0,100934 / 0,002297 / 0,341856 mm². Não é comparado como vencedor com cobertura
equivalente. Isso é um achado para investigação própria, sem alterar Standard
nesta otimização. **Atualização de 2026-11-12:** essas sobras foram confirmadas
na rotina Python e corrigidas no Standard do FX; ver
[STANDARD_COVERAGE.md](STANDARD_COVERAGE.md). Os números acima preservam a
comparação histórica anterior à correção.

## Custo de geração no PC atual

Benchmark isolado após os testes: Core Ultra 7 155H (16 núcleos/22 threads),
mesma JVM Java 25 e heap máximo de 3 GiB. Três pares de processos JVM novos,
alternando radial/adaptativa; três repetições por placa em cada processo.
Medianas abaixo usam as seis amostras com a placa já aquecida de cada variante.
A primeira chamada de cada placa foi guardada, mas excluída dessas medianas;
as placas seguintes já encontram a JVM parcialmente aquecida. Leitura, G-code,
análise de cobertura, escrita e início do processo estão fora do cronômetro.

| Placa | Radial anterior | Adaptativa | Redução de tempo de cálculo |
|---|---:|---:|---:|
| Breadboard Power Supply | 3.810,4 ms | 116,3 ms | 96,9% |
| Modxo Adapter | 122,0 ms | 110,2 ms | 9,7% |
| Single-sided 5V | 288,6 ms | 164,3 ms | 43,0% |

O ganho de cálculo depende da geometria: grande na Breadboard, modesto no
Modxo. Não é medição de FPS ou de utilização máxima dos núcleos/GPU. CPU do
processo, faixas observadas e primeiras chamadas constam no JSON. Não se mediu
pico de RAM, e carga de fundo/temperatura do Windows não foram controladas.
Os testes e outros probes do projeto estavam encerrados durante a rodada.
Dados brutos: `validation/spiral-pockets-generation-20261111.json`.

## Candidatos rejeitados

1. Perfil homotético com divisão em células: manteve cobertura nas três placas,
   mas aumentou trajetos/tempo; descartado.
2. Offsets sem complemento local: melhor tempo, mas sobras reais perto de pads
   e lóbulos (distâncias de 0,214–0,220 mm para raio 0,200 mm). Não era somente
   artefato da área varrida. Descartado.
3. Ajuste apenas em cantos: não corrigiu os lóbulos que somem antes do último
   nível. Descartado. O complemento local integrado passou na matriz de 24 casos.

Relatórios compactos em `validation/spiral-pockets-20261111.json` e
`validation/spiral-pockets-distance-20261111.json`. Logs, WKT, programas e
capturas de investigação em `target/spiral-offset-*` e
`flatcam-fx/target/spiral-preview-review`. Fontes públicas e licenças em
`samples/community`; inventário de entrada preservado.

## Repetir

No diretório `flatcam-next`, depois de compilar CAM e preparar as dependências:

```powershell
& "$env:JAVA_HOME/bin/java.exe" -Xmx3g '-Dflatcam.spiral.methods=LINES,SEED,STANDARD,COMBO,REGION_LINES,SPIRAL' --class-path 'flatcam-cam/target/classes;flatcam-fx/target/dependency/*' tools/SpiralCommunityProbe.java samples/community/validation/fx-import.json target/spiral-repeat-new
& "$env:JAVA_HOME/bin/java.exe" -Xmx3g '-Dflatcam.spiral.methods=LINES,SPIRAL' '-Dflatcam.spiral.boards=.*' --class-path 'flatcam-cam/target/classes;flatcam-fx/target/dependency/*' tools/SpiralCommunityProbe.java samples/community/validation/fx-import.json target/spiral-eight-new
.\tools\audit-cam-related.ps1 -Lines -Method SPIRAL -ClassOverrides .\flatcam-cam\target\classes -OutputDirectory target/spiral-distance-new
```

Usar diretório novo para não reaproveitar os JSON de uma execução anterior.
`MEASURED` significa medido, não aprovação de cobertura: conferir campos de
resíduo, erros e `radialFallback`. Comparação de geração isolada usa
`tools/SpiralGenerationBenchmark.java INVENTORY OUTPUT_JSON TAG`; carregar a
baseline congelada ou as classes atuais no classpath, com mesma JVM/heap.
