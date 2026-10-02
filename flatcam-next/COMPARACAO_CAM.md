# Comparação reproduzível CAM: FX × Python

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

Na pasta `flatcam-next`, com os três módulos instalados:

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

O checkout legado ainda possui iteração multipart incompatível com Shapely 2 em
Seed/Lines e no segundo recorte de gaps do Cutout. É possível testar uma biblioteca
legada em pasta isolada, sem alterar `.venv` ou os algoritmos Python:

```powershell
..\.venv\Scripts\python.exe -m pip install --target target/legacy-shapely185 --no-deps --only-binary=:all: 'shapely==1.8.5.post1'
..\.venv\Scripts\python.exe tools/compare_cam_python.py --legacy-root .. --dependency-path target/legacy-shapely185 --project "$comparisonProject" --fx-export "$comparisonOutput/fx-cam.json" --output "$comparisonOutput/report-shapely185" --strict
```

O wheel depende de Python/plataforma compatíveis. A versão realmente carregada
consta do relatório. Não sobrescreva uma pasta de dependências já utilizada.

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

## Resultado no projeto real autorizado — 2026-10-02

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

Regressão Java: 779 registrados, 768 aprovados, 11 opcionais ignorados, zero
falhas/erros. Seis testes Python do harness aprovados; cinco smoke tests CAM com
o projeto real aprovados separadamente. Testes headless, não conferência da CNC.

Próximos casos do corpus: Rest/múltiplas ferramentas, Connect, referências/áreas,
Follow/exceções, free-form/Thin/M-Bites e IN real; comparar movimentos/alturas CNC
e validar a interação dos painéis no aplicativo. Prévia detalhada editada mantém
o limite protetor de tamanho; o relatório registra quando ele é atingido.
