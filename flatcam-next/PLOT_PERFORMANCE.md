# Diagnóstico de desempenho do Plot Area

## Benchmark visível do renderer — 2026-10-10

`benchmark-plot.ps1` executa pan/zoom determinísticos, seleção e publicação de
apagar/desfazer de Geometry no Plot de produção. Aceita `.FlatPrj`/`.fcnproj`,
filtro de objeto, tema, SW, repetições e JFR. Projeto fonte só é lido e tem hash
conferido. Gera JSON com percentis/amostras, pedidos obsoletos, heap/GC e metadata
do pipeline em pasta exclusiva de target; não mede FPS apresentado pela GPU.
Roteiro, fronteira Canvas extraída e baseline real/pública em
[PLOT_RENDER_BENCHMARK.md](PLOT_RENDER_BENCHMARK.md).

## Persistência de projetos densos — 2026-10-10

Com `flatcam.plot.profile=true` (também em `profile-plot.cmd`), o codec passa
a registrar `[PROJECT-PROFILE]`: construção JSON/WKT, codificação UTF-8,
compressão/gravação, descompressão, JSON e reconstrução dos objetos/WKT.
Os logs incluem o nome da thread. São custos de persistência, não FPS.

No painel real 2 x 2, com cinco CNC Jobs densos, a medição inicial isolada
foi: salvar 41,8 s; abrir incluindo previews/publicação 15,7 s. JFR mostrou
42% das amostras em `HC4.getMatches` (busca da compressão XZ). O preset foi
reduzido de 3 para 1, mantendo XZ, JSON, coordenadas, G-code e gravação
temporária/rename. Abrir identifica o cabeçalho XZ antes de tentar JSON,
evitando uma String UTF-8 inútil do arquivo binário inteiro.

Salvar passou a ~12,6–14,1 s nas primeiras repetições. O arquivo foi de
33.920.140 para ~35.805.512 bytes (+5,6%). Abrir ainda levou ~16–18 s;
não houve aceleração demonstrada dessa etapa. JSON/WKT e reconstrução de
previews continuam candidatos. Não é benchmark controlado nem ganho de GPU;
medições variam com aquecimento da JVM, JFR, cache de disco e carga do PC.
Reexecução completa do fluxo real: **30,06 s combinado** (salvar 13,57 s,
reabrir 16,48 s), contra a observação inicial de 62,41 s; arquivo 35.805.608
bytes. Não implica redução de ~50% em toda operação da aplicação.

Diagnóstico opcional somente leitura do projeto original, com cópia temporária
e sem Stage/preferências (pasta `flatcam-next`):

```powershell
.\mvnw.cmd -q -pl flatcam-fx -am test '-Dtest=MainProjectPerformanceTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dflatcam.native.project.fixture=C:/caminho/painel.fcnproj' '-Dflatcam.plot.profile=true' -l target/project-performance.log
```

O teste separa decode, encode/compress e abertura completa, confere geometria,
configurações e G-code preservados, e verifica o SHA-256 da origem. Não inclui
renderização de janela ou manipulação do mouse. O teste panelizado também
registra `saveMs`, `reopenMs` e `nativeBytes` separadamente em `flow-report.json`.

## Comparação da fluidez da interface: Python × FX

Há agora um **protocolo comum nos dois aplicativos**: um callback na thread
da interface é agendado a cada 16 ms e produz uma linha `[UI-FLUIDITY]` a cada
aproximadamente 10 s. Ele mede atrasos da interface inteira, inclusive pausas
que não aparecem no cronômetro de `redraw` do FX. Não mede FPS apresentado pela
GPU: Qt e JavaFX têm agendadores e pipelines gráficos diferentes, e contar
callbacks como quadros renderizados daria uma comparação enganosa. Compare
principalmente os atrasos grandes (`gaps50`/`gaps100`/`gaps250`) e os percentis
de cauda; uma diferença pequena em `p50_ms` pode ser apenas o agendador.

Abra **um aplicativo por vez**, com a mesma resolução/escala, tema, projeto,
camadas visíveis e zoom. No PowerShell, a partir da pasta `flatcam-next`, e com
o ambiente Python do FlatCAM ativado:

```powershell
cmd /c ".\profile-ui-python.cmd 2>&1" | Tee-Object -FilePath ".\ui-fluidity-python-$(Get-Date -Format 'yyyyMMdd-HHmmss').log"
```

Abra o projeto `.FlatPrj`, espere a carga terminar, mova o Plot Area por cerca
de 40 segundos, depois faça zoom in/out por cerca de 40 segundos. Feche o
Python antes do teste FX. Ainda na pasta `flatcam-next`:

```powershell
cmd /c ".\profile-ui.cmd 2>&1" | Tee-Object -FilePath ".\ui-fluidity-fx-$(Get-Date -Format 'yyyyMMdd-HHmmss').log"
```

Repita a mesma sequência, com as mesmas camadas habilitadas. Use ao menos três
janelas inteiras de 10 s durante cada ação e ignore as janelas de transição ou
espera ociosa. Os
scripts ativam a medição somente em seus processos; `FlatCAM.py` e `run.cmd`
continuam sem ela. O script Python procura primeiro um `.venv312` local e
depois o ambiente da pasta irmã `flatcam-8994`; só então usa o `python` do PATH.
Se necessário, defina `FLATCAM_PROFILE_PYTHON` como o caminho completo do
`python.exe` do ambiente correto antes de executar o script.

Exemplo de saída (os números são ilustrativos):

```text
[UI-FLUIDITY] app=fx window_s=10.0 samples=598 p50_ms=16.7 p95_ms=19.2 p99_ms=32.1 max_ms=118.0 gaps50=2 gaps100=1 gaps250=0
```

`p95_ms`/`p99_ms` são os atrasos de cauda; `max_ms` é a pior pausa observada.
`gaps50`, `gaps100` e `gaps250` contam intervalos de pelo menos 50, 100 e
250 ms (contagens cumulativas). Uma janela com `window_s` acima de 10 s pode
significar que a thread da interface ficou impedida de atender o timer. O
monitor desconsidera períodos em que a janela está oculta/minimizada. Deixe a
janela em primeiro plano durante a comparação. O medidor acrescenta apenas
um callback leve a cada 16 ms, mas, como todo profiler, tem pequeno custo.

Se o FX ainda parecer engasgar com `gaps50=0`, a causa pode estar na etapa de
renderização/apresentação da GPU, fora desta métrica de responsividade. Nesse
caso, guarde também o log de `profile-plot.cmd` (que agora inclui
`[UI-FLUIDITY]`) e descreva em qual ação a diferença aparece.

## Redesenho e camadas do FX

No PowerShell, execute `./profile-plot.cmd` a partir desta pasta, abra o projeto
problemático e reproduza a operação lenta (ativar Plot, enquadrar, zoom ou arrastar).
O terminal exibirá linhas `[PLOT-PROFILE]` apenas quando o diagnóstico estiver
ativado; `run.cmd` continua sem essa instrumentação.
Na inicialização, `prism.verbose` também informa qual pipeline gráfico o JavaFX
selecionou (acelerado ou software). Isso não força o uso de uma GPU dedicada.

Para salvar a saída e acompanhar ao vivo no PowerShell 5, faça a fusão de stdout
e stderr dentro do `cmd`; caso contrário, a mensagem normal do Java `Picked up
JAVA_TOOL_OPTIONS` aparece como `NativeCommandError`:

```powershell
cmd /c ".\profile-plot.cmd 2>&1" | Tee-Object -FilePath ".\plot-profile-$(Get-Date -Format 'yyyyMMdd-HHmmss').log"
```

Uma linha `slow redraw` mostra o tempo total de um redesenho síncrono na thread
JavaFX, dividido em `base` (fundo, grade e eixos), `layers` (desenho das
geometrias) e `other` (varredura de camadas, réguas, seleção e indicadores).
Até três camadas mais lentas são
listadas com nome e tempo. Por padrão, aparecem redesenhos acima de 50 ms;
a cada 100 redesenhos é emitido um resumo. Ao abrir um projeto, `project decode
(worker)` mede a leitura fora da thread JavaFX e `project restore (FX thread)`
mede a montagem da árvore e das camadas, incluindo seus redesenhos.
O sufixo `[LOD]` no nome de um CNC Job indica que o Plot está usando a
pré-visualização leve de linhas centrais; em zoom próximo, usa novamente a
geometria detalhada sem modificar o G-code ou a geometria de CAM.

Para registrar inclusive quadros rápidos, passe `0` como argumento (isso produz
muita saída e pode afetar a fluidez):

```powershell
./profile-plot.cmd 0
```

O padrão do script é sempre 50 ms, mesmo quando `JAVA_TOOL_OPTIONS` contém um
limite antigo. Outro limite pode ser passado como primeiro argumento.

Os tempos medem o trabalho síncrono de preparação/envio de comandos ao Canvas,
não o tempo de apresentação final pelo driver gráfico. Se houver travamento sem
linhas `slow redraw`, a causa pode estar em outra tarefa da thread JavaFX; nesse
caso, o JDK instalado permite capturar uma amostra sem alterar o código. Em outro
terminal, use `jcmd -l` para encontrar o PID de `org.flatcam.fx.MainApp` (não o
processo Maven). Substitua `12345` por esse PID:

```powershell
jcmd 12345 JFR.start name=flatcam_plot settings=profile duration=60s filename=flatcam-plot.jfr
# Reproduza o travamento durante esses 60 segundos.
jfr view hot-methods .\flatcam-plot.jfr
```

Se o `jcmd` indicar outro caminho para a gravação, passe esse caminho ao `jfr`.

Se a interface ficar presa por muito tempo, capture também a pilha da thread:

```powershell
jcmd 12345 Thread.print -l > plot-threads.txt
```

O arquivo `.jfr` é ignorado pelo Git. Antes de compartilhar gravações ou dumps,
revise-os: eles podem conter nomes de arquivos e caminhos locais. Para não
misturar outras opções Java nos testes seguintes, remova a variável criada no
PowerShell com `Remove-Item Env:JAVA_TOOL_OPTIONS`.

## LOD por densidade (camada 1)

O zoom adaptativo anterior só omitia vértices subpixel e cortava partes fora da tela; nada reduzia a **quantidade de
traços** enviados ao `Canvas`. Com milhares de segmentos minúsculos e juntos (uma Geometry muito densa vista de longe)
o custo estava na **execução** dos strokes pelo Prism, não na thread JavaFX: o `slow redraw` mostra poucos ms, mas o
pulso seguinte espera o render e a interface trava.

Agora, uma camada de traços (stroke-only, ou o caminho central de um CNC Job) com muitos segmentos **na área visível**
é desenhada como **uma imagem de densidade** (`DensityRaster`). Cada segmento vira um traço da **largura real** da
camada (1,5 px na Geometry): cada pixel recebe a área exata que o traço cobre nele, calculada na CPU em faixas
horizontais paralelas. Assim a imagem tem antialiasing como o vetor, e linhas mais próximas que um pixel se somam em
vez de deixar buracos. A cobertura `S` (em larguras de linha) vira alpha `a*S` até uma linha inteira e `1-(1-a)^S`
acima disso. A regra (`shouldRasterize`): `>= 15.000` segmentos visíveis, ou `>= 2.500` com comprimento médio abaixo
de ~1,5 px na tela; uma camada que já está no modo só sai abaixo de 60% desses limites (histerese). Traços largos
(> 2,5 px), camadas multicoloridas e polígonos preenchidos continuam vetoriais. Ao aproximar o zoom a densidade cai
e volta o vetor. A imagem de cada camada fica em cache enquanto a vista é idêntica. `[DENSE]` aparece junto do
nome da camada no `[PLOT-PROFILE]`. `-Dflatcam.plot.density=false` desliga o modo.

Histórico de defeitos corrigidos (todos achados comparando com o vetor numa Geometry do Paint "Standard" sobre a
placa real, ampliada): (1) a primeira versão desenhava linhas de 1 px sem antialiasing, deixando falhas onde passes
ficam a ~0,8 px um do outro; (2) o cálculo do intervalo de amostras de um segmento quase horizontal estourava o `int`
(`(int)` satura e o `- 1` seguinte dá a volta), descartando o segmento inteiro, o que dependia da divisão em faixas e
fazia sumir trechos dos anéis (o "buraco preto"); (3) amostrar o segmento em passos fixos dava peso errado a
segmentos curtos (um pixel recebia 1 amostra, o vizinho 2); agora a integração é exata ao longo do eixo principal;
(4) a `drawImage` do `Canvas` suaviza a imagem bilinearmente mesmo no desenho 1:1, o que borrava picos (pixel
totalmente coberto saia a ~82%) e enchia os vãos; agora `setImageSmoothing(false)`. Resultado contra o vetor, na
mesma cena: 0,17% dos pixels mudam bastante no Standard e 0,6% no Seed (ampliado), basicamente antialiasing de
diagonais (a cobertura de um traço inclinado é aproximada por faixas finas: erro de até meio pixel nas bordas das
diagonais de 45 graus).

O custo cresce com o comprimento total dos traços em pixels (cada pixel percorrido é uma amostra), não com a
quantidade de traços. Próximos passos possíveis: cache de interação (reaproveitar o quadro durante pan/zoom) e
rasterizar fora da thread JavaFX (hoje esses ~200-1.100 ms ocorrem na thread da interface).

### Camada 2: imagem de densidade fora da thread da interface

A rasterização da camada densa (~50 ms a ~1 s conforme o tamanho) não roda mais na thread JavaFX. `DenseRenderer`
mantém uma thread de fundo (`plot-density`) e uma fila "a mais recente vence" por camada:

- A camada pede a imagem da vista atual (`View`: geometria, escala, centro, tamanho, cor e largura do traço). O pedido
  só começa depois de **60 ms com a vista parada**, para um arraste contínuo não rasterizar cada vista intermediária;
  um pedido mais novo cancela o anterior (a rasterização consulta o cancelamento a cada ~64 partes).
- Até a imagem nova chegar, o `PlotAreaView` desenha a **imagem anterior movida e escalada** para a vista atual
  (`offset_novo - offset_antigo * k`, `k = escala_nova / escala_antiga`), recortada na área do plot e com interpolação
  bilinear, só como substituta. No `[PLOT-PROFILE]` a camada aparece como `[DENSE-STALE]` enquanto isso, e `[DENSE]` com
  a imagem exata. A primeira imagem de uma camada aparece em ~0,25 s (20 mil traços) a ~1,2 s (500 mil): a interface
  continua responsiva nesse intervalo.
- Quando a imagem exata fica pronta, ela substitui a provisória e a tela é redesenhada.
- `-Dflatcam.plot.density.async=false` volta a rasterizar na thread JavaFX (é o que os scripts de captura usam).

Medido (geometria sintética, pan de 12 quadros, uma vista nova por quadro):

| Traços x vértices | Quadro de pan na thread FX | Imagem exata depois que o pan para |
|---|---|---|
| 20.000 x 30 | ~0,5 ms (antes ~55 ms) | ~145 ms |
| 100.000 x 20 | ~1 ms (antes ~200 ms) | ~350 ms |
| 500.000 x 20 | ~2,5 ms (antes ~1.100 ms) | ~1,1 s |

A imagem provisória foi conferida contra a final (pan e zoom de ~43%): diferença média 0,00. Limitação: a imagem cobre
só a área visível quando foi feita, então num arraste longo a borda que entra na tela fica vazia na camada densa até o
movimento parar (as demais camadas, vetoriais, desenham normalmente). Próximo passo possível: uma margem em volta da
vista. Também seguem vetoriais os polígonos preenchidos (Gerbers muito densos) e traços mais largos que 2,5 px.

### Seleção do editor (realce azul) em geometria densa

O realce azul do editor (`editorHighlightGeometry`) e o contorno de referência tracejado eram desenhados por um caminho
separado (`drawEditorHighlight`), sem índice, **sem recorte pela vista** e sem LOD: selecionar uma região de uma Geometry
muito densa fazia o desenho vetorial completo a cada zoom ou pan. Agora esses dois overlays usam o mesmo caminho dos
layers (`drawOverlay`): índice por geometria (`overlayIndex`), recorte pela vista e, quando são só traços e têm milhares
de segmentos visíveis, o LOD por densidade assíncrono (a referência tracejada perde o tracejado nesse modo). Realces
preenchidos continuam vetoriais, agora com recorte pela vista. Pan com a Geometry densa como seleção azul, por quadro
completo (redesenho + render): 20.000 x 30: ~350 ms -> ~30 ms; 100.000 x 20: ~1.150 ms -> ~30 ms.

## Comparar GPU integrada e dedicada no Windows

Para testar a preferência automática de GPU de alto desempenho com executável
próprio do FX, veja [NATIVE_GPU.md](NATIVE_GPU.md) e use
`profile-plot-native.cmd`. As instruções abaixo continuam úteis para comparar
manualmente a integrada e a dedicada ao iniciar pelo `java.exe` compartilhado.
Nesse caso, defina `FLATCAM_FX_JAVA_ONLY=1` antes de `profile-plot.cmd`.

O JavaFX pode usar aceleração gráfica sem que isso garanta qual adaptador físico
está apresentando a janela. A mensagem `prism.verbose` confirma o pipeline
(por exemplo, D3D), **não** confirma Intel ou NVIDIA. Configure no Windows a
preferência de GPU para o `java.exe` do JDK que executa o FX em **Configurações →
Sistema → Tela → Elementos gráficos → Aplicativo de área de trabalho → Opções**.
Escolha **Alto desempenho** para testar a dedicada, salve e reinicie o FX.
Para a execução na integrada, escolha **Economia de energia** e reinicie de novo.
Essa escolha vale também para outros aplicativos que usem o mesmo `java.exe`;
reverta para **Deixar o Windows decidir** após o teste, se desejar.

No notebook com JDK Eclipse Adoptium 21 observado em setembro de 2026, o
executável é `C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin\java.exe`.
Se o JDK mudar, confirme o caminho do processo FX no Gerenciador de Tarefas.
Durante pan e zoom, confira em **Gerenciador de Tarefas → Detalhes → GPU engine**
se o `java.exe` aparece na GPU dedicada; `nvidia-smi pmon -c 1` é uma segunda
checagem quando o driver lista processos gráficos.

Faça duas execuções de `profile-plot.cmd`, uma por GPU, mantendo projeto, camadas,
zoom, resolução e sequência de movimentos iguais. Compare as linhas
`[UI-FLUIDITY]` e `[PLOT-PROFILE]`. O índice espacial do Plot Area reduz o
trabalho de CPU em zoom próximo, mas a GPU dedicada só ajuda na parte gráfica;
uma pausa longa de processamento de geometria pode persistir nas duas GPUs.

## Correções guiadas pela sessão GTX 1650 / i5-10300H

Na sessão `session-20261006-004149-898-17920-948750` (140 s), os 13 relatórios
de fluidez registraram 75 intervalos >= 50 ms, dos quais 20 >= 100 ms e três
>= 250 ms; o máximo foi 1.071 ms. O pipeline ativo era D3D/NVIDIA. A JFR mostrou
esperas da UI pelo renderer, rasterização Marlin e conversão/upload de pixels.
A maior pausa de GC foi 22,6 ms, portanto não explica sozinha o pico de um segundo.
A enumeração de fontes ao abrir Geometry apareceu na thread FX entre
00:43:34 e 00:43:35 UTC, forte candidata para esse pico. Esses dados são o
baseline anterior às correções abaixo, não um benchmark do código novo.

- Fontes do Editor Geometry: enumeração apenas ao expandir Texto vetorial,
  em worker, compartilhada entre editores. Fontes lógicas ficam disponíveis
  imediatamente; uma conclusão antiga não altera um editor reaberto nem cancela
  sua prévia de posicionamento.
- Índices grandes (>= 128 geometrias ou >= 4.096 pontos): construção em um
  worker, com um pedido pendente por binding e cancelamento de versões antigas.
  A UI mostra "Preparando visualização..." em vez de percorrer a geometria inteira
  sem índice. Partes imutáveis inalteradas reutilizam suas métricas após edição;
  o toolpath e o G-code não são simplificados nem modificados.
- Densidade: pedidos agendados supersedidos são removidos da fila. Remover uma
  camada/fechar projeto libera suas chaves, e fechar o app encerra os workers.
  Voltar a uma vista já em cache também cancela o pedido intermediário, inclusive
  quando sua publicação já foi enfileirada na UI; ele não substitui a vista atual.
  Os arrays prontos e imutáveis usam `PixelBuffer` para evitar a conversão/cópia
  pixel a pixel na thread FX. Isso **não** elimina uploads à GPU nem garante
  zero-copy em todo o pipeline, e cria uma imagem por quadro publicado.
- Ícones: dados de imagem reutilizados, com um `ImageView` independente em cada
  controle, evitando reabrir/decodificar o mesmo recurso a cada painel.

Para comparação controlada, existem overrides por processo:
`-Dflatcam.plot.index.async=false` (índices síncronos) e
`-Dflatcam.plot.density.pixelBuffer=false` (publicação anterior por PixelWriter).
`-Dflatcam.plot.density.async=false` também mantém índices síncronos para os
harnesses de captura que exigem uma imagem exata imediatamente. Nenhum override
altera preferências persistentes. Repetir `profile-plot.cmd` com o mesmo projeto,
camadas, resolução e ações antes de afirmar melhoria de latência/FPS.

Com profiling ligado, `index prepare (plot-index)` mede a preparação em fundo;
índices pequenos identificam a thread chamadora. `font enumeration (worker)`
mede a enumeração lazy de fontes. Ambos são tempos de parede dessas etapas,
não tempos da GPU ou FPS apresentado.

O desenho vetorial de Gerbers preenchidos e traços largos continua no Canvas.
Estas correções não entregam o backend OpenGL proposto e não removem todas as
esperas de renderização observadas; esse passo precisa de medição própria.

### Verificação posterior — 2026-10-05 (horário local)

Suíte normal: 1.087 registrados, 1.075 aprovados, 12 opcionais ignorados,
zero falhas/erros. Probes nativos offscreen D3D/GTX 1650 e software passaram,
incluindo os pixels do novo PixelBuffer. Executável recompilado.

A sessão `session-20261006-011706-269-24196-3065109` do usuário confirmou os
índices grandes no worker (máximo 35,7 ms). Em 80 segundos monitorados houve
62 intervalos >= 50 ms, 16 >= 100 ms e um >= 250 ms; máximo 306,8 ms antes de
carregar o projeto e 181,8 ms depois. A anterior tinha 130 segundos monitorados:
75/20/3 intervalos nesses limiares, máximo 1.071 ms. Os intervalos >= 50 ms por
minuto foram 34,6 antes e 46,5 agora; duração/ações diferentes impedem atribuir
uma melhoria geral às correções. O carregamento de fontes não foi acionado
nesta sessão. A JFR ainda mostra esperas FX pelo renderer de 125–159 ms,
rasterização Marlin e conversão de imagens; maior pausa GC 19,4 ms.
Não foi alterado o projeto privado nem reexecutado um benchmark controlado.

### Robustez do worker de índices — 2026-10-06

Uma falha `Error` na preparação marca a versão como falha antes de notificar
a UI e continua sendo propagada ao executor/handler de diagnóstico. O estado
de drenagem é liberado em `finally`; pedidos já enfileirados são reagendados,
e novos pedidos não ficam presos em "Preparando visualização...". A versão
que falhou não é tentada novamente a cada quadro: é necessário alterar/remover
seu binding. Notificações antigas respeitam substituição, remoção e fechamento.

Regressões incluem `AssertionError` injetado com fila vazia/ocupada e worker
real, preservando a propagação da falha e a preparação da camada seguinte.
Isso não garante recuperação da JVM em falta de memória ou outra falha fatal,
nem mede melhoria de FPS/latência; os limites do renderer acima permanecem.

### Continuidade visual durante zoom e edição — 2026-10-06

O desaparecimento temporário foi reproduzido offscreen: uma edição descartava
os pixels e deixava a camada sem desenho enquanto preparava o novo índice;
alternar densidade → vetor → densidade também descartava a imagem anterior.

- Atualização de Geometry mantém o último índice já preparado como **prévia
  somente visual** enquanto prepara a nova versão. Não percorre a geometria
  nova sem índice na thread FX. Seleção, hit testing e operações continuam
  usando a geometria atual, não a prévia. A imagem anterior permanece durante
  o refinamento de densidade; o indicador também cobre essa etapa assíncrona.
- Sair de densidade para vetor suspende o pedido, sem eliminar a imagem em
  cache. Além do último quadro, cada binding guarda no máximo um quadro de
  visão ampla. Retornar a uma câmera exata em cache é imediato e cancela
  pedidos intermediários; uma vista ampla evita mostrar só o recorte do zoom
  anterior enquanto o novo quadro é calculado.
- Pixels publicados são imutáveis também no override PixelWriter: atualizar
  uma imagem não sobrescreve a prévia ampla. Sem mudança de câmera, uma prévia
  de edição é desenhada 1:1 sem smoothing, evitando que os traços desbotem.
- Geometria vazia, remoção, clear e dispose descartam os caches apropriados;
  camadas ocultas não exibem prévias. Publicações supersedidas não recuperam
  uma camada removida nem substituem a edição mais recente.

O cache amplo tem custo de até uma imagem adicional por binding (proporcional
ao tamanho do viewport); é liberado junto com a camada/projeto/app. Não é um
cache ilimitado de zooms. Câmeras inéditas e áreas fora das imagens armazenadas
ainda dependem de cálculo em fundo. Os 60 ms de settle continuam evitando
rasterizar cada evento intermediário. Isto não acelera o NCC, não altera
geometria/G-code e não comprova ganho de FPS: validação de fluidez no projeto
real continua sendo uma etapa separada. O usuário confirmou que o ajuste
funcionou no teste manual; não houve novo benchmark controlado de FPS/latência.

Oito regressões novas incluem pixels durante excluir/desfazer, troca vetor/
densidade nos quatro temas, recorte após zoom concluído, edições rápidas,
geometria vazia, ocultação/remoção/clear e cancelamento com cache preservado.
O teste visual também é executado com `flatcam.plot.density.pixelBuffer=false`.
