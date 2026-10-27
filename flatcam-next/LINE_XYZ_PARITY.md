# line_xyz: coordenadas estabelecidas e falhas do legado

Esclarecimento posterior do ensaio (troca Manual/Custom, 2026-10-23): o campo
XY vazio agora é passado como `""`, não Python None. O erro de tipo histórico
descrito abaixo para posição corrente depende daquela entrada None e não
prova que o campo realmente vazio falhe da mesma forma. Não afeta a evidência
independente de `end_xy` ausente/Y=X. Ver [TOOLCHANGE_PARITY.md](TOOLCHANGE_PARITY.md).

Revisão de 2026-10-23, base `a1fa7204`, Windows / Java 25.0.4.1 e Python
3.11.1, Shapely 1.8.5.post1 / GEOS 3.10.3. Etapa anterior ISEL commitada em
`a1fa7204`; alterações desta revisão ainda sem commit/push.

## Correções no FX

O conversor inicializava X/Y/Z em zero. Assim, `G00 Z15` virava
`G00 X0 Y0 Z15`: partindo de uma posição XY diferente, acrescentava um
deslocamento horizontal durante a subida. Também reutilizava X/Y anteriores
após M6, apesar de não conhecer os movimentos internos da macro.

Agora o conversor de programas absolutos gerados pelo FX:

- Não inventa coordenadas: a subida inicial permanece vertical.
- Explicita os eixos conforme são estabelecidos pelo próprio programa.
- Invalida as coordenadas lembradas após M6; a restauração de Z continua
  vertical, antes do próximo posicionamento XY explícito.
- Preserva linhas de configuração de avanço sem transformá-las em movimentos.
- Verifica cancelamento durante a conversão, sem materializar uma lista de
  todas as linhas antes do processamento.

Não é um conversor geral para G-code externo/incremental. A descrição do perfil
e a revisão CNC avisam que o controlador precisa aceitar o preâmbulo vertical.
Não é possível garantir XYZ desde a primeira subida sem conhecer a posição
física inicial; supor uma origem seria incorreto. M6 continua opaco, sem
certificação de máquina ou simulação dos movimentos internos.

### Reprodução e prova da correção

Comando mínimo, na raiz `flatcam-next`:

```powershell
.\mvnw.cmd -pl flatcam-cam -am -Dtest=LineXyzRegressionTest -Dsurefire.failIfNoSpecifiedTests=false test
```

`LineXyzRegressionTest` tem três regressões repetidas dez vezes cada. Foram
escritas e executadas antes da correção (30 falhas), staged antes do fix,
passaram depois; a remoção temporária das correções fez as 30 falharem
novamente, e a restauração voltou a passar. Entradas fixas, sem rede, relógio,
hardware ou dependência de agendamento: o token cancela na quinta consulta.
Sem baseline conhecido como bom, não foi feito bisect nem atribuído um commit
introdutor por hipótese.

Evidências:

- `target/line-xyz-red-repeat-20261023.log`
- `target/line-xyz-revert-red-20261023.log`
- `target/line-xyz-restored-green-20261023.log`
- `target/line-xyz-contract-final-20261023.log`

`LineXyzContractTest` também cobre Geometry e Drilling em MM/IN: duas
ferramentas, X/Y distintos, peck, slots, alturas de início/troca diferentes,
dwell, restauração vertical e estacionamento XY antes de descer ao End Z.
`CncPreflightTest` protege o aviso de preâmbulo/M6.

## Python real: não é apenas limitação do comparador

O aplicativo original foi iniciado offscreen, com preferências temporárias,
sem abrir projeto privado. Uma Geometry pública (segmento de (3,7) a (8,7))
foi criada pelo `AppObject.new_object`; `GeometryObject.generatecncjob` foi
executado com e sem troca, X/Y de troca distintos, posição de troca corrente
e dwell zero. É a entrada real usada pelo comando Tcl, não um clique simulado
no painel. Os caminhos GUI foram inspecionados no código, não certificados
por esse ensaio.

Quatro casos por unidade, MM e IN:

- Todos registram `KeyError: 'end_xy'` no cabeçalho. O gerador oferece
  `xy_end`, mas o perfil consulta `end_xy`. Não foi acrescentado alias para
  fabricar equivalência.
- Com troca em (11,17), a saída original contém `X11 Y11`: confirma o
  erro Y=X das strings de troca. Também usa Start Z no lugar de Change Z
  para T1 e permite rápido simultâneo XYZ.
- Com posição de troca corrente, esta entrada real também registra erro
  de tipo ao formatar coordenadas não estabelecidas. O detalhe difere do
  harness direto: não generalizar a mesma exceção para todos os caminhos.
- Dwell zero impede a criação do CNC Job, pois o perfil devolve `None`.
- Os outros três casos criam objetos e textos parciais apesar dos erros.
  Existência de objeto/arquivo não é critério suficiente para aprovação.

Controle `default`: dois casos geram sem erros, os casos posição de troca
corrente/dwell zero também falham; não são defeitos exclusivos de line_xyz.
O comando retorna 1 quando há erro, mesmo se o objeto/texto foi criado.
SHA-256 antes/depois confirma que os fontes Python observados não mudaram.

Para repetir (diretórios de saída precisam ser novos):

```powershell
& target\oracle-py311\Scripts\python.exe tools\open_in_python_app.py --legacy-root .. `
  --probe-cnc-profile line_xyz --probe-output target\xyz-live-new --report target\xyz-live-new.json
```

Acrescente `--initial-units IN` para IN; `--probe-cnc-profile default` para o
controle. Este modo não aceita abrir/regravar projeto. Os programas produzidos
são artefatos diagnósticos, não arquivos aprovados para execução em máquina.

Relatórios: `target/line-xyz-live-20261023.json`,
`target/line-xyz-live-in-20261023.json`,
`target/line-xyz-default-live-20261023.json` e diretórios homônimos.

## Comparação pública e limites

`compare-cnc-parameters.ps1 -Profile LINE_XYZ -Strict` continua
**0 MATCH_EXECUTION / 0 ROUTING_DIFFERENT / 0 MACHINING_DIFFERENT /
104 ORACLE_ERROR / 0 FX_ERROR**. Auditoria FX sem problemas de parâmetros.
Não há equivalência comprovada com um Python corrigido; os erros originais
continuam erros e o strict sai com código 1.

O setup de Drilling no harness agora inicializa `oldx/oldy` como faz
`ToolDrilling` antes do gerador por ferramenta. Não altera os preprocessadores
nem injeta `end_xy`/`x`/`y` para ocultar suas falhas. O controle GRBL manteve
49/19/34/2/0 e zero erros FX, sem mudança de tolerância ou critério.

Relatórios: `target/line-xyz-contract-public-20261023/python/index.html` e
`target/line-xyz-grbl-control-20261023/python/index.html`.
36 testes auxiliares do comparador, três novos testes do diagnóstico e nove
regressões da validação FlatPrj passaram (48 auxiliares nesta rodada).

Primeiro verify (`target/line-xyz-full-20261023.log`) encontrou um erro de
limpeza JUnit, não uma falha de asserção: `DirectoryNotEmptyException` ao apagar
a raiz temporária após `TclLiveHostTest.savingAnEmptyProjectAndContinuingTheScriptWorks`.
Depois da execução o diretório já não existia; o teste isolado passou em
`target/line-xyz-tcl-cleanup-check-20261023.log`. A causa externa não foi
confirmada; não se atribui a antivírus nem se ampliaram retries para ocultar
uma possível corrida. Esse incidente permanece registrado para investigação.

Segundo verify integral aprovado: `target/line-xyz-full-final-20261023.log`.
1788 registros Java (incluindo as repetições explícitas da regressão):
1770 passaram, 18 skips opcionais, zero falhas/erros. A aprovação posterior
não apaga a ocorrência intermitente da primeira rodada.

Pendências: execução em controlador compatível; ensaio GUI de Drilling/multigeo;
outros projetos e modos de macro; equivalência contra legado corrigido, se
isso for autorizado como uma trilha separada e nunca promovida ao original.
Próximo perfil a ampliar no comparador: ISEL_ICP_CNC (dialeto nativo, unidades,
velocidades, troca e finalização), sem normalizar comandos não modelados.
