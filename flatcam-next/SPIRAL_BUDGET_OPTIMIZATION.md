# Espiral densa: orçamento e custo de cálculo — 2026-11-14

Retomada solicitada pelo usuário após as correções de importação de Yago.
Complementa `SPIRAL_POCKETS.md`; não altera a versão Beta nem os parâmetros.

## Problema e solução

Na Audio Watermark, os offsets sucessivos de arcos muito amostrados aumentavam
o número de vértices até esgotar um milhão de amostras. A fresa inteira era
recalculada pela radial, com mais deslocamentos rápidos e retrações.

Agora os níveis densos usam offsets calculados a partir do componente original
**já seguro para o centro da fresa**. Cada resultado fica contido no seu pai
real, inclusive após a divisão em bolsões. Ramos pequenos (até 2.048 pontos no
offset local) e passes terminais continuam com o cálculo local. Um cache por
planejamento guarda no máximo oito níveis; chaves e distâncias não são
arredondadas. Os complementos locais de cobertura continuam presentes.

Sem simplificar a geometria, diminuir o raio físico ou mudar tolerâncias.
Limites de pontos, nós e profundidade e o retry radial atômico continuam iguais.
Grades sintéticas maiores, de 12×9 e 16×12 ilhas arredondadas, ainda atingem o
limite e usam esse retry. Não se promete resolver qualquer entrada densa.

## Medição isolada de geração

Baseline congelada em `d5cab46a`, comparada às classes integradas. Core Ultra 7
155H, 22 processadores lógicos, 31,4 GiB de RAM, Java 25 e heap máximo de 3 GiB.
Três pares de JVMs novas, alternando a ordem: antes/depois, depois/antes,
antes/depois. Três repetições por placa/processo; a primeira fica registrada,
mas a mediana usa as seis amostras seguintes por variante. Entrada, exportação,
análise de cobertura e escrita ficam fora do cronômetro. Nenhum outro teste ou
probe do projeto rodava durante a medição; temperatura e carga de fundo do
Windows não foram controladas.

Paint, fresa 0,4 mm, overlap 40%, Connect/Contour, sem Rest:

| Placa | Antes, mediana | Depois, mediana | Redução observada |
|---|---:|---:|---:|
| Audio Watermark | 4.785,1 ms | 2.588,7 ms | 45,9% |
| Breadboard Power Supply | 143,4 ms | 124,4 ms | 13,3% |
| Modxo Adapter | 110,5 ms | 68,1 ms | 38,4% |
| Single-sided 5V | 170,2 ms | 98,7 ms | 42,0% |

As faixas variam e se sobrepõem em alguns controles; não são garantias para
outros PCs/placas. CPU do processo e todas as primeiras chamadas constam no
JSON. Não foram medidos FPS, pico de RAM ou tempo real de usinagem.

## Trajetória e cobertura

Na Audio, o candidato selecionado produziu 308 trajetos antes da exportação,
sem retry radial. O programa GRBL relido passou de 3.170 para 311 comandos
`G0 Z`, e de 86,410 para 40,423 minutos **estimados**, sem aceleração ou dinâmica
da máquina. O tamanho da geometria subiu de 141.281 pontos radiais para 522.224
adaptativos: isso pode custar mais armazenamento, renderização e transmissão.

As oito placas públicas terminaram sem retry radial e com núcleo estável
residual zero na classificação do harness. Essa campanha usou o protótipo H
selecionado, com o mesmo algoritmo integrado; a integração nomeou as constantes
e acrescentou comentários. Uma repetição Audio com as classes finais gerou
WKT e GRBL idênticos ao protótipo, conferidos por SHA-256. Os testes e os dois
casos Rest também usaram as classes finais. Fonte sem mutação, exportação GRBL
real relida e diâmetro nominal
preservados. O alvo do corpus é a caixa do cobre menos cobre, não o Outline
físico da placa.

Foram preservados resíduos brutos e sensíveis à precisão. Núcleo estável zero
não significa sobra bruta zero. A baseline Audio já tinha um núcleo residual
de aproximadamente 0,0000205 mm², registrado como limitação anterior. Nenhum
limiar foi relaxado para aprovar o candidato.

Rest com fresas 0,8/0,4 mm, Paint MM e NCC IN, foi conferido por ferramenta:
antes a segunda fresa caía na radial; depois ambas terminaram adaptativas.
Núcleos residuais estáveis da geometria e do GRBL ficaram zerados nos dois
casos, com resíduos brutos ainda publicados. Tempos totais dessas auditorias
incluem cobertura/exportação e carga concorrente; não são benchmark isolado.

## Regressões e evidências

- Novo `SpiralDenseBudgetTest`: quatro falhas na baseline (retry radial) e quatro
  aprovações depois, MM/IN × Paint/NCC, com clearance contínuo e testemunhas.
- Controles de distância: 32 casos, zero falhas/erros, incluindo Rest/Connect/
  Contour e duas fresas.
- Build completo: 2.672 casos, 2.652 aprovados, 20 opcionais ignorados; zero
  falhas/erros. Inclui orçamento, cancelamento, .198 exato, cobertura e prévia.
- `validation/spiral-budget-optimization-20261114.json`: corpus, Rest, hashes,
  baseline e limites da investigação.
- `validation/spiral-budget-generation-20261114.json`: medições brutas e resumo.
- `validation/spiral-budget-distance-20261114.json`: controles por fresa.

Reprodução a partir de `flatcam-next`, após compilar e preparar dependências:

```powershell
& "$env:JAVA_HOME/bin/java.exe" -Xmx3g '-Dflatcam.spiral.boards=breadboard-power-supply|modxo-adapter|single-sided-5v|audio-watermark' --class-path 'flatcam-cam/target/classes;flatcam-fx/target/dependency/*' tools/SpiralGenerationBenchmark.java samples/community/validation/fx-import.json target/spiral-generation-new.json current
& "$env:JAVA_HOME/bin/java.exe" -Xmx3g '-Dflatcam.spiral.methods=SPIRAL' '-Dflatcam.spiral.boards=.*' --class-path 'flatcam-cam/target/classes;flatcam-fx/target/dependency/*' tools/SpiralCommunityProbe.java samples/community/validation/fx-import.json target/spiral-corpus-new
.\tools\audit-cam-related.ps1 -Lines -Method SPIRAL -ClassOverrides .\flatcam-cam\target\classes -OutputDirectory target/spiral-distance-new
```

Usar saídas novas. Para a comparação, compilar e congelar as classes CAM da
baseline em separado e mudar somente esse primeiro diretório do classpath;
manter a mesma JVM/heap e alternar a ordem das rodadas.
