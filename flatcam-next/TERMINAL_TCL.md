# Terminal Tcl — FlatCAM FX

Atualizado em 2026-10-05, sobre `9c187b27`, branch `flatcam-next`.

Abra **Ferramentas > Linha de Comando Tcl**. `help` lista comandos;
`help open_project`, `help save_project`, `help plot_all`, `help plot_objects`,
`help set_active`, `help offset`, `help scale`, `help mirror`, `help skew` e `help rotate`
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

## Salvar projeto

```tcl
save_project {C:/pasta com espacos/projeto.fcnproj}
save_project {C:/pasta com espacos/projeto.FlatPrj}
```

O formato é determinado pela extensão explícita, sem seletor de arquivo:
`.fcnproj` = nativo FX, `.FlatPrj` = exportação Python 8.994. Outras extensões
são recusadas para não gravar silenciosamente o formato errado. O destino pode
ser relativo ou absoluto; sua pasta precisa existir. O comando **substitui um
arquivo existente**, assim como o comando Python; use outra cópia para ensaios.

Usa o snapshot/serializadores do menu, incluindo geometria editada, ferramentas,
parâmetros representáveis, cores, follow, visibilidade, G-code e avisos de
importação. Os arquivos Gerber/Excellon e CNC originais não são regravados.
Os limites de persistência de cada formato continuam válidos; preferências
globais, seleção e câmera não passam a ser persistidas por esse comando.
A exportação Python avisa no console inferior sobre compatibilidade limitada;
recusas existentes para unidades mistas/exclusões/sondagem não foram removidas.
Mantenha também uma cópia nativa quando houver recursos exclusivos FX.

Feche/aplique/cancele os editores antes de salvar. Uma operação principal
concorrente também bloqueia a gravação. A serialização/compressão é feita no
worker, para um temporário ao lado do destino. Cancelamento ou mudança dos
objetos/configurações detectada antes da publicação descarta o temporário e
preserva o destino. A verificação de estado ocorre na FX; a substituição do
arquivo ocorre no worker. Não existe transação única entre o estado da UI e
o filesystem nem promessa de abortar uma compressão em curso imediatamente.
Há tentativa de rename atômico, com fallback se não suportado pelo filesystem.

O próximo comando aguarda a gravação. Cancelar um script **depois** da publicação
não desfaz arquivos já salvos. O Terminal não muda a preferência de último
diretório nem marca uma versão posterior da UI como salva.

## Plot e seleção

```tcl
plot_all -plot_status False
plot_objects {copper,holes,route} -plot_status True
set_active route
set_active holes
save_project {C:/pasta/so-as-camadas-escolhidas.fcnproj}
```

- `plot_all` habilita todos por padrão; `-plot_status False` oculta. Aceita
  `-use_thread True|False` como dica de compatibilidade, mas não modifica a
  política de workers/renderização FX. Não força desenho pesado na thread FX.
- `plot_objects` recebe nomes exatos separados por vírgulas; nomes com espaços
  ficam dentro de chaves/aspas. Campos vazios são ignorados e nomes repetidos
  são deduplicados; uma lista sem nomes é recusada. Um nome contendo vírgula
  não pode ser representado nessa sintaxe, como no comando legado.
- Booleans são literais (`True/False`, `1/0`, `yes/no`, `on/off`), sem `eval`.
  Valores desconhecidos são recusados. `-plot_status` sem valor usa True.
- Todos os nomes são validados antes de alterar a visibilidade; nomes ausentes
  ou ambíguos não produzem alteração parcial. CNC Jobs sem prévia compatível
  são recusados explicitamente (inclusive no lote `plot_all`), uma limitação FX.
- CNC Jobs com apenas Cut ou Travel contam somente as subcamadas existentes;
  o checkbox Plot e a visibilidade salva não ficam ativos por uma camada ausente.
  O seletor All/Travel/Cut acompanha comandos de visibilidade sem reaplicar
  o filtro anterior sobre o resultado do comando.
- Camadas e overlays são atualizados em lote; árvore/seleção são atualizadas uma
  vez. O próximo comando espera a atualização do modelo/FX, não todos os quadros
  apresentados na GPU ou a preparação assíncrona do display.
- `set_active` **adiciona** o objeto à seleção, como `ObjectCollection.set_active`
  do Python; não é seleção exclusiva. Expande a categoria recolhida e não torna
  uma camada oculta visível, não muda a câmera e não pede foco para a árvore.
  Feche editores de objetos antes de mudar a seleção pelo Terminal.

## Transformações em memória

Os cinco comandos atualizam o objeto existente, sem criar cópia. Use os nomes
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
| `rotate` (extensão FX) | `rotate {placa} 90 -origin center` | Graus positivos = horário, negativos = anti-horário, como a UI Transformations Python. Centro do próprio objeto por padrão. |
| `rotate` com referência | `rotate {placa} -90 -origin {(3,4)}` ou `rotate {placa} 90 -box {contorno}` | `origin`, `center`, `min_bounds` ou x,y; `-box` usa o centro da caixa da referência e prevalece sobre `-origin`. |

Geometria sólida/follow/formas/aberturas do Gerber, posições de furos/slots do
Excellon e caminhos por ferramenta da Geometry acompanham a transformação.
Nome, origem, unidades, cores, visibilidade, perfis/diâmetros e parâmetros CNC
existentes permanecem. Metadados de dimensão das aberturas Gerber continuam
com o limite já existente em `GerberImage.transformed`; não houve um novo port
desses metadados. Esta entrega não acrescenta undo/redo Tcl: transformações
limpam o histórico de Mover para não restaurar snapshots anteriores incompatíveis.

## Diferenças deliberadas frente ao Python

- Este checkout Python não tem `TclCommandRotate.py` nem comando Tcl `rotate`:
  a referência é `ToolTransform.on_rotate_action`/tooltip da UI, que chama
  `rotate(-num, point)`. O comando é uma extensão FX, não port de um comando
  legado. Opera num objeto nomeado, não no centro da seleção múltipla.
  Ângulos finitos são reduzidos módulo 360 antes do cálculo; múltiplos de 360
  não recalculam nem trocam a versão. Não há `eval` nas referências.
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

### Histórico: abertura e transformações

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

### Incremento: salvar, plot e seleção

23 regressões adicionais: os quatro comandos e ajuda, argumentos/booleans,
round-trip dos dois formatos com os quatro tipos de objetos, continuidade do
script pelo Terminal real, seleção aditiva, nomes ambíguos/ausentes, checkbox
Plot e seletor All/Travel/Cut, CNC com apenas uma subcamada, publicação em lote
com um redesenho, cancelamento e alterações concorrentes antes da publicação,
preservação do destino, limpeza de temporários, formatos/estados inválidos e
worker/FX responsiva. Fixtures próprios, sem alterar projetos privados nem
preferências do usuário.

Verificação deste incremento: `mvnw.cmd -q install`, **1110 registrados,
1098 aprovados, 12 opcionais ignorados**, zero falhas/erros. Probes nativos
`run-native.cmd --probe` (D3D/GTX 1650) e
`target/native/FlatCAMFX.exe --probe --software` aprovados; executável atualizado.
Não houve benchmark de fluidez, teste físico CNC nem nova comparação privada
Python/FX. Validação manual do roteiro com projetos reais continua pendente.

### Incremento: rotação no Terminal FX

Sete regressões adicionais: argumentos/sentido/pivôs e ângulos finitos grandes,
rotação dos dados Gerber completos e de furos/slots/caminhos por ferramenta,
parâmetros e apresentação preservados, G-code original intacto, persistência,
identidades, referência vazia, recusas CNC e cancelamento/alterações concorrentes.
`mvnw.cmd -q install`: **1117 registrados, 1105 aprovados, 12 opcionais ignorados**,
zero falhas/erros. Sem novo benchmark nem validação física CNC.

O incremento salvar/plot/seleção acrescentou quatro famílias; com `rotate`,
há **23 famílias FlatCAM (incluindo a extensão FX de rotação)**, além de aliases
e comandos internos do dialeto/shell. Faltam preferências Tcl, joins/subtract/panelize,
exportações e flags CAM avançadas. Próxima fatia sugerida: junções
pelo Terminal, reutilizando as operações existentes sem ampliar o dialeto Tcl.
