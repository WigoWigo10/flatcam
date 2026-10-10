# Comparação reproduzível CAM: FX × Python

## Fluxo panelizado real — 2026-10-10

Runner novo, sem alteração de algoritmos CAM ou código legado:

```powershell
.\flatcam-next\compare-panelized-flows.ps1 -Project 'C:\caminho\projeto.FlatPrj' -Strict
```

Usa a referência compatível existente `target/oracle-py311/Scripts/python.exe`
(ou `-Python` explícito); valida Python 3.11/Shapely 1.8.5.post1/GEOS 3.10.3 e
não instala dependências. `-OutputDirectory` precisa ser uma pasta NOVA dentro
de `flatcam-next/target`. Guarda logs, projetos/code/geometrias privados apenas
ali e verifica o hash do original. Erros FX abortam antes do oráculo; Strict
não aceita divergências ou erros como aprovação.

Projeto real previamente usado: grade 2 x 2, gap 5 mm, referência Edge_Cuts;
F_Cu/B_Cu, Edge_Cuts, Excellons e área do contorno recebem o mesmo layout.
`MainPanelizedCamFlowTest`: MM/IN sintéticos mais fixture opcional real, usando
MainWindow sem Stage -> CAM workers/publicação -> CNC/export host Tcl ->
salvar/reabrir. Confere quatro cópias, posições/IDs/diâmetros/furos/slots,
limitação do NCC às áreas de placa, Cutout por placa/pontes, previews e
persistência de geometrias/unidades/defaults/G-code. Não substitui testes de
todos os formulários CNC, controladores, opções de usinagem ou uso físico.

Resultado final `target/panelized-flow-20261010-final`:

| Caso | Distância amostrada CAM (mm) | Status |
| --- | ---: | --- |
| Isolation F_Cu, 3 passadas / 0,1 mm / 15% | 0,00097359 | MATCH_SAMPLED |
| Isolation B_Cu, mesmos parâmetros | 0,00202909 | MATCH_SAMPLED |
| NCC F_Cu, Standard / 0,5 mm / 40% / referência Geometry | 0,00000107 | MATCH_SAMPLED |
| NCC B_Cu, mesmos parâmetros | 0,00000106 | MATCH_SAMPLED |
| Cutout Panel / free-form / 0,8 mm / margem 0 / quatro pontes de 2 mm | 0,00044820 | MATCH_SAMPLED |

**5/5, zero DIFFERENT/GCODE_DIFFERENT/PARTIAL_DIFFERENCE/ORACLE_ERROR.** NCC:
Connect off, Contour on, sem Rest; área de clearing coincide nas duas faces,
zero polígonos falhados. Critérios anteriores de 0,003 mm/0,1%/IoU >=99,5%
permanecem. G-code também passa no parser Python e na comparação amostrada.

F_Cu/B_Cu são decodificados independentemente do original e panelizados pelo
corpo original de `ToolPanelize.job_init_geometry`. Stubs limitados a ambiente,
apertures/exportação (CAM usa solid_geometry); lista de saída original mantida.
Referência/box é decodificada do projeto, não confiada a um WKT exportado pelo FX.
O oráculo registra hash do ToolPanelize além dos outros fontes.
Cutout compila `cutout_handler`, bounds/interseção/subtração/flatten originais,
sobre áreas preenchidas explicitamente COMPARTILHADAS; compensação Gerber
não negativa. Não valida reconstrução Edge_Cuts Python, margem negativa,
Single, Thin/M-Bites ou recortes internos neste incremento.

Pré-requisito do fluxo: área do contorno ANTES de panelizar, incluída no conjunto.
Converter o contorno já panelizado pelo atual OutlineToArea escolhe só a maior
região. NCC Itself e Cutout Single têm outras semânticas: não presumir limpeza
limitada às placas nem cortes individuais. O gerador Cutout exterior continua
sem roteamento automático dos anéis internos. O contorno real testado possui
zero anéis internos, enquanto a fixture pública MM/IN inclui um recorte.

Resultados são de teste: o CNC do host Tcl usa uma passada Z e parâmetros
explícitos, não os jobs privados originais nem receitas para usinagem. Não há
Stage/FPS/cliques/segurança física validados. Guardas/defaults GUI têm testes
separados. Salvamento/reabertura local levou ~62,4 s neste painel denso:
próximo diagnóstico de desempenho, sem concluir causa por GPU/renderização.

Oráculo/unitários adicionais conferem corpos originais, lista/polígono,
coordenadas não nulas, buracos, layouts inválidos e quatro pontes por cópia.
32 testes Python auxiliares/compileall passam. Nenhum projeto privado é fixture
commitada; todo relatório/projeto gerado permanece ignorado em target.
As pendências anteriores de Connect/Seed/Lines/multi-settings continuam intactas.

Build final completo: `mvnw.cmd -q verify` passou com 1479 registrados,
1466 aprovados, 13 opcionais ignorados e zero falhas/erros. A fixture real
opt-in passa no runner separado. Primeira tentativa teve falha transitória
de limpeza TempDir/DirectoryNotEmptyException em PythonMainFlowImportTest;
reexecução integral passou sem mudar limpeza/tolerâncias/testes existentes.

## Preparação NCC integrada — 2026-10-08

Base diagnóstica commitada `fcf93ea8`, branch `flatcam-next`. A implementação
agora aplica o kernel GeosBufferOp existente à margem mitre e faz união robusta
moderna do cobre reparado para fontes GeometryCollection genéricas. Isso
reproduz a etapa de união da lista [MultiPolygon] importada, não uma rotação
arbitrária de anéis. Polygon/MultiPolygon ordinários mantêm o preparo anterior;
Paint não foi alterado. Nenhum objeto/projeto de origem é reordenado em memória
ou sobrescrito, e o código Python/critério de comparação permanece intacto.

### Resultados da implementação, não dos controles

Mesmo projeto real e nove casos: **8 MATCH_SAMPLED, 1 DIFFERENT, zero
ORACLE_ERROR/GCODE_DIFFERENT/PARTIAL_DIFFERENCE**. Todos os Standard/multi-order
e Rest Itself/Area/Reference Geometry/Connect passam. Connect simples ainda
reprova por distância 0,020312630895 mm, acima de 0,003 mm. Seu comprimento
relativo difere só 0,000011966129, mas isso não anula o critério de distância.
Antes era ~0,15305533 mm. Clearing area delta é zero nos nove casos.
**Strict real continua reprovado**; não declarar equivalência completa.

Relatório privado atual: `target/main-flow-comparison-20261008-104915-841/source`;
os nove controles sintéticos IN da mesma execução passam. A execução usa o
gerador multi-tool real, verificando o programa conjunto e cada fresa.

Corpus sintético completo repetido: **49/54**, mesmas pendências anteriores:
MM 24 MATCH_SAMPLED, Seed DIFFERENT, Lines e multi-settings GCODE_DIFFERENT;
IN 25 MATCH_SAMPLED, Seed DIFFERENT e multi-settings GCODE_DIFFERENT. Zero
erros/partial; strict completo reprova. Relatórios atuais:
`target/main-flow-comparison-20261008-105800-862/source` e `synthetic-in`.

Probe Connect ampliado para formas diretas e listas [MultiPolygon], nos mesmos
três desenhos públicos, unidades e posições: **24/24 aprovados**. A referência
passa a invocar os helpers GUI originais para limite/margem/subtração, sem
adicionar buffer(0) ao resultado da referência. Aprovação independente exige
área, caminhos Plain e Connect; controles de mesma área são apenas diagnósticos.
Resultados: `target/ncc-prepared-connect-public-python-final-20261008.json`.

Regressões Java conferem a margem rasa nas quatro modalidades de limite em
MM/IN e duas posições, e a preparação da coleção sem mutar a origem. Verify
completo passa: **1476 registrados, 1464 aprovados, 12 opcionais ignorados,
zero falhas/erros**. 29 testes Python e compileall passam; launcher offscreen
Direct3D/Intel Arc e software passam. Sem testes manuais de UI ou máquina CNC.

### Pendências deste ajuste

Investigar os inícios/ordem residuais do Connect simples com fixture pública e
entradas controladas. A união moderna melhora a representação, mas não garante
inícios idênticos em todos os anéis do projeto. Não aplicar rotação arbitrária,
arredondamento da entrada nem relaxar o limite para passar o teste.

A peculiaridade Itself de lista com um único Polygon (conserva concavidades/
vazios, enquanto Polygon direto usa hull) não é coberta por esta integração.
WktJson.buildGeometry pode colapsar essa lista em Polygon e perder a distinção;
esse caso exige metadados/semântica explícitos, não inferir lista de toda
geometria poligonal. O projeto real testado contém lista [MultiPolygon].

## Investigação do projeto real: margem e representação — 2026-10-08

Base commitada `be6b6127`, branch `flatcam-next`. **Diagnóstico, não correção
de produção.** O projeto original foi somente lido; critérios e corpos Python
permanecem inalterados. A produção ainda aprova 2/9 casos reais.

`NccPreparationProbe.java` exporta cobre, reparo, hull, margem, subtração e
variantes de união. `investigate_ncc_preparation.py` usa helpers originais GUI
e distingue geometria topológica de representação dos anéis. Campos de
pareamento arredondam somente bounds para localizar anéis, sem normalizar
entrada nem aprovar paridade; controles de mesma área também não são aprovações.

### Primeira divergência: margem

Cobre bruto/reparado e hull coincidem com Python. O buffer mitre padrão JTS
da margem Itself produz diferença de 0,000350179467 mm² e Hausdorff de
0,000643486557 mm. Aplicar o GeosBufferOp existente elimina a diferença de
margem e clearing nessa placa. A fixture pública independente
`POLYGON ((0 0,30 0,30 20,15 20.15,0 20,0 0))` reproduz o problema de canto
convexo raso: delta JTS 0,001599920006 mm²; candidato GEOS ~1e-15 mm².
Nova regressão ancora o kernel candidato em MM/IN e duas posições, não a
integração na produção. Não se trata de alterar a resolução ou tolerância.

No controle de CAM que substitui apenas a margem, passam Standard, três ordens
multi-tool e Rest Itself; Rest Area/Reference Geometry já passavam. Connect
e Rest Connect ainda reprovam. A execução completa do primeiro controle está
em `target/ncc-margin-control-python-20261008` (7 MATCH_SAMPLED, 2 DIFFERENT).
Nesse primeiro export, o alias `boundary=connect` ainda mantinha a margem
original; o probe foi corrigido e Connect repetido com a margem candidata em
`target/ncc-margin-control-connect-python-20261008`: área delta zero, mas
distância 0,15302112 mm / comprimento relativo 0,00262922, ainda DIFFERENT.
Rest Connect com margem candidata tem distância 0,04647088 mm, também DIFFERENT.
Os programas conjuntos dos controles usam o gerador multi-tool real.

### Segunda divergência: união da lista e início dos anéis

A lista Python contém um MultiPolygon. O FX mantém os mesmos inícios da fonte
bruta (111 anéis pareados sem diferença), mas o helper Python chama
`unary_union(target)` antes da diferença. Essa união desloca os inícios dos
111 anéis sem alterar área, número de vértices ou orientação. Depois da
subtração, 111 dos 112 anéis pareados começam em pontos distintos.

Controles eliminam hipóteses: GEOS com a exata boundary/copper FX produz os
mesmos inícios da subtração JTS. Remover buffer(0) final ou usar cobre bruto no
overlay não resolve. Com a própria área FX fornecida ao Python, Standard e
Connect concordam até ~2e-14 mm; isso localiza a divergência antes do clearing,
não comprova paridade independente. A união moderna JTS aproxima o alvo, mas
ainda deixa 18/111 inícios distintos; a clássica deixa 111/111. Não substituir
por rotação arbitrária nem achatar o oráculo. Também não transportar a regra
de lista sem considerar o caso especial de lista com um único Polygon.

Relatório detalhado: `target/ncc-preparation-complete-python-20261008.json`;
controles Java em `target/ncc-preparation-union-control-20261008.json` e
`target/ncc-margin-control-cam-v2-20261008.json`. Nenhum arquivo privado é
versionado. JSON/HTML novos identificam explicitamente exports candidatos
como controles diagnósticos, não resultados da implementação em produção.

### Reproduzir e próxima ação

```powershell
# Dentro de flatcam-next, com o classpath de testes já preparado:
$prepClasspath = 'flatcam-cam/target/classes;' + (Get-Content target/ncc-connect-classpath.txt -Raw).Trim()
# Fixture pública, sem ler projeto privado; escolher arquivos novos:
java --class-path $prepClasspath tools/NccPreparationProbe.java --synthetic target/prep-public.json
.\target\oracle-py311\Scripts\python.exe tools/investigate_ncc_preparation.py --legacy-root .. --trace target/prep-public.json --output target/prep-public-python.json
# Export existente do projeto, somente lido; terceiro argumento cria controle CAM:
java --class-path $prepClasspath tools/NccPreparationProbe.java CAM_EXPORT.json target/prep-private.json target/prep-private-control.json
.\target\oracle-py311\Scripts\python.exe tools/investigate_ncc_preparation.py --legacy-root .. --trace target/prep-private.json --project PROJETO.FlatPrj --connect-control target/prep-private-control.json --output target/prep-private-python.json
.\target\oracle-py311\Scripts\python.exe tools/compare_cam_python.py --legacy-root .. --fx-export target/prep-private-control.json --project PROJETO.FlatPrj --output target/prep-control-comparison --strict
```

Próximo ajuste proposto: integrar margem compatível e investigar a união/preparo
da fonte para os conectores, com regressões públicas e repetição strict real.
45 testes Java focados e 29 auxiliares Python passam; compileall passa.
Verify completo anterior é o do commit base, não repetido para este diagnóstico.
Sem validação manual de UI ou máquina CNC.

## Connect, referência compatível e Rest/múltiplas ferramentas — 2026-10-08

Referência isolada: Python 3.11.1, Shapely 1.8.5.post1 e GEOS 3.10.3.
Nenhum algoritmo Python foi alterado; o ambiente habitual 3.12/Shapely 2
continua intacto. As oito falhas multipart desaparecem nessa referência,
mas diferenças reais continuam reprovando strict. Dependências capturadas em
`tools/requirements-cam-oracle.txt`; são apenas do ambiente de teste Windows.
Shapely 1.8 fornece wheels até Python 3.11, conforme a
[distribuição oficial](https://pypi.org/project/shapely/1.8.5.post1/).

### Reproduzir sem modificar o ambiente principal

O ambiente local já preparado fica em `target/oracle-py311`, ignorado no Git.
O runner não instala dependências: `-LegacyCompatible` seleciona esse ambiente
ou valida o `-Python` explícito, exigindo Python 3.11/Shapely 1.8.5.post1/GEOS
3.10.3 antes de construir/exportar. Uma referência incompatível é recusada.

```powershell
# Dentro de flatcam-next; o ambiente isolado já preparado nesta máquina:
.\compare-main-flows.ps1 -LegacyCompatible -Strict
.\compare-main-flows.ps1 -LegacyCompatible -Strict -Cases ncc-connect,ncc-rest,ncc-rest-connect,ncc-multi-reverse
# Projeto somente lido; resultados privados continuam em target:
.\compare-main-flows.ps1 -LegacyCompatible -Strict -Project 'CAMINHO_DO_PROJETO.FlatPrj'

# Se o target tiver sido removido, preparar explicitamente com Python 3.11:
$camOraclePython = 'CAMINHO_DO_PYTHON_3_11\python.exe'
& $camOraclePython -m venv target/oracle-py311
.\target\oracle-py311\Scripts\python.exe -m pip install -r tools/requirements-cam-oracle.txt
```

Se um pip antigo não reconhecer o certificado corporativo, usar o pip moderno
já disponível com `--python target/oracle-py311/Scripts/python.exe` para instalar
nesse ambiente, ou configurar a CA correta. Não desativar a verificação TLS.

### Correções comprovadas e escopo

Connect: a subtração inicial usava o overlay clássico padrão do JTS. A área era
igual, mas os anéis começavam em vértices diferentes, alterando os conectores.
Agora essa subtração usa `OverlayNGRobust`, correspondente à preparação moderna
do GEOS. Sem rotação arbitrária de anéis, arredondamento da entrada ou alteração
da referência. O teste `NccConnectProbe.java` cobre três formas (vazio interno,
concavidade e cobre desconexo), MM/IN e coordenadas positivas/negativas: **12/12
Connect independentes passam**. O controle com a área FX fornecida ao Python
passa, mas é explicitamente diagnóstico, não aprovação independente.

```powershell
.\mvnw.cmd -q -pl flatcam-application dependency:build-classpath '-Dmdep.includeScope=test' '-Dmdep.outputFile=../target/ncc-connect-classpath.txt'
$connectProbeClasspath = 'flatcam-cam/target/classes;' + (Get-Content target/ncc-connect-classpath.txt -Raw).Trim()
java --class-path $connectProbeClasspath tools/NccConnectProbe.java target/ncc-connect-stages.json
.\target\oracle-py311\Scripts\python.exe tools/compare_ncc_connect_probe.py --legacy-root .. --trace target/ncc-connect-stages.json --output target/ncc-connect-stages-geos.json
```

Rest: o harness compila os corpos originais de `clear_copper`/`gen_clear_area`
e `gen_clear_area_rest` e seus helpers por AST. Não usa as funções homônimas de
`clear_copper_tcl`, cujo Rest é diferente. Bindings representam apenas campos,
signals, defaults de runtime e objeto de saída headless. Comparações observam
também a ordem real de processamento, não a ordem de inserção do dict Python.

O FX alinha o footprint restante ao painel: resolução 16, raio `tool/1.9999999`,
reparo `+1e-7` nas unidades atuais e subtração robusta moderna. Polígonos onde
a fresa não cabe ficam para a menor sem contar como falha do algoritmo. Paint
mantém sua política conservadora anterior. A margem minúscula de reparo é uma
convenção numérica do legado, não uma comprovação da remoção física do material.

O corpus ganha oito casos por unidade: múltiplas ferramentas em ordem
None/Forward/Reverse, configurações próprias por ferramenta, Rest Itself/Area/
Reference Geometry e Rest com Connect. **27 casos MM e 27 IN**. Cada fresa tem
seu CAM, G-code interpretado, estado vazio e atribuição conferidos separadamente;
a união não pode esconder uma ferramenta ausente/trocada. O programa conjunto
usa o gerador multi-tool real, com trocas e metadados, não uma união emitida
como se houvesse uma única fresa. ISO combinado,
Rest Seed/Lines/Combo e matrizes maiores de parâmetros ainda não estão cobertos.

Resultados sintéticos completos: **MM 24 MATCH_SAMPLED, 1 DIFFERENT (Seed),
2 GCODE_DIFFERENT (Lines e multi-settings); IN 25 MATCH_SAMPLED, 1 DIFFERENT
(Seed), 1 GCODE_DIFFERENT (multi-settings)**. Zero ORACLE_ERROR e
PARTIAL_DIFFERENCE. Não transformar 49/54 em porcentagem geral de paridade.
Strict completo continua reprovado. Seleção de Connect, três ordens multi-tool
e quatro variantes Rest: **16/16 MM+IN passam strict**.

Seed conserva a política estável documentada em `INVESTIGACAO_CAM.md`; não foi
alterado para perseguir o ponto instável do Python. Diferenças de G-code têm
distância pequena mas comprimento da união distinto após quantização/coincidência
de segmentos. Essa é uma hipótese a investigar com rastreamento de movimentos;
os critérios originais não foram relaxados nem tais saídas declaradas aprovadas.

### Projeto real: ainda não estabelece paridade

Executados nove casos NCC no projeto denso do usuário, somente lido, sem
modificar o `.FlatPrj`: **2 MATCH_SAMPLED, 7 DIFFERENT, zero ORACLE_ERROR**.
Rest Area e Rest Reference Geometry passam; Standard/Connect, três ordens
multi-tool e Rest Itself/Connect reprovam. A fonte decodificada independentemente
tem delta de área zero, mas isso não garante preparo/limite equivalente.

Nos casos Itself, a área inicial apresenta diferença simétrica de cerca de
0,00035018 mm²; Standard tem distância amostrada pequena (0,00064324 mm),
mas o critério de área reprova. Connect chega a 0,15306 mm, Rest a 0,08801 mm
e Rest Connect a 0,20477 mm. Preparação/reparo/contêiner do cobre e limite
Itself são a próxima investigação, não uma causa já demonstrada. O Java
preserva coleção genérica com MultiPolygon; o Python mantém seu contêiner
original de lista. Não normalizar o oráculo para encobrir essa divergência.

Esta execução privada antecedeu a última melhoria do exportador para o
programa conjunto multi-tool; os CAMs e programas individuais foram conferidos,
mas o programa conjunto mais recente foi repetido somente no corpus sintético.
Nenhuma validação manual de UI ou máquina CNC realizada. Strict real reprova;
o êxito sintético não deve ser anunciado como paridade dessa placa.

Relatórios locais: comparação sintética completa mais recente em
`target/main-flow-comparison-20261008-093616-558`; probe independente em
`target/ncc-connect-stages-complete-geos.json`; execução focada sintética em
`target/main-flow-comparison-20261008-092350-048`; projeto privado em
`target/main-flow-comparison-20261008-092705-701`. Resultados privados não são
versionados e os arquivos de origem não foram alterados.

Verificação: `mvnw.cmd -q verify` passou, 1473 testes registrados, 1461 aprovados,
12 opcionais ignorados e zero falhas/erros. Os 25 testes auxiliares Python passam
com Shapely 1.8 e 2. Probes nativos Direct3D/Intel Arc e software passam; a
referência incompatível é recusada antes da exportação.

## Correções decorrentes do corpus — 2026-10-07

XY nos programas G-code comuns agora usa seis casas decimais em IN/INCH,
mantendo quatro em MM. Abrange corte/travel Geometry, laser, furos/rasgos,
parking, troca e desvios de exclusão. Feeds, Z, diâmetros/metadados e formatos
específicos HPGL/Roland/ISEL conservam suas políticas anteriores. Nenhum
programa já salvo é reescrito; a mudança vale ao gerar novos programas.

Causa reproduzida para Reference Geometry IN: arredondar as 27 linhas de CAM
a quatro casas, antes mesmo de usar o parser, aumenta o comprimento de
45,76279217 in para 45,88207167 in. Com seis casas resulta em 45,76281894 in.
A comparação original com o parser Python, inclusive o critério de comprimento
de 0,1%, agora passa. Não foi relaxado nenhum critério ou removido o cenário.

Connect agora testa a pegada da fresa contra o polígono original, como
`paint_connect`; antes erodia o polígono e depois testava um buffer do raio,
descontando o raio duas vezes. Começa pela linha com endpoint mais próximo de
(0,0), preservando sua orientação inicial, e exige `within` sem ampliar a área
com a antiga tolerância de 1e-10. Mantém max_walk = 10 × diâmetro e validação
da ferramenta inteira, não apenas do segmento central.

Isso corrige a lógica de conexão e iguala o comprimento no caso sintético,
mas **Connect ainda é DIFFERENT**: os polígonos numericamente equivalentes
das operações booleanas começam os anéis em cantos diferentes no JTS/GEOS;
conectores terminam em posições diferentes. MM: distância amostrada ~0,14217 mm,
comprimento relativo ~2,1e-16 e IoU ~99,99993%. Não foi aplicada uma troca de
canto arbitrária só para aprovar este fixture. Padronização dos pontos iniciais
ou port mais amplo da preparação GEOS exige corpus adicional.

Nova execução completa com Shapely 2.1.2: **14 MATCH_SAMPLED, 1 DIFFERENT,
4 ORACLE_ERROR por conjunto MM/IN** (28/38 atendem aos critérios). Não resta
`GCODE_DIFFERENT` nestes casos. Strict completo ainda reprova por Connect e
pelas oito falhas do legado; não representa paridade total ou ensaio de máquina.
Regressões novas cobrem formatação, CAM denso serializado/interpretado em MM/IN,
laser/Drilling/parking IN, origem Connect, conexão end-to-end sem dupla erosão
e recusa de pegada que sai da área mesmo por menos que a tolerância antiga.

Verificação: `mvnw.cmd -q verify`, 1463 registrados, 1451 aprovados,
12 opcionais ignorados, zero falhas/erros. Probes nativos Direct3D/Intel Arc
e software passam. Strict focado Reference Geometry passa em MM e IN;
strict completo continua reprovado pelos casos explicitados acima.

## Ampliação dos fluxos principais — 2026-10-07

O exportador agora fornece 19 casos por conjunto sintético, em MM e IN:
os dez históricos mais Isolation Exterior/Interior/Follow/exceções e NCC
Connect/sem Contour/Area/Reference Gerber/Reference Geometry. Referências
extrapolam o cobre; as duas referências de objetos são côncavas para exercitar
a diferença entre convex hull Gerber e forma Geometry. Uma placa real sem
anéis internos omite somente o caso Interior vazio, sem contá-lo como aprovação.
`fx-cam.json` mantém o contrato anterior; `fx-cam-in.json` é o segundo conjunto
sintético, mesmo quando o primeiro usa um projeto real.

Follow usa uma entrada de linhas explicitamente compartilhada, não a validação
de toda a reconstrução de follow_geometry de um Gerber. NCC ainda não compara
Rest, ISO combinado ou múltiplas ferramentas neste harness.

Além dos métodos CAM, são compilados da AST original `area_subtraction` e
`poly2rings` de ToolIsolation, e `calculate_bounding_box`/
`apply_margin_to_bounding_box` de ToolNCC. Corpos não são alterados; bindings
headless substituem apenas o contexto UI. O relatório registra o contêiner
original da fonte: Python trata Polygon e lista de um Polygon de modo diferente
no ramo Itself. Decodificação de projeto conserva esse contêiner; não o achata
para fabricar concordância com o FX. O limite/área NCC é comparado separadamente.

O parser Python também compara os cortes XY interpretados do G-code FX com os
caminhos exportados. `camMatchesSampledCriteria` e `gcodeMatchesSampledCriteria`
separam as verificações. `GCODE_DIFFERENT` identifica CAM concordante, mas
programa interpretado fora dos critérios; também reprova strict. Não se trata
de comparação de todas as alturas/feeds/macros de controlador nem segurança CNC.

### Executar com um comando

Na pasta `flatcam-next`:

```powershell
.\compare-main-flows.ps1 -Strict
.\compare-main-flows.ps1 -Project 'CAMINHO_COMPLETO_DO_PROJETO.FlatPrj' -Strict
# Seleção focada, sem contar os casos omitidos como aprovados:
.\compare-main-flows.ps1 -Cases 'isolation-exceptions,ncc-area,ncc-reference-gerber' -Strict
```

O runner encontra o Python como o launcher de profiling; aceita `-Python`
explícito ou `FLATCAM_PROFILE_PYTHON`, e `-DependencyPath` para bibliotecas
isoladas já instaladas. Não instala dependências nem inicia interfaces.
Constrói apenas os módulos necessários com `-am`, exporta ambos os conjuntos
e produz HTML/JSON/SVG/WKT/NC e logs numa pasta nova ignorada em `target/`.
`-OutputDirectory` deve ficar dentro desse target e não existir previamente;
relatórios antigos não são sobrescritos. Avisos em stderr não são tratados
como falha por si só; usa o código de saída nativo.

Saída 0: relatório normal concluído (ou strict aprovado); 1: strict reprovado;
2: falha de infraestrutura/entrada. Sem strict, um relatório de divergências
termina normalmente, mas as divergências permanecem explícitas. Para política
PowerShell restrita, é possível executar em processo local com
`powershell -NoProfile -ExecutionPolicy Bypass -File .\compare-main-flows.ps1 -Strict`,
sem mudar a política global.

### Resultado desta execução sintética

Python 3.12.0, Shapely 2.1.2, GEOS 3.13.1: MM teve **14 MATCH_SAMPLED,
1 DIFFERENT, 4 ORACLE_ERROR**; IN teve **13 MATCH_SAMPLED, 1 DIFFERENT,
1 GCODE_DIFFERENT, 4 ORACLE_ERROR**. Strict reprova corretamente ambos.
Todos os 38 G-codes FX foram interpretados com cortes pelo parser Python;
isso não garante concordância numérica de todos os cortes.

Connect diverge nos conectores, apesar de IoU próxima de 100%; no MM,
distância amostrada ~0,13937 mm e comprimento relativo ~0,12249%.
Reference Geometry IN tem caminhos CAM concordantes; os cortes do G-code
interpretado diferem ~0,25953% em comprimento, embora bounds/distância estejam
dentro da tolerância. Investigar discretização/arredondamento e sobreposições
antes de atribuir causa ou alterar precisão de produção.
Seed/Lines e Cutout com quatro pontes falham no oráculo Shapely 2; falhas
não são paridade aprovada. Sem projeto privado ou teste de FPS nesta execução.

Treze testes Python do harness aprovados, incluindo oráculos AST, seleção,
semântica do contêiner e classificação separada CAM/G-code. Os cenários Java
exigem prévia disponível para cada caso MM/IN. `mvnw.cmd -q verify` aprovado:
1456 registrados, 1444 aprovados, 12 opcionais ignorados, zero falhas/erros.
Runner verificado também com pasta contendo espaços e strict focado aprovado
em Exceptions/Area/Reference Gerber (seis comparações, MM/IN); a suíte completa
continua reprovando. Próximo: investigar Connect e
G-code IN, repetir com bibliotecas legadas isoladas/projetos reais, depois Rest
e múltiplas ferramentas. Critérios não foram relaxados para obter aprovação.

## Nova execução sintética (2026-10-07)

Na consolidação dos cinco fluxos, Isolation 1/3 passadas e NCC Standard foram
reexecutados com Python 8.994 e Shapely 2.1.2: três `MATCH_SAMPLED`, modo estrito
aprovado. NCC Seed/Lines resultaram em dois `ORACLE_ERROR` pela incompatibilidade
multipart do legado com Shapely 2, não aprovações de equivalência. Não se alterou
o algoritmo Python nem suas dependências. Relatórios locais ignorados em
`target/five-flows-cam-comparison/report` e `report-seed-lines`. Não foi usada
placa real nesta execução; seguem pendentes Rest/exceções e combinações avançadas
na comparação diferencial. Os testes FX cobrem integração desses parâmetros.

O harness exporta caminhos e G-code do FX e executa rotinas reais do checkout
Python sobre as mesmas entradas, sem iniciar interfaces ou mudar preferências.
Projetos, WKT, G-code e imagens privados ficam em `target/`, ignorado pelo Git.
O original `.FlatPrj` é somente lido. O Python não é dependência de execução do FX.

## Escopo

Dez casos: Isolation BOTH com 1/3 passes, NCC Standard/Seed/Lines, Paint Standard
em uma área explicitamente compartilhada e Cutout retangular com margem 0/1 mm,
sem gaps/com quatro gaps. Diâmetros de 0,1/0,5/1 mm e sobreposição de 15%/40%;
valores convertidos quando a fonte é IN. O projeto opcional precisa conter um
Gerber B_Cu; sem projeto, o teste Java usa uma placa sintética com vazio interno.

Com projeto, o Python decodifica o `.FlatPrj` independentemente e verifica o cobre
antes do CAM. Isolation usa `Geometry.isolation_geometry`, inclusive o epsilon
do ramo normal de ToolIsolation. NCC reproduz a seleção Itself/margem mitrada e
chama `clear_polygon*`; Paint chama essas mesmas rotinas na área selecionada.
Cutout compila o handler retangular e seus helpers diretamente da AST do fonte
legado, sem modificar os corpos. Isso evita inicialização de plugins/CLI, mas
**não executa todo o fluxo de cliques do plugin**. Arquivos-fonte têm SHA-256 no
relatório para identificar precisamente o oráculo usado.

O parser real `CNCjob.gcode_parse` verifica que cada G-code FX pode ser interpretado
e contém cortes. Isso não equivale a comparar todos os feeds, macros, posições e
alturas nem a certificar execução segura na máquina.

Critérios numéricos conservadores: distância simétrica **amostrada** em 513 pontos
por direção e bounds até 0,003 mm equivalente; comprimento relativo até 0,1%;
interseção/união da pegada da ferramenta ≥99,5% (buffer de resolução 16).
Não é Hausdorff exata nem prova de topologia/safety. Caminhos usam WKT completo;
SVG é simplificado somente para exibição. Ordem/orientação dos caminhos não são
critério de igualdade. Falhas do legado não contam como aprovações.

`MATCH_SAMPLED` atende esses critérios; `DIFFERENT` não; `PARTIAL_DIFFERENCE`
identifica contagem diferente de polígonos não usináveis; `ORACLE_ERROR` registra
exceção ou saída vazia. O modo normal sempre produz um relatório dos casos;
`--strict` retorna código 1 se qualquer caso não atender aos critérios.

## Executar no PowerShell

Na pasta `flatcam-next`, com o reactor instalado (incluindo o suporte de testes):

```powershell
.\mvnw.cmd -q install
$comparisonOutput = Join-Path (Get-Location) 'target/cam-comparison-real'
$comparisonProject = 'CAMINHO_COMPLETO_DO_PROJETO.FlatPrj'
.\mvnw.cmd -q -pl flatcam-application -Dtest=PythonCamComparisonExportTest "-Dflatcam.python.project.fixture=$comparisonProject" "-Dflatcam.cam.comparison.output=$comparisonOutput" test
..\.venv\Scripts\python.exe tools/compare_cam_python.py --legacy-root .. --project "$comparisonProject" --fx-export "$comparisonOutput/fx-cam.json" --output "$comparisonOutput/report" --strict
..\.venv\Scripts\python.exe -m unittest discover -s tools -p test_compare_cam_python.py
```

O relatório é `index.html`, com sobreposições azul (FX)/laranja (Python), métricas
JSON e G-code `.nc` para inspeção. Não execute esses arquivos diretamente na CNC:
são exemplos de comparação, não parâmetros recomendados para sua máquina.

Para investigar casos específicos, acrescente `--cases ncc-standard,ncc-seed,paint-standard`.
O relatório registra os IDs selecionados. Cada caso também salva WKT completo
FX/Python e `distanceWitness`: lado, ponto de maior distância **amostrada** e
ponto mais próximo no outro caminho. Isso permite localizar a divergência sem
alterar os critérios. Esses arquivos também contêm geometria privada e devem
permanecer em `target/`.

O checkout legado ainda possui iteração multipart incompatível com Shapely 2 em
Seed/Lines e no segundo recorte de gaps do Cutout. É possível testar uma biblioteca
legada em pasta isolada, sem alterar `.venv` ou os algoritmos Python:

```powershell
..\.venv\Scripts\python.exe -m pip install --target target/legacy-shapely185 --no-deps --only-binary=:all: 'shapely==1.8.5.post1'
..\.venv\Scripts\python.exe tools/compare_cam_python.py --legacy-root .. --dependency-path target/legacy-shapely185 --project "$comparisonProject" --fx-export "$comparisonOutput/fx-cam.json" --output "$comparisonOutput/report-shapely185" --strict
```

O wheel depende de Python/plataforma compatíveis. A versão realmente carregada
consta do relatório. Não sobrescreva uma pasta de dependências já utilizada.

Os novos relatórios também registram a versão GEOS efetivamente carregada;
Shapely sozinho não identifica o kernel numérico. A investigação posterior
dos buffers e do ponto inicial Seed, com controles públicos reproduzíveis,
está em [INVESTIGACAO_CAM.md](INVESTIGACAO_CAM.md). Os protótipos dessa
investigação não alteram os resultados entregues pela aplicação.

## Correção decorrente da comparação

Standard (NCC/Paint) agora usa o epsilon inicial `diâmetro / 1.999999` do Python e
desativa a simplificação de entrada dos buffers de erosão JTS. Seed/Lines e a
região segura dos conectores também preservam detalhes; Lines usa o epsilon
`diâmetro / 1.99999999` no inset, como o legado. A simplificação
padrão de 1% do offset apagava pequenas concavidades/estreitamentos; erosões
sucessivas amplificavam a diferença em passes ausentes na placa real.

Na comparação com Shapely 2.1.2, a IoU da pegada NCC Standard passou de 98,8652%
para 99,999255%; Paint, de 98,8653% para 99,999291%. Comprimento difere cerca de
0,018%, mas ainda existem diferenças amostradas de aproximadamente 0,021 mm:
ambos continuam classificados `DIFFERENT`, não paridade exata. São medições
deste exemplo, não porcentagem de paridade do aplicativo nem benchmark de FPS.

Testes sintéticos protegem o epsilon e um pequeno entalhe côncavo em todos os
métodos, além dos casos
reais opcionais. Cálculo continua no worker existente. A precisão pode aumentar
o custo geométrico; não foi medido ganho de desempenho.

## Resultado inicial no projeto real autorizado — 2026-10-02 (9e216530)

Python 3.11.0 com Shapely 1.8.5.post1 isolado: **6 casos atendem aos critérios,
4 permanecem diferentes, nenhuma exceção do oráculo**. O modo strict retorna 1,
corretamente. O cobre decodificado independentemente tem diferença de área zero.
O parser Python interpretou os dez G-codes FX e encontrou cortes em todos.

| Caso | IoU da pegada | Maior distância amostrada | Resultado |
| --- | --- | --- | --- |
| Isolation, 1/3 passes | >99,999% | ≤0,00191 mm | MATCH_SAMPLED |
| NCC Standard | 99,99922% | 0,02059 mm | DIFFERENT |
| NCC Seed | 99,99606% | 0,12928 mm | DIFFERENT |
| NCC Lines | 99,99520% | 0,00065 mm | MATCH_SAMPLED |
| Paint Standard | 99,99926% | 0,02159 mm | DIFFERENT |
| Cutout sem gaps, margem 0/1 mm | ≥99,9935% | ≤0,00159 mm | MATCH_SAMPLED |
| Cutout quatro gaps, margem 0 | 99,99908% | 0,00040 mm | MATCH_SAMPLED |
| Cutout quatro gaps, margem 1 mm | 96,96954% | 1 mm | DIFFERENT |

O Cutout FX mantém gaps centrados no contorno ampliado; o handler retangular
Python soma a margem ao centro, deslocando-os. Essa diferença existente não foi
alterada nesta etapa: exige revisar também os limites das bandas, Thin e M-Bites,
pois copiar somente o deslocamento pode eliminar bridges quando a margem cresce.
Não tratar essas saídas como intercambiáveis sem conferir as posições.

Seed/Standard ainda diferem nos caminhos, apesar de cobertura próxima. Na execução
com Shapely 2.1.2, Seed/Lines e quatro gaps falham por incompatibilidades multipart
do legado; isso fica registrado separadamente, sem mascarar com patches.
Isolation 3 passes e Seed atingiram o limite protetor do parser de prévia detalhada
do FX; os caminhos CAM e a geração de G-code foram produzidos, mas o parser não
disponibilizou essa prévia. Não é afirmação sobre a visibilidade do plot original.
Esse é o resultado histórico daquela execução: em 2026-10-07 o descarte por
50 mil segmentos foi substituído por montagem da prévia em blocos. O harness
Java agora exige prévia disponível; isso não muda os resultados CAM históricos.

Regressão Java: 779 registrados, 768 aprovados, 11 opcionais ignorados, zero
falhas/erros. Seis testes Python do harness aprovados; cinco smoke tests CAM com
o projeto real aprovados separadamente. Testes headless, não conferência da CNC.

Próximos casos do corpus: Rest/múltiplas ferramentas, Connect, referências/áreas,
Follow/exceções, free-form/Thin/M-Bites e IN real; comparar movimentos/alturas CNC
e validar a interação dos painéis no aplicativo. A limitação de tamanho da
prévia registrada nesta execução foi substituída no incremento de 2026-10-07.

## Continuação: término dos anéis Seed e diagnóstico — 2026-10-02

Seed agora encerra ao primeiro anel que não intersecta a região erodida, como
`clear_polygon2`. Antes, o FX continuava expandindo e podia voltar a encontrar
uma ilha distante depois de um anel vazio. Um teste sintético com duas regiões
ligadas por um pescoço fino verifica o término e que Contour ainda inclui todos
os componentes. Sem Contour, o método legado pode deixar ilhas sem preenchimento:
escolha outro método/Confira a saída se precisar cobrir toda a região.

A investigação de Standard/Paint localizou uma divergência após erosões
sucessivas: no Paint do exemplo, um ponto do caminho FX fica aproximadamente
0,02159 mm do Python na região da 16ª erosão. Não foi atribuída uma causa
definitiva nem removida essa divergência. O ponto está registrado no relatório
local, sem publicar coordenadas/geometria do projeto privado no repositório.
Os critérios permanecem iguais, sem transformar cobertura semelhante em
declaração de paridade exata.

## Continuação: Cutout retangular com margem — 2026-10-02

Os gaps retangulares agora usam a referência do handler Python: centro da origem
somado à margem, quartos `(dimensão original + 2 × margem) / 4`, sem o raio da fresa no
espaçamento. Thin e M-Bites acompanham essas posições. Bandas atravessam todo o
contorno ampliado; padrões que perdem pontes solicitadas são recusados. Isso
intencionalmente evita reproduzir a perda de bridges do legado com margem alta.
Thin é extraído diretamente pelas máscaras, sem fragmentos espúrios de diferenças
entre contornos recortados nos cantos arredondados. Free-form ainda não foi
alinhado nesse aspecto. Limites e diferenças explícitas em [CUTOUT.md](CUTOUT.md).

Nova comparação completa, usando o projeto original decodificado independentemente
e Shapely 1.8.5.post1 isolado: **7 MATCH_SAMPLED, 3 DIFFERENT, zero ORACLE_ERROR**.
Cobre continua com diferença de área zero; parser Python encontrou cortes nos dez
G-codes. Cutout quatro gaps/margem 1 mm passou de distância amostrada 1 mm para
**0,001770 mm**, IoU de **96,96954% para 99,99329%** e diferença relativa de
comprimento de ~0,00110%. Isso satisfaz os critérios, não igualdade exata.

NCC Standard/Paint permanecem aproximadamente 0,02059/0,02159 mm distantes nas
amostras; Seed, 0,12928 mm, apesar do término alinhado. O modo strict continua
retornando 1 pelas três divergências. Próximas investigações: buffers sucessivos
de Standard e escolha do ponto interior/arcos Seed, seguidas de corpus Free-form,
Thin/M-Bites no Python, Rest, Connect e referências.

Verificação desta continuação: núcleo CAM com 521 testes aprovados em modo
normal; execução funcional do reactor com 787 registrados, 776 aprovados e
11 opcionais ignorados. Essa execução completa usou
`-Djunit.jupiter.tempdir.cleanup.mode.default=NEVER` e uma pasta temporária
local em `target/` via `argLine`: os temporários ficam preservados. O modo normal
falhou repetidamente ao limpar diretórios temporários Windows, em testes distintos,
sem falha nas asserções. Não foi alterada a configuração permanente nem declarada
aprovação do build normal. Investigar essa infraestrutura separadamente.
Cinco smoke tests CAM com o projeto real e sete testes Python do harness também
passaram; não substituem teste visual/manual ou execução a seco na máquina.
