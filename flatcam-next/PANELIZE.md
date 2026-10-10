# Panelização e prévia — FlatCAM FX

## Continuação CAM verificada — 2026-10-10

Validado no projeto real: panelização 2 x 2 com gap 5 mm -> Isolation/NCC
nas duas faces -> Cutout exterior por placa -> CNC/export -> salvar/reabrir.
Cinco casos numéricos passam frente ao Python, dentro das tolerâncias existentes.
Runner/escopo/diferenças em [COMPARACAO_CAM.md](COMPARACAO_CAM.md).

Para reproduzir o fluxo aprovado:

1. Escolha o Edge_Cuts como contorno físico e referência compartilhada. Pode
   convertê-lo para área antes de panelizar, ou deixar a conversão para depois.
2. Panelize cobre, Excellons, contorno e, se já existir, essa área no MESMO
   conjunto/layout. Se ainda não houver área, selecione o contorno panelizado
   e use **Editar > Converter > Contorno → Área**. Agora a conversão preserva
   todas as áreas separadas e seus recortes. Veja [OUTLINE_TO_AREA.md](OUTLINE_TO_AREA.md).
3. Isolation usa F_Cu/B_Cu panelizados. NCC aprovado: Standard, Connect off,
   Contour on, referência **Geometry da área panelizada**, margem zero.
4. Cutout: área panelizada como origem, **Panel**, Free-form e Convex Shape off.
   O modo trabalha por componente separado, não reconhece placas a partir de
   ilhas de cobre. Espaçamento, raio da fresa e pontes precisam ser conferidos.
5. Gere/revise CNC, confira a prévia, exporte e salve `.fcnproj`.

Este cenário NÃO certifica NCC Connect/Rest/Itself ou todas as receitas CNC.
Cutout gera os perímetros externos por padrão. Para recortes internos, marque
**Incluir recortes internos**, com área preenchida, Panel, Free-form, Convex
Shape off e margem não negativa. Os recortes ficam fechados e sem pontes;
veja [CUTOUT.md](CUTOUT.md). A área real testada tem zero anéis internos;
fixtures MM/IN validam a opção via CNC/export e persistência. Programas gerados
pelo teste não devem ser usados na máquina sem revisão/configuração própria.

Observação inicial: salvar/reabrir o painel real denso levou ~62 segundos.
O perfil posterior identificou compressão XZ como maior custo de salvar;
preset 1 reduziu o salvamento isolado de ~42 s para ~13–14 s, com arquivo
~5,6% maior e os mesmos dados. Reabrir ainda tem custo de JSON/WKT/preview.
São medições headless locais, não FPS; veja [PLOT_PERFORMANCE.md](PLOT_PERFORMANCE.md).

Implementação de 2026-10-07. Ferramentas > Panelize Tool.

## Uso

1. Escolha um Gerber, Excellon ou Geometry. Para criar várias camadas juntas,
   marque **Panelizar conjunto** e selecione os objetos nos checkboxes da lista.
2. Use **Caixa de outro objeto** e escolha o contorno físico da placa, por
   exemplo Edge_Cuts. Quando um nome de contorno é identificado ao abrir,
   ele é escolhido como referência e como contorno da prévia (Edge_Cuts tem
   prioridade). Confira essa escolha: o nome não comprova que seja um contorno.
3. Ajuste colunas, linhas e espaçamentos. A mesma grade é aplicada a todas as
   camadas escolhidas. A caixa combinada do conjunto também pode ser usada.
4. Como no 2-Sided Tool, habilite conteúdo, contorno e preenchimento translúcido
   da placa. **Enquadrar prévia** mostra todas as cópias calculadas. São overlays,
   não objetos selecionáveis ou salvos no projeto.
   As categorias usam cores distintas: Gerber azul, Excellon laranja/âmbar e
   Geometry violeta, com legenda no painel. Os tons se ajustam ao tema; furos e
   slots são desenhados por cima do cobre/Geometry. As cores dos objetos originais
   e dos resultados criados não são alteradas por essa paleta de prévia.
5. Clique **Criar painel**. Cada origem recebe um novo objeto `_panelized`;
   os originais não são alterados. Para criar também o contorno, marque-o na lista:
   escolhê-lo só para a prévia não cria seu painel.

Para panelizar Gerber e Excellon separadamente, reutilize exatamente a mesma
referência, colunas/linhas, espaçamentos e limites. Os objetos precisam estar
alinhados antes: o FX só aplica translações iguais, não recentra nem corrige
origens diferentes. Todos os objetos, referência e contorno da prévia precisam
usar as mesmas unidades MM ou IN; conversão implícita é recusada.

## Prévia e limites

- O desenho repete cobre, furos/rasgos e caminhos, incluindo anéis internos.
  O contorno escolhido mostra borda e cortes internos. Sem contorno, as caixas
  são explicitamente identificadas como caixas, não como bordas reais da placa.
- Contornos abertos mostram linhas, mas não são fechados por convex hull ou
  preenchidos artificialmente. Quando o contorno excede o passo entre cópias,
  a prévia avisa sobre possível sobreposição; o usuário ainda pode escolher
  deliberadamente a caixa do próprio objeto.
- A prévia é calculada em worker separado, com debounce de 180 ms. A última
  prévia permanece enquanto a próxima é preparada. Fechar/trocar a ferramenta,
  invalidar entradas ou desabilitar a prévia cancela/invalida a publicação.
- A criação usa um worker com progresso e cancelamento. Todos os resultados
  são calculados antes da publicação; alterações de projeto, nome/versão das
  origens/referência, editores e cancelamento tardio impedem aplicar o lote.
  Abrir outro painel não faz sua seleção/câmera ser tomada pela conclusão antiga.
- Cancelamento é cooperativo entre cópias. Uma transformação ou união JTS
  individual não é interrompida por dentro. Memória e custo crescem com a
  densidade e o número de cópias; não há garantia de fluidez para qualquer grade.
- Limite de segurança: 10.000 cópias por layout, ajustável futuramente ou por
  `-Dflatcam.panelize.maxCopies=N`. Não substitui um orçamento de memória.
  Limites numéricos inválidos, infinitos, negativos e overflow são recusados.
- Excellon mantém IDs e diâmetros exatos, furos e slots. Geometry mantém índices
  de ferramentas, mesmo com diâmetros/perfis iguais. Defaults de ferramentas e
  parâmetros CNC Geometry permanecem associados. Não se copia a configuração
  completa do último job Drilling (incluindo exclusões absolutas); revise-a antes
  de gerar CNC. Gerber continua usando a junção existente de formas/aberturas.
- Publicação é um lote FX, não uma transação com rollback para OOM/erro inesperado
  durante a inserção. O limite de tamanho descreve a caixa de referência; conteúdo
  fora dela pode ultrapassá-lo. Não há mudança nos algoritmos CNC ou no Python.

## Verificação e roteiro manual

`PanelizeAlignmentTest`, `PanelizePreviewTest`, `PanelizeToolPanelTest`,
`PanelizePreviewControllerTest`, `MainPanelizeFlowTest`, `ToolPreviewPaletteTest`
e `ToolPreviewContentTest`: 32 cenários novos (incluindo a diferenciação de cores).
Cobrem MM/IN, origens não nulas, caixas individuais diferentes, ferramentas/slots,
recortes internos, referências compartilhadas, persistência nativa, unidades,
cancelamento, callbacks tardios e overlays em temas claro/escuro. Usam fixtures
sintéticos e cenas JavaFX sem Stage; não alteram preferências ou projetos privados.

Verificação final, incluindo cores: `mvnw.cmd -q verify` passou com 1436 registrados, 1424
aprovados, 12 opcionais ignorados e zero falhas/erros. Probes offscreen do
launcher passaram em Direct3D/Intel Arc e software forçado. Snapshots sintéticos
claro/escuro foram conferidos; isso não mede FPS nem valida o projeto privado.

Pendente no projeto real: marcar F_Cu, B_Cu, PTH, NPTH e Edge_Cuts; escolher
Edge_Cuts como referência; conferir a prévia 2 x 2 com espaçamento e recortes;
criar, verificar sobreposição furos/pads, salvar/reabrir `.fcnproj` e comparar
uma panelização separada com os mesmos valores. Testar fechamento/troca de
ferramenta, temas e cancelamento numa grade densa. Não declara paridade total,
benchmark de desempenho nem validação física CNC. O comando Tcl `panelize`
continua pendente.
