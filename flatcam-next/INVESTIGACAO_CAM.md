# Investigação Standard/Paint/Seed — 2026-10-03, correção em 2026-10-03

Base da aplicação: `89653e29`, Java 25.0.4.1/JTS 1.20.0. Oráculo: checkout
Python local, Python 3.11.0/Shapely 1.8.5.post1/**GEOS 3.10.3** isolados.
As conclusões são específicas dessas versões e entradas, não uma declaração
de paridade geral. Não foi alterado nenhum algoritmo do Python.

## Resultado

As divergências investigadas têm causas demonstradas, não apenas uma hipótese
de diferença entre linguagens:

- Standard/Paint: regras internas diferentes dos buffers JTS/GEOS, amplificadas
  pelas erosões sucessivas. Um protótipo isolado reproduziu o comportamento
  legado e passou pelos critérios nos dois casos reais.
- Seed: diferenças no buffer e **instabilidade da escolha da scan-line/ponto
  interior**. Mesmo reproduzindo o kernel legado, a entrada decodificada
  independentemente pode gerar outro ponto inicial e, portanto, outros anéis.

**Atualização:** as correções Standard/Paint e Seed descritas como "próxima
implementação recomendada" (seção abaixo) foram incorporadas à aplicação - ver
"Correção entregue" mais abaixo em cada uma. O projeto original foi somente
lido; geometrias privadas e classes experimentais do protótipo ficaram em
`target/`.

## 1. Buffers Standard/Paint

Os códigos oficiais mostram duas diferenças relevantes:

1. Para segmentos quase paralelos, o JTS usa fator de separação **0,05** e
   escolhe o endpoint do segmento mais longo; GEOS 3.10.3 usa **0,001** e o
   endpoint do segmento de entrada. Desativar `simplifyFactor` não desativa
   essa heurística interna. Fontes: [JTS 1.20.0](https://raw.githubusercontent.com/locationtech/jts/1.20.0/modules/core/src/main/java/org/locationtech/jts/operation/buffer/OffsetSegmentGenerator.java)
   e [GEOS 3.10.3](https://raw.githubusercontent.com/libgeos/geos/3.10.3/src/operation/buffer/OffsetSegmentGenerator.cpp).
2. O simplificador legado começa no índice 1 inclusive em anéis; o JTS atual
   começa no índice 0 para anéis. Há também uma diferença na ordem dos argumentos
   do teste amostrado de distância, que muda quais concavidades são removidas.
   O GEOS legado usa tolerância equivalente a 1% do offset, mas **habilitar
   simplesmente 1% no JTS não reproduz essas regras**. Fontes:
   [simplificador JTS](https://raw.githubusercontent.com/locationtech/jts/1.20.0/modules/core/src/main/java/org/locationtech/jts/operation/buffer/BufferInputLineSimplifier.java),
   [simplificador GEOS](https://raw.githubusercontent.com/libgeos/geos/3.10.3/src/operation/buffer/BufferInputLineSimplifier.cpp)
   e [offset GEOS](https://raw.githubusercontent.com/libgeos/geos/3.10.3/src/operation/buffer/OffsetCurveBuilder.cpp).

### Experimentos controlados

Reprodução pública: quadrado 12×12 com vazio circular de raio 1, resoluções
4/16/64 por quadrante; ferramenta 0,5, inset inicial `0,5 / 1,999999`, passos
de 0,3 e oito erosões. Com os motores distribuídos, a maior distância entre
fronteiras fica em aproximadamente **0,000386 mm**. No caso de resolução 64,
a primeira fronteira possui 262 pontos JTS contra 518 GEOS; na quinta erosão,
262 contra 8198. Esse exemplo demonstra a redução de vértices, mas não reproduz
sozinho os ~0,021 mm da placa real.

No contorno real investigado, mudar só o fator de separação recupera muitos
vértices, mas **não resolve a divergência**. Reproduzir também a seleção do
endpoint e o simplificador antigo reduz as diferenças das 18 primeiras
erosões para menos de **9×10⁻¹³ mm**, com contagens de pontos iguais. O controle
com simplificador JTS atual a 1% ainda diverge e piora a primeira erosão.

Essas medidas de fronteira usam `hausdorff_distance` do GEOS, **Hausdorff discreta**,
não um limite certificado sobre todos os pontos contínuos. A comparação completa
dos caminhos abaixo mantém os critérios amostrados de [COMPARACAO_CAM.md](COMPARACAO_CAM.md).

O protótipo compilou cópias de duas classes JTS e de `NccGenerator` exclusivamente
em diretórios ignorados, com classpath separado. Não modificou o JAR instalado,
o código de produção nem o oráculo Python. Não é uma solução pronta para distribuir:
altera heurísticas globalmente no processo experimental, inclusive buffers usados
por outras ferramentas, que não foram comparadas nesse experimento.

| Caso real, projeto decodificado independentemente | FX distribuído: distância amostrada | Protótipo: distância amostrada | Protótipo |
| --- | --- | --- | --- |
| NCC Standard | 0,020589 mm | 0,000000094 mm | MATCH_SAMPLED |
| Paint Standard | 0,021587 mm | 0,000001083 mm | MATCH_SAMPLED |
| NCC Seed | 0,129283 mm | 0,147661 mm | DIFFERENT |

Os G-codes experimentais foram gerados e interpretados pelo parser Python,
mas isso não valida alturas, feeds ou execução física. A maior quantidade de
vértices fez Standard/Paint atingir o limite de prévia detalhada do FX no
protótipo; o limite foi preservado. Não foi medido ganho de desempenho.

## 2. Seed: escolher um ponto interior não é uma operação estável

Os dois motores usam uma linha horizontal situada entre os vértices próximos
ao centro vertical da área; depois escolhem o meio do maior trecho interior.
Pequenas alterações podem mudar o intervalo vertical escolhido, não apenas
desempatar trechos horizontais. Fontes:
[InteriorPointArea JTS](https://raw.githubusercontent.com/locationtech/jts/1.20.0/modules/core/src/main/java/org/locationtech/jts/algorithm/InteriorPointArea.java)
e [InteriorPointArea GEOS](https://raw.githubusercontent.com/libgeos/geos/3.10.3/src/algorithm/InteriorPointArea.cpp).

Reprodução mínima pública, sem buffer: losango `(0,0), (2,2), (0,4), (-2,2)`.
Mover os dois vértices de Y=2 para Y=`2 + 10⁻¹²` altera a fronteira em cerca
de **7,07×10⁻¹³ mm**, mas o ponto interior muda aproximadamente **2 mm**.
Isso ocorre tanto no JTS quanto no GEOS. Os pontos continuam interiores:
é uma descontinuidade do critério, não prova de caminho inválido.

No projeto real, após alinhar experimentalmente os buffers:

- Sobre exatamente o mesmo WKT de entrada, os pontos iniciais coincidem.
- Com decodificação independente, três regiões mudam de ponto inicial:
  deslocamentos de aproximadamente **0,42036 / 0,42036 / 0,13347 mm**.
- Suas fronteiras seguras diferem apenas ~**0,00000063 mm** na medida discreta,
  mas os intervalos da scan-line mudam. Diferença de área zero antes da erosão
  não significa coordenadas/vértices idênticos bit a bit.
- No controle sem decodificação independente, o NCC Seed experimental atende
  aos critérios: distância amostrada ~**1,59×10⁻¹⁴ mm**, comprimento relativo
  zero. Esse controle **não substitui** a comparação com o projeto original.

Portanto, apenas trocar a biblioteca de buffers não garante anéis idênticos.
A escolha do ponto inicial também precisa de uma política explícita. Normalizar
ou arredondar a entrada do oráculo apenas para obter aprovação esconderia isso.

## 3. Reproduzir os controles públicos

Novos probes não usam a placa do usuário nem dependem de uma interface gráfica.
Na pasta `flatcam-next`, com o reactor instalado, obtenha o classpath e execute:

```powershell
.\mvnw.cmd -q -pl flatcam-application dependency:build-classpath '-Dmdep.includeScope=test' '-Dmdep.outputFile=../target/cam-kernel-probe/classpath.txt'
$kernelProbeClasspath = (Get-Content target/cam-kernel-probe/classpath.txt -Raw).Trim()
java --class-path $kernelProbeClasspath tools/CamKernelProbe.java target/cam-kernel-probe/jts.json
..\.venv\Scripts\python.exe tools/compare_cam_kernel_probe.py --trace target/cam-kernel-probe/jts.json --output target/cam-kernel-probe/geos.json --dependency-path target/legacy-shapely185
..\.venv\Scripts\python.exe -m unittest discover -s tools -p 'test_compare_cam*.py'
```

A dependência isolada é preparada conforme [COMPARACAO_CAM.md](COMPARACAO_CAM.md).
O relatório registra Java/JTS/Python/Shapely/**GEOS**. O harness principal também
passou a registrar GEOS, sem mudar métricas ou tolerâncias. Os probes contêm três
casos Standard, 36 variantes Seed e o par de losangos; comparam fronteiras e
pontos, não certificam CAM/CNC. Inverter apenas a orientação dos anéis não
mudou os pontos nos 36 controles executados; não atribuir a causa a isso.

Verificação desta investigação: **16 testes auxiliares Python aprovados** tanto
com Shapely 2.1.2 quanto com 1.8.5.post1 isolado; probes públicos executados com
GEOS 3.10.3 e 3.13.1, com os mesmos resultados nesses controles; **541 testes
normais CAM/suporte aprovados**, zero falhas/erros/ignorados. A suíte completa anterior
não foi recontada nesta etapa. O modo strict do FX distribuído continua recusando
as três divergências; o experimental com projeto independente recusa Seed.

Relatórios privados locais: `target/cam-investigation/production-report`,
`experimental-full/report`, `experimental-full/report-shared-source` e
`experimental-full/seed-input-report.json`. Nenhum WKT/coordinate privado foi
incluído neste documento ou nos probes versionados.

## Correção entregue — Standard/Paint, 2026-10-03

`org.flatcam.cam.ncc.geosbuffer` (flatcam-cam) porta as classes de buffer do
JTS 1.20.0 necessárias (EPL-2.0/EDL-1.0, a mesma licença da dependência
`jts-core` já usada) para uma camada própria do projeto, alterando só as duas
regras documentadas acima: o fator de separação/escolha de endpoint em
`GeosOffsetSegmentGenerator.addOutsideTurn` e o índice inicial/ordem dos
argumentos do teste amostrado em `GeosBufferInputLineSimplifier`. O resto do
pipeline (geração de curva, nó, extração de polígono por profundidade) é o
mesmo código do JTS, só copiado para ter acesso às classes `package-private`
que o compila - não há classe do JTS substituída globalmente nem dependência
nova. `GeosBufferOp` reproduz a mesma órbita de fallback por precisão
reduzida que `BufferOp` usa. `NccGenerator.standardPaths` (erosões sucessivas
de Standard) e `preciseRoundBuffer` (área segura de Seed/Lines e do
footprint do Rest Machining) passam a usar esse buffer, com o simplificador
na tolerância padrão (1% do offset) em vez de desativado - é o próprio
simplificador fiel ao GEOS que preserva entalhes pequenos, não mais a
desativação usada antes.

Verificação:

- Probe público (`tools/CamKernelProbe.java`, oito erosões sucessivas,
  resolução 4/16/64): com `GeosBufferOp`, a distância de fronteira amostrada
  contra o oráculo real (GEOS 3.10.3 e 3.13.1, mesmo resultado) caiu de
  ~0,000386 mm (motor distribuído) para ~4-7×10⁻¹⁵ mm - ruído de ponto
  flutuante - nas três resoluções testadas.
- Projeto real autorizado (`PythonCamComparisonExportTest` +
  `tools/compare_cam_python.py`, sem `--strict`): `ncc-standard` passou de
  `DIFFERENT` (distância amostrada 0,020589 mm) para `MATCH_SAMPLED`
  (0,000643 mm); `paint-standard` de `DIFFERENT` (0,021587 mm) para
  `MATCH_SAMPLED` (0,0000011 mm). Com Shapely 2 (`.venv` principal),
  `ncc-seed`/`ncc-lines` e dois casos de Cutout batem em `ORACLE_ERROR` -
  incompatibilidade multipart do Python legado, já registrada em
  [COMPARACAO_CAM.md](COMPARACAO_CAM.md), não uma regressão desta correção.
  Repetindo com a dependência isolada Shapely 1.8.5.post1/GEOS 3.10.3 (que
  contorna essa incompatibilidade): `ncc-lines` **também** passou a
  `MATCH_SAMPLED` (0,000643 mm - usa a mesma `preciseRoundBuffer`); `ncc-seed`
  **continua `DIFFERENT`**, com distância amostrada 0,1477 mm - pior que os
  0,129283 mm da aplicação distribuída antes desta correção, não melhor.
  Confirma exatamente a previsão da seção 2 abaixo: o buffer não resolve a
  instabilidade da escolha do ponto interior de Seed, e corrigi-lo pode
  simplesmente mover a descontinuidade para outro lugar, não eliminá-la.
- `521` testes de `flatcam-cam` aprovados, incluindo os seis novos de
  `GeosBufferOpTest` (ancorados no mesmo valor do oráculo público acima) e os
  já existentes de entalhe côncavo/epsilon em `PaintGeneratorTest` -
  nenhuma regressão. `813` testes no reactor completo. Build completo e
  abertura do FX verificados depois do `install`.

Fora do escopo desta entrega: Seed continua sem a política de ponto inicial
descrita no item 3 abaixo e segue `DIFFERENT`; nenhum outro uso de buffer em
`NccGenerator` (margens, fronteiras com mitra, uniões de footprint) foi
alterado; Isolation e Cutout não usam este buffer e não foram tocados.

## Correção entregue — Seed, 2026-10-03

`org.flatcam.cam.ncc.StableInteriorPoint` (flatcam-cam) substitui
`Geometry.getInteriorPoint()` como ponto de partida de Seed por uma busca
"pole of inaccessibility" (algoritmo polylabel): refinamento de grade que
maximiza a distância até a fronteira, parando quando nenhuma célula restante
pode melhorar o resultado além da precisão pedida ou ao atingir o orçamento
de busca. Diferente do ponto
interior por scan-line (usado tanto pelo JTS quanto pelo
`representative_point()` do Python/GEOS - ambos chamam, no fundo, a mesma
`InteriorPointArea`/`GEOSPointOnSurface`), a distância até a fronteira é uma
função contínua dos vértices do polígono, mas isso **não garante continuidade
do ponto escolhido**: máximos empatados podem trocar de prioridade. A busca
evita o limiar específico de scan-line observado no losango, não todo salto
possível em qualquer entrada.

Isto é uma **diferença deliberada** do Python, documentada na própria classe,
não uma tentativa de reproduzir o algoritmo legado:

- No caso sintético mínimo do losango (mover dois vértices em 10⁻¹²), o ponto
  por scan-line pula ~2 mm; o novo ponto se move menos de 10⁻³ mm para a
  mesma perturbação (`StableInteriorPointTest`).
- No projeto real autorizado, medido com a dependência isolada
  Shapely 1.8.5.post1/GEOS 3.10.3 (necessária para este caso - ver seção 3):
  `ncc-seed` **continua `DIFFERENT`**, distância amostrada 0,1499 mm - na
  mesma ordem de grandeza de antes (0,1293 mm distribuído, 0,1477 mm só com
  o buffer corrigido), não uma melhora nem uma piora relevante. Isso é
  esperado, não uma falha: a comparação agora opõe dois algoritmos
  deliberadamente diferentes, não duas instâncias ruidosas do mesmo
  algoritmo, então não há razão para a distância amostrada cair. O que
  importa é `footprintIntersectionOverUnion = 0,999957` - a área realmente
  limpa pelas duas ferramentas continua praticamente idêntica; só a
  sequência/posição dos anéis concêntricos muda.
- `535` testes de `flatcam-cam` aprovados, incluindo os oito novos de
  `StableInteriorPointTest` (quadrado, polígono com furo, dois polígonos
  disjuntos, L côncavo com valor esperado calculado analiticamente, a
  reprodução do losango, geometria vazia, cancelamento cooperativo e
  consistência entre precisões). `821` testes no reactor completo. Build
  completo e abertura do FX verificados depois do `install`.

Fora do escopo: nenhuma tentativa foi feita de aproximar o ponto do Python
(isso exigiria reproduzir a mesma instabilidade, não eliminá-la). Lines e
Standard não usam este código (só Seed chama `StableInteriorPoint`).

## Próxima implementação recomendada

1. ~~Isolar um buffer de compatibilidade para Standard/Paint, sem substituir
   classes da dependência global.~~ Entregue - ver "Correção entregue" acima.
   Cancelamento não precisou de mudança (`NccGenerator` já verifica entre
   passes de erosão, não dentro de uma chamada de buffer, igual a antes).
   Licença preservada (cabeçalho EPL-2.0/EDL-1.0 original mantido em cada
   arquivo copiado). MM/IN não exigem tratamento especial no buffer em si
   (opera em números, não em unidades); os testes existentes de Paint/NCC em
   IN continuam passando. As outras ferramentas (Isolation, Cutout) não usam
   este buffer.
2. Verificar orçamento de vértices/tempo e a prévia visual com o buffer alinhado,
   mantendo intactos os caminhos CNC e seus limites de proteção. **Pendente:**
   o simplificador fiel ao GEOS gera bem mais vértices que o anterior (ex.: 8198
   contra 278 no probe público, oitava erosão) - o limite de prévia detalhada do
   FX já existe e foi respeitado nos testes, mas tempo de geração/memória em
   placas grandes com muitas ferramentas não foi medido nesta entrega.
3. ~~Para Seed, avaliar preservação da entrada legado e escolha
   explícita/estável do ponto inicial.~~ Entregue - ver "Correção entregue —
   Seed" acima. A política escolhida foi NÃO preservar o comportamento
   legado (instável nos dois motores) e usar um ponto estável e documentado
   como diferença deliberada, em vez de perseguir uma equivalência que o
   próprio Python não garante de uma execução para outra.

O experimento Java mostra que C++/Rust não é um requisito demonstrado para essa
correção numérica. Um backend GEOS nativo continua sendo uma decisão arquitetural
separada, com empacotamento, licenças e desempenho a validar; não foi instalado
nem incorporado ao aplicativo nesta investigação.

### Endurecimento da busca Seed — 2026-10-05

A grade inicial passou a ter no máximo 256 células e a verificar cancelamento
antes da coleta e durante sua construção. A grade anterior podia alocar
milhões de células para polígonos extremamente estreitos antes de consultar o
token. Índices inteiros evitam incrementos flutuantes sem progresso. Partes
não poligonais são ignoradas; precisão deve ser finita e positiva.

O limite de 20.000 células processadas por polígono já existia e continua.
Ao esgotá-lo, retorna-se o melhor candidato interior encontrado, não uma
garantia da precisão alvo. Um ponto interior de fallback protege geometrias
cujos centros de grade/centroide caem fora. As 14 regressões de
`StableInteriorPointTest` passaram; install completo também passou.
A comparação privada Seed/Python não foi reexecutada e seus resultados acima
permanecem históricos. Esta é uma proteção de custo/cancelamento, não uma
declaração de equivalência numérica ou de continuidade global do ponto.
