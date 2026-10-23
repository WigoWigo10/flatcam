# ISEL_ICP_CNC — contrato nativo e comparação

Revisão 2026-10-23, base `09486037` (line_xyz commitado). Esta etapa ICP ainda
sem commit/push. Windows, Java 25.0.4.1; oráculo Python 3.11.1,
Shapely 1.8.5.post1 / GEOS 3.10.3 / OR-Tools 9.10.4067.

## O que foi corrigido

O FX recebia Feed rapids mas não o escrevia em ICP. Agora emite **FASTVEL**
antes do primeiro movimento: 900 mm/min → `FASTVEL 15000`, em µm/s.
Zero seleciona o padrão de 1500 mm/min, não uma velocidade nula. Valores
negativos, não finitos, abaixo da resolução ou fora do inteiro aceito são
recusados, sem clamp. IN continua recusado, inclusive na entrada `finish`.

A conversão final consulta cancelamento durante as linhas, sem construir a
lista inteira antes. A prévia nativa reconhece FASTVEL sem sobrescrever o
avanço independente de MOVEABS e recusa um arquivo sem PROGEND. A prévia XY
não calcula o tempo dos rápidos. A revisão CNC avisa sobre resolução inteira,
troca automática GETTOOL e macros não simuladas.

Referências primárias: [manual ISEL RemoteWin, seções 3.1, 3.5–3.8 (cópia do
manual do fabricante)](https://manualzilla.com/doc/6748210/remote---bedienungsanleitung)
e [ISEL DIN/ISO, fevereiro de 2025](https://www.isel.com/fileadmin/user_upload/Software-Download/20250227_G-and-M-codes_en.pdf).
O primeiro descreve coordenadas em µm, VEL/FASTVEL em µm/s e velocidades de
corte/rápido separadas; o segundo associa o rápido a FastVel e WPCLEAR à
desativação da referência de peça. Não extrapolar esses documentos para
suporte universal de controladores, macros, limites ou segurança física.

## Reprodução e prova

```powershell
.\mvnw.cmd -pl flatcam-cam -am -Dtest=IcpRegressionTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Três regressões, dez repetições cada: FASTVEL ausente; cancelamento ignorado
durante conversão; prévia aceitando programa truncado. Teste escrito e staged
antes da correção, 30 falhas antes e 30 aprovações depois. Remoção temporária
das correções fez as 30 falharem novamente; restauração passou. Dados fixos,
sem relógio/rede/hardware; token cancela na quinta consulta. Sem baseline
conhecido como bom: sem bisect, commit introdutor não estabelecido.

Logs `target/icp-red-20261023.log`, `icp-green-20261023.log`,
`icp-revert-red-20261023.log`, `icp-restored-green-20261023.log`.
`IcpContractTest` acrescenta Geometry/Drilling, duas ferramentas e IDs esparsos,
peck/rasgos, avanços distintos Z/XY, Start/Change Z diferentes, posição de
troca, restauração de Z e estacionamento XY antes de End Z. Ver também
`ControllerProgramCodecTest`, `CncInitialToolChangeTest` e `CncPreflightTest`.
Rodada direcionada: `target/icp-contract-targeted-20261023.log`.

## Leitor comparativo independente

`tools/icp_execution.py` lê o arquivo nativo, não chama o normalizador Java
nem insere comandos para obter concordância. Aceita somente IMF_PBL,
FASTABS/MOVEABS, VEL/FASTVEL, GETTOOL, WAIT, SPINDLE CW/CCW/OFF e PROGEND.
Valida inteiros e sintaxe; comandos desconhecidos, referências novas, arquivos
truncados e comandos após o fim falham. Velocidade rápida sem FASTVEL e RPM
sem palavra explícita permanecem desconhecidas, não viram números inventados.

GETTOOL é uma troca automática opaca, mesmo sem pausa manual. Registram-se
posição comandada e parada explícita de spindle antes da troca; não se
simula movimento interno. XYZ comandado inicial zero é hipótese declarada
do comparador, não leitura da máquina. Faixa inteira aceita é limite do
leitor/encoder, não garantia do limite físico do controlador.

Comando público, na raiz `flatcam-next`:

```powershell
.\compare-cnc-parameters.ps1 -Profile ISEL_ICP_CNC -Strict
```

Exporta 52 receitas MM; 52 IN são excluídas explicitamente, não contadas como
equivalências. Artefatos nativos usam `.imf`. Fonte Python é verificada por
SHA-256 antes/depois, sem alterar preprocessor/geradores/roteamento.

## Resultado e limites — strict continua reprovado

Relatório final `target/icp-public-final-20261023/index.html`:
**0 MATCH_EXECUTION / 0 ROUTING_DIFFERENT / 0 MACHINING_DIFFERENT /
52 ORACLE_ERROR / 0 FX_ERROR**.

- O leitor aceitou os 52 programas FX completos. FASTVEL/terminação/trocas
  estão explícitos; nenhum avanço observado diferiu do truncamento declarado.
- Em **48 programas**, a auditoria contra os números solicitados acusa
  diferença de avanço: por exemplo 100 mm/min → VEL 1666 → 99,96 mm/min.
  O diagnóstico `fxIcpQuantizationReview` separa esse efeito de um avanço
  nativo inesperado, mas **não limpa avisos nem aumenta a tolerância strict**.
  Resolução do formato não é precisão física. Coordenadas/esperas também
  usam inteiros truncados, não precisão decimal ilimitada.
- 51 programas Python chegam a **WPCLEAR**. O leitor não conhece o vínculo
  entre origem de peça e máquina para resolver o retorno posterior a Z0/X0/Y0.
  São limitações do modelo comparativo, **não 51 crashes do Python** e não
  prova de que WPCLEAR seja errado por si só. O FX preserva a origem e o
  estacionamento configurado; essa diferença não é normalizada.
- Um programa Python falha realmente em Dwell zero (`None` concatenado).
- Antes de WPCLEAR, o prefixo tem evidências por ferramenta: 43 comparações
  de cortes ordenados iguais, 10 em outra ordem e oito com cortes/parâmetros
  diferentes (61 ferramentas em 51 programas). É diagnóstico parcial,
  **nunca equivalência de execução completa**. Diferenças incluem sentido de
  passes, Point retraído, rasgos fresados contra cadeias de furos e spindle
  desligado/seleção automática. Mantidas explícitas, não copiadas para subir placar.

O aplicativo Python real também foi executado offscreen, com configurações
temporárias e Geometry pública, pela entrada real `generatecncjob` usada pelo
Tcl. Três casos geraram, incluindo troca (11,17), sem troca e posição corrente;
dwell zero falhou. Todos os três programas incluem WPCLEAR/retorno zero.
Nada foi aberto/salvo sobre projeto privado; SHA-256 dos fontes preservado.
Relatório `target/icp-live-final-20261023.json`, programas `.imf` no diretório
homônimo. Não é teste de cliques GUI nem validação do controlador.

Para repetir (pasta nova):

```powershell
& target\oracle-py311\Scripts\python.exe tools\open_in_python_app.py --legacy-root .. `
  --probe-cnc-profile ISEL_ICP_CNC --probe-output target\icp-live-new --report target\icp-live-new.json
```

Controle GRBL inalterado: 49/19/34/2/0 em
`target/icp-grbl-control-20261023/index.html`. Auxiliares desta rodada:
37 do comparador, cinco do leitor ICP, quatro do diagnóstico e nove FlatPrj
(55 passaram). Nenhuma aprovação física ou equivalência global reivindicada.

Verify integral aprovado em `target/icp-full-20261023.log`: 1822 registros
Java (incluindo as repetições de regressão), 1804 passaram, 18 skips opcionais,
zero falhas/erros. Esta rodada não reproduziu a falha intermitente anterior
de limpeza temporária; sua causa continua não estabelecida.

Próximos limites: modelar explicitamente os referenciais para analisar o fim
legado WPCLEAR, sem presumir origem máquina=origem peça; ensaios GUI/máquina;
e ampliar os perfis `TOOLCHANGE_MANUAL`/`TOOLCHANGE_CUSTOM` no comparador.
