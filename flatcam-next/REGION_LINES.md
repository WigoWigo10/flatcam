# Lines por regiões — protótipo opt-in, 2026-11-10

## Comportamento

NCC e Paint oferecem **Lines por regiões (experimental)**. Os quatro métodos
anteriores e seus padrões permanecem. A opção usa as mesmas passadas do Lines,
incluindo complementos de ilhas e acabamento; altera sua ordem/direção.

Um grafo liga intervalos sobrepostos em linhas de varredura consecutivas quando
há um corredor central contido na área permitida aos centros. Continuidade
um-para-um prolonga uma célula; divisões/junções iniciam outras. O planejador
termina a célula antes de escolher uma vizinha, ou a mais próxima se não houver
vizinha pendente. Os anéis de acabamento completos ficam no final.
É uma decomposição **amostrada pelas linhas**, não uma decomposição poligonal
exata. Não há cortes adicionais para preencher toda a área da célula.

Com Connect, só trechos consecutivos do plano podem ser unidos. Mantêm-se o
limite estrito de 10 diâmetros e o teste de contenção da área varrida por toda a
fresa no polígono original. O predicado de agrupamento das células não autoriza
movimentos de corte; estes passam pelo predicado da fresa completa.

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

## Evidências e decisão

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

Isso **não justifica trocar o padrão atual**. O resultado sem Connect é uma
comparação de planos em SOURCE; não compara com a otimização NEAREST que um CNC
Job comum pode executar. Não há medição de ciclo físico ou ganho de FPS.
O modo permanece identificado como experimental. O próximo refinamento é
integrar acabamento e entradas/saídas entre células antes de afirmar um ganho
geral do Connect. Não introduzir fallback silencioso que mascare as regressões.

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
