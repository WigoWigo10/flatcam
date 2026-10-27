# NCC: preparação de componentes que se tocam em pontos — 2026-10-24

Continuação em 2026-10-26: [NCC_OFFSET_LOADING_REVIEW.md](NCC_OFFSET_LOADING_REVIEW.md)
reproduziu os dois erros Offset também após abrir o projeto no aplicativo
Python real, com a representação original dos objetos e hashes intactos.
Isso não muda os resultados nem eleva os erros abaixo a paridade.

Continuação do commit `79f2aedd`, branch `flatcam-next`. A investigação começa
na diferença B_Cu, painel 3×1 sem espaço, Rest + Itself + Connect, fresa de
1 mm, registrada em [MAIN_FLOW_MATRIX.md](MAIN_FLOW_MATRIX.md).

## Causa reproduzida

Observadores dos métodos originais Python capturaram os argumentos e os
resultados, sem mudar caminhos, predicados ou arquivos do legado. Cobre,
limite e subtração coincidem exatamente entre FX e Python. O FX aplicava
`buffer(0)` ao resultado inteiro da subtração. Isso preserva a área, mas
reprocessa os nós compartilhados por componentes que se tocam em pontos e
pode mudar o início dos anéis. A erosão seguinte e o Connect passam a usar
outros vértices; a diferença não se resume à ordem de visita dos caminhos.

O fluxo GUI original tem duas políticas distintas em `appTools/ToolNCC.py`:

- `gen_clear_area_rest` usa os polígonos da subtração diretamente.
- `gen_clear_area` aplica `p.buffer(0)` a cada polígono separadamente.

O buffer do conjunto inteiro não reproduz nenhuma dessas duas políticas.
O port de erosão GEOS existente, usado isoladamente, também não resolve a
limpeza extra; o defeito é a operação adicional e sua posição no fluxo.

## Correção do comparador de ferramenta única

A primeira matriz ampliada encontrou uma reprovação aparente de 0,032991 mm
em B_Cu 2×2, Itself + Connect sem Rest. A investigação expôs outra ocorrência
da mesma limpeza extra, desta vez no próprio comparador: `legacy_paths`
montava a área e aplicava `buffer(0)` ao conjunto em casos antigos sem tabela
de ferramentas. Os casos com tabela já chamavam o inicializador GUI real.

Essa matriz foi interrompida, preservada como incompleta em
`target/ncc-point-touch-matrix-20261024` e não conta como aprovação final.
O comparador agora adapta somente os metadados do formato de ferramenta
única e chama `legacy_ncc_tools`, com o mesmo código GUI original usado por
Rest e por múltiplas ferramentas. Conserva a geometria e o recipiente da
origem, parâmetros fornecidos e a entrada original. Não inventa tabelas FX
nem elimina erros do legado. O relatório identifica `pythonSingleToolAdapter`.

Uma regressão Python com a mesma figura pública falhou no caminho antigo.
A suíte final de **28 testes do comparador** passou. A mesma regressão falha
10/10 vezes com o módulo do commit `79f2aedd` em diretório isolado e passa
10/10 com o atual; `adapter-counterproof-final.log` preserva a contraprova.
O B_Cu 2×2 passou ao usar a GUI original: distância 5,68e-14 mm e delta
relativo de comprimento zero. Não houve mudança de tolerância, desenho,
traçado FX ou fonte `appTools/ToolNCC.py` para obter esse resultado.

## Reprodução pública e regressão

Figura independente, sem dados da placa privada: retângulo de 30×20 e
losango de cobre `(5 20, 2 10, 5 0, 8 10, 5 20)`. As duas regiões vazias
tocam-se nas pontas do losango. A limpeza conjunta muda o início do anel
direito de `(30,20)` para `(5,0)`.

Com fresa de 1 mm, sobreposição 40% e 64 segmentos por quadrante:

| Modo | Comprimento Python original (mm) |
| --- | ---: |
| Sem Connect, com ou sem Rest | 851,904024978 |
| Com Connect, com ou sem Rest | 883,699523548 |

Antes do ajuste, o FX com Connect media 881,415034449 mm. A regressão Java
falhou com esse valor antes da alteração. Um ponto testemunha estava a
0,248191566 mm do caminho Python. O teste ampliado cobre MM/IN e todas as
combinações Rest/Connect. A expectativa de representação do teste ISO/Rest
também foi corrigida: ela copiava a limpeza extra, em vez do fluxo original.

`tools/NccRestSeamProbe.java` exporta oito controles públicos para os
handlers GUI originais e o parser G-code do Python. Não instala dependências,
não abre janela e recusa sobrescrever uma pasta de resultados existente.

```powershell
# Com o reactor compilado e instalado no cache Maven local, obtenha as dependências CAM.
.\mvnw.cmd -q -pl flatcam-cam dependency:build-classpath '-Dmdep.includeScope=test' '-Dmdep.outputFile=../target/ncc-rest-seam-classpath.txt'
$probeCp = 'flatcam-cam/target/classes;' + (Get-Content target/ncc-rest-seam-classpath.txt -Raw).Trim()
java --class-path $probeCp tools/NccRestSeamProbe.java target/ncc-seams-novo
target/oracle-py311/Scripts/python.exe tools/compare_cam_python.py --legacy-root .. --fx-export target/ncc-seams-novo/MM.json --output target/ncc-seams-novo/MM-python --strict
target/oracle-py311/Scripts/python.exe tools/compare_cam_python.py --legacy-root .. --fx-export target/ncc-seams-novo/IN.json --output target/ncc-seams-novo/IN-python --strict
```

## Validação pública

Os oito controles de `tools/NccRestSeamProbe.java` passaram com o código
ajustado: **4 MATCH_SAMPLED em MM e 4 em IN**, strict exit 0 nas duas
execuções. Incluem Rest ligado/desligado e Connect ligado/desligado, área
preparada, CAM por ferramenta e interpretação do G-code FX pelo Python.
Antes de ajustar a limpeza por polígono, os dois controles sem Rest
divergiam em cada unidade; a maior distância amostrada com Connect era
0,348006309 mm no controle MM.

O teste Java ampliado também falhou antes da segunda correção, no controle
`Rest=false, Connect=false`, com comprimento 851,898843619 em vez de
851,904024978 mm. A suíte focada final passou: 63 testes de NCC, Paint e
buffer. Logs em `target/connect-investigation-20261024`, sem geometria
privada no repositório.

Regressão: [NccGeneratorTest.java](flatcam-cam/src/test/java/org/flatcam/cam/ncc/NccGeneratorTest.java),
`pointTouchingAreaSeamsSurvivePreparationAcrossRestConnectAndUnits`.
Ambiente: Windows, JDK 25.0.4.1, JTS 1.20.0; oráculo Python 3.11.1,
Shapely 1.8.5.post1 e GEOS 3.10.3. Baseline de código `79f2aedd`.

Contraprova sem tocar no código em execução da matriz: o `NccGenerator.java`
de `79f2aedd` foi compilado em classpath isolado dentro de `target`, e a mesma
regressão compilada falhou **10/10** vezes com ele. Restaurando o classpath
das classes corrigidas, passou **10/10** vezes. Logs `revert-red-10.log` e
`restored-green-10.log`. As figuras são fixas; não há rede, relógio, seed
aleatória, normalização de anéis ou configuração do usuário nesse teste.
Não há versão anterior comprovadamente boa para esse caso; o `git blame`
rastreia a linha de limpeza, mas não é usado como prova de introdução por
um commit específico nem como substituto de `git bisect`.

## Caso real isolado

Reexportação de produção do painel B_Cu 3×1 sem espaço, sem editar a fonte:
`ncc-rest-itself-connect` passou no modo strict, com as duas ferramentas
conferidas separadamente. A região a limpar continuou igual.

| Fresa | Distância amostrada antes (mm) | Depois (mm) | Delta relativo de comprimento depois |
| --- | ---: | ---: | ---: |
| 1 mm | 0,218295261 | 5,68e-14 | 1,22e-16 |
| 0,2 mm | já atendia aos critérios | 1,07e-14 | 3,32e-16 |

Artefatos privados: `target/connect-investigation-20261024/real-rest-isolated`.
A exportação desta contraprova usou a primeira correção Rest; a correção
seguinte alterou somente o ramo sem Rest. A validação final reexportou o
painel 3×1 com ambos os ramos da versão final e repetiu o resultado.

## Validação final encerrada

| Conjunto | MATCH_SAMPLED | Diferenças CAM/G-code/parciais | ORACLE_ERROR |
| --- | ---: | ---: | ---: |
| Real 2×2, espaçamento 5/5 | 19 | 0 | 0 |
| Real 3×1, espaçamento zero | 19 | 0 | 0 |
| Real 1×2, espaçamento 2/7, avançado | 15 | 0 | 2 |
| Públicos padrão e avançado MM/IN | 148 | 0 | 0 |
| Total | **201** | **0** | **2** |

**203 controles, 33 relatórios completos; strict equivalente continua 1**.
Todas as comparações NCC usam agora o inicializador GUI original. Os dois
erros são `ncc-offset-reference` nas duas faces reais; não contam como
paridade. A região, CAM por ferramenta quando há tabela e interpretação
do G-code FX pelo Python foram conferidos, sem relaxar critérios.

Os três fluxos FX passaram com registro de furos e reabertura nativa. Uma
fonte real, SHA-256 antes/depois igual. Os exports completos de 2×2 e dos
públicos padrão, já produzidos com o Java final antes de corrigir o
comparador, foram reutilizados; 3×1 e avançado foram reexportados. Todas as
comparações Python foram refeitas, com faces independentes em processos
separados; os builds Maven permaneceram em série. A matriz intermediária
interrompida fica fora da contagem final.

Resumo e links: `target/ncc-seams-final-validation-20261024/index.html` e
`summary.json`. O resumo confere 33 relatórios/203 casos, layouts, hashes dos
exports FX, fontes originais Python consistentes, fluxo GUI em todos os
NCC válidos e hash do projeto intacto. Scripts auxiliares e geometria privada
ficam em `target/connect-investigation-20261024`, sem entrar no Git.

Isso amplia a evidência de CAM e exportação; não mede paridade global do app
nem prova outra placa, reconstrução independente dos recortes internos de
Cutout, plot visual ou comportamento físico de uma CNC.

## Limites

O verify integral final passou: **1868 aprovações Java, 18 skips opcionais**,
1886 registros desta execução, zero falhas/erros. Evidências em
`target/connect-investigation-20261024/full-verify-diagnostic.log`,
`full-verify-totals.json` e `full-verify-suites.json`; a contagem foi
preservada antes dos ensaios privados seguintes sobrescreverem XML.

A primeira execução integral teve uma falha no teste
`CncNativePrecisionTest.drillingReviewIncludesEffectiveOffsetDepthFeedAndEndPositions`
ao conferir o texto de End Z. Não se repetiu no ensaio isolado, no ensaio
com módulos empacotados nem na reexecução integral. A causa não foi
confirmada. O teste continua ativo, com a mesma expectativa; sua mensagem
agora inclui os valores do relatório para diagnóstico se voltar a falhar.
O primeiro log foi preservado em `full-verify.log`; não foi descartado como
prova de sucesso nem corrigido reduzindo a exigência numérica.

Os dois erros originais de NCC Offset ao receber uma lista aninhada de
MultiPolygons continuam separados desta correção. Não serão convertidos em
paridade alterando a entrada, o algoritmo do oráculo ou suas tolerâncias.
Os arquivos privados da investigação ficam exclusivamente em `target`.
Comparações numéricas e leitura de G-code não certificam uma máquina física.
