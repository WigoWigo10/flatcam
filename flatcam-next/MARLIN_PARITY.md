# Marlin CNC — contrato de geração e comparação

Nota posterior laser: alterar `usesG94()` não bastava para o emissor laser,
que tinha G94 direto. Corrigido junto ao M400 depois do estacionamento em
[LASER_PARITY.md](LASER_PARITY.md). Esta nota não declara equivalência física.

## Correção posterior do Dwell — 2026-10-23

G4 P também usa milissegundos no Marlin: FX e leitor independente corrigidos
na etapa [Repetier/Berta](REPETIER_BERTA_PARITY.md). 0,25 s passa a P250,
independentemente de MM/IN. O Python original não foi modificado. Os três
modos G0 agora têm 0/44/58/2/0, não 0/68/34/2/0; a auditoria FX continua sem
desvios. A regra de G94 foi estendida aos dois perfis Marlin laser e Repetier.
O registro abaixo é histórico: seus números e escopo não são a rodada atual.

## Revisão em 2026-10-23

Esta etapa trata somente de **MARLIN de fresagem**. Não muda ISEL, Repetier,
laser/FAN, nem o código original Python. Não declara compatibilidade automática
com uma impressora Marlin ou equivalência física do movimento.

## Correções no FX

- Cabeçalhos de fresagem/furação Marlin deixam de emitir **G94**. O dispatcher
  Marlin 2.1.2.5 consultado não possui esse comando; avanços usam nativamente
  unidades/minuto. Outros perfis conservam o comportamento anterior.
- O arquivo termina com **M400 após o estacionamento final**, incluindo End XY
  e End Z. O M400 anterior ao M5 final só aguardava os movimentos anteriores,
  não os rápidos de estacionamento posteriores. Não se acrescenta M30.
- Revisão Geometry/Drilling e descrição do perfil explicitam configuração de
  spindle/RPM, troca/pausa, unidades IN e comportamento dos rápidos. S não é
  RPM em toda configuração Marlin, e M400 não mede a parada física do spindle.

Referências primárias: [comandos do firmware 2.1.2.5](https://github.com/MarlinFirmware/Marlin/blob/2.1.2.5/Marlin/src/gcode/gcode.cpp),
[G0/G1](https://marlinfw.org/docs/gcode/G000-G001.html),
[M400](https://marlinfw.org/docs/gcode/M400.html),
[M3](https://marlinfw.org/docs/gcode/M003.html).

## Uma correção importante no próprio ensaio

Não existe um único comportamento G0 em todas as compilações do Marlin.
O leitor anterior assumia feed compartilhado. Agora há três modelos explícitos,
baseados em [G0_G1.cpp 2.1.2.5](https://github.com/MarlinFirmware/Marlin/blob/2.1.2.5/Marlin/src/gcode/motion/G0_G1.cpp):

| Modelo | G0 | Efeito sobre o feed G1 |
| --- | --- | --- |
| `shared` | Usa o F modal compartilhado | G0 F altera o feed posterior |
| `variable` | Mantém F próprio para G0 | Restaura o F G1 anterior |
| `fixed` | Usa G0_FEEDRATE compilado, mesmo com F no bloco | Restaura o novo F lido no bloco |

O valor inicial de G0 variável fica **desconhecido** até ser declarado; não
se inventa o default do firmware. Modo fixo exige informar uma velocidade
positiva em mm/min, convertida pelo leitor para unidades IN quando necessário.
É uma condição do ensaio, não detecção da configuração da máquina.
O FX declara F em cada movimento dos controles, evitando herança incorreta
entre G0/G1 em shared/variable. Em fixed, o firmware pode ignorar Feed rapids;
um valor compilado diferente do solicitado é detectado pela auditoria.

Exemplo, a partir de `flatcam-next` (usar diretórios novos em cada rodada):

```powershell
.\compare-cnc-parameters.ps1 -Profile MARLIN -MarlinRapidMode shared -Strict
.\compare-cnc-parameters.ps1 -Profile MARLIN -MarlinRapidMode variable -Strict
.\compare-cnc-parameters.ps1 -Profile MARLIN -MarlinRapidMode fixed -MarlinFixedRapidMm 1500 -Strict
```

## Resultados públicos MM/IN

Cada modo executou os **104 casos**, com fontes Python originais verificadas
por SHA-256. Resultado em cada um: 0 MATCH_EXECUTION, 68 ROUTING_DIFFERENT,
34 MACHINING_DIFFERENT, dois ORACLE_ERROR (dwell zero), zero FX_ERROR.
Auditoria de parâmetros FX sem problemas nos três modos ensaiados.

Shared/variable: 102 programas Python legíveis têm pelo menos um feed de G0
divergente ou desconhecido no modelo. Fixed=1500 mm/min: apenas oito programas
Python falham a auditoria (alturas/pausas/Over). Portanto, omitir F em G0 **não
é defeito universal**: depende da configuração. A afirmação anterior sobre
todos os rápidos Python deve ser lida com essa restrição, não como prova para
todo firmware. As três rodadas não são três projetos adicionais reais.

`fxMarlinControls` e `pythonMarlinControls` registram pontos M400, barreira final,
seleções/trocas/pausas e estado final comandado do spindle. Eventos adicionais
ou ausentes não são removidos para forçar equivalência. O FX conserva barreiras
de inicialização, altura de troca validada, G90 após troca e a nova barreira final.
As diferenças anteriores de ordem, passes e conversão de rasgos também ficam.

Artefatos: `target/marlin-contract-{shared,variable,fixed}-20261023/python`.
Strict continua exit 1 esperado; nenhuma tolerância foi aumentada. O leitor
agora recusa G94 em MARLIN em vez de tratá-lo como comando válido desse dialeto.

## Pré-condições que não foram simuladas

- `SPINDLE_FEATURE` e `CUTTER_POWER_UNIT=RPM` para interpretar S como RPM.
- `Tn/M6` mantidos pelo perfil legado precisam de configuração/macros CNC
  compatíveis. [Tn seleciona ferramentas/extrusores](https://marlinfw.org/docs/gcode/T.html)
  e pode executar movimentos internos; não é uma simples etiqueta no firmware.
- [M0](https://marlinfw.org/docs/gcode/M000-M001.html) precisa de suporte de
  pausa (LCD ou EMERGENCY_PARSER); não supor que toda placa para ao recebê-lo.
- IN requer `INCH_MODE_SUPPORT`. Não foram modelados limites, aceleração,
  ferramenta física, SCARA, offsets internos de Tn, nem macros M6.

MarlinContractTest verifica MM/IN em Geometry e Excellon com duas ferramentas,
peck/rasgo, Start Z distinto da troca, End Z baixo e estacionamento XY: sem
G94, F explícito, parada comandada antes das pausas e M400 depois da última
posição. CncPreflightTest verifica avisos sem contaminar outros perfis.
35 regressões Python cobrem os três modos, ausência de F inicial, taxa fixa
incompatível, estado modal, barreira final e rejeição de G94/combinações inválidas.

Verify completo aprovado: 1750 registros, 1732 passaram, 18 skips opcionais,
zero falhas/erros em `target/marlin-contract-full-20261023.log`. Regressão do
comparador GRBL manteve 49/19/34/2/0, em
`target/marlin-contract-grbl-control-20261023/index.html`. Novas alterações
ainda sem commit/push. Sem instalação de dependências ou alterações em
preferências/projetos privados.

## Próximos limites

Equivalência completa com o Python original continua **não atingida**.
Não remover proteções FX nem copiar falhas do legado para elevar a contagem.
Próximo perfil: ISEL CNC. Marlin em hardware exige conhecer configuração real
e validar origem, troca, velocidades e teste a seco. Nenhum teste físico ocorreu.
