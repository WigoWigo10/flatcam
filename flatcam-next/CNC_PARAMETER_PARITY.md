# Paridade CNC: parâmetros executados, não só cortes XY

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
