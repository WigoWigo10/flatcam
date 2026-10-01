# Diagnóstico de desempenho do Plot Area

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

Histórico: a primeira versão desenhava linhas de 1 px sem antialiasing, uma amostra por pixel. Em isolação com 40
passes sobre a placa real isso deixava falhas e bordas serrilhadas onde o vetor é sólido (passes a ~0,8 px um do
outro); a cobertura de área corrigiu. Contra o vetor, na mesma cena, só 0,14% dos pixels mudam bastante (o contorno
de 1 px de antialiasing nas bordas).

Medido com uma Geometry sintética (linhas onduladas, tudo visível, 1000x700, panning: vista nova a cada quadro):

| Traços x vértices | Vetor (Prism) | Densidade |
|---|---|---|
| 20.000 x 30 | ~260 ms | ~45 ms |
| 100.000 x 20 | ~745 ms | ~155 ms |
| 500.000 x 20 | ~4.400 ms | ~620 ms |

O custo cresce com o comprimento total dos traços em pixels (cada pixel percorrido é uma amostra), não com a
quantidade de traços. Próximos passos possíveis: cache de interação (reaproveitar o quadro durante pan/zoom) e
rasterizar fora da thread JavaFX (hoje esses ~150-600 ms ocorrem na thread da interface).

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
