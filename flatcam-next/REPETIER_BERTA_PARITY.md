# Repetier e Berta — contrato de comandos e limites

## Revisão em 2026-10-23

Base `a0f19c06` (Manual/Custom), branch `flatcam-next`. Etapa reunida com laser
no commit de consolidação de 2026-10-24 (ver git log), sem push.
Nenhum projeto privado ou fonte original Python foi alterado.
O ensaio compara pedidos e comandos; não certifica máquina, firmware ou GUI.

## Defeito reproduzido e corrigido

A interface fornece Dwell em segundos. Repetier e Marlin interpretam `G4 P`
em **milissegundos**, independentemente de MM/IN. O FX emitia `G4 P0.2500`
para 0,25 s; agora emite `G4 P250.0000`. O leitor independente também foi
corrigido: não interpreta mais o erro do legado como 0,25 s.
O temporizador nativo recebe milissegundos inteiros: `P0.25` acaba em zero,
não em 0,25 s nem em uma espera fracionária de 0,25 ms. O leitor conserva o
truncamento e recusa valores fora do intervalo modelado. Referência adicional:
[parser de tempo Marlin](https://raw.githubusercontent.com/MarlinFirmware/Marlin/2.1.x/Marlin/src/gcode/parser.h).

Fontes primárias consultadas: [Repetier Commands.cpp](https://raw.githubusercontent.com/repetier/Repetier-Firmware/master/src/ArduinoAVR/Repetier/Commands.cpp)
e [Marlin G4.cpp](https://raw.githubusercontent.com/MarlinFirmware/Marlin/2.1.x/Marlin/src/gcode/motion/G4.cpp).
Os dispatchers consultados não têm G94: retirado também do Repetier e dos
dois perfis Marlin laser. A regra de Dwell compartilhada cobre esses perfis;
isso não significa que o gerador laser tenha passado a usar Dwell.
Retificação posterior: o helper usesG94() foi corrigido aqui, mas o gerador
laser ainda escrevia G94 diretamente. A emissão efetiva foi corrigida e
comprovada na etapa [LASER_PARITY.md](LASER_PARITY.md), sem alterar Repetier.
ISEL conserva sua conversão anterior. Berta conserva P em segundos conforme
o contrato legado; esse comportamento Berta não foi confirmado em hardware.

Reprodução mínima, de `flatcam-next`:

```powershell
.\mvnw.cmd -pl flatcam-cam -am -Dtest=RepetierDwellRegressionTest -Dsurefire.failIfNoSpecifiedTests=false test
```

`RepetierDwellRegressionTest` usa apenas linhas/furos sintéticos, sem rede,
relógio, aleatoriedade ou controlador. Repetier/Marlin × MM/IN × Geometry/
Drilling são verificados em cada uma das dez repetições. Berta funciona como
controle de não conversão. Ambiente: Windows, Java 25, Maven do wrapper;
baseline `a0f19c06`. Commit introdutor não determinado: não houve bisect,
pois não há baseline conhecido correto para esta semântica.
Primeiro red comportamental: dez falhas, zero erros, em
`target/repetier-dwell-red-20261023.log`; primeiro green em
`target/repetier-dwell-green-20261023.log`. Teste adicionado ao stage antes
da correção. Não foi usado um erro de compilação como prova de reprodução.
Retirada temporária somente da correção de tempo voltou a produzir dez
falhas; restauração passou. Evidências `target/repetier-dwell-revert-red-20261023.log`
e `target/repetier-dwell-restored-green-20261023.log` (red/green/red/green).

## Repetier: FAN não é spindle/RPM

- Fixtures escolhem explicitamente PWM **128/200** por ferramenta; o gerador
  continua recusando valores fora de 0–255. Não converte nem limita RPM.
  `powerMeaning=FAN_DUTY_0_255` documenta o campo histórico `rpm` do JSON.
- Leitor registra `fanDuty`, com `rpm/spindle=null`, e distingue M106 de M107.
  M106 sem S significa 255, não zero ou o valor solicitado na interface.
- `@pause` é uma **solicitação ao operador**, condicionada ao Repetier-Host.
  Só são reconhecidos os formatos conhecidos FX/Python; comentários não
  selecionam ferramenta. Tn de extrusor não é aceito como troca CNC.
- M84 e barreiras M400 permanecem eventos. M84 pode liberar a fixação dos
  eixos e perder a referência: XYZ posterior é somente posição comandada,
  condicionada à referência preservada/restabelecida pelo operador.
- G94 original é registrado como comando não suportado e REPROVADO pela
  auditoria, não transformado em modo válido para melhorar a pontuação.
- Revisão antes de exportar identifica PWM, unidades Dwell e limites de troca.

## Berta: macros e origem desconhecidas

Leitor observa G91.1 (não confundir com G91), G64 P0.03, M110, G54,
M111 e M30. Conserva sequência, posição e unidade da tolerância: 0,03 mm
em MM ou 0,762 mm em IN. Isso não é precisão física garantida.

Não há configuração/documentação específica desta máquina que permita
determinar os efeitos de M110/M111 e offsets G54. Não são tratados como
no-ops conhecidos, nem se afirma que a Berta usa firmware Marlin ou LinuxCNC.
`bertaControls` conserva os comandos opacos; a auditoria acrescenta um
limite explícito em TODOS os casos, impedindo MATCH_EXECUTION/strict aprovado.
Geometria comandada ainda é útil para diagnóstico, não execução completa.
Não foram removidas macros nem alterado P0.03 no gerador para fabricar acordo.
A revisão CNC apresenta esse limite e a diferença de tolerância em IN.

## Comparação pública e aplicativo Python real

104 receitas MM/IN por perfil, fontes originais verificadas por SHA-256.

| Perfil | Execução igual | Rota diferente | Usinagem diferente | Oracle error | FX error |
| --- | ---: | ---: | ---: | ---: | ---: |
| Repetier | 0 | 0 | 100 | 2 | 2 |
| Berta_CNC | 0 | 68 | 34 | 2 | 0 |

Repetier: os dois FX_ERROR são **limites do leitor**, não crashes do gerador:
sem troca solicitada, não se conhece a ferramenta inicialmente instalada.
`fxErrorKind=UNMODELED_TOOL_SELECTION_NOT_A_GENERATOR_CRASH`. Os outros 102
programas FX passam a auditoria de parâmetros. Todos os cem pares legíveis
conservam diferenças de comandos FAN, além das diferenças preexistentes de
rotas/passes/rasgos; a parada adicional FX não é apagada da assinatura.
O Python também emite G94, rápidos com avanço herdado/divergente e Dwell em
unidade errada; Start Z pode substituir Change Z na primeira troca.

Berta: 104 saídas FX têm o aviso de macros/origem não modeladas, sem outro
desvio auditado. Esse aviso NÃO é defeito numérico, mas impede alegar execução
equivalente. Permanecem diferenças de altura de troca, Over, passes e rasgos
no legado. Em ambos, os dois ORACLE_ERROR são falhas reais de Dwell zero.
Strict retorna 1: esses resultados não são paridade completa.

Artefatos comparativos: `target/repetier-timer-final-20261023/index.html` e
`target/berta-contract-20261023/index.html`, com JSON, programas e traces.
Repetição (diretório novo):

```powershell
.\compare-cnc-parameters.ps1 -Profile REPETIER -Strict
.\compare-cnc-parameters.ps1 -Profile BERTA_CNC -Strict
```

O aplicativo Python real, fora da tela e com configurações temporárias,
gerou no-change, XY distinto e campo XY vazio; Dwell zero falhou no camlib.
Três GENERATED + um ERROR por perfil; fontes originais inalteradas.
`GENERATED` não significa execução física validada. Relatórios:
`target/repetier-live-20261023.json` e `target/berta-live-20261023.json`.
O diagnóstico Repetier solicita PWM 128 explicitamente, não RPM 12000.

```powershell
& target\oracle-py311\Scripts\python.exe tools\open_in_python_app.py --legacy-root .. `
  --probe-cnc-profile Repetier --probe-output target\repetier-live-new --report target\repetier-live-new.json
```

119 testes auxiliares Python passaram, incluindo dez novos contratos
Repetier/Berta e o teste do PWM no diagnóstico real. Evidência:
`target/repetier-berta-python-final3-20261023.log`. A seleção anterior de 62
auxiliares não era toda a descoberta `test_*.py`; 119 não significa 57 testes
novos desta etapa.

Verify integral: **1858 registros Java / 1840 aprovados / 18 skips opcionais /
zero falhas e erros**, incluindo dez repetições do novo teste. Log
`target/repetier-berta-full-20261023.log`; não se afrouxou limpeza temporária.

Controle GRBL: 49/19/34/2/0, inalterado, em
`target/repetier-grbl-control-20261023/python/index.html`. Marlin shared,
variable e fixed=1500 mm/min: agora **0/44/58/2/0** em cada um, zero problemas
nos parâmetros FX. Mais diferenças de usinagem são expostas porque o leitor
deixou de interpretar o Dwell errado do legado como segundos. Resultados
anteriores 0/68/34/2/0 não são o resultado atual. Artefatos
`target/marlin-shared-timer-final-20261023`,
`target/marlin-variable-timer-final-20261023` e `target/marlin-fixed-timer-final-20261023`.

## O que não foi fechado

Não copiar bugs Python para elevar a pontuação. Faltam validar configuração
real Repetier-Host/FAN/referência e macros/origem Berta, inclusive GUI e teste
a seco. A comparação laser foi ampliada posteriormente em LASER_PARITY.md;
HPGL/Roland e sonda Mach3 ainda precisam do ensaio específico.
WPCLEAR/frames ICP e seleção implícita Custom continuam
limites separados. Este avanço não significa paridade global ou máquina segura.
