# Geometry → CNC: compensação e posições

Atualizado em 2026-10-02. Este incremento cobre fresagem sem sondagem;
não certifica o funcionamento físico dos controladores.

## Compensação por ferramenta

No painel Geometry → CNC Job, selecione uma ferramenta e configure **Tool Offset**:

- **Path**: mantém os centros dos caminhos. É o padrão e normalmente deve ser
  usado nas saídas de Isolation/NCC, que já contêm o afastamento da ferramenta.
- **In / Out**: contrai/expande pela metade do diâmetro dessa ferramenta.
- **Custom**: distância assinada nas unidades do objeto; zero exige usar Path.

A operação segue o Python `generate_from_geometry_2`: linhas fechadas são
convertidas em polígonos antes de `buffer`, com junções mitradas. Polígonos
mantêm seus furos; coleções são processadas por elemento. Em linhas abertas,
offset positivo cria o contorno do buffer, não uma simples linha paralela.
Um offset que elimina um elemento é recusado, sem gerar um trabalho parcial.

A Geometry original não é modificada. O G-code e a prévia usam os caminhos
compensados. O parâmetro é individual; **Aplicar parâmetros a todas as
ferramentas** copia a configuração explicitamente. Rascunhos inválidos são
preservados ao trocar de linha e todas as linhas são verificadas antes de gerar.
Não são emitidos comandos de compensação do controlador G41/G42.

## Posições comuns

Expanda **Posições e troca de ferramenta**. Vazio ou `None` mantém o comportamento
automático. As posições valem para todas as ferramentas do trabalho:

- **Start Z**: movimento inicial Z, antes de ligar o spindle. Antes do primeiro
  deslocamento XY, o gerador retrai para a altura de segurança.
- **End Z / End X,Y**: desligamento do spindle, retração e estacionamento final.
  Se End Z for baixo, o movimento XY ocorre em `max(Travel Z, End Z)` e só depois
  desce para End Z. Isto é uma diferença de segurança deliberada do Python.
- **Tool change Z / X,Y**: retração antes do posicionamento e sequência de troca.
  Depois de M0/M6, modo absoluto e retração Z são reafirmados antes do próximo XY.
  A altura explícita precisa ser pelo menos o maior Travel Z das ferramentas.
  Só é aplicada com troca ativada ou seleção automática pelo perfil. A posição
  explícita também é usada na primeira troca; sem configuração explícita,
  o perfil FX Portable mantém a pausa apenas entre ferramentas.

O modo automático usa o maior Travel Z no fim/troca e não adiciona posições XY.
Start Z e End Z aceitam zero, mas não valores negativos; confirme o ponto de
contato e o destino. XY aceita `X,Y`, `(X,Y)` ou `X;Y`; para vírgula decimal,
use ponto e vírgula entre as coordenadas (`12,5;20,5`). Valores não finitos são
recusados. Deslocamentos de troca/fim também aparecem na prévia.

Laser, HPGL, Roland e Mach3 com sondagem não aceitam estes novos parâmetros
neste incremento. Os controles ficam desabilitados, mas os valores são
preservados: um perfil incompatível recusa configurações ativas em vez de
descartá-las silenciosamente. Volte ao perfil de fresagem para limpar os campos;
Mach3 com sonda continua usando seu painel específico.

## Persistência, compatibilidade e limites

`.fcnproj` guarda offset/valor individual e posições comuns; arquivos antigos
sem esses campos continuam com Path e posições automáticas. `.FlatPrj` usa
`offset`/`offset_value` da ferramenta e `startz`, `endz`, `endxy`, `toolchangez`,
`toolchangexy` nos dados CAM, além das opções comuns. Reabrir no FX após o Python
descartar os metadados privados recupera esses campos disponíveis. Valores
automáticos de End Z/troca são exportados como alturas explícitas para o Python.

Offset/valor da Tools Database agora são transferidos explicitamente em Milling
e Geometry/CNC. Cutout já gera caminhos compensados e mantém Path no CNC.
Áreas de exclusão Around/Over implementadas para Geometry, com recusa de cortes
que atingem as regiões protegidas e persistência nativa; ver CNC_EXCLUSIONS.md.
Exportação de projetos Python com exclusões bloqueada para evitar perda silenciosa.
Compensação G41/G42 e posições distintas por ferramenta não estão implementadas.
Macros M6 podem mover a máquina; a prévia não simula a macro.
Start Z não substitui o referenciamento nem confirma que o material está livre.

Testes: `GeometryAdvancedCncTest`, `GeometryAdvancedPanelTest`,
`GeometryPerToolProjectTest` e `PythonProjectWriterTest`. Controles são exercitados
na thread JavaFX sem janela visível. Validação visual no aplicativo e teste a
seco na máquina continuam necessários.

## Publicação pela UI (2026-10-07)

O worker prepara G-code e prévia antes de substituir o destino. Arquivo temporário
no mesmo diretório, revalidação da origem/projeto/configurações/editor e checkpoints
de cancelamento evitam truncar o destino em falhas anteriores à publicação.
Defaults só são atualizados na conclusão válida, e outro painel aberto é preservado.
`GeometryCncGenerationTest` cobre 11 cenários, incluindo MM/IN e origem alterada.
Não há rollback conjunto entre a substituição do arquivo e a publicação do CNC Job
na UI; cancelar depois da substituição não recupera o arquivo anterior.
