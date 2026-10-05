# Terminal Tcl — FlatCAM FX

Atualizado em 2026-10-05, sobre `94ad07b1`, branch `flatcam-next`.

Abra **Ferramentas > Linha de Comando Tcl**. `help` lista comandos;
`help open_project`, `help offset`, `help scale`, `help mirror` e `help skew`
mostram sintaxe e referências. O Terminal executa um script por vez no worker,
com Cancelar/ESC. Ele não é um interpretador Tcl completo: os limites de
`TclInterpreter` continuam válidos.

## Abertura de projeto

```tcl
open_project {C:/pasta com espacos/projeto.fcnproj}
get_names
```

Aceita `.fcnproj` e `.FlatPrj`, inclusive os formatos comprimidos já suportados
pelo menu Arquivo. Use `/` nos caminhos Windows e chaves para nomes com espaços,
evitando substituição/escapes do Tcl. Não há seletor de arquivo nesse comando.

A leitura/decodificação e a análise CNC usam o mesmo carregador do menu, fora
da thread FX. O próximo comando só começa depois da publicação do projeto.
A publicação preserva cores, visibilidade, follow, ferramentas e parâmetros
suportados pelo importador existente. O comando não salva nem modifica o arquivo
original e não atualiza a preferência real de último diretório.

Abrir substitui o projeto atual: salve antes suas alterações em memória.
Rascunhos de editores precisam ser aplicados/cancelados. Uma operação principal
em andamento também bloqueia a abertura. Se a leitura falhar, houver cancelamento
antes da publicação, cores inválidas ou edição/troca do projeto durante a carga,
a publicação é recusada e o projeto corrente fica intacto. Não há rollback geral
para falhas inesperadas durante a publicação FX, como falta de memória.

O carregamento pelo próprio Terminal não cancela o script: é possível usar
`open_project {...}; offset {objeto} -x 2; get_names` na mesma entrada.
Aberturas pelo menu continuam cancelando um script que esteja trabalhando no
projeto anterior. Avisos e limites da importação Python não foram removidos.

## Transformações em memória

Os quatro comandos atualizam o objeto existente, sem criar cópia. Use os nomes
exatos retornados por `get_names`; coloque nomes com espaços entre chaves.
Gerber, Excellon e Geometry são suportados. CNC Jobs são recusados: o G-code
não é reescrito. Feche editores de objetos antes dessas transformações.

| Comando | Exemplo | Referência e convenção |
| --- | --- | --- |
| `offset` | `offset {placa} -x 1.2 -y -0.3` | Deslocamento absoluto; eixo omitido vale 0. Também aceita `offset {placa} 1.2 -0.3`. |
| `scale` | `scale {placa} 2 -origin center` | Centro por padrão; fator posicional uniforme prevalece sobre `-x`/`-y`. |
| `scale` por eixo | `scale {placa} -x 2 -y 1 -origin {(3,4)}` | `origin` = (0,0), `min_bounds` = canto inferior esquerdo, `center` = centro ou ponto x,y. Eixo omitido vale 1. |
| `mirror` | `mirror {placa} -axis X -origin 0,0` | Eixo X reflete Y; eixo Y reflete X. Padrão: Y. |
| `mirror` por caixa | `mirror {placa} -axis Y -box {contorno}` | Usa o centro da caixa da referência; `-box` prevalece sobre `-origin`. |
| `skew` | `skew {placa} -x 10 -y 0` | Graus; canto inferior esquerdo do objeto. Eixo omitido vale 0. |

Geometria sólida/follow/formas/aberturas do Gerber, posições de furos/slots do
Excellon e caminhos por ferramenta da Geometry acompanham a transformação.
Nome, origem, unidades, cores, visibilidade, perfis/diâmetros e parâmetros CNC
existentes permanecem. Metadados de dimensão das aberturas Gerber continuam
com o limite já existente em `GerberImage.transformed`; não houve um novo port
desses metadados. Esta entrega não acrescenta undo/redo Tcl: transformações
limpam o histórico de Mover para não restaurar snapshots anteriores incompatíveis.

## Diferenças deliberadas frente ao Python

- O Python usa `eval` para alguns pontos; FX só aceita dois números finitos,
  com vírgula e parênteses opcionais. Expressões/código Python são recusados.
- `scale -x`/`-y` não colapsa o eixo omitido a zero. Ele permanece com fator 1;
  fator explicitamente zero é recusado. Fatores negativos são permitidos.
- `mirror` sem `-box`/`-origin` atua em (0,0). No comando Python deste checkout,
  a execução sem ambos acaba sem aplicar transformação.
- Skew exige ângulos estritamente entre -90 e 90 graus, sem singularidades
  de tangente. NaN/infinitos e coordenadas resultantes não finitas são recusados.
- Transformar CNC Job não é permitido aqui, mesmo onde o legado admite offset
  ou scale: atualizar somente a prévia sem reescrever o código seria enganoso.
- O Plot é atualizado após publicar a transformação. O `open_project` Python
  deste checkout solicita `plot=False`; FX restaura a visibilidade do projeto.

## Responsividade e limites

Cálculos rodam no worker, com checagem de cancelamento e de identidade/nome da
origem, referência e projeto antes de publicar. Alterar/remover esses objetos
invalida o resultado atrasado. Transformações de identidade não trocam entradas.

Cancelamento é cooperativo: a decodificação JSON/XZ e uma transformação JTS
individual não são interrompidas no meio. A restauração da árvore e a publicação
do Plot ainda ocorrem na FX em lote. Não se promete tempo máximo/fluidez para
projetos arbitrários. O percentual da abertura é por etapas, com progresso do
parser CNC na fase correspondente; transformações sem fração mensurável exibem
progresso indeterminado, não porcentagem inventada. Cancelar um script não desfaz
comandos já publicados nem restaura o projeto anterior após uma abertura concluída.

## Cobertura e próximos incrementos

22 regressões novas em `TclFlatcamCommandsTest` e `TclLiveHostTest`: argumentos,
referências, ajuda, transformação de dados reais de todos os tipos suportados,
configurações/cores, projetos nativo e Python comprimido, script após abrir,
arquivo original intacto, cancelamento, rascunhos, erros, FX responsiva e
descarte de resultados atrasados. Testes usam fixtures próprios, não projetos
privados nem preferências/arquivos originais do usuário. Validação manual com
os projetos densos reais ainda é necessária; não é benchmark Python/FX.

Verificação: `mvnw.cmd -q install`, 1063 registrados, 1051 aprovados,
12 opcionais ignorados, zero falhas/erros, limpeza normal ativa. Probes nativos
`--probe` e `--probe --software` aprovados com as classes recompiladas.

Agora são 18 famílias de comandos FlatCAM, além de aliases e comandos internos
do dialeto/shell. Faltam, entre outros, `save_project`, `rotate`, `plot_all`/
`plot_objects`, preferências Tcl, joins/subtract/panelize, exportações e flags
CAM avançadas. Próximo incremento recomendado: salvar projeto pelo Terminal e
controle de plot/seleção, mantendo execução serial, gravação segura e erros claros.
