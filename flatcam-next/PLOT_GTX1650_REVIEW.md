# Plot no notebook GTX 1650 — 2026-10-26

Máquina de referência: **PC-GTX1650-82CG**. Especificações, condições registradas
e roteiro para repetir as cargas em outro computador:
[BENCHMARK_PC_GTX1650.md](BENCHMARK_PC_GTX1650.md).

Quarta etapa sobre `1c58cd71`. Não é migração LWJGL nem novo renderer de produção.
As rodadas registraram worktree dirty dessa base, com as mudanças do benchmark
desta entrega; consultar o commit de fechamento no histórico.

## Benchmark corrigido e host nativo

O primeiro ensaio falhou antes de medir: a proteção de projeto não salvo recusava
a fixture inicial descartável do próprio MainCamFlowTest.Session. O benchmark
agora a substitui explicitamente com `openProject(source, true)`; não descarta
sessão interativa, não salva o projeto de entrada e verifica seu hash ao terminar.

O `java.exe` do Surefire selecionou Intel UHD. Adicionado `benchmark-plot.ps1 -Native`:
compila o mesmo teste, lê seu classpath do Surefire e executa suas mesmas asserções
num host JNI de teste com NvOptimusEnablement/AmdPowerXpressRequestHighPerformance,
como o launcher de produção. Usa JAVA_HOME e g++ UCRT64, sem editar registro,
preferências ou launcher de produção. É solicitação ao driver, não garantia:
**confirmar sempre `graphics.adapter` e `graphics.pipeline` no relatório**.

`-DisableWideDensity` conserva corpos largos no Canvas, sem omitir elementos.
`launchMode` e `wideDensityEnabled` ficam registrados. C++/entry Java vivem em
tools/src-test, não entram no runtime do aplicativo. Compilados e classpath ficam
em target. Falhas Java retornam 1; argumentos inválidos retornam 2, ambos
conferidos. Corrigida a captura de stderr no PowerShell 5 para não interpretar
log normal da JVM como falha; status/exit code continuam obrigatórios.

## Ambiente e condições

Windows 11, i5-10300H/8 lógicos, 23,87 GiB RAM, Java 25.0.4.1, JavaFX 25.0.4.
GTX 1650, driver 32.0.16.1062, pipeline D3D confirmado nas três rodadas GPU.
Controle SWPipeline sem GPU confirmado na rodada software. Scene 1280×800
lógicos, escala 1,25×1,25; ICE_DARK, 120 passos, três repetições por configuração,
JFR profile em todas. Execuções sequenciais, sem build/oráculo concorrente.
Mesmo projeto autorizado, CNC denso e Geometry, com hashes finais intactos.

Uma sessão por configuração, não experimento estatístico intercalado. O projeto
atual tem 18 objetos; as medições históricas Intel Arc não têm a mesma escala,
hardware e estado de projeto. Não calcular ganho entre esses equipamentos.

## CNC: zoom aquecido

Repetições 2 / 3, milissegundos. Fila é pedido → atendimento FX; pulso não é frame
efetivamente apresentado pela GPU/compositor.

| Configuração | Fila p95 | Pulso p95 | Pulso máximo | Pulsos ≥50 ms |
| --- | --- | --- | --- | --- |
| GTX + imagem larga atual | 24,52 / 21,23 | 46,21 / 32,52 | 48,60 / 48,53 | 0 / 0 |
| GTX + corpos largos no Canvas | 59,25 / 72,18 | 80,47 / 80,99 | 95,14 / 111,77 | 38 / 42 |
| Software + imagem larga atual | 161,16 / 166,14 | 176,62 / 191,92 | 192,30 / 208,09 | 119 / 119 |

Primeiro zoom, mantido separado: máximo 240,14 ms no atual, 832,50 ms no Canvas
e 447,54 ms no software. Não esconder custo frio/JIT nem confundi-lo com interação
sustentada. Pan aquecido atual: fila p95 8,57 / 0,29 ms, máximos de pulso
32,89 / 32,13 ms; software: 153,36 / 147,24 ms de fila.

No último zoom GPU atual, o contador registrou BODY 121 chamadas em imagem e
121 vetoriais; PASS_LINES 100 vetoriais. São chamadas/grupos por fase, não número
de paths ou draw calls da GPU. Nenhum corpo/linha foi omitido.

## Geometry: apagar/desfazer

Quatro ciclos por repetição: **24 ações**, identidade da geometria original
restaurada após undo. Pedido → comandos prontos p95: 107,69 / 93,32 / 108,44 ms.
Fila p95: 0,14 / 0,23 / 0,14 ms. Pulso máximo: 29,56 / 22,46 / 31,52 ms.
Inclui preparação assíncrona/estabilização, não cliques físicos ou toda a sidebar.
O CNC isolado não tinha Geometry visível e marcou edição como não exercitada;
a rodada separada é que verifica esse caminho.

## Sinal de gargalo e decisão

JFR no zoom atual: repetições 2/3 têm 93/77 amostras CPU, 72/50 com Marlin,
85/69 com Prism e zero sobreposição de pausas GC registradas. No último zoom,
71/77 amostras são QuantumRenderer-0. Categorias se sobrepõem; não são percentuais
de tempo GPU ou prova causal de um comando. O aquecimento teve GC observado.

Manter a imagem de corpos largos: o controle Canvas foi pior nesta bateria.
Não trocar automaticamente por SW nem prometer que selecionar a GTX elimina
Marlin/Prism CPU. Próxima investigação pequena: isolar as linhas de passadas e
apresentação com os controles existentes, antes de escolher outra implementação
de renderer. Esta rodada não justifica trocar JTS ou iniciar Vulkan/LWJGL inteiro.

## Evidências e reprodução

Quatro COMPLETED, todos `sourceUnchanged=true`:

- `target/plot-benchmark-20261026-210210-298`: GPU atual.
- `target/plot-benchmark-20261026-210259-914`: GPU, largos Canvas.
- `target/plot-benchmark-20261026-210341-904`: software.
- `target/plot-benchmark-20261026-210620-834`: Geometry GPU.

Preservados, **não aprovados**: tentativa `205857-352` (proteção de fixture)
e `210119-472` (PowerShell interrompeu captura de stderr; sem relatório final).
Geometria, código de placa, JFR e relatórios privados permanecem ignorados em target.

```powershell
.\benchmark-plot.ps1 -Project 'C:/pasta/projeto.FlatPrj' -ObjectName Cobre_Morto_Bottom_cnc -Steps 120 -Repeats 3 -Jfr -Native
# Repetir em série com -DisableWideDensity e depois -Software.
.\benchmark-plot.ps1 -Project 'C:/pasta/projeto.FlatPrj' -ObjectName isolation_bottom -Steps 120 -Repeats 3 -Jfr -Native
```

Não minimizar/interagir com a janela durante o ensaio. O benchmark mede fila,
comandos, preparo, pulsos e amostragem CPU; não FPS GPU, latência física do mouse,
CAM/tempo de save, VRAM real, todas as placas ou segurança de usinagem.

Fechamento: `mvnw.cmd -q verify`, **1919 registros atuais / 1901 aprovações /
18 skips opcionais / zero falhas ou erros**. Log
`target/session-review-20261026/full-final-verify.log`. Treze testes auxiliares
Python aprovados. Ensaios opt-in acima são separados dessa contagem JUnit.
Executável de produção recompilado; probes `--probe --no-diagnostics` em
D3D/GTX 1650 e `--probe --software --no-diagnostics` passaram. São verificações
offscreen de inicialização/renderização, distintas dos benchmarks visíveis.
