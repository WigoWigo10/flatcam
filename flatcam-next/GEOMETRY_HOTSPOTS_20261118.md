# Gargalos geométricos detalhados — 18/11/2026

## Resultado

Dois alvos ficaram identificados, sem alterar o algoritmo:

1. **Lines em Horizon:** recortar cada linha contra a área segura concentra
   52–61% das amostras de CPU. Preparar a área segura acrescenta 11–28%.
2. **Rest com três Clear:** os buffers que preparam a máscara do estoque restante
   concentram 42–54%. Na Audio, preparar **e aplicar** essa máscara fica em
   aproximadamente 63%, tanto no perfil anterior quanto na confirmação.

As cópias de arrays relevantes vêm principalmente do crescimento de listas
internas de interseção/buffer. Não há evidência para atribuir esse custo
genericamente a cópias de coordenadas do editor ou ao renderer.

## Dados e método

Foram reanalisados os quatro JFRs de geração de
`CONNECT_MEASUREMENT_20261118.md`, preservando os arquivos originais. O algoritmo
é o da base `7d7110a8`; os testes e a medição anterior foram registrados em
`fe4d6655`. Ambiente/protocolo continuam os do relatório anterior.

Para confirmação, foram gerados **dois JFRs adicionais sem o agente Java**:
Horizon 2×2 Lines e Audio Lines/Rest com três Clear. Usam o snapshot CAM
congelado da conferência anterior, Connect/Contour ligados, os mesmos parâmetros,
limites e entradas públicas. JFR continua ativo, portanto não são benchmarks
sem instrumentação. O resultado bruto e os dados por fresa coincidem com a
referência ligada, incluindo hashes da fonte/área e contagem de falhas.

A classificação usa o ponto de chamada e seus pais na pilha, com os números
de linha obtidos da fonte congelada. Não confundir porcentagens de amostras
com milissegundos exatos. As APIs inclusivas (buffer, overlay, cópia) podem se
sobrepor. Os grupos exclusivos de chamadas mantêm a soma dos pesos de cada JFR.
Nenhuma pilha dos seis perfis ficou truncada nesta leitura.

## Lines: recorte das linhas e erosão da área segura

| Caso | Amostras CPU | Interseções das linhas | Erosão da área segura |
|---|---:|---:|---:|
| Horizon simples, perfil original | 589 | 305 — 51,8% | 162 — 27,5% |
| Horizon 2×2, perfil original | 1.598 | 961 — 60,1% | 174 — 10,9% |
| Horizon 2×2, confirmação sem agente | 1.865 | 1.131 — 60,6% | 199 — 10,7% |

As chamadas estão em `NccGenerator.linePaths`: `line.intersection(safeArea)`
nas duas orientações de varredura, e `preciseRoundBuffer(polygon, -toolRadius)`
antes do recorte. As pilhas passam por overlay/noding, árvores de consulta e
interseção de cadeias monotônicas.

No painel original, os recortes das linhas respondem por cerca de **2.900 MiB
de alocações estimadas acumuladas**; a erosão, aproximadamente 1.000 MiB.
Esses números incluem objetos temporários liberados durante a geração; não
significam consumo residente ou pico de RAM desse tamanho.

Alvo de uma futura otimização: investigar o reaproveitamento da preparação/
consulta geométrica entre linhas, preservando recortes, ordem e direção.
Não foi implementado índice novo nem alterado o stepover nesta etapa.

## Rest: máscara do material restante

`clearAreaImpl` executa duas operações de seleção antes de processar as formas:

```java
core = buffer(stock, -tolerance);
needed = buffer(core, toolDiameter / 2 + tolerance);
```

O primeiro elimina franjas de aproximação; o segundo permite selecionar
centros cuja fresa alcance o material restante. Essa máscara não substitui
os limites originais de acesso da fresa.

| Caso | Erosão da máscara | Expansão da máscara | Recorte dos caminhos pela máscara |
|---|---:|---:|---:|
| Audio, perfil original | 12,6% | 39,0% | 11,5% |
| Audio, confirmação sem agente | 12,3% | 30,0% | 20,3% |
| Breadboard 2×2 Standard, perfil original | 7,5% | 46,3% | 1,8% |

Na Audio original, as estimativas acumuladas desses três pontos são
aproximadamente **1.637 / 1.263 / 1.356 MiB**. A união dos footprints para
preparar a próxima fresa acrescenta cerca de 6,4% das amostras e 1.077 MiB
estimados. As proporções variam entre rodadas, mas a concentração na seleção
do trabalho Rest se repete.

O Standard e seu refinamento não dominam o controle Breadboard: offsets
principais representam 2,8% e `StandardCoverageRefinement` 7,5%, enquanto os
dois buffers da máscara chegam a 53,7%.

Não remover o acesso ao espaço já limpo para simplificar o cálculo: isso pode
impedir a fresa menor de alcançar cantos. Reduzir o raio ou aumentar tolerâncias
também não foi usado como atalho.

## Onde ocorrem as cópias de arrays

Na Audio, `Arrays.copyOf` aparece em 293/1.449 amostras (20,2%). Em 248 delas,
o primeiro chamador da biblioteca após os frames Java é
`GeosSubgraphDepthLocater.findStabbedSegments`. No Breadboard são 68 de 80
amostras de cópia, ou 17,6% da geração inteira.

As pilhas mostram `ArrayList.add → grow → Arrays.copyOf` ao acumular segmentos
atingidos pelo raio. O código depois usa `Collections.min` dessa lista.
**Candidato futuro:** verificar uma seleção incremental do mínimo ou uma redução
dos candidatos, conservando comparações, empates e resultado geométrico. A
análise não prova quanto essa mudança economizaria nem autoriza trocar a ordem.

Nas Horizon, um contexto frequente é `ArrayListVisitor.visitItem` durante
consultas ao HPRtree do noding. Isso liga as cópias ao trabalho de interseção
das linhas. As classes com maiores pesos de alocação incluem arrays de objetos,
`Coordinate`, `KdTree.QueryStackFrame` e estruturas de eventos/arestas.
Tempo amostrado de cópia e bytes alocados não são a mesma métrica.

## Verificações e limites

- Contagens CPU e pesos de alocação dos quatro JFRs conferem com o sumário
  anterior independente. Os pesos dos grupos exclusivos fecham nos seis perfis.
- SHA-256 dos JFRs conferido antes/depois: nenhuma gravação foi alterada.
- Duas confirmações sem agente preservam as geometrias brutas e por fresa.
- Dois controles negativos passaram: uma gravação válida sem amostras NCC é
  recusada; uma referência com contagem CPU adulterada também é recusada.
  Nenhum deles produz um resumo final marcado como completo.
- Os 16 testes focados do Connect e três do comparador Python passaram novamente.
- Não houve otimização, nova biblioteca nativa, mudança de precisão, teste
  físico de CNC ou nova medição de FPS. Não se atribui um speedup a esta análise.
- Continua pendente o projeto/receita do relato original do Yago. Esses perfis
  não encerram esse caso nem certificam cobertura integral de todas as placas.

## Repetir e evidências

Ferramentas: `tools/SummarizeGeometryHotspots.java` e
`tools/profile_geometry_hotspots.py`. Usam Java 25/JFR e as dependências locais.

```powershell
python tools/profile_geometry_hotspots.py --recordings target/connect-measurement-20261118-runs --confirmation target/geometry-hotspots-20261118/confirmation --output target/NOVO-PERFIL-GEOMETRIA
```

O destino precisa ser novo. A reanálise lê os dados; não executa CAM novamente.
As confirmações vêm de `ConnectMeasurementProbe` com a propriedade
`flatcam.connect.jfr` e **sem** `-javaagent`, usando os `case.json` originais.

Resumo portátil: `validation/geometry-hotspots-20261118.json`. Dados completos:
`target/geometry-hotspots-20261118-final/summary.json`; novas gravações em
`target/geometry-hotspots-20261118/confirmation/`; controles negativos em
`target/geometry-hotspots-20261118-guards/`.
