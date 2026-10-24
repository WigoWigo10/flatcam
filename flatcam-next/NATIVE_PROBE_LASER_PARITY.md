# Roland, HPGL, sondagem e posições laser — 2026-10-24

Continuação das cinco etapas pedidas, sobre `ef1cefdb`, branch `flatcam-next`.
Etapa consolidada em commit, sem push. Fontes originais Python e
projetos privados preservados. Os relatórios são comparações de comandos,
não validação em uma máquina ou certificação de segurança.

## 1. Roland MDX-20

`tools/native_execution.py` lê diretamente o subconjunto RML usado pelos
geradores: inicialização, posicionamento absoluto, velocidade V em mm/s,
XYZ em unidades de 1/40 mm e motor binário. Não inventa RPM, troca de
ferramenta nem classificação G0/G1. Motor inicial desconhecido; XYZ comandado
zero é uma hipótese declarada, não homing. Comandos desconhecidos, reset no
meio, valores inválidos e movimento sem modo/velocidade são recusados.

20 receitas públicas MM: Geometry e Drilling, profundidades/passadas, avanço
XY/Z distintos, furos/rasgos, coordenadas negativas, limites de avanço,
arredondamento e estacionamento. IN e troca de várias ferramentas permanecem
fora deste corpus. Extensão dos artefatos: `.rml`.

Resultado em `target/series-final-roland_mdx_20-20261024/python/index.html`:
**0 execuções iguais, 7 rotas diferentes, 12 usinagens diferentes, 1 erro
Python, 0 erros de leitura FX**. O Python usa Feed XY no mergulho Geometry;
o caso Geometry Multi-Depth falha com `Decimal * float`. Drilling não cumpre
End XY no controle de estacionamento. Ordem dos furos e conversão de rasgos
continuam diferenças explícitas, não equivalência.

A auditoria FX acusa dois pedidos não exatamente representáveis: Feed 125
vira 126 mm/min; profundidade 0,6006 vira 0,6000 mm. Não aumentamos tolerâncias
para esconder essas diferenças. Não houve alteração do gerador Roland.

O [manual oficial MDX-20](https://downloadcenter.rolanddg.com/contents/manuals/MDX-20_USE_EN_R7.pdf)
informa resolução de software de 0,025 mm/passo, resolução mecânica de
0,00625 mm/passo e avanço de 0,1–15 mm/s. Coordenadas fracionárias emitidas no
texto não comprovam precisão física nem aceitação de subpassos pelo hardware.
O manual não foi tratado como referência completa de toda a gramática RML.

## 2. HPGL

Leitor independente mantém IN, SP, PU/PD e PA/PR, inclusive várias canetas,
sentido, ordem e repetição dos traços. Contexto MM/IN vem explicitamente do
caso-fonte; não é um comando de unidade nativo HPGL. Não inventa feed,
spindle, Z ou largura física da caneta. Extensão: `.plt`.

20 receitas MM/IN, com dois grupos/canetas, contornos e cortes internos,
limite de coordenadas e quantização. Resultado final em
`target/series-final-hpgl-20261024/python/index.html`: **20 concordâncias nos
traços ordenados por caneta, 20 diferenças na execução completa**, nenhum
erro FX/Python. PU/PA final do Python e PU/SP0 do FX são conservados como
diferença. Duas receitas quantizadas acusam perda frente ao pedido original
em ambos, mesmo quando os dois emitem o mesmo traço. O FX recusa overflow;
o legado pode limitar coordenadas. Sem mudança no gerador HPGL.

### Defeito encontrado no comparador novo

Na integração local, `machining_signature` ainda filtrava somente Z negativo
e devolvia assinatura vazia para HPGL. Dois arquivos com traços diferentes
podiam aparentar concordância. A correção usa os traços PU/PD, conservando
caneta, direção e multiplicidade. O relatório `series-hpgl-first-20261024`
foi **substituído** pela rodada final; não é evidência válida dessa igualdade.

Reprodução (Windows, Python 3.11.1/Shapely 1.8.5.post1, base `ef1cefdb`):

```powershell
$env:PYTHONPATH='tools'
target\oracle-py311\Scripts\python.exe -m unittest test_native_execution.NativeExecutionTest.test_hpgl_quantization_and_repeated_exposure_do_not_become_false_full_matches
```

Uma linha é comparada com a mesma linha percorrida três vezes. Esperado:
assinaturas diferentes; antes: ambas vazias/iguais; depois: 1 versus 3 traços.
Teste adicionado antes da correção; red/green e retirada temporária apenas da
correção red/green confirmados. Dez repetições do teste corrigido passaram.
Sem rede, relógio, aleatoriedade ou controlador. Evidências:
`target/series-native-tests-20261024.log`, `series-native-green-20261024.log`,
`series-hpgl-revert-red-20261024.log`, `series-hpgl-restored-green-20261024.log`
e `series-hpgl-repeat-20261024.log`. Defeito introduzido nesta integração
local desta etapa; não há commit anterior introdutor a atribuir ou bisect aplicável.

## 3. Mach3 com sondagem

`tools/probe_contract.py` verifica um **contrato estático**: dois G31 por
ferramenta, limite/avanços rápido e lento, parada comandada, confirmação antes
de G92, altura de contato, restauração de unidades/G90 após M6 e confirmação
de retirada da sonda antes do spindle. Estado do spindle fica desconhecido
após a macro M6 até nova ordem explícita; o avanço anterior também não é
presumido preservado. Coordenadas de contato, movimentos
internos da macro e transformações físicas G92 não são inventados.

16 receitas Geometry/Drilling em MM/IN: uma/duas ferramentas, placa de contato
e XY de troca. Resultado em
`target/series-final-toolchange_probe_mach3-20261024/python-modal-final/index.html`:
**16 PROBE_CONTRACT_ONLY**, nenhum erro de leitura nem problema estático FX.
O legado não fornece as mesmas confirmações/restaurações e usa G92 Z0 mesmo
no controle de placa não zero. Não há cálculo de cortes nem coordenada final
para este perfil; strict reprova mesmo se todos os comandos coincidirem.
Não se compara contato desconhecido como se G31 fosse um G1 até o limite.

Referência: [Mach3Mill, manual oficial](https://www.machsupport.com/wp-content/uploads/2013/02/Mach3Mill_1.84.pdf).
Faltam validação física, contrato da macro instalada e auditoria geométrica
dos movimentos reais após estabelecer a referência. XY/altura da troca não
receberam uma auditoria física independente nesta rodada.

Um segundo teste negativo encontrou no leitor local avanço herdado de antes
de M6: inserir `G1 F50` antes da macro e retirar F do primeiro G31 ainda
passava. Esperado: avanço desconhecido e problema de contrato. O leitor agora
invalida o avanço após M6. `ProbeContractTest.test_feed_before_an_opaque_macro_does_not_establish_the_probe_feed`
reproduz a falha; red/green/revert-red/restored-green e dez repetições
confirmados, sem tempo/rede/hardware. Logs `target/series-probe-modal-*-20261024.log`.
Também é um defeito introduzido nesta integração local, sem commit anterior para bisect,
não um novo crash do produto. Os 16 contratos foram comparados novamente
após o ajuste; permanecem sem problemas estáticos FX e sem paridade física.

## 4. Posições finais laser

Os quatro perfis laser aceitam **End Z e End X,Y comuns** no backend e no
painel Geometry → CNC. Potência é desligada antes do estacionamento. O
gerador sobe para `max(Focus/End Z, End Z solicitado)`, faz o XY nessa altura
e só depois desce se necessário. Marlin mantém M400 após todo o estacionamento.
Deslocamento XY final entra na geometria de viagem da prévia; o resumo CNC
mostra os valores solicitados e a altura do deslocamento.

Start Z, troca mecânica, exclusões, roteamento avançado e parâmetros
individuais permanecem bloqueados. Ajustes incompatíveis preservados não
são descartados silenciosamente. Parâmetros por grupo exigem definir potência,
foco e transições entre grupos, além da interface; não basta remover a guarda
de fresagem. Não foi alegada equivalência dessa parte ainda ausente.

Corpus ampliado de 24 para **28 receitas por perfil**, incluindo End Z baixo/
alto e End XY em MM/IN: GRBL laser **0/28/0/0/0**, Z_laser e os dois Marlin
**0/0/28/0/0** (execução/rota/usinagem/erro Python/erro FX). Zero problemas
auditados FX nos quatro perfis em modo shared. Foco/comandos do legado
continuam diferentes. Relatórios `target/series-final-*-laser*-20261024/python`.

`LaserPositionsTest`, `CncRoutingFieldsTest` e auditor independente cobrem
sequência desligar/subir/XY/descer, unidades, restrições e estacionamento baixo
incorreto. O teste de preservação de opções sem suporte agora usa HPGL:
exigir End XY desabilitado no laser se tornou uma expectativa obsoleta.

## 5. Regressão dos fluxos e aplicação original

`compare-main-flows.ps1 -LegacyCompatible -Strict` executado em corpus público:
**27 MATCH_SAMPLED + 1 DIFFERENT em MM e em IN**, nenhum ORACLE_ERROR nem
GCODE_DIFFERENT. Única diferença: `ncc-seed` estável deliberadamente diferente;
`ncc-seed-python` continua concordante. Região a limpar e G-code passam no
caso estável, mas o trajeto não coincide (distância amostrada 0,1492 mm no
controle MM, tolerância 0,003 mm). Strict permanece reprovado, sem exceção
artificial. Relatórios `target/series-main-flows-20261024/{source,synthetic-in}`.

Aplicação Python real, fora da tela e com configurações temporárias:
Roland e HPGL geraram quatro controles cada; Mach3 gerou três e falhou em
Dwell zero (`NoneType + str` no legado). Relatórios
`target/series-{roland,hpgl,probe}-live-20261024.json` comprovam fontes intactas.
O controle Mach3 sem troca não contém ciclo de sonda e não o valida. Os
ensaios reais verificam geração/erros da aplicação, não simulação do hardware.

## Repetir

```powershell
.\compare-cnc-parameters.ps1 -Profile ROLAND_MDX_20
.\compare-cnc-parameters.ps1 -Profile HPGL
.\compare-cnc-parameters.ps1 -Profile TOOLCHANGE_PROBE_MACH3
# Também: GRBL_LASER, Z_LASER, MARLIN_LASER_FAN_PIN, MARLIN_LASER_SPINDLE_PIN
.\compare-main-flows.ps1 -LegacyCompatible -Strict
.\mvnw.cmd verify
target\oracle-py311\Scripts\python.exe -m unittest discover -s tools -p test_*.py
```

Validação final: **1854 aprovações Java, 18 skips opcionais**, zero falhas/
erros (1872 registros, incluindo repetições). Contagem apenas dos XML desta
execução; três registros antigos de `LauncherConfigurationTest` não entraram.
**150 testes auxiliares Python aprovados**. Logs
`target/series-full-final-20261024.log` e `series-python-final-20261024.log`.
A primeira rodada integral reprovou a expectativa antiga de End XY bloqueado
no laser; foi preservada em `series-full-verify-20261024.log`. O teste ajustado
e os novos testes de posições passaram antes da rodada final integral.
`git diff --check` sem problemas. Os sete perfis foram também comparados com
`--strict`: todos saíram com código 1 pelas diferenças/limites descritos,
sem promover concordância parcial a execução equivalente.

Não instalar dependências nem enfraquecer strict para produzir números de
paridade. Próximas lacunas: ampliar corpus nativo e coordenadas subpasso,
reduzir diferenças de rotas justificadas por ferramenta e definir parâmetros
laser por grupo; sonda depende também da macro/controlador real.
