# Revisão dos trajetos CNC — 2026-10-12

## Controles entregues (série 2/4)

- Geometry CNC > Ordem dos trajetos: Original, Proximidade ou Terminar cada
  placa **por fresa**. Original preserva sequência e entrada; as outras opções
  podem escolher outro vértice de entrada de anéis fechados sem inverter direção.
- Preservar direção dos caminhos abertos: impede a inversão feita para economizar
  deslocamento. Ativar quando a orientação de corte deve prevalecer sobre distância.
- Contornos internos/fechados primeiro: anéis fechados contidos precedem seus pais;
  fechados precedem segmentos abertos com pontes. É opção explícita para recorte,
  não uma inferência universal de usinagem válida para NCC/Paint/Isolation.
- Drilling: otimizar furos por proximidade dentro da ferramenta, partindo da
  posição efetiva após troca. Não elimina repetições nem altera profundidade/slots.

Os defaults anteriores são preservados para projetos/chamadas antigas: sem
otimização de furos e roteamento NEAREST legado, incluindo inversão de abertos.
As novas escolhas são explícitas e persistidas/restauradas no `.fcnproj`.
Configurações avançadas que a exportação Python não consegue representar podem
continuar sendo recusadas; não prometer equivalência do formato Python por isso.

## Referência para panelização

Selecionar uma Geometry de **área**, Polygon/MultiPolygon, na mesma unidade e
origem, com um polígono por placa (por exemplo a área da borda panelizada).
Não escolher ilhas de cobre. A referência é capturada no painel e seus polígonos
são salvos como WKT: pode ser reutilizada mesmo se o objeto de referência não
estiver mais na lista. Reabrir o painel para capturar mudanças na referência.

Agrupamento usa caixas desses polígonos, expandidas pelo raio da fresa e pela
margem extra explícita. Trajeto que não cabe inteiro em uma caixa ou cabe em duas
é recusado; não é atribuído a uma placa pelo primeiro ponto ou por um palpite.
Dentro de cada caixa aplica proximidade/prioridade/direção. Termina os caminhos
de uma fresa na placa antes de passar à próxima; não alterna todas as ferramentas
por placa nem elimina trocas físicas. Não garante menor percurso que a ordem global.

## Limites remanescentes

- Não reconstrói contornos internos abertos nem restaura semântica perdida após
  edições/uniões arbitrárias. A prioridade fechados/abertos requer revisão do usuário.
- Não otimiza slots juntamente com furos, custo real dos desvios Around/Over ou
  ótimo global TSP. Os heurísticos são quadráticos e cooperam com cancelamento;
  desempenho em milhões de furos/caminhos ainda precisa de índice espacial.
- Agrupamento por caixas não é classificação pelo contorno exato. Referência de
  área errada continua errada; unidades/origem, distância entre placas e margem
  devem ser conferidas. Nenhum raster do Plot entra no cálculo.
- Não redefine automaticamente Climb/Conventional. Preservar direção conserva
  a orientação já existente na Geometry. Perfis laser/plotter/Roland/sonda
  mantêm seus caminhos próprios e não aceitam estas opções avançadas de fresagem.
- Não certifica fixação/segurança física. Não foram executadas máquinas reais.

## Testes

CncRoutingTest: entrada de anel, direção, interno/exterior, grupo versus distância
global, referência ambígua/fora de área, cancelamento, furos repetidos e G-code real.
GeometryPerToolProjectTest: referência/política sobrevivem ao save/reopen nativo.
CncRoutingFieldsTest: seletor de unidades, referência salva e envio do painel real.
Regressões dos painéis, estacionamento e auditoria de tooltips reexecutadas.
Logs: target/series-2-routing-20261012.log e target/series-2-final-20261012.log.
