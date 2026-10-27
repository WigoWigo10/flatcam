# Plot no Avell Core Ultra 7 155H / Arc — 2026-10-27

Foram concluídas **seis capturas de FPS apresentado**, com PresentMon 2.6.0 e
JFR, todas usando Intel Arc / Direct3D. Fonte preservada por SHA-256 em todas.
É uma baseline deste computador, não um ganho isolado contra o Lenovo: o arquivo
de entrada, revisão do código e refresh da tela diferem da rodada histórica.

## Máquina e condições

| Item | PC-ARC155H-AVELL |
| --- | --- |
| Fabricante / modelo informado pelo Windows | Avell / Smart |
| CPU | Intel Core Ultra 7 155H, 16 núcleos / 22 threads |
| RAM | 2 × 16 GiB, 5600 MT/s; aproximadamente 31,43 GiB visíveis ao OS |
| GPU ativa no Plot | Intel(R) Arc(TM) Graphics, integrada |
| Driver | igd9trinity64.dll — 32.0.101.8991 |
| Pipeline confirmado em cada relatório | com.sun.prism.d3d.D3DPipeline |
| Windows | Windows 11 Pro, build 26200 |
| Runtime | Oracle JDK 25.0.4.1+1-LTS-5; dependência JavaFX 25.0.4 |
| Tela inventariada antes do teste | 2880 × 1800, 120 Hz |
| Energia inventariada antes do teste | Plano Equilibrado; BatteryStatus=2, carga 100% |
| Tema / área lógica do Plot | ICE_DARK / 1280 × 800 |
| Escala da série principal e Geometry | 1,25 × 1,25: área física correspondente de 1600 × 1000 |
| Escala habitual, confirmada na última rodada | 2 × 2: área física correspondente de 2560 × 1600 |
| Instrumentação | Host nativo JNI, JFR profile, PresentMon 2.6.0 com display tracking |

Não foram amostrados temperatura, clocks, ocupação GPU, RAM/VRAM compartilhada
ou carga completa de outros aplicativos. Não inferir saturação da GPU desses dados.
A escala 125% foi aplicada apenas ao processo por `glass.win.uiScale`, conforme
o [código do OpenJFX 25](https://github.com/openjdk/jfx/blob/jfx25/modules/javafx.graphics/src/main/java/com/sun/glass/ui/win/WinApplication.java).
As configurações de tela e energia do Windows não foram alteradas.

## Corpus e revisão

Arquivo local autorizado: `Project_20260917_160328.FlatPrj`, 1.948.008 bytes.
SHA-256: `c41580f1ac0d1e0baf93026e6afed18719a5614e78da197407790c82667aa10c`.
O [perfil Lenovo](BENCHMARK_PC_GTX1650.md) registra outro hash,
`87b9af217f2089931df7b91ae083fcc63c8001e0708ed64b0ba52ae4a4f4f6a4`.
O nome do objeto é o mesmo; isso não prova identidade completa da carga.

Código: `bfba1d3b`, worktree dirty com a rotação ainda sem commit. O patch das
alterações rastreadas e o teste de rotação não rastreado foram guardados junto
à baseline, antes de criar este documento. SHA-256 de `working-tree.patch`:
`caefbacaf9e0ef995851b3ea5f880f38c6978047819f85d2608bda7c266396f1`.
Não reescrever os relatórios como se o teste usasse árvore limpa.

CNC: somente `Cobre_Morto_Bottom_cnc` e seus sublayers. Geometry: somente
`isolation_bottom`. Sem geração CAM ou salvamento da fonte. Capturas Canvas/Plot
foram feitas após as fases medidas, não usadas para calcular FPS.

## Resultados aquecidos

Repetições 2 e 3, mantidas separadas nos dados brutos. Intervalos abaixo mostram
o menor/maior valor dessas repetições, não intervalo de confiança estatística.

| Controle | Passos / repetições | Escala | Pan FPS médio | Zoom FPS médio | Zoom 1% low | Zoom frame p95 ms |
| --- | --- | --- | ---: | ---: | ---: | ---: |
| REFERENCE 1 | 240 / 3 | 125% | 58,52–58,65 | 57,83–58,42 | 19,98–19,99 | 33,32 |
| CLIPPED 1 | 240 / 3 | 125% | 58,78–59,02 | 58,30 | 18,94–19,98 | 33,30–33,33 |
| REFERENCE 2 | 240 / 3 | 125% | 58,42–58,90 | 58,18–58,54 | 21,17–22,47 | 24,99–25,01 |
| CLIPPED 2 | 240 / 3 | 125% | 58,17–58,66 | 58,40–58,65 | 19,99–21,16 | 25,08–28,82 |
| Geometry FULL | 120 / 3 | 125% | 57,60–57,83 | 57,37–57,58 | 17,07–17,14 | 24,99–25,00 |
| CNC FULL, escala habitual | 120 / 3 | 200% | 58,07–58,08 | 57,37 | 17,13 | 33,32–33,33 |

REFERENCE/CLIPPED foram alternados em dois pares. O clipping foi efetivamente
exercitado: sem fallback, descartou 603.402 de 719.582 segmentos aceitos na
repetição 3 do primeiro par. Os Canvas longe/perto tiveram hashes idênticos
entre os modos em ambos os pares; inspeção das capturas confirmou placa inteira
e detalhes próximos. Isso valida esses estados prontos, não todos os quadros.

**Não houve benefício consistente do clipping.** A média fica próxima do teto
da carga gerada; o 1% low não melhora de forma uniforme. Mantê-lo experimental.
O harness envia uma atualização de câmera e espera 16 ms no worker, além do
tempo para atender a solicitação. Portanto, ~58–59 FPS aqui não é uma medição
da capacidade máxima da Arc nem prova um limite global de 60 FPS do JavaFX:
a cadência UI registrada chega perto de 120 Hz e é uma métrica distinta.

Ainda há engasgos: os piores intervalos do zoom aquecido chegam a ~83 ms.
O primeiro zoom das quatro execuções de 125% ficou em 48,65–52,65 FPS, com
1% low de 6,10–8,78. Na escala habitual, o primeiro zoom ficou em 47,06 FPS,
com pior intervalo de 162,84 ms. A repetição 1 permanece em CSV/JSON.

Geometry executou quatro ciclos apagar/desfazer por repetição: 24 ações no total,
com identidade original restaurada ao final. Pedido até comandos prontos p95:
90,96 / 95,31 / 93,33 ms. Não é latência física até a tela, nem inclui toda a
tabela/sidebar do editor. Não interpretar FPS de ações discretas como animação.

JFR do CNC continua concentrando amostras CPU em QuantumRenderer/Marlin. No
primeiro REFERENCE, zoom 2/3: 80/70 amostras totais, 69/65 QuantumRenderer,
59/55 com Marlin. Categorias se sobrepõem. Não houve sobreposição de pausas GC
registradas nos zooms aquecidos das seis execuções; ausência de eventos registrados
não prova ausência absoluta de GC. Não é medida de tempo/utilização GPU.

A comparação entre as duas escalas também tem 240 versus 120 passos e sessões
diferentes: não usar a tabela para calcular isoladamente o custo do DPI. Para isso,
repetir o mesmo protocolo alternado em ambas as escalas. Para comparar hardware,
repetir também no Lenovo com o mesmo corpus, código e condições de apresentação.

## Comparação com os registros GTX 1650

A comparação CNC abaixo usa as duas execuções REFERENCE, 240 passos × três
repetições, escala 125%, área lógica 1280 × 800 e desenho completo. São os
protocolos mais próximos disponíveis nos dois computadores. Intervalos agregam
as repetições 2/3 de ambos os pares; não misturam CLIPPED nem controles OMIT.
Origem Lenovo: [comparação alternada das linhas](PLOT_CNC_LINE_CLIPPING.md#comparação-alternada-240-passos--três-repetições).

| Medida | Lenovo i5-10300H + GTX 1650 | Avell Ultra 7 155H + Arc |
| --- | ---: | ---: |
| CNC zoom: FPS médio | 34,65–39,01 | 57,83–58,54 |
| CNC zoom: 1% low FPS | 16,36–19,97 | 19,98–22,47 |
| CNC zoom: frame time p95 ms | 50,01–50,05 | 24,99–33,32 |

Nos registros disponíveis, o conjunto Avell apresentou zoom mais fluido em média.
A diferença nos intervalos mais lentos é menor: o Avell ainda teve intervalos de
até ~83 ms no zoom aquecido. O clipping não trouxe ganho consistente em nenhum
dos dois computadores e permanece desativado por padrão.

Geometry tem protocolo separado, 120 passos × três repetições e escala 125%.
O [registro Lenovo com PresentMon](PLOT_PRESENTED_FPS.md#resultado-desta-sessão)
informa zoom aquecido de aproximadamente 27,44 FPS; o Avell mediu 57,37–57,58.
Nas 24 ações apagar/desfazer, pedido até comandos prontos p95 por repetição:
Lenovo 109,62 / 93,45 / 108,08 ms; Avell 90,96 / 95,31 / 93,33 ms.
Não houve a mesma diferença de magnitude na latência de edição que na média de
FPS do zoom; são métricas distintas e a carga não foi demonstrada idêntica.

**Essas observações não isolam o desempenho das GPUs.** O Avell tem outra CPU,
memória, driver e tela de 120 Hz; o Lenovo usa tela inventariada de 60 Hz. O
arquivo e a revisão também diferem, ambos foram medidos com alterações sem
commit, e os dados brutos Lenovo não estão nesta cópia. O pipeline JavaFX ainda
executa trabalho CPU em QuantumRenderer/Marlin em ambas as máquinas. Não calcular
um ganho exclusivo da Arc, de CPU ou de uma refatoração a partir dessas tabelas.

Para uma comparação controlada: repetir nos dois PCs a mesma revisão limpa,
arquivo por hash, objeto, parâmetros e instrumentação; igualar ou declarar as
condições de refresh/composição, energia e temperatura. A primeira repetição e
os dados brutos devem continuar preservados. Como a carga envia atualizações
próximas de 60 por segundo, o resultado do Avell também está próximo desse teto
de demanda, sem medir sua capacidade máxima de apresentação.

## Evidência e permissões

A tentativa inicial sem elevação teve quatro capturas recusadas por acesso à
sessão ETW. Elas permanecem reprovadas em
`target/plot-suite-20261027-084635-522`; não entram nos resultados nem viram zero FPS.
Após autorização explícita do usuário e confirmação UAC, seis capturas aprovadas:

| Caso | Diretório em target |
| --- | --- |
| REFERENCE 1 | plot-benchmark-20261027-084822-520 |
| CLIPPED 1 | plot-benchmark-20261027-084903-749 |
| REFERENCE 2 | plot-benchmark-20261027-084945-655 |
| CLIPPED 2 | plot-benchmark-20261027-085027-083 |
| Geometry 125% | plot-benchmark-20261027-085108-248 |
| CNC escala habitual 200% | plot-benchmark-20261027-085137-922 |

Manifest da série: `target/plot-suite-20261027-084822-474/suite.json`.
Consolidação portátil: `target/arc-benchmark-20261027-084821-053/` contém
`preflight.json`, `summary.md`, `metrics.csv` (todas as repetições),
`captures.json`, `canvas-comparison.json`, `working-tree.patch` e o teste novo.
Os diretórios individuais contêm CSV/JSON/logs PresentMon, JFR e snapshots.
Transcrição em `target/arc-benchmark-20261027-elevated-transcript.log`.

PresentMon portátil veio do [release oficial 2.6.0](https://github.com/GameTechDev/PresentMon/releases/tag/v2.6.0),
com SHA-256 idêntico ao registrado no Lenovo. Não houve instalação global nem
mudança de grupo/permissão do usuário. Os seis coletores retornaram zero e as
consultas finais por seus seis nomes ETW exclusivos retornaram sessão não encontrada.
Nenhum serviço ou profiler externo foi encerrado.

Resultados privados em target não acompanham o Git; preservar a árvore completa
para reanálise. Não foi alterado código de produção nem entregue otimização.
