# Excellon incremental e roteamento circular — 2026-10-27

## Leitura

- `G91`, em linha própria, acumula os X/Y informados sobre a posição anterior;
  eixo omitido não se move. A posição inicial é `(0,0)`. `G90` restaura absoluto.
- A mesma regra vale em furos, movimentos de roteamento e G85: neste último,
  primeiro se resolve o início, depois o destino a partir desse início.
- Decimais explícitos e formatos/zeros declarados continuam passando pelo
  mesmo decodificador. Raio/offsets de centro não são somados à posição XY.
- `G02` é horário e `G03` anti-horário, com raio `A` positivo ou par `I/J`.
  Movimento e parâmetros circulares são modais; parâmetros podem vir antes
  do bloco com o destino. `G00` posiciona, M15 baixa, M16/M17 sobem e G05 volta
  à furação. Arco com ferramenta levantada só atualiza a posição.
- O texto CNC-7 descreve I/J do centro ao início. O leitor também aceita o
  vetor oposto quando os dois extremos selecionam uma única circunferência.
  Se ambas as interpretações cabem na tolerância, recusa e pede raio A.
  Componentes I e J devem estar ambos explícitos, inclusive zero.
- Arcos devem ter extremos distintos e abertura até 180°. Círculos precisam
  de dois arcos. Raio impossível, centro inconsistente, parâmetros conflitantes,
  coordenadas não finitas/overflow e palavras repetidas são recusados.
- M30 encerra a leitura; texto posterior não cria outros furos.

Referências primárias: [Ucamco XNC, seções 3.13–3.14](https://www.ucamco.com/the_xnc_file_format_specification.pdf)
e [manual CNC-7 da Excellon, cópia arquivada, páginas 12–13 e 21](https://static1.squarespace.com/static/54982a02e4b02e9f5e5d9ca7/t/577e77209f74568b965f785e/1467905825798/Excellon%2BAutomation%2BCo.pdf).
O FX recusa um raio impossível em vez de ajustar silenciosamente a curva.
Isso não implementa toda a linguagem CNC-7/XNC: ciclos G32/G33, compensação,
repetições e mudanças de origem não fazem parte deste incremento.

## Representação e precisão

Cada arco é um slot com uma lista imutável de pontos da linha central. Não
vira um slot reto entre os extremos nem um conjunto de furos. A aproximação
tem orçamento de erro de corda de **0,001 mm** (equivalente em IN), incluindo
reserva numérica. Verifica cancelamento na amostragem. Limite de 16.384 segmentos
por arco; entradas além disso ou grandes demais para precisão double são
recusadas. Tolerância e limite poderão ser configuráveis futuramente.

O orçamento vale na importação: escala/inclinação posteriores e arredondamento
de coordenadas do pós-processador alteram a precisão final. Não há preservação
analítica de G02/G03 após a leitura; CNC e exportação usam os segmentos.

## Fluxos que conservam o trajeto

Plot, seleção/edição, mover/copiar, redimensionar diâmetro, undo/redo,
transformações, panelização, Join, conversão para Gerber, Rules Check,
alinhamento e Milling usam a linha central completa. A tabela do editor distingue
`Routed slot`. Calibration continua permitindo pegar os centros dos extremos.

CNC percorre todos os vértices com um mergulho/retração por passe do arco,
mantendo unidades, avanço e pós-processador. Exclusões examinam a curva inteira,
incluindo o raio/margem existentes. Drill slots distribui os furos pelo comprimento
da linha, sem cortar pela corda. O resumo CNC informa a aproximação dos trajetos.

## Arquivos e compatibilidade

- **`.fcnproj` com trajetos curvos usa versão 3**, para o leitor antigo de versão
  2 recusar o arquivo em vez de perder o caminho. Projetos sem esses trajetos
  continuam gravados como v2; o leitor novo aceita v1/v2/v3. Os pontos ficam
  como doubles JSON exatos em `_fx_path`; a geometria de exibição mantém o WKT
  e a precisão já usados pelo projeto. Dados de caminho inválidos recusam a leitura.
- **`.FlatPrj` é recusado para esses objetos**: o modelo de slots do Python
  só representa os extremos. A recusa ocorre antes de substituir o destino.
  Use `.fcnproj`, ou exporte o Excellon roteado.
- **Exportar Excellon roteado** escreve os segmentos entre um M15 e M16;
  **G85 é recusado** para trajetos curvos. A janela de exportação seleciona e
  mantém Roteado nesses objetos. Terminal/API recebem a mesma recusa para G85.
  Na reimportação do arquivo linearizado, cada G01 vira um slot linear, como no
  leitor anterior; a geometria permanece, mas contagem/agrupamento dos slots e
  número de mergulhos numa geração CNC posterior podem mudar. O projeto nativo
  é o formato que conserva o agrupamento e os pontos.

## Evidência

`ExcellonAdvancedRoutingTest`: G90/G91, eixos omitidos, LZ/TZ e IN/MM, G85,
modalidade, A/IJ, sentido, semicircunferências, erro físico dos segmentos,
tool-up, M30, entradas inválidas, limite e cancelamento.
`ExcellonCurvedFlowTest`: edição/histórico/ferramentas, transformações/painel/Join,
Gerber/Buffer/Milling, exportar/reler, CNC reanalisado, exclusões na curva e
fora dela, Drill slots pela linha. `ExcellonCurvedPersistenceTest`: arquivo
nativo, caminho inválido e recusa de exportação Python sem sobrescrever arquivo.

O probe `tools/probe_excellon_routing_python.py` usa o leitor Python ORIGINAL,
com o stub de defaults/sinais já existente, sem janela ou arquivos do usuário.
Em quatro controles públicos: **1 MATCH_LINEAR, 1 DIFFERENT, 2
LEGACY_CURVE_UNSUPPORTED**. Em G91, o Python retornou `(1,-2)` para o segundo
furo onde o acumulado é `(11,18)`; não criou os dois arcos. Esses resultados
nunca contam como paridade. Fonte e hashes ficam no relatório.

```powershell
.\mvnw.cmd -pl flatcam-application -am '-Dtest=Excellon*Test,DrillSlotsTest,DrillingExclusionTest,ProjectFileIOTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
# Use o Python do ambiente legado com as dependências instaladas:
python tools/probe_excellon_routing_python.py
```

Os arquivos sintéticos e CSV ficam em `flatcam-cam/target/excellon-routing-controls`;
relatório em `target/excellon-routing-python.json`. `--strict` reprova qualquer
diferença/limite do legado. Não houve nova sessão GUI comparativa nem ensaio em
controlador/máquina; testes de leitura/CAM não demonstram equivalência física.

Integral final: **2048 registros, 2030 aprovações, 18 skips opcionais, zero
falhas/erros**, em `target/excellon-advanced-routing-final-verify.log` e JSON
homônimo. O contrato de exceção antigo de parâmetros inválidos de Drilling foi
preservado; as asserções anteriores não foram alteradas.
Launcher nativo reconstruído, dependências sincronizadas e probe aprovado:
`target/excellon-routing-native-build.log`. Probe sem janela não valida gestos.
