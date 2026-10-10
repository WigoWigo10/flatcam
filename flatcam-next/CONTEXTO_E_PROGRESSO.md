# FlatCAM FX — contexto, progresso e próximos passos

## Continuidade: matriz NCC panelizado — 2026-10-10

Correção NCC Connect anterior commitada em **6fc150ee**, branch `flatcam-next`,
sem push. Testes/runner/documentação ampliados e reunidos na entrega da matriz
NCC panelizado (consultar `git log` para o hash). Não houve alteração de
CAM/renderização de produção.
MainPanelizedCamFlowTest: Reference Geometry/Itself × Rest × Connect, Standard,
40%, margem zero, Contour; simples 0,5 mm, Rest 0,2/1 mm em ordem de entrada
pequena-primeiro, verificando processamento grande-primeiro. CNC multi-tool
real usa ferramentas publicadas; cada ferramenta/CNC tem oráculo próprio.
Asserções de contenção/interseção usam OverlayNGRobust; duas primeiras tentativas
reais falharam na asserção clássica, não na geração CAM. Não contam como PASS.

Público: seis fixtures MM/IN, grades 2×2 5/5 mm, 3×1 0/0, 1×3 2/7; abertura
interna, furo/slot, origem deslocada, mesmo layout para todas as camadas.
Fluxos FX passam com CNC/persistência. Oráculo Python original compatível:
**114 execuções = 82 MATCH_SAMPLED + 32 DIFFERENT**, zero GCODE_DIFFERENT,
PARTIAL_DIFFERENCE/ORACLE_ERROR. F/B sintéticos têm o mesmo cobre: não são
114 geometrias independentes. Diferenças exclusivamente Connect (Reference,
Rest Reference, Rest Itself); Itself simples Connect e todos sem Connect passam.
Publica cobre traduzido explicitamente compartilhado; não aprova importação/
panelização Python independente. Strict 1 deve continuar visível, sem relaxar
critérios nem compartilhar área FX para contabilizar paridade.

Projeto real: fluxo FX ampliado completo passou (19 CAM/CNC, mesma referência,
furos/slots registrados, ferramentas/diâmetros/geometrias e G-code restaurados).
Native 224586312 bytes; save 74,28 s, reopen 83,24 s: matriz muito maior que a
anterior, não benchmark renderer/FPS. Oráculos reais completos: **19 casos =
11 MATCH_SAMPLED + 8 DIFFERENT**, zero GCODE_DIFFERENT/PARTIAL_DIFFERENCE/
ORACLE_ERROR; strict 1. F/B: cada face 5 MATCH + 4 DIFFERENT. Isolation, Cutout
e todos os NCC sem Connect passam; TODAS as quatro variantes Connect divergem
em cada face (Itself simples público passara, mas não o real). Clearing areas
coincidem e ordem Rest/G-code FX passam. Isso não torna os conectores aprovados.
Distâncias Connect reais 0,040188..0,285471 mm; limites mantidos.
Relatórios completos em target/panelized-ncc-matrix-real-final-20261010;
somente Gerber Python panelizado independentemente, não Excellon/apertures/UI;
Cutout na área compartilhada, não Edge_Cuts Python independente.
Original SHA256 C41580F1AC0D1E0BAF93026E6AFED18719A5614E78DA197407790C82667AA10C intacto.
Verify completo: **1524 registrados, 1509 aprovados, 15 opcionais ignorados,
zero falhas/erros**; 37 auxiliares Python passam; parser PowerShell e diff check
passam. Relatórios privados/candidatos só em target, não adicionar ao Git.

Diagnóstico público Reference Connect 1×3: áreas equivalentes, anéis/ordem não
idênticos; fornecer a área FX ao Python coincide (~7,3e-15 mm / zero IN), mas
é apenas controle diagnóstico. mitreBuffer pula preparação para margem zero;
Python aplica buffer(0) por componente e unary_union no Reference Geometry.
Candidato GeosBufferOp(0) por componente + NccCopperUnion na referência passa
os dois controles simples MM/IN; **não integrado**, sem cobertura Rest/matriz
completa e não somado aos MATCH de produção.

Runner compare-panelized-flows.ps1 aceita Columns/Rows/SpacingXmm/SpacingYmm,
SkipPublic (apenas privado), mantém saída nova sob target/hash no finally e
agrega relatórios em comparison-summary.json. Matrizes densas escrevem centenas
de MB por face e projeto nativo grande; preservar evidência, não versioná-la.
Detalhes/limites/reprodução: **PANELIZED_NCC.md**. Próximo: validar/integrar a
preparação de margem zero com regressões e diagnosticar resíduos Rest Itself;
repetir strict completo. Não migrar renderização nem declarar fluxos 100% ainda.

## Continuidade em 2026-10-10: correção da preparação NCC Connect

Branch `flatcam-next`, base `81208c85`; etapa reunida na entrega de correção NCC
Connect (consultar `git log` para o hash). Nenhum push realizado.
NccCopperUnion prepara listas de cobre (GeometryCollection genérica) com folhas
STR de capacidade 10 e união binária plana, incluindo os empates da ordenação
MSVC da referência Windows. O controle nativo confirmou a ordem e a adaptação
Java reproduziu-o; aplicativo continua sem dependência nativa adicional.
Não usar ordenação estável como equivalente a std::sort com empates >32 itens.
Origem preservada; membros válidos não recebem reparo global antes da união.
Membros inválidos têm reparo limitado; Polygon/MultiPolygon diretos e Paint
mantêm as políticas anteriores. Conector e tolerâncias permanecem intactos.
Detalhes, licença distribuída no JAR e reprodução: NCC_CONNECT.md.

Controle Java no projeto real: Connect MATCH_SAMPLED, distância 2,1316282e-14 mm,
delta de área zero, comprimento relativo 1,96e-16, G-code também aprovado.
Não generalizar esse controle isolado para todos os cenários/painéis.
Build integral aprovado: 1520 registrados, 1505 aprovados, 15 opcionais ignorados,
zero falhas/erros; 37 auxiliares Python passam. Golden público independente para
dez tamanhos, MM/IN, translação, IDs/índices dos vértices e cancelamento.
Sem medição de FPS, validação visual manual ou segurança física CNC.
Projeto original somente lido, hash C41580F1AC0D1E0BAF93026E6AFED18719A5614E78DA197407790C82667AA10C intacto.
Comparação de produção final: **32/32 públicos independentes**, também Plain/área
e controles de mesma área (estes últimos só diagnósticos). No projeto real,
**10 MATCH_SAMPLED, 0 DIFFERENT/GCODE_DIFFERENT/PARTIAL_DIFFERENCE, 1 ORACLE_ERROR**
em ncc-multi-settings: mesmo ValueError original Python [MultiPolygon] com offset,
não corrigido/achatado nem contado como paridade. Rest/Rest Connect, três ordens,
Standard e Seed Python passam. Onze controles sintéticos IN passam (11/11).
Strict real retorna 1 somente pelo erro do legado; não afirmar strict integral
aprovado. Relatórios privados ignorados em target/ncc-union-production-real-20261010;
trace público em target/ncc-connect-production-union*-20261010.json.
Próximo: ampliar o corpus panelizado Connect/Rest/Itself, grades e espaçamentos.
Depois, baseline/protótipo incremental da renderização, sem atribuir JSON/WKT/CAM
ao renderer ou prometer ganhos de FPS. Nenhum push realizado.

## Continuidade em 2026-10-10: Item 2, NCC Seed e métricas de exportação

Branch `flatcam-next`, base anterior `941750b8`; mudanças deste item e da
conversão multipartes anterior reunidas nesta entrega. Consultar `git log`
para o commit desta etapa ao retomar. Nenhum push realizado.

NCC oferece **Seed inicial** por ferramenta CLEAR: Estável (FX), padrão
preservado, ou Representativo (Python). NccSeedPolicy é um campo explícito de
NccToolSettings; construtor anterior mantém STABLE. Rest conserva a política
por fresa, Combo respeita-a no fallback, e Standard/Lines não a usam. Painel
recupera ao trocar de linha, copia em Aplicar a todas, desabilita sem uma única
CLEAR/Seed/Combo e explica a instabilidade numérica no tooltip. Escolha só na
geração/painel, não preferência global/projeto nativo/Terminal. Paint intacto.
Ver NCC_SEED.md. Não afirmar que o Seed estável reproduz literalmente Python.

Os GCODE_DIFFERENT de Lines MM e multi-settings MM/IN eram falsos negativos:
união após arredondamento de XY elimina comprimento de passadas quase
coincidentes, embora elas continuem executadas. Comparador agora preserva
os caminhos do parser original Python e verifica comprimento de percurso
com multiplicidade; distância/bounds continuam por união. Conserva comprimento
único como diagnóstico, sem aumentar nenhum limite. CAM×Python mantém as
métricas anteriores. Testes negativos recusam passada omitida/repetida/extra/
deslocada; atribuição/ordem por ferramenta e erros continuam reprovando.

Mesmos exports sintéticos anteriores: **52/54**, Seed estável ainda diferente
em MM/IN, sem GCODE_DIFFERENT/ORACLE_ERROR/PARTIAL_DIFFERENCE. Corpus ampliado
mantém Seed estável e adiciona ncc-seed-python: **27/28 MM + 27/28 IN**; Seed
Python coincide nos dois. Strict completo ainda reprova, deliberadamente não
remove/normaliza o caso estável. Relatórios ignorados em target/ncc-gcode-travel-*
e target/ncc-item2-public-*-python-20261010. As novas métricas/escopo estão
explicados em COMPARACAO_CAM.md, inclusive comprimentos brutos que provam a causa.

Connect simples ainda NÃO corrigido. NccRepresentationProbe testa seis ordens
de união/reparo: todas preservaram 0,020312630895 mm do projeto real. Controle
aproximando GEOS flat union piorou para 0,10509134 mm apesar de aproximar mais
inícios de anéis. Não integrado à produção, não portar essa aproximação como
solução. C++ std::sort/empates e outras heurísticas não estão reproduzidos.
Não fazer rotação arbitrária nem relaxar tolerância. Controles privados ficam
somente em target/ncc-representation*-20261010. Investigação seguinte deve
usar a fixture pública descrita abaixo e comparar a representação por estágio,
não mudar o conector que já coincide no controle de mesma área.

Verify integral final: **1507 registrados, 1492 aprovados, 15 opcionais ignorados,
zero falhas/erros**. 35 testes auxiliares Python aprovados; compileall/py_compile
dos scripts modificados e diff --check passam. NccSeedPolicyPanelTest cobre
seleção/desabilitação/envio por fresa; NccGCodeFidelityTest verifica exportação
Lines/per-tool MM/IN com parser FX real. Ajuste final de largura mínima do combo
também testado no JavaFX. Sem validação visual manual ou de máquina CNC.

Validação independente real ampliada em target/ncc-item2-real-20261010:
**9 MATCH_SAMPLED, 1 DIFFERENT (Connect), 1 ORACLE_ERROR (multi-settings)**.
Seed Python passa incluindo G-code: distância 4,72713794e-10 mm, delta relativo
4,17276764e-13. Rest/todas as três ordens passam. Os 11 controles sintéticos IN
selecionados também passam. Strict real reprova; não somar erro como paridade.
Erro original Python reproduzido com fixture pública: get_tool_empty_area,
ToolNCC.py:1934, chama MultiPolygon(sol_geo) quando sol_geo é [MultiPolygon]
e Copper offset está ativo. Shapely rejeita essa lista de multipolígonos.
Referência não corrigida/achatada; Java tem regressão MM/IN de que aceita essa
entrada e preserva a origem. Não declarar paridade nesse caso sem oráculo válido.
Original somente lido, SHA-256
C41580F1AC0D1E0BAF93026E6AFED18719A5614E78DA197407790C82667AA10C intacto.

NccConnectProbe ampliado para 12 discos em grade pública, com empates X/Y e
mais polígonos que as capacidades dos índices de união. **28/32 independentes**,
os quatro casos novos list-multipart reproduzem Connect DIFFERENT nas duas
unidades/posições (0,07455957 / 0,14524959 mm equivalentes). Plain passa nos 32;
controles de mesma área 32/32, apenas diagnósticos. Isso localiza a divergência
antes do conector. Dados ignorados em target/ncc-connect-expanded*-20261010.json.
Próximo: investigar os inícios/união dessa fixture pública, sem coordenadas
privadas nem afrouxar métricas; depois ampliar panelizados Connect/Rest/Itself.

## Continuidade em 2026-10-10: conversão de contornos multipartes

Etapa anterior commitada em `941750b8`, branch `flatcam-next`. Nesta entrega,
OutlineToArea preserva material de todas as regiões
separadas e resolve aberturas/ilhas por profundidade de contenção dos shells.
Usa polygonização de todas as faces, índice espacial/prepared geometry e união
robusta das faces materiais; Polygonizer(true) poderia descartar faces adjacentes.
Não escolher maior face nem unir as faces das aberturas preenchendo-as.
Faces contíguas são unidas: não inventa espaçamento/corte entre placas encostadas.
Trechos abertos/dangles/cut edges/rings inválidos detectados recusam o objeto
inteiro, não publicam uma área parcial. Grade de precisão anterior mantida.

Menu Contorno → Área agora calcula em worker, com cancelamento e guards de
versão/nome/projeto/editor antes da publicação. Console/tooltips informam
regiões/recortes preservados; seleção de vários objetos mantém erros por objeto.
Preview de panelização usa a mesma conversão com token de cancelamento.
Original não alterado. Pode converter Edge_Cuts antes OU depois de panelizar.
Detalhes/limites em OUTLINE_TO_AREA.md; Python mantém a regra da maior região,
logo esta melhoria não é paridade literal com essa limitação do legado.

Testes novos: OutlineToAreaTest e MainOutlineConversionTest (MM/IN, placas
menores, múltiplos recortes, ilha com abertura, duplicatas/adjacência, origem
intacta, entradas abertas/inválidas, cancelamento/stale publication).
Fluxo sintético usa área convertida pós-panelização -> NCC Reference Geometry
-> Cutout com recortes -> CNC/export -> salvar/reabrir. Fixture real opt-in
confere quatro placas após conversão do contorno panelizado, contra replicar
a área pré-convertida, e verifica SHA-256 da origem. Não certifica usinagem
física nem cliques manuais; o projeto real tem zero anéis internos.

Verify completo final aprovado: 1501 registrados, 1486 aprovados, 15 opcionais
ignorados, zero falhas/erros. Primeira tentativa falhou somente na limpeza
TempDir do PythonMainFlowImportTest no Windows (falha intermitente já registrada);
reexecução integral passou sem mudar/desabilitar esse teste ou sua limpeza.
Fixture real de MainOutlineConversionTest aprovada separadamente (9/9), com
SHA-256 da origem intacto. Runner panelizado strict repetido: 5/5 MATCH_SAMPLED,
zero divergências/erros, mesmas tolerâncias e mesma referência Python.
Relatórios privados ignorados em `target/panelized-flow-20261010-outline-conversion`.
O oráculo ainda compara área pré-convertida replicada, não a conversão Python
multipartes. Não confundir esses cinco casos com paridade total.

Próximo: divergências NCC Connect simples/Seed/Lines/multi-settings;
ampliar casos panelizados Connect/Rest/Itself e grades; abertura JSON/WKT/preview.

## Continuidade em 2026-10-10: Cutout interno e persistência densa

Validação panelizada anterior commitada em `d4ad0ce5`, branch `flatcam-next`.
Implementação desta etapa: opção explícita **Incluir recortes
internos**, desligada por padrão. Free-form, sem convex hull, margem >= 0,
Geometry preenchida de placa recomendada; Panel obrigatório se várias partes.
Cada abertura recebe erosão por margem + raio, emitindo caminho fechado sem
bridges/Thin/M-Bites internos. Abertura que some ou se divide recusa o job
inteiro. Chamadas antigas/Tools Database permanecem exterior-only. Não é
desbaste de bolso; fixação e cantos precisam de revisão. Detalhes: CUTOUT.md.
Tests MM/IN públicos incluem job MainWindow -> CNC/export -> salvar/reabrir;
fixture privada continua com zero anéis internos, não prova esse caso.

Persistência: JFR apontou HC4/XZ como maior custo. Salvamento isolado da cópia
nativa densa levou 41,8 s (preset 3); preset 1 levou 12,6–14,1 s, aumentando o
arquivo ~5,6% (33,92 -> 35,81 MB), sem simplificar dados. Abrir ainda ~16–18 s;
nenhuma aceleração demonstrada nessa etapa. Cabeçalho XZ agora é reconhecido
antes de tentar decodificar binário como UTF-8/JSON. Logs opt-in
`[PROJECT-PROFILE]` separam fases; `MainProjectPerformanceTest` lê fixture
nativa opcional e grava só cópia temporária, sem Stage/preferências.
`MainPanelizedCamFlowTest` separa saveMs/reopenMs/nativeBytes. Não são FPS nem
benchmark controlado. Formato nativo/Python, conteúdo e rename atômico mantidos.
Reexecução do fluxo privado: saveMs 13572,76, reopenMs 16482,74, combinado
30056,50 ms, arquivo 35805608 bytes; origem intacta. Resultados ignorados em
`target/panelized-flow-20261010-improved`. Medição inicial combinada 62411,15 ms.
Runner strict repetido: 5/5 MATCH_SAMPLED, zero divergências/erros; comportamento
perímetro-only anterior preservado. Verify completo passou: 1484 registrados,
1470 aprovados, 14 opcionais ignorados, zero falhas/erros. O diagnóstico nativo
opt-in foi aprovado separadamente com a cópia densa, incluindo JFR e SHA-256.
Testes de controles/tooltips e recortes sintéticos são headless; falta revisão
manual do novo checkbox e da operação em uma placa real com abertura interna.

Pendências seguem: desempenho de JSON/WKT/preview na abertura; contornos
multipartes na conversão para área; ampliar NCC Connect/Rest/Itself panelizado;
divergências Connect simples/Seed/Lines/multi-settings já documentadas.

## Continuidade em 2026-10-10: fluxo panelizado no projeto real

A pedido do usuário, verificado o `.FlatPrj` real em grade 2 x 2 / espaçamento
5 mm, com Edge_Cuts como referência compartilhada. Original somente lido;
SHA-256 antes/depois igual. Nenhum algoritmo CAM/Python foi alterado nesta etapa.
Novos testes/runner e extensão do oráculo commitados em `d4ad0ce5`.

`MainPanelizedCamFlowTest` cobre panelização via MainWindow, Isolation três
passadas em F_Cu/B_Cu, NCC Standard sem Connect e referência Geometry preenchida
nas duas faces, Cutout Panel/free-form/margem zero/quatro pontes, CNC/exportação
pelo host Tcl e salvar/reabrir `.fcnproj`. MM/IN públicos + fixture real opt-in
passam. Furos/slots preservam IDs, diâmetros e posições nas quatro cópias,
inclusive após persistência (o codec reagrupa por ferramenta; não exigir a
mesma ordem global da lista). Contorno/recortes da origem são replicados.

`compare-panelized-flows.ps1 -Project <arquivo.FlatPrj> -Strict` passou:
**5/5 MATCH_SAMPLED**, zero DIFFERENT/GCODE_DIFFERENT/PARTIAL_DIFFERENCE/ORACLE_ERROR.
Isolations: distâncias amostradas 0,00097359 / 0,00202909 mm; NCC ~0,00000107 mm,
delta da área de clearing zero nas duas faces; Cutout ~0,00044820 mm.
Critérios anteriores intactos, não são prova de paridade total ou segurança CNC.
Referência Python 3.11 / Shapely 1.8.5.post1 / GEOS 3.10.3 existente, sem instalar.

Oráculo de panelização usa corpo original de `ToolPanelize.job_init_geometry`
com cópia/exportação das apertures stubadas: valida a solid_geometry, não edição
de apertures nem panelização Excellon Python. Cutout usa handler free-form
original sobre a MESMA área preenchida explícita; não compara a conversão
Edge_Cuts->área do Python nem roteamento interno. Resultados privados só em
`target/panelized-flow-20261010-final`, incluindo `fx/panelized-flow.fcnproj`.

Cuidados de uso: converter o contorno original para área ANTES de panelizar e
incluir essa Geometry no conjunto; `OutlineToArea.convert` continua escolhendo
a maior região, não todas as placas de um contorno já panelizado. NCC Itself
continua incluindo espaços entre placas; Cutout Single não é corte por placa.
Recortes internos não entram automaticamente no Cutout exterior. O Edge_Cuts
real tem concavidades, mas zero anéis internos; MM/IN sintéticos têm um recorte.
As configurações CNC desta execução são de teste, não recomendações de máquina;
o host Tcl usa uma passada Z, enquanto defaults GUI multi-depth são persistidos.

Tempos headless locais: CAM/publicação ~0,15–2,8 s, panelização ~0,32 s,
CNC/export/preview ~0,05–5,54 s, salvar/reabrir ~62,4 s. Sem Stage, FPS, cliques
ou usinagem física. Investigar persistência desse painel denso como gargalo;
não atribuir automaticamente o tempo a renderização/GPU.

Próximo: fontes de contorno/área por placa e recortes internos no Cutout,
persistência densa e ampliação do painel para NCC Connect/Rest/Itself e outras
grades/espaçamentos. Connect simples residual e Seed/Lines/multi-settings do
corpus anterior permanecem pendentes; 5/5 deste cenário não os resolve.

Verify completo final: 1479 registrados, 1466 aprovados, 13 opcionais ignorados,
zero falhas/erros. Inclui os dois novos cenários públicos MM/IN; fixture real
opt-in foi aprovada separadamente pelo runner. A primeira tentativa falhou
apenas na limpeza de TempDir de PythonMainFlowImportTest no Windows;
reexecução integral passou, sem desabilitar a limpeza ou alterar testes antigos.
32 testes Python auxiliares/compileall aprovados no ambiente compatível.

## Continuidade em 2026-10-08: preparação NCC integrada, 8/9 no projeto real

Diagnósticos commitados em `fcf93ea8`, branch `flatcam-next`. Novo incremento
integra GeosBufferOp na margem mitre do NCC e união robusta moderna do cobre
quando a fonte é uma GeometryCollection genérica (caso da lista importada
[MultiPolygon]). Polygon/MultiPolygon ordinários não recebem semântica de
lista; Paint mantém seus buffers anteriores. A origem não é modificada.

Comparação independente do mesmo projeto real: 8/9 MATCH_SAMPLED, 1 DIFFERENT,
zero erros do oráculo/GCODE_DIFFERENT/PARTIAL_DIFFERENCE. Standard, três ordens
multi-tool e quatro Rest, incluindo Rest Connect, passam. Connect simples
ainda tem distância 0,02031263 mm (antes ~0,15305533); limite permanece 0,003 mm,
logo strict real REPROVA. Clearing area delta agora é zero em todos os nove.
Não declarar correção completa nem usar porcentagem desses casos como paridade.

Sintéticos completos preservam o resultado: MM 24 MATCH_SAMPLED, Seed DIFFERENT,
Lines/multi-settings GCODE_DIFFERENT; IN 25 MATCH_SAMPLED, Seed DIFFERENT e
multi-settings GCODE_DIFFERENT. Total 49/54, strict completo ainda reprovado.
Probe público ampliado: 24/24 aprovados, formas diretas e listas [MultiPolygon],
MM/IN e translações. Python usa helpers GUI originais de preparo, sem buffer(0)
extra do harness; aprovação independente exige área e caminhos Plain/Connect.
Controles de mesma área continuam explicitamente só diagnósticos.

Regressões novas: margem rasa nas quatro modalidades de limite, MM/IN e
translação; preparação da coleção sem mutar a origem. Verify completo passa:
1476 registrados, 1464 aprovados, 12 opcionais ignorados, zero falhas/erros.
29 testes auxiliares Python e compileall passam; probes D3D/Intel Arc e software
passam. Sem cliques/validação física. Incremento autorizado para commit pelo usuário.

Próximo: localizar a representação residual de Connect simples no projeto
denso; união moderna ainda não garante os mesmos inícios de todos os anéis.
Investigar com entradas/ordens controladas e fixture pública, sem rotação
arbitrária ou relaxamento de critérios. Semântica especial de lista com um
único Polygon não foi portada neste incremento: a representação atual pode
perder a distinção dessa lista na importação. Mais detalhes em COMPARACAO_CAM.md.

## Continuidade em 2026-10-08: diagnóstico do NCC no projeto real

Incremento anterior commitado em `be6b6127`, branch `flatcam-next`. Nesta etapa
o pedido é investigação; nenhum algoritmo de produção foi alterado. Novos
probes, teste do kernel candidato e documentação autorizados para commit.

Duas diferenças de preparo demonstradas:

1. Margem Itself: cobre e convex hull concordam, mas BufferOp/JTS difere em
   0,000350179467 mm² / Hausdorff 0,000643486557 mm. GeosBufferOp já existente
   aplicado como controle elimina o delta da margem e da área de clearing.
   Fixture pública independente com canto convexo raso reproduz a diferença;
   nova regressão do kernel cobre MM/IN e translação, sem mudar produção.
2. Representação: FX preserva os anéis originais do projeto; o Python executa
   unary_union da lista [MultiPolygon] em get_ncc_empty_area, mudando o início
   de 111 anéis sem mudar área, número de vértices ou orientação. Após a
   subtração, 111/112 anéis começam em pontos distintos. Mesmas entradas nos
   dois motores produzem os mesmos inícios; remover o reparo final ou o reparo
   inicial do FX não resolve. Não atribuir isso a corrupção do importador.

Controle só de margem aprova Standard, três ordens multi-tool e Rest Itself;
os dois Rest com limites explícitos continuam aprovados. Connect e Rest Connect
ainda reprovam. Connect com exatamente a área FX fornecida ao Python concorda
até ~2e-14 mm; esse controle localiza a diferença e NÃO estabelece paridade.
União moderna JTS aproxima a representação do alvo Python, mas ainda diverge
no início de 18/111 anéis; união clássica mantém 111/111 divergentes.

Próxima implementação proposta: alinhar a margem com o kernel GEOS validado,
investigar a preparação/união do contêiner antes de subtrair e preservar seus
inícios sem rotação arbitrária. Não aplicar a regra de lista indiscriminadamente:
lista com um Polygon tem outra semântica Itself no legado. Repetir corpus e
strict real antes de declarar correção. Critérios/oráculo permanecem intactos.

45 testes Java focados (GeosBufferOpTest/NccGeneratorTest) e 29 auxiliares
Python passam; compileall passa. Verify completo anterior pertence ao commit
be6b6127 (1461 aprovados), não foi repetido nesta etapa diagnóstica.
Comandos e relatórios privados em `COMPARACAO_CAM.md`; fontes só lidas, sem UI
ou usinagem física. A produção continua com 2/9 casos reais aprovados.

## Continuidade em 2026-10-08: referência compatível, Connect e NCC Rest

Três passos executados após os tooltips: preparar referência Python isolada,
investigar/corrigir Connect e ampliar o corpus Rest/múltiplas ferramentas.
Ambiente `target/oracle-py311` (ignorado): Python 3.11.1, Shapely 1.8.5.post1,
GEOS 3.10.3; ambiente habitual preservado. Runner `-LegacyCompatible` valida
versões e não instala dependências. Lock e instruções em `COMPARACAO_CAM.md`.

NCC usa overlay robusto moderno na subtração inicial, preservando os pontos
iniciais dos anéis usados por Connect: 12/12 fixtures independentes passam.
Rest segue a pegada do painel Python (resolução 16, raio tool/1.9999999,
reparo +1e-7 nas unidades atuais), sem alterar a política conservadora de Paint.
Polígonos onde a fresa Rest não cabe permanecem para a menor sem contar falha.
O oráculo executa os corpos originais do painel GUI, não os homônimos Tcl.

Corpus agora tem 27 casos MM + 27 IN e compara atribuição/ordem/CAM/G-code de
cada ferramenta, além do programa conjunto gerado com trocas de ferramenta.
Completo: 49/54 MATCH_SAMPLED, dois DIFFERENT (Seed), três GCODE_DIFFERENT
(Lines MM e multi-settings MM/IN), zero ORACLE_ERROR/PARTIAL_DIFFERENCE.
Strict completo reprova; os 16 casos focados Connect/multi-order/Rest passam.
Não relaxar critérios nem converter esses números em paridade geral.

Projeto real do usuário, somente lido: 2/9 casos NCC passam, 7 DIFFERENT e zero
erros do oráculo. Rest Area/Reference Geometry passam; Itself apresenta pequena
diferença na área inicial, e Connect/Rest acumulam diferenças nos caminhos.
Próxima investigação: preparo/contêiner/reparo do cobre e limite Itself no
projeto denso, depois movimentos/quantização G-code remanescentes. Não achatar
o contêiner Python nem modificar sua lógica para fazer o teste passar.
Relatórios privados continuam apenas em target. Detalhes e caminhos locais em
`COMPARACAO_CAM.md`; nenhuma validação manual de UI ou máquina CNC realizada.

Verify completo repetido: 1473 registrados, 1461 aprovados, 12 opcionais
ignorados, zero falhas/erros; 25 testes Python passam com Shapely 1.8 e 2.
Probes Direct3D/Intel Arc e software passam. Incremento autorizado para commit;
último commit anterior `8d5c4b85`, branch `flatcam-next`.

## Continuidade em 2026-10-07: revisão dos tooltips

A pedido do usuário, prioridade temporária na ajuda visual antes das divergências
CAM. Completados campos/ações secundários, editores Gerber/Excellon/Geometry,
menus, terminal, código, status e Sobre, reutilizando TooltipContent/FluidTooltips.
Quebras de linha, negrito e destaques de unidades/atalhos/atenção nos quatro temas;
células da árvore preservam atualização nativa. Adicionar/Copiar/Excluir da DB
passam a ter ajuda contextual (substitui a omissão histórica dessas três ações).
Copiar código/DB/objetos não compartilha uma descrição equivocada.

Auditoria reproduzível de 745 ocorrências de controles em 26 painéis, três
editores e duas barras sem lacunas no cenário de teste; não confundir esse
inventário com todos os estados possíveis da UI. Capturas offscreen inspecionadas
nos quatro temas e contraste de spans >= 4,5:1. Detalhes e roteiro em `TOOLTIPS.md`.
Incremento autorizado para commit na branch `flatcam-next`. Fluxos CAM/critério
strict permanecem inalterados.
Verify completo: 1467 registrados, 1455 aprovados, 12 opcionais ignorados, zero
falhas/erros. Auditoria ampliada repetida passa; probes D3D/Intel Arc e software
passam. Hover/cliques em todos os estados reais ainda requerem validação manual.

## Continuidade em 2026-10-07: precisão IN e lógica Connect

Corrigida serialização XY: seis casas em IN/INCH, quatro em MM. Z/feeds e
dialetos específicos mantêm políticas anteriores; programas salvos não mudam.
O G-code interpretado Reference Geometry IN agora atende aos critérios originais.
Connect usa pegada contra área original (sem dupla erosão), primeira linha mais
próxima de (0,0), orientação inicial preservada e within estrito sem ampliar área.
Comprimento Connect sintético concorda, mas distância ainda reprova por pontos
iniciais diferentes dos anéis após booleanas JTS/GEOS. Não forçar um canto só
para aprovar um teste. Resultado atual 28/38 critérios atendidos; dois Connect
DIFFERENT e oito ORACLE_ERROR Shapely 2. Detalhes em `COMPARACAO_CAM.md`.

Próximo: investigar preparação/pontos iniciais em corpus maior e repetir os
casos com uma referência Python compatível; não relaxar critérios nem declarar
paridade física. Corpus/runner e estas correções são registrados juntos no
incremento atual, na branch `flatcam-next`; o commit anterior das opções comuns
é `e6ac239b`.

Verify: 1463 registrados, 1451 aprovados, 12 opcionais ignorados, zero
falhas/erros. Probes D3D/software e strict focado Reference Geometry MM/IN
passam; strict completo ainda reprova, conforme relatório.

## Continuidade em 2026-10-07: corpus diferencial dos fluxos principais

Configurações comuns da DB commitadas em `e6ac239b`, branch `flatcam-next`.
Próximo incremento amplia o harness para 19 casos sintéticos MM e 19 IN:
Isolation Exterior/Interior/Follow/exceções, NCC Connect/sem Contour e três
limites explícitos. Usa métodos originais dos plugins por AST sem patches e
conserva o contêiner original da fonte ao decodificar projetos. Compara também
cortes XY do G-code interpretado, distinguindo `GCODE_DIFFERENT` de CAM.
Runner `compare-main-flows.ps1` gera relatórios numa pasta nova em target,
aceita projeto/Python/bibliotecas isoladas/casos e não abre UI/instala dependências.

Execução Shapely 2.1.2/GEOS 3.13.1: 14 casos MM e 13 IN atendem aos critérios;
Connect difere em ambos; G-code interpretado Reference Geometry IN difere em
comprimento apesar de CAM concordante. Quatro erros do oráculo por conjunto
(Seed/Lines e Cutout com quatro gaps). Strict reprova; não houve relaxamento
dos critérios nem mudança de algoritmo/precisão CAM. Treze testes Python passam.
Detalhes em `COMPARACAO_CAM.md`. Próximo: investigar Connect e G-code IN,
ampliar Rest/múltiplas ferramentas e executar com projetos reais. Nenhuma
declaração de paridade total, FPS real ou segurança física.

Verify completo deste incremento: 1456 registrados, 1444 aprovados, 12 opcionais
ignorados, zero falhas/erros. Runner strict focado em Exceptions/Area/Reference
Gerber passa em MM/IN, inclusive com espaços no destino. Suíte completa continua
strict reprovada pelos casos documentados; não contar seleção focada como 100%.

## Continuidade em 2026-10-07: configurações comuns da Tools Database

Após Panelize (`1a281641`, commit local), implementada transferência explícita
de preprocessor, rapid feed, troca e Start/End/Tool change Z/XY. Isolation/NCC,
Milling e Cutout preservam sugestões por ferramenta na Geometry; o painel CNC
recupera e exige revisão explícita para campos conflitantes. Drilling importa
pela DB, considerando só brocas selecionadas e limpando sugestões ao trocar
origem/resetar. Campos ausentes não apagam rascunhos; None é automático.

Sugestões pendentes de Geometry persistem no `.fcnproj`, inclusive conflitos;
Join reindexa e geração CNC bem-sucedida guarda os valores efetivos revisados.
Exportar `.FlatPrj` com sugestões pendentes é recusado para não perder dados.
Drilling conserva o contrato anterior de salvar comuns após geração, não um
rascunho não submetido. Perfis desconhecidos/incompatíveis não têm fallback.
Não houve conversão implícita de unidades ou alteração de algoritmos CAM/GPU.

Detalhes, testes e roteiro manual em `CNC_DATABASE_COMMON.md`. Esta seção
substitui a limitação histórica dos cinco incrementos quanto a esses oito
campos comuns; outras opções globais, consumo Tcl e validação real permanecem
pendentes. Próximo: testar estes fluxos com a DB/projeto do usuário e ampliar
o corpus diferencial CAM. Não declarar 100% ou segurança CNC por teste sintético.

Verify completo: 1455 registrados, 1443 aprovados, 12 opcionais ignorados,
zero falhas/erros. Probes offscreen Direct3D/Intel Arc e software passam.

## Continuidade em 2026-10-07: prévia e conjunto Panelize

Panelize ganhou prévia no estilo 2-Sided: conteúdo real replicado, borda/cortes
internos e preenchimento translúcido opcional, com enquadramento. Worker
independente com debounce e invalidação evita publicar overlays antigos.
Panelizar conjunto aplica uma grade/referência comum a Gerber/Excellon/Geometry;
caixas individuais não alteram os deslocamentos. Contorno identificado ao abrir
é referência inicial, mas a escolha deve ser conferida. Unidades divergentes
são recusadas, sem conversão ou recentramento implícito.

Criação tem progresso, cancelamento cooperativo e guards antes de publicar todo
o lote. Excellon preserva IDs/diâmetros exatos; Geometry mantém índices de tools,
substituindo a fusão histórica da panelização. Trinta e dois cenários novos,
incluindo cenas reais offscreen nos temas claro/escuro. Uso, limites e roteiro
manual em `PANELIZE.md`. Validação com o projeto privado e `panelize` Tcl
permanecem pendentes; não declarar paridade total nem ganho de FPS.

As categorias da prévia têm cores distintas e legenda: Gerber azul, Excellon
laranja/âmbar, Geometry violeta. Tons se ajustam ao tema e furos/slots são
desenhados por cima das demais categorias. Paleta é só dos overlays, não muda
cores do projeto; caches separados por categoria/estilo são limpos ao fechar.

Verify completo com diferenciação de cores: 1436 registrados, 1424 aprovados, 12 opcionais
ignorados, zero falhas/erros. Probes D3D/Intel Arc e software forçado passam.
Snapshots offscreen claro/escuro conferidos, sem ensaio de FPS/projeto privado.

## Continuidade em 2026-10-07: prévia de CNC denso

Depois dos cinco incrementos publicados (último `8b2c9429`), implementado o
próximo passo autorizado: parser de prévia sem descarte em 50 mil segmentos.
Programas acima de 50 mil linhas agrupam buffers em blocos de 128 pontos,
mantendo ferramenta/diâmetro, corte/travel, descontinuidades e furos. Centros
colineares redundantes são omitidos só no display; navegação retém todos os
pontos em arrays primitivos, com distância/tempo completos. Não há mudança
no G-code, nas geometrias CAM ou no algoritmo NCC. Setas decorativas continuam
limitadas a 50 mil; arcos usam a aproximação já existente.

Importação/edição usam workers com progresso percentual limitado a 101 updates
por arquivo/análise. Cancelamento e validade de projeto/CNC são rechecados na
UI antes de aplicar. Edição preserva o modo de centros com largura real.
Dezessete testes novos (parser + MainWindow offscreen); NCC e harness passam
a exigir prévia válida também para programas densos. Detalhes e limites em
`FLUXO_PRINCIPAL.md`. Próximo: validar fluidez/importação/edição/cancelamento
com o projeto denso real, depois ampliar o corpus diferencial de produção.
Não declarar paridade total ou desempenho real com base em testes sintéticos.

Verificação deste incremento: `mvnw.cmd -q verify` passou com 1404 registrados,
1392 aprovados, 12 opcionais ignorados e zero falhas/erros. Os probes offscreen
passam em D3D/Intel Arc e software forçado. A exportação Java do harness CAM
também foi reexecutada: os dez fixtures agora fornecem prévia disponível,
inclusive Isolation 3 e NCC Seed, em `target/dense-cnc-cam-comparison/fx-cam.json`.
Não foi repetido o oráculo Python nem um ensaio de FPS com o projeto privado.

## Continuidade em 2026-10-07: cinco fluxos principais

Usuário autorizou implementação individual, commits por incremento e push para
`fork/flatcam-next`, sem force-push. Base inicial `eeeef1ff`.
Primeiro incremento: Geometry → CNC pela UI usa worker para geração/prévia,
temporário e revalidação antes de substituir arquivo. Onze testes novos passam,
assim como `mvnw.cmd -q verify`; consultar `FLUXO_PRINCIPAL.md` para limites.
Seguem Drilling/Milling, importação/preparação, Isolation e NCC. Não declarar
paridade total: validação manual com projetos reais e ensaio físico são separados.
Diagnóstico paralelo de Panelize Python não modificou o legado. FX já multiplica
furos/rasgos e exporta corretamente no ensaio sintético; comando Tcl ainda ausente.

Primeiro commit publicado: `98d29d35`. Segundo incremento: Milling revalida
entradas/epoch/editores/cancelamento e preserva outro painel. Drilling protege
callbacks tardios e valida cancelamento na UI. Tools Database recusa Cut Z
positivo em Drilling. `MainExcellonFlowTest` cobre 12 cenários, com persistência
e CNC Drills/Slots em MM/IN; opções comuns da DB permanecem parcialmente aplicadas.

Segundo commit publicado: `8dc3a89f`. Terceiro incremento normaliza unidades
de Gerber/Geometry legados, recusa rótulos desconhecidos e não converte Cut Z
positivo opcional em profundidade de usinagem. Mantém geometria e avisos de
parâmetros incompletos. Oito testes novos em `PythonMainFlowImportTest`.

Terceiro commit publicado: `41fc71b5`. Quarto incremento transporta dados de corte
Milling explícitos da DB Isolation até Geometry/CNC, incluindo V-Tip. Reindexa
por ferramentas efetivamente publicadas; não herda valores a ferramentas manuais,
não duplica offset CAM e não transporta parâmetros comuns de máquina/perfil.
Seis novos casos em `MainIsolationMachiningTest`; detalhes em `FLUXO_PRINCIPAL.md`.

Quarto commit publicado: `8e67b337`. Quinto incremento aplica a mesma transferência
à DB NCC/CLEAR/ISO. `MainNccMachiningTest`: nove casos de limites/Rest/MM/IN,
ISO V, persistência, painel CNC e geração/prévia, além do transporte pelas forms.
Os cinco incrementos técnicos não provam 100% dos fluxos: comparação diferencial
real, testes manuais de todos os controles e validação física permanecem pendentes.

Verificação final desta sequência: `mvnw.cmd -q verify` aprovado, **1387
registrados, 1375 aprovados, 12 opcionais ignorados**, zero falhas/erros.
Probes offscreen do launcher passam em D3D/Intel Arc e software forçado.
Comparação sintética via harness, Python 8.994/Shapely 2.1.2: Isolation 1/3
passadas e NCC Standard = 3 `MATCH_SAMPLED`, modo estrito aprovado. NCC Seed/Lines
= 2 `ORACLE_ERROR` por incompatibilidade multipart do legado com Shapely 2;
não contam como aprovação nem demonstram defeito FX. Artefatos locais em
`target/five-flows-cam-comparison/`, sem alterar Python ou preferências.

Este é o documento operacional de continuidade do **FlatCAM FX**. Ele foi
escrito para que uma nova sessão de IA (Codex, Claude ou equivalente) consiga
entender o estado real do projeto, tomar decisões compatíveis com as já feitas
e continuar a migração sem recomeçar a investigação.

> Histórico anterior (**2026-10-06**), branch `flatcam-next`, revisão da base `0a3fb636`.
> Java 25 + JavaFX 25.0.4, 24 ferramentas de menu implementadas com opções ainda
> parciais, editores, Tools Database e Terminal com 28 famílias de comandos FlatCAM,
> incluindo a extensão FX de rotação; isso não declara paridade Tcl completa.
> A revisão desta sessão impede herança de parâmetros ausentes na fusão Excellon
> e libera o worker de índices do Plot após Error sem ocultar a falha dos diagnósticos.
> O Plot preserva prévias durante a atualização de índices/densidade; retornar do
> zoom vetorial reutiliza o cache e uma visão ampla evita depender só do recorte anterior.
> O menu da árvore mostra somente Ativar/Desativar Plot conforme a visibilidade
> atual; seleções mistas mostram ambas as ações com seus respectivos totais.
> Definir Cor marca a cor atual com seleção exclusiva, independente da opacidade,
> preservando os quadrados e ícones; cores fora da paleta marcam Personalizada.
> Drilling agora tem exclusões Around/Over, editor compartilhado com Geometry,
> persistência nativa e geração/prévia/gravação em worker cancelável. Furos/slots
> protegidos recusam o trabalho inteiro; `.FlatPrj` com áreas também é recusado.
> Drilling ganhou Start Z e posição XY de troca opcionais, com trajetos Around/Over,
> clearance validado e restauração por objeto. Roland/sondagem recusam estes campos.
> O console inferior recolhido agora mantém uma barra curta com porcentagem e
> Cancelar na barra de status; a versão expandida mantém a barra longa.
> Console inferior e painel lateral agora abrem/recolhem com transição de 180 ms,
> reversível, sem persistir dimensões intermediárias.
> Seleções de tabelas sem foco agora usam destaque discreto coerente com o tema,
> sem o fundo quase branco herdado do Modena no escuro.
> Tabelas #/diâmetro/TT de Isolation, NCC e Geometry→CNC têm colunas compactas
> e conteúdo centralizado; a largura extra fica no diâmetro, como no Python.
> Alternar Snap atualiza imediatamente coordenadas/prévia/cruz e não é desfeito
> por texto inválido nos campos de passo X/Y.
> A árvore só inicia renomeação por F2 ou comando Renomear; repetir um clique
> numa linha selecionada não abre mais o editor de nome.
> Diagnósticos locais por sessão: logs/JFR limitados, exceções, CPU/RAM/JVM,
> watchdog FX e menu Ajuda > Diagnosticos. Heap dump é opt-in.
> Ajuda > Sobre agora abre diálogo com logo/versão, créditos do Python, licença
> do repositório, atribuições e sistema; copia dados técnicos sem fechar.
> Sistema mostra CPU/RAM/heap e consulta o pipeline gráfico ativo: GPU/driver
> Direct3D da janela ou compatibilidade por software, sem inferir de prism.order.
> Editor G-code e Ver Fonte agora usam CodeEditor/RichTextFX: linhas numeradas,
> sintaxe por tema, linha atual, posição do cursor e busca literal assíncrona.
> Terminal agora abre projetos nativo/Python e transforma Gerber/Excellon/Geometry
> por offset/scale/mirror/skew/rotate, salva projetos, junta Geometry/Excellon,
> exporta Excellon/Gerber/SVG e controla visibilidade/seleção, com validação de snapshots.
> Gerber/SVG exigem destino explícito e usam worker/temporário; SVG tem fator
> de traço e CNC é analisado do código atual, independente da visibilidade do Plot.
> Prioridade atual: completar fluxos principais, não novos comandos Tcl.
> Isolation/NCC/Cutout revalidam entradas/projeto/cancelamento e preservam outro
> painel aberto durante o cálculo. Isolation recorta coleções por parte/passada.
> Isolation tem seletor piloto de fresas com ícones vetoriais; índices grandes do
> Plot e enumeração lazy de fontes foram movidos para workers.
> `mvnw.cmd -q verify` completo passou com limpeza TempDir normal ativa:
> **1340 testes registrados, 1328 aprovados, 12 opcionais ignorados**, zero falhas/erros.
> São 653 CAM, 127 application, 540 FX e 20 de suporte de testes.
> Probes nativos de renderização fora da tela passaram no modo padrão D3D→SW e
> software forçado; isso não comprova fluidez em projetos grandes nem validação manual.
> O Terminal continua sendo um dialeto Tcl reduzido. Seed continua deliberadamente
> diferente do Python; a comparação privada CAM não foi reexecutada nesta sessão.
> Veja a revisão de 2026-10-06 ao final e `PLANO_PARIDADE.md` para limites e pendências.
> Antes de continuar, confirme HEAD, status e testes. Trechos antigos ficam como
> histórico e não substituem o código e as verificações mais recentes.

## 1. Objetivo do projeto

O FlatCAM FX é uma reimplementação gradual do FlatCAM Python/PyQt5 em
**Java 25 + JavaFX**, mantida no mesmo repositório enquanto ainda não substitui
o aplicativo legado.

A meta solicitada é obter paridade tão completa quanto for razoável com o
FlatCAM Python, preservando seus fluxos e formatos, mas melhorando arquitetura,
responsividade, feedback de progresso e apresentação quando isso não quebrar a
expectativa do usuário.

Paridade significa reproduzir o comportamento observável e os resultados CAM;
não significa traduzir o Python linha por linha. O aplicativo Python é o
oráculo funcional. O código Java deve continuar idiomático, testável e sem
dependências da interface no núcleo CAM.

## 2. Documentos que devem ser lidos

Leia estes arquivos antes de uma mudança ampla:

1. `CONTEXTO_E_PROGRESSO.md` — este estado operacional e a fila atual.
2. `../CONTEXTO_FLATCAM_FX.md` — estratégia, princípios arquiteturais e plano
   de migração original. Algumas fases descritas ali já foram superadas.
3. `UI_INVENTORY.md` — inventário detalhado da interface Python usado como
   referência de paridade.
4. `README.md` — requisitos e comandos confiáveis de build/teste/execução.

Quando um documento antigo disser que o JavaFX ainda é apenas um esqueleto,
prefira o estado descrito aqui e confirme no código.

## 3. Estado técnico verificável

### Stack e módulos

- Java 25 (LTS).
- Maven Wrapper; Maven global não é necessário.
- JavaFX 25.0.4.
- Quatro temas próprios sobre JavaFX Modena: original branco/preto e gelo branco/preto; sem AtlantaFX.
- JTS 1.20.0 para geometria.
- ZXing `core` 3.5.4 (Apache 2.0) só para gerar a matriz do QRCode Tool.
- JUnit 6.1.3 via BOM e Surefire 3.5.4 para testes.
- `JAVA_HOME` precisa apontar para um JDK 25 (o `release` do compilador é 25 e o JavaFX 25 exige Java 23+).
  Um terminal/VS Code aberto antes de mudar a variável continua com o valor antigo: reabra-o.
- Launcher nativo opcional (`native-launcher/FlatCAMFX.cpp`, `run-native.cmd`): cria a JVM dentro de um `.exe`
  próprio que exporta os sinais `NvOptimusEnablement`/`AmdPowerXpressRequestHighPerformance`. Exige o `g++` do
  MSYS2 UCRT64 (`pacman -S mingw-w64-ucrt-x86_64-gcc`); sem ele, `run.cmd` usa o `javafx:run` do Maven. Ver
  `NATIVE_GPU.md`.

O reactor Maven contém quatro módulos (três de aplicação e um exclusivo de testes):

| Módulo | Responsabilidade atual | Regra de dependência |
| --- | --- | --- |
| `flatcam-application` | modelo leve de projeto, jobs, progresso e cancelamento | não depende de JavaFX; depende de `flatcam-cam` desde a persistência embutida de Gerber/Excellon (seção 9.3) - `ProjectFile` guarda `GerberImage`/`ExcellonImage` de verdade, não paths |
| `flatcam-cam` | parsing, geometria, operações CAM e geração de G-code | não depende de JavaFX nem de `flatcam-application` |
| `flatcam-fx` | janela, árvore do projeto, painéis de ferramentas, temas e renderização | depende dos dois módulos anteriores |
| `flatcam-test-support` | estratégia de limpeza TempDir Windows e seus testes | consumido somente no escopo `test`; não entra no runtime |

Não existem ainda módulos separados de renderer, CLI, scheduler, compat ou
native. Só devem ser criados quando houver uma fronteira real que justifique a
separação.

### Verificação mais recente

Em **2026-10-06**, após exclusões Drilling sobre `f59f849a` e o ajuste de cor
no menu, `mvnw.cmd -q verify` completo passou: **1220 registrados,
1208 aprovados e 12 opcionais ignorados**, zero falhas/erros, com limpeza TempDir
normal. São 617 CAM, 120 application, 463 FX e 20 test-support. São 39 regressões
adicionais de exclusões Drilling (14 CAM, 5 persistência, 9 painel/temas,
6 worker/arquivo e 5 host real). As regressões anteriores de cor/Plot permanecem.
Na entrega visual anterior, os sete testes PlotPreparationUiTest também passaram
com `flatcam.plot.density.pixelBuffer=false`, além do modo padrão.
Probes offscreen do launcher existente passaram com as
classes recompiladas em D3D/Intel Arc e software forçado. Sem benchmark de
fluidez, teste físico CNC ou nova comparação dos projetos privados Python.

### Histórico das verificações anteriores

Em **2026-10-05**, `mvnw.cmd -q install` completo passou: **939 registrados,
928 aprovados, 11 opcionais ignorados**, zero falhas/erros, com limpeza TempDir
ativa. Nenhuma propriedade `NEVER` foi usada. Probes do launcher existente com
`--probe` e `--probe --software` passaram usando as classes recompiladas.
Os novos testes do Terminal executam os comandos fora da thread FX e verificam
responsividade, serialização, cancelamento, progresso e o host real/G-code.
A validação manual do painel e a comparação CAM privada não foram reexecutadas.

Na entrega anterior de 2026-10-02, `mvnw.cmd -q install` completo (três módulos com JDK 25.0.4.1 e JavaFX 25.0.4) passou com
**779 testes registrados**: 514 em `flatcam-cam`, 114 em `flatcam-application` e 151 em `flatcam-fx`;
768 executados, 0 falhas, 0 erros e
11 ignorados (fixtures opcionais de `PythonProjectWriterTest`, `NccPythonParityTest`, `PythonProjectCamSmokeTest`, `PythonProjectIOTest` e `PlotAreaNestedGeometryTest`,
que só rodam com um projeto real do Python indicado por variável de ambiente, como `FLATCAM_PARITY_PROJECT`). `JobExecutorTest`
registra intencionalmente uma `IllegalStateException: boom` ao testar propagação de erro, e um teste de jobs
imprime "Job failed"; esses logs, isoladamente, não representam falha da suíte. O smoke visual anterior
com `javafx:run` no Java 25 passou (20 s sem exceções). No incremento de pós-processadores, controles
de Geometry foram verificados na thread FX sem abrir janela; teste manual da tela completa permanece pendente.

**Continuação mais recente:** 787 registrados (521 CAM, 114 application, 152 FX),
776 aprovados, 11 ignorados, zero erros/falhas de asserções na execução com
`junit.jupiter.tempdir.cleanup.mode.default=NEVER` e TempDir em `target/junit-temp-parity`
(via `-DargLine=-Djava.io.tmpdir=CAMINHO_ABSOLUTO`). O install nesse modo passou;
os temporários são conservados, não se modificou a configuração permanente de testes.
Quatro tentativas do install normal falharam exclusivamente na limpeza TempDir:
DirectoryNotEmptyException no diretório raiz, em testes diferentes; os diretórios
já não existiam na inspeção posterior. Trocar a pasta temporária não resolveu.
A causa Windows/JDK/JUnit ainda não foi determinada e **o build normal não está
declarado aprovado**. O núcleo CAM passou com limpeza normal. Corrigir/verificar
essa infraestrutura é pendência separada, sem esconder exceções nem desativar
asserções ou pular testes funcionais.

Há testes automatizados de controles JavaFX na thread FX, incluindo persistência CNC e Tools Database,
mas não uma suíte end-to-end completa da janela. Painéis, `MainWindow` e `PlotAreaView` também são validados por
harnesses fora da tela (capturas por `Node.snapshot`, sem janela visível) e pelo usuário no app real.
Vários painéis recentes (2-Sided, Paint, Panelize, Invert, Subtract, Extract Drills, Punch, Etch, Film,
Fiducials, Corner Markers, QRCode, SolderPaste, Align Objects) têm a **lógica** testada, mas o fluxo de
cliques e janelas ainda não foi confirmado por uso manual.

Sempre refaça essas verificações depois de mudanças relevantes; números e resultados podem mudar.

## 4. O que já funciona

### Shell, temas e área de plotagem

- Janela JavaFX com menus, barras de ferramentas, painel lateral, console,
  status e progresso.
- O chrome principal agora expõe os menus e barras de Arquivo, Editar, Exibir,
  Shell e Ferramentas com os ícones PNG originais do Python. Comandos ainda não
  portados aparecem desabilitados; ações já funcionais continuam ligadas aos
  fluxos reais. Os ícones claro/escuro trocam ao vivo com o tema. A barra de
  ferramentas Gerber também aparece dentro do editor, com operações existentes
  ativas e ferramentas futuras desabilitadas. Os comandos dos editores
  Excellon/Geometry estão catalogados em Editar → Ferramentas dos editores,
  desabilitados até esses editores existirem.
- Temas Original (branco/preto) e Gelo (branco/preto), todos próprios e alternáveis no menu Tema.
- Tools Database tem tooltips com parágrafos, opções em linhas separadas, termos em negrito e destaques de unidades
  e integração CAM legíveis nos quatro temas. Adicionar/Copiar/Excluir não têm tooltip redundante; campos desabilitados
  continuam com ajuda no rótulo. Em 2026-10-02: 40 testes direcionados passaram e capturas fora da tela conferiram os
  quatro temas; a suíte completa não foi repetida neste incremento.
- A Plot Area acompanha o tema ativo, inclusive fundo, grade, eixos e textos.
- Canvas com pan, zoom, enquadramento, réguas, grade adaptativa, origem, posição
  e delta do cursor.
- Ordem de desenho por categoria e visibilidade por objeto.
- Plot preenchido ou em contorno, multicolor, cor personalizada, opacidade e
  restauração da cor padrão.

### Árvore do projeto e menus de contexto

- Categorias `Gerbers`, `Excellon`, `Geometry` e `CNC Jobs`.
- Alinhamento compacto semelhante ao legado.
- Ícones de objeto e de ações reutilizados do FlatCAM Python.
- Em tema escuro, ícones predominantemente pretos recebem contorno/halo claro
  para continuarem legíveis.
- Amostras de cor arredondadas nos menus.
- Seleção múltipla e ações contextuais pertinentes ao tipo do objeto.
- Ações de plot, fonte, renomear, copiar, remover, salvar e propriedades já
  existem onde aplicáveis.

### Carregamento e projetos

- Abertura de Gerber e Excellon em background.
- Cancelamento e progresso real, monotônico, calculado sobre o trabalho de
  leitura/parsing em vez de uma animação fictícia.
- Abertura de projeto de forma atômica: o estado anterior não fica parcialmente
  substituído se o carregamento falhar.
- **Formato `.fcnproj` v2 (2026-09-22): Gerber e Excellon embutem sua própria
  geometria**, no mesmo formato que o `.FlatPrj` do Python usa de verdade -
  pesquisado diretamente em `camlib.py`/`app_Main.py` antes de implementar
  (ver seção 9.3). Um objeto Gerber/Excellon salvo por este app pode, em
  princípio, ser aberto por uma instalação real do FlatCAM Python (verificado
  contra o código-fonte Python; **não verificado contra uma instalação
  Python+Shapely rodando de verdade**, que não está disponível neste
  ambiente de desenvolvimento). Detalhes técnicos completos na seção 9.3.
  Geometry agora é persistido em `_java.geometries` (WKT e associação por ferramenta),
  uma extensão do FX não interpretada pelo Python. CNC Job embute o texto G-code em `_java`,
  além do path e nome; na reabertura, reconstrói uma prévia limitada a G0-G3 em XY,
  sem a largura original da ferramenta.

### Gerber

- Parser RS-274X com unidades, formato de coordenadas, modos de coordenada,
  apertures, aperture macros, polaridade, regiões e operações usuais cobertas
  por testes.
- Geometria sólida e geometria `follow`.
- Exibição, tabela de apertures, propriedades e visualização da fonte.
- Operações auxiliares de região não-cobre e bounding box.
- Isolation Routing com parâmetros e resultado em Geometry editável; o G-code é
  gerado depois pelo fluxo Geometry -> CNC Job.
- Cutout Tool com forma, tipo, margens e gaps/bridges; produz Geometry editável,
  que depois gera CNC Job/G-code pelo fluxo Geometry -> CNC Job.
- NCC Tool em primeira fatia funcional.

### Excellon

- Parser, ferramentas, furos e slots.
- Exibição e propriedades/tabela de ferramentas.
- Geração de G-code de furação.

### Geometry e CNC Job

- Objetos Geometry podem ser produzidos por Isolation Routing, Cutout e NCC.
- Geometry pode gerar CNC Job com `safe Z`, profundidade, multi-depth,
  profundidade por passe, feed rate e spindle.
- CNC Job separa e desenha trajetos de viagem e corte.
- Visualização, ativação/desativação e salvamento de G-code.

### Ferramentas adicionais

- Calculadoras de unidades, ferramenta V e galvanoplastia.
- Isolation, Cutout e NCC executam como jobs canceláveis, sem bloquear a thread
  JavaFX.

### Ferramentas do menu Ferramentas já portadas do Python (2026-09-30 a 2026-10-01)

Cada uma segue o Python correspondente (defaults, fluxo, nomes dos objetos) e tem uma entrada detalhada,
com as diferenças deliberadas, na seção 9.1. Lógica em `flatcam-cam` (testada), painel em `flatcam-fx`.

| Ferramenta | Classes principais | Resumo |
| --- | --- | --- |
| 2-Sided | `DoubleSidedToolPanel` | espelha objetos por X/Y e eixo por caixa ou ponto; furos de alinhamento; pré-visualização no plot |
| Align Objects | `AlignObjects` | alinha por 1 ponto (translada) ou 2 (translada e gira), clicando em pads ou furos |
| Calibration | `Calibration` | quatro pontos, G-code de verificação, fatores de escala/inclinação e objetos calibrados |
| Copper Thieving | `CopperThieving` | preenchimento sólido/pontos/quadrados/linhas, robber bar e máscara de galvanoplastia |
| Rules Check | `RulesCheck` | 10 regras de projeto (trilha, cobre, seda, máscara, contorno, anel anular, furos), violações localizáveis no plot |
| Optimal | `MinimumDistance` | menor distância entre os elementos de cobre de um Gerber, pares, locais e demais distâncias |
| Extract Drills | `ExtractDrills` | Gerber → Excellon pelos flashes: fixo, proporcional ou anel anular |
| Cutout, NCC, Isolation, Drilling | (anteriores) | ver seção 5 e 9.7 |
| Paint | `NccGenerator.paint`, `PaintToolPanel` | Standard, Seed, Lines e Combo sobre polígonos |
| Panelize | `Panelize` | grade de cópias de Gerber, Excellon ou Geometry |
| Film | `FilmExporter` | filme positivo/negativo em SVG, PNG ou PDF, com escala, inclinação, espelho e punch |
| SolderPaste | `SolderPaste` | geometria de dispensa por bico e G-code `Paste_1` |
| Subtract | `Subtract` | Gerber ou Geometry menos outro |
| Transform, Calculators | (anteriores) | seção 4 |
| QRCode | `QrCodeMarker` | QR de quadrados de cobre num Gerber (ZXing) |
| Fiducials, Corner Markers | `Fiducials`, `CornerMarkers` | marcas circulares/cruz/xadrez e marcadores de canto, com furos opcionais |
| Punch Gerber | `Punch` | furos nos pads por Excellon ou por tamanho |
| Invert Gerber | `InvertGerber` | inverte cobre e vazio dentro de uma caixa com margem |
| Etch Compensation | `EtchCompensation` | cresce ou encolhe o cobre pela espessura e fator de corrosão |

Todas as ferramentas do menu estão portadas (aparecem no menu, desabilitados).
Conversion e Join Objects: portados Outline→Area, Convert Any→Geo/Gerber/Excellon, Single↔MultiGeo e Join
Gerber/Excellon/Geo.

### Plot Area com geometrias muito densas (2026-10-01)

Camadas de traços (Geometry, caminho central de CNC Job) e os overlays do editor (realce azul e contorno de
referência) com milhares de segmentos visíveis não são mais desenhados como milhares de strokes no `Canvas`:
`DensityRaster` conta a cobertura de área por pixel na CPU (faixas paralelas, antialiasing igual ao vetor) e
`DenseRenderer` faz isso numa thread de fundo, com a imagem anterior movida/escalada enquanto a nova não
chega. Um quadro de pan em 100–500 mil traços passou de ~0,2–1,1 s para 1–3 ms na thread da interface. Detalhes,
medidas e os quatro defeitos de fidelidade corrigidos: `PLOT_PERFORMANCE.md`. `-Dflatcam.plot.density=false`
desliga o modo; `-Dflatcam.plot.density.async=false` volta à rasterização síncrona.

### Transformations

Implementado nesta revisão (2026-09-22), pesquisado diretamente em
`appTools/ToolTransform.py`, `appGUI/ObjectUI.py` (o "mini-painel" comum) e
`app_Main.py` (ações rápidas do menu Options) antes de codificar:

- `org.flatcam.cam.transform.TransformOp` (sealed: `Rotate`, `Scale`, `Skew`,
  `MirrorX`, `MirrorY`, `Offset`) - motor puro de transformação afim via
  `AffineTransformation` do JTS, aplicável tanto a `Geometry` quanto a um
  `Coordinate` isolado (necessário para Excellon, que guarda furos/slots como
  pontos, não só como geometria agregada). `Rotate` usa a convenção
  matemática crua (positivo = anti-horário, igual ao `camlib.Geometry.rotate`
  do Python); as UIs que apresentam "positivo = horário" negam o ângulo antes
  de construir o op, exatamente como o Python faz em seus dois pontos de
  chamada (`obj.rotate(-num, point)`).
- `GerberImage.transformed(op)` / `ExcellonImage.transformed(op)` /
  `ToolGeometry.transformed(op)` - cada tipo de objeto transforma toda a
  geometria que carrega (Gerber: solid + follow + geometria por aperture;
  Excellon: cada furo/slot individualmente, não só o solid agregado;
  Geometry: cada ferramenta do multigeo). Os campos de diâmetro/largura de
  aperture NÃO são recalculados após a transformação (só alimentam a tabela
  de apertures, nenhum cálculo de CAM os lê diretamente) - simplificação
  deliberada de v1, documentada no Javadoc de `GerberImage`.
- **Ações rápidas no menu Opções**: Girar Selecao/Inclinar em X/Inclinar em
  Y/Espelhar em X/Espelhar em Y, com os mesmos ícones do Python
  (`rotate.png`/`skewX.png`/`skewY.png`/`flipx.png`/`flipy.png`, reaproveitados
  via `pom.xml` como os demais ícones legados). Referência sempre "Selection"
  (centro da caixa delimitadora combinada dos objetos selecionados), igual ao
  `app_Main.py`. Sem atalhos de teclado Shift+R/X/Y/bare X/bare Y do Python -
  um acelerador global sem modificador em X/Y sequestraria a digitação normal
  em qualquer campo de texto da aplicação.
- **Ferramenta completa** (`TransformToolPanel`, aberta pelo botão
  "Transformations" do mini-painel, ícone `transform.png`): Reference
  (Origin/Selection/Point/Object), Rotate, Skew X/Y (com Link), Scale X/Y (com Link),
  Flip X/Y, Offset X/Y, Buffer distância/percentual e Reset Tool (ícone `reset32.png`). Cada botão aplica
  de imediato à seleção atual da árvore, sem passo de "Gerar" - igual ao
  Python. Botões sem ícone (igual ao Python - só Reset e o combo de tipo de
  objeto de referência têm ícone lá).
- **Mini-painel "Transformations"** (`MainWindow.transformationsSection`):
  igual nos três tipos de objeto (Scale uniforme sobre a Origem + Offset
  (dx,dy) + botão que abre a ferramenta completa), replicando o
  `ObjectUI.py` comum do Python (que já era compartilhado entre
  Gerber/Excellon/Geometry, não três painéis distintos).
- CNC Job recusa transformação (`CNC Job nao pode ser transformado`), igual
  ao Python.

Buffer e referência Object entregues em 2026-10-02. Buffer usa worker, valida
unidades e publica atomicamente; Excellon atualiza diâmetros, conservando centros.
Gerber conserva Follow, mas não reconstrói integralmente metadados/macros de
aberturas no percentual. Não há undo global; limites em TRANSFORMATIONS.md.

## 5. NCC: estado exato da implementação atual

O commit-base `9f6c7463` adicionou a primeira fatia vertical do Non-Copper
Clearing (uma ferramenta). Nesta revisão (2026-09-22) o NCC evoluiu para
**multi-tool com Rest Machining**, seguindo a ordem recomendada na revisão
anterior deste documento. Pesquisa em `appTools/ToolNCC.py` confirmou o
algoritmo exato antes da implementação (ver `NccGenerator`'s class doc para a
citação completa):

- `NccParameters.toolDiameters()` é uma lista ordenada de diâmetros (não mais
  um único `double`). `NccToolSettings` guarda overlap/método/connect/contour/
  copperOffset por ferramenta CLEAR; a margem e o boundary são comuns. Com
  Rest Machining, overlap/método continuam por ferramenta, enquanto
  connect/contour/offset vêm dos controles comuns, como no Python.
- `NccOrder` (`NONE`/`FORWARD`/`REVERSE`) replica o `ncc_order_radio` do
  Python; ignorado quando `restMachining=true`, que sempre processa da maior
  para a menor ferramenta (mesmo comportamento do Python, que desabilita o
  radio nesse caso).
- **Sem Rest Machining**: cada ferramenta limpa a área não-cobre inteira de
  forma independente (mesmo resultado, diâmetros diferentes) - não há
  coordenação entre ferramentas, replicando `gen_clear_area`.
- **Com Rest Machining**: ferramentas processadas da maior para a menor; a
  área restante para a próxima ferramenta é a área anterior menos a pegada
  física (o caminho já limpo, re-inflado pelo próprio raio da ferramenta,
  com um encolhimento de 1e-6 para evitar que ruído de ponto flutuante "coma"
  área que a ferramenta não varreu de fato) de cada ferramenta maior que já
  rodou - replica `gen_clear_area_rest`. Um polígono que uma ferramenta não
  consegue limpar simplesmente permanece disponível para a próxima (menor),
  sem precisar reproduzir a lista `rest_geo` separada do Python.
- `NccResult.toolResults()` guarda a contribuição de cada ferramenta
  (diâmetro + geometria própria + polígonos que falharam), preservando a
  associação ferramenta -> caminho; `geometry()` continua expondo a união de
  todas as ferramentas para plotagem/compatibilidade.
- O objeto Geometry resultante agora carrega `List<ToolGeometry>` (par
  diâmetro+geometria por ferramenta) em vez de um único diâmetro opcional;
  "Geometry -> CNC Job" (`GeometryCncToolPanel`/`GCodeGenerator.generateGeometryCncJob`)
  detecta esse caso e gera **um único G-code** com troca de ferramenta (M0
  opcional + comentário, mesmo padrão já usado no G-code de furação) entre
  seções, em vez de pedir um diâmetro ao usuário - mesma abordagem do
  `mtool_gen_cncjob` do Python (um CNCJob, não um por ferramenta).
- **Boundary** (`NccBoundary`, sealed interface): `Itself` (convex hull do
  próprio Gerber - default, igual antes) ou `ReferenceGerber`/`ReferenceGeometry`
  (objeto de referência já carregado no projeto). Réplica de
  `calculate_bounding_box()`/`ncc_select==2` do Python: para um Gerber de
  referência, o boundary é a interseção dos dois convex hulls (fonte ∩
  referência); para uma Geometry de referência, a forma é usada **como está**,
  sem convex hull (confirmado no código Python - só o caso Gerber tira hull).
  "Area Selection" permite escolher um retângulo com dois cliques ou um
  polígono por vértices no Plot Area (Enter/botão direito conclui, Esc cancela).
  Uma referência Geometry linear é preservada até a aplicação da margem, para
  não perder o contorno antes da operação de buffer.
- **Verificar validade dos diâmetros** (`NccGenerator.minimumCopperClearance`):
  réplica de `find_safe_tooldia_multiprocessing`/`find_optim_mp` do Python -
  calcula a menor distância entre quaisquer duas partes de cobre disjuntas do
  Gerber e informa no console se pelo menos uma ferramenta selecionada é fina
  o bastante para um isolamento completo. Puramente informativo, não bloqueia
  a geração (mesmo comportamento do Python); painel tem um checkbox
  "Verificar validade dos diâmetros", marcado por padrão (mesmo default do
  Python).
- Painel `NccToolPanel` ganhou uma tabela editável de diâmetros
  (adicionar/remover) e os controles "Rest Machining"/"Order". Passou por uma
  rodada de polimento de UI/UX após feedback visual direto do usuário: seções
  com título (FERRAMENTAS/PARAMETROS DE LIMPEZA/MULTI-FERRAMENTA), Enter no
  campo de diâmetro adiciona a ferramenta, botão Remover desabilita sem
  seleção, lista de diâmetros preserva a ordem de entrada (para Order = None),
  tabela com altura dinâmica e
  coluna ocupando 100% da largura (`CONSTRAINED_RESIZE_POLICY`), e tooltips
  explicando Method/Connect/Contour/Copper offset/Rest Machining/Order com
  `showDuration` estendido (`Duration.INDEFINITE`) - o padrão do JavaFX
  esconde tooltips após ~5s mesmo com o mouse parado em cima, cedo demais
  para textos multi-linha. Evite usar `Button.setDefaultButton`/
  `setCancelButton` nos painéis desta app: nenhum dos 4 temas estiliza o
  pseudo-estado `:default` do JavaFX, e o botão fica com a aparência pálida
  do Modena por baixo do tema (foi tentado e revertido nesta mesma revisão).
- A tabela NCC agora distingue `ISO` e `CLEAR` por linha e executa apenas as
  ferramentas selecionadas. `ISO` só aparece para origem Gerber: cria contornos
  próprios, recortados pelo boundary, antes do clearing. Pelo menos uma
  ferramenta `CLEAR` é obrigatória, como no fluxo Python. O maior diâmetro ISO
  define o envelope de cobre a preservar na limpeza; isso evita deixar a
  ferramenta CLEAR invadir uma faixa reservada ao isolamento. O painel oferece
  Climb/Conventional para inverter o sentido do contorno externo ISO; o sentido
  dos anéis internos permanece como gerado, seguindo `generate_envelope()`.

O botão NCC já deve abrir o painel da ferramenta. Se voltar a “não fazer nada”,
primeiro suspeite de snapshots internos desatualizados no repositório Maven
local e execute `install` no reactor completo, conforme a seção de comandos.

Ainda falta para paridade NCC:

- comparação visual dos contornos ISO e resultados NCC com o Python;
- teste manual dos parâmetros por ferramenta e comparação diferencial com
  resultados do Python;
- integração com Tools Database ("Pick from DB");
- comparação diferencial mais ampla com resultados do Python (incl. Rest
  Machining, boundary por referência e "Check validity" num board real).

Fechados nesta revisão: boundary por objeto de referência (Gerber ou
Geometry), seleção retangular de área, ordem manual das ferramentas,
seleção de subconjunto e operação ISO/CLEAR por linha e
validação/sugestão de diâmetro ("Check validity"). Uma Geometry usada como
origem precisa conter área preenchida; contornos puros são rejeitados, como
no Python.

## 6. Matriz honesta de paridade

Os rótulos abaixo são deliberadamente conservadores.

| Área | Estado | Observação principal |
| --- | --- | --- |
| Shell, temas e layout principal | forte/parcial | base utilizável; barra lateral recolhível, barra inferior com coordenadas, snap X/Y, eixos, HUD, A4, console, unidades e atividade; aba básica de Preferências para tema e Plot Area; vários menus ainda não têm fluxo completo |
| Plot 2D e interação | forte/parcial | Canvas com seleção por clique/retângulo, menu contextual e mover/copiar objetos com prévia; snap configurável atua no posicionamento, grade visual pode ser ocultada independentemente do snap, eixos/HUD/A4 alternáveis; faltam estilos avançados da grade e perfilamento para placas enormes |
| Árvore lateral Gerber | forte/parcial | aparência e ações principais implementadas; editor inicial (menu "Editar") |
| Importação Gerber | forte/parcial | boa cobertura do subconjunto real testado; ampliar corpus de compatibilidade |
| Ferramentas Gerber/Geometry | parcial | Isolation tem Follow, Rest Machining (com "Forced Rest" como no Python), saídas separadas e áreas de exceção; Cutout aceita Gerber ou Geometry preenchida e tem Bridge, Thin, M-Bites e gaps manuais por área (Free-form com margem negativa corrigido - ver [PLANO_PARIDADE.md](PLANO_PARIDADE.md)), mas não o gesto exato do cursor Python; NCC é multi-tool com Rest Machining, ISO/CLEAR, boundary, validação e leitura de `.FlatDB`; faltam comparação visual com projetos reais e opções avançadas |
| Editor Gerber | funcional, paridade parcial | todos os comandos da paleta têm ação: seleção, desenho, edição de aberturas, operações geométricas e undo/redo; várias ferramentas avançadas usam parâmetros numéricos no painel em vez dos gestos/controles exatos do Python; falta validação manual da interação completa e corpus amplo de Gerbers |
| Importação/plot Excellon | parcial | parser, plot, editor de furos/slots, exportação `.drl` do estado editado, Drilling Tool com Multi-Depth/Dwell/Offset Z e `.FlatDB`; projetos Python importam valores básicos de furação por ferramenta; Milling Tool cria Geometry para furos/slots; faltam opções avançadas e validação manual ampla |
| Geometry | parcial | multi-tool e conversões Single↔Multi; CNC por ferramenta, Feed XY/Z, Dwell, Extra Cut, V-Tip e compensação Path/In/Out/Custom; posições comuns e exclusões Around/Over com validação/persistência, somente fresagem sem sonda; editor com Texto, Borracha e Paint Shape transacional; faltam gestos avançados; ver GEOMETRY_CNC.md, CNC_EXCLUSIONS.md e GEOMETRY_EDITOR.md |
| CNC Job | parcial | geração, plot (com numeração, setas e navegação passo a passo, além do Python), abertura e edição de G-code, Aplicar/Cancelar e Salvar; prévia G0-G3 em XY, laser por estado de emissão, ICP/HPGL/RML lineares; 19 perfis Python no seletor e `Paste_1` embutido no SolderPaste, total 20 de 20 ports parciais; Mach3 com sonda gera G31/G92 e exige confirmação manual, sem prévia; Roland inicialmente só MM/uma ferramenta e sem metadados de diâmetro na reabertura; ver `PREPROCESSADORES.md` |
| Persistência de projeto | parcial | `.fcnproj` embute objetos/G-code e parâmetros CNC individuais, compensação/posições e seleção/ordem de Drilling; confirmação da sonda não persiste; abertura e exportação `.FlatPrj` JSON/XZ validadas com serializadores Python e 18 objetos reais; não é round-trip universal, ver COMPATIBILIDADE_FLATPRJ.md |
| Calculadoras | parcial | três calculadoras implementadas |
| Ferramentas do menu Ferramentas | forte/parcial | 24 de 24 portadas (seção 4); vários painéis têm só a lógica testada e ainda precisam de validação manual no app |
| Plot Area com geometria densa | forte | LOD por densidade assíncrono (seção 4 e `PLOT_PERFORMANCE.md`); faltam margem em volta da vista e fidelidade total em diagonais de 45° |
| Plataforma (Java/launcher) | forte | Java 25 + JavaFX 25.0.4; launcher nativo opcional para pedir a GPU de alto desempenho (exige `g++`); opções de arquitetura futuras guardadas na memória do projeto (ver 9.0) |
| Transformations | forte/parcial | Rotate/Skew/Scale/Flip/Offset, Buffer distância/percentual e referência Object; Buffer assíncrono/atômico; reconstrução de metadados/macros Gerber ainda parcial, ver TRANSFORMATIONS.md |
| Tools Database | parcial | editor com 63 campos Python, busca/filtro, `.FlatDB`, backup e campos desconhecidos preservados; integrado a NCC/Isolation/Drilling/Geometry CNC/Milling/Paint/Cutout; Offset e profundidades Cutout/Thin transferidos, Paint individual; falta consumo integral de opções avançadas |
| Preferências globais | inicial/parcial | aba funcional para tema, snap, grade visual e visibilidade do Plot Area; ainda longe da cobertura do Python |
| Automação/CLI/scripts | ausente | não é a prioridade imediata |

“Forte/parcial” não significa compatibilidade certificada. Significa que o
fluxo principal existe e tem testes, mas ainda há casos e opções do legado a
cobrir.

## 7. Limitações e dívida técnica conhecidas

### Persistência parcial

Gerber/Excellon são salvos como geometria embutida. Gerbers salvos após este
incremento também preservam cada forma e sua ordem, permitindo reabrir e
continuar a edição. Projetos `.fcnproj` antigos, salvos com uma única união
por abertura, ainda carregam e plotam, mas o editor os mantém somente para
inspeção: reconstruir uma edição a partir dessas uniões poderia perder regiões
ou fundir pads/trilhas que se tocam. Reabra o arquivo Gerber original para
editar e salve um projeto novo.

Geometry gerada, geometria de plot de CNC Job e parâmetros por ferramenta
continuam sem snapshot completo. Ver seção 9.3.

### `MainWindow` concentra responsabilidades demais

`flatcam-fx/.../MainWindow.java` tem mais de 3.000 linhas e concentra
estado, menus, árvore, diálogos e orquestração de jobs. Não é necessário
reescrever a tela agora, mas novas áreas grandes devem extrair controladores ou
serviços coesos. A implementação do editor não deve aumentar indefinidamente
essa classe - por isso o Gerber Editor vive em `GerberEditorController`
(sessão, painel, camadas de plot e handler de seleção do canvas); o
`MainWindow` só expõe a ele uma interface `Host` estreita. Novas fatias do
editor devem crescer o controlador, não o `MainWindow`.

### Renderer atual é uma etapa, não um compromisso definitivo

O Canvas resolve a fatia vertical atual. Não migre prematuramente para GPU ou
código nativo. Primeiro meça arquivos representativos, tempo de frame, memória
e latência de seleção. Uma troca de renderer só é justificada por perfil real.

### Cobertura de interface

O módulo `flatcam-fx` ainda não possui uma suíte automatizada de interação de
UI. Fluxos críticos precisam de smoke test manual; lógica extraível deve ficar
nos módulos testáveis ou em classes sem dependência forte da janela.

### Localização e consistência

Ainda há textos fixos e mistura de idiomas em pontos da UI. Não espalhe novas
strings sem necessidade. A internacionalização completa pode vir depois, mas
novos painéis devem manter terminologia consistente com o produto.

### Temas disponíveis

Original restaura o CSS e a paleta anteriores à revisão visual de gelo, com
branco e grafite. Gelo mantém a interface recente em branco azulado e azul
escuro. Ambos usam o Modena do JavaFX; a dependência e o menu AtlantaFX foram
removidos. Preferências antigas `CUSTOM_*` migram para Gelo e `ATLANTAFX_*`
para Original, preservando claro/escuro. A limitação anterior de troca do
user-agent stylesheet do AtlantaFX deixou de se aplicar.

### Corrigido: menu de contexto herdando texto branco da célula selecionada

Achado e corrigido na revisão de 2026-09-23, causado pela própria correção
de contraste da árvore descrita mais abaixo. Ao clicar com o botão direito num item **já
selecionado**, `MainWindow` chamava `menu.show(cell, ...)` - ancorando o
popup na `TreeCell` em si. Uma célula selecionada+focada tem
`-fx-fill: -fc-selection-text-focused` (branco); `-fx-fill` é uma
propriedade herdável, e um `ContextMenu` mostrado via `show(Node, ...)`
herda essa propriedade do nó-âncora. O valor herdado do nó-âncora **vence
qualquer contra-regra CSS** de prioridade normal (confirmado tentando três
seletores diferentes em `components.css`, todos perderam) - não é algo
corrigível só com CSS. O texto de todo o menu ficava branco, invisível
contra o fundo claro do popup, exceto a linha em hover (que recebe estilo
próprio). Corrigido trocando a âncora para `projectTree` (a árvore inteira,
que não carrega essa cor sobrescrita) em vez da célula - sem efeito na
posição do popup, já que o código já usa coordenadas de tela absolutas
(`event.getScreenX()/getScreenY()`). Confirmado isolado (harness fora da
tela) antes/depois da correção.

### Múltiplos monitores: DPI e largura da barra lateral (2026-09-29)

Bug do JavaFX/Glass: ao **arrastar** a janela maximizada entre monitores com
escalas diferentes (ex.: 200% e 100%), a geometria do Stage e o `outputScale`
ficam inconsistentes e a interface distorce; Win+Seta não dispara o bug.
Contorno em `MainApp`: um poll (400 ms) detecta a transição
`isMaximized()` false -> true; após 3 leituras estáveis (`STABILITY_THRESHOLD`)
e 1 s de armamento (`ARM_DELAY`), o Stage é **recriado sobre a mesma Scene**
no monitor atual. Detalhes que importam: o monitor é capturado pela posição do
mouse no primeiro poll maximizado, `setX/setY` usam os bounds do `Screen` do
JavaFX (não os do AWT), `Platform.setImplicitExit(false)` evita o
encerramento ao fechar o Stage antigo (o fechamento explícito chama
`Platform.exit()`) e o Timeline anterior é parado a cada recriação.

Largura da sidebar por monitor: `AppPreferences.loadSplitHorizontalForScreen`
/ `saveSplitHorizontalForScreen` (chave por id AWT do monitor, com fallback
para o valor compartilhado). `MainWindow.setCurrentScreenId()` reaplica o
divisor via `Platform.runLater` após cada recriação.

### Desempenho do parse do Gerber (2026-09-29)

Medido com JFR na placa STM32F4-spindle (139 KB): 80% do parse estava no overlay
legado do JTS (`SnapIfNeededOverlayOp`, via `UnaryUnionOp`/`Geometry.union`).
Todas as uniões em `flatcam-cam` passaram a usar `OverlayNGRobust` (união e
`overlay` UNION/DIFFERENCE) e a geometria por abertura (`apertureGeometry()`,
usada só por Mark, editor e persistência) é calculada sob demanda por
`LazyApertureGeometry`. Parse: ~10-14 s -> ~1 s (aquecido). A geometria fica
equivalente (o solid tem alguns vértices a menos por causa do snapping) e os 329
testes passam. `ProjectFileIO.load` tinha o mesmo problema (16 s): o codec Gerber ainda usava
`UnaryUnionOp` e unia cada abertura ao abrir. Agora usa `LazyApertureGeometry`
(pública) e o `MainWindow` usa `OverlayNGRobust`; abrir levou ~0,65 s. Salvar
leva ~2,2 s, quase tudo compressão XZ. O buffer da isolação em
painéis grandes (4x4: ~24 s) era o `BufferOp` do JTS num multipolígono enorme
(`SubgraphDepthLocater`). `ParallelGeometry.separateGroups` agora separa o cobre em
grupos que não podem se tocar depois do offset (caixas envolventes alargadas), e
cada (grupo, passe) roda em paralelo: 4x4 caiu para ~3 s, com anéis idênticos
(comprimento e contagem iguais ao buffer único). A união das pegadas do G-code
(`ParallelGeometry.union`, por grupos de caixas) caiu de ~31 s para ~15 s no 4x4;
depois, jobs com mais de 2000 deslocamentos não unem a pegada dos deslocamentos
(coleção de polígonos), os buffers dos cortes rodam em paralelo e a formatação de
coordenadas/linhas deixou de usar `String.format` (texto idêntico, com teste):
G-code do 4x4 de ~31 s para ~8-10 s. Sem código nativo.

### Pendências abertas (2026-10-01)

- **Validação manual** dos painéis recentes no app real (lista na seção 3); só a lógica tem teste.
- **Plot Area densa:** a imagem de densidade cobre só a vista em que foi feita (num arraste longo a borda que
  entra fica vazia até parar); o custo cresce com o comprimento total dos traços em pixels (500 mil traços ≈
  1 s para a imagem exata); polígonos preenchidos muito densos ainda são vetoriais; diagonais de 45° têm
  até meio pixel de erro de cobertura nas bordas.
- **SolderPaste:** o preview do job é montado da geometria (o parser de G-code trata Z positivo como
  deslocamento) e o job não tem a tabela de passos; parâmetros valem para todos os bicos.
- **QRCode:** falta exportar o QR como SVG/PNG e as cores de preenchimento (só afetam a exportação).
- **Align Objects:** sem o realce em cor do objeto durante os cliques.
- **Film:** PDF escrito direto em vetores (sem biblioteca), PNG renderizado no DPI pedido.
- **`flatcam-fx`:** `MainWindow` passa de 7 mil linhas; os `Host` dos painéis continuam dentro dele.
- **Terminais antigos:** depois de mudar o `JAVA_HOME`, reabra o terminal e o VS Code; o `PATH` pode ainda listar
  o JDK 21 antes do 25 (afeta só scripts que chamam `java` direto).

### Diferenças intencionais já aceitas

- Operações pesadas rodam em background e são canceláveis.
- Progresso deve refletir trabalho real sempre que for mensurável.
- Ícones legados podem receber tratamento de contraste no tema escuro.
- Controles e amostras de cor podem ser modernizados sem mudar sua semântica.
- JTS substitui Shapely/GEOS na implementação Java; compare resultados por
  tolerância geométrica, não por igualdade textual ou ordem de coordenadas.

## 8. Histórico de progresso (2026-09-23; estado atual nas seções 9.5-9.6 e 15)

**NCC multi-tool com Rest Machining, boundary por objeto de referência,
"Check validity", Transformations, e persistência embutida de Gerber/Excellon
(compatível com o `.FlatPrj` do Python) foram concluídos** (seções 5, 4 e
9.3) - critérios de aceite verificados via testes do reactor (112 testes) e
smoke test do app; validação visual dos painéis feita ao vivo com o usuário
a cada rodada. A compatibilidade Python é verificada por leitura de código +
round-trip Java, não contra uma instalação Python real (indisponível neste
ambiente) - ver seção 9.3 para o aviso completo.

Na época, o que restava de 9.3 (Geometry e CNC Job com o mesmo tratamento) precisava de
mudanças de modelo reais antes de qualquer serialização (Geometry precisa de
um dict de parâmetros CAM persistente por ferramenta; CNC Job precisa reter
uma lista por segmento durante a geração) - cada um é essencialmente seu
próprio projeto, no mesmo espírito de "Excellon+Gerber primeiro" que guiou
essa fase.

**Gerber Editor - fatia 1/7 concluída (2026-09-23, opinião do Claude
registrada em 22/09 acima aplicada: avançar o editor em vez de Geometry/CNC
Job persistence, já que a lacuna mais visível é a ausência total de editor e
a persistência Gerber/Excellon necessária para "objeto editável que sobrevive
a save/reload" já existia).**

- `org.flatcam.cam.gerber.edit.GerberEditSession` (núcleo, `flatcam-cam`,
  testado): sessão de edição com `apply()`/`nextEditedName()`, ported de
  `AppGerberEditor.py`'s `edit_fcgerber()`/`update_fcgerber()`. Sem operações
  de edição ainda - `workingImage()` é sempre igual ao objeto de origem.
- `GerberEditToolPanel`/`MainWindow` (fatia de UI): menu "Editar" no Gerber
  abre a sessão no painel "Ferramenta" (oculta o objeto original no plot,
  igual ao Python's `orig_grb_obj.visible = False`); "Aplicar" cria um NOVO
  objeto `<nome>_edit` (nunca sobrescreve o original, igual ao Python);
  "Cancelar" descarta a sessão sem criar nada, restaura a visibilidade.
- Deliberadamente **sem**: seleção/hit-testing no canvas, command stack
  undo/redo, qualquer ferramenta de desenho (pad/track/region/disc/buffer/
  scale/etc.), tabela de apertures editável. Ver seção 9.4 para a ordem das
  próximas fatias (2: seleção/hit-testing; 3: undo/redo; 4: mover/copiar/
  excluir; 5: pads/tracks/regions/apertures).
- Critério de aceite desta fatia: usuário consegue entrar no editor, ver o
  objeto original ocultado, e Aplicar/Cancelar produzem exatamente o
  resultado acima - verificado por `GerberEditSessionTest` (4 testes) e smoke
  test manual (`MainApp started`/`stop` limpo). Nenhuma edição de geometria é
  possível ainda; não anunciar isso como "editor funcional" para o usuário
  final até pelo menos a fatia 2 (seleção) e 5 (ferramentas básicas)
  existirem.

**Gerber Editor - fatia 2/7 concluída (2026-09-23): seleção e hit-testing.**
Portado de `AppGerberEditor.py`'s `SelectEditorGrb`,
`draw_selection_area_handler()` e `plot_all()`, mais o `selection_type` de
`app_Main.py`:

- Formas individuais: `GerberShape` (abertura, geometria, polaridade) e
  `GerberImage.shapes()` - o parser agora guarda cada flash/trilha/região antes
  da união (regiões sob a abertura `"0"`, como o `'REG'` do Python).
  `transformed()` transforma as formas também.
- `GerberEditSession`: `clickSelect`/`boxSelect`/`selectedApertures`, com as
  regras do Python - só formas escuras são selecionáveis (o Python só testa
  `'solid'`); clique simples substitui a seleção, Ctrl+clique alterna;
  caixa arrastada para a direita seleciona formas **envolvidas**, para a
  esquerda formas **tocadas**. Divergência deliberada: o Python alterna no
  máximo um acerto por bloco de 77 formas (artefato do seu multiprocessing);
  aqui todos os acertos sob o ponto são alternados.
- Limitação conhecida: objeto restaurado de projeto (`.fcnproj` v2 guarda um
  agregado por abertura) não tem as formas individuais; a sessão as
  reconstrói dividindo cada agregado em partes disjuntas
  (`shapesApproximated()`), e o painel avisa que pads/trilhas que se tocam
  viram uma forma só e que regiões ficam de fora. Resolver isso exige
  persistir as formas individuais no codec (lista por abertura, como o
  `.FlatPrj` real do Python já faz) - candidato natural para a fatia 7.
- UI: `GerberEditorController` (novo, fora do `MainWindow`) desenha as formas
  em `#FF0000AF` e as selecionadas em `#0000FFAF` (cores e alfa de
  `global_draw_color`/`global_sel_draw_color` + `plot_shape()`), e liga o
  `PlotAreaView.SelectionHandler`: com o editor ativo, botão esquerdo
  seleciona (retângulo azul/verde de `global_sel_*`/`global_alt_sel_*`) e
  direito/meio fazem pan; sem editor, o canvas se comporta como antes. O
  painel mostra quantas formas e quais aberturas estão selecionadas (no lugar
  do destaque de linhas da tabela de aberturas do Python, que ainda não
  existe).
- Testes: `GerberEditSessionTest` (15, cobrindo clique, alternância, caixa
  nos dois sentidos, polaridade clara, fallback e transformação). O
  comportamento interativo no canvas foi verificado só até a inicialização do
  app - falta validação visual manual.
- **Corrigido nesta revisão (2026-09-23):** a pendência acima era, na
  prática, bloqueante - o usuário reportou "clicar não faz nada" ao testar
  esta fatia, e a causa raiz era exatamente esse bug. A árvore tem um
  listener em `getSelectedItems()` (linha ~625 do `MainWindow`) que remove
  reativamente linhas de categoria (Gerbers/Excellon/...) da seleção, já que
  elas são só agrupamento visual. Esse listener chamava
  `clearSelection(row)` **sincronamente**, de dentro da própria notificação
  de mudança - e o `TreeViewBehavior` interno do JavaFX (que também escuta a
  mesma lista, para implementar o clique) não tolera a lista mudar de
  tamanho enquanto ele ainda está lendo a mudança corrente, e lança
  `IndexOutOfBoundsException` no PRÓXIMO clique em qualquer linha da árvore.
  Na prática isso deixava a árvore (e portanto todo menu de contexto,
  inclusive "Editar") morta para cliques depois da primeira interação com
  uma linha de categoria. Corrigido adiando a chamada com
  `Platform.runLater(...)`. Reproduzido e confirmado antes/depois com um
  harness isolado (Stage fora da tela, eventos de mouse sintéticos reais
  contra `TreeCell`s reais) - a versão antiga reproduz o
  `IndexOutOfBoundsException` byte a byte igual ao relatado; a versão com
  `Platform.runLater` não lança nada.
- **Também corrigido nesta revisão: contraste de texto na árvore do
  projeto** (`components.css` + as 4 paletas `vars-*.css`). Dois bugs
  distintos, achados durante o teste manual desta fatia:
  1. Linha selecionada que perde o foco (usuário clica em Propriedades,
     Ferramenta ou no Plot Area) usava a cor padrão do JavaFX/Modena para
     esse estado, que em tema escuro fica cinza-claro com texto quase
     invisível. Corrigido com cores próprias por estado
     (`-fc-selection-bg-focused/-unfocused`,
     `-fc-selection-text-focused/-unfocused`).
  2. Ao trocar a seleção para outra linha, a linha desselecionada ficava com
     texto **preto puro**, mesmo com a paleta escura carregada - bug
     separado do Modena (o fill padrão de `.tree-cell .text` deriva de
     `-fx-text-background-color`, que não respeita bem `-fx-text-base-color`
     nesse caso). Corrigido fixando `-fx-fill: -fc-panel-text` para todo
     `.tree-cell:filled .text`, servindo de base para as regras de seleção
     acima.
  Achado (2): a correção inicial do item 1 usava `.tree-cell:selected:focused`
  para distinguir os dois estados - mas o pseudo-estado `:focused` de uma
  `TreeCell` reflete o índice de foco interno do `FocusModel` da árvore, não
  se a árvore em si tem foco de verdade, e nunca é desligado quando o foco
  sai da árvore para outro controle. Trocado para `.tree-view:focused
  .tree-cell:selected`, que usa o foco real do controle. Os dois bugs foram
  confirmados e corrigidos verificando o estado computado real (pseudo-classes
  e `text.getFill()`) num harness isolado antes/depois, não só lendo o CSS.

**Gerber Editor - primeiro fluxo de edição concluído no código (2026-09-24;
posicionamento visual validado pelo usuário em 2026-09-25).** As fatias 3 e 4 e a persistência das
formas da fatia 7 foram implementadas juntas:

- `GerberEditSession` mantém uma lista de formas por revisão e até 100 estados
  de undo/redo. Excluir, mover e copiar operam sobre a seleção; mover/copiar
  aplicam deslocamentos X/Y informados no painel e também movem a geometria
  `follow`. A cópia nova fica selecionada.
- `GerberImage.withEditedShapes()` recompõe cobre sólido em ordem de
  polaridade dark/clear, geometria `follow` e uniões por abertura. O cálculo de
  Apply roda num job cancelável com progresso; Cancelar o job conserva a sessão
  aberta. Aplicar cria um novo Gerber e enquadra o resultado no Plot Area.
- `GerberFlatPrjCodec` escreve cada forma na lista de geometria da sua
  abertura, no shape do `.FlatPrj` Python. `_java.shape_order` guarda a ordem
  global entre aberturas e polaridades para a reconstrução Java. Regiões
  ficam sob a abertura `0` (`REG`). Os tipos e campos de aberturas gravados
  agora usam os nomes do Python (`C`, `R`, `O`, `P`, `size`, `diam`, `nVertices`),
  mantendo a leitura dos nomes usados pelos projetos Java anteriores.
  `ProjectFileIO` publica o arquivo salvo
  por substituição atômica quando o sistema suporta e o save roda fora da
  thread JavaFX.
- Projetos Java antigos, que continham apenas a união por abertura, continuam
  abrindo; suas formas individuais são irrecuperáveis e o editor bloqueia
  operações nelas para não destruir geometria. Projetos Python sem
  `_java.shape_order` aceitam edição quando todas as formas são dark; se houver
  formas clear, a ordem relativa é incerta e a edição fica bloqueada.
- O painel mantém deslocamento numérico X/Y e agora os botões/ícones Mover e
  Copiar iniciam um posicionamento com dois cliques no canvas: origem e destino,
  com prévia entre eles. Esc ou clique direito curto cancela. Ctrl+Z/Y acionam o
  histórico da sessão sem capturar atalhos dos campos de texto. Naquele
  incremento ainda faltavam ferramentas de desenho e tabela editável de aberturas.
  Compatibilidade com um FlatCAM Python executável continua sem validação cruzada real.
- Verificação automática: `test`, 137 testes, sem falhas; teste de
  inicialização chegou a `MainApp started`. Ainda executar manualmente o fluxo
  Abrir Gerber -> Editar -> selecionar -> mover/copiar/excluir -> undo/redo ->
  Aplicar -> Salvar Projeto -> reabrir.

**Exportação Gerber da imagem atual (2026-09-25).** `GerberExporter` em
`flatcam-cam` escreve o cobre sólido resolvido em regiões G36/G37, com unidades,
coordenadas absolutas e polaridade dark/clear. A UI usa esse resultado em
"Salvar como..." para qualquer Gerber, inclusive um `_edit` sem `sourcePath`;
antes ela copiava o arquivo original, perdendo alterações, ou recusava objetos
sem origem. O arquivo é publicado via temporário e substituição atômica quando
suportada, evitando truncar um destino existente em caso de falha. Testes cobrem
edição, transformação, vazios com ilhas, imagem vazia,
limites de coordenadas e exportação/reabertura de uma placa real. O arquivo
preserva a imagem final, **não** os comandos/apertures/atributos X2 originais;
essa reconstrução semântica é um passo posterior. Coordenadas usam seis casas
decimais; regiões que colapsam nessa precisão são recusadas em vez de
silenciosamente perdidas. Ainda validar visualmente o arquivo exportado no
FlatCAM Python ou em visualizador Gerber independente.

**Trilhas multiponto no Gerber Editor (2026-09-25).** A ferramenta Track não
termina mais obrigatoriamente no segundo clique: cada clique adiciona um trecho
e Enter, duplo clique ou botão direito curto conclui a trilha. Backspace volta
um ponto; T/R percorre nos dois sentidos os cinco modos do legado (45 graus,
45 invertido, 90 graus, 90 invertido e ângulo livre). Com grid snap desligado,
o segmento é livre, como em `TrackEditorGrb.utility_geometry()` do Python. O
preview mostra o caminho confirmado e o trecho até o cursor. A trilha completa
é publicada como uma única `GerberShape`, com `LineString` em `follow` e cobre
bufferizado pela abertura C; por isso todo o gesto é um único passo de
undo/redo. `TrackBendModeTest` cobre os cinco roteamentos e
`GerberEditSessionTest` cobre criação, seleção e histórico da polilinha.

**Interação de objetos no Plot Area (2026-09-25).** Fora do Editor Gerber, clique
esquerdo seleciona o objeto visível no topo (cliques repetidos alternam entre
objetos sobrepostos); arrasto esquerda→direita exige
enquadramento completo e direita→esquerda seleciona por interseção do retângulo
com os limites do objeto. Ctrl alterna a seleção múltipla. A seleção sincroniza
com a árvore Projeto e recebe um contorno no canvas. Clique direito curto abre
o mesmo menu funcional da árvore para o objeto atingido; arrasto direito/meio
continua deslocando a vista. Clicar numa área vazia com o botão direito mostra
Enquadrar tudo/Limpar seleção. O Editor Gerber mantém seu próprio handler de
seleção sem herdar o menu global. `PlotObjectSelection` cobre hit-testing e
seleção por caixa em testes sem JavaFX. Os gestos básicos foram validados pelo
usuário; ainda faltam ações próprias de editor no menu do canvas.
O menu do Plot Area recebeu cores explícitas para texto normal e item focado
nos quatro temas; o destaque escuro usa azul mais profundo para manter
contraste de texto de pelo menos 4,5:1 (teste automático do CSS).

**Posicionamento no Plot Area (2026-09-25).** O menu contextual de um Gerber,
Excellon ou Geometry visível agora oferece "Mover no Plot Area" e "Copiar no Plot
Area". Um fantasma da geometria acompanha o cursor; clique esquerdo confirma o
deslocamento entre o ponto do menu e o destino, enquanto Esc ou clique direito
curto cancela. Multisseleção move/copia o grupo com o mesmo deslocamento.
CNC Jobs não entram nesse fluxo: mudar apenas o desenho deixaria o G-code
incoerente. Os gestos foram validados pelo usuário; ainda verificar a
persistência após salvar e reabrir um projeto. Não há undo/redo global para
cópias ou outras operações fora do histórico de movimentos.
Movimentos confirmados no Plot Area têm histórico próprio: Ctrl+Z desfaz e
Ctrl+Y refaz, inclusive movimentos de grupos; cópias e transformações feitas
por outros caminhos invalidam esse histórico. O menu contextual do Plot Area
agora fecha ao clicar em qualquer ponto do canvas. A janela maximizada tenta
reabrir no último monitor usado enquanto ele estiver conectado, aplicando a
largura lateral já salva para aquele monitor; sem ele, abre no principal. O
usuário confirmou o comportamento em dois monitores em 2026-09-25.

Alternativa não escolhida agora, mas ainda válida como próximo passo depois
das próximas fatias do editor: completar 9.3 (Geometry/CNC Job persistence),
adiando para quando algo realmente força a mão (ex.: um NCC resultado
precisar sobreviver a um reload).

### Critérios de aceite do incremento concluído (referência)

- O usuário consegue configurar ao menos duas ferramentas e sua ordem. ✅
  (`NccToolPanel`: tabela de diâmetros + `NccOrder`)
- A ferramenta menor processa apenas o material que permaneceu após a maior
  quando Rest Machining está ativo. ✅ (`NccGenerator`, testado)
- Resultado por ferramenta é identificável e chega corretamente ao G-code. ✅
  (`NccResult.toolResults()` -> `List<ToolGeometry>` -> G-code com troca de
  ferramenta)
- Parâmetros impossíveis falham com mensagem útil antes de iniciar o job. ✅
  (diâmetro duplicado/não-positivo, lista vazia)
- Cancelamento não publica resultado parcial como se fosse concluído. ✅
  (reutiliza `CancellationToken` já existente)
- Progresso não regride e termina em 100% no sucesso. ✅ (testado)
- Testes do reactor passam e o app inicia com os módulos recém-instalados. ✅
  (112 testes, `MainApp started`)

Decisão registrada: várias ferramentas produzem troca de ferramenta **num
único CNC Job** (G-code concatenado com M0 opcional entre seções), não jobs
separados - confirmado que é assim que `mtool_gen_cncjob` do Python funciona
(ver seção 5).

### Nota de divergência (opinião do Claude, 2026-09-22)

A recomendação acima (NCC multi-tool com Rest Machining) foi escrita por uma
sessão anterior (Codex). Uma sessão Claude, ao revisar o projeto neste mesmo
ponto, discorda da ordem e recomenda **resolver primeiro a persistência de
projeto** (seção 7, "Persistência ainda não é um modelo editável") antes de
abrir o incremento NCC multi-tool. Motivo:

- NCC multi-tool é trabalho horizontal: adiciona um novo tipo de resultado
  (segmentado por ferramenta, com ordem e possivelmente Rest Machining) que
  também não seria persistido pelo `ProjectFile` atual, que hoje só guarda
  caminhos de Gerber/Excellon e referências de CNC Job.
- Cada fatia nova que roda em cima desse formato (Isolation, Cutout, NCC,
  Geometry, CNC Job) é mais um caso que a futura reforma de persistência
  (já prevista na seção 9.3) vai ter que migrar depois. Resolver isso agora,
  com a superfície de objetos ainda pequena, custa menos do que esperar
  crescer mais.
- A seção 9.3 já lista o modelo de projeto versionado como pré-requisito do
  Gerber Editor; adiantar uma fatia mínima dele agora evita que o NCC
  multi-tool vire mais uma dependência a desembaraçar nessa reforma.

Isso não invalida o plano do Codex nem a ordem descrita nas seções 8 e 9 -
é uma divergência de julgamento sobre sequenciamento, registrada para que a
próxima sessão (humana ou IA) escolha com o argumento explícito, em vez de
herdar uma prioridade sem saber que houve debate sobre ela. Se a próxima
sessão seguir o plano original do Codex, não é necessário reverter esta nota;
só marque aqui qual caminho foi escolhido e por quê.

Escopo mínimo proposto pelo Claude para essa fatia de persistência, caso seja
adotada antes do NCC multi-tool:

1. Dar versão ao `.fcnproj` (campo `version`), pensando em migração futura.
2. Persistir parâmetros CAM por objeto (Isolation/Cutout/NCC), não só o
   G-code de saída - o suficiente para reabrir e reexecutar/reeditar sem
   reconfigurar do zero.
3. Persistir a associação Geometry -> ferramentas/parâmetros que a geraram,
   e CNC Job -> parâmetros que o geraram.
4. Manter compatibilidade de leitura com o `.fcnproj` atual (arquivo sem
   `version` é tratado como v1).
5. Testes: salvar -> reabrir -> objetos e parâmetros idênticos; e leitura de
   um `.fcnproj` "antigo" (sem os campos novos) continua funcionando.

Isto não é o modelo de objeto versionado completo da seção 9.3 (identidade,
metadados e origem separados) - é o mínimo para parar de perder estado a
cada ferramenta nova, sem bloquear o NCC depois.

## 9. Roadmap depois do próximo incremento

Esta é a sequência recomendada, sujeita a revisão com evidência do legado:

### 9.0 Fila atual (2026-10-02)

Sequência Paint Shape → Transformations Object/Buffer → exclusões Geometry/CNC
→ comparação CAM real entregue; consulte as quatro etapas ao final e
COMPARACAO_CAM.md. Próximas prioridades: divergências diferenciais restantes,
corpus Rest/referências/múltiplas ferramentas, exclusões Drilling e validação
manual dos painéis/segurança física. Não declarar paridade total com base nos
testes headless ou na simples existência das 24 ferramentas.

Em ordem aproximada de valor para o usuário; qualquer ordem é aceitável desde que alinhada ao Python:

1. **Ferramentas do menu:** todas portadas; resta validar manualmente os painéis novos. As conversões (Convert Any e Single↔MultiGeo) já foram feitas.
2. **Tools Database/projetos**: integração Milling/Paint/Cutout e exportação `.FlatPrj` entregues;
   completar opções avançadas, ampliar round-trip legado e validar os
   **pós-processadores** (hoje 20 ports parciais de 20 perfis Python, incluindo `Paste_1`;
   FX portable não entra nessa contagem). Perfil e campos globais CNC já persistem após gerar;
   priorizar transferência dos parâmetros da Tools Database, suplemento de metadados Roland, compensações de mesa
   e parâmetros individuais/laser. Rascunhos de formulários ainda não têm snapshot completo.
3. **Validação manual** dos painéis novos e, se houver divergência, correção guiada por captura (harness fora
   da tela, sem capturar a tela inteira).
4. **Plot Area:** margem em volta da vista para a imagem de densidade e, se um profile mostrar necessidade,
   rasterização incremental.
5. **Arquitetura/desempenho (decisão em aberto, não iniciada):** a avaliação de um núcleo de geometria nativo
   (Clipper2 via FFM, atrás de uma interface `GeometryEngine`, com o JTS como reserva), de viewport na GPU e das
   alternativas C++/Rust está registrada na memória do projeto (`architecture-options-native-gpu`) e, para continuidade
   independente dessa memória, em [ARQUITETURA_RENDERIZACAO_FUTURA.md](ARQUITETURA_RENDERIZACAO_FUTURA.md), avaliação de
   2026-10-02. A direção candidata é JavaFX + core Java/JTS + backend OpenGL experimental, preservando Canvas;
   Vulkan e motor geométrico nativo são avaliações posteriores e independentes. O primeiro passo recomendado é
   baseline reproduzível e protótipo de apresentação GPU **integrada** ao JavaFX, incluindo custo de transporte,
   GPUs híbridas, precisão CAD e fallback. Não há adoção definitiva nem prazo de ganho garantido.

### 9.1 Completar a paridade NCC (o que resta)

- validação manual dos parâmetros por ferramenta e contornos ISO;
- opções avançadas da Tools Database (integração básica concluída);
- fixtures diferenciais e casos de desempenho (incl. Rest Machining e
  boundary por referência num board real).

Concluído: boundary por objeto de referência, seleção retangular/poligonal de área,
preservação de Order = None, seleção de ferramentas, ISO/CLEAR e
validação/sugestão de diâmetro ("Check validity")
- ver seção 5.

**Fixture diferencial do NCC com um projeto real (2026-09-30).** O projeto
`Dados_Ambientais_C6_V2` (`.FlatPrj` do Python) traz o `Cobre_MortoFino_Top_cnc`, gerado
pelo NCC do Python a partir de `F_Cu` com a área da placa (`Edge_Cuts.gm1_area`) como
limite. Comparando com o NCC do FX (cortador 0,1829 mm, Standard, connect, limite =
`NccBoundary.Area` da placa, margem 0): área limpa idêntica (IoU 0,9987; a sobra de
1,1 mm² é o arredondamento das pontas) e comprimento de percurso 8955 mm contra 8962 mm
do Python com sobreposição 0,54 (o espaçamento medido no G-code do Python dá ~0,53).
Com o limite "Itself" (casco convexo do cobre) o FX limpa 310 mm² a mais, na reentrância
da borda superior: era só uma diferença de parâmetro, não de algoritmo. O método Seed
gera ~10x mais caminhos e é bem mais lento (15-30 s). `NccPythonParityTest` repete a
comparação e só roda com `FLATCAM_PARITY_PROJECT` apontando para o `.FlatPrj`, porque o
projeto é privado. Ficam sem oráculo: Rest Machining, Lines e Combo.

**Conversion e Join Objects do Python (2026-09-30), em andamento.** Feitos: Editar >
Converter > "Contorno → Area" (`OutlineToArea`, o `convert_outline2area` do Python: fecha o
contorno com `Polygonizer` e usa a maior região; com o `Edge_Cuts` do projeto real o
resultado é idêntico ao `gm1_area` do Python, diferença simétrica 0) e Editar > Juntar
Objetos > "Gerber(s) → Gerber" (`GerberJoin`, o `GerberObject.merge`: junta aberturas,
formas, sólido e follow; código de abertura em conflito recebe o próximo livre,
aberturas idênticas são compartilhadas; Gerbers com unidades diferentes são recusados; se
um Gerber posterior tem formas clear, o resultado fica sem formas editáveis para não
apagar o cobre anterior no Gerber Editor). Novo objeto "Combo_Gerber". Também feitos:
Join Excellon(s) → Excellon (`ExcellonJoin`: ferramentas são identificadas pelo diâmetro, e com
`fuse_tools`, o padrão do Python, as de mesmo diâmetro a 4 casas viram uma; furos e slots são
renumerados; `Combo_Excellon`) e Join Geo/Gerber/Exc → Geo (`GeometryJoin`: objetos simples
viram um Geometry só; Geometrys multi-ferramenta fundem as ferramentas de mesmo diâmetro e
perfil; misturar simples com multi-ferramenta é recusado como no Python; `Combo_SingleGeo`/
`Combo_MultiGeo`). Com o projeto real, os 3 Excellons somam 80 furos em 4 ferramentas (as duas
de 0,8 mm do PTH viram uma). Convert Any to Geo/Gerber/Excellon e Single↔MultiGeo foram feitos depois (entrada abaixo).

**Barra superior com paridade ao Python (2026-09-30).** As barras File, Edit, View e Shell do
Python (mesmos botões, mesma ordem) e a barra Tools podem ser ligadas e desligadas em Exibir >
Barras de ferramentas, com o estado lembrado como o `global_toolbar_view` do Python (bits 1, 2,
4, 8 e 256); por escolha do usuário o padrão mostra só File e Edit (o padrão de fábrica do
Python mostra todas). Saíram da barra os botões que o Python não tem: Abrir G-Code (continua em
Arquivo), o job de demonstração (agora em Ajuda) e o botão Cancelar, que passou para a barra de
status e só aparece enquanto uma operação está em andamento. "Salvar e Fechar Editor" da barra
agora está ligado.

**2-Sided Tool (2026-09-30; "Centro da placa" adicionado depois e é a referência inicial).** Ferramentas > 2-Sided Tool (`DoubleSidedToolPanel`), o
`ToolDblSided.py` do Python com os mesmos padrões (eixo X, referência Ponto, furo de 3,125).
Além do Python: espelha vários objetos de uma vez; a linha de espelhamento pode passar pelo
centro ou por uma borda da caixa de um objeto, dos próprios objetos marcados, ou por um ponto
digitado, pego no plot (clique; Esc cancela) ou a origem; modo "criar cópia espelhada" além do
"no lugar"; pré-visualização no plot da linha, do contorno da placa já espelhado (o objeto Edge_Cuts/
Outline/Profile com as próprias linhas, ou a caixa de todos os objetos; desligável), dos
contornos espelhados dos objetos marcados e dos furos de alinhamento. O contorno original
(placa e objetos marcados) aparece bem fraco e tracejado como referência
(`PlotAreaView.setEditorReference`), o interior da placa pode ser preenchido de forma
translúcida (`setEditorFills`) e o conteúdo dos objetos marcados (cobre, furos, caminhos) aparece
já espelhado, preenchido em ciano com as trilhas visíveis (`setEditorContent`), para ver como
a outra face vai ficar. Furos de alinhamento: lista de "X, Y" (um por linha ou "(X, Y), (X, Y)") ou pegos
no plot; cria o Excellon "Alignment Drills" com cada furo e o seu espelho, pelo mesmo eixo e
linha. Eixo X inverte Y (linha horizontal) e eixo Y inverte X, como no Python.
"Pegar no plot" e "Adicionar furo no plot" prendem o clique ao centro exato do furo (ou ponta de
slot) de um Excellon num raio de 14 px (caixa "Prender ao centro de furos existentes", ligada por
padrão), o equivalente ao "Pick hole" do Python, e vale também para os furos de alinhamento.

**Paint Tool (2026-09-30).** Ferramentas > Paint Tool (`PaintToolPanel`, `NccGenerator.paint`,
`PaintParameters`): preenche polígonos de um Gerber ou Geometry com caminhos, reaproveitando as
estratégias do NCC (Standard, Seed, Lines, Combo), com sobreposição, margem (encolhe cada
polígono), conectar, contorno, ordem das ferramentas e rest machining, tudo com os padrões do
Python (0,3 mm, 20%, ordem reversa). Seleção dos polígonos: todos, um por clique (Esc
termina), área por retângulo ou polígono desenhados, ou os que tocam um objeto de
referência; a seleção aparece destacada no plot. Gera um Geometry `<nome>_paint` multi-
ferramenta. O Laser Lines do Python não é oferecido (depende das aberturas do Gerber). Sem
oráculo do Python para comparar caminhos; verificado por propriedades (cobertura ~100% da placa
do projeto real em Standard, Seed e Lines, em menos de 1 s) e testes.

**Panelize Tool (2026-09-30).** Ferramentas > Panelize Tool (`PanelizeToolPanel`, `Panelize`):
Registro histórico: prévia/conjunto e preservação de IDs/índices foram ampliados
em 2026-10-07; consultar `PANELIZE.md`. A fusão Excellon descrita abaixo não é
mais usada na panelização atual (Join Objects continua independente).
repete um Gerber, Excellon ou Geometry em colunas x linhas, as cópias afastadas pela caixa de
referência (a do próprio objeto ou a de outro, como o contorno) mais o espaçamento; "limitar o
tamanho" reduz a grade até caber, como o Python; um Gerber também pode virar painel Geometry. O painel
é feito transladando cada cópia e juntando com as regras do Juntar Objetos (Gerber: aberturas
compartilhadas e formas de todas as cópias; Excellon: ferramentas de mesmo diâmetro fundidas;
Geometry: caminhos por ferramenta). O plot mostra o contorno de cada cópia e o tamanho do painel
antes de criar; o resultado é `<nome>_panelized`. Padrão inicial 2 x 2 (o Python começa em 1 x 1).
Com o projeto real, o F_Cu em 6 x 6 leva ~80 ms e a área é exatamente 36 vezes a original. Corrigido
de passagem: a união por grupos descartava polígonos dentro de coleções aninhadas.

**Trocar de ferramenta no meio do uso (2026-09-30).** As ferramentas que desenham no plot (2-Sided,
Paint, Panelize) registram uma limpeza (`activeToolCleanup`) que roda quando outra ferramenta
assume a aba ou o painel fecha: cancela o clique armado e a seleção de área e apaga previews,
preenchimentos, conteúdo e referência. Antes, o que a ferramenta anterior deixava no plot ficava até
o painel seguinte fechar. A limpeza não toca o destaque dos editores.

**Invert Gerber Tool (2026-09-30).** Ferramentas > Invert Gerber Tool (`InvertGerber`,
`InvertGerberToolPanel`), o `ToolInvertGerber.py`: a caixa do Gerber, acrescida da margem (padrão 0,1;
cantos Redondo, Chanfrado ou Quadrado = mitre, padrão do Python), perde todo o cobre e o que sobra vira
um novo Gerber `<nome>_inverted` feito de regiões (abertura "0"). Com o F_Cu do projeto real a área
confere (caixa - cobre = 1122,64 mm²) e leva ~150 ms.

**Subtract Tool (2026-09-30).** Ferramentas > Subtract Tool (`Subtract`, `SubtractToolPanel`), o
`ToolSub.py`: para dois Gerbers ou dois Geometrys cria `<alvo>_sub`. Gerber: as formas do alvo que
tocam o subtraendo são cortadas e arquivadas na abertura de regiões "0" (como o Python), as demais
mantêm a abertura, e o cobre é refeito na ordem das formas. Geometry: "Fechar caminhos" (padrão
ligado) corta o alvo como uma forma fechada; desligado, cada polígono vira seus anéis e cada linha
é cortada separadamente; subtraendo multi-ferramenta é recusado, como no Python; alvo
multi-ferramenta mantém as ferramentas. "Excluir os objetos de origem" remove os dois depois.
Diferença deliberada: o Python une as diferenças do alvo contra cada forma do subtraendo, o que
deixa cobre coberto por só uma de várias formas sobrepostas; aqui o alvo perde a união de todas.
Com o projeto real, F_Cu menos B_Cu deixa 404,2 mm² dos 3092,3 mm² em 1,7 s.

**Extract Drills Tool (2026-09-30).** Ferramentas > Extract Drills Tool (`ExtractDrills`,
`ExtractDrillsToolPanel`), o `ToolExtractDrills.py`: cada flash (follow = ponto) dos tipos de pad marcados
(circular, oblongo, quadrado, retangular, outros) vira um furo no centro; diametro fixo (0.5), proporcional
(80% do menor lado) ou anel anular (menor lado - 2 x anel, 0.2). Furos de mesmo diametro (4 casas) dividem a
ferramenta; nada extraido = aviso. Gera um Excellon `<nome>_drills`. No projeto real: 31 pads redondos em
F_Cu e B_Cu (modo anel). Testes em `ExtractDrillsTest`.

**Punch Gerber Tool (2026-09-30).** Ferramentas > Punch Gerber Tool (`Punch`, `PunchGerberToolPanel`), o
`ToolPunchGerber.py`: fura os pads dos apertures escolhidos (lista multipla, todos por padrao). Origem dos
furos: Excellon (brocas que caem dentro de um flash dos apertures escolhidos, com o diametro da ferramenta)
ou o mesmo dimensionamento do Extract Drills (fixo 0.5, anel 0.2, proporcional 80%; `ExtractDrills.holes`
foi extraido para ser compartilhado). No modo fixo, furo >= pad falha como no Python. Cada furo entra como
shape "clear" (circulo) no Gerber `<nome>_punched`, entao o cobre mantem as formas individuais.
Testes em `PunchTest`.

**Etch Compensation Tool (2026-09-30).** Ferramentas > Etch Compensation Tool (`EtchCompensation`,
`EtchCompensationToolPanel`), o `ToolEtchCompensation.py`: cria `<nome>_comp` com o cobre crescido (ou
encolhido, offset negativo) pelo deslocamento = espessura do cobre (padrao 18 um) / (1/fator) pelo fator de
corrosao, por corrosivo (CuCl2 0.33, Fe3Cl e alcalinos 0.25) ou manual (um). Inclui os conversores oz->um
(x34.798) e mils->um (x25.4). Diferencas deliberadas: o Python trata todo Gerber como mm; aqui os microns sao
convertidos para as unidades do Gerber, e os shapes "clear" (furos) encolhem o mesmo deslocamento para ficarem
coerentes com o cobre crescido. Os tamanhos dos apertures ficam como estavam. Testes em `EtchCompensationTest`.

**Film Tool (2026-09-30).** Ferramentas > Film Tool (`FilmExporter`, `FilmToolPanel`), o `ToolFilm.py`: filme
imprimivel de um Gerber ou Geometry em SVG, PNG ou PDF, enquadrado pela caixa de outro objeto (Gerber, Geometry
ou Excellon). Negativo (folha preta, feicoes brancas, borda padrao 1.0) ou positivo (cor, margem 1 mm);
escala X/Y (a partir do canto inferior esquerdo da caixa), inclinacao em graus com referencia (4 cantos ou
centro), espelho X/Y/ambos (em torno do centro da caixa), espessura do traco ("Scale Stroke": contorno de 2x o
fator, padrao 0.01), e furar o positivo por Excellon ou centro dos pads (reusa `Punch`). PDF: "Bounds" ou
A0-A6/Letter/Legal/Tabloid, retrato/paisagem; PNG: DPI escolhido. Diferencas deliberadas: escala/inclinacao/
espelho agem na geometria das feicoes (no Python sao repassados ao export_svg do objeto), o PNG e renderizado
no DPI pedido (Java2D) em vez do ajuste estranho do Python, e o PDF e escrito direto em vetores (sem
svglib/reportlab). No projeto real: F_Cu negativo com moldura do Edge_Cuts gera SVG 58 ms, PNG 275 ms, PDF
151 ms. Testes em `FilmExporterTest`.

**Fiducials Tool e Corner Markers Tool (2026-09-30).** Ferramentas > Fiducials Tool (`Fiducials`,
`FiducialsToolPanel`), o `ToolFiducials.py`: marcas de alinhamento num Gerber de cobre, gerando `<nome>_fid`.
Tipos: circular, cruz (duas linhas com espessura) e xadrez (dois quadrados). Posicoes: automatico (cantos da
caixa do objeto + margem; terceiro ponto acima/abaixo/nenhum) ou manual (cliques no plot: inferior esquerdo,
superior direito e o segundo). "Aberturas na mascara" cria os mesmos pontos com o dobro do tamanho num Gerber
de mascara de solda. Reaproveita um aperture de mesmo tipo/tamanho, senao cria o proximo codigo livre.
Ferramentas > Corner Markers Tool (`CornerMarkers`, `CornerMarkersToolPanel`), o `ToolCorners.py`: marcadores
em L ("safe") ou cruz nos 4 cantos da caixa (fora por margem + meia espessura; padroes 0.1 / 3.0 / 0.0),
gerando `<nome>_corners`, e "Criar furos nos cantos" gera um Excellon `<nome>_corner_drills` (padrao 0.5) nos
mesmos pontos. Testes em `MarkersTest`.

**Migracao para Java 25 + JavaFX 25.0.4 (2026-10-01; branch `java-25` já mesclado no `flatcam-next`).** `maven.compiler.release` 25 e `javafx.version` 25.0.4; build e todos os testes passam, e os temas renderizam igual no JavaFX 25. Comparacao Java 21 x 25 no F_Cu real (isolacao 3 passes): primeira execucao 312 ms -> 93 ms e execucao aquecida 55 ms -> 26 ms; painel 3x3: 460-600 ms -> 164-206 ms frio; carga do projeto sem diferenca (~60 ms). O `JAVA_HOME` precisa apontar para um JDK 25 para compilar e rodar.

**QRCode Tool (2026-10-01).** Ferramentas > QRCode Tool (`QrCodeMarker`, `QrCodeToolPanel`), o `ToolQRCode.py`:
QR Code de quadrados de cobre num Gerber, gerando `<nome>_qrcode`. A matriz vem do ZXing (`com.google.zxing:core`
3.5.4, Apache 2.0, nova dependencia de runtime do `flatcam-cam`). Padroes do Python: versao 1 (cresce sozinha ate 40
se o texto nao couber), correcao L (L/M/Q/H), caixa 3 (modulo = caixa/10 unidades), borda 4 modulos, polaridade
positiva (negativa = mascara menos os modulos) e mascara quadrada (ou arredondada). Clique no plot define o destino.
Diferencas deliberadas: o QR fica centrado no ponto clicado (o Python ancora um canto) e a mascara limpa todo cobre
sob ela, nao so os poligonos que a contem por inteiro. Nao ha ainda exportar o QR como SVG/PNG nem a cor de preenchimento
do Python (so afetam a exportacao). Teste de ida e volta: a geometria colocada e rasterizada e decodificada de volta ao
texto. `build-native.cmd` agora apaga `flatcam-fx\target\dependency` antes de copiar, senao jars de um JavaFX antigo
ficavam junto dos novos no launcher nativo. Testes em `QrCodeMarkerTest`.

**SolderPaste Tool (2026-10-01).** Ferramentas > SolderPaste Tool (`SolderPaste`, `SolderPasteToolPanel`), o
`ToolSolderPaste.py` + `generate_gcode_from_solderpaste_geo` + pre-processador `Paste_1`. Passo 1: tabela de bicos
(padrao 1.0 e 0.3) e um Gerber de mascara de pasta geram a Geometry `<nome>_solderpaste` (multi-ferramenta): cada pad
recebe uma linha, do maior bico que cabe nele (linha pelo meio do lado maior se as diagonais empatam, senao pela
diagonal mais longa, encolhida por meia largura do bico; pads sem bico que caiba sao contados e avisados). Passo 2:
a Geometry de pasta vira um CNC Job (diálogo de salvar; `<nome>_cnc_solderpaste.nc`): cabecalho, troca de bico
`T n / M6 / M0` e, por caminho (vizinho mais proximo a partir de 0,0): G00 ate o ponto, Z de deslocamento, Z de inicio,
M03 + espera, Z de dispensa, avanco XY, M05, M04, Z de parada, M05, espera, Z de deslocamento — igual ao Paste_1.
Parametros com os padroes do Python (Z 0.05/0.1/0.05, deslocamento 0.1, troca 1.0 em 0,0, avancos 150/150/1.0, 300 rpm
1 s frente e 200 rpm 1 s reversa). Diferencas deliberadas: um pad pequeno demais nao herda a linha do pad anterior (bug do
Python: a variavel `geo` vaza entre iteracoes); os parametros valem para todos os bicos (o Python guarda um conjunto por
bico); velocidade e espera sao escritas quando maiores que zero; o programa termina so subindo para a altura de troca
(sem X,Y final). O preview do CNC Job e montado da geometria (o parser de G-code trata Z positivo como deslocamento, e
a dispensa usa Z positivo), por isso o job de pasta nao tem a tabela de passos. Com o F_Cu do projeto como mascara de
teste: 44 pads, 2 bicos, 53 caminhos em ~25 ms. Testes em `SolderPasteTest`.

**LOD por densidade no Plot Area (2026-10-01).** Camada 1 da proposta de desempenho para Geometry muito densa:
`DensityRaster` + integracao no `PlotAreaView` (ver `PLOT_PERFORMANCE.md`, secao "LOD por densidade"). Medido com
geometria sintetica em pan: 100 mil tracos 745 ms -> ~200 ms; 500 mil 4,4 s -> ~1,1 s (cobertura de area com antialiasing, para
ficar igual ao vetor; 4 defeitos de fidelidade corrigidos, ver PLOT_PERFORMANCE.md). Testes em `DensityRasterTest`.
Camada 2 feita (cache de interacao + rasterizacao fora da thread JavaFX, `DenseRenderer`): quadro de pan 1-3 ms, imagem exata
chega ~145 ms (20 mil), ~350 ms (100 mil), ~1,1 s (500 mil) apos o pan parar. Pendente: cobrir uma margem em volta da vista.

**Align Objects Tool (2026-10-01).** Ferramentas > Align Objects Tool (`AlignObjects`, `AlignObjectsToolPanel`), o
`ToolAlignObjects.py`: alinha um Gerber ou Excellon (objeto a alinhar) a outro (referencia) clicando em pads ou furos.
Um ponto (padrao do Python): clique num pad/furo do objeto a alinhar e no correspondente da referencia, e o objeto e
transladado. Dois pontos: repete o par e, alem da translacao, gira em torno do primeiro destino para que o segundo
ponto caia no segundo destino. O clique e resolvido para o centro do pad (flash de Gerber que contem o ponto) ou do
furo/ranhura (raio da broca + 6 px). Aplica `TransformOp.Offset` e `TransformOp.Rotate` pelo mesmo caminho do Transform
Tool. Diferenca deliberada: o angulo vem de `atan2` (o `atan(dy/dx)` do Python falha com dx = 0 e erra de quadrante em
giros acima de 90 graus), e a rotacao so e pulada quando o angulo e ~0 (a regra do Python tambem pulava giros
legitimos). Nao ha o realce em cor azul do objeto durante os cliques (o status do painel diz qual clique e o
proximo). Testes em `AlignObjectsTest`.

**Convert Any→Geo/Gerber/Excellon e Single↔MultiGeo (2026-10-01).** Editar > Converter (`ObjectConversion` em
`flatcam-cam/.../convert/`, ligações no `MainWindow`), do `app_Main.py`. *Objeto → Geometry*: Gerber ou Excellon
viram um Geometry de polígonos preenchidos (`<nome>_conv`); um Geometry é copiado com suas ferramentas. *Objeto →
Gerber*: Excellon vira um Gerber com uma abertura redonda por ferramenta (códigos a partir de 10), furos como
flashes e slots como traços; Geometry vira regiões (polígonos) e traços (linhas) com a largura da ferramenta a que
pertencem. *Objeto → Excellon*: cada forma fechada de um Geometry, ou cada flash de um Gerber, vira um furo no
centro com o menor lado da caixa como diâmetro; um traço Gerber de exatamente 2 pontos vira slot com a largura da
abertura (como no Python, isso inclui trilhas retas de 2 pontos); ferramentas de mesmo diâmetro a 4 casas
compartilham o id. *Single → Multi*: pergunta o diâmetro e põe a geometria sob uma ferramenta; *Multi → Single*:
une a geometria de todas as ferramentas e descarta a informação de ferramenta, como o Python. As duas últimas
alteram o objeto no lugar. Diferenças deliberadas: um Geometry só de linhas vira cobre por traço da largura da
ferramenta (o Python grava as linhas nuas como "sólido", sem área), as comparações de diâmetro são sempre a 4
casas (o Python mistura exata e arredondada) e o Single → Multi pede o diâmetro porque um Geometry de uma
ferramenta no FX não o guarda. Com o projeto real: Excellon→Gerber preserva a área exatamente e o F_Cu → Excellon
dá 72 furos, os mesmos do Excellon PTH; as isolações viram 99 e 111 formas (~0,3 s). Testes em
`ObjectConversionTest`.

**Tooltips animados e descrições das ferramentas (2026-10-01).** `FluidTooltips` substitui o `Tooltip` nativo do
JavaFX em toda a janela: atraso de 0,45 s (60 ms se outro tooltip fechou há menos de 0,7 s), entrada com fade e
deslize de 150 ms, saída com fade de 90 ms e, com um tooltip aberto, **deslize de 170 ms até o controle vizinho** em
vez de fechar e reabrir (com cruzamento de opacidade). Posição abaixo do controle (acima se faltar espaço; ao lado
nos itens de menu), dentro do monitor, com a paleta de cada tema (`ThemeOption.tooltipPalette`: cinza neutro #3d3d3d no Preto, azul-acinzentado no Preto
gelo, branco com borda cinza ou azulada nos claros), com título opcional em negrito. Ele **adota os tooltips nativos que já existem**: ao entrar num controle com `Tooltip`, o texto passa para o
gerenciador e o nativo é removido, então nenhum painel precisou mudar; células de tabela e árvore mantêm o nativo (se
atualizam a cada linha). Itens de menu (janelas à parte, que o filtro da cena não vê) ganham os handlers quando o
menu abre (`attachMenu`). `ToolDescriptions` traz título e texto (baseados nos tooltips do Python) das 24 ferramentas
do menu Ferramentas, usados nos itens do menu e nos botões da barra; as quatro ainda não portadas dizem isso. Os
itens de Converter e Juntar Objetos também têm descrição. O popup fecha junto com a janela e antes do "Sair" (um
tooltip aberto num `Platform.exit()` direto derrubava o toolkit nativo). Limitações: controles desabilitados não
recebem eventos do mouse, então os botões das ferramentas ainda não portadas não mostram o tooltip na barra; a
navegação dentro dos menus por tooltip só foi validada por simulação (harness fora da tela), não com o mouse.
Testes: `ToolDescriptionsTest` (toda ferramenta do menu tem descrição).

**Optimal Tool (2026-10-01).** Ferramentas > Optimal Tool (`MinimumDistance` em `flatcam-cam/.../analysis/`,
`OptimalToolPanel`), o `ToolOptimal.py`: une o cobre do Gerber em peças separadas, mede a distância entre todo par
de peças (arredondada à precisão, padrão 4 casas) e mostra a menor distância, quantos pares estão nela, onde (os dois
pontos mais próximos) e as demais distâncias em ordem crescente, cada uma com seus pares. Só uma peça: mensagem como
a do Python. Diferenças deliberadas: os pares são medidos em paralelo com `IndexedFacetDistance` (o Python mede um a
um), roda como job cancelável com progresso, e escolher um local na lista já leva o plot até o meio do vão e o marca com
um anel e o segmento entre os dois pontos (o Python exige clicar em "Locate"). Com o projeto real: F_Cu tem 44 peças e
o vão mínimo é 0,3505 mm em 16 pares (B_Cu: 47 peças, 0,3505 mm em 18), em ~70 ms. Mais memória cresce com o quadrado
do número de peças (todas as distâncias são guardadas, como no Python). Testes em `MinimumDistanceTest`.

**Rules Check Tool (2026-10-01).** Ferramentas > Rules Check Tool (`RulesCheck` em `flatcam-cam/.../analysis/`,
`RulesCheckToolPanel`), o `ToolRulesCheck.py`: dez regras (tamanho da trilha, cobre-cobre, cobre-contorno, seda-seda,
seda-máscara, seda-contorno, lasca da máscara, anel anular, furo-furo, tamanho do furo) com os limites padrão do Python
(0,25 / 0,25 / 1,0 / 0,25 / 0,25 / 1,0 / 0,25 / 0,3 / 0,3 / 0,3). Cada regra vira um resultado OK/FALHOU/NÃO EXECUTADA
(faltou escolher objeto, valor inválido); regras com violação listam os pontos e clicar num ponto leva o plot até ele
(todos ficam marcados com anéis). Diferenças deliberadas em relação ao Python: os vãos são buscados com `STRtree` +
`IndexedFacetDistance` em paralelo (o Python compara todos os pares); a regra da lasca da máscara tem o próprio
checkbox (no Python ficava atrelada ao de seda-seda); seda-contorno usa o próprio valor (o Python lia o de
cobre-contorno); o tamanho do furo é verificado por Excellon; uma peça única de cobre passa com uma nota (o Python a
relatava como FALHA); vãos de até 2e-6 mm contam como encostados. Roda como job cancelável. Com o projeto real
(F_Cu, B_Cu, Edge_Cuts, PTH, NPTH): cobre-cobre OK (vão mínimo 0,35), cobre-contorno falha em 4 pontos com o limite 1,0,
tudo em ~340 ms. Testes em `RulesCheckTest`.

**Copper Thieving Tool (2026-10-01).** Ferramentas > Copper Thieving Tool (`CopperThieving` em
`flatcam-cam/.../convert/`, `CopperThievingToolPanel`), o `ToolCopperThieving.py`: (1) preenche o cobre vazio
(diferença entre a caixa e o cobre afastado pela distância) como sólido, pontos, quadrados ou grade de linhas, com
referência ao próprio objeto (caixa retangular ou casco convexo), a áreas desenhadas (dois cliques por retângulo) ou a
outro Gerber/Geometry; gera o objeto `_thief`; (2) robber bar: anel de espessura dada ao redor da caixa do cobre
(`_robber`); (3) máscara de galvanoplastia a partir da máscara de solda, com o thieving e/ou a robber bar da última
execução, distância (negativa encolhe as aberturas) e área galvanizada (`_plating_mask`). Padrões do Python (distância
0,25, margem 1,0, área mínima 0,1, 64 passos de círculo, pontos/quadrados 1,0 e espaço 2,0, linhas 0,25 e 2,0, robber
1,0/1,0). Diferenças deliberadas: a distância é um único buffer do cobre inteiro (mesmo resultado que bufferizar e unir
cada polígono, em um terço do tempo); pontos e quadrados só são construídos onde a envoltória de uma área livre os
comporta e testados com geometria preparada; a grade de linhas é cortada pelo cobre numa só operação (o Python corta
linha por linha e deixa peças sobrepostas, que a união final do Gerber elimina: a área unida bate com a do Python em
0,004%). Comparação com o Python (shapely) no F_Cu do projeto real, mesmos parâmetros: sólido 21 ms contra 34 ms,
pontos 42 ms contra 79 ms, quadrados 25 ms contra 75 ms, linhas 75-120 ms contra 207 ms (FX depois de aquecido; 1ª
execução com JVM fria é 2 a 4 vezes maior); mesmas contagens de polígonos e áreas (sólido, pontos, quadrados).
Testes em `CopperThievingTest`.

**Calibration Tool (2026-10-01).** Ferramentas > Calibration Tool (`Calibration` em `flatcam-cam/.../transform/`,
`CalibrationToolPanel`), o `ToolCalibration.py`, o que fecha as 24 ferramentas do menu: (1) quatro pontos (inferior
esquerdo = origem, inferior direito, superior esquerdo, superior direito) clicados sobre furos ou pads flashados de um
objeto (o ponto vira o centro do furo/pad) ou livres, arredondados a 4 casas; (2) G-code de verificação que visita a
origem, o ponto de alinhamento (superior esquerdo ou inferior direito, à escolha), o de checagem e o de verificação, com
Z de deslocamento 2,0, Z de verificação 0,1, Z de troca 15 e etapa opcional de zerar Z; (3) fatores a partir dos deltas
medidos nos pontos 2 e 3 (escala X = dx/(x2-x1)+1, escala Y = dy/(y3-y1)+1, inclinação X = atan(dx3/(y3-y1)), inclinação Y
= atan(dy2/(x2-x1))), aplicáveis aos próprios pontos (escala e inclinação pela origem); (4) objeto calibrado
(`_calibrated`): escala e depois inclinação pela origem em Gerber, Excellon ou Geometry. Diferenças deliberadas: o Python
compara o delta com a coordenada do ponto (campo vazio = sem desvio) e soma a Y da origem ao delta da inclinação Y; aqui
um delta vazio ou zero não corrige nada e a inclinação Y usa só o delta; ponto fora de furo/pad no modo "objeto" é recusado
e o clique repetido (o Python ignora em silêncio); o G-code é salvo num arquivo (o Python abre um editor de código).
Comparação com o Python (shapely) no F_Cu real, escala 1,002/0,998 e inclinação 0,1/0,05: ~2 ms nos dois (FX: 2 ms depois
de aquecido, 33 ms na 1ª execução; a área resultante é igual, 3092,1585). A ferramenta é só aritmética de poucos pontos,
então não há ganho a mostrar. Testes em `CalibrationTest`.

### 9.2 Transformations - concluído nesta revisão (ver seção 4)

Rotate/Skew/Scale/Flip/Offset reutilizáveis para Gerber, Excellon e Geometry,
com motor no núcleo (`org.flatcam.cam.transform`), independente de JavaFX.
Falta apenas Buffer (distância/fator) e a referência "Object" - ambos
pequenos, adicionáveis quando houver demanda real.

### 9.3 Modelo de objetos e projeto versionado

**Fase 1 concluída nesta revisão (2026-09-22): Gerber + Excellon com
estrutura de dados inspirada no `.FlatPrj` do Python, mas sem suporte completo
de importação/exportação desse formato.** O usuário pediu
explicitamente para usar o modelo real do Python em vez de inventar um
formato próprio mais leve - decisão registrada e pesquisada a fundo em
`camlib.py`/`app_Main.py` antes de implementar.

**O que o Python realmente faz** (confirmado lendo o código-fonte, não
suposto):
- `save_project()`/`open_project()` (`app_Main.py` ~10605-10809) escrevem
  `{"objs": [obj.to_dict() para cada objeto], "options": {...}, "version":
  ...}` como JSON (`json.dumps(..., indent=2, sort_keys=True)`), opcionalmente
  comprimido em XZ de verdade (`lzma.open(..., preset=3)` - default
  `global_save_compressed=True`). O carregamento tenta JSON puro primeiro e
  cai para XZ se falhar - sem cabeçalho/flag, é auto-detecção por tentativa.
- Cada geometria Shapely vira `{"__class__":"Shply","__inst__":"<WKT>"}` via
  `shapely.wkt.dumps`/`loads` (`camlib.py:8144-8186`) - **WKT bruto, não
  GeoJSON**. JTS fala WKT nativamente (`WKTWriter`/`WKTReader`), então isso
  interopera sem nenhum dos dois lados conhecer a biblioteca de geometria do
  outro.
- Gerber/Excellon **não guardam path do arquivo-fonte** - só a geometria já
  resolvida (e o texto bruto do arquivo original, como metadado inerte). É
  exatamente essa propriedade que resolve o problema que motivou essa
  mudança (Transformations/edições se perdiam ao reabrir).
- O campo `"version"` é escrito mas **nunca lido de volta** no carregamento -
  é write-only. Compatibilidade retroativa é por tolerância a chave ausente
  (cada atributo é lido num `try/except KeyError`, mantendo o default do
  construtor se faltar), não por migração versionada.

**O que foi implementado** (`org.flatcam.app.project.flatprj`,
`GerberFlatPrjCodec`/`ExcellonFlatPrjCodec`/`WktJson`):
- Mesma estrutura de arquivo (`objs`/`options`/`version`), mesmo wrapper WKT,
  XZ real via `org.tukaani:xz` (preset 3, igual ao default do Python),
  mesma auto-detecção JSON-puro-depois-XZ no carregamento.
- Gerber: `kind`/`units`/`solid_geometry`/`follow_geometry`/`tools`/
  `apertures`/`options.name`/`fill_color`/`outline_color`/`alpha_level`.
- Excellon: furos/slots reagrupados por ferramenta em `tools[id] =
  {tooldia, drills, slots, solid_geometry, data}`, igual ao shape do Python,
  em vez das listas próprias deste port (indexadas por campo `toolId`).
- `ProjectFile.GerberEntry`/`ExcellonEntry` também guardam cor de
  preenchimento/contorno, visibilidade, "Solid"/"Multi-Color" e modo
  "Follow" (Gerber) - reabrir um projeto restaura a aparência inteira, não
  só a geometria.
- Fallback para o formato v1 antigo (só paths) - se o path ainda existir,
  reparseia; se não, pula esse objeto sem falhar o carregamento inteiro.
- Testes de round-trip (`ProjectFileIOTest`), incluindo um que verifica
  que o arquivo salvo por padrão **é XZ de verdade** (bytes não são JSON
  puro) e outro que carrega um projeto v1 legado reparseando o arquivo.

**Simplificações e limites documentados**:
- Projetos novos escrevem uma entrada por flash/stroke/região em
  `apertures[code]['geometry']` e preservam a ordem em `_java.shape_order`.
  Projetos Java anteriores a essa mudança tinham só a união por abertura;
  carregam para plot/CAM, mas são somente leitura no editor.
- Aperture do tipo MACRO volta como um círculo placeholder ao recarregar (o
  texto bruto da macro não é retido) - só afeta a exibição daquela linha na
  Apertures Table; a geometria real (`apertureGeometry()`/`solidGeometry()`)
  é independente e volta exatamente como salva.
- `tools[id]['solid_geometry']` e `['data']` do Excellon voltam vazios (este
  port só mantém uma geometria agregada por todas as ferramentas, e nenhum
  dict de parâmetros CAM persistente por ferramenta) - inofensivo para
  visualizar/gerar G-code, só afetaria um fluxo Python que precisasse da
  geometria isolada de uma ferramenta específica.
- `int_digits`/`frac_digits`/`aperture_macros`/`source_file`/`zeros`/campos
  de formato Excellon não são escritos - o Python tolera a ausência (usa o
  default do construtor); nenhum é necessário para visualizar/gerar CAM.

**Importante**: a importação foi verificada com um `.FlatPrj` 8.994 real e
com conversão/reabertura do `.fcnproj` resultante do lado Java. Ainda não
houve validação visual da abertura no FX nem escrita de `.FlatPrj` pelo FX.
O `.fcnproj` é formato nativo do FX e não deve ser apresentado como projeto
diretamente abrível pelo FlatCAM Python.

**O que falta para completar 9.3**:
- Geometry: parâmetros básicos compartilhados de corte já são importados e
  persistidos, mas ainda falta um dict `data` completo por ferramenta, inclusive
  configurações diferentes entre ferramentas.
- CNC Job: precisa reter uma lista por segmento com "kind" (`gcode_parsed`
  do Python) durante a geração - `GCodeGenerator` hoje só produz duas
  geometrias já unidas (viagem/corte), não uma lista ordenada por segmento.
- O leitor nativo `ProjectFileIO.load()` continua restrito às versões internas
  1 e 2; `PythonProjectIO.load()` importa `.FlatPrj` Python 8.9xx em modo
  somente leitura. O seletor da interface oferece ambos, mas salva apenas
  `.fcnproj`. A conversão de um projeto real 8.994 para `.fcnproj` foi testada
  com os quatro tipos de objeto; a abertura visual ainda requer teste manual.
- O FX grava Geometry e CNC Job em `_java`, não em `objs`; o Python ignora essa
  extensão e não restaura esses objetos. `options` é gravado vazio, portanto
  preferências de projeto do Python também não fazem ida e volta.
- O futuro suporte dual deve preservar `.fcnproj` como formato nativo e ter
  um exportador `.FlatPrj` separado, validado na instalação Python real e com
  aviso explícito quando uma conversão perder objetos ou metadados.
- O undo/redo do Gerber Editor cobre a sessão em memória; histórico de
  comandos entre sessões não é serializado (o resultado aplicado é salvo).

### 9.4 Gerber Editor

Implementar por fatias verticais, não como um bloco único:

1. sessão de edição com Aplicar/Cancelar; ✅ concluída (seção 8);
2. seleção e hit testing; ✅ concluída (seção 8) - `GerberShape`,
   `GerberEditSession.clickSelect/boxSelect`, `GerberEditorController`;
3. command stack com undo/redo; ✅ concluída para operações em memória;
4. mover, copiar e excluir; ✅ deslocamento X/Y e gesto de origem/destino no canvas;
5. pads, tracks, regions e apertures; ✅ pads C/R/O/P/AM, arrays lineares/circulares, trilhas multiponto com cinco modos de dobra, regiões e criação/renomeação/redimensionamento/exclusão de aberturas (macro pode ser usada em pads, mas não redimensionada);
6. operações avançadas do editor legado; 🟡 poligonizar, disco/semidisco, escala, buffer, marcação por área, borracha e transformações têm ação no painel e undo/redo quando alteram formas. Array, disco/semidisco e transformações usam entrada numérica em vez do gesto original no canvas; marcação por área usa o destaque da seleção sem anotações de área. Ainda falta teste manual de ponta a ponta desses controles;
7. persistência e reabertura do resultado editado; ✅ concluída para projetos
   novos; projetos antigos só têm uniões por abertura e não podem recuperar
   as formas individuais.

Depois disso, repetir a estratégia para o Editor Excellon e ampliar Geometry e
CNC Job até a paridade necessária.

Ordem acordada em 2026-09-25: trilhas multiponto/modos de dobra, regiões e edição
de aberturas implementados; o Editor de G-Code agora tem um fluxo inicial de
edição/aplicação/exportação. Próximo passo: validar manualmente os dois editores
no JavaFX com arquivos reais e ampliar a prévia G-code conforme corpus.

### 9.5 Editor de G-Code

Arquivos `.nc`, `.gcode`, `.tap`, `.cnc`, `.txt` e ICP `.imf` podem ser abertos diretamente
como CNC Jobs, com leitura/análise em background, progresso e cancelamento. O
Editor de G-Code abre o texto de um CNC Job em aba central protegida, com
Aplicar, Cancelar e Salvar arquivo no painel lateral. Aplicar substitui o
texto do objeto em memória, sem sobrescrever automaticamente o arquivo de
máquina. O processamento roda em job cancelável, com progresso; falhas mantêm
o rascunho aberto. A prévia é reconstruída para G0/G1 e arcos G2/G3 em XY
(centro I/J incremental ou absoluto G90.1, raio R positivo/negativo, círculo
completo), incluindo G90/G91 e G20/G21. Planos G18/G19, trocas de unidades
durante movimentos e comandos de movimento não suportados deixam
a prévia indisponível, nunca conservam a geometria antiga. Isso não é um
validador de segurança CNC nem reconstrói a largura original da ferramenta.

O projeto salva o nome e texto G-code em `_java.cncJobs`, mantendo leitura dos projetos
antigos que referenciam somente o arquivo externo. Na reabertura, a análise
da prévia também roda em background; se não for possível interpretá-la, o
texto ainda é carregado. Falta validar a UI com arquivos reais e ampliar o
subconjunto modal/planos do parser antes de alegar paridade com o Python.

### 9.6 Editor de Geometry (tres fatias)

A interface foi reorganizada para corresponder ao Python: a aba lateral
"Editor Geometry" mostra a tabela virtualizada `ID | Type | Name` e os
controles de sair/descartar. Os IDs sao estaveis durante mover/desfazer; a
selecao da tabela e do desenho e sincronizada nos dois sentidos, inclusive
para Delete. As ferramentas agora ficam em uma barra superior dedicada, que
substitui a barra geral de ferramentas enquanto o editor esta aberto. O menu
de topo "Geo Editor" so aparece durante a edicao. Funcoes ainda nao portadas
(texto, paint, borracha, transformacoes) aparecem
desabilitadas na barra, sem sugerir que ja funcionam. A escolha de ferramenta
de corte aparece na barra apenas para Geometry multi-tool.

Objetos Geometry abrem pelo botão em Propriedades, menu contextual ou Editar
Objeto. O editor permite seleção por clique ou retângulo (esquerda-direita
inclui formas inteiras, direita-esquerda inclui formas tocadas), Ctrl para
múltipla seleção, Excluir pelo botão ou tecla Delete, Desfazer/Refazer e
Aplicar/Cancelar. Delete funciona enquanto o editor está ativo mesmo com foco
no painel lateral, mas não interfere com campos de texto. Aplicar altera
o objeto em memória, preservando suas ferramentas por forma; o projeto salva
essa Geometry e os caminhos de cada ferramenta em `_java.geometries`. Cancelar
mantém o objeto original. O modelo de seleção usa índice espacial; o destaque
usa uma camada Canvas separada para não repintar todos os objetos a cada
clique. Acima de 2.000 formas selecionadas, só o contorno agregado é exibido,
mas Excluir ainda opera sobre todas as formas selecionadas.

A segunda fatia inclui Caminho/Polígono por múltiplos cliques (Enter, duplo
clique ou botão direito
conclui; Backspace remove o último ponto), Retângulo/Círculo por dois
cliques e Mover/Copiar pela origem e destino no Plot Area. As formas novas são
editáveis, participam do undo/redo e entram no projeto salvo. Em Geometry
multi-tool, o painel permite escolher a ferramenta que receberá a nova forma;
mover/copiar mantém a ferramenta das formas de origem. A prévia de desenho e
movimento usa a camada Canvas leve, sem repintar o projeto inteiro em cada
movimento do mouse. O menu Editor Geometry ativa essas seis ferramentas.

A terceira fatia acrescenta Uniao, Intersecao, Subtracao (a primeira forma
selecionada e o alvo), Buffer arredondado completo/interior/exterior e Explodir
poligonos em segmentos editaveis. As operacoes booleanas exigem pelo menos
duas formas da mesma ferramenta. Buffer cria novas formas sem remover as
originais e preserva a ferramenta de cada uma; os modos interior/exterior
exigem poligonos. Explodir substitui poligonos selecionados por arestas,
incluindo contornos de furos, limitado a 10.000 segmentos por acionamento.
Todas as mudancas participam do undo/redo. Uniao, Intersecao, Subtracao e
Buffer calculam em background, com cancelamento pelo controle geral da tarefa;
em erro ou cancelamento, o rascunho permanece intacto. Durante o calculo, o
editor bloqueia outras alteracoes. O menu Editor Geometry aciona essas funcoes.

Ainda faltam texto, paint, borracha e transformacoes,
teste manual com arquivos reais grandes e perfilamento da renderização inicial
de Geometry muito extensa. A persistência `_java.geometries` é do FX e não
garante abertura de objetos Geometry no FlatCAM Python. O sentido de fresagem
CL/CV do Python (`geometry_editor_milling_type`, padrão Climb) foi portado em
2026-09-29: `GeometryEditSession.setClimbMilling` inverte a direção das formas
novas (linha, polígono, retângulo no anel do Python, círculo, arco; só o anel
exterior de polígonos), com botões Climb/Conventional no painel do editor e
preferência lembrada. Não altera formas já existentes; confira o trajeto antes
de usar o G-code.

### 9.7 CNC Job de furação e Importar/Exportar (2026-09-29)

**CNC Job de Excellon.** O gerador grava em cada troca de ferramenta um
comentário inofensivo para a máquina, `FCFX TOOL T<n> D<diâmetro>`, e o
`GCodeToolpathParser` o usa para desenhar furos e slots no diâmetro real (mesmo
depois de reabrir o projeto, abrir o `.nc` ou editar o G-code) e para montar
`ToolpathStats`: tabela de ferramentas (#, Dia, Drills, Slots, Cut Z) com Plot
por ferramenta, ordem de usinagem numerada no plot ("Display Annotation", uma
numeração igual à do Python desde 2026-09-29: início e fim de cada deslocamento,
incluindo a origem da troca de ferramenta como nº 1), distância percorrida e tempo estimado (descidas no avanço, deslocamentos
a 1500 mm/min como no Python; "—" quando falta F). Jobs de fresagem ainda não
têm o marcador e continuam com traço fino.

**Numeração em jobs de Python e de fresagem (2026-09-29).** O parser também
entende o G-code de Excellon do Python (`T1` + `(MSG, Change to Tool Dia = ...)`,
só quando o cabeçalho diz "G-code from Excellon"), então projetos `.FlatPrj`
mostram furos e números. Jobs de fresagem numeram como o Python: início e fim de
cada deslocamento G0, pulando posições já numeradas (`ToolpathStats.pathMarks`;
no projeto de teste STM32/Dados_Ambientais a Isolação dá 100 números, igual ao
Python). Usam o mesmo "Display Annotation" e o desenho com anti-colisão.

**Setas de direção (melhoria além do Python).** Jobs de fresagem guardam o meio
e o sentido de cada movimento de corte (`ToolpathStats.cutArrows`) e o
`PlotAreaView` desenha um triângulo por célula de tela de 46 px, só em
movimentos com pelo menos 14 px na tela, então mais setas aparecem ao aproximar.
Checkbox "Display Direction Arrows" (ligado por padrão) nas propriedades do job.
Não verificado visualmente em execução.

**Desempenho e desenho do preview de fresagem (2026-09-29).** Jobs que declaram a
largura do cortador (`(TOOL DIAMETER: x)` do Python) passam a ser desenhados como
linhas de centro traçadas na largura real em qualquer zoom (`setLayerCenterlineLod`
com `stroked`), em vez de um polígono bufferizado por segmento, que travava ao dar
zoom. As setas usam níveis pré-calculados por zoom (`ArrowLevels`): posições fixas no
mundo, sem recalcular a cada movimento, e o desenho só percorre a lista do nível
atual. Os números viraram etiquetas com fundo e ponto de ancoragem, e o
deslocamento fino fica tracejado e mais visível.

**Trajeto passo a passo (novo, o Python não tem).** Clique num número ou numa linha
de rota de um CNC Job (ou "Percorrer" nas propriedades do job): o trecho clicado
fica em destaque (amarelo/âmbar), o anterior em azul e o seguinte em laranja/vermelho,
com setas de direção e as passadas finas sobre o corpo largo; o resto do plot
esmaece e os números dos três trechos ganham crachá na cor do papel. Um resumo
("Passo 12/325 - Corte (5 > 6) - 3.9 mm") aparece no plot e nas propriedades.
A faixa do rodapé tem botões clicáveis (anterior, próximo, limpar); as setas do
teclado (esq/dir, cima/baixo) também andam pelos trechos e Esc limpa; clicar no
vazio também limpa. O parser gera `ToolpathStats.steps` (`PathStep`: deslocamento,
corte ou furo, encadeados na ordem do programa, com o número de cada ponta) e
`CncStepView` cuida da seleção, do clique e do desenho. Verificado por captura
fora da tela no projeto STM32/Dados_Ambientais (Furos_Alinhamento e Cobre_MortoFino).

**Jobs de fresagem gerados pelo FX (2026-09-29).** O G-code de Geometry, isolamento
e recorte agora escreve `FCFX MILL D<largura>` (um por ferramenta), que o parser
lê como largura do cortador; jobs de uma só largura ganham o mesmo preview rápido
(linhas de centro traçadas na largura real) dos importados do Python, e os
passos/setas/numeração usam o parser. Jobs com várias larguras seguem no desenho
por polígonos.

**Reprodução, tempo e CSV do trajeto (novo, o Python não tem).** A faixa do passo a
passo ganhou reproduzir/pausar (também com Espaço) e velocidade (0.5x a 8x; cada
passo dura 0,7 s / velocidade). A reprodução acompanha o trecho movendo a vista
(sem mudar o zoom), para ao chegar no fim e ao navegar ou clicar manualmente. A
faixa mostra o tempo estimado decorrido até o fim do trecho e o total
(`PathStep.endMinutes`, mesma base do "Estimated time"; some quando falta F). Nas
propriedades do job, "Reproduzir" e "Exportar CSV" (`CncStepCsv`: passo, tipo,
marcas, coordenadas, comprimento, unidade, duração e tempo acumulado). O tempo de
um furo só aparece no fim do deslocamento seguinte, porque o mergulho é lido junto
com ele.

**Exportar** (menu Arquivo > Exportar):

- Gerber/Excellon: diálogo com as opções `gerber_exp_*`/`excellon_exp_*` do
  Python (unidades com conversão, dígitos, zeros L/T ou LZ/TZ, decimal ou sem
  ponto, slots roteados G00/M15/G01/M16 ou G85), lembrado entre sessões. O
  Gerber continua como regiões do cobre resolvido. O Python exportava slots
  roteados com início = fim e trocava a supressão de zeros; aqui não.
- SVG: Gerber/Excellon/Geometry no visual do shapely; CNC Job com deslocamentos
  (#F0E24D) sob cortes (#5E6CFF) na largura da ferramenta. viewBox corrigido.
- DXF: R12 POLYLINE/VERTEX com `$INSUNITS` e anéis fechados; aceita também
  contornos de Gerber/Excellon (o Python só Geometry).
- PNG: a área de plotagem como está, na escala de saída da tela.

**Importar** (menu Arquivo > Importar), em múltiplos arquivos num job:

- SVG como Geometry/Gerber: unidades reais (px = 1/96 in; 1/72 em arquivos do
  Illustrator e 1/90 no Inkscape < 0.92), origem do viewBox, aspecto, `<use>`,
  transformações compostas, `defs` só quando usados, furos even-odd; no Gerber,
  linhas com traço ganham a largura do traço. Texto é contado, não importado.
- DXF como Geometry/Gerber: `$INSUNITS`, bulges como arcos, SPLINE NURBS,
  INSERT com arrays/rotação, pontas de linha que quase se tocam são unidas; no
  Gerber, contornos fechados viram cobre com furos (even-odd).
- HPGL2 como Geometry: fluxo de comandos HPGL real (vários por linha, PD/PR com
  pontos, CI/AA/AR/AT/RT, retângulos); uma Geometry por caneta.
- O parser Excellon agora lê slots roteados e ferramentas `T01F00S00C...`, o
  formato que o Python exporta.
- PDF: só o estilo que o Python também suporta (um "print" vetorial de
  artes tipo Gerber pelos operadores de caminho do content stream) - sem
  xref/object streams, criptografia, imagens ou rotação/inclinação. Cada troca
  de cor de traço (`RG`) vira um objeto Gerber (`_1`, `_2`, ...); um
  preenchimento branco em forma de curva vira furo (`_0`, Excellon). Ao
  contrário do Python, os operadores são lidos como o fluxo de tokens que um
  content stream realmente é, não um por linha - a suposição de "um operador
  por linha" do Python faz com que ele descarte silenciosamente qualquer
  operador que divida a linha com outro, o que geradores reais fazem (o
  reportlab, por exemplo, escreve `n` seguido de `re S` inteiro na mesma
  linha); sem esse ajuste, PDFs reais simplesmente não importavam nada.
  Também corrigido: um preenchimento de Bezier encadeado gerava polígonos
  extras errados (Python nunca zerava os pontos entre os segmentos); o último
  ponto de cada Bezier era descartado; a largura/altura de um retângulo levava
  o deslocamento em dobro; o operador de curva `y` nunca marcava seu subcaminho
  como curva; e `s`/`b`/`b*` nunca eram reconhecidos, mesmo com os padrões já
  compilados no Python. Os valores geométricos foram conferidos rodando o
  próprio `ParsePDF.PdfParser` do Python lado a lado (stub só da dependência
  Qt) com o mesmo content stream, e por fim contra um PDF real gerado pelo
  reportlab.

Ainda não portado: Imprimir PDF e backup de preferências.
Posições no SVG e no PDF importados são relativas à página (canto inferior
esquerdo na origem), como no Python e no Inkscape.

## 10. Regras de implementação para qualquer IA

### Use o Python como oráculo

Antes de portar uma função, localize o fluxo real em `appGUI`, `appTools`,
`appEditors`, `camlib.py` e arquivos relacionados. Consulte também
`UI_INVENTORY.md`. Registre:

- entradas, defaults e unidades;
- validações e mensagens;
- objetos produzidos e seus metadados;
- comportamento de plot e seleção;
- efeito de cancelamento;
- formato da saída.

Se o ambiente Python não puder executar por dependência ausente, ainda é
possível inspecionar o código e criar fixtures. Não invente paridade apenas a
partir do nome de um controle.

### Preserve as fronteiras

- Nada de JavaFX em `flatcam-cam` ou `flatcam-application`.
- Parsing e geometria não devem conhecer controles de tela.
- Trabalho pesado não roda na JavaFX Application Thread.
- Todo loop potencialmente longo deve ter checkpoints de cancelamento.
- O progresso deve ser monotônico, limitado a `0.0..1.0` e baseado em unidades
  de trabalho conhecidas; use indeterminado somente quando necessário.
- A UI só publica objetos completos. Falha/cancelamento não deixa metade de um
  resultado na árvore.

### Compare geometria corretamente

- Normalize ou compare com tolerância.
- Não dependa da ordem de componentes/pontos quando ela não tem semântica.
- Teste área, envelope, comprimento, quantidade de elementos e validade.
- Guarde fixtures reais pequenos; não dependa apenas de exemplos sintéticos.
- Adicione regressão para todo bug de parser ou CAM corrigido.

### Não esconda simplificações

Uma fatia menor é aceitável quando termina em um fluxo utilizável. Marque no
código/documento o que ficou faltando e não apresente como paridade completa.

### Preserve o trabalho existente

Antes de editar, execute `git status --short`. Mudanças preexistentes são do
usuário até prova em contrário. Não faça reset destrutivo, não reformate o
repositório inteiro e mantenha commits focados.

## 11. Comandos confiáveis

Execute a partir de `flatcam-next`.

### Windows PowerShell

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-25.0.4.1"   # ou o JDK 25 instalado; reabra o terminal se mudar a variável do usuário
.\mvnw.cmd -q clean test
.\run.cmd                      # usa o launcher nativo se achar o g++, senão o javafx:run
.\run-native.cmd --verbose-gpu # compila e abre pelo FlatCAMFX.exe; mostra o adaptador D3D usado
```

O launcher nativo exige o `g++` do MSYS2 UCRT64 em `C:\msys64\ucrt64\bin`. `build-native.cmd` apaga
`flatcam-fx\target\dependency` antes de copiar os jars, para não misturar JavaFX de versões diferentes.

### Linux/macOS

```bash
./mvnw -q clean test
./mvnw -q install -DskipTests
./mvnw -q -pl flatcam-fx org.openjfx:javafx-maven-plugin:0.0.8:run
```

No Windows, `run.cmd` executa `install -DskipTests` antes do `javafx:run`.
Ao executar apenas `flatcam-fx`, Maven pode resolver `flatcam-cam` e
`flatcam-application` a partir de snapshots antigos em `~/.m2`. O app pode
abrir e falhar somente ao clicar numa ferramenta cuja classe nova não esteja
instalada. Se não usar o script, execute esses dois comandos em sequência.

Use o goal JavaFX totalmente qualificado; `javafx:run` curto pode não ser
resolvido sem `pluginGroups` no `settings.xml`.

## 12. Mapa de arquivos

| Caminho | Papel |
| --- | --- |
| `flatcam-application/.../job/` | executor, contexto, handle, progresso e cancelamento de jobs |
| `flatcam-application/.../project/` | schema e IO do `.fcnproj` (v2: Gerber/Excellon e Geometry com geometria embutida) |
| `flatcam-application/.../project/flatprj/` | codecs compatíveis com o `.FlatPrj` do Python - `WktJson`, `GerberFlatPrjCodec`, `ExcellonFlatPrjCodec` |
| `flatcam-cam/.../gerber/` | parser e geração de geometrias Gerber |
| `flatcam-cam/.../excellon/` | parser e modelo Excellon |
| `flatcam-cam/.../isolation/` | Isolation Routing |
| `flatcam-cam/.../cutout/` | Board Cutout |
| `flatcam-cam/.../ncc/` | Non-Copper Clearing (multi-tool + Rest Machining) |
| `flatcam-cam/.../geometry/` | modelo por ferramenta e sessão de seleção/exclusão do Editor Geometry |
| `flatcam-cam/.../transform/` | motor de Transformations (Rotate/Scale/Skew/Mirror/Offset) - `TransformOp` (sealed), `TransformReference` e `AlignObjects` |
| `flatcam-cam/.../convert/` | ferramentas Gerber→Gerber/Excellon: `OutlineToArea`, `InvertGerber`, `Subtract`, `ExtractDrills`, `Punch`, `EtchCompensation`, `Fiducials`, `CornerMarkers`, `QrCodeMarker` |
| `flatcam-cam/.../merge/`, `.../panel/`, `.../solderpaste/` | Join Objects (`GerberJoin`, `ExcellonJoin`, `GeometryJoin`), `Panelize`, e `SolderPaste` (geometria de dispensa + G-code Paste_1) |
| `flatcam-cam/.../svg/FilmExporter.java` | Film Tool: SVG, PNG (Java2D) e PDF vetorial |
| `flatcam-fx/.../DensityRaster.java`, `DenseRenderer.java`, `PlotDrawableIndex.java` | LOD por densidade do Plot Area: rasterização por cobertura de área, thread de fundo e índice de partes visíveis |
| `native-launcher/`, `build-native.cmd`, `run-native.cmd` | launcher nativo (C++ + JNI) que cria a JVM no próprio processo; ver `NATIVE_GPU.md` |
| `flatcam-cam/.../gcode/` | parâmetros, geração e resultado de G-code; `GCodeToolpathParser` também extrai `ToolpathStats` |
| `flatcam-cam/.../svg/`, `.../dxf/`, `.../hpgl/`, `.../pdf/` | importadores/exportadores SVG, DXF, HPGL2 e importador de PDF (ver seção 9.7) |
| `flatcam-fx/.../CamExportDialog.java`, `PlotPngExporter.java`, `CncJobToolsTable.java` | diálogo de formato Gerber/Excellon, exportação PNG e tabela de ferramentas do CNC Job |
| `flatcam-fx/.../MainWindow.java` | integração principal da UI; atualmente grande demais |
| `flatcam-fx/.../PlotAreaView.java` | Canvas, viewport e desenho das camadas |
| `flatcam-fx/.../*ToolPanel.java` | painéis JavaFX por ferramenta |
| `flatcam-fx/.../Icons.java` | resolução/tratamento de ícones claro/escuro |
| `flatcam-fx/src/main/resources/.../theme/` | tokens e componentes CSS dos temas |
| `flatcam-cam/src/test/` | testes unitários, baselines e fixtures CAM |

Os caminhos acima omitem o prefixo comum
`flatcam-next/<modulo>/src/main/java/org/flatcam/` para facilitar a leitura.

## 13. Definition of Done de uma fatia funcional

Uma funcionalidade não está pronta apenas porque o botão aparece. Para marcar
uma fatia como concluída:

- comportamento e defaults foram comparados com o Python;
- cálculo principal está fora da UI;
- erros de entrada têm mensagens úteis;
- operação pesada tem progresso/cancelamento quando aplicável;
- há testes positivos, limites e regressões relevantes;
- falha/cancelamento não corrompe o estado do projeto;
- UI completa o fluxo até um resultado observável/salvável;
- temas claro e escuro foram verificados quando há mudança visual;
- `clean test` passa;
- app inicia depois de `install` do reactor;
- documentação de progresso/limitações foi atualizada;
- commit é focado e descreve o resultado.

## 14. Checklist de início para a próxima IA

1. Ler este arquivo, o contexto arquitetural e o inventário de UI.
2. Rodar `git status --short` e `git log -5 --oneline`; conferir `echo $env:JAVA_HOME` (JDK 25).
3. Rodar `clean test` para estabelecer baseline (462 testes em 2026-10-01).
4. Confirmar no código Python o próximo comportamento a portar.
5. Trabalhar primeiro no núcleo e nos testes, depois ligar a UI.
6. Instalar o reactor e executar o smoke test JavaFX.
7. Atualizar este documento se o estado ou a prioridade mudou.
8. Mostrar `git diff --check`, revisar o diff e só então commitar.

## 15. Resumo executivo

**Persistência das configurações CNC (2026-10-01, após `77140231`).**
Geometry e Excellon agora preservam o perfil escolhido no `.fcnproj` v2, com
`GeometryCncSettings` e `DrillCncSettings` opcionais, mantendo construtores/arquivos
anteriores compatíveis. Geometry salva diâmetro informado (quando não tem ferramentas
associadas), V-Tip Dia/Angle por índice e os parâmetros usados na última geração;
o callback antes não atualizava esses defaults na origem. Drilling salva Tool change,
altura de troca, End Z/XY, Feed rapids, sondagem, seleção e ordem das ferramentas,
além dos defaults individuais já existentes. Os dados são registrados somente
após gerar/salvar o programa com sucesso; rascunhos não enviados não são salvos.
Trocar a origem restaura dados daquele objeto; copiar mantém os registros, remover
ou limpar o projeto os elimina. Confirmação de sondagem nunca persiste e deve ser
refeita no painel. IDs excluídos não selecionam outras ferramentas e são avisados.
Perfil novo desconhecido/configuração inválida recusa a abertura sem fallback
silencioso. A importação `.FlatPrj` continua com o subconjunto anterior; parâmetros
globais/perfis Python não foram mapeados aqui. Metadados Roland nos CNC Jobs e
opções CAM adicionais continuam pendentes. Testes regeneram código idêntico para
20 perfis Geometry e 15 Drilling em JSON/XZ, e exercitam formulários na thread FX,
precisão, V-tip, subconjunto/ordem, confirmação, troca de origem/reset e versões
sem os novos campos. Fluxo completo na janela principal aguarda teste manual.

**Sexto incremento de pós-processadores (2026-10-01, após `d87cc986`).**
Adicionado Toolchange_Probe_MACH3 em Geometry e Drilling: troca Tn/M6 obrigatória,
duas sondagens G31 (a segunda com metade do avanço), G92 Z de contato e XY de troca
opcional. Exige RPM positivo, números finitos/representáveis e alturas coerentes.
O painel exige confirmação dos cuidados; em cada G31 há M0 antes de aplicar G92
para confirmar contato real ou abortar, diferença deliberada do legado. Depois
há pausa para remover placa/clips antes de ligar spindle. G92 permanece ativo;
sensor, curso físico, offsets e macro M6 precisam ser validados pelo operador.
Não há conexão com hardware nem detecção automática de falha da sonda.
Prévia/estatísticas ficam indisponíveis para G31/G92, inclusive abrir, Aplicar e
reabrir projeto; o código continua editável/exportável. A geração não calcula
footprints inutilizados nesse perfil. Defaults de Geometry persistem opcionalmente
no `.fcnproj`; configuração global de Drilling ainda não persiste. Geradores
diretos de Isolation/Cutout recusam sondagem sem parâmetros e orientam usar Geometry.
Testes do ciclo, falhas/configuração, furos/slots/Multi-Depth, MM/IN, cancelamento,
persistência e controles FX passaram no build completo. Teste manual e físico pendentes.
Inventário fechado: 19 perfis Python selecionáveis + Paste_1 separado = **20/20 ports
parciais**, não paridade completa. Detalhes/limites em `PREPROCESSADORES.md`.

**Quinto incremento de pós-processadores (2026-10-01, na sequência do HPGL).**
Adicionado Roland_MDX_20 em Geometry e Drilling, com arquivo RML-1 nativo `.rml`/`.prn`,
motor !MC1/!MC0, XYZ absolutos em 1/40 mm e velocidades V de 0,1..15 mm/s. Por enquanto
somente MM e uma ferramenta por arquivo: IN, múltiplas ferramentas, troca mecânica e dwell
são recusados na API, com campos equivalentes bloqueados no painel sem apagar rascunhos.
RPM não é emitido; o motor liga durante a usinagem mesmo com o valor ignorado de RPM = 0.
Rapid = 0 usa 900 mm/min; avanços fora de 6..900 são recusados, corrigindo o mínimo do Python
sem seu clamp silencioso. O primeiro XYZ assume origem XY; cabe ao operador posicionar e
conferir alturas. A prévia lê ^IN/^PA/Z/V/!MC e aceita o motor sem terminador do legado,
recusa resets/comandos não modelados, preserva furos/slots e estima tempo pela V efetiva.
Arquivos nativos não contêm metadados de ferramenta: ao reabrir/aplicar (também projetos)
o percurso volta, mas com largura fina e sem tabela de ferramentas de furação; a geração
mantém a largura real. Isso permanece como pendência para um suplemento de metadados.
Testes do codec, Geometry/Drilling, V-tools, limites, cancelamento, persistência e controles
FX passaram. Teste manual e no equipamento pendentes. Total: 19 de 20 ports parciais,
incluindo Paste_1; resta Toolchange_Probe_MACH3. HPGL e Roland são entregues nesta rodada.

**Quarto incremento de pós-processadores (2026-10-01, após `d61304e6`).** Adicionado HPGL em
Geometry → CNC Job, com saída nativa `.plt` (IN/PU/PD/PA/SP/CO), canetas por ordem de ferramenta,
quantização de 0,025 mm e conversão de MM/IN. Mantém as larguras/unidades de origem em CO e gera
o plot a partir dos pontos arredondados. Não emite Z, spindle ou feed; a interface desabilita esses
campos, Multi-Depth, V-tip e troca mecânica sem apagar valores. Coordenadas fora de -32767..32768,
pontos isolados e caminhos colapsados são recusados, sem o clipping silencioso do Python. Finaliza
com PU/SP0. A prévia CNC cobre comandos lineares absolutos/relativos, recusa resets/transformações
e outros comandos não modelados e informa tempo indisponível. Importar HPGL2 continua como fluxo
separado para Geometry. Abertura, editor, exportação e reabertura de projeto aceitam HPGL. Testes
de geração/leitura, unidades, limites, cancelamento, persistência embutida e controles FX passaram;
teste manual do painel e do plotter pendentes. Total: 18 de 20 ports parciais, incluindo Paste_1.
Na sequência: Roland_MDX_20 (entregue acima) e depois Toolchange_Probe_MACH3.

**Terceiro incremento de pós-processadores (2026-10-01, após `3d891d08`).** Adicionados line_xyz e
ISEL_ICP_CNC: 16 perfis Python no seletor mais FX portable, total 17 de 20 ports parciais incluindo Paste_1.
line_xyz escreve XYZ em todos os movimentos; o parser identifica mergulhos por deslocamento real,
sem confundir X/Y repetidos com movimento lateral. O codec ICP gera FASTABS/MOVEABS, VEL, GETTOOL,
SPINDLE, WAIT e PROGEND em unidades inteiras; normaliza o subconjunto para a prévia compartilhada,
com cancelamento/progresso. Aceita apenas MM; não copia o WPCLEAR/retorno Z0 final do Python.
GETTOOL automático independe da pausa manual e é seguido de reafirmação da altura livre. UI usa
extensão .imf na geração/abertura/edição; texto nativo embutido reabre sem depender do arquivo externo.
Comandos ICP desconhecidos, resets de origem e movimentos relativos invalidam a prévia. Testes cobrem
conteúdo nativo, unidades, furos/slots, tool changes, limites e os controles JavaFX sem janela visível.
Restam hpgl, Roland_MDX_20 e Toolchange_Probe_MACH3; validação visual e física continuam pendentes.

**Segundo incremento de pós-processadores (2026-10-01, após `c718e582`).** Adicionados ISEL_CNC,
Toolchange_Manual e Toolchange_Custom: 14 perfis Python no seletor (mais FX portable), 15 de 20
ports parciais incluindo Paste_1. ISEL emite G71, aceita somente MM e troca por M06/M01; o parser
interpreta G71 apenas com identificação do perfil. Manual inclui três M0 e G01 Z0 com feed explícito;
Custom depende da macro M6 da máquina. Após M6/M06 o gerador reafirma G90 e altura livre, sem simular
a macro. Geometry permite ativar a troca inicial também em jobs de ferramenta única. Testes cobrem
geração/reabertura com duas ferramentas, rejeição de IN no ISEL e os controles JavaFX. Pendentes:
ISEL_ICP_CNC, Roland_MDX_20, hpgl, line_xyz e Toolchange_Probe_MACH3. Detalhes em PREPROCESSADORES.md.

**Incremento de pós-processadores (2026-10-01, após `1b67894e`).** Adicionados Marlin, Repetier,
Berta_CNC, GRBL_laser, Marlin_laser_FAN_pin, Marlin_laser_Spindle_pin e Z_laser. Furação oferece
somente fresagem; Geometry oferece os 12 perfis do seletor (11 do Python + FX portable). Laser
tem geração própria, saída desligada antes dos deslocamentos, potência validada e parser baseado
em M/S para conservar a prévia ao reabrir/aplicar. UI desabilita parâmetros de mergulho/troca no
laser. Feed rapids Marlin/Repetier é configurável e o valor de Geometry é persistido, com fallback
automático para projetos antigos. Repetier usa FAN/PWM e @pause pelo host; Marlin mantém M6 do
Python, que exige verificar suporte no firmware. A contagem antiga de 21 era de arquivos: existem
20 classes de perfil, excluindo __init__.py; 12 têm port parcial incluindo Paste_1. SolderPaste
não foi alterado: permanecem as duas falhas identificadas na revisão. Detalhes, diferenças,
limites e testes em `PREPROCESSADORES.md`; validação física não foi feita.

O FlatCAM FX já deixou de ser um esqueleto: carrega e plota Gerber/Excellon,
tem uma árvore lateral próxima do legado, jobs com progresso/cancelamento,
Isolation, Cutout, NCC multi-tool com Rest Machining e boundary por
referência, Geometry -> CNC, G-code, e Transformations (Rotate/Skew/Scale/
Flip/Offset) completas para os três tipos de objeto. O Gerber Editor já
seleciona, exclui, move e copia formas, tem undo/redo, aplica um novo objeto e
salva/reabre suas formas individuais em projetos novos. A paleta inteira tem
ações funcionais: pads C/R/O/P/AM, arrays, trilhas multiponto com cinco modos
de dobra, regiões, discos, semidiscos, poligonização, edição de aberturas,
marcação por área, borracha, escala, buffer e transformações. As operações de
edição participam do undo/redo e da persistência do projeto; ferramentas
avançadas numéricas ainda não reproduzem todos os gestos do Python.
"Salvar como..." exporta
o cobre atual como Gerber válido, embora ainda sem preservar a semântica das
aberturas originais. Os menus Importar (SVG, DXF, HPGL2) e Exportar (SVG, DXF,
PNG, Gerber, Excellon com formato escolhido, e Importar PDF) funcionam; só
"Imprimir PDF" e o backup de preferências faltam (seção 9.7).
CNC Jobs de furação mostram furos no diâmetro real, tabela de ferramentas,
ordem de furação e tempo estimado. O Plot Area seleciona objetos por clique/retângulo e abre
menus funcionais no botão direito e abre Propriedades com duplo clique. O
Editor de Geometry tem seleção/exclusão visual, desenho básico, mover/copiar,
undo/redo, Aplicar/Cancelar e
salvamento no projeto, com índice espacial e destaque isolado para evitar
repintura integral ao clicar. Editor de G-Code agora abre arquivos diretamente, edita o texto do CNC Job, aplica em background com
progresso/cancelamento e salva o rascunho como arquivo. O projeto embute esse
texto para que a edição sobreviva à reabertura; a prévia G0-G3 em XY é reconstruída
sem reutilizar um plot antigo após mudança. O próximo trabalho recomendado é
validar manualmente os dois editores com arquivos reais e conferir Gerber
exportado em um visualizador independente. Ferramentas avançadas do Editor
Geometry, plot CNC com largura de ferramenta e lacunas de NCC seguem no roadmap.

**Estado em 2026-10-01.** O projeto roda em Java 25 + JavaFX 25.0.4 (ganho medido de ~2-3x na primeira
isolação do projeto real) e já porta as 24 ferramentas do menu Ferramentas do Python (2-Sided, Align Objects,
Extract Drills, Paint, Panelize, Film, SolderPaste, Subtract, QRCode, Fiducials, Punch Gerber, Invert Gerber,
Corner Markers e Etch Compensation, além das anteriores), mais Outline→Area e Join Objects. O Plot Area aguenta
geometrias com centenas de milhares de traços com um LOD por densidade assíncrono. Restam
ampliar a integração da Tools Database (editor já entregue), salvar `.FlatPrj` e completar parâmetros/validação dos pós-processadores.
A suíte atual registra 695 testes (686 executados sem falhas, 9 ignorados); a validação manual dos painéis
recentes no app real é a principal pendência de qualidade. A fila detalhada está na seção 9.0.

### Tools Database: editor visual/funcional (2026-10-01, após `9c229bba`)

`Opções > Tools Database` agora abre uma aba reutilizável na área central, não um placeholder.
`ToolsDatabasePanel` reproduz a lista ID/Tool Name e as seções Description, Milling, Drilling,
Isolation, Paint, NCC e Cutout de `appDatabase.py::ToolsDB2`. Os **63 campos** são verificados
contra as chaves do formulário Python em teste automatizado. Os ícones dos comandos são os
mesmos assets Python, com variantes claras/escuras. Colunas de parâmetros agrupadas, rolagem,
cards recolhíveis e botões que quebram linha evitam exigir a largura fixa do Python.

- Adicionar, copiar uma/múltiplas ferramentas, excluir com confirmação, buscar nome/ID/diâmetro
  e filtrar operação. IDs existentes são preservados e novos IDs não sobrescrevem entradas esparsas.
- Apenas uma ferramenta pode ser editada por vez; seleção múltipla permite copiar/excluir.
- Parâmetros dependentes ficam desabilitados conforme checkbox/shape/gap type. V-tool calcula
  o diâmetro efetivo a partir de V-Dia, V-Angle e Cut Z, quando esses campos são alterados.
  `Laser_lines` de Paint fica desabilitado no seletor como no Python; valores importados são preservados.
- Alterações são validadas por **Aplicar parametros**, troca de seleção ou Save DB. Valores inválidos
  bloqueiam a troca/uso sem apagar o rascunho. Campos não modificados e desconhecidos, inclusive
  preprocessadores e configurações avançadas, não são substituídos por defaults.
- Import DB e Save DB rodam via `JobExecutor`; falhas mantêm a base/rascunho atual. Save DB associa
  o arquivo à base e limpa `*`; Export DB escreve uma cópia e mantém a associação/estado de edição
  (exportar sobre o próprio arquivo ativo equivale a salvar). O último arquivo associado é lembrado.
- Escrita JSON UTF-8 compatível com Python, publicação atômica e backup exato `.bak` com nome único
  antes de sobrescrever. Links simbólicos e destinos que não sejam arquivos são recusados.
  Não há migração destrutiva nem gravação automática na base Python.
- Fechar aba/aplicativo com alterações pede confirmação para descartar; Cancelar permite voltar
  ao editor e salvar. Durante I/O, fechamento é bloqueado. Ctrl+S salva e Ctrl+F foca a busca;
  atalhos globais do Plot não são aplicados enquanto a aba da base está ativa.
- Isolation/NCC/Drilling passam a ler o snapshot validado do editor, incluindo alterações ainda
  não salvas. Se a aba nunca foi aberta, preservam o seletor de arquivo já existente. A conversão
  desse fluxo antigo ainda é síncrona; somente Import/Save/Export do editor são assíncronos.

**Limites:** ainda não é paridade total da base. A transferência para Milling/Geometry, Paint e
Cutout e a reprodução do botão contextual “Transfer the Tool” do Python não foram implementadas
nesta etapa. Os 63 campos são editáveis/persistidos, mas os adaptadores CAM existentes só consomem
os parâmetros que já suportavam; editar um campo avançado não garante que o gerador FX o utilize.
Defaults de ferramentas novas são valores portáveis fixos, não as preferências globais completas
do Python. `.FlatDB` não especifica unidades: não há conversão automática de MM/IN. IDs inválidos,
diâmetro não positivo, tolerâncias invertidas e arquivos maiores que 10 MB são recusados.

**Ajuste futuro — tamanho da Tools Database:** o limite fixo de 10 MB é uma proteção
conservadora herdada dos leitores `.FlatDB` do FX, não uma exigência do Python, do formato
ou do JavaFX, nem um valor determinado por benchmark. Deve ser revisado para permitir
bases legítimas maiores: avaliar limite configurável e/ou aviso antes de carregar, mantendo
I/O em segundo plano e medindo memória/tempo de parsing e cópia. Revisar conjuntamente
os leitores CAM, o editor e a gravação, que hoje aplicam esse limite. Ele se refere apenas
à Tools Database; não limita Gerbers, Geometry, CNC Jobs ou projetos. Nesta entrega o
comportamento de recusa permanece inalterado.

**Verificação:** 7 testes de documento/backup/compatibilidade e 15 testes de painel, incluindo os
quatro temas e largura reduzida. Capturas `target/tools-db-*.png` podem ser geradas com
`-Dflatcam.tests.snapshots=true` e foram inspecionadas. `install` completo: 684 registrados,
675 executados, 0 falhas/erros, 9 ignorados. O teste de importação inválida imprime intencionalmente
“Job failed”, sem falha na suíte. Falta validação manual das confirmações, FileChooser, troca de
temas no app e round-trip com a base pessoal do usuário aberta novamente no Python.

**Próximo passo recomendado nesta frente:** seleção/transferência de ferramenta da base para
Geometry/Milling, Paint e Cutout, seguida de mapeamento explícito dos parâmetros ainda ignorados.

### Tools Database: contraste das confirmações e tooltips (2026-10-01, após `358da42c`)

As confirmações da base herdam explicitamente os stylesheets da janela atual. O botão padrão
usa `-fc-accent` e `-fc-on-accent`, com estados hover/pressionado/foco nos dois estilos de
componentes. Quatro testes JavaFX verificam contraste mínimo de 4,5:1 nos quatro temas e
nesses estados; capturas sem janela visível foram inspecionadas.

`ToolsDatabaseDescriptions` adiciona ajuda em português baseada em `ToolsDB2UI` do Python,
cobrindo os 63 campos, seus rótulos, sete seções, oito botões, busca, filtro, lista e arquivo
associado. Explica unidades, opções, dependências e quais campos ainda não são transferidos
para CAM no FX. Rótulos continuam habilitados e permitem consultar a ajuda de inputs opcionais
desabilitados. Aplicar/Salvar/Exportar têm explicações distintas sobre memória, disco e backup.

Os textos usam título e corpo do `FluidTooltips`, com animação, quebra de linha e a paleta
do tema atual, sem um segundo popup nativo concorrente. O menu de botão direito da base
é registrado por `attachContextMenu`, após seus nós estarem disponíveis; fechar o menu
fecha também sua ajuda. Não houve mudança de parâmetros, formato de arquivo ou geração CAM.

Cinco testes verificam cobertura/conteúdo e dois verificam os metadados nos controles,
rótulos (inclusive inputs desabilitados), seções e ações contextuais. `install` completo:
695 registrados, 686 executados sem falhas/erros, 9 ignorados. Os testes cobrem conteúdo e
integração nos nós; hover/animação e menus precisam da confirmação manual no app real.

### Tooltips contextuais em todo o FX (2026-10-02)

O formato estruturado de `TooltipContent` agora é compartilhado por `ToolDescriptions`,
pela Tools Database e pela adoção dos tooltips nativos pelo `FluidTooltips`. Preserva os
textos existentes, com parágrafos, opções em negrito, unidades em destaque azul e notas
`Atenção:`/`Integração FX:` em destaque âmbar, adaptados aos quatro temas. Não interpreta
HTML/Markdown. Mantém os tempos/animações e o fechamento por clique, scroll e teclado.

`PanelTooltips` acrescenta ajuda contextual aos formulários CAM, editores, propriedades
dos objetos e preferências, por meio de `MainWindow.openToolPanel`/`showProperties`.
Descreve parâmetros como profundidades, avanços, ponta V, passes, seleção de área,
rest machining, pontes, espaçamentos, transformações e unidades especiais de calculadoras
e QR Code. Os significados de Margin, Method e outros rótulos ambíguos dependem da ferramenta;
os avisos de transferência da base não são reutilizados nos painéis de operação.

Os rótulos oferecem ajuda mesmo com o input desabilitado. Linhas criadas dinamicamente e
rótulos que mudam conforme o pós-processador atualizam a ajuda; trocar o input de uma linha
não deixa sua descrição vinculada ao controle antigo. O instalador não percorre skins ou
células virtualizadas. Tooltips nativos de células continuam sendo atualizados pelo controle.
Botões textuais óbvios não recebem uma repetição do próprio nome; identificação de comandos
somente com ícone e descrições de ações não óbvias são preservadas.

Os diálogos de exportação Gerber/Excellon e de cor/opacidade também registram o
`FluidTooltips` em suas próprias cenas, herdando o tema da janela quando houver owner e
fechando o popup ao encerrar o diálogo. A ajuda distingue explicitamente L/T do Gerber
e LZ/TZ do Excellon. Nenhuma alteração em geometria, parâmetros CAM ou formatos de projeto.

Validação: nove novos testes cobrem contexto, unidades, as 24 descrições do menu Tools,
rótulos/input desabilitado, linhas dinâmicas, troca de input, preservação de ajuda nativa,
ações óbvias versus ícones, células e instalação em diálogos. Suíte `flatcam-fx`:
133 registrados, 131 aprovados, 2 ignorados, zero falhas/erros. Capturas sem janela visível
de NCC, Cutout, sondagem e exportação nos quatro temas foram inspecionadas. A suíte completa
foi tentada também com diretório temporário isolado, mas falhou na limpeza de `@TempDir`
(`DirectoryNotEmptyException`) em testes de persistência/CAM, sem falhas de asserção;
essa execução não deve ser registrada como uma regressão completa aprovada.
# Série de paridade — 2026-10-02

Etapa 4: exportação Python 8.994 `.FlatPrj` (JSON/XZ) no diálogo Salvar Projeto,
além do formato nativo. Gerber/Excellon/Geometry/CNCJob ficam em `objs`; parâmetros
CNC nativos são complementados por metadados privados. Reimportação de arquivo
reserializado pelo Python recupera parâmetros por ferramenta e ponta V disponíveis.
Corrigida prioridade de unidades Excellon: coordenadas seguem `units`, não o
cabeçalho original `excellon_units`. Fixture autorizada tinha MM/INCH discordantes.
Validação headless com os serializadores/parsers reais Python 3.11 e Shapely 2.1.2:
18 objetos do projeto real aprovados, além de exemplo JSON/XZ e reabertura FX após
reeserialização Python. Original preservado; artefatos privados somente em target.
Limitações e comandos em COMPATIBILIDADE_FLATPRJ.md. Sem declaração de paridade
integral ou validação visual da janela Python.

Regressão final da série: `mvnw.cmd -q test` concluído com sucesso, 727 testes
registrados (716 aprovados, 11 ignorados), zero falhas/erros. CAM: 483; Application:
108 (9 ignorados); FX: 136 (2 ignorados). Houve tentativas anteriores com erro de
limpeza de temporários Windows, sem falha de asserção; a repetição final completa
passou sem desabilitar a limpeza. Testes opcionais com o projeto real e com o
arquivo reserializado pelo Python também executados e aprovados separadamente.

Etapa 3: Geometry/CNC possui parâmetros individuais por linha e Aplicar a todas.
Edições inválidas não desaparecem ao trocar a seleção; todas as linhas são validadas.
Inclui Feedrate Z distinto de XY, dwell após ligar spindle e Extra Cut em caminhos
fechados (no máximo um perímetro adicional). Defaults, campos novos e mapa por ferramenta
persistem no formato nativo; versões antigas usam XY como Feed Z. Tools Database
transfere esses campos em Milling. Troca/probing, avanço rápido e perfil são comuns.
Laser/HPGL/Roland/sondagem continuam com parâmetros comuns e aviso explícito;
diferenças individuais não são aceitas para Roland/sondagem. Testes de gerador,
perfis, persistência e controles FX aprovados. A execução adicional ProjectFileIOTest
teve erros somente na limpeza de temporários Windows, sem falha de asserção.

Etapa 2: Paint/Cutout possuem seleção e aplicação explícita da Tools Database,
incluindo aviso de unidades e campos suportados. Paint aplica uma ferramenta e seus
parâmetros comuns, preservando seleção/ordem/Rest. Combo usa índice Python 4;
Laser Lines (3), padrões inválidos e pontas V são recusados nestes fluxos.
Cutout transfere diâmetro, margem, convexidade, padrão/tipo de gap e M-Bites;
preserva gaps manuais. Cut Z e Thin Depth continuam configurados na Geometry,
com aviso explícito. Testes de adaptadores e controles FX aprovados.

Etapa 1: Tools Database integrada a Geometry/CNC e Milling Excellon. Seleção não
altera valores até Aplicar; filtra Milling/General e valida diâmetro, Cut Z e ponta V.
Geometry com caminhos associados não aceita mudar a largura sem regenerar os caminhos.
Milling preserva perfil, ponta V e parâmetros básicos na Geometry criada. A unidade
da base não é convertida automaticamente; a interface avisa. Offset, dwell, Feed Z
e extra cut ainda não são transferidos nesta etapa. Parâmetros por ferramenta seguem
na etapa 3. Testes direcionados: MillingDatabaseTest, DatabaseTransferTest e
GeometryCncToolPanelTest aprovados.

## Geometry → CNC: compensação e posições — 2026-10-02

Tool Offset Path/In/Out/Custom por ferramenta, sem modificar a Geometry original.
Buffer mitrado compatível com a estratégia Python; linhas fechadas viram polígonos,
linhas abertas usam contorno de buffer positivo e compensações que eliminam um
elemento são recusadas. Custom zero exige Path. Todos os rascunhos são validados.

Painel recolhível com Start Z, End Z/XY e Tool change Z/XY comuns. Retração antes
de XY e pausa/troca, inclusive primeira troca com posição explícita; movimentação
final em altura segura antes de baixar ao End Z (diferença deliberada do Python).
Após M0/M6, G90 e altura de troca são reafirmados antes do próximo movimento XY.
Altura de troca não pode ser menor que o maior Travel Z. Movimentos de troca/fim
constam da prévia; corrigida também a prévia do retorno XY entre passes abertos.
Novos campos só em fresagem sem sonda; perfis incompatíveis recusam configurações
ativas em vez de ignorá-las. A sonda conserva seu painel próprio.

Persistência nativa retrocompatível; `.FlatPrj` guarda offset/valor e posições nos
campos CAM padrão. End XY comum do objeto tem prioridade sobre cópia antiga da
ferramenta, como no Python. Opções globais servem de fallback quando o dado por
ferramenta falta. Reimportação após reserialização Python conserva os novos valores.

Testes de núcleo, persistência e controles JavaFX aprovados. Serializadores/parser
Python validaram exemplo avançado (Geometry + CNC Job) e os 18 objetos do projeto
real autorizado, sem modificar o original. Conferência visual e teste a seco
permanecem pendentes. Limites e instruções em GEOMETRY_CNC.md.

Regressão final: install aprovado, 739 testes registrados, 728 aprovados,
11 ignorados, zero falhas/erros; propriedade opcional advancedResaved verificou
a reabertura do exemplo reserializado pelo Python. Tentativas anteriores tiveram
erros intermitentes ao excluir temporários Windows; ProjectFileIOTest agora limpa
somente seu diretório JUnit, com repetição limitada, fechando a enumeração antes
de apagar. Limpeza não foi desativada e erros persistentes continuam falhando.

## Tools Database: fechamento das transferências — 2026-10-02

Ponto 2: Milling/Geometry CNC recebem Offset/valor da base. Cutout recebe Cut Z,
Multi-Depth, Depth per pass e Thin Depth, com prioridade dos campos gerais como
no callback Python. Geometry principal e Thin recebem parâmetros CNC e perfil;
não reaplicam compensação aos caminhos Cutout. Thin exige profundidade menor.
Paint agora mantém Overlap, margem, método, Connect e Contour por diâmetro,
preserva rascunhos inválidos e aplica a todas somente por ação explícita.
Rest desconta a cobertura anterior da área individual de cada ferramenta.

Install completo aprovado: 743 testes registrados, 732 aprovados, 11 ignorados.
Repetida uma execução que falhou somente na limpeza de temporário Windows em
CncSettingsPersistenceTest, sem falha de asserção. Testes adicionais de adaptadores,
gerador e controles FX aprovados. Validação visual/física permanece pendente.
Limites em TOOLS_DATABASE_TRANSFER.md.

## Editor Geometry: Texto e Borracha — 2026-10-02

Ponto 4: Texto e Borracha habilitados na barra e no menu Geo Editor. Texto usa
contornos de fontes instaladas (Java/AWT), tamanho com escala MM/IN do ParseFont
Python, negrito/itálico e múltiplas linhas; mantém vazios das letras e associação
de ferramenta. Geração não altera o rascunho: prévia e clique confirmam inserção;
Esc/botão direito ou mudança de parâmetros/seleção/ferramenta cancelam a prévia.

Borracha usa as selecionadas como molde preenchido, como no Python. Origem e
destino deslocam a região de recorte, sem mover os originais. Recorta linhas e
áreas de todas as ferramentas, preserva associações e permite apagamento completo.
Formas não atingidas conservam IDs; sem alteração não cria undo. Cada ação é uma
transação undo/redo; cancelamento e falha não aplicam resultado parcial.

Texto, união do molde e recortes usam o executor existente fora da thread FX.
Corrigida a reabilitação de botões após trabalho/cancelamento. O FX encerra cada
gesto de borracha (Python permite destinos repetidos). Paint Shape e outros modos
avançados ainda faltam; não equivale a paridade total. Instruções/limites em
GEOMETRY_EDITOR.md. Contornos/métricas não são idênticos ao parser FreeType Python.

Install aprovado: 756 testes registrados, 745 aprovados, 11 ignorados, zero falhas
ou erros. Testes incluem cliques reais no plot sem janela, cancelamento, undo/redo,
10.000 formas e persistência nativa/legada com regeneração do CNC. Erro intermitente
de limpeza Windows em CncSettingsPersistenceTest motivou reutilizar a limpeza
limitada de ProjectFileIOTest via helper comum, sem desativar limpeza nem ocultar
falhas persistentes. Validação visual e teste na máquina continuam pendentes.

## Revisão dos tooltips após as implementações de paridade — 2026-10-02

Atualizadas as descrições de Tools Database para Offset/Custom Offset, Thin Depth
e parâmetros individuais do Paint, removendo avisos de transferência pendente que
já não correspondiam à implementação. Ajuda adicional no Editor Geometry (Texto,
Borracha, Buffer e operações de seleção), consistente entre barra e menu. Campos
de texto explicam limites, fontes instaladas, prévia, inserção e cancelamento.

Paint explica rascunhos por ferramenta, sincronização da lista e aplicação a todas.
Cutout distingue Free-form/Rectangular, Cut Z/Thin Depth e saídas separadas; gaps
manuais substituem o padrão automático. CNC detalha compensação, posições comuns,
perfis incompatíveis e limites da prévia de macros. Isolation Follow avisa que corta
sobre o cobre; NCC distingue margem externa de afastamento do cobre.

Reutilizado o padrão fluido com parágrafos, negrito e cores semânticas dos temas,
sem novos tooltips para Excluir/Fechar. Corrigida a prioridade de ajuda específica
em relação ao catálogo genérico, inclusive cópia para legendas de campos desabilitados
e atualização posterior do formulário. Ícones receberam identificação acessível.

Install completo aprovado: 762 testes registrados, 751 aprovados, 11 ignorados,
zero falhas/erros. Testes verificam controles reais, metadados, textos, prioridade,
consistência barra/menu e contraste dos temas; validação visual manual pendente.

## Etapa 1: Paint Shape no Editor Geometry — 2026-10-02

Paint acrescenta caminhos de preenchimento às áreas selecionadas, preservando
contornos e associação/diâmetro das ferramentas. Anéis fechados simples aceitos;
linhas abertas, diâmetro incompatível e resultados vazios/incompletos recusados.
Standard/Seed/Lines/Combo, margem, Connect/Contour e sobreposição. Cálculo usa o
executor existente, com cancelamento, prévia fixa e confirmação separada por botão
ou clique; Esc/botão direito cancela. Uma inserção = um undo. Mudança de parâmetros
cancela a prévia; revisão/seleção obsoletas impedem aplicar resultado antigo.
Sem associação de ferramenta, conferir o diâmetro ao gerar CNC. Não altera o
diâmetro global como o patch Python; não reclassifica caminhos anteriores.
Testes direcionados do núcleo e controles FX passaram; validação visual pendente.

## Etapa 2: Transformations Object/Buffer — 2026-10-02

Referência Object usa bounds do objeto escolhido e valida remoção/ausência de
geometria. Point não aceita mais texto inválido como zero. Buffer por distância
e percentual, cantos Rounded/mitrados, mesma unidade entre os objetos. Excellon
altera diâmetros (distância somada ao diâmetro, igual ao Python), não centros ou
extremos de slots. Geometry conserva associação/diâmetro; Gerber conserva Follow.
Buffer executado em worker, com publicação atômica, cancelamento e verificação
de fontes obsoletas. Resultados vazios/inválidos recusados. Limites de Gerber e
ausência de undo global documentados em TRANSFORMATIONS.md. Testes de núcleo e
controles aprovados; comparação visual manual pendente.

## Etapa 3: exclusões Geometry/CNC — 2026-10-02

Painel para desenhar/editar áreas Around/Over, ativação explícita com rascunhos
preservados, unidades e tooltips. Desvio usa grafo de visibilidade sobre união de
áreas ampliadas por raio + 0,1 mm equivalente. Over sobe antes de todo o deslocamento
para a maior altura necessária, conservando clearance. Cortes e origem/destino
dentro das áreas são recusados. Troca/fim e retornos de passes também roteados;
prévia XY representa desvios. Áreas/ativação persistem em `.fcnproj`. Exportação
`.FlatPrj` com áreas bloqueada: sem mapeamento seguro para armazenamento global
legado, não se descarta informação de segurança. Drilling permanece sem exclusões.
Testes de roteamento, geração, cancelamento, incompatibilidade de perfis, controles
e persistência passaram. Limites/validação física pendente em CNC_EXCLUSIONS.md.

## Etapa 4: comparação CAM real e preservação de detalhes — 2026-10-02

Harness Java exporta dez casos Isolation/NCC/Paint/Cutout e G-code. Script Python
decodifica independentemente o projeto original e usa rotinas reais do checkout,
incluindo AST do handler/helpers Cutout sem alterações de algoritmo. Relatório
HTML/SVG/JSON, pegada da ferramenta, comprimento/bounds e distância amostrada;
strict falha diante de divergência. Fonte legado tem SHA-256 registrado. Arquivos
privados, dependência de teste isolada e relatórios somente em target/; original
preservado, ambiente .venv continua Shapely 2.1.2.

Comparação revelou perda de passes por simplificação de entrada do Buffer JTS.
Standard usa epsilon Python e erosão sem simplificação; Seed/Lines/conectores
também preservam entalhes. NCC Standard passou de IoU 98,8652% para ~99,9992%.
Não é medida de paridade global nem ganho de FPS. Teste sintético cobre pequeno
entalhe em todos os métodos e o epsilon inicial. Código continua Java/JTS/worker.

Com Shapely 1.8.5.post1 em pasta isolada, seis casos atendem aos critérios, quatro
continuam diferentes (Standard, Seed, Paint e Cutout quatro gaps com margem);
zero exceções. Cobre importado idêntico em área; parser Python interpretou os dez
G-codes. Standard/Paint têm pequenas diferenças de caminhos e Seed ainda requer
comparação de centros/arcos. Cutout FX centra gaps no contorno; Python os desloca
pela margem. Revisar com Thin/M-Bites e verificar manutenção das bridges antes de
alterar isso. Shapely 2 não executa os casos multipart antigos: sem patches do
oráculo, o harness registra erro, não aprovação.

Install completo: 779 registrados, 768 aprovados, 11 opcionais ignorados, zero
falhas/erros. Seis testes Python do harness e cinco smoke tests CAM no projeto real
aprovados separadamente. Prévia detalhada atinge limite de tamanho em Isolation
3 passes/Seed, sem retirar limites para passar no teste. COMPARACAO_CAM.md descreve
comandos, critérios, resultados, pendências e distinção de testes headless/visuais.

## Continuação 1: Seed e diagnóstico das divergências — 2026-10-02

Seed encerra os anéis no primeiro resultado vazio, como clear_polygon2; antes
o FX voltava a atingir ilhas distantes após um intervalo vazio. Teste sintético
verifica esse término e a independência de Contour, que ainda visita todos os
componentes erodidos. Isso não garante preenchimento completo pelo método Seed
legado nem resolve todas as diferenças de centros/arcos do projeto real.

O harness ganhou --cases, WKT completo dos dois resultados e distanceWitness
(lado, ponto de maior distância amostrada e contraparte mais próxima). Sete
testes Python do harness passaram. Critérios numéricos não foram relaxados.
Standard/Paint ainda divergem após erosões sucessivas: investigar a diferença
geométrica entre kernels, sem afirmar causa definitiva ou mascarar com IoU.
Arquivos privados somente em target/. Nenhuma modificação do oráculo Python.

## Continuação 2: Cutout retangular, Thin e M-Bites — 2026-10-02

Posições retangulares usam centro da origem + margem e quartos baseados na
dimensão original + duas margens, sem raio da fresa no espaçamento. Thin e
M-Bites compartilham essa referência. Bandas atravessam o contorno inteiro com
margem alta; se o padrão perder pontes ou consumir o caminho, geração recusada.
Thin é extraído diretamente das máscaras, eliminando segmentos espúrios por
roundoff de diferenças entre contornos recortados em cantos arredondados.
Parâmetros não finitos/modos nulos recusados; tooltips ricos/acessíveis explicam
posicionamento, largura, modos e limitações. Gaps manuais permanecem substitutos
do padrão. Free-form e gap zero ainda têm diferenças explícitas em CUTOUT.md.

Comparação completa com o projeto privado decodificado independentemente e
Shapely 1.8.5.post1 isolado: sete MATCH_SAMPLED, três DIFFERENT, zero erros do
oráculo. Cutout quatro gaps/margem 1 mm passa: maior distância amostrada
0,00177 mm (antes 1 mm), IoU 99,99329% (antes 96,96954%). NCC Standard, Seed e
Paint Standard continuam divergentes; strict retorna 1. Cobre igual em área e
dez G-codes interpretados pelo Python. Não é paridade exata/global, benchmark
de desempenho ou validação física. Sete testes Python do harness passaram.
Cinco smoke tests CAM com o projeto real passaram separadamente após o install.

Próximos passos: investigar buffers Standard/Paint e centros/arcos Seed,
comparar Free-form/Thin/M-Bites com o Python, ampliar Rest/Connect/referências;
resolver separadamente a limpeza TempDir Windows. Suíte funcional conforme a
ressalva da seção 3; validação visual manual permanece pendente.

## Inicializadores: avisos Java 25 — 2026-10-02

O log enviado pelo usuário mostrava startup/stop normais e D3D na GTX 1650,
não falha do aplicativo. Wrapper atualizado de Maven 3.9.9 para 3.9.12, com
Guava 33.5.0-jre, removendo o uso legado de UnsafeAtomicHelper no build.
`.mvn/jvm.config` habilita native access para Jansi no classpath da JVM Maven;
o launcher C++ e o javafx-maven-plugin habilitam apenas `javafx.graphics` na
JVM da aplicação. Não há supressão global de Unsafe ou alteração das variáveis
de ambiente do usuário. Critérios de GPU e fallback SW permanecem iguais.

LauncherProbe inicializa JavaFX e verifica um pixel em Canvas 16×16, sem janela,
projeto ou preferências. O --probe nativo usa illegal-native-access=deny para
detectar ausência de permissão; build-native.cmd executa esse teste. Smoke de
renderização D3D (GTX 1650), SW forçado e iniciador Java/Maven passaram sem os
avisos do anexo. As mensagens Prism/VRAM/shaders de --verbose-gpu são mantidas.
NATIVE_GPU.md documenta as permissões e comandos.

A execução normal de testes ainda apresentou DirectoryNotEmptyException ao
limpar TempDir (agora em ExcellonExporterTest), sem falha de asserção; não foi
atribuída relação aos avisos de inicialização nem declarado conserto desse
problema separado. A configuração permanente de limpeza não foi alterada.
Regressão funcional com limpeza TempDir desativada: 787 registrados, 776
aprovados, 11 opcionais ignorados, zero falhas/erros. Essa opção foi usada
somente na invocação de teste, não nos scripts de execução. Controle negativo
com JavaFX sem permissão nativa e política deny foi recusado (exit 1), como
esperado: o probe realmente detecta a configuração faltante.

## Plano de paridade e estabilização de testes — 2026-10-03

PLANO_PARIDADE.md registra sete etapas priorizadas, critérios de conclusão e
limites da evidência; documento inicial commitado em 5157740e. Infraestrutura
vem primeiro, seguida da investigação Standard/Paint/Seed; demais etapas cobrem
corpus CAM, Drilling/CNC, projetos, editores/UX e preferências/automação.

JUnit centralizado via BOM 6.1.3 e Surefire 3.5.4. Novo flatcam-test-support é
dependência exclusivamente test dos três módulos do aplicativo. A SPI pública
de remoção TempDir permite delegar a limpeza recursiva/links ao JUnit e repetir
apenas DirectoryNotEmptyException em diretórios Windows já vazios, dentro da
raiz exata fornecida. Máximo seis tentativas adicionais/375 ms por diretório;
sem repetir recursivamente conteúdo novo, suprimir falhas ou forçar GC.
Erros de arquivo/acesso e falhas persistentes continuam fazendo o teste falhar.
Helper antigo em dois testes foi substituído pela estratégia compartilhada.
Permissão nativa ALL-UNNAMED limitada à JVM Surefire FX, que usa classpath;
permissão específica javafx.graphics do launcher permanece inalterada.

Vinte testes novos verificam limite/recuperação/interrupção/escopo, cem ciclos
internos com recursos fechados, falha de asserção preservada, política NEVER
explícita e cleanup real recusado com handle NOSHARE_DELETE. Junctions reais
Windows preservam o destino fora da raiz; controles limpam seus próprios
artefatos, sem varrer temporários antigos ou remover projetos do usuário.

Três execuções finais consecutivas da suíte normal aprovadas com limpeza ativa:
807 registrados, 796 aprovados, 11 opcionais ignorados, zero falhas/erros.
Install normal também passou. Sete auxiliares Python e probe Java/Maven passaram;
árvore runtime FX sem suporte/JUnit confirmada. Nenhum algoritmo CAM ou critério
numérico foi alterado, comparação completa privada não foi reexecutada nesta
etapa e suas três divergências permanecem pendentes. Causa exata da falha
histórica Windows não comprovada; resultados repetidos locais não garantem
ausência global de falhas intermitentes. TESTES.md documenta comandos, contratos
e limites, incluindo necessidade de validação em outros sistemas/CI.

## Investigação Standard/Paint/Seed — 2026-10-03

INVESTIGACAO_CAM.md registra experimentos controlados sobre JTS 1.20.0 e
GEOS 3.10.3/Shapely 1.8.5.post1 isolados. Standard/Paint diferem nas heurísticas
de separação de offsets, seleção do endpoint e simplificador de entrada.
Reproduzir apenas parte dessas regras não basta; protótipo Java com regras
legadas alinhadas passa ambos os casos reais mantendo os critérios existentes.
Foi compilado exclusivamente em target/, sem modificar classes/JARs de produção
ou o algoritmo Python. Não é correção distribuída nem prova das outras ferramentas.

Seed fica caracterizado como sensível à entrada: losango sintético com fronteira
alterada ~7e-13 mm muda o ponto interior ~2 mm em ambos os kernels. No projeto
real, três regiões mudam de ponto por mudança do intervalo da scan-line, apesar
de fronteiras seguras extremamente próximas. Protótipo Seed coincide sobre a
mesma entrada, mas continua DIFFERENT com projeto decodificado independentemente.
Isso não justifica arredondar o oráculo, aumentar tolerâncias ou declarar paridade
por cobertura. Standard/Paint alinhados aumentam vértices e atingem o limite de
prévia detalhada no protótipo; limite preservado, desempenho não medido.

Probes públicos em tools/CamKernelProbe.java e compare_cam_kernel_probe.py
reproduzem três Standard, 36 Seed e o par de losangos, sem dados privados.
Harness principal passou a registrar GEOS; 16 testes auxiliares Python aprovados
em Shapely 2.1.2 e 1.8.5.post1. Testes normais CAM/suporte aprovados; não foi
reexecutado/recontado o reactor completo nesta investigação. Comparação dos três
casos da aplicação distribuída mantém DIFFERENT e strict=1. Nenhum algoritmo
CAM/CNC, preferência ou projeto do usuário foi alterado. Relatórios privados
em target/cam-investigation; não foram incluídos no Git. Próximo passo: buffer
de compatibilidade isolado para Standard/Paint e validação do custo/da prévia,
seguido de política explícita para o ponto inicial Seed. Backend nativo não é
requisito demonstrado para essa correção.

## Revisão das outras sessões e correções — 2026-10-05

Base desta sessão: `flatcam-next`, HEAD `5fa4b871`, inicialmente limpa.
Revisados os 36 commits desde `87d3b752`: buffer de compatibilidade
Standard/Paint, Seed deliberadamente distinto, controles CNC/Geometry,
Terminal reduzido e seus 13 comandos. A revisão isolou defeitos no host real
que os testes apenas de flags não encontravam.

Corrigidos: profundidade Tcl `cncjob` negativa convertida para a profundidade
interna positiva; nomes novos globalmente únicos e recusa de nomes ambíguos;
Terminal em worker com progresso/cancelamento/entrada serializada; publicação
FX validando origem/projeto e referência NCC. Exportação de arquivo usa
temporário e substituição após cancelamento verificado. Saída visual do
Terminal conserva no máximo os últimos 200.000 caracteres.

Seed tinha grade inicial proporcional à razão largura/altura, sem limite ou
cancelamento. Agora ela tem até 256 células e construção cancelável; mantém
20.000 células processadas por polígono, retornando o melhor candidato interior
quando o orçamento se esgota. Índices inteiros evitam incremento flutuante
sem progresso. Não poligonais não recursam em si mesmos. Precisão finita e
positiva é obrigatória. A continuidade do ponto escolhido NÃO é garantida;
a diferença deliberada frente ao Python permanece.

`mvnw.cmd -q install` aprovado: **939 registrados, 928 aprovados,
11 opcionais ignorados**, zero falhas/erros, TempDir normal ativo.
602 CAM + 114 application + 203 FX + 20 test-support. Probes do launcher
existente `--probe` e `--probe --software` aprovados com as classes atuais.
Os 23 testes líquidos novos cobrem G-code real, colisões entre tipos,
importações/CAM, origem alterada/removida, troca de projeto, cancelamento
da fila FX, UI responsiva, loops/parse canceláveis e Seed em geometrias estreitas.
Não foram alterados preferências, projetos privados nem o oráculo Python;
a comparação privada não foi reexecutada. Validação manual permanece pendente.

Antes de ampliar o Terminal, testar os fluxos com projetos densos. Próxima
implementação sugerida: `open_project` e transformações Tcl, preservando
worker/FX, cancelamento e testes do host real. Os limites do dialeto e das
flags continuam em `PLANO_PARIDADE.md`; não declarar paridade Tcl completa.

## Progresso com console recolhido — 2026-10-05

Atendendo às imagens do console inferior (não à aba central Tcl), o progresso
das operações da janela principal continua visível quando esse painel é
recolhido. `CompactJobProgress` espelha a fração real da barra existente,
com largura preferida de 128 px, porcentagem sobreposta e botão Cancelar ao
lado. Fica à esquerda da infobar, junto da mensagem de status, sem deslocar
os controles do Plot para outra linha. Ao reabrir o console, a versão compacta
sai do layout e reaparece a barra longa, também com Cancelar ao lado.

Ambos os botões solicitam cancelamento do mesmo job. Ficam ocultos quando
inativos ou quando a operação não é cancelável (por exemplo, publicação de
arquivo). O último percentual continua visível após concluir, inclusive 100%.
Fases sem fração conhecida permanecem indeterminadas (`...`), sem porcentagem
inventada. Não houve alteração de algoritmos CAM ou cálculo de progresso.
O progresso independente da aba Tcl não foi misturado com o job principal.

`CompactJobProgressTest`: sete casos, incluindo quatro temas, sincronização,
cancelamento real pelo handle, limite visual de tamanho e alternância sem
perder a fração. Capturas offscreen dos quatro temas inspecionadas. Install
completo: **946 registrados, 935 aprovados, 11 opcionais ignorados**, zero
falhas/erros e limpeza normal ativa. Sem validação manual de importação pesada
nesta entrega; iniciar o app e recolher/reabrir o console durante uma operação.

Ajuste visual seguinte: o quadrado de Cancelar usava um Group escalado cujos
bounds de layout não refletiam o tamanho desenhado; ficou 6 px fora do centro.
`Icons.cancel` usa um X e um wrapper que mede os bounds já escalados. Botões
compacto/expandido compartilham `job-cancel-button` (22×20 px), borda leve,
hover/pressionado/foco e cores do tema. Os testes agora verificam também o
centro real do desenho nos quatro temas e no botão expandido, além dos estados
interativos. Sem alterar o helper global de outros ícones.

## Transições dos painéis — 2026-10-05

`AnimatedSplitPanel` anima o divisor do painel lateral Projeto/Propriedades/
Ferramenta e o do console inferior durante 180 ms, com aceleração/desaceleração.
Não se refere à aba central Tcl. Durante a transição, um clip limita o desenho
ao espaço disponível e o mínimo do eixo é temporariamente zero. Ao recolher,
o painel é removido do SplitPane; ao terminar, mínimos, clip e comportamento
do mouse originais são restaurados. Cliques rápidos invertem a partir da posição
atual; callbacks de animações interrompidas não encerram a nova transição.

Posições intermediárias não são gravadas nas preferências. A largura salva por
monitor e a altura expandida do console permanecem como destinos. Troca de
monitor conclui a transição usando o destino atualizado. A identificação do
divisor usa o SplitPane ancestral mais próximo, sem confundir divisores dos
painéis aninhados. Listeners do console não se duplicam ao inverter a animação.
A barra compacta permanece disponível durante a abertura do console.

`AnimatedSplitPanelTest`: sete casos cobrem posições intermediárias, reversão,
repetição, início recolhido, restauração de restrições, destino atualizado,
proteção da persistência e execução de eventos FX durante a Timeline real.
Install completo: **953 registrados, 942 aprovados, 11 opcionais ignorados**,
zero falhas/erros, limpeza normal ativa. Validar manualmente com projeto denso,
cliques rápidos e troca de monitor. Não foi medida fluidez de projeto privado
nem alterado o renderer/CAM; a animação continua dependendo do custo de layout
e desenho por pulse.

## Seleção de tabelas sem foco — 2026-10-05

Após o commit `900bda8a` dos painéis/progresso, foi confirmado que Isolation,
NCC e outros painéis selecionam a primeira ferramenta ao abrir. A seleção é
funcional e foi preservada; o defeito era visual. No tema clássico, o fundo
inativo herdado do Modena ficava quase branco mesmo no escuro. O Ice já tinha
estilo de seleção em painéis, mas não diferenciava foco da tabela.

Os dois arquivos de componentes agora aplicam regras comuns para TableView,
inclusive fora dos painéis: sem foco, destaque discreto usando a paleta de
seleção inativa existente; com foco, cor forte `-fc-selection-row`, com texto
contrastante. O foco vem da tabela, não do índice do FocusModel da linha.
Valores de ComboBox embutidos conservam a cor de texto da própria entrada.
Células de totais mantêm a cor de destaque original após desselecionar.
Não foram alteradas seleção inicial, geração CAM ou preferências.

`TableSelectionThemeTest`: oito casos parametrizados nos quatro temas,
cada um verificando tabelas genéricas, tool-panel e object-panel. Cobrem
seleção inicial, cores computadas da linha/célula/Text, contraste mínimo 4,5:1,
foco CSS, hover, seleção múltipla, desseleção, troca de tema e ComboBox.
O teste reproduziu o fundo incorreto antes do ajuste; capturas offscreen dos
quatro temas foram inspecionadas após a correção. Install completo aprovado:
**961 registrados, 950 aprovados, 11 opcionais ignorados**, zero falhas/erros.
O foco real por clique e navegação entre painéis ainda precisa de teste manual.

## Colunas e conteúdo das tabelas de ferramentas — 2026-10-05

`CompactToolsTable` padroniza as tabelas de três colunas de Isolation, NCC e
Geometry→CNC: # fixo em 32 px, TT entre 72 e 96 px (preferência 84), diâmetro
flexível com mínimo 64 px. Segue a disposição do Python em ToolIsolation/
ToolNCC: índice fixo, diâmetro esticado, tipo compacto. A coluna TT não toma
metade da tabela nem cria uma coluna vazia ao lado. Valores/seletores ficam
centralizados sob os cabeçalhos nos quatro temas. Linhas de 28 px e seletores
de 24 px mantêm alinhamento vertical; padding interno permite ler C1-C4 sem
reticências nas larguras verificadas. Geometry→CNC reserva 40 px de cabeçalho/
borda na altura, evitando scrollbar vertical desnecessária com poucos itens.

`CompactToolsTableTest`: quatro casos (um por tema), usando as tabelas reais
dos três painéis e ciclos entre 180/240/320/560 px. Verificam limites das
colunas, preenchimento do viewport sem coluna vazia, ausência de rolagem
horizontal nessas condições, alinhamento, tamanho/valor renderizado do
ComboBox, preservação dos itens/seleção e mudança C1→C2 no modelo real.
Capturas fora da tela foram inspecionadas nos quatro temas. Install aprovado:
**965 registrados, 954 aprovados, 11 opcionais ignorados**, zero falhas/erros.
Não houve alteração de parâmetros CAM ou das outras tabelas, como aperturas e
Drills/Slots. Validação manual no painel completo permanece pendente; abaixo
dos mínimos somados ou com muitos itens, os limites do viewport continuam
valendo, sem garantia de eliminar toda rolagem em qualquer largura.

## Alternância do Snap — 2026-10-05

As tabelas foram commitadas em `2bc6ffb4`. Na investigação seguinte, o usuário
relatou que o botão mudava de estado, mas o Plot continuava preso à grade.
O teste básico de alternar com passos válidos já liberava as coordenadas reais;
não foi reproduzido um bloqueio persistente de novos pontos nesse caminho.
Foram reproduzidos dois defeitos relacionados: HUD/prévia não atualizados até
o próximo movimento do mouse, e clique desfeito se o texto de passo era inválido.

`setGridSnap` agora recalcula coordenadas, posição da prévia e cruz no último
cursor dentro do Plot, imediatamente. O botão mantém o estado escolhido mesmo
com passo inválido: restaura os últimos valores válidos, aplica/salva o estado
e avisa no feedback. Não oculta a grade visual, que é uma preferência separada.
Pontos/âncoras já confirmados não são reposicionados ao alternar.

`PlotStatusControlsTest`: onze casos com salvamento injetado, sem alterar as
preferências reais. Cobrem botão/rota de menu-G, passos X/Y independentes,
vazios/zero/negativos/NaN/infinito/texto (vinculados e não vinculados), HUD,
cruz transparente, prévia de área e movimento de objeto, deslocamento confirmado
sem arredondamento e trilha do editor sem pontos de dobra após desligar Snap.
Eventos MouseEvent exercitam o Plot real; G usa a mesma rota toggleGrid, mas
não houve automação de teclado/clique no app completo.

Install completo: **976 registrados, 965 aprovados, 11 opcionais ignorados**,
zero falhas/erros, limpeza normal ativa. Reabrir o app e validar o caso exato
do usuário; a correção imediata não prova a causa de toda captura persistente
possível. Não houve alteração de geometria CAM, formato de projeto ou prefs
do usuário durante os testes.

## Renomeação explícita na árvore — 2026-10-05

Snap commitado em `fdc1d200`. O comportamento relatado depois foi confirmado:
Modena/TreeCellBehavior pede TreeView.edit(item) também com um clique simples
numa linha já selecionada, não apenas com duplo clique. A proteção anterior
contra duplo clique não cobria esse caso.

A TreeView da janela agora permite iniciar edit(item) somente durante uma
solicitação explícita de F2 ou do menu Renomear (`requestTreeRename`). O alvo
permitido é removido em finally. edit(null) continua permitido para cancelamento
e término normal. Não se consomem cliques simples, preservando Ctrl/Shift e
seleção múltipla; duplo clique continua abrindo Propriedades. F2 é consumido
depois de executar o comando para evitar repetir a ação no comportamento nativo.
Validação de nome vazio/duplicado e o campo Name nas propriedades não mudaram.

`ProjectTreeRenameTest`: sete casos usando a árvore/células/painéis reais da
MainWindow fora da tela. Cobrem cliques repetidos, seleção Ctrl/Shift, duplo
clique para Propriedades, F2 com commit, comando do menu na fila FX, Esc,
categorias e nomes inválidos. O mesmo clique inicia edição quando a proteção
é temporariamente liberada no teste, confirmando o gatilho nativo. Os testes
não gravam projetos ou preferências. Install completo: **983 registrados,
972 aprovados, 11 opcionais ignorados**, zero falhas/erros, limpeza normal ativa.
Reabrir o app para validação manual do clique físico e foco do editor.

## Reinvestigação do Snap pelo mouse — 2026-10-05

O usuário informou que alternar pelo botão ainda não funciona, com capturas
em passo 0,1. A captura desativada tem o mouse fora do Plot, portanto não
comprova o estado do cursor ao retornar. Não presumir que o relato foi resolvido.
`PlotSnapMouseTest` adiciona verificação opt-in com Robot numa janela temporária:
cliques físicos, foco saindo do campo de passo e retorno ao Plot, três ciclos
por passo (0,1 e 5,0). Os dois casos e os onze testes anteriores passaram;
o estado, as coordenadas e a cruz acompanharam o botão. Não houve outra
alteração de execução do Snap nem de preferências reais nesta investigação.
As linhas da grade são independentes do Snap e usam espaçamento visual adaptativo;
em 0,1, o arredondamento pode ser menor que um pixel. Ainda esclarecer se o
relato envolve cruz persistente, movimento/desenho preso ou somente as linhas.
Comando e limites do teste em `TESTES.md`.

Antes do commit solicitado, `mvnw.cmd -q install` completo passou novamente:
**984 registrados, 972 aprovados, 12 opcionais ignorados**, zero falhas/erros.
O teste Robot fica desabilitado por padrão e é registrado como um caso ignorado;
quando habilitado, executa os dois passos parametrizados. O commit reúne a
renomeação explícita da árvore, suas regressões e a investigação opcional do
Snap; não declara resolvido o relato persistente.

## Diagnósticos locais e watchdog FX — 2026-10-05

`FlatCamLauncher` é o entry point Java/Maven e nativo, iniciando diagnósticos
antes de Application.launch. `MainApp` inicia/encerra só o monitor UI. Uma pasta
única por execução fica em LOCALAPPDATA/FlatCAMFX/diagnostics no Windows, com
override por FLATCAM_FX_DIAGNOSTICS_DIR. Nada é enviado nem projetos/prefs alterados.

`DiagnosticSession` captura System.Logger/JUL e stdout/stderr em logs rotativos
(quatro de aproximadamente 2 MiB por conjunto), amostra versão/build/OS/CPU/RAM,
inicia JFR default com retenção de 10 minutos/64 MiB e salva checkpoint a cada
60 segundos/incidente. Exceções não tratadas preservam o handler anterior. Um
worker separado grava até cinco incidentes, com fila pendente limitada a dois;
dumps de até 256 threads de plataforma/64 frames. `UiWatchdog` envia no máximo
um callback pendente, reporta atraso de 5–6 segundos uma vez por interrupção e
permite novo incidente depois da recuperação. Não se declara todo atraso deadlock.

Launcher nativo configura ErrorFile e repositório JFR antes de criar a JVM e
registra os adaptadores Windows; Java/Maven configura o hs_err em flatcam-fx/target.
`flatcam.diagnostics.heapDump=true` habilita dump do heap por HotSpot MXBean;
permanece opt-in, sem quota e potencialmente sensível. `--no-diagnostics` e
FLATCAM_FX_DIAGNOSTICS=false desativam a instrumentação do app. Sessões antigas
não são apagadas automaticamente. Menu Ajuda > Diagnosticos abre pasta ou solicita
captura manual sem fazer a coleta pesada na thread FX.

`JobExecutor` agora completa excepcionalmente e registra um Error que escape do
job, antes de relançá-lo: antes o FutureTask absorvia a falha e deixava a
completion pública pendente indefinidamente. Não há alteração dos algoritmos CAM.

Dezenove regressões novas: dez DiagnosticSessionTest, seis UiWatchdogTest, duas
DiagnosticsFxTest (exceção de callback e bloqueio real com captura independente)
e uma JobExecutorTest. Cobrem rotação/UTF-8, restauração/locks, limites, JFR
legível, shutdown hook em JVM filha e retorno da FX depois da falha controlada.
Install completo: **1003 registrados, 991 aprovados, 12 opcionais ignorados**,
zero falhas/erros. Probes diagnósticos Java/Maven e nativo passaram; heap dump
opt-in foi configurado/verificado sem induzir OOME. Probes de renderização
D3D→SW/software passaram. Nenhum crash fatal nativo foi induzido. Build nativo
padrão passou após o usuário fechar o app que inicialmente bloqueava seus JARs.

Guia, localização, limites, privacidade e comandos: `DIAGNOSTICOS.md`. Ainda
validar manualmente os dois comandos do menu e overhead/fluidez no projeto real.
O relato persistente do Snap permanece não reproduzido; esta entrega não o resolve.

## Ajuda > Sobre — 2026-10-05

Diagnósticos commitados em `330dba47`. Sobre era apenas uma mensagem no console;
agora `AboutDialog` abre um diálogo redimensionável/modal vinculado à janela.
Mantém a estrutura do `app_Main.py:on_about`: apresentação, programadores,
tradutores, licença e atribuições. Acrescenta Sistema e botão Copiar informações,
que não fecha o diálogo; Fechar/Esc continuam disponíveis. Logo original tem
contorno claro no escuro; textos quebram linha sem reticências verticais.

Os 33 programadores e oito idiomas/tradutores/corretores/contatos históricos
foram preservados em TSV UTF-8. Créditos são explicitamente do Python, não
autorias inventadas do port ou promessa de idiomas disponíveis no FX. A licença
é copiada pelo build diretamente do LICENSE raiz, incluindo copyright. Links
históricos são rotulados Python e só abrem por ação explícita no navegador;
não foram verificados online. Não há WebView, novas dependências ou rede ao abrir.
Versão/data de build vêm do recurso Maven filtrado; Java/OS/arquitetura/CPU/heap
são dados locais reais. A referência Python 8.994 BETA (2020/11/7) fica separada.

`AboutInfoTest`: quatro casos, incluindo comparação dos nomes/ordem contra o
fonte Python e licença contra o arquivo raiz. `AboutDialogTest`: quatro temas,
todas as abas em larguras 360/480/700, logo/estilos/cópia/links com callbacks
injetados e um caso com diálogo mostrado verificando copiar sem fechar/fechar.
Capturas de apresentação/créditos nos quatro temas foram inspecionadas.
Install completo: **1012 registrados, 1000 aprovados, 12 opcionais ignorados**,
zero falhas/erros. Clipboard/navegador reais não foram acionados nos testes;
validar manualmente pelo menu. Implementação de Sobre pronta e validada.

### Sistema: hardware e renderizador ativo — 2026-10-05

`SystemHardwareInfo` acrescenta modelo de CPU (registro Windows somente leitura;
/proc/cpuinfo no Linux), processadores lógicos disponíveis ao Java, RAM física
total/disponível, heap usado/reservado/máximo e threads Java de plataforma.
Consultas rodam num daemon; o registro tem timeout de três segundos e saída
limitada. Não depende de PowerShell, WMI, rede nem biblioteca extra. RAM/heap
ficam explicitamente separados; valores indisponíveis não são apresentados como zero.

`GraphicsRuntimeInfo` isola reflexão opcional sobre Prism 25.0.4. A thread FX
captura o ordinal do monitor da janela principal; um RenderJob lê o pipeline
instalado e a factory já inicializada, sem criar dispositivo ou alterar escolha
de GPU. Em D3D consulta descrição/driver do adaptador correspondente. SW mostra
compatibilidade por software/CPU, sem atribuir a GPU instalada à renderização.
Offscreen resolve a factory padrão por identidade, não presume adaptador zero.
Outros backends têm nome do pipeline, sem identificação da GPU implementada.

Launcher nativo e Maven têm cinco exports específicos e um opens D3D (somente
Windows no Maven). Internals podem mudar: falta de acesso, factory ausente ou
timeout produz informação indisponível, não impede o diálogo. Revalidar ao
atualizar JavaFX. Dados são uma amostra ao abrir Sobre; não medem uso/VRAM da GPU.
Consultas não bloqueiam a FX, com timeout total e descarte de atualização de
diálogo já fechado. Copiar usa o texto exibido. GPU vem antes da RAM para ficar
visível no tamanho inicial; detalhes completos podem ser rolados/copiados.

Onze regressões adicionais: parsing/unidades/valores reais, seleção de adaptador
sem inferência, janela proprietária real, informação/cópia assíncrona e JVM
separada forçada a SW. Install completo: **1023 registrados, 1011 aprovados,
12 opcionais ignorados**, sem falhas/erros, limpeza normal. Probes nativo D3D,
nativo SW e Maven modular passaram; D3D identificou Intel Arc, driver
igd9trinity64.dll 32.0.101.8991. Testes não induzem crash nem alteram projetos.
Validar manualmente a aba pelo menu e reabrir após mover o app entre monitores
(o teste de owner real usa apenas um monitor).

## Apresentação das áreas de código — 2026-10-05

Sobre/hardware commitados em `1b309154` na branch `flatcam-next`. Esta entrega
posterior troca TextArea no Editor G-code e nas views Ver Fonte (Gerber,
Excellon, Geometry/WKT, CNC) por `CodeEditor`, com RichTextFX **0.11.7** e
VirtualizedScrollPane. Essa versão é necessária para JavaFX 25:
https://github.com/FXMisc/RichTextFX#requirements.

Há gutter numerado, fonte monoespaçada, linha atual, barra com linguagem e
modo rascunho/somente leitura, Ln/Col/total de linhas e sintaxe colorida nos
quatro temas, incluindo troca de tema com editor aberto. Ctrl+F abre busca
literal case-sensitive; Enter/F3 e Shift+Enter/Shift+F3 percorrem ocorrências
com retorno ao início/fim; Esc fecha busca. Menu de contexto oferece edição,
cópia/seleção e busca, respeitando leitura/busy. Atalhos de texto são reconhecidos
pelo filtro da janela para não acionar mover/Snap/remoção do Plot.

Leitura de arquivos e geração de WKT rodam no worker. Documentos com pelo menos
200 mil caracteres são preparados fora da FX; a publicação do modelo ocorre
na FX. WKT gerado quebra linha após vírgulas para não desenhar um único parágrafo
gigante; a geometria equivalente foi testada. Quebras CRLF/CR são normalizadas
para LF na apresentação/edição. Fonte de Gerber/Excellon não é regravada.

Sintaxe roda em worker após debounce de 80 ms, por viewport (até 120 linhas /
aproximadamente 64 Ki caracteres por lote); linhas acima de 8192 caracteres
não recebem segmentação sintática. Filas têm um slot, resultados antigos de
conteúdo/viewport/busca são descartados e close encerra workers/subscription.
Arquivos continuam inteiros em memória; não é editor streaming e não garante
fluidez para tamanhos arbitrários. Coloração é lexical, não validação semântica.

Aplicar/Salvar arquivo/Cancelar mantêm os contratos existentes; não se grava o
original automaticamente. Ajuda longa da lateral fica em seção recolhível,
com aviso de segurança visível. Textos de QR/Geometry, console e aba Sobre
continuam controles de texto comuns: não são edição de código.

Avisos BSD 2-Clause completos de RichTextFX, ReactFX, UndoFX, Flowless e
WellBehavedFX são empacotados em about/editor-licenses.txt, acessíveis na aba
Licença do Sobre; LICENSE principal do repositório não foi alterado.

18 regressões novas cobrem léxico, temas/cores/troca, gutter/cursor, atalhos,
busca, undo/redo, rascunho/busy/falha/aplicar/salvar/cancelar, documento de 40 mil
linhas, cargas obsoletas, erro de leitura e equivalência WKT. Snapshots dos quatro
temas inspecionados; warnings de CSS do gutter foram eliminados usando Text
em vez de Label em células de medição destacadas do Flowless. A dependência
JavaFX no classpath em Surefire ainda produz o aviso conhecido de unnamed module.

Validar manualmente com Cobre_Morto_Bottom_cnc e os arquivos densos reais:
rolagem, seleção, digitação, Ctrl+Z/Y, busca, menu e alternância de temas.
Entrega do editor autorizada para commit pelo usuário em 2026-10-05.

Install completo da entrega: **1041 registrados, 1029 aprovados, 12 opcionais
ignorados**, zero falhas/erros. Probes do launcher com CodeEditor passaram em
D3D e software; também passou o caminho modular Maven. O app do usuário foi
reaberto enquanto os testes finais continuavam; reiniciar para carregar as
últimas classes/recursos. Não foi fechado automaticamente nem foram removidos
JARs de dependências em uso; as cinco bibliotecas novas já estavam copiadas
pelo build do usuário, e os probes utilizaram esse runtime atualizado.

## Projetos e transformações pelo Terminal — 2026-10-05

Entrega sobre `94ad07b1`, branch `flatcam-next`, autorizada para commit pelo usuário em 2026-10-05.
`open_project`, `offset`, `scale`, `mirror` e `skew` foram adicionados: agora
são 18 famílias FlatCAM, além dos aliases e comandos internos. Ajuda disponível
por `help nome_do_comando`; roteiro e diferenças em [TERMINAL_TCL.md](TERMINAL_TCL.md).

O fluxo do menu foi extraído para `loadProject` (worker) e `restoreProject` (FX),
compartilhados com o Terminal. O script espera a publicação e pode continuar sem
se autocancelar. Falhas/cancelamento antes da publicação preservam a sessão;
rascunhos, outro job, alterações de identidade/nome/quantidade, aparência e
configurações durante a carga bloqueiam a troca. Cores são validadas antes de
limpar o projeto. O original não é regravado; Tcl não muda a preferência de
último diretório. Callback do menu agora trata erros de publicação e libera o job.
Não há rollback geral para falhas inesperadas depois de começar a restauração FX.

Transformações preparam versões novas fora da FX e verificam origem/referência/
epoch antes de substituir as entradas. Atualizam sólido/follow/formas/aberturas,
furos/slots e caminhos por ferramenta; mantêm nomes/parâmetros/aparência. CNC
Jobs são recusados; histórico de Mover é limpo. Sem novo undo Tcl. Identidades
(offset/skew 0, scale 1) não recalculam nem trocam versões.

Diferenças deliberadas: sem eval em coordenadas; scale mantém eixo omitido com
fator 1 e recusa zero; mirror sem referência usa (0,0); skew limita ângulos a
(-90,90). Eixo X de mirror reflete Y, e vice-versa, como o comando Python, não
como o nome da operação interna de UI. Validação recusa resultados não finitos.
Limites já existentes das dimensões das aberturas não foram removidos.

22 regressões adicionais entre argumentos e MainWindow real. Abriram fixtures
nativo e Python comprimido com quatro tipos, preservaram arquivo fonte, cores,
follow, ferramentas/defaults, provaram continuação do script e FX responsiva,
cancelamento, rascunhos, erros e descarte de resultados atrasados. Install completo:
**1063 registrados, 1051 aprovados, 12 opcionais ignorados**, zero falhas/erros,
limpeza normal ativa (602 CAM, 115 application, 326 FX, 20 test-support).
Probes nativos D3D e software aprovados. Não houve teste físico CNC nem benchmark
ou reexecução da comparação privada Python; validação manual segue necessária.

Cancelamento continua cooperativo: decode JSON/XZ e uma transformação JTS
individual não são interrompidos no meio; publicação da árvore/Plot é FX em lote.
O percentual da abertura é por fase, não medição byte a byte do decode. Comandos
já publicados não são desfeitos por cancelar um script. Próxima fatia recomendada:
`save_project` e controle de plot/seleção pelo Terminal, sem ampliar o dialeto
silenciosamente nem declarar paridade de automação completa.

## Correções de fluidez após diagnóstico na GTX 1650 — 2026-10-05

Fontes do editor Geometry carregadas lazy/worker; índices de display grandes
em worker com cancelamento/reuso de partes imutáveis; fila de densidade limitada
por camada, descarte de publicações antigas, PixelBuffer para arrays imutáveis
e cache compartilhado de ícones. Fechamento do viewport encerra seus workers.
Não há alteração dos cálculos CAM/G-code nem implementação LWJGL nesta entrega.

Suíte normal: 1.087 registrados, 1.075 aprovados, 12 opcionais ignorados, zero
falhas/erros. Probes nativos D3D/GTX 1650 e software passaram; artefato atualizado.
Nova sessão do usuário confirma os índices no worker, mas ainda registra esperas
FX pelo renderer. Picos menores não comprovam melhora global: ações e duração
diferem e a taxa de intervalos >= 50 ms aumentou. Números, limites e overrides
em [PLOT_PERFORMANCE.md](PLOT_PERFORMANCE.md). Commit autorizado pelo usuário.
Próxima fatia de paridade segue sendo salvar/plot/seleção pelo Terminal; a
proposta de renderização OpenGL continua uma trilha separada, a medir.

## Terminal: salvar projeto, plot e seleção — 2026-10-05

Incremento sobre `1ca038a6`, após o commit das correções de fluidez. Acrescenta
`save_project`, `plot_all`, `plot_objects` e `set_active`: **22 famílias de
comandos FlatCAM**, além dos comandos internos/aliases do dialeto reduzido.
Referência: os quatro comandos correspondentes em `tclCommands/` e seleção
aditiva de `ObjectCollection.set_active` do Python local. Sem `eval`, nova
paridade de algoritmos CAM ou implementação LWJGL.

Salvar reutiliza o snapshot do menu e os serializadores existentes `.fcnproj`
e `.FlatPrj`. Serialização/compressão em worker; temporário ao lado do destino,
checagem de cancelamento/estado antes de substituir. Erros, cancelamento e
edições detectadas antes da publicação preservam o destino. Não altera arquivos
de origem/G-code nem preferências. Extensão explícita e diretório existente
obrigatórios; destino existente é substituído. Exportação Python conserva as
limitações do writer e avisa para manter também uma cópia nativa. Compressão
não é interrompida no meio; checagem FX e rename não formam uma transação única.

Plot valida o lote completo antes de alterar camadas, agrupa redesenho e
sincroniza checkbox Plot/seletor All/Travel/Cut. Corrigido um bug existente:
CNC com apenas Cut ou Travel não conta uma subcamada ausente como visível.
CNC sem prévia compatível é recusado explicitamente, inclusive em `plot_all`.
`-use_thread` é validado, sem substituir a política de workers FX. `set_active`
adiciona à seleção, expande a categoria e não exibe camadas ocultas nem rouba
foco do Terminal. Fechar editores de objetos antes de selecionar pelo comando.

23 regressões adicionais nos testes de argumentos e MainWindow real, incluindo
round-trip dos dois formatos, continuidade do script pelo Terminal, publicação
em lote, estados inválidos, cancelamento/edição concorrente e destino intacto.
`mvnw.cmd -q install`: **1110 registrados, 1098 aprovados, 12 opcionais
ignorados**, zero falhas/erros (602 CAM, 115 application, 373 FX, 20 test-support).
Probes nativos D3D/GTX 1650 e software aprovados; executável recompilado.
Fixtures próprios; sem modificar projetos privados/preferências do usuário.
Não houve benchmark de fluidez, teste físico CNC nem nova execução do oráculo
Python com projetos privados. Validação manual permanece pendente.

Roteiro e diferenças deliberadas em [TERMINAL_TCL.md](TERMINAL_TCL.md).
Próxima fatia recomendada: `rotate` e joins pelo Terminal, reaproveitando as
operações existentes. Não declarar paridade Tcl completa.

## Terminal: rotação — 2026-10-05

Incremento sobre `9c187b27`: `rotate {nome} angulo -origin center|origin|min_bounds|x,y`,
com `-box {referencia}` opcional prevalecendo sobre origin. Graus positivos =
horário; centro do próprio objeto por padrão. Referência é a UI Transformations
Python (`ToolTransform.on_rotate_action`/tooltip), não uma classe Tcl: este
checkout não contém `TclCommandRotate.py` nem registro Tcl rotate. Extensão FX
documentada como tal, não remoção de lacuna de comando Python inexistente.

Reutiliza o caminho seguro de transformações em worker: Gerber completo/follow,
Excellon drills/slots e Geometry por ferramenta, preservando aparência, parâmetros
e fontes. Ângulo finito reduzido módulo 360; identidades não trocam versões.
CNC recusado para não alterar só a prévia. Cancelamento e origem/referência/epoch
verificados antes de publicar. Sem eval e sem novo undo/redo Tcl.

Sete regressões adicionais. `mvnw.cmd -q install`: **1117 registrados, 1105
aprovados, 12 opcionais ignorados**, zero falhas/erros (602 CAM, 115 application,
380 FX, 20 test-support). Fixtures próprios, fontes e G-code intactos; sem
benchmark, comparação privada Python ou teste físico CNC nesta etapa.
Próxima fatia: joins Geometry/Excellon pelo Terminal e aliases do Python.

## Terminal: junções Geometry e Excellon — 2026-10-05

Incremento sobre `25ae2abb`: `join_geometry`/`join_geometries` e
`join_excellon`/`join_excellons`, seguindo sintaxe das classes Python locais.
**25 famílias FlatCAM**, contando a extensão FX rotate; não Tcl completo.
Saída primeiro, fontes nomeadas separadamente, pelo menos duas distintas do
mesmo tipo/unidades. Nome ocupado recebe sufixo retornado pelo comando.
Resultado oculto como `plot=False` no Python, sem seleção/fit nem remoção de
fontes; aparência inicial padrão FX. Menus de Join inalterados.

Composição em worker via GeometryJoin (sem fuse_tools, como o comando Tcl
Python) e ExcellonJoin (fusão a quatro casas já existente). ExcellonJoin agora
também fornece mapas imutáveis de IDs por fonte, sem alterar a API anterior ou
o algoritmo geométrico. `TclObjectJoin` preserva/remapeia caminhos, perfis,
V-Tip, overrides CNC, defaults de furação e seleção Drilling. Valida snapshot
do projeto/configurações/epoch e cancelamento antes da publicação FX em lote.
Fechar editores e concluir operação principal antes; sem novo undo Tcl.

Diferenças deliberadas: Geometry single/multi não misturados; single exige
parâmetros iguais. Multi conserva split que o merge Tcl Python trata de outra
forma, usa default global da última fonte e remapeia overrides de cada ferramenta.
Preprocessor/opções comuns conflitantes ou fontes configuradas/não configuradas
são recusadas. Perfis sem overrides não recebem parâmetros individuais que não
poderiam gerar. Excellon não funde parâmetros conflitantes nem amplia seleção
de usinagem ao fundir selecionada/não selecionada. Fontes/arquivos intactos.
Python copia opções globais da última fonte; FX não promete preservar todos
os campos legados nem remove os limites dos formatos/writers existentes.

Validação automatizada usa fixtures próprios: aliases/argumentos, conflito de
nomes/tipos/unidades, worker responsivo, cancelamento/edições concorrentes,
snapshot/configurações, persistência nativa/Python e script real do Terminal.
Teste adicional junta configurações, salva/reabre e gera G-code em memória
verificando profundidades/avanços por ferramenta e seleção Drilling remapeada.
Sem teste físico CNC, benchmark ou nova comparação dos projetos privados Python.
Próxima fatia: exportações Gerber/Excellon/SVG pelo Terminal; subtract/panelize
e preferências Tcl continuam pendentes. Roteiro em [TERMINAL_TCL.md](TERMINAL_TCL.md).

22 regressões adicionais (10 metadados, 2 argumentos, 9 host/Terminal, 1 CAM).
`mvnw.cmd -q install`: **1139 registrados, 1127 aprovados, 12 opcionais
ignorados**, zero falhas/erros (603 CAM, 115 application, 401 FX, 20 test-support).
Limpeza normal de fixtures ativa. Validação manual dos projetos reais pendente.
Executável recompilado; `run-native.cmd --probe` (D3D/GTX 1650) e
`target/native/FlatCAMFX.exe --probe --software` aprovados. Probes de
inicialização/renderização offscreen, não benchmark ou migração LWJGL.

## Terminal: exportação Excellon — 2026-10-05

Incremento sobre `f7295058`: `export_excellon {furos} {C:/pasta/saida.drl}`,
aliases Python `export_exc` e `ee`. **26 famílias FlatCAM**, incluindo rotate
como extensão FX; não Tcl completo. Reutiliza ExcellonExporter sem modificar
seu algoritmo: ferramentas/furos/slots atuais, formato lembrado pelo diálogo
FX compartilhado com o menu (unidades, precisão, zeros, slots G85/roteados).
Preferências reais apenas lidas. Fallback existente IN decimal 2:4 LZ roteado;
conversão/arredondamento podem perder precisão. Não exporta parâmetros CNC
nem gera G-code. Origem em memória, seleção/visibilidade permanecem.

Referência: TclCommandExportExcellon.py, app_Main.export_excellon e defaults.py
locais, sem mudar o oráculo. Diferença deliberada: filename obrigatório no FX,
opcional no Python. Destino substituído, inclusive arquivo de entrada caso o
usuário escolha esse caminho; usar outra cópia para ensaios. Pasta deve existir,
sem seletor ou extensão automaticamente acrescentada. Não importa preferências
do Python nem promete bytes idênticos ao legado.

Serialização/gravação ASCII no worker, temporário ao lado do destino e validação
de origem/nome/versão/epoch, editores/operação e formato antes da substituição.
Cancelamento/mudanças detectadas antes de publicar preservam destino existente;
temporários removidos e rename atômico com fallback. Serialização/escrita
individual não interrompidas no meio; checagem FX/rename não são transação única,
e cancelar depois de publicar não desfaz arquivo. Menus existentes inalterados.

14 regressões novas (2 argumentos, 3 formatos, 9 host/Terminal real), incluindo
round-trip com objeto editado, IN/MM/slots, cancelamento/alterações concorrentes,
editores/operação principal, nomes ambíguos/tipos/arquivos inválidos, origem
separada intacta, limpeza e script abrir/juntar/rotacionar/exportar/continuar.
Teste de formato usa parser puro e preferências atuais somente em leitura;
não simula mudança alterando preferências reais. `mvnw.cmd -q install`:
**1153 registrados, 1141 aprovados, 12 opcionais ignorados**, zero falhas/erros
(603 CAM, 115 application, 415 FX, 20 test-support). Executável recompilado;
probes D3D/GTX 1650 e software aprovados. Sem benchmark, migração LWJGL,
teste físico CNC ou nova comparação dos projetos privados Python.

Pedido atendido com mais um incremento e commit; encerrar por hoje. Próxima
sessão sugerida: exportações Gerber/SVG pelo Terminal; subtract/panelize e
preferências Tcl seguem pendentes. Roteiro: [TERMINAL_TCL.md](TERMINAL_TCL.md).

## Revisão e correções de robustez — 2026-10-06

Revisados os sete commits entre `16373f3d` e `d5ff39fe`: seletor piloto de
fresas, processamento assíncrono do Plot/fontes e comandos Tcl de projeto,
visibilidade/seleção, rotação, junções e exportação Excellon.

- Junção Excellon: parâmetros ausentes agora participam da validação de cada
  ferramenta fundida, após o remapeamento/arredondamento de diâmetros. Misturar
  parâmetros explícitos com ausentes no mesmo diâmetro é recusado em qualquer
  ordem e também dentro de uma mesma origem. Ambos ausentes continuam ausentes;
  diâmetros distintos podem manter configurações diferentes/ausentes. Objetos
  originais não são alterados.
- Índices do Plot: `Error` marca a versão como falha, é registrado e continua
  propagando para o executor/handler. `finally` libera o estado de drenagem e
  reagenda pedidos pendentes; novas versões podem ser preparadas. Não há retry
  por quadro da versão com falha nem publicação tardia após remoção/fechamento.
  Isso não promete recuperação da JVM de falhas fatais/falta de memória.
- Resumo operacional atualizado para 26 famílias Tcl (incluindo rotate FX) e
  os números atuais da suíte, preservando as entregas antigas como histórico.

Quatro regressões Excellon e quatro de índices: **1161 registrados, 1149
aprovados, 12 opcionais ignorados**, zero falhas/erros em `mvnw.cmd -q verify`.
Probes `target/native/FlatCAMFX.exe --probe` (D3D/Intel Arc) e `--probe --software`
aprovados. Sem alteração de projetos privados/preferências, benchmark, teste
físico CNC ou nova comparação Python; validação manual do usuário permanece
pendente. Próximo incremento de paridade continua sendo exportação Gerber/SVG
pelo Terminal, antes de subtract/panelize e preferências Tcl.

## Continuidade visual do Plot no zoom e na edição — 2026-10-06

Sobre `fcb5b592`, reproduzidos offscreen o apagão ao alternar densidade/vetor
e o ciclo visível → vazio → atualizado ao excluir/desfazer Geometry densa.

- `updateLayerGeometry` preserva a prévia e cancela publicações da versão antiga.
  Durante a preparação do índice, somente o último índice já pronto é usado no
  desenho; durante a rasterização, a imagem anterior permanece. O indicador de
  preparação inclui a densidade. Os dados editados/selecionáveis são os atuais.
- Sair do modo densidade suspende pedidos sem descartar a imagem. Cada binding
  guarda o quadro mais recente e no máximo uma imagem ampla adicional, útil
  ao afastar depois de uma vista recortada. Uma câmera exata em cache é reutilizada
  imediatamente e cancela pedidos intermediários. Não há cache ilimitado de zooms.
- Imagens publicadas não são sobrescritas, inclusive no override PixelWriter.
  Prévia sem mudança de câmera não usa smoothing, evitando desbotar os traços.
  Geometria vazia/remoção/clear/dispose limpam caches; ocultação não mostra prévias.

Oito regressões novas (seis visuais, uma de índice, uma de suspensão), com
exclusão/desfazer reais no modelo, quatro temas, densidade/vetor, recorte após
zoom concluído, atualizações rápidas, dados atuais e limpeza de estados antigos.
`mvnw.cmd -q verify`: **1169 registrados, 1157 aprovados, 12 opcionais ignorados**,
zero falhas/erros. Os sete testes PlotPreparationUiTest passaram também no modo
PixelWriter. Probes D3D/Intel Arc e software do launcher existente passaram.

Nenhum projeto privado/preferência foi alterado. Sem benchmark ou mudança no
cálculo CAM/NCC. A imagem ampla custa até um quadro adicional por binding;
câmeras inéditas ainda são refinadas em fundo, com os 60 ms de settle mantidos.
O usuário confirmou que o ajuste funcionou no teste manual. Não houve novo
benchmark controlado de fluidez/FPS.
Detalhes e limites: [PLOT_PERFORMANCE.md](PLOT_PERFORMANCE.md).

## Ações de Plot contextuais na árvore — 2026-10-06

A pedido do usuário, o menu usa a visibilidade real a cada abertura: somente
`Desativar Plot` quando o objeto está visível, somente `Ativar Plot` quando
está oculto. Vale para Gerber, Excellon, Geometry e CNC Job, preservando os
ícones Python e as demais ações. Os menus de objetos do Plot Area compartilham
essa mesma regra. É uma diferença visual deliberada em relação ao Python,
que mantém as duas ações no menu, não uma limitação de paridade funcional.

Seleção múltipla mostra uma ação por estado presente, com a quantidade de
objetos afetados, ignorando CNC Jobs sem prévia e outros nós não plotáveis.
Em uma seleção mista, ativar altera somente os ocultos e desativar somente os
visíveis. Mudanças são publicadas em lote, com uma atualização da árvore.
CNC Job é visível quando pelo menos uma subcamada existente (Cut ou Travel)
está ativa; subcamadas ausentes não contam. CNC sem prévia não oferece nenhuma
ação de Plot. Menu sem ações de Plot não ganha separador vazio no início.

Seis regressões em ProjectPlotMenuTest cobrem os quatro tipos, CNC Cut/Travel/
ambos, estados e cliques, reabertura do menu, seleções uniformes/mistas,
contadores, ícones, CNC sem prévia e mudança de visibilidade fora do menu.

`mvnw.cmd -q verify`: **1175 registrados, 1163 aprovados, 12 opcionais ignorados**,
zero falhas/erros. Probes do launcher existente em D3D/Intel Arc e software
passaram com o build atualizado. Sem alteração de projetos privados/preferências
ou cálculos CAM, benchmark ou nova comparação Python. Validação manual do menu
pelo usuário permanece pendente.

## Cor atual no menu de objetos — 2026-10-06

O ajuste anterior de Ativar/Desativar Plot foi commitado em `f59f849a`, na branch
`flatcam-next`. O submenu Definir Cor de Gerber, Excellon e Geometry agora usa
RadioMenuItem e ToggleGroup: uma marca persistente indica a cor realmente usada,
separada do destaque de navegação/hover. Os quadrados arredondados e ícones Python
foram mantidos. A seleção é lida ao construir/abrir o submenu e após cada ação
de cor, inclusive quando o diálogo de cor personalizada é cancelado.

A comparação usa RGB com tolerância de 1e-6, sem opacidade. O amarelo Python
permanece `#FFDF00`, não `#FFFF00`. Uma cor da paleta tem prioridade sobre Padrão
se ambos coincidem (por exemplo, Geometry padrão marca Vermelho). Cor padrão
fora da paleta marca Padrão, mesmo com opacidade alterada; as demais marcam
Personalizada. Opacidade permanece uma ação separada. Nenhuma cor é alterada
apenas por abrir o menu; restaurar Padrão continua restaurando fill e contorno.

Seis regressões novas em ProjectPlotMenuTest passaram, incluindo todos os oito
presets, os três tipos e objetos ocultos. `mvnw.cmd -q verify` passou com **1181
registrados, 1169 aprovados, 12 opcionais ignorados**, zero falhas/erros. Probes
offscreen do launcher existente passaram com o build atualizado em D3D/Intel Arc
e software. Nenhum projeto privado/preferência foi alterado. Validação manual
da marca de cor nos temas pelo usuário permanece pendente.

## Áreas de exclusão no Drilling — 2026-10-06

Prioridade escolhida pelo usuário, antecipando as exportações Gerber/SVG Tcl.
O painel Drilling passou a usar CncExclusionEditor: desenhar retângulo/polígono,
retângulo numérico, tabela Strategy/Over Z, seleção com destaque, editar/apagar
e checkbox de ativação. Ícones originais nos dois temas de assets, aplicados
também ao editor compartilhado Geometry; capturas nos quatro temas inspecionadas.

DrillJobOptions ganhou áreas/ativação com construtores antigos preservados.
GCodeGenerator reutiliza CncExclusionPlanner. Around desvia em XY; Over sobe
antes e restaura Travel Z no destino. Brocas são validadas com raio real + 0,1 mm
(ou equivalente IN), slots em toda a extensão; interseções recusam o trabalho
inteiro. Só ferramentas selecionadas participam. Saída inicial, retorno de
passes de slots e estacionamento são roteados; troca exige altura coerente e
a posição anterior também deve acomodar a nova broca. Diâmetro desconhecido/
inválido não recebe fallback fictício para exclusões. Roland e Mach3 com sonda
recusam áreas ativas. Áreas desativadas não mudam o G-code anterior.

Geração Drilling deixou a thread FX: DrillCncGeneration prepara código/prévia e
arquivo temporário no JobExecutor, com cancelamento cooperativo, progresso por
quantidades de trabalho e callbacks limitados a mudanças de percentual. Fases
sem fração ficam indeterminadas. Antes de publicar, MainWindow revalida projeto,
origem/nome/defaults e editores. Destino existente permanece intacto até rename
com tentativa atômica/fallback. Cancelar após publicação não desfaz o arquivo;
checagem FX e rename não constituem uma transação única. UI só publica defaults
e CNC Job após sucesso e não fecha outro painel aberto durante a geração.

ProjectFileIO conserva ativação e WKT/Strategy/Over Z em JSON/XZ `.fcnproj`,
regenera o mesmo código e aceita projetos antigos sem esses campos. Dados novos
malformados causam erro, sem descartar áreas silenciosamente. PythonProjectWriter
recusa `.FlatPrj` com áreas Drilling, mesmo desativadas, antes de tocar o destino.
O Python guarda exclusões globalmente; FX as associa ao objeto. Rascunhos do
formulário não são salvos ao fechar: a persistência usa a última geração bem-
sucedida. Trocar fonte cancela desenho e restaura suas áreas; Reset limpa o draft.

39 regressões novas passaram, incluindo host MainWindow real, cancelamento,
arquivo original preservado, nomes/projeto/defaults alterados, worker responsivo,
slots/múltiplas brocas, MM/IN e renderização em quatro temas. `mvnw.cmd -q verify`:
**1220 registrados, 1208 aprovados, 12 opcionais ignorados**, zero falhas/erros.
Probes do launcher existente passaram com o build atualizado em D3D/Intel Arc e
software. Nenhum projeto privado/preferência foi alterado nos testes; sem novo
benchmark ou comparação diferencial privada Python. Desenhos/cliques físicos
e teste a seco CNC continuam pendentes. Limites: [CNC_EXCLUSIONS.md](CNC_EXCLUSIONS.md).

O usuário priorizou em seguida Start Z e posição XY de troca no Drilling;
o incremento abaixo substitui a fila anteriormente sugerida. As exportações
Gerber/SVG pelo Terminal continuam candidatas após a validação de Drilling.

## Start Z e posição XY de troca no Drilling — 2026-10-06

DrillJobOptions ganhou campos opcionais startZ/toolChangeX/toolChangeY, mantendo
todos os construtores anteriores e preservando posições ao alterar exclusões.
Common Parameters mostra Start Z e Tool change X,Y, com tooltips, campos compactos
e rótulos sem truncamento nos quatro temas. None mantém o comportamento anterior;
X;Y aceita vírgulas decimais e X,Y aceita ponto decimal. Fonte/Reset não herdam
posições de outro objeto. Valores ficam nas unidades do Excellon.

Start Z é emitido inicialmente, seguido de Travel Z antes do primeiro XY. Troca
com posição explícita exige Tool change (ou ICP automático) e Tool change Z >=
maior Travel Z selecionado. Spindle para antes do trajeto; Around/Over considera
a broca instalada durante a saída e ambas no destino. Isso permite afastar uma
broca fina de um furo próximo a obstáculo antes de instalar a próxima, mais larga.
Sem posição explícita, mantém a validação antiga na posição atual. A primeira
ferramenta portable também pausa quando há XY explícito; G90/Travel Z são retomados
após a troca. Movimentos internos de macros e dimensões do cabeçote não são modelados.

Roland/Mach3 com sonda recusam Start Z/XY explícitos nesta fatia, sem ignorá-los.
Os defaults antigos desses perfis continuam funcionando. O Start Z separado de
sondagem continua pendente. Geração/prévia/gravação permanece no worker cancelável;
falha de altura/rota não publica um arquivo parcial nem altera o destino existente.

JSON/XZ `.fcnproj` preserva posições da última geração bem-sucedida; projetos
antigos sem os campos opcionais mantêm o G-code anterior. Campos novos inválidos
recusam abertura. `.FlatPrj` sem exclusões e com posições explícitas escreve
chaves comuns Python em options/tools.data, além do snapshot FX. A reabertura
direta no FX conserva as posições; UI Python pode aplicar preferências globais,
e reimportação após remover metadados FX ainda não recupera todas as posições
comuns de Drilling. Não foi declarada equivalência de ordem textual com Python.

47 regressões adicionais: 25 CAM, 7 persistência, 14 painel FX (incluindo quatro
temas) e 1 publicação de arquivo/prévia. Cobrem perfis comuns, MM/IN, valores
inválidos, compatibilidade antiga, raio da broca instalada/substituta, Around/Over,
Reset/restauração e arquivo intacto após falha. A suíte final `mvnw.cmd -q verify`
passou com **1267 registrados, 1255 aprovados, 12 opcionais ignorados**, sem
falhas/erros e com limpeza TempDir normal. Capturas nos quatro temas inspecionadas;
probes do launcher existente passaram com o build atualizado em D3D/Intel Arc e
software. Nenhum projeto privado/preferência foi alterado; nenhum novo benchmark
ou ensaio físico/Python GUI foi feito.

Validação manual do novo painel/posições e teste a seco CNC continuam pendentes.
Detalhes: `CNC_EXCLUSIONS.md`, `PREPROCESSADORES.md` e `COMPATIBILIDADE_FLATPRJ.md`.
Próxima fila sugerida, após validação: exportações Gerber/SVG pelo Terminal ou
continuação das opções avançadas de Drilling; não declarar paridade global completa.

## Exportações Gerber/SVG pelo Terminal — 2026-10-06

Sobre `ad943c9b`, `export_gerber`/`export_grb`/`egr` e `export_svg` completam a
fatia de exportações de desenho indicada na fila anterior. Terminal chega a 28
famílias FlatCAM, incluindo rotate como extensão FX, não a paridade Tcl total.

Gerber reutiliza o formato do diálogo (fallback IN 2:4 L) e o writer de regiões
resolvidas, sem preservar macros/identidades de aperturas/comandos da origem.
SVG cobre Gerber/Excellon/Geometry/CNC, unidades da origem e fator de traço
posicional ou opção; não escala coordenadas nem copia a aparência/visibilidade
do Plot. CNC usa código atual, Travel sob Cut e recusa programas sem prévia
compatível. Marcadores de ponto agora não ficam cortados ao usar traços largos.

Destino explícito numa pasta existente, substituição do arquivo escolhido,
serialização/gravação no worker, temporário ASCII/UTF-8 e revalidação de
origem/nome/versão/projeto/editores/operação/formato Gerber antes de publicar.
Erros/cancelamento/mudanças detectadas preservam o destino e removem temporários.
Sem mudanças nas preferências, seleção, câmera ou dados do objeto. Cancelamento
cooperativo; serialização/escrita individual não interrompida no meio. Checagem FX
e rename não constituem transação única, e publicação não tem undo de arquivo.

24 regressões novas: 6 CAM e 18 FX, incluindo host/Terminal real, aliases/ajuda,
fallback de formato, MM/IN, cancelamento por fase, dados/código editados,
recusas e destino intacto, desenho/traços e worker responsivo. Fixtures próprios,
sem usar projetos privados ou gravar preferências reais. `mvnw.cmd -q verify`:
**1291 registrados, 1279 aprovados, 12 opcionais ignorados**, zero falhas/erros.
Probes do launcher existente, com o build atualizado, passaram em D3D/Intel Arc
e software forçado, sem abrir janela ou alterar preferências.
Validação manual no Terminal e comparação de arquivos com o Python permanecem
pendentes; nenhum novo benchmark ou teste físico CNC. Roteiro e limites:
[TERMINAL_TCL.md](TERMINAL_TCL.md).

Fila Tcl daquela entrega: subtract/panelize ou preferências, em incrementos separados;
as opções CAM avançadas e compatibilidade de persistência Python continuam parciais.

## Consolidação dos fluxos principais: Gerber → Geometry — 2026-10-06

Exportações Gerber/SVG commitadas em `0a3fb636`. O usuário mudou a prioridade
para completar fluxos principais antes de ampliar automação/ferramentas secundárias.
Primeiro incremento: publicação segura das Geometries de Isolation/NCC/Cutout.

NCC antes verificava apenas existência da origem, permitindo resultado de uma
versão antiga. As três operações agora validam origem e referências da mesma
versão oferecida no painel antes de iniciar; revalidam identidade/nome/versão,
projeto, editores e cancelamento antes de publicar. Máscaras desenhadas continuam
independentes de objetos. Referências/exceções por objeto carregam sua identidade.
Cancelamento depois do worker, antes da publicação FX, também é respeitado.
Outro painel não é fechado/selecionado pela conclusão antiga; cores/visibilidade
e objetos não participantes não invalidam resultados válidos. Callbacks antigos
não encerram outro job nem sobrescrevem seu progresso.

Os testes revelaram falha de Isolation com exceção em GeometryCollection gerada.
Corrigido recorte de cada parte com reagrupamento por passada. Máscaras poligonais
aninhadas são unidas; partes não preenchidas numa máscara mista são recusadas.
Follow preserva pontos não cobertos. Referência: laço de recorte por anéis/linhas
de `ToolIsolation.area_subtraction`; não foi executado novo benchmark/oráculo privado.

44 cenários FX novos e 5 CAM; seis caminhos MM/IN seguem CAM da UI → CNC pelo
host Tcl → G-code → salvar/reabrir nativo. Não exercitam FileChooser/geração CNC
pela UI nem certificam usinagem física. Fixtures próprios, sem gravar preferências.
Roteiro e limites: [FLUXO_PRINCIPAL.md](FLUXO_PRINCIPAL.md).

Verificação final `mvnw.cmd -q verify`: **1340 registrados, 1328 aprovados,
12 opcionais ignorados**, zero falhas/erros, limpeza TempDir normal.
Probes do launcher existente com build atualizado aprovados em D3D/Intel Arc
e software forçado; somente renderização offscreen, não benchmark CAM.

Próxima fatia prioritária: Geometry → CNC pela UI com gravação temporária,
prévia preparada antes da publicação e validação de origem/defaults/projeto;
hoje essa rota ainda escreve o destino diretamente antes de concluir a prévia.
Depois, Drilling avançado/Tools Database e persistência dos fluxos principais.
Não declarar 100% global ou de fluxo antes da validação manual/diferencial.
