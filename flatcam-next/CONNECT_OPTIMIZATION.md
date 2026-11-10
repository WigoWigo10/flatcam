# Otimização do Connect — 10 de novembro de 2026

Implementação: **61859b6c**, posterior à Beta 7. Acelera o cálculo no NCC/Paint,
mantendo os trajetos; não é uma nova estratégia de fresagem nem uma medição de FPS.

## Gargalo medido e alteração

A gravação JFR do controle Horizon Choc apresentou 4.035 amostras com Connect
na pilha. Dessas, 3.872, aproximadamente **96%**, estavam no predicado `within`:
cada possível conector reconstruía estruturas geométricas para comparar a área
varrida pela fresa com o mesmo polígono original.

O Connect agora prepara esse polígono uma vez com `PreparedGeometryFactory`
e reutiliza seus índices. Consulta `preparedArea.contains(sweptConnector)`,
o predicado inverso de `sweptConnector.within(area)`, preservando a política
topológica. Mantém o buffer de raio igual à metade do diâmetro, resolução 64,
limite estrito de dez diâmetros, ordem de busca, desempates e direção inicial.
Não testa apenas a linha central nem desconta o raio duas vezes.

Também busca o array de coordenadas uma vez por caminho. Dependendo do
`CoordinateSequence`, `getCoordinates()` pode materializar um array; a sequência
de arrays padrão do JTS retorna seu próprio array. Os testes contam **acessos**,
não bytes copiados. O perfil mostrou que esse não era o principal custo.

A busca do próximo segmento continua sendo uma varredura. O índice espacial
novo é o da **área usada nas consultas de segurança**, não dos extremos dos
caminhos. Alterar o algoritmo de ordenação não era necessário para este ganho
e criaria risco de mudar os desempates do legado.

## Medição pareada

Controle: `horizon-choc-ncc-c1-e0`, fresa 0,4 mm, overlap 40%, Lines,
Connect ligado, Contour e Rest desligados. Referência explícita: retângulo
dos limites do cobre, usada para diagnóstico; não é um setup físico de CNC.

Três pares em JVMs novas, em sequência, nas ordens anterior/nova,
nova/anterior e anterior/nova. `-Xmx4g`, sem gravar JFR durante essas medições.
O tempo abaixo vem de dentro da sonda de geração, sem incluir compilação do
arquivo Java e partida do processo, registradas separadamente em `processMs`.

| Par | Anterior | Otimizado |
| --- | ---: | ---: |
| 1 | 41,253 s | 5,004 s |
| 2 | 38,278 s | 6,014 s |
| 3 | 36,738 s | 6,109 s |
| **Mediana** | **38,278 s** | **6,014 s** |

Neste controle, razão de **6,36×** e redução de **84,29%** na latência de geração.
As seis saídas têm o mesmo SHA-256 dos caminhos brutos e as mesmas áreas,
contagens e diagnósticos. Não é garantia de ganho igual em todos os projetos.

Ambiente: Intel Core Ultra 7 155H, 16 núcleos/22 processadores lógicos,
31,4 GiB de RAM física reportados pelo Windows, JDK 25.0.4.1. São cálculos de CPU;
esta mudança não altera a renderização nem introduz execução na GPU.

A comparação JFR separada apresentou peso amostrado de alocações nas pilhas do
Connect de aproximadamente 6,61 GB para 0,30 GB. **Isso não mede RAM residente
nem pico de heap.** As categorias de pilha são diagnósticas e podem se sobrepor.
Os JFR originais ficam preservados; `SummarizeConnectJfr.java` lê os eventos em
fluxo, evitando exportar centenas de MB de metadados JSON.

Um caso pequeno usado somente para conferir o CLI levou 134/158 ms numa dupla
e 278/241 ms em outra. Não são séries de benchmark estatístico; reforçam que
preparação de índices/JIT tem custo e o ganho não é uniforme.

## Equivalência e regressões

- **64/64 configurações comunitárias:** `EXACT_MATCH`. Comparação textual
  dos caminhos brutos, incluindo ordem/direção/repetições, áreas, cobre,
  parâmetros e diagnósticos por fresa. Zero diferenças ou casos não verificados.
- **96 verificações de distância:** zero falhas/erros, incluindo Rest, duas
  fresas, ângulos e MM/IN. Não substituem medição integral de material removido.
- **Suíte integral:** 2.477 casos, 2.459 aprovados, 18 opcionais ignorados,
  zero falhas/erros; `verify` verde em 5 min 42 s. Os nove controles do Connect
  passaram novamente após esclarecer os nomes/contrato de acesso às coordenadas.
- **Contraprova:** o JAR anterior, congelado antes da alteração, continua
  falhando nos dois controles de desperdício (2.144 buscas de arrays para 64
  caminhos e 63 leituras da borda). O candidato passa os nove controles.
  `LegacyConnectOracle` conserva a lógica de `47f32d8a` como referência histórica.
- **Três testes Python** protegem o comparador contra aceitar inversão de
  direção, caso ausente ou falha de orçamento como equivalência.

Essa alteração mantém a cobertura e os recaminhamentos anteriores. A cobertura
integral das placas comunitárias continua **NOT_MEASURED**, como explicado em
`LINES_REFINEMENT.md`. Otimizar a qualidade/ordem dos trajetos é um trabalho
separado de acelerar sua geração. Não houve ensaio físico de usinagem.

## Evidências e reprodução

Diretório desta rodada: `target/connect-optimization-20261110/`.

- `baseline-cam.jar`: cópia imutável usada como controle, fora dos outputs Maven.
- `baseline.jfr`, `optimized.jfr` e respectivos `*-profile.json`.
- `pairs-results.json`, `benchmark-summary.json`, `pairs/`: três pares completos.
- `optimized-matrix/`, `exact-comparison.json`: 64 casos e comparação exata.
- `distance/`, `verify.log`, `final-focused.log`: verificações do candidato.
- `red*.log`, `counterproof-final*.log`: testes anteriores e contraprova em binário.
- `environment.json`, `summary.json`: ambiente e consolidação da evidência.
- `benchmark-tool-smoke/`, `benchmark-tool-final-smoke/`: conferências do CLI.

Antes de compilar uma nova alteração, preserve o JAR de referência. Nunca use o
mesmo JAR sobrescrito pelo Maven nos dois lados da comparação. Execute da pasta
`flatcam-next`, substituindo `NEW-CONNECT` por um diretório novo:

```powershell
.\mvnw.cmd verify
python tools/benchmark_connect.py --baseline-jar target/connect-optimization-20261110/baseline-cam.jar --output target/NEW-CONNECT/pairs --pairs 3
java -Xmx4g '-Dflatcam.audit.caseBudgetSeconds=300' --class-path 'flatcam-cam/target/classes;flatcam-fx/target/dependency/*' tools/LinesCommunityProbe.java samples/community/validation/fx-import.json target/NEW-CONNECT/community
python tools/compare_connect_outputs.py --before target/lines-refinement-20261109/validated-community --after target/NEW-CONNECT/community --output target/NEW-CONNECT/comparison.json
java --class-path 'flatcam-fx/target/dependency/*' tools/SummarizeConnectJfr.java target/connect-optimization-20261110/baseline.jfr target/NEW-CONNECT/profile.json
python -m unittest discover -s tools -p test_compare_connect_outputs.py
```

O comparador exige `GENERATED` dos dois lados e a mesma lista de casos. Não une
nem normaliza os caminhos para esconder mudanças de trajeto. Os scripts usam
somente dependências já presentes no projeto. Instaladores/tag Beta 7 permanecem
os da entrega anterior; o código desta rodada será incluído na próxima Beta.
