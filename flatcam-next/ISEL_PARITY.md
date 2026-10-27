# ISEL CNC — tempos, troca e término

## Escopo e referência — 2026-10-23

Marlin foi commitado em `4fcc2a3a`. Esta etapa trata **ISEL_CNC**, não do formato
ISEL ICP. Referência do contrato: [ISEL Remote DIN/ISO, fevereiro de 2025,
Remote a partir de V1.47.5.2](https://www.isel.com/fileadmin/user_upload/Software-Download/20250227_G-and-M-codes_en.pdf).
Não foi executado controlador real nem o intérprete comercial Remote.

A documentação confirma G71 métrico, G94 em mm/min, G4 em milissegundos,
ferramentas T1–T128, M06 para troca e M30 como término. M01 no Remote é uma
parada com interação do operador; **não** deve ser descrito universalmente
como uma parada opcional dependente de chave. O perfil FX continua limitado
a MM; o suporte IN do intérprete não significa que este port esteja validado
em IN. A palavra P do dwell é mantida conforme o preprocessor legado; não se
alega homologação de todas as variações de sintaxe do controlador.

## Defeito reproduzido: espera mil vezes menor

- **Reprodução mínima:** `IselDwellRegressionTest.quarterSecondDwellMustBe250MillisecondsInIselGeometryAndDrilling`.
  Gera um segmento Geometry e um furo Excellon públicos, com Dwell=0,25 s.
- **Comando:** a partir de `flatcam-next`, `mvnw.cmd -pl flatcam-cam -am test -Dtest=IselDwellRegressionTest -Dsurefire.failIfNoSpecifiedTests=false`.
- **Ambiente/base:** Windows 11, Java 25.0.4.1, base `4fcc2a3a`; dados sintéticos,
  sem UI, máquina, aleatoriedade, relógio ou chamadas de rede no teste.
- **Esperado/obtido:** esperado `G4 P250.0000`; antes saía `G4 P0.2500`.
  A diferença é da unidade do tempo emitido, não uma medição física da máquina.
- **Histórico:** não há baseline conhecido em que esse contrato estivesse
  correto; não foi feito bisect nem atribuído um commit introdutor sem prova.
  O perfil foi originalmente adicionado em `3d891d08`, mas isso não constitui
  resultado de bisect.
- **Regressão:** o teste foi escrito/falhou antes da alteração e foi staged
  antes da correção. Mesmas asserções, sem desabilitar testes para passar.
- **Evidência:** `target/isel-dwell-red-20261023.log`, dez rodadas
  `target/isel-dwell-red-repeat-*.log`; após corrigir, green em
  `target/isel-dwell-green-20261023.log`. Remover temporariamente só a conversão
  voltou a falhar em `target/isel-dwell-revert-red-20261023.log`; restaurada,
  passou dez vezes (`target/isel-dwell-green-repeat-*.log`). A retirada foi
  local via patch e restaurada, sem reset/checkout de alterações do usuário.

`GCodePreprocessor.dwellCode` converte segundos para milissegundos somente no
ISEL_CNC, valida overflow e mantém segundos nos outros perfis. Geometry e
Drilling usam esse emissor. Dwell zero continua zero válido no FX.

## Outras correções e proteções

- M30 é emitido **depois** de End XY e End Z. Não há retorno implícito à origem.
- T fora de 1–128 é recusado; não é truncado, renumerado nem ignorado.
- Alturas e XY de troca já estavam corretos nos controles FX e foram mantidos:
  Start Z não substitui a altura de troca; M05 antecede M06/M01; G90/Z são
  restaurados após a troca e End Z baixo só é atingido depois de estacionar XY.
- A revisão/descrição explica a unidade do Dwell, a parada Remote, faixa de
  ferramentas e limites de simulação de M06. Não simula referenciamento,
  movimentos internos nem offsets da macro, grampos ou máquina física.

IselContractTest cobre duas ferramentas, IDs esparsos incluindo T128,
peck/rasgos, posições distintas, limites/overflow e troca desabilitada.
GCodePreprocessorTest preserva as expectativas dos outros perfis.
GeometryPerToolTest agora exige exatamente dois dwells com a unidade correta
de cada perfil, em vez de supor segundos para todos. Essa suposição antiga
foi a única falha de asserção na primeira rodada completa, preservada em
`target/isel-contract-full-20261023.log`; não foi suprimida.

## Comparador: condição de parada explícita

O modo padrão é `-IselM01Mode remote`. As alternativas `optional-enabled` e
`optional-disabled` são modelos explícitos para outros comportamentos de
controlador, não prova de que o Remote documentado possua uma chave opcional.
O leitor retém comando/atividade da parada e registra `fxIselControls` e
`pythonIselControls`, além dos diagnósticos de altura/XY por ferramenta.
M00/M02 com semântica de abortar ficam recusados, não convertidos em pausa
ou em M30 silenciosamente. M30 é evento de término e desliga o estado comandado
do spindle no modelo. Movimentos internos de M06 continuam opacos.

```powershell
.\compare-cnc-parameters.ps1 -Profile ISEL_CNC -IselM01Mode remote -Strict
.\compare-cnc-parameters.ps1 -Profile ISEL_CNC -IselM01Mode optional-enabled -Strict
.\compare-cnc-parameters.ps1 -Profile ISEL_CNC -IselM01Mode optional-disabled -Strict
```

52 controles MM, IN excluído com motivo no relatório. Cada modo:
**0 MATCH_EXECUTION, 22 ROUTING_DIFFERENT, 29 MACHINING_DIFFERENT,
1 ORACLE_ERROR, 0 FX_ERROR**. Strict permanece exit 1.

Remote/optional-enabled: auditoria FX sem problemas; 51 programas Python têm
avisos (posições/alturas de troca não emitidas explicitamente, Start Z, Over,
e 13 programas com dwell errado no contrato de milissegundos). O M06 real pode
mover a máquina: a auditoria não mede esses movimentos nem prova qual seria
a posição física de parada.

Optional-disabled: auditor aponta pausa não ativa em 51 programas FX, incluindo
o caso cujo Python falha com dwell zero. É incompatibilidade do modelo escolhido
com a pausa solicitada, **não** falha escondida nem parâmetro silenciosamente
ignorado. O mesmo arquivo não é considerado seguro sob todo controlador.

A correção do tempo aumenta diferenças de usinagem frente ao Python original,
que continua emitindo o valor em segundos no campo interpretado como ms.
Não copiar esse defeito para elevar a contagem de equivalências. Fontes legadas
são verificadas por SHA-256 e não foram alteradas; tolerâncias/strict intactos.

Relatórios: `target/isel-contract-remote-20261023/python`,
`target/isel-contract-optional-enabled-20261023`,
`target/isel-contract-optional-disabled-20261023`. Controles de regressão:
GRBL mantém 49/19/34/2/0 (`target/isel-contract-grbl-control-20261023`), Marlin
shared mantém 0/68/34/2/0 (`target/isel-contract-marlin-control-20261023`).
36 regressões auxiliares Python passam.

Verify completo final aprovado: 1755 registros, **1737 passaram**, 18 skips
opcionais e zero falhas/erros: `target/isel-contract-full-final-20261023.log`.
52 testes direcionados do core/revisão também passaram. Contexto de QA
evidenciado em `../.agents/qa-project-context.md`; não é um plano fictício
de CI ou uma alegação de cobertura percentual global.

## Pendências

Equivalência completa não atingida, por diferenças deliberadas/defeitos do
legado e macros não simuladas. É necessário validar a configuração real antes
de usar em máquina, inclusive teste a seco. Próximo perfil da sequência:
line_xyz, cujo original ainda falha no corpus. Alterações ISEL sem commit/push;
nenhum projeto privado/preferência foi modificado.
