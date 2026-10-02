# Transferências da Tools Database

Selecione e clique **Aplicar**: carregar ou selecionar não gera um trabalho nem
substitui parâmetros automaticamente. A base Python não informa uma unidade
inequívoca; confirme MM/IN. Não há conversão automática.

- Milling/Geometry CNC: Offset Path/In/Out/Custom e valor, além de profundidade,
  passes, alturas, avanços, spindle, Dwell, Extra Cut e ponta V já suportados.
  Parâmetros permanecem individuais e Aplicar a todas é explícito.
- Cutout: diâmetro, margem, convexidade, gaps, M-Bites, Cut Z, Multi-Depth,
  Depth per pass e Thin Depth. Como no callback Python, os dados gerais de
  fresagem têm prioridade sobre cópias `tools_cutout_*`. Profundidades Z são
  negativas na interface e positivas internamente. Thin deve ser mais raso.
  As Geometry geradas recebem o perfil, profundidades e demais parâmetros CNC
  da base. Não reaplicam Offset: os caminhos Cutout já têm compensação.
- Paint: acrescenta uma ferramenta ou atualiza a linha do mesmo diâmetro;
  preserva outras linhas e seleção/ordem/Rest. Overlap, margem (inclusive negativa),
  método, Connect e Contour são individuais. Atualizar lista sincroniza os
  diâmetros; Pintar também sincroniza e valida todos os rascunhos. Rest mantém
  maior→menor e subtrai a cobertura anterior da área específica de cada ferramenta.

Thin ainda produz uma Geometry separada, diferente da ferramenta especial no
objeto Python. Gere/revise ambos os CNC Jobs. M-Bites gera Excellon, cuja furação
é configurada separadamente. Paint não transfere o perfil TT ou parâmetros CNC;
V e Laser Lines ainda são recusados nesse fluxo. Opções avançadas não listadas,
como áreas de exclusão CNC e posições de troca da base, não são transferidas.

Testes: MillingDatabaseTest, PaintGeneratorTest, DatabaseTransferTest e
PaintDatabaseTransferTest. Os controles são exercitados sem janela visível;
conferência visual e teste a seco continuam necessários.
