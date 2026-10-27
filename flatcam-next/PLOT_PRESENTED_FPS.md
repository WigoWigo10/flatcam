# FPS apresentado e custo das linhas — GTX 1650, 2026-10-26

## Escopo e interpretação

O benchmark agora mede **FPS de quadros apresentados atribuído pelo ETW**, via
PresentMon, além da cadência da thread de interface. Não calcula FPS a partir do
tempo de emissão de comandos, da fila da UI ou dos callbacks de AnimationTimer.
São métricas diferentes; um Canvas que recebe comandos rapidamente ainda pode
demorar para rasterizar e apresentar o quadro.

O renderer de produção continua JavaFX/Canvas. Esta entrega instrumenta e compara;
os modos `OMIT_*` **não são otimizações entregues nem paridade visual**.
Não houve mudança em CAM, G-code, projetos, preferências ou seleção de GPU do Windows.

## Resultado desta sessão

Base `207f83b9`, worktree dirty; notebook i5-10300H, GTX 1650 confirmada pelo Prism,
driver 32.0.16.1062, Java 25.0.4.1/JavaFX 25.0.4, ICE_DARK, Scene 1280×800
lógicos, escala 1,25×1,25. Projeto privado autorizado, filtro exato de objeto;
CPU/JFR e captura de display juntos, sempre em série. Steps 120, três repetições.
Não comparar esses valores absolutos como ganho contra a bateria anterior sem
PresentMon: mudou a instrumentação. Fonte intacta por hash em todas as nove rodadas.

`target/plot-suite-20261026-214007-660/suite.json`, `summary.csv` e `summary.md`:
oito execuções com FPS completo; SW completou as ações, mas **não gerou CSV de
display utilizável**. A série retorna `INCOMPLETE`/erro por esse controle, não
declara nove aprovações. Não foi determinada uma solução de captura SW nesta sessão.

Zoom aquecido, repetições **2 e 3 separadas**, intervalo entre os dois resultados:

| Controle | FPS apresentado médio | 1% low FPS | Frame time p95 ms |
| --- | ---: | ---: | ---: |
| GPU FULL | 37,73–39,23 | 15,00–17,14 | 50,04 |
| Sem linhas das passadas | 47,74–56,16 | 13,34–17,14 | 16,81–50,00 |
| Sem drawImage | 37,13–39,87 | 15,00 | 50,03–50,09 |
| Sem linhas e sem drawImage | 55,16–55,72 | 17,14–17,16 | 16,93–33,25 |
| PixelWriter completo | 35,27–36,43 | 12,02–14,99 | 50,06 |
| Sem CNC, piso diagnóstico | 57,05–58,00 | 20,01–20,03 | 16,79–16,80 |
| GPU FULL, recontrole final | 38,52–38,74 | 15,01–19,98 | 50,00–50,01 |

FULL teve pan aquecido em 54,42–56,19 FPS; zoom da primeira repetição foi
29,71 FPS/1% low 5,00. Preparação inicial/JIT não devem ser escondidos: tabela
completa e CSV retêm a repetição 1. No recontrole FULL, o pior intervalo de
display no zoom aquecido foi 50,07/83,22 ms. A cauda 1% low não melhora de modo
uniforme com omissão; há poucas amostras por fase e a variabilidade é relevante.

**Inferência limitada:** as linhas finas sobre as passadas são um alvo mais
promissor que trocar PixelBuffer/PixelWriter ou omitir a apresentação das imagens.
Omitir drawImage sozinho não resolveu o zoom; retirar linhas favoreceu a média,
sem eliminar todos os engasgos. Isso não prova saturação da GTX 1650, nem que
LWJGL resolverá sozinho. São omissões de partes diferentes do trabalho visual,
não comparação de renderers funcionalmente equivalentes.

JFR no zoom das repetições 2+3: FULL teve 162 amostras CPU, 152 QuantumRenderer e
114 com Marlin; sem linhas, 75/64/52; sem drawImage, 168/154/116. São contagens
estatísticas de fases com durações diferentes, com categorias sobrepostas, não
percentuais GPU ou custos aditivos. Não houve pausa GC registrada sobre esses
trechos GPU. O recontrole FULL repetiu a concentração em QuantumRenderer/Marlin.

Geometry é outro corpus/workload: zoom aquecido em ~27,44 FPS, e 24 ações de
apagar/desfazer com identidade original restaurada. Pedido→comandos prontos p95
109,62/93,45/108,08 ms por repetição; não é latência até display. No SW, fila p95
do zoom aquecido 157,19/162,22 ms e cadência UI ~6 Hz, **não 6 FPS medidos**.

### Evidência local e tentativas não aprovadas

Todos os artefatos abaixo ficam ignorados em `target`; não versionar projetos,
CSV, JFR ou capturas privadas:

| Caso | Pasta `target/plot-benchmark-...` |
| --- | --- |
| GPU FULL | `20261026-214007-905` |
| Sem linhas | `20261026-214044-655` |
| Sem drawImage | `20261026-214117-756` |
| Ambos | `20261026-214153-185` |
| PixelWriter | `20261026-214226-962` |
| Piso sem CNC | `20261026-214306-638` |
| Recontrole FULL | `20261026-214337-421` |
| SW, FPS indisponível | `20261026-214412-621` |
| Geometry | `20261026-214631-554` |

Capturas visuais adicionais FULL: `20261026-214755-652/plot-far.png` e
`plot-near.png`, inspecionadas diretamente. **Ambas mostram usinagem**; longe
aparece a placa inteira, perto os corpos e as linhas das passadas. Essa rodada
curta de 24 passos/uma repetição verifica desenho/coleta, não compõe a tabela.

Tentativas `212417-262`, `212718-358` e `212858-716` falharam em readiness/log
bufferizado, descarte de probes após dispose e observação do exit code do helper.
A série `plot-suite-20261026-213122-734` não teve capturas completas, incluindo
uma perda de eventos reportada; não usar seus números como FPS aprovado. Duas
sessões ETW próprias deixadas pelas primeiras falhas foram encerradas por seus
nomes exatos. O efeito isolado dessas sessões na falha da captura não foi provado.
`213002-044` e `213901-222` foram controles intermediários válidos, mas não foram
misturados à tabela final por diferenças de configuração da captura.

### Próximo incremento recomendado

Investigar cache/batching da camada de linhas CNC preservando cada passe, alpha,
endpoints, ordem visual e seleção, com ownership próprio para não cancelar o
preparo do corpo raster. Repetir FULL/controle final com mais passos e amostras
antes de afirmar ganho no 1% low. Se necessário, um protótipo LWJGL deve comparar
o mesmo desenho completo e manter testes de pixels, escala, lifecycle e picking;
trocar backend sem essa equivalência não é uma conclusão deste benchmark.

## Instrumentação

- `report.json`, schema 4: tempos CPU, fila, prontidão, pulsos UI, contadores por
  passe CNC, operações de imagem e timestamps monotônicos por fase. O campo
  `notMeasured` deste arquivo se refere ao medidor Java, não ao analisador externo.
- `presentmon.csv`: frames brutos do PID do host nativo, separados por swapchain.
- `presentmon-analysis.json`: FPS médio, 1% low, frame time p50/p95/p99/máximo e
  intervalos de pelo menos 50/100 ms, separados por fase e repetição.
- `capture.json` e logs: PID/sessão ETW exclusiva, versão por hash do executável,
  encerramento e configuração da captura. JFR continua opcional e separado.

`commandEmissionMs` por BODY/PASS_LINES é tempo CPU do caminho de submissão,
incluindo checks/preparo síncrono que ocorrerem nele, não tempo da GPU. DECORATIONS
só possui contagem, sem cronômetro separado; o resultado final marca seu tempo
como indisponível, não zero. Na bateria inicial esse campo antigo era zero e
deve ser ignorado para as decorações.

O host JNI calibra `System.nanoTime()` contra QueryPerformanceCounter; frequência,
offset e janela de calibração são registrados. O analisador reconstrói o instante
de display com CPUStartQPC + DisplayLatency, exclui frames não exibidos/NA, mantém
swapchains separadas e remove timestamps duplicados. Não soma FPS de janelas.
Fases são atribuídas pelo início CPU calibrado: uma apresentação pode atravessar
um limite de fase. A calibração não é uma medida de erro óptico do monitor.

FPS médio = 1000 / média dos intervalos entre displays. 1% low = 1000 / média do
1% mais lento desses intervalos, arredondando a contagem para cima. O JSON informa
o tamanho dessa cauda e sinaliza menos de 100 intervalos: em amostras curtas,
1% low pode representar somente um ou dois intervalos e varia bastante.
Quadros de seleção/apagar-desfazer são event-driven; não equivalem a animação
contínua. Não mede input-to-photon físico, frequência do monitor, utilização GPU
ou VRAM. Rastreamento de trabalho GPU e input foi desativado nesta captura de FPS
para reduzir o volume de eventos; **display tracking permanece ativo**.

Sem CSV/calibração/display suficiente, fase incompleta, erro do benchmark ou perda
de eventos ETW, a execução reprova a captura. FPS indisponível não vira zero nem
é substituído por Hz da UI. O parser opcional `--capture-log` também rejeita perda
de buffers/overflow; o script de execução sempre verifica o log.

## Controles e o desenho que desaparece

| Controle | O que muda propositalmente |
| --- | --- |
| FULL | Desenho normal, sem omissões |
| OMIT_PASS_LINES | Retira as linhas finas sobre o corpo das passadas CNC |
| Raster OMIT_DRAW | Calcula/publica/cacheia as imagens, mas não emite seu drawImage |
| Ambos | Separa a interação entre linhas finas e imagens |
| PixelWriter | Desenho completo usando a transferência alternativa já existente |
| OMIT_CNC | Piso diagnóstico: retira os passes CNC, não representa trabalho útil equivalente |
| Software FULL | Controle do pipeline SW, sem omissões |
| Geometry FULL | Pan/zoom e ciclos reais de apagar/desfazer, sem salvar a fonte |

**Raster OMIT_DRAW pode deixar só deslocamentos amarelos à distância e exibir
detalhes vetoriais quando aproxima.** É esperado nesse controle: a geometria não
foi apagada. O título da janela agora distingue `DESENHO COMPLETO` de
`DIAGNOSTICO: DESENHO OMITIDO` e mostra os modos CNC/raster. Não atribuir uma janela
desse controle ao comportamento normal do aplicativo.

`-Snapshots` salva `plot-far.png` e `plot-near.png` após todas as fases medidas,
aguardando prontidão e restaurando a câmera. São snapshots do nó JavaFX, não
fotografias do monitor nem fonte de FPS. Também podem conter dados privados.

## Reproduzir

Dependências: JDK/JAVA_HOME, g++ UCRT64, Python e o executável oficial
[PresentMon 2.6.0](https://github.com/GameTechDev/PresentMon/releases/tag/v2.6.0).
Nenhuma instalação global ou elevação é feita pelo script. Executável usado em
`target/fps-tools/PresentMon-2.6.0-x64.exe`, SHA-256 verificado com o asset oficial:
`b2a706bc6ad475749e3b7e3409263aa1e6906d45bdcf993f6dbc0f660188f1af`.
O script não baixa nem executa automaticamente uma versão arbitrária.

```powershell
# Na pasta flatcam-next; manter a janela visível e não interagir durante o teste.
$projetoTeste = 'D:\caminho\Projeto.FlatPrj'
$presentMonTeste = 'target/fps-tools/PresentMon-2.6.0-x64.exe'
.\benchmark-plot.ps1 -Native -Project $projetoTeste -ObjectName Cobre_Morto_Bottom_cnc `
    -PresentMon $presentMonTeste -Steps 120 -Repeats 3 -Jfr -Snapshots

# Série sequencial: sete controles GPU, SW opcional, Geometry opcional.
.\benchmark-plot-suite.ps1 -Project $projetoTeste -ObjectName Cobre_Morto_Bottom_cnc `
    -PresentMon $presentMonTeste -GeometryObjectName isolation_bottom `
    -IncludeSoftware -Steps 120 -Repeats 3 -Jfr

# Após o término: manifest aponta para os relatórios de cada execução.
..\.venv\Scripts\python.exe tools/summarize_plot_suite.py --suite target/plot-suite-<data>/suite.json
```

O último comando gera `summary.csv` e `summary.md` sem sobrescrever resultados;
cada repetição permanece separada. Runs reprovados não são usados na tabela.
A série confere hash preservado da fonte, runtime, revisão, tema, viewport/escala
e adaptador/pipeline efetivos dos controles GPU. Worktree dirty é registrado.
Não rodar em paralelo com build/oráculos/benchmark; manter energia, resolução,
driver, escala, monitor, corpus e carga externa constantes. `-Native` é uma
solicitação de GPU; o nome ativo deve ser confirmado no JSON.

A captura usa PID e uma sessão ETW com GUID. O início é confirmado por consulta
read-only à sessão, não por sleep ou stdout que pode ficar bufferizado. O término
usa Ctrl+C em console isolado, com verificação de que só o coletor e o helper
estão anexados, conferindo também o caminho executável e mantendo handle do
processo; recusa console compartilhado/identidade diferente. Falhas limpam somente processos e
sessão criados pela própria execução. Não encerra sessões de outros profilers.

## Verificação de fechamento

Instrumentação consolidada no commit `37d1d851`; medições foram feitas sobre
`207f83b9` com esses arquivos em worktree dirty, antes do commit, conforme metadata.

`mvnw.cmd -q verify` aprovado: 1921 registros em XML novos, **1903 testes
aprovados e 18 skips opcionais**, nenhuma falha/erro. Os dois testes adicionais
Java cobrem cadência UI e a sonda raster, além de extensões dos testes de CNC.
Comparação de pixels confirma que desativar a sonda restaura exatamente a imagem
normal. O modo FULL continua coberto pela comparação com o desenho sem probe.
Logs em `target/fps-tools/full-verify.log` e `focused-instrumentation-final.log`;
11 testes Python dos novos analisadores aprovados. São testes de software, não
validação física de máquina ou aprovação visual de cada quadro intermediário.

Host nativo final recompilado; argumentos inválidos retornam 2, identidade de
processo incorreta retorna 3, e término seguro do coletor em console isolado
retorna 0 com coletor exit 0 (`target/fps-tools/native-host-check.json`).
Consulta final sem sessões FlatCAMFX remanescentes; nenhum profiler externo foi
interrompido. Não foi entregue backend LWJGL nem declarado ganho em produção.

As definições dos campos e opções vêm da
[documentação oficial do PresentMon 2.6.0](https://github.com/GameTechDev/PresentMon/blob/v2.6.0/README-ConsoleApplication.md).
