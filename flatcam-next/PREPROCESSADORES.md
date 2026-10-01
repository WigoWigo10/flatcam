# Preprocessadores de G-code no FlatCAM FX

Atualizado em 2026-10-01. A escolha fica em **Drilling Tool** (somente fresagem)
e **Geometry → CNC Job** (fresagem, laser e plotter). O perfil padrão é
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
| line_xyz | `preprocessors/line_xyz.py` | XYZ explícitos nos movimentos G00/G01; troca `Tn/M6/M0` |
| ISEL_ICP_CNC | `preprocessors/ISEL_ICP_CNC.py` | Formato ICP: `IMF_PBL`, `FASTABS/MOVEABS`, `VEL`, `GETTOOL`, `SPINDLE`, `WAIT`, `PROGEND` |
| hpgl | `preprocessors/hpgl.py` | Formato HPGL: `IN`, `PU/PD`, `PA`, `SP`; caneta levantada e `SP0` ao encerrar |
| Roland_MDX_20 | `preprocessors/Roland_MDX_20.py` | RML-1: `;;^IN;`, `^PA`, `Zx,y,z`, `V`, `!MC1/!MC0`; somente MM e uma ferramenta por arquivo |

Os perfis Python são código executável; o FX porta explicitamente os comandos
essenciais, **não** executa os módulos Python nem reproduz seus
cabeçalhos completos. `Tn` identifica as ferramentas pela ordem do objeto de
Geometry ou pelo número do Excellon. Isolamento e cutout geram Geometry na
interface; a escolha do perfil acontece ao criar o CNC Job desse objeto. Seus
geradores diretos de G-code aceitam somente os perfis de fresagem.

São **18 perfis Python selecionáveis**, mais o FX portable. Somando `Paste_1`,
**19 dos 20 perfis do legado têm um port parcial**. A contagem antiga de 21
incluía `__init__.py`; FX portable não é um perfil Python. Pendentes:
`Toolchange_Probe_MACH3`.

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
- **line_xyz:** XYZ explícitos em todos os G00/G01, com coordenadas modais preservadas.
  Corrige o erro do Python que repete X no campo Y da troca. Furação com X/Y repetidos
  num mergulho puramente Z continua sendo reconhecida como furo, não desaparece da prévia.
- **ISEL_ICP_CNC:** inicialmente somente MM. Coordenadas inteiras em micrômetros,
  VEL em micrômetros/s e WAIT em milissegundos, truncados como o `int()` do Python;
  valores fora do limite inteiro ou avanço abaixo de 1 µm/s são rejeitados.
  GETTOOL é emitido para cada ferramenta, sem depender da pausa manual; a opção
  de troca manual fica desabilitada. Após GETTOOL, reafirma a altura de retorno.
  Ao final usa End move Z/XY da furação ou Travel Z de Geometry e PROGEND, sem
  WPCLEAR nem retorno forçado à origem/Z0 do Python. Configure a origem e a troca
  automática no controlador; os movimentos internos de GETTOOL não são simulados.
- **Arquivos ICP:** geração, abertura e exportação do editor aceitam `.imf`.
  Reabertura do projeto usa o texto embutido. O leitor cobre os comandos gerados
  acima e recusa mudanças de origem como WPCLEAR, movimentos relativos, homing e
  comandos não modelados, deixando a prévia indisponível em vez de inventar um
  percurso. Isso também limita a leitura de arquivos ICP legados que terminam
  com WPCLEAR. A estimativa de tempo não mede troca, WAIT nem velocidade real
  de FASTABS; continua aproximada. ICP gera movimentos lineares, sem arcos nativos.

## HPGL: geração e prévia

- Disponível em **Geometry → CNC Job → Preprocessor → hpgl**. Cada ferramenta
  corresponde a uma caneta `SP`, numerada pela ordem da Geometry. O diâmetro
  é a largura de desenho da prévia; a caneta física deve ser configurada no plotter.
  Geometry de isolamento, cutout e NCC pode ser usada como fonte. Não há perfil
  HPGL em Drilling nem nos geradores diretos de fresagem.
- Gera `.plt`; abertura de programas CNC, Salvar como e o editor também aceitam
  `.hpgl` e `.hpg`. **Importar HPGL2** continua sendo outro fluxo, criando Geometry
  com o importador já existente, mais abrangente que a prévia de programas CNC.
- MM e IN são convertidos para passos de **0,025 mm**, com arredondamento para
  o inteiro par nas metades, como `round()` do Python. A faixa é a mesma do perfil
  legado, **-32767..32768** por eixo, mas o FX recusa excessos em vez de cortar
  coordenadas silenciosamente. A geometria de plot usa os pontos arredondados.
- Uma passagem por caminho; Z, spindle, feed, Multi-Depth, V-tip e troca mecânica
  ficam desabilitados sem apagar os valores anteriores. O gerador recusa Multi-Depth,
  pausa mecânica, pontos isolados e caminhos que colapsam pela resolução. Não emite
  comandos de velocidade, como o perfil Python: a estimativa de tempo fica indisponível.
- Encerra com **PU e SP0**, sem movimento adicional à origem. Cabeçalho e metadados
  usam comentários nativos `CO`, incluindo unidades de origem e larguras de caneta.
  Isso permite salvar/reabrir o projeto sem o arquivo externo. A prévia de arquivos
  FX mantém MM/IN da fonte; arquivos externos sem esses metadados aparecem em MM,
  com largura fina quando não declarada. A posição física inicial não é conhecida.
- O leitor CNC cobre **IN inicial, CO, SP, PU/PD e PA/PR**, múltiplos pares e comandos
  na mesma linha, terminados por `;`. Recusa reset IN adicional, troca SP com caneta
  baixada e comandos não modelados (incluindo SC/IP, arcos, texto e velocidade), em
  vez de apresentar um percurso incorreto. Mantém cancelamento e progresso. A geração
  não depende do limite de 50 mil segmentos da prévia detalhada do editor.

## Roland MDX-20: RML-1

- Disponível em **Drilling Tool** e **Geometry → CNC Job**. Gera `.rml`; abertura
  de programas CNC, editor e Salvar como também aceitam `.prn`. Usa apenas comandos
  nativos, sem comentários de G-code nem metadados inventados no arquivo da máquina.
- **Somente MM nesta primeira versão**, como os perfis ISEL. IN é recusado
  explicitamente; a divisão por 25,4 do Python não é reproduzida. Converta o objeto
  para MM antes de gerar. Emite XYZ explícitos em unidades de 1/40 mm, com uma
  casa decimal, conservando a precisão textual XY do perfil Python e aplicando-a
  também a Z. Isso não promete resolução física de 0,0025 mm no equipamento.
- **Uma ferramenta não vazia por arquivo**, sem troca automática ou pausa manual.
  A interface bloqueia troca e avisa sobre múltiplas ferramentas antes de abrir
  o diálogo de destino. Furação exige selecionar uma única linha. Geometry V
  continua exigindo seus parâmetros de ponta/ângulo; Multi-Depth permanece disponível.
- O motor é binário: **!MC1 liga e !MC0 desliga**, sem parâmetro de RPM. O perfil
  liga automaticamente durante a usinagem, mesmo com RPM = 0; esse campo fica
  desabilitado no painel. Dwell também fica desabilitado e a API recusa espera,
  em vez de ignorar silenciosamente. Retornar a outro perfil restaura a edição
  dos campos sem apagar os rascunhos anteriores.
- **Feed e Feed rapids: 6..900 mm/min**, convertidos para `V0.1..V15.0` mm/s
  com uma casa decimal. Rapid = 0 usa 900 mm/min. Valores fora da faixa são
  recusados, sem o clamp do Python e sem seu erro de mínimo (6 mm/s em vez de
  0,1 mm/s). A faixa foi conferida na seção 12 do
  [manual oficial MDX-20/15](https://downloadcenter.rolanddg.com/contents/manuals/MDX-20_USE_EN_R7.pdf).
- Encerra na altura livre definida e, em Drilling, no XY final opcional, sem
  homing/reset adicional. O primeiro movimento nativo XYZ assume X/Y = 0:
  **posicione na origem XY e confira a folga Z antes de executar**. O limite
  numérico do encoder não verifica o curso físico da mesa nem colisões.
- A prévia cobre **^IN inicial, ^PA, V, Z e !MC**; também lê o !MC sem `;`
  que o Python escreve. Movimentos relativos, resets adicionais, mudanças de
  origem e comandos não modelados deixam a prévia indisponível. Estabelece a
  primeira posição XY antes de reconhecer mergulhos, mantendo furos e slots visíveis.
  A estimativa usa a velocidade V efetiva inclusive nos deslocamentos, mas não
  considera aceleração, tempo de partida do motor ou posição inicial real.
- **Limitação de metadados:** o RML nativo não contém diâmetro, IDs de ferramenta
  ou unidades de origem embutidos. A geração mantém o plot com largura real,
  mas reabrir/aplicar o texto (inclusive o texto embutido em projetos) usa largura
  fina e não reconstrói a tabela de ferramentas de furação. Não há suplemento de
  metadados de ferramenta persistido nesta rodada. O percurso e suas unidades MM
  são reconstruídos; compensações de mesa e controle de RPM não foram portados.

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
`ControllerProgramCodecTest` cobre XYZ, ICP, unidades, velocidade/espera, ferramentas,
limites, cancelamento e progresso. `ProjectFileIOTest` reabre um job ICP embutido sem
o arquivo externo; os controles de Geometry e Drilling são exercitados na thread FX.
`HpglProgramCodecTest` cobre saída nativa, MM/IN, quantização, seleção/largura de canetas,
leitura linear absoluta/relativa, comandos recusados, cancelamento e progresso. O job
HPGL embutido é reaberto por `ProjectFileIOTest`; controles e retorno ao perfil de
fresagem são verificados por `GeometryCncToolPanelTest`. Teste manual no app e no
plotter permanecem pendentes.
`RolandProgramCodecTest` cobre RML nativo, velocidades, motor, XYZ modal, mergulhos,
slots/Multi-Depth, V-tools, restrições MM/ferramenta, comandos recusados e cancelamento.
`ProjectFileIOTest` reabre o texto RML embutido sem arquivo externo; controles de
Geometry/Drilling, desabilitação/preservação de rascunhos e rejeição de múltiplas
ferramentas são exercitados na thread FX. Validação manual e em máquina pendentes.

**Antes de enviar a uma máquina:** confira unidades, alturas Z, ordem de
ferramentas, comportamento de `M0` e suporte de `M6` no controlador. Faça uma
simulação e um teste a seco. O nome `grbl_11` reproduz o perfil do Python,
mas não garante que todo firmware GRBL aceite `M6`.
Confira também potência e pinagem do laser/FAN. Testes de software não
certificam o comportamento físico do controlador.
