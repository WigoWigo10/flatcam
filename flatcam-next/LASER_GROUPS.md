# Laser: parâmetros individuais por grupo — 2026-10-24

Implementação sobre `c8ae73cf`, consolidada em commit na branch `flatcam-next`,
sem push. Fontes Python e projetos privados não foram alterados.

## Comportamento

- Os quatro perfis laser aceitam avanço XY e potência por grupo de Geometry.
- `Z_laser` e os dois Marlin laser aceitam foco Z por grupo, usando o campo
  existente `safeZ`/Focus Z. Continuam exigindo valores positivos e finitos.
- `GRBL_laser` simples não posiciona foco antes dos trajetos. Valores
  diferentes de Focus/End Z entre grupos são recusados; use `Z_laser` para
  posicionar foco. Não há troca mecânica, Tn ou M6 entre grupos.
- Feed rapids, perfil e posições finais permanecem comuns. FAN aceita 1–255;
  os demais perfis usam S bruto, sem transformar o valor em RPM físico.
- Entre grupos nos perfis com Z: potência desligada, subida ao maior foco
  dos grupos com trajetos, deslocamento XY, foco do próximo grupo e partida
  na potência desse grupo. Entre caminhos do mesmo grupo mantém o foco,
  com potência desligada durante o posicionamento.
- Sem End Z explícito, a altura final é a do último grupo com trajetos.
  Para End XY, o gerador usa a maior altura entre os focos ativos e End Z,
  desloca em XY e só depois baixa se necessário. Marlin mantém M400 final.
  Grupos vazios não aumentam a altura de transição nem definem o Z final.

O plano de transição é uma política comandada, não detecção de obstáculos,
homing, calibração óptica ou prova de folga física. Multi-Depth, troca
mecânica, sonda, Dwell, Extra Cut, compensação, exclusões e roteamento
avançado continuam recusados no backend laser.

## Interface, projeto e Terminal

Selecionar uma linha restaura seus próprios avanço/potência/foco. Erros em
linhas não selecionadas bloqueiam a geração e identificam o grupo. “Aplicar
parâmetros a todos os grupos” copia explicitamente a linha atual. Os campos
de fresagem continuam desabilitados e preservados para voltar à fresagem.
O resumo CNC usa “Grupo”, mostra os valores efetivos, a altura de transição
e o estacionamento final; não apresenta grupos como trocas físicas.

`GeometryCncSettings.parametersByTool` já comportava esses valores; não houve
nova versão de formato. Testado salvar/reabrir `.fcnproj` e `.FlatPrj`, ambos
simples/comprimidos, e regenerar o mesmo G-code. O ensaio `.FlatPrj` também
remove os metadados privados FX: avanço/potência/foco seguem nas chaves Python
dos grupos e o End Z automático exportado corresponde ao último grupo ativo.

O escritor não exige ponta V mecânica para um grupo laser que carregue esse
tipo de ferramenta. Na importação Python, defaults de posição de troca
mecânica são inativos quando o perfil é laser e `toolchange=false`; não viram
posições solicitadas. Com troca ativada continuam preservados e sujeitos à
recusa do gerador. O payload legado original não é editado.

`join_geometry` também conserva os parâmetros dos grupos laser. Divergências
em opções comuns/perfis continuam recusadas; `GRBL_laser` mantém a guarda de
foco igual. Não houve mudança da política de troca dos outros perfis.

## Comparação com o Python

O leitor independente audita exposições por caminhos, avanços, potência e
foco solicitados, com multiplicidade. Não deduz seleção de ferramenta ou
identidade física de comentários: o programa laser não contém Tn/M6. Em
trajetos coincidentes, a correspondência é por exposição/parâmetros, não uma
prova de qual grupo físico a produziu. Ordem completa de eventos/exposições
continua comparada; união geométrica não transforma repetição em igualdade.

Corpus público ampliado de 28 para **32 receitas por perfil** em MM/IN.
Os quatro casos novos por perfil usam dois grupos, avanço/potência distintos,
focos distintos quando suportados e estacionamento baixo opcional.

| Perfil | Execução igual | Rota diferente | Usinagem diferente | Erro Python | Erro FX |
| --- | ---: | ---: | ---: | ---: | ---: |
| GRBL_laser | 0 | 32 | 0 | 0 | 0 |
| Z_laser | 0 | 0 | 32 | 0 | 0 |
| Marlin laser FAN | 0 | 0 | 32 | 0 | 0 |
| Marlin laser spindle | 0 | 0 | 32 | 0 | 0 |

**Nenhum problema nos parâmetros solicitados ao FX nos 128 controles.**
Strict segue reprovado: o legado não executa as mesmas transições/focos e
estacionamentos; isso não foi normalizado nem copiado para inflar paridade.
A altura comum entre grupos é uma política explícita do FX, não uma promessa
de que o controlador Python tem a mesma política ou uma certificação física.

Relatórios finais: `target/laser-groups-{perfil}-20261024/python-final/index.html`.
Controle de fresagem GRBL: **49/19/34/2/0** em 104 receitas, inalterado,
`target/laser-groups-grbl_11_no_m6-20261024/python/index.html`.

## Verificação

`LaserGroupsTest`: dois grupos, MM/IN, quatro perfis, sequência de potência/
altura/XY, feeds/potências diferentes, ausência de troca mecânica, geometria
de viagem/prévia, grupo vazio e restrições. `LaserGroupsPersistenceTest`:
serialização/regeneração nos dois formatos e sem metadados privados.
`LaserGroupsPanelTest`: seleção/restauração, campos inválidos escondidos,
FAN fora de faixa, aplicar a todos e junção de objetos. `CncPreflightTest`:
valores efetivos e estacionamento independente da linha selecionada.
Auditor Python tem controles negativos de avanço, potência e XY baixo entre
grupos. **151 testes auxiliares Python aprovados** em
`target/laser-groups-python-final-20261024.log`.

O primeiro verify integral encontrou uma expectativa antiga: alterar a
potência de uma linha antes bastava para gerar todos os grupos no laser.
O teste agora usa “Aplicar a todos” explicitamente. Os novos testes exigem
que potência inválida em outra linha impeça a geração. Log inicial preservado
em `target/laser-groups-full-20261024.log`.

Verify integral final aprovado: **1861 aprovações Java e 18 skips opcionais**,
zero falhas/erros, 1879 registros atuais incluindo repetições. XML anteriores
ao início da execução não entraram na contagem. Evidência:
`target/laser-groups-full-final-20261024.log`. `git diff --check` sem problemas.

Não houve validação manual da aparência em uma janela nem ensaio com máquina.
Paridade completa de execução continua pendente; os resultados acima são de
corpus público e não uma porcentagem global de paridade.
