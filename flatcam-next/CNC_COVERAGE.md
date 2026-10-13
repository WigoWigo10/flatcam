# Cobertura nominal NCC no Plot — 2026-10-13

## Como usar

1. Gere uma Geometry pelo NCC atual, a partir de Gerber ou Geometry.
2. Abra Geometry → CNC e revise os parâmetros. Clique Gerar CNC Job.
3. No resumo, clique **Ver cobertura no Plot**, antes de escolher/gravar arquivo.
4. Selecione uma fresa e alterne entre **Após esta fresa (acumulado)** e
   **Somente esta fresa**. **Mostrar resíduo final** destaca o restante de todo
   o alvo. Laranja claro no Dark e laranja escuro no Light.
5. **Voltar aos parâmetros CNC** conserva o rascunho revisado. Cálculo,
   seleção e destaque não modificam os trajetos nem gravam G-code.

Projetos antigos/importados sem a área-alvo não ganham cobertura por inferência
de bounding box. Para ter o mapa, regenere o NCC. Não são avaliadas nesta etapa
áreas-alvo de Isolation/Paint/Cutout ou de uma Geometry genérica.

## O que significa cada coluna

- **Individual:** alvo próprio do NCC para aquela fresa menos a faixa varrida
  por ela. Em Rest, o alvo já pode ser o resíduo disponível quando a fresa foi
  processada. Fora de Rest, offsets de cobre diferentes podem definir alvos
  diferentes; não se presume um retângulo comum.
- **Acumulado:** alvo global menos todas as fresas até a linha selecionada.
- **Final:** alvo global menos todas as faixas nominais. ISO pode contribuir
  ao acumulado, mas não tem alvo individual de limpeza; isso aparece como
  **Sem alvo individual**, não como zero material residual.

São áreas em mm²/in², não distâncias. Resíduos positivos muito pequenos são
mostrados em notação científica; não são apagados/arredondados a zero para
declarar cobertura completa.

## Cálculo e segurança

`NccGenerator` conserva a área realmente preparada para cada CLEAR e o alvo
global representado. `MachiningTarget` vincula esses alvos aos trajetos e
diâmetros ordenados por assinatura de WKT canônico. Antes de calcular,
`MachiningCoverage` confere assinatura, unidades e índices.

O worker aplica a compensação CNC configurada, extrai trajetos lineares/pontos,
bufferiza com raio nominal da ferramenta (16 segmentos por quadrante) e faz
diferenças robustas JTS. Polygon fornece seus contornos externo/internos:
**não** é tratado como se todo o interior tivesse sido fresado. Dimensões e
buracos do alvo são preservados. Áreas numéricas são produzidas no worker;
o painel só formata números e escolhe uma geometria já calculada para o Plot.

Não é remoção física de material nem simulação 3D. Não valida Z, espessura,
perfil físico da fresa (incluindo esfera/V), grampos, máquina, sondagem, macros
M6, colisões ou exclusões CNC. É cobertura nominal 2D, antes da geração.
As validações do gerador continuam independentes e podem recusar o G-code.
Trabalhos laser/plotter/sonda não abrem esta análise. Resultados não são uma
certificação de usinagem nem prova de que a ferramenta foi montada corretamente.

Não se altera ordem, Connect, Rest, paths CAM ou política CNC para calcular o
mapa. O worker usa cancelamento entre operações; uma operação JTS individual
pode terminar antes de observar o pedido. Trocar painel cancela e descarta o
resultado tardio; mudar/remover a origem também invalida a publicação.

## Persistência e invalidação

Projetos nativos guardam alvo global, alvos por índice e assinatura em campo
opcional `machiningTarget` da Geometry. Áreas compartilhadas são serializadas
uma vez, com referências, para não multiplicar WKT grande por cada fresa.
Contêiner `.FlatPrj` escrito/reaberto diretamente pelo FX também conserva a
extensão privada. O Python pode descartá-la ao regravar; não se afirmou que o
aplicativo Python mantém esses novos metadados.

Registros/arquivos anteriores continuam válidos, sem alvo. Campos inválidos de
metadados nativos são recusados, não usados como cobertura. A assinatura usa a
mesma precisão WKT da persistência e não depende de perfil ou profundidade:
esses não mudam a faixa nominal XY. Compensação é recalculada com o rascunho.

Editar/transformar/bufferizar/juntar a Geometry invalida o alvo nesta etapa.
Deslocar/panelizar uma Geometry NCC não transporta seu alvo automaticamente;
regenere NCC depois da transformação. Alterar apenas parâmetros CNC preserva
o alvo. Desfazer pode restaurar o registro anterior com sua referência válida.

## Evidências e pendências

Build completo `verify` aprovado: 1684 testes registrados, 1666 aprovados,
18 opcionais ignorados, zero falhas/erros. Log:
`target/coverage-final-20261013.log`. Auditoria de tooltips: 1096 ocorrências,
zero MISSING no cenário construído.

No Python deste repositório, `appTools/ToolNCC.py` calcula resíduos
internamente no Rest e oferece **Check validity** para diâmetros de isolamento.
Não foi encontrado um painel equivalente de cobertura individual/acumulada/final
com áreas numéricas e seleção de resíduos antes de exportar CNC. Este painel
é uma melhoria de usabilidade do FX, não uma funcionalidade copiada do legado.

Testes: `MachiningCoverageTest`, `MachiningTargetProjectTest`,
`CncCoveragePanelTest`, `MainNccCoverageTest` e `MainCamFlowTest`.
Cobrem resíduos individuais/acumulados, interior de Polygon não preenchido,
furos, compensação, ISO, cancelamento, assinatura, MM/IN, persistência e
referências inválidas; fluxo real NCC → mapa → parâmetros e invalidação por
transformação; overlay laranja nos quatro temas sem camada selecionável nova.
Snapshots: `flatcam-fx/target/cnc-coverage-*.png`.

Evidência **C**: testes derivados dos contratos e testes da implementação FX,
não comparação diferencial deste mapa com o Python. A rodada GRBL anterior
continua em 42 controles / 22 equivalências completas, com strict 1. Este mapa
não fecha as 12 diferenças de rota, 6 de usinagem e dois erros do legado.

Próximos: validação manual no projeto real/denso; transportar alvos em
transformações/panelização; adicionar outros CAM; medir custo/memória com muitas
fresas; ampliar corpus CNC e definir diferenças conservadoras de trajetos.
