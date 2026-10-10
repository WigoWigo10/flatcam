# Isolamento do custo vetorial CNC — 2026-10-10

Worktree dirty sobre `a8116d0f`, branch `flatcam-next`; sem commit/push.
Continuação dos ensaios de [PLOT_RENDER_TEST_RESULTS.md](PLOT_RENDER_TEST_RESULTS.md).

## Hipótese e controle

O CNC filled usa centerlines com largura da fresa. Quando essa largura alcança
7 pixels lógicos, desenha também centerlines finas (1,25 px) por cima para
revelar as passadas. Corpos largos não usam o raster denso (limite 2,5 px).
O JFR anterior concentrou amostras CPU em Marlin/Quantum durante zoom.
Precisamos distinguir o custo dos corpos, detalhes e decorações, em vez de
atribuir todo o custo à publicação de imagens ou trocar o backend às cegas.

`-CncProbe` é um **controle diagnóstico com omissão deliberada de pixels**:

| Modo | O que fica ausente |
| --- | --- |
| FULL (padrão) | Nada; referência |
| OMIT_BODY | Corpo das camadas CNC (incluindo travel); linhas de detalhe podem permanecer |
| OMIT_WIDE_BODY | Apenas corpos com traço > 2,5 px lógicos; mantém os estreitos elegíveis para raster denso |
| OMIT_PASS_LINES | Apenas as linhas finas por cima dos corpos largos |
| OMIT_DECORATIONS | Grupos de setas/anotações |
| OMIT_CNC | Corpos, linhas finas e decorações; grade/réguas/seleção permanecem |

Não é preferência do aplicativo ou otimização: os modos OMIT não contam como
paridade visual, mesmo com status COMPLETED. O JSON schema 3 identifica
`cncProbe` e `visualValidation=NOT_VALIDATED_DIAGNOSTIC_OMISSIONS`. O script
avisa no console e exige Project/ObjectName explícitos; o teste exige CNC visível.
O setter interno exige opt-in benchmark + thread FX. Startup normal não instala
probe nem muda o projeto; dispose remove o observer. Nenhum formato/CAM/picking,
LOD, largura, precisão ou cor padrão foi alterado.

`cncPasses` por fase conta submissões dos grupos BODY/PASS_LINES/DECORATIONS,
com seleção do caminho vetorial/raster e min/max da largura lógica. Não conta
quantos caminhos/vértices/pixels foram efetivamente apresentados. Raster calls
incluem PREPARING/PREVIEW, e decorações são grupos potencialmente presentes,
não contagem de cada seta/texto visível. A coleta só ocorre com probe instalado.

Testes dedicados cobrem política de cada omissão, contadores, recusa fora do
benchmark, FULL contra os pixels normais de um CNC com traço largo/pass lines,
identidade/visibilidade da geometria intactas e liberação no dispose. Permanecem
os regressores anteriores de pixels, undo, densidade e publicação em background.

Consulta primária: o [NGCanvas do OpenJFX 25](https://github.com/openjdk/jfx/blob/jfx25/modules/javafx.graphics/src/main/java/com/sun/javafx/sg/prism/NGCanvas.java)
envia STROKE_PATH à operação de desenho Prism. O
[DMarlinRasterizer](https://github.com/openjdk/jfx/blob/jfx25/modules/javafx.graphics/src/main/java/com/sun/prism/impl/shape/DMarlinRasterizer.java)
produz máscaras alpha CPU, com buffers dimensionados pelos limites do shape.
Isso sustenta a investigação; não prova qual chamada do nosso Plot domina.
Clips retangulares inteiros têm caminho específico em NGCanvas: não remover
clipping/réguas com base na suposição de que todo clip é uma máscara cara.

## Executar

```powershell
.\benchmark-plot.ps1 -Project 'C:\caminho\Projeto.FlatPrj' -ObjectName 'Cobre_Morto_Bottom_cnc' -Steps 120 -Repeats 3 -Jfr
.\benchmark-plot.ps1 -Project 'C:\caminho\Projeto.FlatPrj' -ObjectName 'Cobre_Morto_Bottom_cnc' -Steps 120 -Repeats 3 -CncProbe OMIT_BODY -Jfr
.\benchmark-plot.ps1 -Project 'C:\caminho\Projeto.FlatPrj' -ObjectName 'Cobre_Morto_Bottom_cnc' -Steps 120 -Repeats 3 -CncProbe OMIT_WIDE_BODY -Jfr
.\benchmark-plot.ps1 -Project 'C:\caminho\Projeto.FlatPrj' -ObjectName 'Cobre_Morto_Bottom_cnc' -Steps 120 -Repeats 3 -CncProbe OMIT_PASS_LINES -Jfr
.\benchmark-plot.ps1 -Project 'C:\caminho\Projeto.FlatPrj' -ObjectName 'Cobre_Morto_Bottom_cnc' -Steps 120 -Repeats 3 -CncProbe OMIT_DECORATIONS -Jfr
```

Não interagir/minimizar, nem executar builds/oráculos/análise de JFR em paralelo.
Mesmo arquivo, visibilidade, Scene/escala, energia, driver e tema. Separar frio
e quente; pulsos/comandos/filas continuam não sendo FPS apresentado pela GPU.
Relatórios e JFR privados ficam em target, fonte validada por SHA-256.

## Resultados e decisão

Sete execuções sequenciais no mesmo projeto privado, apenas
`Cobre_Morto_Bottom_cnc`, 120 passos × três repetições, JFR, D3D/PixelBuffer,
Intel Arc (driver 32.0.101.8991), ICE_DARK, Scene 1280 × 800 e escala 2 × 2.
Todas COMPLETED; SHA-256 original intacto. Edição não conta: não há Geometry
visível nesses ensaios. FULL foi repetido depois dos controles de omissão.

Valores abaixo são das **repetições aquecidas 2 e 3**, em milissegundos.
Intervalo máximo de pulso não é duração do frame apresentado pela GPU.

| Controle | Fila até primeiros comandos p95 (2 / 3) | Intervalo máximo de pulso (2 / 3) | Diretório privado em target |
| --- | --- | --- | --- |
| FULL inicial | 19,67 / 19,02 | 48,44 / 48,09 | plot-benchmark-20261010-171351-802 |
| OMIT_BODY | 0,24 / 0,27 | 17,13 / 17,65 | plot-benchmark-20261010-171453-478 |
| OMIT_PASS_LINES | 22,49 / 24,30 | 48,41 / 48,94 | plot-benchmark-20261010-171533-144 |
| OMIT_DECORATIONS | 20,19 / 19,37 | 48,64 / 48,23 | plot-benchmark-20261010-171656-283 |
| OMIT_CNC (piso diagnóstico sem desenho CNC) | 0,22 / 0,16 | 16,84 / 16,78 | plot-benchmark-20261010-171803-942 |
| FULL recontrole | 20,31 / 20,54 | 48,70 / 48,37 | plot-benchmark-20261010-171857-172 |
| OMIT_WIDE_BODY | 14,84 / 14,09 | 16,72 / 17,31 | plot-benchmark-20261010-172044-786 |

FULL submeteu por repetição de zoom 242 grupos BODY (31 raster / 211 vetor),
100 PASS_LINES e 121 grupos de decoração. As larguras dos corpos foram de
1,88 a 15,01 px lógicos. OMIT_WIDE_BODY conserva 62 grupos BODY (31 raster /
31 vetor), largura até 2,46 px, e as mesmas 100 linhas de detalhe. Portanto,
o controle final mantém o caminho raster estreito, em vez de eliminar todo
o trabalho CNC. Esses contadores não são quantidade de caminhos/pixels.

A convergência dos controles aponta os **corpos largos vetoriais** como alvo
prioritário das pausas sustentadas neste zoom/corpus. Tirar detalhes ou
decorações não resolveu; tirar apenas os corpos > 2,5 px resolveu nos dois
períodos aquecidos. O JFR anterior e destes controles também concentra
amostras em Marlin/QuantumRenderer. Isso não prova o resultado em todo projeto,
nem mede utilização/FPS GPU. Uma sessão por omissão e duas FULL não são uma
bateria estatística; não usar o quociente dos números como speedup entregue.

Os picos frios permanecem: pulso máximo na primeira repetição de FULL foi
400,66 ms (recontrole 319,46 ms); OMIT_WIDE_BODY ainda teve 114,27 ms. Não há
promessa de eliminar toda pausa/JIT/inicialização. Os JSON/JFR e logs integrais
estão nos diretórios acima, ignorados pelo Git, não distribuídos com o código.

**Decisão:** não promover omissões ao app. O próximo spike opcional de
apresentação GPU deve atacar os corpos largos, conservando detalhes, raster
estreito, overlays e fallback Canvas. Precisa validar cor/alpha, sobreposições,
caps/joins arredondados, HiDPI, resize e lifecycle, com equivalência de imagem
e ganho medido contra FULL. Nenhuma biblioteca/backend novo foi escolhido ou
implementado; CAM/JTS, geometria original e renderer padrão continuam intactos.

## Verificação

`mvnw.cmd verify`: BUILD SUCCESS, exit 0; **1550 testes registrados,
1534 aprovados, 16 opcionais ignorados**, zero falhas/erros. Quatro testes novos
`PlotCncProbeTest` incluídos. Log integral:
`target/plot-cnc-ablation-full-recheck-20261010.log`. Parser PowerShell e
`git diff --check` aprovados. Testes focados de probe, Canvas, preparação e JFR
também passaram (`target/plot-cnc-probe-final-focused-20261010.log`).

A ocorrência Tcl/save intermitente da etapa anterior permanece documentada em
PLOT_RENDER_TEST_RESULTS.md; não reapareceu nesta verificação, mas sua causa
não foi corrigida por esta investigação do renderer. Sem commit/push nesta etapa.
