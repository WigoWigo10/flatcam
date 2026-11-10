# Lines por regiões — protótipo opt-in, 2026-11-10

## Refinamento do Connect e comparação CNC real

O fluxo atual começa pelas ligações retas seguras. Depois integra os segmentos
solicitados de raster e acabamento num grafo. Caminhadas curtas sobre arestas
de acabamento já cortadas (estritamente menos de dez diâmetros) reduzem vértices
ímpares; conexões virtuais restantes significam levantar a fresa, nunca um corte
atravessando cobre. O percurso usa as prioridades das células e conserva as
ligações seguras restantes. Não posterga todos os anéis para o final.

O grafo pode subdividir segmentos em interseções. Por isso, a verificação nova
confere **cobertura contínua dos intervalos requeridos**, dentro da tolerância
numérica declarada, em vez de exigir a mesma lista de arestas sem subdivisão.
O auditor é protegido por um caso em que vértices/pontos médios coincidem mas
seis milímetros de um segmento estão ausentes: apenas amostrar pontos não basta.
Nenhuma coordenada é arredondada deliberadamente para construir o grafo.

**16/16 programas comunitários** (oito placas × NCC/Paint, Contour/Connect):
Lines comum exportado com **NEAREST** versus regional com **SOURCE e direção
preservada**. Todos mantêm os intervalos de corte requeridos e ficam dentro da
precisão de exportação na ida/volta pelo parser. As retrações diminuem em
**10–1.455 comandos**, e o tempo estimado diminui **4,1–25,7%** nas condições do
ensaio. Em **10/16**, o deslocamento XY rápido aumenta: menos retrações não
significa automaticamente menos XY. Os dois números e o comprimento de corte
estão no relatório; não há fallback silencioso para esconder uma regressão.

Parâmetros: GRBL_11_no_M6, Safe Z 3 mm, corte −0,1 mm, avanço XY 300 mm/min,
avanço Z padrão e rápidos estimados de 1500 mm/min. São estimativas do parser,
sem aceleração ou comportamento real do controlador. Não são ganho de FPS nem
tempo cronometrado de máquina. O planejamento tem mais etapas de cálculo e
permanece experimental; não houve benchmark isolado de geração nesta rodada.

Relatório: `target/clearing-connect-fix-20261110/report/index.html`.
Snapshot dos 16 programas: `validation/region-cnc-refinement-20261110.json`.
Programas comparados: `cnc-final/`, a partir dos caminhos `regions-v2/`.
O primeiro candidato (`regions/`) reduzia retrações mas piorava a estimativa
nos teclados densos; foi refinado antes da consolidação. Essas pastas preservam
a evidência e não são duas opções do produto.

```powershell
& "$env:JAVA_HOME/bin/java.exe" -Xmx3g --class-path 'flatcam-cam/target/classes;flatcam-fx/target/dependency/*' tools/CompareRegionCncProbe.java target/connect-optimization-20261110/optimized-matrix target/clearing-connect-fix-20261110/regions-v2 target/clearing-connect-fix-20261110/cnc-final
& "$env:JAVA_HOME/bin/javac.exe" -cp 'flatcam-cam/target/classes;flatcam-fx/target/dependency/*' -d target/region-tool-tests tools/CompareRegionCncProbe.java tools/TestRegionCncCoverage.java
& "$env:JAVA_HOME/bin/java.exe" --class-path 'target/region-tool-tests;flatcam-cam/target/classes;flatcam-fx/target/dependency/*' TestRegionCncCoverage
```

## Comportamento

NCC e Paint oferecem **Lines por regiões (experimental)**. Os quatro métodos
anteriores e seus padrões permanecem. A opção usa as mesmas passadas do Lines,
incluindo complementos de ilhas e acabamento; altera sua ordem/direção.

Um grafo liga intervalos sobrepostos em linhas de varredura consecutivas quando
há um corredor central contido na área permitida aos centros. Continuidade
um-para-um prolonga uma célula; divisões/junções iniciam outras. O planejador
prioriza a célula antes de escolher uma vizinha, ou a mais próxima se não houver
vizinha pendente. Com Connect/Contour, o grafo integra o acabamento ao percurso.
É uma decomposição **amostrada pelas linhas**, não uma decomposição poligonal
exata. Não há cortes adicionais para preencher toda a área da célula.

Sem Contour, Connect mantém as ligações consecutivas do plano. As ligações retas
mantêm o limite de dez diâmetros e o teste da fresa inteira no polígono original;
o grafo só pode recaminhar arestas de corte já solicitadas. O agrupamento das
células não autoriza atalhos de corte pelo cobre.

## Interface e CNC

Ângulo automático ou explícito por fresa também funciona neste método. Os
painéis mantêm a seleção e dispõem de ajuda formatada/acessível nos quatro
idiomas. O seletor cabe em uma barra de 310 px nos quatro temas.

A Geometry gerada recebe o padrão CNC **SOURCE com direção preservada**.
Posições, exclusões e demais parâmetros existentes são mantidos; roteamento
personalizado diferente do padrão não é sobrescrito. O aviso no terminal e a
ajuda explicam que trocar para Proximidade desfaz o plano. Esse padrão usa o
campo persistido de parâmetros CNC existente, não um marcador transitório.

Os IDs numéricos Python continuam limitados aos quatro métodos históricos;
REGION_LINES não é exportado/importado como um falso método Python número 4.
A base Python e o Paint Shape do editor mantêm suas escolhas anteriores.

## Histórico: primeiro protótipo em 513cbccb

32 configurações comunitárias: oito placas × NCC/Paint × Connect, Contour
ligado. O comparador confere a multiplicidade **exata das arestas sem direção**
contra o Lines sem Connect: zero cortes ausentes; sem Connect, zero arestas
extras. Com Connect, só são acrescentadas ligações, sem apagar as passadas.
Três testes do comparador impedem aceitar passe perdido ou duplicado sem Connect.

Deslocamento XY, origem (0,0), ordem SOURCE, sem retorno final:

- Sem Connect: as 16 configurações reduziram deslocamento; faixa **7,7–91,3%**.
- Com Connect: 14 aumentaram deslocamento; duas reduziram apenas cerca de
  0,47 mm. **Todas as 16 aumentaram o número de sequências separadas de corte**
  (+1 a +543). Esse número é um indicador de retomadas, não uma simulação de Z.

Esses resultados iniciais **não justificavam trocar o padrão**. O resultado sem Connect é uma
comparação de planos em SOURCE; não compara com a otimização NEAREST que um CNC
Job comum pode executar. Não há medição de ciclo físico ou ganho de FPS.
O refinamento descrito no início deste documento integra o acabamento e compara
os programas reais. O modo continua experimental, sem promover um ganho geral
para projetos ou parâmetros ainda não avaliados.

Os controles sintéticos do modo novo passaram **96/96**, incluindo NCC/Paint,
Rest, duas fresas, ângulos, Contour, Connect e MM/IN. Medem distância ao cobre e
à borda com tolerância explícita de 0,5% do diâmetro, sem provar cobertura total.
Testes CAM mantêm todas as passadas/acabamentos em ângulos 0/37,5/90 e MM/IN,
verificam obstáculos arredondados, células, cancelamento e ordem no G-code.
Testes FX exercitam ambos os painéis, publicação de ordem CNC e quatro temas.

## Reprodução

```powershell
& "$env:JAVA_HOME/bin/java.exe" -Xmx4g '-Dflatcam.audit.method=REGION_LINES' '-Dflatcam.audit.caseFilter=.*-c[01]-e1' --class-path 'flatcam-cam/target/classes;flatcam-fx/target/dependency/*' tools/LinesCommunityProbe.java samples/community/validation/fx-import.json target/coverage-regions-20261110/regions
& target/oracle-py311/Scripts/python.exe tools/compare_region_lines.py target/connect-optimization-20261110/optimized-matrix target/coverage-regions-20261110/regions target/coverage-regions-20261110/region-report
.\tools\audit-cam-related.ps1 -Lines -Method REGION_LINES -ClassOverrides .\flatcam-cam\target\classes -OutputDirectory .\target\coverage-regions-20261110\distance-regions
```

HTML lado a lado, métricas e SVGs: `target/coverage-regions-20261110/region-report`.
Snapshot das 32 comparações: `validation/region-lines-20261110.json`.
Capturas dos temas: `flatcam-fx/target/region-lines-review`.
Build/testes: `target/regions-focused.log`, `target/regions-ui.log` e
`target/regions-verify.log`: **2.503 casos**, **2.485 aprovados**, 18 opcionais
ignorados, zero falhas/erros, 6min03s. Os totais vêm da execução completa do
reator, não da soma de XMLs residuais de execuções anteriores. Após localizar
também o nome do método na receita NCC, **12/12 controles FX/i18n** passaram
novamente (`target/regions-final-focused.log`). Nenhum instalador novo foi
produzido nesta rodada.
