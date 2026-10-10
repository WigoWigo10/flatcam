# Fluxos principais — critérios e validação

## Estado atual — 2026-10-10

O histórico abaixo registra etapas anteriores; o estado atual é consolidado
em [CONTEXTO_E_PROGRESSO.md](CONTEXTO_E_PROGRESSO.md) e
[COMPARACAO_CAM.md](COMPARACAO_CAM.md). A união NCC foi corrigida (`6fc150ee`),
mas a matriz **panelizada** ampliada ainda revela diferenças de Connect,
inclusive em fixtures públicas. Não declarar 100% dos fluxos principais antes
de fechar essas diferenças; detalhes em [PANELIZED_NCC.md](PANELIZED_NCC.md).

Atualizado em 2026-10-07, branch `flatcam-next` (incremento anterior: `eeeef1ff`).
Prioridade solicitada: consolidar os fluxos de produção antes de ampliar
ferramentas secundárias/automação. Não declarar 100% apenas pela presença dos painéis.

## Corpus diferencial ampliado — 2026-10-07

Comparação headless agora cobre 19 casos MM e 19 IN, incluindo Follow/exceções,
tipos de isolamento, Connect/Contour e limites NCC. Métodos de seleção/margem
do Python são compilados do fonte original; cortes do G-code interpretado são
comparados separadamente. Runner e resultados em [COMPARACAO_CAM.md](COMPARACAO_CAM.md).
Connect e o comprimento dos cortes interpretados de Reference Geometry IN
ainda divergem; falhas do legado Shapely 2 não contam como equivalência.
Rest/múltiplas ferramentas, projetos reais e interação manual seguem pendentes.

## Configurações comuns da Tools Database — 2026-10-07

Transferência de preprocessor, rapid feed, troca, Start/End Z/XY e Tool change
Z/XY implementada para Geometry CNC e Drilling. Isolation/NCC/Milling/Cutout
preservam sugestões explícitas por índice de ferramenta na Geometry e no
`.fcnproj`; o CNC recupera campos concordantes e bloqueia conflitos até revisão
explícita. Drilling resolve só ferramentas selecionadas. Ausência não limpa
rascunhos; None explícito preserva automático. Unidades não são convertidas.

Esta seção supera as limitações históricas abaixo para esses campos, não para
todas as opções globais da DB. `.FlatPrj` recusa sugestões pendentes antes de
gerar/revisar CNC para evitar perda; projetos nativos antigos continuam abrindo.
Contrato e roteiro manual em [CNC_DATABASE_COMMON.md](CNC_DATABASE_COMMON.md).

## Panelize: prévia e registro entre camadas — 2026-10-07

Implementada prévia de conteúdo/contorno/cortes como no 2-Sided Tool e criação
de conjunto com um layout comum. Excellon mantém IDs/diâmetros e Geometry
índices de ferramentas; geração valida entradas, projeto e cancelamento antes
do lote FX. Trinta e dois cenários novos, incluindo MM/IN, persistência, criação
separada equivalente, callbacks tardios e temas offscreen. Detalhes, limites
de memória/cancelamento e roteiro manual pendente em [PANELIZE.md](PANELIZE.md).
As origens precisam estar previamente alinhadas; compartilhar a referência
não corrige desalinhamento original. Nenhum novo oráculo Python/FPS real.
Prévia diferencia Gerber/Excellon/Geometry por azul/laranja/violeta, com legenda,
tons por tema e furos por cima. Não altera a paleta persistida dos objetos.

## Primeiro incremento: Gerber → Geometry

Isolation, NCC e Cutout agora validam os dados usados no cálculo antes de
publicar. A origem precisa ser a mesma geometria que o painel ofereceu; referências
de NCC e objetos de exceção Isolation também precisam existir nessa versão.
Um editor ativo ou outro job principal impede iniciar. Antes da publicação,
revalidam projeto, identidade/nome/versão das entradas, editores e cancelamento.
Mudanças invalidam o resultado com mensagem no console/status, sem criar Geometry.
Até um cancelamento posterior à conclusão do worker, mas anterior ao callback FX,
descarta o resultado.

Mudar cores/visibilidade ou um objeto não participante não invalida o cálculo.
Se outro painel foi aberto enquanto o cálculo rodava, ele não é fechado e sua
seleção/câmera não é tomada pela conclusão antiga. A Geometry válida ainda entra
no projeto. Progresso/erros de callbacks antigos não encerram um job posterior.
Concluir/cancelar uma geração não desfaz objetos anteriormente publicados.

Corrigido também Isolation com exceções: passadas geradas como GeometryCollection
não são aceitas pelo overlay binário JTS. Agora cada parte é recortada contra a
máscara poligonal e reagrupada na mesma passada, como o laço de anéis/linhas em
`ToolIsolation.area_subtraction`. Máscaras poligonais aninhadas são unidas;
misturas de polígonos com pontos/linhas são recusadas explicitamente. Follow
mantém pontos fora da máscara (o helper Python não trata esses pontos de modo
equivalente). Passadas totalmente removidas ficam vazias, sem inventar caminhos.
Valores permanecem nas unidades da origem. Fontes não são alteradas.

Cancelamento é cooperativo entre partes; um buffer/overlay/união JTS individual
não é interrompido no meio. A publicação de múltiplos objetos ainda é um lote
FX, não uma transação com rollback para falhas inesperadas como falta de memória.
Não houve mudança dos algoritmos NCC/Cutout, dos limites Seed, da precisão CAM
ou da política de agendamento/uso da GPU.

## O que os testes automatizados demonstram

`MainCamFlowTest`: 44 cenários no MainWindow/painéis reais, sem Stage, arquivos
privados ou escrita de preferências. Incluem origem/projeto/referência alterados,
cancelamento antecipado e tardio, cliques repetidos, editores, troca de painel,
visibilidade e recorte Isolation com saídas separadas por passada.

Seis cenários seguem **geração CAM da UI → CNC pelo host Tcl → exportação G-code →
salvar/reabrir `.fcnproj`**, para Isolation/NCC/Cutout em MM e IN. Conferem caminhos,
unidades, diâmetros/perfis e defaults Cutout persistidos, G-code e prévia.
Eles não exercitam o FileChooser nem a geração pelo painel Geometry CNC da UI.
`IsolationGeneratorTest` acrescenta cinco regressões de coleções/Follow/máscaras,
remoção total, unidades IN, fontes intactas e cancelamento.

Verificação: `mvnw.cmd -q verify` completo passou com **1340 registrados,
1328 aprovados, 12 opcionais ignorados**, sem falhas/erros e com limpeza normal.
Probes do launcher existente, com build atualizado, passaram em D3D/Intel Arc
e software forçado, sem abrir janela. Não são medições de fluidez CAM.

Não foi reexecutada a comparação diferencial privada Python nesta entrega.
Leitura do código legado e fixtures sintéticos não substituem comparação com
projetos reais, validação de todos os controles ou teste físico CNC.

## Roteiro manual pendente no FX e no Python

Use cópias do mesmo projeto e os mesmos valores/unidades. Nunca substitua os
Gerbers/Excellons originais nos ensaios.

1. Isolation: duas ferramentas, duas passadas, Combine ligado/desligado; depois
   Rest/Forced Rest, exceção por objeto e desenhada. Confira ferramentas,
   caminhos remanescentes, seleção/Plot e mensagens para regiões não isoladas.
2. NCC: Standard/Lines, ferramentas CLEAR/ISO, Rest, Connect, limites Itself,
   área e referência. Compare também a pegada das ferramentas e regiões não
   alcançadas; Seed tem diferença deliberada, não equivalência literal.
3. Cutout: contorno retangular e não retangular, pontes, Thin e M-Bites.
   Confira os gaps, profundidade Thin e Excellon derivado. Nenhum teste sintético
   prova resistência física das pontes.
4. Durante cada geração: Cancelar/ESC; renomear/remover/transformar a origem ou
   referência; abrir outro painel. Confira que nada obsoleto entra na árvore.
   Alternar visibilidade deve continuar permitido sem invalidar um cálculo válido.
5. Geometry → CNC: revisar compensação Path, parâmetros por ferramenta,
   passes Z, avanços/spindle, posições e exclusões; exportar para outro arquivo.
   Salvar, fechar e reabrir o nativo; comparar paths, parâmetros e código.
6. Conferir claro/escuro e projetos densos. Só depois, ensaio a seco separado
   com limites, fixação e referenciamento verificados na máquina.

## Próximos incrementos

1. **Geometry → CNC pela UI: incremento implementado em 2026-10-07.** Geração,
   prévia e gravação usam worker; o arquivo é preparado em temporário no mesmo
   diretório, validado e só depois substitui o destino. Projeto, identidade/nome
   da origem, configurações salvas e editores são revalidados. Falha/cancelamento
   antes da publicação preserva o arquivo e os defaults. Outro painel aberto não
   é fechado pela conclusão. `GeometryCncGenerationTest`: 11 cenários (MM/IN,
   cancelamento por fase, falha de validação e alterações reais no MainWindow).
   `mvnw.cmd -q verify` passou; FileChooser e ensaio físico não foram automatizados.
   A substituição usa movimento atômico quando suportado; arquivo e objeto da UI
   não constituem uma transação única. Cancelamento após substituir o arquivo não
   desfaz essa publicação. Operações geométricas individuais continuam cooperativas.
2. Completar consumo avançado de parâmetros Drilling/Tools Database e seus
   roteiros CNC/persistência; seguir `CNC_EXCLUSIONS.md` e `PREPROCESSADORES.md`.
   Incremento 2026-10-07: Milling valida identidade/nome/projeto/editor e
   cancelamento antes de criar Geometry; preserva outro painel. Drilling ignora
   callbacks antigos e revalida cancelamento tardio. Cut Z positivo na Tools
   Database é recusado, não convertido silenciosamente em profundidade negativa.
   `MainExcellonFlowTest`: 12 cenários, incluindo Drills/Slots em MM/IN,
   parâmetros de DB → Geometry → G-code → projeto nativo e fontes intactas.
   O perfil e parâmetros de corte já suportados são conservados; posições,
   exclusões, ordem e rapid feed continuam opções comuns do painel Drilling,
   não há aplicação integral automática de todos os campos globais da DB.
3. Ampliar validação do salvar → Python → FX para dados dos fluxos principais,
   sem remover avisos/recusas de perda de dados.
   Incremento 2026-10-07: Gerber/Geometry importados normalizam MM/METRIC e
   IN/INCH sem reescalar coordenadas (Excellon já fazia isso). Unidade desconhecida
   é recusada. Cut Z positivo opcional não vira corte negativo: os caminhos/furos
   são mantidos, os defaults inválidos são descartados e permanecem os avisos de
   dados CAM parcialmente restaurados. `PythonMainFlowImportTest`: 8 casos,
   incluindo cores, aberturas, visibilidade e salvar/reabrir nativo/Python.
   Não houve migração automática de preferências globais ou remoção dos avisos.
4. Consolidar transferência de parâmetros de usinagem da Tools Database na
   geração Isolation → Geometry → CNC, além dos parâmetros de isolamento.
   Incremento 2026-10-07: dados Milling opcionais explícitos seguem a ferramenta
   por diâmetro e são reindexados após filtrar saídas vazias/separar passadas.
   Preserva Travel/Cut Z, passes Z, feeds XY/Z, spindle, dwell, extra cut e V-Tip.
   Campos obrigatórios incompletos são recusados, não preenchidos silenciosamente.
   Offset fica Path: o isolamento já gerou centros compensados. Ferramentas
   manuais não herdam a configuração importada. Rapid feed, preprocessor,
   posições e troca continuam comuns/revisados no CNC, não transferidos da DB.
   `MainIsolationMachiningTest`: 6 casos, Combine on/off e MM/IN, painel CNC,
   persistência nativa, V-Tip e compensação. Rest/Follow/exceções mantêm a cobertura
   existente; comparação diferencial/manual de todas as opções ainda pendente.
5. Consolidar essa transferência para NCC e testar CLEAR/ISO, Rest e limites;
   conservar a diferença documentada do algoritmo Seed.
   Incremento 2026-10-07: parâmetros de corte/V-Tip explícitos da DB seguem
   ferramentas CLEAR/ISO e índices efetivos na Geometry; não herdam valores para
   outras ferramentas. `MainNccMachiningTest`: 9 casos, MM/IN, Rest, Itself,
   Area/Reference, settings individuais CLEAR, ISO V, salvar/reabrir, painel CNC,
   geração/prévia e transporte pelas duas forms CAM. Offset Path e opções comuns
   têm os mesmos limites do incremento Isolation. Nenhum algoritmo NCC foi
   alterado; Seed permanece diferente do legado.
   A prévia do programa sintético denso agora também deve estar disponível;
   o incremento de CNC denso abaixo substitui o antigo corte em 50 mil segmentos.

Os cinco incrementos de consolidação acima foram implementados separadamente.
Isso não encerra todos os critérios de paridade: permanecem a comparação CAM
com projetos reais, todos os controles manuais, opções globais da DB/preferências
não transferidas e os ensaios a seco/físicos. Os testes são verificações de
regressão e integração sintéticas, não uma certificação de segurança CNC.

Verificação final em 2026-10-07: **1387 registrados, 1375 aprovados, 12 opcionais
ignorados**, zero falhas/erros no `mvnw.cmd -q verify`. Probes D3D/software passam.
Harness sintético contra Python 8.994/Shapely 2.1.2: Isolation 1/3 e NCC Standard
atendem aos critérios amostrados (`MATCH_SAMPLED`, 3 casos, `--strict` aprovado).
Seed/Lines retornam `ORACLE_ERROR` por incompatibilidade multipart no legado;
não são equivalência aprovada. Relatórios em `target/five-flows-cam-comparison/`.

Referências: [plano de paridade](PLANO_PARIDADE.md), [Cutout](CUTOUT.md),
[Geometry/CNC](GEOMETRY_CNC.md) e [compatibilidade](COMPATIBILIDADE_FLATPRJ.md).

## Incremento seguinte: prévia de CNC denso (2026-10-07)

O parser não descarta mais a prévia depois de 50 mil segmentos. Acima de
50 mil linhas, buffers são montados em blocos de até 128 pontos de entrada,
separados por deslocamento/corte, diâmetro, ferramenta e descontinuidade.
Otimização só da representação da prévia: centros colineares redundantes são
removidos com orientação robusta; curvas, reversões e bends são preservados.
G-code e geometria CAM original não são alterados. Arcos mantêm a discretização
já existente; não se trata de simulação exata de controlador.

Navegação conserva todos os pontos e comprimentos, inclusive além do limite
antigo, com arrays primitivos para evitar o boxing de coordenadas. Distância,
tempo estimado e dados de furos/rasgos/ferramentas cobrem o programa completo.
Somente as setas decorativas continuam limitadas às primeiras 50 mil; não é
um limite de desenho, de etapas de navegação ou de movimentos gerados.

Importação/edição continuam usando workers. Progresso de leitura ocupa 0–95%,
buffer final/estatísticas ocupam o restante; 100% só após montar a prévia.
Importação e edição limitam atualizações FX a uma por porcentagem. Resultado
cancelado, projeto alterado ou CNC substituído/removido não é aplicado, mesmo
quando o cancelamento chega depois do worker e antes do callback FX.
Edição reutiliza o desenho por centros com largura real quando há diâmetro único.

`DenseGCodeToolpathParserTest`: nove testes, incluindo 60 mil movimentos reais,
fim da rota, estatísticas, diâmetros distintos, furos/slots, curvas, reversões,
IN, 51 mil bends, progresso em comentários, código não suportado no fim e
cancelamento durante/final do processamento.
`MainDenseCncPreviewTest`: oito cenários offscreen de importação/edição,
cancelamento tardio/troca de projeto/remoção e callbacks de job antigo.
NCC também exige prévia disponível.

Cancelamento continua cooperativo: um buffer JTS individual não é interrompido
por dentro; seu tamanho de entrada é limitado. Memória ainda cresce com o
programa e seus pontos de navegação, não há garantia de arquivos arbitrariamente
grandes. Validar com Cobre_Morto_Bottom_cnc: abrir/editar, cancelar, navegar até
o último caminho, zoom/pan e comparar G-code salvo. Testes sintéticos não
comprovam fluidez do projeto privado nem segurança da máquina.

Verificação deste incremento: `mvnw.cmd -q verify`, 1404 registrados,
1392 aprovados, 12 opcionais ignorados, zero falhas/erros. Probes do launcher
em Direct3D e software passaram sem janela. Dez fixtures da exportação Java
do harness CAM exigem e obtêm prévia disponível; dados locais em
`target/dense-cnc-cam-comparison/fx-cam.json`. Não foi reexecutado o oráculo
Python nesta etapa. Os resultados diferenciais históricos continuam distintos
desta regressão de disponibilidade da prévia.
