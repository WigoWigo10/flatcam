# Matriz de validação dos fluxos principais — 2026-10-24

Resultado posterior e correção do comparador:
[NCC_POINT_TOUCH_REVIEW.md](NCC_POINT_TOUCH_REVIEW.md). **201 concordâncias,
zero diferenças de CAM/G-code/parciais, 2 erros do handler Offset Python**
em 203 controles. Todos os casos NCC agora chamam a GUI original, inclusive
os schemas antigos de ferramenta única. A tabela abaixo mantém o baseline
histórico anterior; a matriz intermediária interrompida não conta como
aprovada. Relatório final em `target/ncc-seams-final-validation-20261024`.

Terceira etapa sobre `c52029c1`, após a revisão GRBL e a revisão de precisão
nativa. `compare-main-flow-matrix.ps1` executa fontes `.FlatPrj` em série,
usando o fluxo existente de `compare-panelized-flows.ps1`. Aceita várias
fontes, remove caminhos repetidos e informa também a quantidade de hashes
distintos; variações do mesmo arquivo não são contadas como novos projetos.

| Cenário real por fonte | Grade | Espaçamento X/Y | Receitas NCC |
| --- | --- | --- | --- |
| 2x2-gap5 | 2 × 2 | 5 / 5 mm | Reference Geometry/Itself × Rest × Connect |
| 3x1-gap0 | 3 × 1 | 0 / 0 mm | Reference Geometry/Itself × Rest × Connect |
| 1x2-gap2-7-advanced | 1 × 2 | 2 / 7 mm | Lines/Rest, Combo, margem, offset e ISO/Rest |

Também executa, uma vez por suíte, seis cenários públicos padrão e dois
avançados em MM/IN. `-SkipPublic` permite limitar a execução às fontes reais.
Um resultado sem erros nos casos amostrados não certifica uma solução de
usinagem para espaçamento zero nem o comportamento físico de uma CNC.

## O que a matriz verifica

- Lê os Gerbers F_Cu/B_Cu, Edge_Cuts e Excellon do projeto real, aplicando a
  mesma referência e deslocamentos de panelização às categorias.
- Exercita os handlers CAM reais do FX para Isolation, NCC e Cutout; mantém
  associações e ordem das ferramentas, registros de furos/rasgos e cópias.
- Gera/reabre CNC e salva/reabre o projeto nativo produzido. Não é uma
  reexecução ou otimização dos CNC Jobs prontos do projeto original.
- Compara CAM por ferramenta com os handlers originais Python e verifica
  se o parser Python interpreta o G-code FX de forma consistente com os
  caminhos FX. Isso não compara duas gerações de G-code comando a comando;
  essa verificação é feita separadamente no comparador de parâmetros GRBL.
  F_Cu/B_Cu são decodificados do `.FlatPrj` independentemente; Cutout ainda
  usa a área compartilhada, sem provar a reconstrução Python de Edge_Cuts.
- Confere SHA-256 da fonte antes/depois de cada execução e no encerramento.
  G-code, projetos derivados, logs e relatórios ficam em nova pasta `target`.

Há somente **uma fonte real disponível** no levantamento de arquivos
`.FlatPrj`/`.fcnproj` sob `Documents/VSCode`, excluindo `target`, `.git`,
ambientes e dependências. A matriz desta etapa usa essa fonte já autorizada
em três cenários; os casos públicos continuam identificados como sintéticos.
Projetos com outra estrutura podem revelar lacunas não cobertas aqui.

## Resultado final desta rodada

| Conjunto | Concordâncias amostradas | Trajeto diferente | Erro do oráculo Python |
| --- | ---: | ---: | ---: |
| Fonte real 2×2, gaps 5/5 | 19 | 0 | 0 |
| Fonte real 3×1, gap zero | 18 | 1 | 0 |
| Fonte real 1×2, gaps 2/7, avançado | 15 | 0 | 2 |
| Públicos padrão/avançado MM/IN | 148 | 0 | 0 |
| Total | **200** | **1** | **2** |

**203 controles; strict exit 1**, sem `GCODE_DIFFERENT`, sem
`PARTIAL_DIFFERENCE` e sem execução inconclusa. As três execuções reais FX
terminaram em `PASS_WITH_DOCUMENTED_LIMITATIONS`, com registro de furos e
round-trip nativo conferidos. Todos os hashes da fonte original permaneceram
iguais. Isso não transforma as três reprovações em paridade nem prova remoção
integral de material ou equivalência de comandos de máquina.

O resumo final foi renderizado novamente a partir das contagens preservadas,
usando os novos campos de progresso; nenhum resultado individual foi alterado.

## Relatórios e falhas

`matrix-summary.json` e `index.html` são atualizados após cada execução,
preservando relatórios individuais. Agregam as contagens originais, fontes,
hashes e execuções sem conclusão. `PlannedRuns`, `RecordedRuns` e
`MatrixComplete` distinguem relatório parcial de matriz encerrada; encerrada
não significa aprovada se há diferenças ou falhas. Código 2/falta de relatório não vira
aprovação; diferenças/erros do legado não viram paridade. Com `-Strict`,
divergência encerra a matriz com código 1; falha de execução, com código 2.
Os demais cenários continuam disponíveis para investigação.

O agregador foi verificado com um resultado concluído e outro interrompido:
preservou quatro concordâncias, duas diferenças, uma execução sem conclusão,
hash da fonte e escape HTML. Evidência de controle em
`target/three-steps-matrix-summary-test-20261024` (fonte sintética, não incluída
na contagem de projetos reais). O runner não instala dependências, abre
janela visível ou usa configurações reais do usuário.

## Achados que continuam reprovando strict

1. **Rest + Itself + Connect, B_Cu, painel real 3×1 sem espaço:** diferença
   reproduzida também como caso isolado, sem alterar geometria/tolerância.
   A região a limpar coincide exatamente, mas a trajetória da fresa de
   1 mm difere: distância amostrada máxima 0,218295 mm e comprimento
   3728,964719 mm FX versus 3723,645373 mm Python. A cobertura por largura da
   fresa tem IoU 0,999999908; isso não aprova a divergência do trajeto.
   A fresa de 0,2 mm atende aos critérios. Um recorte diagnóstico do ponto
   testemunha contém um trecho FX adicional; a causa exata da escolha do
   conector ainda precisa ser minimizada/investigada. Não foi classificada
   como diferença intencional nem corrigida copiando uma saída específica.
2. **NCC Offset nas duas faces do projeto real:** o handler original Python falha ao construir
   `MultiPolygon(sol_geo)` a partir da representação aninhada, em
   `appTools/ToolNCC.py:1934`: `Sequences of multi-polygons are not valid arguments`.
   A falha foi reproduzida isoladamente, capturando o traceback sem mudar
   entrada/algoritmo. É erro do handler no ensaio, não uma validação de uma
   sessão GUI completa. O FX executou a receita; não há saída Python válida
   para declarar paridade desse caso.

Diagnósticos privados em `target/three-steps-connect-repro-20261024`:
`fx-case.json` é a exportação original reduzida a uma receita, **não** uma
geometria mínima; `python/report.json` mantém a reprovação. O recorte local
está em `project-1-3x1-gap0/b-cu/connect-route-diagnostic.json`, separado dos
caminhos executáveis. `offset-trace.log` identifica a linha original da
falha de Offset. Os artefatos contêm dados da placa e não entram no Git.

Para repetir apenas o caso Connect, sem os outros 202 controles:

```powershell
target\oracle-py311\Scripts\python.exe tools\compare_cam_python.py --legacy-root .. --project 'C:\caminho\projeto.FlatPrj' --fx-export target\three-steps-connect-repro-20261024\fx-case.json --output target\connect-repro-novo --cases ncc-rest-itself-connect --strict
```

```powershell
.\compare-main-flow-matrix.ps1 -Projects @('C:\caminho\projeto.FlatPrj') -Strict
# Várias fontes:
.\compare-main-flow-matrix.ps1 -Projects @('C:\caminho\a.FlatPrj','C:\caminho\b.FlatPrj') -Strict
```

Saída desta rodada: `target/three-steps-real-matrix-20261024/index.html`.
Critérios: comparação numérica amostrada e invariantes funcionais do FX;
sem certificação visual/manual, segurança física ou percentual global de paridade.

Verify integral final: **1867 aprovações Java, 18 skips opcionais**, zero
falhas/erros, 1885 registros desta execução. Contagem exclui XML antigos.
Log `target/three-steps-full-final-20261024.log`. As fontes Python não foram
alteradas e os artefatos privados permaneceram em `target`.
