# Diagnóstico de desempenho do Plot Area

No PowerShell, execute `./profile-plot.cmd` a partir desta pasta, abra o projeto
problemático e reproduza a operação lenta (ativar Plot, enquadrar, zoom ou arrastar).
O terminal exibirá linhas `[PLOT-PROFILE]` apenas quando o diagnóstico estiver
ativado; `run.cmd` continua sem essa instrumentação.

Uma linha `slow redraw` mostra o tempo total de um redesenho síncrono na thread
JavaFX, dividido em `base` (fundo, grade e eixos), `layers` (desenho das
geometrias) e `other` (varredura de camadas, réguas, seleção e indicadores).
Até três camadas mais lentas são
listadas com nome e tempo. Por padrão, aparecem redesenhos acima de 50 ms;
a cada 100 redesenhos é emitido um resumo. Ao abrir um projeto, `project decode
(worker)` mede a leitura fora da thread JavaFX e `project restore (FX thread)`
mede a montagem da árvore e das camadas, incluindo seus redesenhos.

Para registrar inclusive quadros rápidos, altere o limite antes de executar:

```powershell
$env:JAVA_TOOL_OPTIONS = '-Dflatcam.plot.profile.slowMs=0'
./profile-plot.cmd
```

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
