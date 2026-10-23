# Paridade CNC: parâmetros executados, não só cortes XY

## Marlin: validade do dialeto e configuração G0 — 2026-10-23

G94 removido da saída MARLIN, M400 final após estacionamento. Leitor diferencia
G0 shared/variable/fixed, com condição de firmware explícita. As rodadas dos
104 controles por modo continuam 0/68/34/2/0, sem problemas nos parâmetros FX.
Em fixed=1500 mm/min, a omissão de F no legado não implica avanço diferente;
o aviso anterior de 102 programas vale para shared/variable. Ver [MARLIN_PARITY.md](MARLIN_PARITY.md).

## Comparação adicional de perfis — 2026-10-23

Harness aceita também MARLIN, LINE_XYZ e ISEL_CNC. Marlin modela M400 e feed
dos rápidos; ISEL MM modela G71 e M01 opcional. Nenhum problema nos parâmetros
solicitados ao FX nestes controles, mas NÃO há equivalência completa nesses
novos perfis. line_xyz original falha em todos os 104 controles; não conta
como paridade. GRBL mantém 49/19/34/2/0. Diagnóstico por fresa, resultados e
restrições em [CNC_FOLLOWUP_REVIEW.md](CNC_FOLLOWUP_REVIEW.md).

## Série de seis lacunas — 2026-10-14

Comparador ampliado com `-Profile GRBL_11_NO_M6|GRBL_11|DEFAULT|DEFAULT_NO_M6`
e diagnóstico de cortes por ferramenta, sem substituir a equivalência ordenada.
104 controles por perfil, cada um 49/19/34/2/0 (execução igual/rota diferente/
usinagem diferente/erro Py/erro FX); auditoria FX sem problemas de parâmetros.
M6 explícito é modelado, movimentos internos da macro não. 28 auxiliares passam.
Round-trip real descobriu/corrigiu sobrescrita de ajustes pelo Python e tornou
explícitas suas restrições. Cobertura conhecida transportada/PAINT e revisão de
exportações existentes integradas. [CNC_SIX_STEP_REVIEW.md](CNC_SIX_STEP_REVIEW.md)
documenta todos os ensaios, novas restrições e o que ainda não foi validado.

## Drill slots: alternativa explícita à fresagem — 2026-10-14

Drilling oferece **Drill slots — furar rasgos**, sobreposição (0 ≤ valor < 100%)
e último furo, por ferramenta. Desativado mantém a fresagem lateral anterior.
Ativado acrescenta centros interpolados aos furos da ferramenta, sem deduplicar,
usando passo `diâmetro × (1 − sobreposição/100)` e a regra Python para último
centro (distância ≥ passo/10). Rasgo degenerado gera um centro; conversão exige
diâmetro real positivo. A fonte Excellon não muda. Furos convertidos usam peck,
offset/profundidade, seleção/ordem de ferramenta, proximidade opcional e as
mesmas exclusões por raio/rotas dos furos existentes. Prévia mostra círculos,
não uma cápsula fresada. Não comprova remoção integral nem segurança física.

Parâmetros persistidos no nativo JSON/XZ, snapshot FX de `.FlatPrj` e chaves
Python `tools_drill_drill_slots`, `tools_drill_drill_overlap`,
`tools_drill_last_drill`; importação Python/Tools Database reconhece as chaves.
Ausência das novas chaves nativas mantém fresagem. Restos de material entre
furos e diferença entre broca/fresa são avisados no tooltip/resumo.

Conversão roda no worker, com progresso/cancelamento antes da publicação
atômica. Limite preventivo: um milhão de furos convertidos por tarefa e rasgo;
**ajustável futuramente**, não uma tolerância geométrica. Recusa explosão de
quantidade, 100%/NaN/diâmetro inválido, sem substituir o arquivo escolhido.

Corpus GRBL **104**: 49 MATCH_EXECUTION, 19 ROUTING_DIFFERENT,
34 MACHINING_DIFFERENT, 2 ORACLE_ERROR, zero FX_ERROR. Os 92 status anteriores
ficaram iguais. Os 12 novos controles (MM/IN, passe único/peck, último furo,
zero overlap, proximidade e dwell) passam auditoria de centros/multiplicidade,
profundidades e avanços nos dois geradores, mas diferem na **ordem dos cortes**;
strict continua reprovado, sem ampliar tolerância. No exemplo simples MM,
FX percorre 12 mm de rápidos XY contra 13,25 mm do Python. Não copiar uma ordem
mais longa para aumentar o placar. O auditor obtém os centros esperados do
**corpo original** `ToolDrilling.process_slot_as_drills`, não do algoritmo FX.
Os quatro controles antigos de rasgos fresados continuam marcando operações
diferentes; ativar Drill slots não redefine retroativamente esses casos.

Evidência: `target/drill-slots-oracle-20261014/python-reviewed/index.html`;
`DrillSlotsTest`, `DrillSlotsPersistenceTest`, `DrillSlotsPanelTest` e teste de
cancelamento do worker. O round-trip do aplicativo Python real após regravar
esses novos parâmetros ainda não foi executado; testes aqui são de codecs e
geradores. A UI Python pode aplicar overlap/último furo do controle comum;
valores distintos por ferramenta no FX não provam a mesma geração no legado.

Outro limite observado ao inspecionar o conversor original: com diâmetro 1,
overlap 0 e rasgo diagonal `(3,4) → (0,0)`, Python/GEOS 3.10.3 retorna só início
e fim; o `within(LineString)` rejeita o primeiro centro interpolado por precisão
numérica. FX mantém os seis centros do passo unitário, cobertos no teste do
core. Não reproduzir a perda de furos do legado. Os 12 controles de comparação
novos são horizontais; não abrangem essa divergência diagonal nem todo Excellon.

27 testes auxiliares do leitor/auditor Python passaram, incluindo controle
individual de rasgos/último furo e registro da divergência diagonal. Fontes
Python/GEOS do oráculo permanecem intactas (SHA-256 conferido pelo harness).

Build completo `verify` aprovado: 1728 registrados, **1710 passaram**, 18 skips
opcionais e zero falhas/erros, incluindo test-support. Log final:
`target/drill-slots-full-retry-20261014.log`. Primeira rodada teve dois erros
de limpeza `@TempDir` em TclLiveHostTest (arquivo em uso/DirectoryNotEmpty),
não falhas de asserção; nenhum teste foi alterado/suprimido para a repetição.
Log inicial preservado em `target/drill-slots-full-20261014.log`.

## Tipos de contorno, múltiplos furos e rasgos — 2026-10-13

Novo modo explícito Geometry **Contínuo; mesma direção (fechados)**:
`CONTINUOUS_SAME_DIRECTION`. Repete contornos fechados no mesmo sentido, sem
retração intermediária, como `LinearRing` no Python. Não altera os dois modos
anteriores nem o default. Caminhos abertos são recusados antes de produzir o
CNC Job; valida o caminho depois da compensação. Pontos usam somente o Z final.
Preservar direção é permitido; Extra Cut/sonda continuam incompatíveis com
passes contínuos. Não alternar um anel implicitamente só por estar fechado:
o Python alterna LineStrings (mesmo fechados), mas não LinearRings.

Cobertura: `GeometryClosedPassStrategyTest` (anel/polígono/LineString fechado,
furos internos, sem lift intermediário, validação de abertos/mistos, compensação,
cancelamento e pontos); persistência de enum e tipo LinearRing no nativo;
seletor/restauração e revisão. A regressão `.9/.3` agora testa todos os três modos
com uma forma válida para cada um, mantendo três passes e todas as travessias.
A primeira rodada completa encontrou que o teste antigo iterava todos os enums
usando um caminho aberto; o teste foi ampliado com um contorno fechado para o
novo modo, não foi removida a verificação. Logs preservados em
`target/cnc-typed-pass-core-20261013.log` e `target/cnc-typed-full-20261013.log`.

Corpus GRBL ampliado de **62 a 92** controles, todos os anteriores mantidos:
anéis single/contínuo/alternado, Polygon com furo single/contínuo, Point
single/contínuo/retraído, LineString fechado mesma direção; quatro receitas
de vários furos (incluindo duplicados, peck, duas ferramentas, ordem origem/
proximidade); slots single/peck. Cada receita em MM/IN. WKT padrão + tipo
explícito reconstrói LinearRing no oráculo, sem convertê-lo silenciosamente em
LineString. Audita bordas, buracos, profundidades e multiplicidade de segmentos,
além das verificações anteriores. Não aceita um contorno errado com a mesma
quantidade de linhas. 25 testes auxiliares Python aprovados.

Resultado final: **49 MATCH_EXECUTION, 19 ROUTING_DIFFERENT,
22 MACHINING_DIFFERENT, 2 ORACLE_ERROR, 0 FX_ERROR**. Os 62 controles anteriores
mantêm suas classificações; todos os 92 FX sem problemas nos parâmetros.
Quatorze equivalências adicionais: seis receitas de Geometry × MM/IN
(anel single/contínuo, Polygon single/contínuo, Point single/contínuo) e
furação múltipla em ordem de origem × MM/IN. Strict permanece **1**.
Relatório: `target/cnc-typed-final-oracle-20261013/python/index.html`.

As diferenças adicionais NÃO significam contornos/furos perdidos:

- Alternar anéis, repetir LineString fechado sem alternar ou peck em Point são
  escolhas explícitas FX diferentes dos comportamentos Python. O tipo e a
  direção realmente executada são comparados, sem normalizar para esconder isso.
- Furação por proximidade FX versus OR-Tools Basic original Python: ambas
  visitam todos os furos/passadas, inclusive repetidos, mas ordem diferente é
  `MACHINING_DIFFERENT`, pois a assinatura mantém cortes ordenados. No exemplo
  MM, FX faz 14 mm de rápidos XY, Python 22 mm. Não copiar uma ordem pior para
  elevar o placar; não é prova de tempo melhor em todos os projetos.
- **Rasgos são uma lacuna funcional distinta:** FX fresa o segmento de ponta
  a ponta. Python Drilling usa **Drill slots** para converter o rasgo em cadeia
  de furos sobrepostos. O comparador executa o corpo original
  `ToolDrilling.process_slot_as_drills` (25% de sobreposição e último furo
  ativado nestes controles) e o gerador Excellon original. O relatório explicita
  `operationDifference` e os pontos convertidos. São operações diferentes,
  não equivalência aprovada nem erro do oráculo. Falta oferecer essa alternativa
  de furação de rasgos no FX sem remover sua fresagem atual.

O auditor Python verifica a multiplicidade de visitas independente da ordem
que seu otimizador escolhe; o auditor FX verifica a ordem solicitada (origem ou
nearest). A comparação ordenada de cortes/eventos e o strict não foram relaxados.
Fontes originais (incluindo ToolDrilling) têm SHA-256 antes/depois da rodada;
nenhum algoritmo legado ou tolerância foi alterado.

Build integral aprovado em `target/cnc-typed-full-retry-20261013.log`:
1712 registrados, 1694 aprovados, 18 opcionais ignorados, zero falhas/erros
(somente relatórios com fontes atuais). Auditoria de tooltips: 1099 ocorrências,
zero MISSING. Rodada diferencial final feita após o build confirmou os números.
Não houve teste físico nem validação manual de todos os projetos/painéis.

## Travel Z individual explícito, passes e RPM zero — 2026-10-13

Geometry → CNC, **Posições e troca de ferramenta → Política de Travel Z**:
**Maior Travel Z do trabalho** continua o padrão; **Travel Z de cada ferramenta**
é uma escolha explícita para fresagem comum, correspondente à altura individual
do fluxo Python testado. Usa a altura da fresa depois da troca, entre trajetos e
retornos de passes. Estacionamento e End Z automático usam a última fresa ativa;
End Z abaixo dessa altura só é atingido depois de End X,Y. Ferramentas vazias
não substituem a altura final. Troca continua comum, validada contra o maior
Travel Z do trabalho; Over mantém seu piso/alturas de exclusão. Laser/HPGL/Roland/
sondagem recusam a opção não padrão, não a ignoram ao gerar.

Não se redefine silenciosamente um projeto antigo. `GeometryTravelClearance`
persiste em `jobOptions.travelClearance` no nativo JSON/XZ; ausente = JOB_MAXIMUM,
valor desconhecido = erro de leitura. Helpers de exclusão/roteamento preservam
a escolha. Aplicar sugestões comuns da DB também preserva política e roteamento
(este último era perdido ao reconstruir posições). O painel restaura a escolha;
o resumo distingue valor configurado, efetivo por fresa e estacionamento.
Regravação real pelo Python e novo argumento Tcl ainda não foram validados;
não presumir preservação da extensão privada após regravar pelo legado.

Passes contínuos alternados já existiam: seguem opção por fresa, não novo default.
Foram combinados com altura individual e trajetos separados no oráculo. Retração
conserva direção; contínuo alterna e não aceita preservar direção/Extra Cut/sonda.
Não copiar a alternância de todas as variantes Polygon/LinearRing como se fossem
LineStrings: esses tipos ainda exigem corpus dedicado.

RPM zero continua **acionamento/parada manuais**, sem partida automática. Testes
Geometry/Drilling com troca ligada/desligada e com fresa anterior em RPM positivo
garantem ausência de M3/M4 automático no trecho zero. Isso não significa enviar
M5 final nem simular partida manual/macros. Aviso/tooltip agora explicitam a
ausência de garantia de parada final. Auditor FX recusa partida automática em
RPM zero. O Python emite M3 sem S e, no caso sem troca, pausa mesmo com troca
desligada: diferenças mantidas, não copiadas para elevar equivalências.

Corpus ampliado para **62 controles**, mantendo os 54 anteriores. Oito novos:
duas fresas, trajetória única/separada, single/contínuo, MM/IN, altura individual
explicitamente selecionada. **Todos os oito MATCH_EXECUTION** e auditorias de
parâmetros aprovadas. Total: **35 MATCH_EXECUTION, 19 ROUTING_DIFFERENT,
6 MACHINING_DIFFERENT, 2 ORACLE_ERROR, 0 FX_ERROR**; strict **1**, sem relaxamento.
As 54 classificações anteriores foram preservadas. Os modos conservadores
antigos continuam no corpus, não foram substituídos pelos modos compatíveis.
Relatório final: `target/cnc-travel-policy-final-oracle-20261013/python/index.html`.

Testes `GeometryTravelClearanceTest`, `SpindleZeroContractTest`,
`GeometryPerToolProjectTest`, `CncRoutingFieldsTest`, `CncPreflightTest` e
`TooltipAuditTest`: Z modal realmente emitido, alturas/pausas, parking, vazios,
compatibilidade de construtores/arquivos, perfil inválido e retorno ao painel.
20 testes auxiliares do auditor Python aprovados. Logs direcionados:
`target/cnc-travel-policy-core-20261013.log` e
`target/cnc-travel-policy-ui-retry-20261013.log`. A primeira tentativa de UI
verificava Tooltip nativo, mas a ajuda central usa propriedades/accessibleHelp;
a asserção foi corrigida para o contrato real, não foi removida a ajuda.

Pendências: decisões sobre Start Z/primeira troca e precisão de diâmetros IN,
variantes de passes Polygon/LinearRing/pontos, outras fresas/perfis/projetos,
Tcl explícito e validação visual/física manual. A diferença Around mais longo
e omissão de altura Over sobreposta do Python não devem ser reproduzidas.

Build completo aprovado em `target/cnc-travel-policy-full-20261013.log`:
1705 testes registrados, 1687 aprovados, 18 opcionais ignorados, zero
falhas/erros (somente relatórios com fonte atual). Tooltips: 1099 ocorrências,
zero MISSING no cenário construído. Nova rodada diferencial após o build
confirmou os mesmos números, sem alterar fontes Python nem tolerâncias.

## Over segmentado, Around e primeira troca — 2026-10-13

`CncExclusionPlanner.travelLegs` divide a rota nas entradas/saídas das áreas
Over ampliadas pelo raio + 0,1 mm equivalente. O emissor troca Z antes de
percorrer cada segmento, sem baixar de Travel Z. Em sobreposição, usa o máximo
das alturas presentes no trecho; áreas baixas não criam descidas/segmentos
desnecessários. Around conserva sua rota de visibilidade. A API `travel`
anterior permanece disponível como plano conservador de altura única; os dois
geradores CNC agora emitem o plano por trechos. Não muda o formato salvo.

Os sete testes `CncOverTravelTest` verificam os segmentos e o XYZ realmente
emitido: MM/IN, sobreposição, separação, furos internos, reverso, Around misto,
cancelamento, Z seguro alto, Drilling/Geometry e quatro perfis de fresagem.
Quatro asserções antigas exigiam subir desde a origem e descer só no destino;
foram substituídas pela sequência exata de entrada/subida/saída/descida, sem
remover validação de altura, troca ou unidades. Primeira tentativa registrada
em `target/cnc-over-first-20261013.log`; validações direcionadas aprovadas em
`target/cnc-over-targeted-20261013.log` e `target/cnc-over-emitted-20261013.log`.
Tooltips e revisão antes de salvar explicam as transições.

Corpus ampliado para **54 controles**, mantendo os 46 anteriores. Adicionados:
Drilling Over com diâmetro representável em quatro casas, duas áreas separadas
e duas sobrepostas; Geometry Over; todos em MM/IN. O relatório inclui distâncias
XY/Z dos rápidos realmente emitidos, não estimativas de tempo/segurança.
19 testes auxiliares Python aprovados. Fontes originais e tolerâncias intocadas.

Resultado: **27 MATCH_EXECUTION, 19 ROUTING_DIFFERENT, 6 MACHINING_DIFFERENT,
2 ORACLE_ERROR, 0 FX_ERROR**; 54 saídas FX sem problemas na auditoria de
parâmetros. `drilling-over-mm` passou de diferença de rota a execução equivalente;
as outras 45 classificações anteriores não regrediram. Quatro equivalências
adicionais vêm dos controles novos, incluindo Over IN com diâmetro representável.
Relatório: `target/cnc-over-expanded-20261013/python/index.html`; strict **1**.

Diferenças investigadas e mantidas explicitamente:

- **Start Z versus troca:** o Python GRBL usa Start Z na primeira pausa, mesmo
  quando difere de Tool change Z. O FX conserva a altura de troca solicitada;
  novo aviso com link para esse campo explica a distinção. Não é copiado como bug.
- **Around:** no controle público MM, Python percorre 13,9153 mm de rápidos XY,
  FX 10,7153 mm, ambos sem infringir a auditoria de exclusão. Não se alonga a rota
  FX só para imitar o legado. Não é prova de melhor tempo em qualquer projeto.
- **Over IN com diâmetro completo:** Python arredonda `1/25,4` para `0,0394`
  antes de ampliar a exclusão. A entrada emitida `0,1338` difere do FX `0,133858`
  além do limite original de arredondamento. Não truncar a ferramenta do FX nem
  ampliar a tolerância para esconder essa diferença.
- **Over sobreposto:** nos dois novos controles MM/IN, Python cruza a segunda
  região abaixo de seu Over Z. O auditor acusa isso; a assinatura de corte
  concorda, mas o deslocamento não é aprovado. FX usa a maior altura necessária.

Ainda faltam decisões explícitas para compatibilidade de Travel Z individual
versus global, passes retraídos/alternados e spindle zero, ampliar trajetos reais
e outros perfis. Análise nominal 2D e auditoria XYZ limitada não validam haste,
cabeçote, grampos reais, macros, limites físicos ou máquina. Teste a seco necessário.

Build completo aprovado: `target/cnc-over-full-20261013.log`, 1696 testes
registrados, 1678 aprovados, 18 opcionais ignorados, zero falhas/erros, contando
somente fontes atuais. Tooltips: 1097 ocorrências, zero MISSING no cenário
construído. Não houve teste físico nem interação manual com a máquina.

## Altura global entre trajetos/passadas — 2026-10-13

A investigação das rotas encontrou um defeito FX que a prévia 2D não mostrava:
o trabalho anunciava o maior Travel Z das ferramentas como altura comum,
mas retraía ao Travel Z individual entre trajetos/passadas. Sem exclusões,
o helper de viagem emite só XY, portanto podia deslocar em Z3 mesmo com altura
comum Z4. As duas retrações agora emitem a altura global. O resumo distingue
Travel Z configurado de **Retração / Travel Z efetivo**, sem alterar valores,
profundidades, feeds, spindle ou direção dos passes.

`GeometryTravelClearanceTest` lê o Z modal emitido e reproduziu o defeito antes
da correção (quatro testes falhando em `target/cnc-clearance-before-20261013.log`).
Depois passaram, incluindo MM/IN, retorno entre passes, trajetos separados,
contínuo, troca ligada/desligada, segunda fresa mais baixa e estacionamento com
End Z baixo. Regressões dos geradores/troca/exclusões também passaram em
`target/cnc-clearance-targeted-20261013.log`.

Corpus GRBL ampliado para **46 controles**, mantendo os 42 anteriores. Os
quatro novos usam duas fresas e MultiLineString separado, single/contínuo ×
MM/IN. O auditor conta mergulhos e travessias de todos os caminhos; audita
também o contrato global do FX, separadamente da política individual Python.
18 testes auxiliares do leitor/auditor aprovados. Fontes Python e tolerâncias
não foram alteradas.

Resultado: **22 MATCH_EXECUTION, 16 ROUTING_DIFFERENT, 6 MACHINING_DIFFERENT,
2 ORACLE_ERROR, 0 FX_ERROR**; todas as 46 saídas FX sem problemas na auditoria
dos parâmetros. Os quatro novos concordam na assinatura de usinagem, mas não
nos deslocamentos. Relatório: `target/cnc-clearance-oracle-20261013/python/index.html`.
Strict permanece **1**. A quantidade de diferenças de rota cresceu porque
há quatro controles adicionais, não porque equivalências anteriores regrediram.

O FX mantém a altura global conservadora; Python usa Travel Z individual.
Não foi reduzida silenciosamente a altura global para igualar seus G-codes.
Start Z versus altura da troca, Around/Over, passes com retração e RPM zero
continuam diferenças registradas; ainda exigem decisões explícitas de política.
Nenhuma prévia ou auditoria limitada substitui validação física/teste a seco.

Build completo aprovado em `target/cnc-clearance-full-20261013.log`:
1688 testes registrados, 1670 aprovados, 18 opcionais ignorados, zero
falhas/erros (somente relatórios com fontes atuais). Auditoria de tooltips:
1096 ocorrências, zero MISSING. Confirmadas as mesmas classificações para
todos os 42 controles anteriores.

## Duas fresas e revisão GUI — 2026-10-13

Corpus ampliado para **42** controles, sem remover os 38 anteriores. Quatro
novos casos multigeo (single/contínuo × MM/IN) chamam a rotina original Python
`geometry_tool_gcode_gen`. A comparação descobriu e corrigiu uma passada extra
FX em `.9/.3`; a auditoria passou a contar travessias XY, além dos mergulhos.
Contagem de passes agora é compartilhada entre resumo GUI e gerador.

Resultado final: **22 MATCH_EXECUTION, 12 ROUTING_DIFFERENT,
6 MACHINING_DIFFERENT, 2 ORACLE_ERROR, 0 FX_ERROR**; strict **1**. Os quatro
controles novos concordam na usinagem e nos parâmetros, mas não nas rotas.
Relatório: `target/cnc-review-oracle-fixed-20261013/python/index.html`.

Geometry e Drilling têm revisão por fresa antes do FileChooser, com links para
campos e avisos explícitos. O mapa posterior está limitado ao NCC com área-alvo
preservada ([CNC_COVERAGE.md](CNC_COVERAGE.md)); não simula segurança física.
Exportações de CNC Job existente/Terminal não mudam.
Contrato e limites: [CNC_REVIEW.md](CNC_REVIEW.md).

## Série 3/4: política explícita de passes — 2026-10-12

Geometry CNC permite selecionar por ferramenta `RETRACT_SAME_DIRECTION`
(default anterior) ou `CONTINUOUS_ALTERNATING`. A opção aparece junto de
Multi-Depth, participa de Aplicar a todas e persiste no projeto nativo.
Contínuo desce no ponto final e inverte as coordenadas para o próximo passe,
inclusive em LineString fechado, sem um rápido lateral dentro do material.
O endpoint efetivo, inclusive em número par de passes, alimenta a próxima rota.
Ponto isolado usa apenas a profundidade final, como a rotina Python testada.
Alternância não é otimização universal: troca direção Climb/Conventional.
Preservar direção, Extra Cut e sonda são incompatíveis e recusados; laser,
plotter e Roland não usam a nova política. Não simula segurança física.

Corpus ampliado **38 casos**, mantendo todos os 34 anteriores e adicionando
contínuo aberto/fechado × MM/IN. Python original repetido, sem alteração de
fontes/tolerância: **22 MATCH_EXECUTION, 8 ROUTING_DIFFERENT,
6 MACHINING_DIFFERENT, 2 ORACLE_ERROR, 0 FX_ERROR**. Os quatro controles novos
contínuos concordam. As quatro receitas antigas com retração continuam
divergindo por escolha explícita; não foram removidas para melhorar o placar.
Strict permanece **1**, não foi relaxado e não significa paridade total.

Restantes: Start Z versus altura explícita da primeira troca, Around/Over,
RPM zero com troca desligada e erros Dwell zero do legado. Preservadas as
políticas mais conservadoras do FX em vez de copiar movimentos/acionamentos
sem autorização específica. Ainda falta ampliar o oráculo para várias
geometrias/fresas, slots, casos reais e perfis; LinearRing/Polygon do Python
não têm necessariamente a mesma alternância de um LineString fechado.
Não houve harmonização de todas essas variantes nesta etapa.

Regressões de modos, direção, endpoint par, profundidade de pontos,
cancelamento, persistência e painel; logs target/series-3-passes-20261012.log.
Oráculo: target/series-3-cnc-20261012/python/index.html (strict 1 esperado).

## Primeiro incremento — 2026-10-12

Branch `flatcam-next`, base `e76dacb8`. Incluído no commit de consolidação
de 2026-10-12 (consultar git log), sem push.
O comparador anterior lia os cortes XY pelo parser Python. Este incremento
executa os geradores CNC **originais** e lê o estado XYZ/F/S/T de ambas as saídas.
Não é simulação física, aprovação de máquina nem paridade de todos os perfis.

### Correções de produção

- Drilling e Geometry CNC: parada explícita do spindle antes da **troca inicial**
  nos perfis de fresagem portados. Não se presume que a máquina esteja parada
  só porque um arquivo novo foi aberto. Probing conserva sua sequência própria;
  o contrato manual do FX portable sem troca inicial permanece.
- Drilling portado restaura `G90` e a altura de troca após a pausa, também sem
  posição XY explícita. Geometry restaura a altura de deslocamento após a troca,
  evitando começar o mergulho na altura maior reservada à troca.
- Não baixar primeiro até Travel Z para depois subir até Tool change Z antes
  da troca inicial portável. A sequência sobe diretamente à altura validada.
- Drilling rejeita Tool change Z abaixo do maior Travel Z selecionado quando
  a troca está ativa, mesmo sem exclusões ou posição XY de troca.

Não se alteraram a profundidade final, a política de passes, os cálculos CAM,
os arquivos originais Python ou as tolerâncias dos comparadores CAM anteriores.

### Reprodução

Na raiz do worktree:

```powershell
.\flatcam-next\compare-cnc-parameters.ps1 -Strict
```

O runner usa o ambiente já existente `target/oracle-py311`, ou `-Python` e
`-DependencyPath`. Exige Python 3.11 / Shapely 1.8.5.post1 / GEOS 3.10.3 e
OR-Tools disponível, sem instalar dependências. Resultados em uma pasta nova
sob `flatcam-next/target`: HTML com links para cada G-code e trace XYZ, JSON,
logs de exportação e ambiente. Não modifica preferências ou projetos privados.

Exportador: `PythonCncParametersExportTest`. Oráculo:
`tools/compare_cnc_python.py`. São usados `CNCjob.excellon_tool_gcode_gen`,
`generate_from_geometry_2`, o otimizador OR-Tools Basic e o preprocessor real
`GRBL_11_no_M6`. `ExclusionAreas.travel_coordinates` e seus dois helpers são
compilados do AST original, sem substituir o algoritmo. Apenas serviços de UI
são stand-ins. Erros que `doformat2` registra/engole também reprovam o oráculo.
O relatório registra as versões e SHA-256 dos fontes antes/depois da rodada.

Testes novos: `CncInitialToolChangeTest` (inclui alturas iguais e seleção ISEL
ICP antes do deslocamento), exportador público e 15 regressões auxiliares do
leitor/auditoria. A suíte auxiliar completa tem 70 testes Python aprovados.
Runner também verificado com espaço no caminho de saída; parser PowerShell
e `git diff --check` aprovados.

### Escopo e critérios

34 casos públicos: **17 receitas × MM/IN**, não 34 projetos distintos.

- Drilling: um furo por ferramenta, single/peck, offset positivo/negativo,
  duas ferramentas com parâmetros distintos, seleção só da segunda, posições,
  dwell positivo/zero, RPM zero, Around e Over.
- Geometry: uma ferramenta, caminho aberto single/peck, LineString fechado
  peck, posições e dwell. Feed XY difere do feed Z para detectar confusão.
- Compara ordem, repetição, profundidades, avanços efetivos, spindle/RPM, tool,
  pausas e tempo de dwell. Não une caminhos nem elimina furos ou passes repetidos.
- Audita também a saída contra os parâmetros pedidos, para que duas saídas
  igualmente erradas não sejam aprovadas; verifica diâmetro nas exclusões e Z
  efetivo do Over, não apenas a prévia XY. É uma verificação geométrica limitada.
- Leitor aceita explicitamente o subconjunto GRBL G0/G1/G4/G17/G20/G21/G90/G94,
  T, M0/M3/M4/M5/M6 e término M2/M30. Requer unidades, G90 e G94; comandos
  desconhecidos, incremental, arcos, macros e arquivos sem cortes são recusados.
- XYZ inicial assumido `(0,0,0)`. Comandos sem movimento efetivo e controles
  redundantes não geram movimentos fictícios. M2/M30 versus fim do arquivo é
  registrado separadamente: não se simula o reset do controlador após o término.
- Python usa quatro casas de coordenadas e **quatro de avanço**, configuração
  real para igualar a precisão fixa de avanço do FX, não o default Python de duas.
  Limite de arredondamento numérico `0,000051` nas unidades da fonte; não é uma
  tolerância mecânica. Em IN não certifica precisão de Z acima das quatro casas
  atualmente emitidas pelo FX. Nenhum critério anterior foi relaxado.

`MATCH_EXECUTION`: equivalência do estado executado **dentro desse subconjunto**
e auditorias aprovadas. `ROUTING_DIFFERENT`: assinatura de usinagem concorda,
mas deslocamentos/pausas não. `MACHINING_DIFFERENT`: também muda a assinatura de
usinagem. `ORACLE_ERROR`/`FX_ERROR`: não estabelecem paridade.
**Strict só aprova MATCH_EXECUTION**, não concordância isolada de parâmetros.

### Resultado e pendências

Rodada final: **18 MATCH_EXECUTION, 8 ROUTING_DIFFERENT,
6 MACHINING_DIFFERENT, 2 ORACLE_ERROR, 0 FX_ERROR**. Strict retorna **1**,
corretamente: este incremento não fecha toda a lacuna.

- 18: Drilling single, peck, offsets ±, duas ferramentas, seleção da segunda,
  dwell e Geometry single/dwell, em MM/IN.
- 8: posições (Python usa Start Z como altura da primeira troca T1; o FX
  conserva Tool change Z), Around (desvios diferentes) e Over (FX sobe antes
  de sair da origem; Python sobe na entrada da área protegida). Usinagem
  concorda, mas não se declararam os percursos equivalentes.
- 6: Geometry peck aberto/fechado (Python não retrai e alterna direção;
  FX retrai e conserva direção), RPM zero (FX não comanda spindle; Python
  emite M3 sem S e pausa mesmo com `toolchange=False`). Não copiar silenciosamente
  esses comportamentos para obter um placar melhor.
- 2: Dwell ativado com tempo zero faz o preprocessor Python devolver `None`
  e falhar ao concatenar o código. O FX aceita pausa de duração zero.

As 34 saídas FX passam na auditoria dos parâmetros solicitados. Não confundir
isso com 34 equivalências contra Python. O Python diverge da opção de troca
nos dois controles RPM zero e da altura de troca nos quatro controles de posições
(Drilling/Geometry × MM/IN); os erros Dwell zero continuam registrados.

Próximo: definir/testar a política compatível de multi-depth Geometry sem perder
o modo atual; ampliar para vários furos, rasgos (Drilling Python não fresa slots
como o FX), panelização e outros projetos; Geometry com parâmetros por fresa,
retração lenta/rapid plunge, troca sem pausa e outros pós-processadores.
Depois, fluxo UI → exportar → salvar/reabrir e resumo de revisão por ferramenta.
UI/FileChooser, operação real e segurança física ainda exigem validação própria.

### Histórico de validação do build

As primeiras rodadas completas passaram (`target/cnc-parameters-full-20261012.log`
e `target/cnc-parameters-full-final-20261012.log`). Após as duas regressões de
altura, `target/cnc-parameters-full-validated-20261012.log` teve um erro na
**limpeza JUnit**, depois do corpo de `TclLiveHostTest.joinedMachiningSettingsAreRemappedPersistedAndUsableForGcode`:
`DirectoryNotEmptyException` ao apagar a raiz temporária. Nenhuma asserção CNC
falhou. Não atribuir ao antivírus nem ocultar o erro; a causa não foi comprovada.
O código/estratégia de limpeza não foi alterado. Repetição integral registrada
em `target/cnc-parameters-full-retry-20261012.log`: **aprovada**, 1603 testes
registrados, 1586 aprovados e 17 skips opcionais, zero falhas/erros (20 suporte,
727 CAM, 164 aplicação, 692 FX). Contagens excluem XML antigo de testes removidos.
As 70 regressões auxiliares Python passam em
`target/cnc-parameters-python-final-20261012.log`.
Relatório de entrega: `target/cnc-parameters-delivery-20261012/python/index.html`;
logs/JSON conservam strict 1 e as divergências, sem editar o Python.
