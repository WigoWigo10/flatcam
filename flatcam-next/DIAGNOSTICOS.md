# Diagnósticos locais do FlatCAM FX

Implementado em 2026-10-05. Não envia arquivos, não captura telas e não altera
o projeto aberto. Os diagnósticos ajudam a investigar falhas; não evitam crashes.

## Uso normal

```powershell
.\run.cmd
```

Os diagnósticos ficam ativos por padrão. O terminal informa
`[DIAGNOSTICS] session=...`. No app, **Ajuda > Diagnosticos** oferece:

- **Abrir pasta desta sessao**: abre o gerenciador de arquivos fora da thread FX.
- **Capturar estado agora**: solicita estado do sistema, threads e checkpoint
  JFR em segundo plano. Compartilha o limite de cinco incidentes automáticos.

No Windows, a localização padrão é `%LOCALAPPDATA%\FlatCAMFX\diagnostics`.
Cada execução tem um diretório `session-...` único, com data UTC e PID.
Sem LOCALAPPDATA, o launcher Java usa `~/.flatcam-fx/diagnostics`; o nativo usa
a pasta temporária do Windows. Não é necessário redirecionar o terminal para
preservar os logs Java e a saída stdout/stderr posterior ao bootstrap.

## Arquivos

| Arquivo | Conteúdo |
| --- | --- |
| `app-0.log` e rotações | System.Logger/JUL, jobs, exceções e stack traces |
| `console-0.log` e rotações | stdout/stderr, incluindo profiling do Plot quando habilitado |
| `system-start.json` | Versões app/build/Java/JavaFX, OS, arquitetura, CPU lógica, heap, RAM física, PID e configuração gráfica solicitada |
| `system-latest.json` | Amostra atualizada aproximadamente a cada 60 segundos |
| `system-final.json` | Estado final em fechamento normal ou falha de lançamento |
| `incident-N-system.json` / `incident-N-threads.txt` | Estado e threads de uma ocorrência; o motivo fica no dump |
| `recent.jfr` | Checkpoint atualizado em incidentes e aproximadamente a cada 60 segundos |
| `session.jfr` | Gravação salva no encerramento normal/shutdown hook |
| `graphics-adapters.txt` | Adaptadores enumerados pelo Windows, somente no launcher nativo |
| `hs_err_pid*.log` | Relatório fatal da JVM, quando ela consegue produzi-lo |
| `heap.hprof` | Heap dump em esgotamento do heap Java, somente quando habilitado |

O launcher nativo configura o relatório fatal antes de criar a JVM e coloca
seu repositório JFR temporário na sessão. O launcher Maven/Java configura
`flatcam-fx/target/hs_err_pid%p.log`: nesse caminho o relatório fatal fica fora
da sessão, inclusive para erros anteriores ao bootstrap Java. O campo
`fatalErrorFile` dos JSON registra a configuração efetiva.

O modelo de GPU enumerado não prova qual adaptador o Prism escolheu. Para
registrar a escolha e detalhes do driver na saída do Prism:

```powershell
.\run-native.cmd --verbose-gpu
```

Não há medição de uso/VRAM da GPU nesta implementação. CPU/RAM são amostras,
não uma captura atômica de todo o PC; valores de carga negativos significam
que a JVM não conseguiu medir aquele indicador.

### Identificação na janela Sobre

**Ajuda > Sobre > Sistema** também apresenta modelo de CPU, RAM física e heap
Java separados, threads e identificação do renderizador ativo. A coleta ocorre
em segundo plano ao abrir o diálogo; **Copiar informações** copia o texto exibido.
No Windows/Direct3D, a placa e o driver vêm do adaptador Prism da janela
principal. No pipeline SW, mostra **Compatibilidade por software (CPU)**.
Não deduz a escolha da primeira placa enumerada nem da ordem solicitada.
Os JSON de diagnóstico continuam registrando apenas a configuração gráfica
solicitada; a identificação ativa, nesta entrega, é mostrada no Sobre e no probe.

A ponte é opcional e isolada em `GraphicsRuntimeInfo`, validada em JavaFX
25.0.4, com exports/opens específicos nos launchers. Usa internals documentados
no código do [D3DPipeline](https://github.com/openjdk/jfx25u/blob/master/modules/javafx.graphics/src/main/java/com/sun/prism/d3d/D3DPipeline.java)
e [D3DDriverInformation](https://github.com/openjdk/jfx25u/blob/master/modules/javafx.graphics/src/main/java/com/sun/prism/d3d/D3DDriverInformation.java).
Revalidar após atualizar JavaFX; se acesso/informação falhar ou o renderer não
responder, o diálogo informa indisponibilidade. Outros backends mostram o
pipeline, mas a identificação da placa neles não está implementada. Isso não
mede VRAM/uso da GPU nem muda a escolha do driver ou acelera o CAM.

## Travamentos e limites

Um watchdog daemon envia no máximo um callback pendente para a thread FX.
Após aproximadamente 5–6 segundos sem resposta, solicita um relatório em
outro worker. Emite uma captura por interrupção; depois que a UI responde,
pode detectar outra. Isso indica atraso de resposta, não comprova deadlock.
Suspender o PC, depuração ou sobrecarga do sistema também podem causar avisos.

- Cada conjunto de logs rotaciona em quatro arquivos de aproximadamente 2 MiB:
  cerca de 16 MiB para app + console, com possível excedente de um registro.
- JFR usa a configuração `default`, com retenção de 10 minutos / 64 MiB.
  São limites de retenção do JFR, não uma quota rígida da pasta inteira:
  checkpoints, gravação final e repositório ocupam espaço adicional.
- Até cinco solicitações de incidente por sessão; fila de escrita limitada a
  duas tarefas pendentes. Em sobrecarga, uma solicitação pode ser descartada.
- Dumps limitados a 256 threads de plataforma e 64 frames por thread; threads
  virtuais não são enumeradas por ThreadMXBean. O app usa workers de plataforma.
- Sessões antigas **não são apagadas automaticamente**. Revise/remova as pastas
  que não precisar mais, depois de encerrar o app. Heap dumps não têm quota.
- A maioria das falhas de captura é best-effort e não impede abrir o app.
  O bootstrap acrescenta I/O/inicialização JFR antes de mostrar a janela; o
  impacto no uso com projetos grandes ainda exige medição no equipamento real.

Uma queda de energia, encerramento forçado ou falha nativa severa pode impedir
o fechamento/salvamento final. Nesse caso, `recent.jfr` preserva no máximo o
último checkpoint concluído; buffers posteriores podem ser perdidos. A JVM
também pode gerar uma gravação de emergência, mas isso não é garantido.
Sem `system-final.json`, o encerramento pode ter sido abrupto ou a escrita pode
ter falhado: não classificar automaticamente qualquer ausência como crash.

## Configuração

Para desativar os diagnósticos nessa execução:

```powershell
.\run.cmd --no-diagnostics
```

Também existe `FLATCAM_FX_DIAGNOSTICS=false`, válida para ambos os launchers.
Isso desativa a instrumentação do app, não o mecanismo fatal padrão da JVM:
ela ainda pode produzir um hs_err em uma falha nativa.
`FLATCAM_FX_DIAGNOSTICS_DIR` altera a raiz das novas sessões. Variáveis definidas
no terminal valem para os processos iniciados dele; remova-as quando não quiser
mais o comportamento. Se usar Maven diretamente, passe
`"-Dflatcam.args=--no-diagnostics"` ao goal JavaFX.

O heap dump fica desligado por padrão, salvo flags explícitas da própria JVM.
Para habilitar o tratamento opcional do app:

```powershell
$diagnosticPreviousOptions = $env:JAVA_TOOL_OPTIONS
try {
    $env:JAVA_TOOL_OPTIONS = ($diagnosticPreviousOptions + ' -Dflatcam.diagnostics.heapDump=true').Trim()
    .\run.cmd
} finally {
    if ($null -eq $diagnosticPreviousOptions) { Remove-Item Env:JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue }
    else { $env:JAVA_TOOL_OPTIONS = $diagnosticPreviousOptions }
}
```

Isso não provoca falta de memória: só habilita o dump se ela ocorrer. Um dump
pode ocupar gigabytes, pausar a JVM enquanto é gravado e conter código,
geometria, nomes e outros dados do projeto. Logs/JFR também podem incluir
caminhos locais, nomes de threads, bibliotecas e mensagens com dados sensíveis.
A coleta inicial indiscriminada de variáveis de ambiente/propriedades foi
desabilitada no JFR, mas isso **não torna o conjunto anônimo**. Revise antes
de compartilhar; não existe envio automático.

## Verificação segura

Depois de compilar, os probes criam uma sessão de teste sem abrir o projeto:

```powershell
.\target\native\FlatCAMFX.exe --diagnostics-probe
.\mvnw.cmd -q -pl flatcam-fx "-Dflatcam.args=--diagnostics-probe" org.openjfx:javafx-maven-plugin:0.0.8:run
```

Uma exceção de teste intencional aparece no log; o probe retorna sucesso após
registrá-la e fechar a sessão. Não provoca crash nativo nem falta de memória.
Os probes de renderização `--probe` / `--probe --software` continuam separados
e não criam sessões de diagnóstico.

Os testes cobrem rotação, UTF-8, limite/fila, restauração de streams/handlers,
locks, JFR legível, shutdown hook em JVM filha, callback FX com exceção e uma
thread FX realmente bloqueada enquanto o watchdog coleta. O teste da UI usa
o toolkit de testes, sem interagir com o app do usuário.
Ainda validar manualmente os comandos do menu e a fluidez no projeto real.
Nenhum crash fatal real ou OutOfMemoryError foi induzido: os probes verificam
configuração/captura segura, não garantem salvamento em todas as falhas.

Referências: [JFR Recording](https://docs.oracle.com/en/java/javase/25/docs/api/jdk.jfr/jdk/jfr/Recording.html),
[relatório fatal](https://docs.oracle.com/en/java/javase/25/troubleshoot/location-fatal-error-log.html)
e [ThreadMXBean](https://docs.oracle.com/en/java/javase/25/docs/api/java.management/java/lang/management/ThreadMXBean.html).
