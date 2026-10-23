# Troca Manual e Custom — revisão de contrato

2026-10-23, base `4552cf00` (ICP commitado), branch `flatcam-next`.
Novas alterações ainda sem commit/push. Windows / Java 25.0.4.1; Python
3.11.1, Shapely 1.8.5.post1 / GEOS 3.10.3 / OR-Tools 9.10.4067.

## Correções FX verificadas

Na Geometry, o ajuste manual `G01 Z0` recebia o **Feed XY comum** em vez do
Feed Z da ferramenta ativa. Exemplo: XY=300, Z=40 emitia `F300`; agora emite
`F40`. Overrides por ferramenta também passam a valer (por exemplo F25/F60).
Drilling já usava seu avanço Z e foi preservado.

O bloco manual agora reemite G90 depois das duas primeiras pausas, antes do
ajuste em Z0 e do retorno à altura de troca. A terceira pausa já é seguida
pela restauração G90/Z do gerador. Isso não simula jog, mudança de unidades,
origem física ou contato; não é sondagem nem garantia de segurança.

A revisão CNC mostra o avanço de ajuste por ferramenta com acesso ao campo
Feed Z correspondente, explica as três pausas e distingue Custom: Tn/M6,
sem M0 extra, posição comandada antes da macro e restauração de Z depois.
Macros internas, seleção física da ferramenta e alterações de referência
continuam fora da prévia.

### Reprodução mínima e determinismo

```powershell
.\mvnw.cmd -pl flatcam-cam -am -Dtest=ManualToolChangeRegressionTest -Dsurefire.failIfNoSpecifiedTests=false test
```

`ManualToolChangeRegressionTest`: duas regressões repetidas dez vezes cada,
incluindo MM/IN e overrides. Produção no baseline: 20 falhas de asserção;
correções aplicadas: aprovação; remoção temporária somente das correções:
20 falhas; restauração: aprovação. Entradas fixas, sem rede, relógio ou
hardware. Sem baseline conhecido bom: não foi feito bisect; commit introdutor
não estabelecido. Teste staged antes da correção comportamental definitiva.

Primeira tentativa do teste teve assinatura de overload incorreta (VTipSettings
versus parâmetros por ferramenta); esse erro de compilação **não foi contado
como reprodução**. A assinatura foi corrigida e a produção restaurada ao
baseline antes do red comportamental. Logs preservados:

- `target/toolchange-red-behavior-20261023.log`
- `target/toolchange-green-final-20261023.log`
- `target/toolchange-revert-red-20261023.log`
- `target/toolchange-restored-green-20261023.log`
- `target/toolchange-contract-targeted-final-20261023.log`

`ToolChangeContractTest`: ambos os perfis, Geometry e Drilling, MM/IN,
ferramentas esparsas 2/7, peck/rasgos, três pausas versus M6, Start/Change Z
distintos, XY de troca, ajuste/retração e estacionamento XY antes de End Z.
Desativar a troca não deixa M0/M6 ou ajuste em Z0. `CncPreflightTest` protege
os valores por ferramenta, links de edição e avisos.

## Comparador ampliado, sem alterar o Python

```powershell
.\compare-cnc-parameters.ps1 -Profile TOOLCHANGE_MANUAL -Strict
.\compare-cnc-parameters.ps1 -Profile TOOLCHANGE_CUSTOM -Strict
```

104 receitas públicas MM/IN por perfil. Classes originais `Toolchange_Manual`
e `Toolchange_Custom`, geradores e roteamento preservados, SHA-256 verificado
antes/depois. Não são introduzidos Tn, pausas ou posições fictícias no legado.

Manual: o auditor distingue as três fases (Change Z / Z0 / Change Z), avanço
do ajuste, spindle parado, XY e retorno vertical; não exige erroneamente que
as três pausas estejam todas em Change Z. Custom exige seleção explícita e
audita M6 sem inventar uma pausa M0. Comandos/erros não modelados continuam
impedindo aprovação; tolerâncias não foram ampliadas.

Resultados finais:

- **Manual:** 20 MATCH_EXECUTION, 48 ROUTING_DIFFERENT, 34 MACHINING_DIFFERENT,
  dois ORACLE_ERROR, zero FX_ERROR. Auditoria de parâmetros FX sem problemas.
  No Python, 50 avisos T1 e 16 T2 de avanço de ajuste diferente do Feed Z,
  além de Start Z substituindo Change Z em alguns casos, pausas omitidas
  no controle RPM zero e os casos Over sobrepostos já conhecidos. Diferenças
  de passes/sentido/rasgos também permanecem. Dois erros reais: Dwell zero.
- **Custom:** 104 ORACLE_ERROR, zero FX_ERROR, auditoria FX sem problemas.
  **102 são seleção de ferramenta não modelada, não crashes do Python**;
  dois são a falha real Dwell zero. A macro sem Tn pode escolher ferramenta
  por configuração externa: o leitor não tem essa informação. Não atribuir
  automaticamente os cortes à ferramenta solicitada nem declarar erro físico
  só porque a identidade não foi resolvida.

A rodada intermediária Custom classificava T0 contra a ferramenta solicitada
como diferença de usinagem; isso foi substituído pelo limite explícito
`UNMODELED_TOOL_SELECTION_NOT_A_PYTHON_CRASH`. Arquivos brutos são preservados;
a correção do ensaio não promove nenhuma receita a equivalência.

Relatórios finais: `target/toolchange-manual-final-20261023/index.html` e
`target/toolchange-custom-final-20261023/index.html`. Custom sem XY configurado
pode omitir M6 no fonte original; com XY escreve M6 mas não Tn. O FX mantém
Tn/M6 quando a troca está ativada, sem copiar a dependência de seleção implícita.
O controle GRBL segue 49/19/34/2/0 em
`target/toolchange-grbl-control-20261023/index.html`.

## Aplicativo Python real e correção do ensaio de campo vazio

Rodadas offscreen com configurações temporárias, sem projeto privado, usando
Geometry pública e `generatecncjob` (entrada real usada pelo Tcl), não cliques
GUI. Ambos os perfis geram três casos e falham no quarto, Dwell zero. Manual
mostra o ajuste herdando o avanço XY; Custom não tem Tn, escreve M6 com XY
explícito e o omite quando o campo está vazio. Fontes originais preservados.

O ensaio anterior usava Python `None` para a posição corrente: o legado pode
transformá-lo em texto `"None"`, causando erro de tipo. Agora o ensaio passa
o campo vazio real `""`. Não foi corrigido o aplicativo Python nem adicionado
alias ao preprocessor. As falhas anteriores com None não provam falha do
campo realmente vazio. Esse esclarecimento também vale para os controles
históricos default/line_xyz; o erro de cabeçalho `end_xy` continua distinto.

Relatórios: `target/toolchange-manual-blank-live-20261023.json` e
`target/toolchange-custom-blank-live-20261023.json`, saídas nos diretórios
homônimos. Os relatórios anteriores `*-live-20261023.json` ficam como evidência
da entrada None, não a representação final do campo vazio.

Repetição (escolha pasta nova; troque o perfil para Custom):

```powershell
& target\oracle-py311\Scripts\python.exe tools\open_in_python_app.py --legacy-root .. `
  --probe-cnc-profile Toolchange_Manual --probe-output target\manual-live-new --report target\manual-live-new.json
```

Auxiliares desta rodada: 37 do comparador, cinco ICP, cinco do diagnóstico,
nove FlatPrj e seis dos perfis de troca (62 passaram). Strict continua
reprovado nos dois perfis. Sem certificação GUI, contato, controlador ou máquina.

Verify integral aprovado: `target/toolchange-full-20261023.log`, 1847 registros
Java (incluindo repetições explícitas de regressão), 1829 passaram, 18 skips
opcionais, zero falhas/erros. A falha intermitente de limpeza temporária de
rodadas anteriores não se repetiu; causa ainda não confirmada.

Pendências: configuração real da macro Custom para resolver Tn implícito;
interação manual/painéis novos; comparações dos demais perfis de controlador
(por exemplo Repetier/Berta), sem confundir comportamento configurável com
bugs universais. WPCLEAR/frames ICP continuam em trilha separada.
