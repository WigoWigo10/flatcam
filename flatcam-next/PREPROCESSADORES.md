# Preprocessadores de G-code no FlatCAM FX

Atualizado em 2026-10-01. A escolha fica em **Drilling Tool** (somente fresagem)
e **Geometry → CNC Job** (fresagem e laser). O perfil padrão é
**FX portable (atual)**, que mantém a saída anterior sem alterações.

| Perfil no FX | Referência Python | Diferenças relevantes |
| --- | --- | --- |
| FX portable (atual) | Gerador anterior do FX | `G0/G1`, `M3/M5`; pausa `M0` entre ferramentas |
| default (Mach3, M6) | `preprocessors/default.py` | `G00/G01`, `M03/M05`, `Tn`, `M6`, `M0` |
| Default_no_M6 (Mach3) | `preprocessors/Default_no_M6.py` | Como acima, sem `M6` |
| grbl_11 (M6) | `preprocessors/grbl_11.py` | Como `default`, com `G17` |
| GRBL_11_no_M6 | `preprocessors/GRBL_11_no_M6.py` | Como `grbl_11`, sem `M6` |
| Marlin | `preprocessors/Marlin.py` | `G0/G1`, avanço nos G0, `M3`, `M400/M5`; troca `Tn/M6/M0` |
| Repetier | `preprocessors/Repetier.py` | Saída FAN `M106 S0-255` / `M107`; avanço nos G0; troca `M84` / `@pause` pelo Repetier-Host |
| Berta_CNC | `preprocessors/Berta_CNC.py` | `G17`, `G91.1`, `G64 P0.03`, `M110`, `G54`; encerra `M111/M30` |
| GRBL_laser | `preprocessors/GRBL_laser.py` | `M03 S...` / `M5` por caminho; sem mergulho de corte |
| Marlin_laser_FAN_pin | `preprocessors/Marlin_laser_FAN_pin.py` | `M106 S1-255` / `M400/M107`; avanço nos G0 |
| Marlin_laser_Spindle_pin | `preprocessors/Marlin_laser_Spindle_pin.py` | `M3 S...` / `M400/M5`; avanço nos G0 |
| Z_laser | `preprocessors/Z_laser.py` | `M03/M5`; Focus Z no início de cada ferramenta, com laser desligado |
| ISEL_CNC | `preprocessors/ISEL_CNC.py` | Somente mm: `G71`, `G00/G01`, `M03/M05`; troca `M06/M01` |
| Toolchange_Manual | `preprocessors/Toolchange_Manual.py` | Três pausas `M0`, ajuste da ferramenta em `G01 Z0`, retorno à altura livre; sem `M6` |
| Toolchange_Custom | `preprocessors/Toolchange_Custom.py` | Chama macro `M6` do controlador, sem `M0` adicional |

Os perfis Python são código executável; o FX porta explicitamente os comandos
essenciais, **não** executa os módulos Python nem reproduz seus
cabeçalhos completos. `Tn` identifica as ferramentas pela ordem do objeto de
Geometry ou pelo número do Excellon. Isolamento e cutout geram Geometry na
interface; a escolha do perfil acontece ao criar o CNC Job desse objeto. Seus
geradores diretos de G-code aceitam somente os perfis de fresagem.

São **14 perfis Python selecionáveis**, mais o FX portable. Somando `Paste_1`,
**15 dos 20 perfis do legado têm um port parcial**. A contagem antiga de 21
incluía `__init__.py`; FX portable não é um perfil Python. Pendentes: `hpgl`,
`ISEL_ICP_CNC`, `line_xyz`, `Roland_MDX_20` e `Toolchange_Probe_MACH3`.

## Limites e diferenças dos novos perfis

- **Feed rapids:** configurável para Marlin/Repetier e os lasers Marlin. Zero usa
  1500 mm/min ou o equivalente em polegadas. Os geradores diretos de
  Isolation/Cutout usam esse padrão. O valor de Geometry é preservado no
  `.fcnproj`; projetos antigos usam zero/automático.
- **Marlin:** conserva `M6` do perfil Python, sem certificar suporte no seu
  firmware. Os perfis novos de fresagem iniciam com a saída desligada; os
  antigos permanecem inalterados.
- **Repetier:** potência é PWM FAN, não RPM. Não emite `Tn` para evitar selecionar
  um extrusor; a identificação fica nos comentários. Antes de `M84` acrescenta
  `M400`. `@pause` precisa do Repetier-Host, não de envio direto ao controlador.
  `M84` libera motores: avalie a retenção da posição/referenciamento.
- **Laser:** potência inteira maior que zero; FAN limitada a 255. As demais
  saídas usam o limite definido pelo controlador, sem conversão automática de
  RPM para potência. A interface desabilita Cut Z, Multi-Depth, parâmetros V e
  troca mecânica; o gerador rejeita Multi-Depth/troca e pontos isolados. Exposição
  de pontos e repetição de passes laser ainda não estão implementadas.
- **Z laser:** altura compartilhada entre ferramentas, sem foco individual.
  Lasers Marlin aplicam Focus Z inicialmente; GRBL_laser não move Z inicialmente.
  Todos encerram desligados e vão a Focus/End Z (o FX ainda não separa essas alturas).
- **Prévia laser:** reconhece `FCFX LASER` e os nomes dos perfis nos cabeçalhos
  FX/Python. Classifica emissão por `M3/M4/M5` ou `M106/M107` e `S`, não pelo sinal
  de Z. Sem identificação de laser mantém a regra de fresagem. A estimativa de
  tempo não inclui a intervenção manual nas pausas.
- Compensações de mesa, troca XY e todos os parâmetros individuais do Python
  ainda não estão portados.
- **ISEL_CNC:** `G71` indica milímetros neste dialeto. Objetos IN são rejeitados,
  sem conversão silenciosa. `M01` é parada opcional; habilite-a no controlador
  se precisar trocar a ferramenta manualmente. O parser só reconhece G71 como
  unidades quando o cabeçalho identifica ISEL_CNC; não o aceita globalmente.
- **Toolchange_Manual:** requer origem Z correta e intervenção do operador nos
  três `M0`. `G01 Z0` tem feed explícito, usando o avanço de corte/furação atual;
  confira se esse avanço é adequado para o ajuste. A altura de retorno vem de
  Tool change Z na furação e Travel Z em Geometry. Não há sondagem automática.
- **Toolchange_Custom:** depende da macro M6 instalada/configurada na máquina;
  não carrega nem executa arquivos de macro no FX. O gerador seleciona Tn,
  chama M6 sem M0 adicional e reafirma G90/altura livre antes do próximo XY.
  A macro deve restaurar o sistema de coordenadas correto. O FX não simula seus
  movimentos internos. Esse retorno explícito também é aplicado após M06/M01 ISEL.
- **Troca inicial em Geometry:** a opção de troca agora pode ser ativada mesmo
  com uma única ferramenta, para executar a sequência inicial. Se desativada,
  não emite M6/M06 nem o ajuste manual em Z0. Laser continua sem troca mecânica.
  Nos geradores diretos de Isolation/Cutout não existe sequência de troca.

## Pasta e validação

**Dispensador de pasta (`Paste_1`).** O único pré-processador de pasta do Python é gerado diretamente pelo
SolderPaste Tool (`org.flatcam.cam.solderpaste.SolderPaste`), não pela escolha de perfil acima: cabeçalho, troca de
bico (`Tn`, `M6`, `M0`), e por caminho `G00` até o ponto, Z de deslocamento, Z de início, `M03` e espera, Z de
dispensa, avanço XY, `M05`, `M04`, Z de parada, `M05`, espera e retorno ao Z de deslocamento. Diferenças do Python:
velocidade e espera são escritas quando maiores que zero, o programa termina só subindo para a altura de troca (sem
X,Y final) e os parâmetros valem para todos os bicos. Conferir unidades, alturas Z e o comportamento de `M0`
antes de usar numa máquina.

Este incremento não altera SolderPaste: permanecem as limitações de seleção
de Geometry e de reclassificação da dispensa ao reabrir identificadas na revisão.

`GCodePreprocessorTest` cobre comandos, ferramentas/slots, unidades, cancelamento,
limites de potência, desligamento nos G0 e reabertura de laser, inclusive arcos.
`GeometryCncToolPanelTest` verifica os controles na thread FX, sem janela visível
(Windows). `ProjectFileIOTest` verifica persistência e compatibilidade do avanço.

**Antes de enviar a uma máquina:** confira unidades, alturas Z, ordem de
ferramentas, comportamento de `M0` e suporte de `M6` no controlador. Faça uma
simulação e um teste a seco. O nome `grbl_11` reproduz o perfil do Python,
mas não garante que todo firmware GRBL aceite `M6`.
Confira também potência e pinagem do laser/FAN. Testes de software não
certificam o comportamento físico do controlador.
