# Lines: cobertura nominal e exportação — 2026-11-10

**Retificação posterior:** os resultados históricos abaixo usavam buffers que
subestimavam anéis fechados minúsculos e não separavam acesso de encaixe exato.
Não interpretar aqueles percentuais como defeitos de trajeto confirmados.
Ver [reprodução, correção e comparação com o mesmo analisador nos dois lados](REST_COVERAGE_REFINEMENT.md).

Esta rodada é desenvolvimento após a Beta 7. Não altera a versão nem gera
instalador: cobertura, Connect e o protótipo por regiões entrarão juntos na
próxima Beta, depois da consolidação.

## Plano e critérios

Escopo: oito placas públicas já preservadas em `samples/community`, NCC e Paint,
Contorno/Connect, dois diâmetros em Rest, varreduras inclinadas e MM/IN.
As caixas do cobre são limites **diagnósticos**, não o Outline real da PCB.

| Requisito | Risco | Evidência |
|---|---|---|
| C1: distinguir material inalcançável de sobra dos trajetos | alto: aprovação falsa de limpeza completa | abertura morfológica na área original, testes analíticos MM/IN |
| C2: Rest pode sobrepor áreas já removidas | alto: chamar uma sobra fina de impossível | teste de faixa estreita alcançável a partir da área original |
| C3: G-code mantém cortes por ferramenta | alto: perder/deslocar passes | GRBL_11_no_M6, exportações individuais e combinada, ida e volta pelo parser |
| C4: quantificação não altera dados | alto: modificar geometria/diâmetro | assinaturas existentes, fonte somente leitura, igualdade da fonte após geração |
| C5: resultados incompletos continuam visíveis | médio: erro tratado como aprovação | estado ERROR_OR_BUDGET; relatório vazio não aprova |
| C6: separar discretização de sobra relevante | médio: esconder sobra por tolerância | áreas brutas e núcleo erodido apresentados separadamente |

Entrada: build CAM, inventário e 64 controles anteriores gerados; sem máquina
conectada e sem regravar arquivos comunitários. Saída: cada caso com estado e
métricas, testes afetados e reator aprovados. Erros e sobra não são paridade nem
aprovação de usinagem. A validação física por um operador fica fora desta rodada.
O orçamento por caso reserva margem para investigação; é cooperativo, não
interrompe uma operação geométrica indivisível.

## Modelo

`MachiningCoverage` já calcula as áreas varridas nominais por ferramenta. Esta
rodada o reutiliza sem alterar o seu algoritmo. Cada linha/ponto recebe um
buffer de raio diâmetro/2 e 16 segmentos por quadrante; Polygon significa seus
contornos, nunca o interior preenchido. A diferença com o alvo fornece a sobra.

`MachiningReachability` calcula centros possíveis por erosão da **área original**
em diâmetro/2 e a região nominal alcançável por dilatação e interseção, com 64
segmentos por quadrante. Em Rest, usar somente a sobra como área permitida
proibiria indevidamente sobrepor o que já foi removido. A menor fresa disponível
define o acesso final; uma abertura vazia não equivale a falha do algoritmo.

O relatório mantém a sobra alcançável bruta. Mostra também seu núcleo após
erosão pela soma das flechas dos segmentos circulares Q16/Q64; no G-code,
acrescenta o limite de arredondamento XY (4 casas em MM, 6 em IN). Essa política
de análise **não é um limite geral de erro topológico**, nem modifica trajetos,
fresas ou parâmetros de corte. Não mede Z, material, rigidez, fixação ou máquina.

## Matriz e reprodução

- 64 controles anteriores: oito placas × NCC/Paint × Connect × Contour,
  diâmetro 0,4 mm, overlap 40%, margem/offset zero, ângulo automático, sem Rest.
- 32 controles adicionais: oito placas × NCC/Paint × MM/IN; Rest com fresas
  0,8/0,4 mm, Connect e Contour ligados, ângulos 37,5°/90° em combinação pareada.
- Cada fresa é exportada e relida separadamente; o programa combinado também é
  relido e confere o comprimento total. O máximo desvio de vértices e pontos
  médios é amostrado nos dois sentidos dentro do limite de arredondamento.
  Não é uma prova contínua de equivalência geométrica de programas arbitrários.
- Ordem CNC SOURCE, sem reversão: facilita conferir a fidelidade do programa
  gerado. Distâncias deste ensaio não representam as rotas padrão NEAREST.

No PowerShell, a partir desta pasta:

```powershell
& "$env:JAVA_HOME/bin/java.exe" -Xmx4g --class-path 'flatcam-cam/target/classes;flatcam-fx/target/dependency/*' tools/ClearingCoverageProbe.java target/connect-optimization-20261110/optimized-matrix samples/community/validation/fx-import.json target/coverage-regions-20261110/coverage
python tools/summarize_clearing_coverage.py target/coverage-regions-20261110/coverage target/coverage-regions-20261110/report
```

O probe preserva os relatórios existentes para retomada. Use uma pasta nova ao
mudar o código; o filtro opcional `-Dflatcam.audit.caseFilter=...` seleciona IDs.
O orçamento padrão é 240 segundos por caso. As saídas detalhadas e o HTML ficam
em `target/coverage-regions-20261110`; não são entradas de produto.

## Resultados

**96/96 casos medidos**, oito placas, zero erros/orçamentos excedidos e zero
violações do limite de desvio amostrado na exportação. Os programas combinados
também conservaram o comprimento de corte das exportações individuais.

Percentual de sobra nominal **alcançável**, sobre a área nominal alcançável:

| Configuração | Casos | Faixa observada |
|---|---:|---:|
| Sem Rest, Contour desligado | 32 | 3,233–22,192% |
| Sem Rest, Contour ligado | 32 | 0,00056–0,5825% |
| Rest, Contour ligado, duas fresas | 32 | 0,3397–2,7265% |

O maior núcleo de sobra além da tolerância de análise foi 0,5701% sem Rest,
com Contour, e 2,6937% com Rest; ambos no Air Quality Sensor. Portanto, as sobras
não se resumem ao arredondamento do G-code. São achados de cobertura do Lines,
não motivo para declarar o material inalcançável nem para afirmar limpeza 100%.
O próximo trabalho de cobertura é atacar esses núcleos com passadas adicionais
validadas; reordenar as linhas não resolve sozinho essa lacuna.

O maior desvio de área bruta entre Geometry e G-code foi cerca de 1,1592 mm²,
em Audio Watermark Rest MM. A métrica de área pode acumular pequenas mudanças
ao longo de muitas bordas; os desvios posicionais amostrados desse caso também
ficaram dentro do limite de arredondamento. Os dados individuais registram
essa diferença, sem substituí-la por um estado genérico de equivalência.

Testes analíticos de acesso/cobertura: **10/10 aprovados**. A suíte integrada
com o protótipo é documentada em [REGION_LINES.md](REGION_LINES.md).
Snapshot das 96 métricas e regras: `validation/clearing-coverage-20261110.json`.
HTML detalhado: `target/coverage-regions-20261110/report/index.html`.

O cache do probe final usa a identidade completa da área permitida. Os alvos
NCC/Paint desta matriz foram conferidos independentemente com GEOS: diferença
simétrica de área zero nas oito placas. A reexecução final de dois controles
com o cache endurecido conservou as métricas na precisão exibida (maior mudança
de sobra: 3,84e-10 mm²), em `target/coverage-final-probe.log`.
