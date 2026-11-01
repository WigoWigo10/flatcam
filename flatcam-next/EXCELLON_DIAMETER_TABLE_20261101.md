# Diâmetro direto nas propriedades Excellon — 2026-11-01

A tabela Ferramentas de Excellon Object passa a editar a coluna Diameter por
duplo clique, F2 ou Enter. Aceita ponto/vírgula decimal, Enter confirma,
Esc descarta o texto e Tab/Shift+Tab percorrem as ferramentas, pulando os
totais. Total Drills/Total Slots e demais colunas não são editáveis.

## Efeito da alteração

- Altera o diâmetro real de todos os furos e rasgos do ID escolhido.
  Mantém os IDs, centros, extremidades e os vértices dos rasgos roteados.
- Não junta ferramentas que passam a ter o mesmo diâmetro; seus IDs e
  parâmetros de usinagem continuam separados.
- O texto em edição usa o double completo, sem copiar a exibição arredondada.
  Fora da edição, a tabela respeita a precisão de exibição MM/IN.
- Reconstrói as formas em worker com progresso e cancelamento. O Plot mantém
  a geometria anterior durante o cálculo e recebe a nova sem trocar cor,
  visibilidade ou recriar o painel. Só a edição fica bloqueada durante a operação.
- Valores zero, negativos ou não finitos não são aplicados; o texto inválido
  fica disponível para correção ou Esc. As ações Drilling/Milling/Editor do
  painel validam o texto pendente antes de continuar.
- Salvar pelo menu também valida o campo pendente; se a confirmação iniciar
  a atualização, aguarde sua conclusão antes de salvar novamente. O painel
  avisa que o diâmetro aplicado ainda precisa ser salvo/exportado.
- O arquivo de origem não é sobrescrito. Salvar projeto conserva o valor;
  exportar Excellon usa o diâmetro atualizado com a precisão do formato de saída.
  CNC Jobs e Geometries já gerados não são recalculados automaticamente.

## Histórico e publicação

Ctrl+Z/Ctrl+Y e o menu da tabela desfazem/refazem até 100 alterações confirmadas
por objeto na sessão. O histórico guarda apenas ID e valores anteriores/novos,
sem reter 100 geometrias densas. Continua disponível ao trocar e voltar às
propriedades. Dentro do campo, o histórico do texto é local.

Outro editor/transformação que substitua a imagem invalida esse histórico.
Excluir o objeto ou limpar o projeto remove seu histórico. Movimentos antigos
do Plot são invalidados ao aplicar o diâmetro, como nas demais edições de forma.
O histórico não é serializado; o diâmetro confirmado é.

Antes da publicação, o worker confere a época do projeto e a identidade da
imagem. Cancelamento, remoção/substituição da origem e troca de projeto
preservam o objeto atual e não registram uma alteração no histórico. Os
parâmetros Drilling por ID permanecem; os geradores continuam validando-os.

## Validação

- `ExcellonToolDiameterTest`: MM/IN, precisão, IDs, furos e rasgos retos/roteados,
  dimensões reais das formas, diâmetros iguais sem fusão, valores inválidos e
  cancelamento no meio da reconstrução.
- `ExcellonDiameterTableTest`: F2/Enter nos controles reais, atualização do Plot,
  Undo/Redo, roundtrip `.fcnproj`/`.FlatPrj` em MM/IN, erros/Esc, totais,
  diâmetros iguais e workers cancelados/obsoletos.
- Rodada focada: 56 casos aprovados (3 CAM + 53 FX), zero falhas/erros,
  `target/excellon-diameter-focused4.log`. Não somar execuções repetidas.
- A primeira rodada registrou erro de compilação de teste por uma chamada
  inexistente a `layerGeometry`; a asserção foi corrigida para ler a camada
  real do Plot. Log inicial preservado; nenhuma asserção funcional foi removida.
- O probe da distribuição foi ampliado para editar, desfazer/refazer e
  conferir persistência usando os JARs do pacote. A validação integral fica
  registrada na revisão da Beta 4 que incluir este incremento.

Tooltips PT-BR/EN explicam o alcance por ID, os atalhos, o salvamento e a
necessidade de gerar novamente as saídas CAM/CNC quando apropriado.
