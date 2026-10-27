# Investigação de segmentos externos das passadas CNC — 2026-10-26

## Decisão

**Não ativar o protótipo por padrão.** Ele reduz comandos enviados ao Canvas e
preserva os pixels nos cenários verificados, mas não demonstrou melhora consistente
de FPS ou 1% low na GTX 1650. Em um par adicional, os contadores de CPU pioraram.
O aplicativo normal mantém o renderer anterior; a nova opção é experimental e
explícita no benchmark, não uma preferência salva ou otimização anunciada.

Base: `32688546`, worktree dirty durante os ensaios. Mesmo projeto privado
autorizado e objeto `Cobre_Morto_Bottom_cnc`, GTX 1650 confirmada no Prism, driver
32.0.16.1062, Java 25.0.4.1/JavaFX 25.0.4, ICE_DARK, 1280×800 lógicos e escala
1,25×1,25. Ensaios em série com JFR + PresentMon, sem geração CAM ou gravação da
fonte. Todos os sete ensaios desta investigação preservaram seu hash original.
Implementação consolidada em `b942ca47`; os relatórios mantêm a base/estado reais
da execução anterior ao commit, sem reescrever metadata histórica.

## Implementação experimental

`CanvasCncLineRenderer` testa segmentos contra a área do Canvas com halo de
meia largura do traço + 4 pixels lógicos. Um segmento só é rejeitado quando as
duas pontas estão fora do mesmo lado desse limite. Não calcula novos endpoints
nem usa a imagem para picking/CAM. Mantém a simplificação subpixel anterior,
inclusive o último vértice aceito quando um segmento é rejeitado.

Subpaths remanescentes da mesma peça permanecem em **um único stroke**; não
mistura peças nem altera sua ordem. Isso é importante para a composição de
transparência em cruzamentos. Só atende à passada sólida de **1,25 px**, com
pontas/juntas redondas, transform identidade e sem efeitos. Outras larguras,
tracejado, Multi-Color, fills, transform externo, efeitos ou coordenadas não
finitas usam o renderer anterior. Não há caches de geometria/tarefas extras,
cancelamento do raster do corpo, mudança de ferramenta, seleção ou G-code.

`-CncLines REFERENCE` mantém o caminho anterior (padrão do script/aplicativo);
`-CncLines CLIPPED` liga o experimento. O título registra esse modo e continua
identificando omissões `OMIT_*` separadamente. CLIPPED não é OMIT_PASS_LINES:
todos os segmentos que podem influenciar pixels visíveis continuam presentes.
Contadores em `cncPasses.PASS_LINES`: accepted/emitted/culledSegments, strokeCalls,
clippedCalls e fallbackParts. São contagens acumuladas na fase, não segmentos
únicos do projeto. A simplificação subpixel anterior ocorre antes da contagem.

## Comparação alternada, 240 passos × três repetições

`target/plot-suite-20261026-220612-397`: quatro capturas completas, sem perda ETW
reportada, runtime/viewport/adaptador e fonte conferidos pelo runner. Dois pares
REFERENCE→CLIPPED, preservando repetições individuais e o custo frio inicial.
Resultados do zoom aquecido, repetições 2 e 3:

| Execução | FPS médio, rep. 2 / 3 | 1% low, rep. 2 / 3 | Frame p95 ms, rep. 2 / 3 |
| --- | ---: | ---: | ---: |
| REFERENCE 1 | 39,01 / 34,65 | 16,36 / 17,98 | 50,01 / 50,02 |
| CLIPPED 1 | 36,54 / 36,00 | 14,99 / 14,99 | 50,02 / 50,03 |
| REFERENCE 2 | 38,01 / 35,12 | 19,97 / 17,11 | 50,02 / 50,05 |
| CLIPPED 2 | 38,68 / 41,09 | 16,35 / 19,91 | 50,03 / 50,02 |

Nas repetições 2/3 de CLIPPED, cada fase evitou **603.402 de 719.582 segmentos**
aceitos (~83,85%), enviando 116.180 e 3.660 strokes. Ainda assim, p95 de display
permaneceu ~50 ms; o primeiro par não melhorou a média de maneira consistente e
o segundo melhorou a média, mas não o 1% low. Não escolher apenas a melhor rodada
para afirmar aceleração. Os números não devem ser misturados aos ensaios de
120 passos da sessão anterior: trajetória temporal e tamanho das amostras mudaram.

JFR do zoom aquecido, soma das duas repetições, **amostras estatísticas CPU**:

| Execução | ExecutionSamples | QuantumRenderer | Com Marlin na pilha |
| --- | ---: | ---: | ---: |
| REFERENCE 1 | 351 | 329 | 254 |
| CLIPPED 1 | 344 | 328 | 256 |
| REFERENCE 2 | 338 | 320 | 248 |
| CLIPPED 2 | 315 | 302 | 243 |

Categorias se sobrepõem, as fases têm durações diferentes e contagens não são
percentuais de ocupação GPU. A concentração em QuantumRenderer/Marlin persiste.
**Inferência:** enviar menos segmentos externos não retirou o custo dominante
do desenho dos trechos visíveis. Não determina sozinho qual operação interna é
responsável nem prova saturação, temperatura ou limitação de clock da GTX 1650.

## Contadores CPU por thread e capturas determinísticas

O benchmark agora registra `threadCpuTime` com ThreadMXBean: CPU/user time por
thread de plataforma, incluindo QuantumRenderer, FX, plot-* e common pool.
Não habilita globalmente contadores desligados, não gera CPU sintética nem mede
tempo GPU. Os snapshots antes/depois têm seus próprios timestamps: enquadram a
fase, mas incluem uma pequena janela de amostragem, não são fronteiras perfeitas.
Thread nova, terminada, contador inválido/reset ou mecanismo indisponível fica
explicitamente sem comparação, não zero. User time ausente também não vira zero.
Granularidade é a do runtime/OS; não inferir precisão de microssegundos das casas
decimais. `summary.csv` mantém CPU ms separado de FPS/Hz e usa vazio para ausência.

Par de integração posterior, 120 passos × três repetições, com esses contadores:
`target/plot-suite-20261026-221813-538`, ambas as capturas completas.

| Modo / repetição | Duração de fase ms | QuantumRenderer CPU ms | FX CPU ms | FPS médio |
| --- | ---: | ---: | ---: | ---: |
| REFERENCE / 2 | 2730,19 | 1140,625 | 109,375 | 38,89 |
| REFERENCE / 3 | 2809,43 | 1140,625 | 62,500 | 36,22 |
| CLIPPED / 2 | 2952,60 | 1421,875 | 187,500 | 37,29 |
| CLIPPED / 3 | 3231,41 | 1515,625 | 156,250 | 34,24 |

Esse par não substitui os pares de 240 passos e não estabelece causalidade de
cada custo, mas reforça a decisão de não habilitar CLIPPED. Tempo CPU cresceu em
vez de diminuir nessa amostra; menor volume de comandos não é aceleração por si.

`-Snapshots` acrescenta **canvas-far.png / canvas-near.png** além dos snapshots
do Plot completo. Captura o Canvas principal, sem HUD/cursor de Snap em outros
nós; ocorre após as fases medidas e não oculta cursor durante a interação.
Os snapshots do Plot completo continuam incluindo a interface, cujo cursor pode
mudar de posição entre execuções, impedindo um hash determinístico.

No projeto real, os arquivos Canvas REFERENCE e CLIPPED deste par tiveram hashes
SHA-256 idênticos, respectivamente:

- Longe: `DEE3E7F78A467AD41C22FE410A39061CD3D4013D2C3B0E2DE3C01DA38AB28F81`.
- Perto: `46972683381B7ABF099C814CFA0D2EEEE3CE9C69748A0EB643B6A58D119DA160`.

Isso comprova igualdade nesses dois estados prontos, não em todos os quadros de
movimento, todos os projetos ou todos os drivers. Ambos continuam mostrando o
desenho de longe e os detalhes de perto.

## Reproduzir e localizar os dados

```powershell
$projetoTeste = 'D:\caminho\Projeto.FlatPrj'
$presentMonTeste = 'target/fps-tools/PresentMon-2.6.0-x64.exe'
.\benchmark-plot-suite.ps1 -Project $projetoTeste -ObjectName Cobre_Morto_Bottom_cnc `
    -PresentMon $presentMonTeste -CncLineComparison -LinePairs 2 `
    -Steps 240 -Repeats 3 -Jfr -Snapshots
# Usar o caminho real impresso pelo runner; nenhum relatório é sobrescrito.
..\.venv\Scripts\python.exe tools/summarize_plot_suite.py --suite target/plot-suite-<data>/suite.json
```

`LinePairs` aceita 1–5 pares intercalados. A bateria histórica de omissões continua
disponível sem `-CncLineComparison`; comparar controles sem omissões primeiro.
Binário oficial PresentMon, versão/hash e permissões conforme
[PLOT_PRESENTED_FPS.md](PLOT_PRESENTED_FPS.md). Não houve elevação nem mudança de
registro, driver, clock ou preferência. Não rodar benchmark junto de build/testes.

| Artefato em target | Papel |
| --- | --- |
| `plot-benchmark-20261026-220513-869` | Piloto CLIPPED, 120 passos/3 repetições, não misturado à bateria |
| `plot-benchmark-20261026-220612-650` | REFERENCE 1, 240 passos |
| `plot-benchmark-20261026-220703-872` | CLIPPED 1, 240 passos |
| `plot-benchmark-20261026-220755-693` | REFERENCE 2, 240 passos |
| `plot-benchmark-20261026-220846-141` | CLIPPED 2, 240 passos |
| `plot-benchmark-20261026-221813-829` | REFERENCE com CPU/Canvas snapshots |
| `plot-benchmark-20261026-221850-607` | CLIPPED com CPU/Canvas snapshots |

Projetos, imagens, CSV, JSON e JFR privados permanecem ignorados em target, sem
upload ou dados de placa versionados.

## Limites encontrados e próximo alvo

Uma repetição da matriz de pixels detectou divergência em traço **largo de 7 px**
a 2×; o escopo foi reduzido a 1,25 px, não houve relaxamento da tolerância para
as passadas CNC. Traço largo agora cai explicitamente no renderer anterior.
Falhas iniciais de compilação/getEffect e ausência de dash array foram corrigidas
antes das capturas aprovadas; logs iniciais não contam como verificação final.

Próxima hipótese: investigar um caminho de strokes retidos/primitivas adequado à
GPU ou um protótipo restrito LWJGL, com o mesmo desenho completo, antes de migrar
o viewport. O código do OpenJFX diferencia emissão de path e de linha simples em
[NGCanvas](https://github.com/openjdk/jfx/blob/jfx25/modules/javafx.graphics/src/main/java/com/sun/javafx/sg/prism/NGCanvas.java)
e possui tratamento de primitivas no
[BaseShaderGraphics](https://github.com/openjdk/jfx/blob/jfx25/modules/javafx.graphics/src/main/java/com/sun/prism/impl/ps/BaseShaderGraphics.java).
Isso é uma hipótese para novos ensaios, **não prova de que trocar chamadas ou
desenhar segmentos separados preservará pixels/composição de alpha ou será mais
rápido**. Não substituir uma polyline translúcida por strokes independentes sem
testar juntas, cruzamentos, pontas, ordem, Snap/picking e custo de apresentação.

## Verificação de fechamento

`mvnw.cmd -q verify` aprovado: **1928 registros novos, 1910 aprovados,
18 skips opcionais**, zero falhas/erros. Sete testes Java novos: três do renderer
experimental, três dos contadores CPU e um dos contadores de segmentos. Não
confundir as 72 configurações internas da matriz de pixels com 72 testes JUnit.
A matriz final inclui o fallback para traço largo, mantendo a exigência de
igualdade exata para 1,25 px; passou também em três execuções focadas consecutivas.
O lifecycle do probe foi verificado com a opção CLIPPED na JVM. Logs em
`target/cnc-lines-pixels-repeat-{1,2,3}.log`, `cnc-lines-integration-clipped.log`
e `cnc-lines-full-verify.log`. Doze testes Python dos analisadores aprovados.
Esses testes não certificam usinagem física nem todos os quadros intermediários.

Não há sessão ETW FlatCAMFX restante após os ensaios, nem mudanças de preferências.
Nenhuma aceleração foi promovida ao aplicativo normal e não foi entregue LWJGL.
