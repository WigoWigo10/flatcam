# Standard: cobertura entre offsets — 2026-11-12

Escopo: item 1 após `da7b5e53`, investigar e corrigir as sobras do Standard
observadas na comparação da Espiral. Mantida a Beta 7, sem novo instalador.

## Reprodução e causa

Windows, Java 25, mesma árvore de fontes e inventário público. Reproduzidas as
três sobras antes de modificar o algoritmo. Núcleos estáveis de sobra:
Breadboard 0,1009337109 mm², Modxo 0,0022966021 mm² e Single-sided 5V
0,3418564391 mm². Em pontos interiores dessas sobras, a distância ao trajeto
é 0,214–0,222 mm, maior que o raio físico de 0,200 mm. Não é apenas área
minúscula produzida por arredondamento do relatório.

Caso mínimo: retângulo 4 × 0,84 mm, fresa Ø 0,4 mm, overlap 40%, Connect
inativo. A primeira erosão deixa altura 0,44 mm. O passo seguinte, 0,24 mm de
cada lado, faz o offset desaparecer. O meio permanece a 0,2199999 mm dos
trajetos: o disco de raio 0,2 mm não o alcança. A rotina real
`camlib.Geometry.clear_polygon` do Python reproduz a mesma distância nas dez
execuções consecutivas. O mesmo desaparecimento pode ocorrer num lóbulo local
enquanto outra parte do bolsão continua recebendo offsets. Overlap zero
expõe também intervalos sem cobertura perto dos cantos internos dos offsets.

**Origem:** limitação herdada da rotina Python; não há uma versão FX conhecida
sem a limitação para usar como ponto bom de bisect. Não se atribui a falha à
recente implementação da Espiral. O hash da fonte Python, versões
Shapely/GEOS e pontos testemunha estão no relatório.

## Correção

Conservados a primeira erosão, cada offset seguinte e todos os contornos
originais. Antes de avançar, identifica-se o material interior que fica além
do alcance do contorno atual e do próximo offset. Um contorno intermediário a
meio passo é recortado localmente perto dessa sobra e acrescentado aos passes.
Isso também ocorre quando o próximo offset é vazio, inclusive em ramificações.
Metade do passo solicitado não ultrapassa o raio nominal; o diâmetro informado
não é reduzido. O guard numérico só seleciona a região dos complementos.
Connect continua sendo aplicado depois; não se prometem menos entradas.
Cancelamento cooperativo é verificado entre as operações geométricas.

No **Rest com Standard**, o espaço original permitido determina onde cabem os
centros. A sobra seleciona o trabalho, como já ocorre no Lines/Espiral. Erodir
a sobra estreita como se fosse uma nova parede impedia a fresa menor de alcançar
cantos válidos depois de uma limpeza maior. Reutiliza-se o seletor/footprint
nominal existente, sem permitir atravessar cobre ou a borda original.

Standard sempre inclui contornos; desmarcar Contour não os retira, como no
Python. O ângulo raster não se aplica. Tooltip específico, com parágrafos e
formatação do sistema de ajuda, explica os complementos e o Rest em PT-BR,
inglês, espanhol e francês. IDs numéricos, nomes serializados e outros métodos
não mudam. Seed e combinações de Rest com outros métodos mantêm suas políticas.

## Resultado

Mesmo alvo, fresa 0,4 mm, overlap 40%, Connect/Contour, sem Rest. Caixa do cobre
menos cobre: limite diagnóstico, não o Outline físico de uma placa.

| Placa | Núcleo estável antes → depois (mm²) | Minutos estimados antes → depois | G0 Z antes → depois |
|---|---:|---:|---:|
| Breadboard Power Supply | 0,100934 → 0 | 6,862 → 7,020 | 70 → 76 |
| Modxo Adapter | 0,002297 → 0 | 5,002 → 5,024 | 26 → 26 |
| Single-sided 5V | 0,341856 → 0 | 9,315 → 9,535 | 31 → 31 |

O tempo estimado de programa aumenta 0,5–2,4%, removendo material antes omitido.
Não é tempo medido numa CNC. Geração exigiu mais operações geométricas; os
milissegundos guardados são observações concorrentes aos testes, sem alegação
de benchmark isolado ou melhoria de FPS. Raw stock e regiões inacessíveis
continuam publicados. **Núcleo zero na tolerância declarada não é sobra bruta
zero nem certificação física de limpeza completa.**

As distâncias dos pontos testemunha foram conferidas de forma independente
pelo Shapely, tanto nos caminhos antigos quanto nos novos. O oracle usa os
métodos Python reais sem abrir a interface, sem alterar preferências, fontes ou
arquivos das placas. Não é uma execução completa do aplicativo Python.

## Validação e regressão

Build completo: **2.603 casos, 2.584 aprovados e 19 opcionais ignorados**, sem
falhas/erros, incluindo fluxos com panelização. Durou 11:03 nesta rodada; o
registro anterior era 4:55. A classe de fluxos panelizados levou 270,1 s contra
92,36 s. Não foi uma comparação isolada: carga/temperatura/JIT não controlados.
Há custo novo na preparação de complementos e no recorte Rest pela área
original. A correção não é anunciada como melhoria de desempenho; esse custo
merece medição e otimização próprias, preservando a cobertura alcançada.


- `StandardCoverageTest`: 34 casos determinísticos, Paint/NCC × MM/IN ×
  Connect × Rest, retângulo estreito, lóbulo que desaparece e cantos com
  overlap zero. Distância física ao ponto testemunha, cobertura por fresa,
  diâmetro nominal e fonte sem mutação. Os 34 falharam antes; passaram após.
- Reativar o comportamento legado faz os mesmos 34 falharem novamente. A
  sequência foi registrada nos logs; nenhum teste foi desativado.
- 12 casos públicos com duas fresas 0,8/0,4 mm, Rest, Paint/NCC e MM/IN:
  núcleo estável zero na geometria e no GRBL relido. Desvios de exportação e
  sobras individuais/cumulativas de cada fresa preservados no JSON.
- 32 controles de distância com pads redondos/arredondados/cápsulas, Rest,
  Connect/Contour, duas fresas e MM/IN: zero falhas/erros. O harness publica
  sua margem de aproximação de 0,5% do diâmetro; não é prova exata de colisão.
- Teste de UI verifica a ajuda específica do Standard e a troca de método;
  catálogos nos quatro idiomas. Dois testes antigos com valores exatos do
  Python continuam exercitando explicitamente o modo legado; o caso de
  costura também verifica que todos os contornos originais sobrevivem no
  modo corrigido. Não se atualizaram goldens para esconder a diferença.

Diagnóstico para comparar o algoritmo bruto com Python:
`-Dflatcam.standard.refine=false`. Isso desliga os complementos **e** restaura
a seleção Rest anterior. É uma opção técnica para os oracles, não novo campo
na interface. O padrão corrigido intencionalmente diverge dos trajetos Python;
não mascarar essa diferença nem contá-la como igualdade geométrica estrita.

Evidência compacta: `validation/standard-coverage-20261112.json` e
`validation/standard-distance-20261112.json`. WKT, NC, logs e figura do caso
mínimo ficam em `target/standard-*`. As placas públicas originais não mudam.

## Repetir

A partir de `flatcam-next`, usar diretórios novos para não reutilizar JSONs:

```powershell
cmd /c ".\mvnw.cmd -pl flatcam-cam -am -Dtest=StandardCoverageTest,NccGeneratorTest -Dsurefire.failIfNoSpecifiedTests=false test"
& "$env:JAVA_HOME/bin/java.exe" -Xmx3g '-Dflatcam.spiral.methods=STANDARD' --class-path 'flatcam-cam/target/classes;flatcam-fx/target/dependency/*' tools/SpiralCommunityProbe.java samples/community/validation/fx-import.json target/standard-repeat-new
.\tools\audit-cam-related.ps1 -Lines -Method STANDARD -ClassOverrides .\flatcam-cam\target\classes -OutputDirectory target/standard-distance-new
```

O retorno ao legado para provar a regressão é o mesmo Maven com
`-Dflatcam.standard.refine=false`; espera-se **falha** nos 34 testes.
Para a comparação de testemunhas, gerar uma pasta baseline com esse parâmetro
na JVM e depois executar:

```powershell
.\target\oracle-py311\Scripts\python.exe tools/probe_standard_coverage.py --legacy-root .. --before target/standard-before-20261112 --after target/standard-repeat-new --inventory samples/community/validation/fx-import.json --output target/standard-witness-new.json
```

`ClearingCoverageProbe.java` aceita `-Dflatcam.audit.method=STANDARD`; os casos
Rest suplementares são selecionados por `flatcam.audit.caseFilter`. O primeiro
argumento é uma pasta de casos já exportados; uma pasta vazia executa apenas
os casos comunitários suplementares. Manter o padrão Lines para outras rodadas.
