# Matriz de paridade FlatCAM FX × FlatCAM Python — 2026-10-10

Revisão `a71294f1` + harness das ferramentas secundárias do FX (branch `flatcam-next`), checkout Python deste repositório. Complementa
[PLANO_PARIDADE.md](PLANO_PARIDADE.md) (o que fazer) e [COMPARACAO_CAM.md](COMPARACAO_CAM.md)
(histórico das comparações). Aqui fica **o que foi medido e com que evidência**.

## Como ler

Paridade não tem um número único. Cada linha tem um **nível de evidência**:

| Nível | Significado |
| --- | --- |
| **A** | Comparação diferencial contra o **código real do Python** (harnesses `compare-main-flows.ps1` e `compare-secondary-tools.ps1`: mesmas entradas, rotinas originais compiladas da AST do checkout, Python 3.11 + Shapely 1.8.5 + GEOS 3.10.3). |
| **B** | Comparação contra o **algoritmo do Python reescrito** num script (mesmos laços e chamadas shapely), com a geometria exportada do FX. Mede o resultado, mas o script não é o código original. |
| **C** | Só **testes do FX** com expectativas derivadas da leitura do código Python. Nenhuma execução do Python. |
| **D** | Implementado, sem verificação de resultado contra o Python (ou só validação manual pendente). |
| **—** | Não portado. |

Um `MATCH_SAMPLED` significa: distância amostrada entre os caminhos, comprimento relativo,
limites e pegada da ferramenta dentro das tolerâncias do harness (513 amostras por direção,
comprimento relativo ≤ 0,1%). Não é igualdade geométrica vértice a vértice, nem validação de
alturas/avanços/macros do G-code, nem de interface.

## Resultado desta execução (nível A)

`compare-main-flows.ps1 -LegacyCompatible`, 28 casos por conjunto. Relatórios em
`target/parity-matrix-synthetic-20261010` e `target/parity-matrix-real-20261010` (ignorados pelo Git).

| Conjunto | MATCH_SAMPLED | DIFFERENT | GCODE_DIFFERENT | ORACLE_ERROR |
| --- | --- | --- | --- | --- |
| Sintético MM | 27 | 1 | 0 | 0 |
| Sintético IN (rodado duas vezes, mesmo resultado; contado uma vez) | 27 | 1 | 0 | 0 |
| Projeto real (MM, `Project_20260917_160328.FlatPrj`) | 26 | 1 | 0 | 1 |

- O único `DIFFERENT` é sempre **`ncc-seed`**, e é **deliberado**: o FX usa por padrão um ponto
  inicial estável para os anéis Seed; o caso `ncc-seed-python` (política "Representativo (Python)")
  passa nos três conjuntos. Distância amostrada ~0,149 mm (MM), comprimento relativo 0,07%–0,24%.
  Ver [NCC_SEED.md](NCC_SEED.md).
- O `ORACLE_ERROR` do projeto real é **`ncc-multi-settings`**: o próprio Python lança
  `ValueError: Sequences of multi-polygons are not valid arguments` nessa entrada; não é uma
  divergência do FX nem uma aprovação. O mesmo caso passa nos dois conjuntos sintéticos.
- Contando só casos comparáveis (excluindo o erro do oráculo): **80 de 83 execuções concordam
  (96,4%)**; excluindo também a diferença deliberada do Seed, **80 de 80**.

Casos por operação (iguais nos três conjuntos, salvo indicado):

| Operação | Casos | Resultado |
| --- | --- | --- |
| Isolation | 1 e 3 passadas, Exterior, Interior, exceções, Follow | 6/6 |
| NCC | Standard, Lines, Seed (Python), Connect, sem Contour, Area, Reference Gerber, Reference Geometry, 3 ordens de múltiplas ferramentas, multi-settings, Rest, Rest Area, Rest Reference, Rest Connect | 16/16 (real: 15/15 + 1 erro do oráculo) |
| NCC Seed (padrão FX) | 1 | diferente de propósito |
| Paint | Standard | 1/1 |
| Cutout | margem 0 e com margem, sem pontes e com quatro | 4/4 |

O harness também interpreta com o parser do Python os cortes XY do G-code gerado pelo FX
(`GCODE_DIFFERENT` = 0 nos três conjuntos).

## Ferramentas secundárias no harness (nível A) — 2026-10-10

`compare-secondary-tools.ps1 -LegacyCompatible -Strict` exporta o que o FX produz
(`PythonToolsComparisonExportTest`) e roda os **corpos originais** dos métodos do Python
(`tools/compare_tools_python.py`): as seis regras estáticas de `ToolRulesCheck.py`;
`copper_thieving`, `on_add_robber_bar_click` e `on_new_pattern_plating_object` de
`ToolCopperThieving.py`; `calculate_factors` e `generate_verification_gcode` de
`ToolCalibration.py`; e `Gerber.scale`/`Gerber.skew`. Só a interface é substituída por
stand-ins (campos, sinais, criação de objeto); nenhum algoritmo é alterado. Relatórios em
`target/tools-comparison-synthetic-20261010` e `target/tools-comparison-real-20261010`.

| Conjunto | MATCH | DIFFERENT | Diferença documentada | ORACLE_ERROR |
| --- | --- | --- | --- | --- |
| Sintético (29 casos; Gerber/Excellon lidos pelos **parsers do Python**) | 27 | 0 | 2 | 0 |
| Com o projeto real (50 casos; objetos decodificados do `.FlatPrj` pelo Python) | 47 | 0 | 3 | 0 |

Modo estrito aprovado nos dois (saída 0). Critérios: Rules Check compara **as mesmas violações**
(o par de peças de cada ponto, pois bordas paralelas não têm um único ponto mais próximo) e os
mesmos tamanhos; geometria compara a diferença simétrica das uniões (≤ 0,1% da área) e, em pontos
e quadrados, a contagem; fatores com 1e-9; G-code de verificação linha a linha, sem comentários.

| Ferramenta | Casos | Resultado |
| --- | --- | --- |
| Rules Check | 10 regras no sintético; 10 casos no projeto real (trilha, cobre-cobre topo/base em 0,25/0,4/0,45, cobre-contorno, anel anular 0,3/0,6, furo-furo, tamanho do furo) | 19 iguais (até 57 violações idênticas no anel anular), 2 documentadas |
| Copper Thieving | sólido (caixa retangular e mínima), pontos, quadrados, linhas, áreas desenhadas (sólido e pontos), referência Gerber; robber bar; máscara de galvanoplastia com e sem distância — nos dois conjuntos | 22 iguais; diferença de área entre 0 e 0,07% |
| Calibration | fatores (3), G-code de verificação (3, em mm e polegadas, com e sem zerar Z), objeto calibrado | 6 iguais, 1 documentada |

Diferenças documentadas (o caso existe para registrá-las, não conta como igual):

- **Cobre-cobre com uma peça só:** o Python responde "Failed. Only one polygon."; o FX passa com uma nota.
- **Vão exatamente no limite:** o Python aumenta o cobre em 1e-6 antes de medir, então um vão de
  0,400000 é acusado com limite 0,4 (5 pares a mais no B_Cu real); o FX só acusa vão menor que o limite.
- **Inclinação Y da Calibration com origem fora de Y = 0:** o Python soma a Y da origem ao delta
  (11,86° no caso de teste); o FX usa só o delta (0,573°).

O que a comparação mostrou além do placar:

- **O Copper Thieving do Python não processa um Gerber de projeto reaberto.** O `.FlatPrj` guarda a
  geometria como lista com um MultiPolygon; as rotinas originais falham com
  "Sequences of multi-polygons are not valid arguments" ou encerram com "No object available."
  O harness registra esse erro (`legacyContainerError`) e repete com a mesma geometria em lista plana
  (como fica após abrir o arquivo Gerber), marcando a entrada como `python-project-flattened`.
  O FX processa os dois casos.
- No cobre-contorno com topo e base, o Python funde as duas camadas antes de medir e dá um ponto por
  violação; o FX dá um ponto por peça de cada camada (4 pontos para a mesma violação no projeto real).
- Tempo das rotinas originais no projeto real (Shapely 1.8.5): thieving sólido ~1,0 s, pontos ~1,2 s,
  linhas ~2,2 s; cobre-cobre ~0,2–0,3 s por camada. Os números da seção "Tempo" abaixo são de um script
  mais simples e ficam como ordem de grandeza.

Não coberto: Rules Check com polígonos "clear" (LPC) e com dois Excellon; referência Geometry no
thieving; "Apply Scale/Skew" sobre os pontos da Calibration; corpus em polegadas (só o G-code).

## Ferramentas do menu (24)

| Ferramenta | Nível | Evidência e limites |
| --- | --- | --- |
| Isolation | **A** | 6 casos × 3 conjuntos. Não cobre: Rest de isolação, combinação com várias ferramentas, V-shape. |
| NCC | **A** | 16 casos × 3 conjuntos, mais a matriz panelizada (114 públicos e 19 reais, ver COMPARACAO_CAM.md). Seed padrão difere de propósito. |
| Paint | **A** (parcial) | Só Standard. Seed, Lines, Combo, seleção por área/polígono/referência: nível C. |
| Cutout | **A** (parcial) | Retangular e free-form com margem e quatro pontes. Thin, M-Bites, gaps manuais, recortes internos: nível C. |
| Rules Check | **A** | As 10 regras contra as rotinas originais, sintético e projeto real: mesmas violações. Duas diferenças documentadas (peça única, vão no limite). |
| Copper Thieving | **A** | 4 preenchimentos, 3 referências, robber bar e máscara, sintético e projeto real. Falta referência Geometry. |
| Calibration | **A** | Fatores, G-code de verificação e objeto calibrado. Uma diferença documentada (inclinação Y). |
| Drilling | **C** | Testes do FX (profundidades, troca, exclusões, banco de ferramentas). Sem oráculo Python do G-code de furação. |
| Geometry CNC | **A** (XY) / **C** | Cortes XY interpretados pelo parser do Python no harness; alturas, avanços, multi-depth, V-Tip: nível C. |
| Optimal | **C** | Projeto real: 44 peças, vão mínimo 0,3505 mm; não executado contra o Python. |
| 2-Sided | **C** | Espelhos e furos de alinhamento testados no FX. |
| Align Objects | **C** | |
| Extract Drills | **C** | |
| Punch Gerber | **C** | |
| Etch Compensation | **C** | |
| Film | **C** | SVG/PNG/PDF gerados; sem comparação de arquivo com o Python. |
| Fiducials | **C** | |
| Corner Markers | **C** | |
| QRCode | **C** | Matriz gerada por biblioteca diferente (ZXing × qrcode): mesmo padrão não garantido byte a byte. |
| Invert Gerber | **C** | |
| Subtract | **C** | |
| Panelize | **C** (+ **A** no fluxo) | Posições testadas no FX; o fluxo NCC/Isolation/Cutout sobre painéis tem nível A (ver PANELIZED_NCC.md). |
| SolderPaste | **C** | G-code do perfil Paste_1 sem oráculo. |
| Transform | **C** | Rotação, escala, inclinação, espelho e deslocamento; a inclinação bate com `shapely.affinity.skew` (ver Calibration). |
| Calculators | **C** | Fórmulas conferidas por teste. |

Resumo: **7** ferramentas com nível A (duas delas parciais) e **17** só com nível C.
Nenhuma das 24 está sem implementação. "24/24 no menu" não é "24/24 equivalentes".

## Outras áreas

| Área | Estado | Evidência |
| --- | --- | --- |
| Importação Gerber/Excellon | implementada | Testes do FX e corpus; projeto real abre e reserializa. Opções de importação nas preferências (este incremento). |
| Projetos `.FlatPrj` | leitura e escrita, preservando o que o FX não modela | Projeto real aberto e salvo pelo FX: 0 diferenças em 1541 valores de objeto e 550 preferências; serializadores reais do Python aceitam o resultado (COMPATIBILIDADE_FLATPRJ.md). |
| Pós-processadores | 20 no FX: os 19 perfis Python selecionáveis e o FX portable; `Paste_1` atende o SolderPaste | Portados à mão; **nenhum** validado em máquina (PREPROCESSADORES.md). |
| Terminal Tcl | 28 comandos registrados no FX, de 66 arquivos de comando do Python | Cobre abrir/salvar/transformar/plotar/exportar e parte do CAM (isolate, ncc, cutout, cncjob); faltam paint, drillcncjob, milling e os demais. |
| Preferências | 116 padrões de ferramentas/CNC/importação no FX, de 562 chaves do `defaults.py` | Cobre os valores iniciais dos painéis; faltam cores, opções de plot por objeto, exportação, editores. |
| Editores (Geometry, Gerber, Excellon, G-code) | implementados | Nível C/D: sem comparação sistemática de cada ferramenta de edição. |
| Interface | 24 painéis, temas, tooltips | Nível D: validação manual dos painéis novos pendente. |

## Tempo (medições pontuais, não benchmark)

Mesmas entradas do projeto real, Intel Core Ultra 7 155H. Python = algoritmo reescrito (nível B)
com Shapely 1.8.5/GEOS 3.10.3; FX depois de aquecido (a primeira execução com JVM fria é 2–4× maior).

| Operação | Python | FX |
| --- | --- | --- |
| Rules Check, 5 regras | ~1020 ms | ~145 ms |
| Copper Thieving sólido | 44 ms | 21 ms |
| Copper Thieving pontos | 394 ms | 42 ms |
| Copper Thieving quadrados | 381 ms | 25 ms |
| Copper Thieving linhas | 266 ms | 75–120 ms |
| Calibration (escala + inclinação; Python medido com Shapely 2.1) | ~2 ms | ~2 ms |

Com Shapely 2.1/GEOS 3.13 o Python fica cerca de 2× mais rápido que esses números (Rules Check
~520 ms, pontos ~79 ms); o FX continua à frente, exceto no empate da Calibration. Uma máquina,
uma execução por medida: servem de ordem de grandeza.

## O que este documento não afirma

- Não há uma "porcentagem de paridade do aplicativo". Os 96,4% são dos **casos CAM comparados**.
- Nível A cobre caminhos e cortes XY, não segurança CNC, alturas ou avanços.
- Os tempos da tabela "Tempo" vêm de scripts simplificados que não estão no repositório.
- Nenhum G-code foi validado numa máquina.

## Próximas ações sugeridas, por retorno

1. Feito: Rules Check, Copper Thieving e Calibration estão no harness (seção acima).
2. Subir de C para A as ferramentas de geometria pura mais usadas: Extract Drills, Punch, Fiducials,
   Corner Markers, Invert, Subtract, 2-Sided (as rotinas do Python são curtas e sem interface).
3. Oráculo de G-code para Drilling e Geometry CNC (alturas, avanços, troca), por pós-processador.
4. Comandos CAM do Terminal Tcl, que permitem rodar o mesmo script nos dois aplicativos.
