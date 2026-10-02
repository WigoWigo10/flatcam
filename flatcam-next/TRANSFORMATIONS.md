# Transformations: referência e Buffer

Origin, Selection, Point e Object disponíveis. Object usa o centro da caixa do
objeto escolhido (Gerber/Excellon/Geometry), não o centro da seleção. Referências
removidas/vazias e coordenadas não finitas são recusadas, sem fallback para zero.

Buffer por distância: positivo expande, negativo contrai; Rounded usa cantos
arredondados, desmarcado usa mitrados. Buffer por percentual usa 1 + percentual/100,
maior que zero e diferente de 1, com centros locais em vez da referência global.
Execute em objetos da mesma unidade; não há conversão automática.

Geometry preserva associação e diâmetro de ferramentas, atualizando as geometrias.
Gerber preserva Follow; metadados de tamanho das aberturas ainda ficam originais,
como nas outras transformações do FX. Contornos e exportação por regiões carregam
o sólido alterado. Buffer percentual de Gerber usa escala dos contornos locais;
não reproduz todas as reconstruções por abertura/macro do Python.

Excellon mantém centros de furos e extremos dos slots. Distância é somada ao
**diâmetro**, como em ParseExcellon.buffer (não ao raio); percentual multiplica
diâmetros. Reconstrói footprints a partir desses diâmetros, inclusive slots.
Diâmetro zero/negativo é recusado. CNC Job não pode ser transformado.

Buffer usa executor fora da thread FX, calcula todas as saídas antes de publicar
e recusa resultados vazios/inválidos. Cancelamento, erro ou mudança dos objetos
durante o cálculo não publica alterações parciais. Não há undo global de Buffer;
salve o projeto ou faça cópia antes. Validação visual manual continua pendente.
