# Fluxos principais — critérios e validação

Atualizado em 2026-10-07, branch `flatcam-next` (incremento anterior: `eeeef1ff`).
Prioridade solicitada: consolidar os fluxos de produção antes de ampliar
ferramentas secundárias/automação. Não declarar 100% apenas pela presença dos painéis.

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
5. Consolidar essa transferência para NCC e testar CLEAR/ISO, Rest e limites;
   conservar a diferença documentada do algoritmo Seed.

Referências: [plano de paridade](PLANO_PARIDADE.md), [Cutout](CUTOUT.md),
[Geometry/CNC](GEOMETRY_CNC.md) e [compatibilidade](COMPATIBILIDADE_FLATPRJ.md).
