# Laser — geração e comparação por perfil

## Atualização — 2026-10-24

End Z/XY comuns agora são aceitos no backend/painel e aparecem no resumo.
Potência desligada, Z em `max(Focus/End Z, End Z solicitado)`, depois XY e
eventual descida final. Marlin aguarda todo esse estacionamento com M400.
Start Z, troca mecânica, exclusões e parâmetros individuais continuam fora.
Corpus ampliado a 28 receitas/perfil: GRBL 0/28/0/0/0 e os outros três
0/0/28/0/0; zero problemas auditados FX em shared, strict não aprovado.
Detalhes e validação: [NATIVE_PROBE_LASER_PARITY.md](NATIVE_PROBE_LASER_PARITY.md).
As restrições a todas as posições avançadas e os números 24 abaixo descrevem
a rodada anterior e foram substituídos apenas quanto a End Z/XY e ao corpus.

## Etapa de 2026-10-23

Base commitada `a0f19c06`, branch `flatcam-next`, sobre as alterações locais
Repetier/Berta preservadas. Etapas reunidas no commit de consolidação de
2026-10-24 (ver git log), sem push. Nenhum
projeto privado nem fonte original Python foi alterado. Geração já existia;
agora os quatro perfis possuem leitor/auditoria próprios e correções de emissão.

## Dois defeitos FX comprovados

1. `generateLaserCncJob` escrevia G94 diretamente. A regra `usesG94()` havia
   sido corrigida na etapa anterior, mas o gerador laser não a consultava.
   Agora consulta: Marlin laser não recebe G94; os dois GRBL conservam G94.
2. Nos dois Marlin laser, o último M400 antecedia o estacionamento Z final.
   Agora há M400 depois dele. Não foi acrescentado M30. A barreira aguarda
   movimentos do planejador, não confirma desligamento físico do feixe.

Fontes primárias: [dispatcher Marlin 2.1.2.5](https://raw.githubusercontent.com/MarlinFirmware/Marlin/2.1.2.5/Marlin/src/gcode/gcode.cpp)
e [M400](https://raw.githubusercontent.com/MarlinFirmware/Marlin/2.1.2.5/Marlin/src/gcode/motion/M400.cpp).
Não foi alterado o emissor Repetier para implementar a finalização laser.

Reprodução mínima, em Windows/Java 25/Maven wrapper, a partir de `flatcam-next`:

```powershell
.\mvnw.cmd -pl flatcam-cam -am -Dtest=LaserControllerRegressionTest -Dsurefire.failIfNoSpecifiedTests=false test
```

`LaserControllerRegressionTest` cria uma linha, gera os dois perfis Marlin em
MM/IN e exige ausência de G94 e M400 após o último rápido Z. Antes: G94 presente
e arquivo terminando em G0. Depois: modo nativo e barreira final. Dez repetições,
sem tempo/rede/aleatoriedade/controlador envolvidos. Teste colocado no stage
antes do ajuste; red/green/red/green confirmado retirando apenas os dois ajustes
desta etapa e restaurando-os. Evidências em `target/laser-controller-{red,green,
revert-red,restored-green}-20261023.log`. Red: dez falhas, zero erros.
Commit introdutor não determinado: sem baseline conhecido correto para bisect;
o worktree anterior foi preservado, não se atribuiu um culpado por suposição.

## Leitor laser independente

`tools/laser_execution.py` não reutiliza o critério de fresagem Z negativo:
caminho XY iluminado conta também em Z zero/positivo. Conserva direção,
repetições, ordem, avanços, plano Z, potência bruta, modos M3/M4/FAN, comandos
de desligamento, rápidos e M400. Não transforma PWM em RPM físico.

Estado inicial de potência é desconhecido até um comando explícito. A origem
comandada XYZ zero é uma hipótese declarada, não detecção da posição real.
Grupo-fonte 1 não representa seleção física de ferramenta. A auditoria exige
um grupo, sem Tn/M6/M0, sondagem, arcos, movimento incremental ou comandos
inline não modelados. Pontos/exposição estacionária não são simulados.

GRBL: conferir [escala/configuração $30](https://github.com/gnea/grbl/wiki/Grbl-v1.1-Configuration)
e [$32/modo laser](https://github.com/gnea/grbl/wiki/Grbl-v1.1-Laser-Mode).
A auditoria verifica parada comandada antes dos rápidos, sem fingir conhecer
o comportamento óptico de $32 nem a calibração da escala S. M3 e M4 não são
equivalentes. Marlin: modo padrão, não inline; IN requer suporte no firmware.
[M106](https://marlinfw.org/docs/gcode/M106.html) tem S em 0–255 e sem S usa
255; o FX mantém a exigência laser de 1–255. FAN, circuito elétrico e
LASER_SYNCHRONOUS_M106_M107 são pré-condições, não certificados pelo leitor.
Configuração de [M3/M5](https://raw.githubusercontent.com/MarlinFirmware/Marlin/2.1.2.5/Marlin/src/gcode/control/M3-M5.cpp)
e mapeamento da potência também não são descobertos automaticamente.

O cabeçalho original Z_laser contém `(Laser Power (Spindle Speed): ...)`.
O leitor separa esse texto para diagnóstico, registra `nonstandardComment`
e acrescenta um aviso que impede equivalência completa. Isso não é um crash
Python, nem comprova aceitação da sintaxe pelo controlador. Delimitadores
desbalanceados ou comandos desconhecidos continuam recusados.

## Corpus público

24 receitas MM/IN por perfil: linha, contorno fechado, LinearRing preservado,
polígono com corte interno, trajetos separados, ordem-fonte invertida,
coordenadas negativas, potência baixa/alta, rápido automático, foco e avanço
lento. São **96 casos únicos**, não 104 receitas de fresagem forçadas a laser.
Exclusões explícitas: Drilling, pontos, Multi-Depth, troca mecânica, Dwell,
Extra Cut, posições avançadas e parâmetros individuais; não contam como paridade.
Fixtures FAN usam 1/128/255; as demais usam S 1/128/1000, sem alegar que 1000
seja o máximo universal do controlador. Fontes originais verificadas por SHA-256.

| Perfil | Execução igual | Rota diferente | Usinagem diferente | Oracle error | FX error |
| --- | ---: | ---: | ---: | ---: | ---: |
| GRBL_laser | 0 | 24 | 0 | 0 | 0 |
| Z_laser | 0 | 0 | 24 | 0 | 0 |
| Marlin_laser_FAN_pin | 0 | 0 | 24 | 0 | 0 |
| Marlin_laser_Spindle_pin | 0 | 0 | 24 | 0 | 0 |

Em shared/variable, nenhum desvio auditado FX. GRBL possui os mesmos cortes
iluminados dirigidos, mas conserva diferenças de eventos; não remover M5
adicional para fabricar MATCH_EXECUTION. Z/Marlin diferem no plano de foco:
nas receitas com Start Z vazio/troca desativada, o Python não comanda esse
plano; FX o posiciona automaticamente. Z_laser tem também o cabeçalho acima.
Marlin Python não tem a barreira depois do estacionamento final.

Resultados em `target/laser-{grbl,z,fan,spindle}-contract-20261023/index.html`.
Strict permanece exit 1 nos quatro perfis, não foi relaxada a tolerância.

Marlin FAN e SPINDLE também repetidos nos modos variable e fixed=900 mm/min:
24 MACHINING_DIFFERENT por modo, sem erros de leitura. Em fixed, os dois casos
`rapid-default` de cada perfil pedem 1500 mm/min e a auditoria FX detecta a
limitação compilada de 900: isso não é defeito de emissão nem pode ser ocultado.
Repetir o corpus sob outra configuração não cria novos casos/projetos únicos.
Artefatos `target/laser-{fan,spindle}-{variable,fixed}-20261023`.

```powershell
.\compare-cnc-parameters.ps1 -Profile GRBL_LASER -Strict
.\compare-cnc-parameters.ps1 -Profile Z_LASER -Strict
.\compare-cnc-parameters.ps1 -Profile MARLIN_LASER_FAN_PIN -Strict
.\compare-cnc-parameters.ps1 -Profile MARLIN_LASER_SPINDLE_PIN -MarlinRapidMode variable -Strict
```

## Aplicativo Python real e limites de foco

Quatro controles por perfil, fora da tela/configurações temporárias: sem troca,
XY de troca distinto, campo XY vazio e Dwell zero. Todos geraram código (16
GENERATED), fontes intactas; Dwell laser é omitido no legado, não falha como
os perfis de fresagem. Não são testes de máquina ou equivalência de execução.

O app real confirmou: Z_laser com troca ativada posiciona Z de foco; sem troca
não. Marlin laser sem troca e com Start Z preenchido posiciona `z_move`, não
o valor numérico de Start Z; com troca ativada o caminho pula startz_code e a
troca do perfil é vazia, não posicionando o foco. São condições do fluxo,
não uma afirmação de defeito universal em qualquer uso do Python.

Relatórios `target/laser-{grbl,z,spindle}-live-20261023.json` e
`target/laser-fan-live-serial-20261023.json`. A primeira tentativa FAN paralela
encerrou antes de produzir relatório, exit 1, sem causa confirmada; log
`target/laser-fan-live-launch-20261023.log` preservado. Repetição serial passou;
isso não prova causa nem elimina a tentativa inconclusiva. Os comandos usam
potência 128 explícita, não RPM 12000 limitado artificialmente para FAN.

## Revisão antes da exportação e validação

Resumo CNC agora identifica S laser/PWM, mostra foco e Z final emitidos e não
promete Dwell/Extra Cut ignorados. Explica limites de posições, G0 fixo,
configuração FAN/GRBL e que a prévia não valida feixe/material/intertravamentos.
End XY avançado e ajustes individuais continuam recusados, não implementados.

Verify integral: **1869 registros Java / 1851 aprovados / 18 skips opcionais /
zero falhas e erros** (`target/laser-full-20261023.log`). 134 auxiliares Python
aprovados, incluindo 14 contratos laser e o diagnóstico de potência;
`target/laser-python-final-20261023.log`. Controle GRBL fresagem manteve
49/19/34/2/0 em `target/laser-grbl-milling-control-20261023`.

Próximos candidatos: leitores nativos HPGL/Roland e sondagem Mach3; posições
laser avançadas, configurações reais de controladores, frames ICP e seleção
Custom continuam pendentes. Não copiar falhas do legado ou remover proteções
FX para elevar a contagem. Nenhuma equivalência física/segurança foi declarada.
