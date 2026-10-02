# Áreas de exclusão CNC (Geometry)

No painel Geometry → CNC Job, abra **Áreas de exclusão CNC**. Desenhe retângulo
ou polígono no plot (Esc cancela), ou informe um retângulo por coordenadas.
Selecione uma linha para editar Strategy/Over Z e clique Aplicar estratégia.
Ativar exclusões usa as áreas no trabalho; desmarcar conserva o rascunho, sem
usá-lo. Áreas e ativação são comuns a todas as ferramentas e persistem em `.fcnproj`.

- **Around**: menor rota do grafo de visibilidade em XY, contornando a união das
  regiões. Mantém altura segura, inclusive retornos entre passes, troca e fim.
- **Over**: sobe antes de XY para o maior Over Z necessário em todo o deslocamento;
  nunca baixa Travel Z. Só retorna à altura segura no destino fora da exclusão.
  É mais conservador que o Python, que pode subir somente perto da entrada.
- As regiões são ampliadas pelo raio da ferramenta + 0,1 mm (ou equivalente em in),
  como o legado. Troca com posição explícita considera o maior diâmetro das duas
  ferramentas. Cortes que atinjam essa área ampliada são recusados, não truncados.
- Origem/destino dentro de exclusão são recusados, mesmo em Over. A posição inicial
  XY do gerador continua sendo (0,0); configure a origem física antes de executar.

Disponível apenas em fresagem sem sonda. Laser, HPGL, Roland e sondagem recusam
exclusões ativas. Nada é ignorado ao mudar de perfil. Prévia XY contém os desvios;
não é visualização 3D da altura Over Z e não simula movimentos internos de macros.

Limites: até 100 áreas, 512 pontos por polígono e 510 vértices combinados no grafo
quando um desvio for necessário. Cancelamento, falha ou rota inexistente não gera
arquivo parcial. Uma operação JTS em andamento ainda não é interrompida de imediato.

Não modela haste, cabeçote, limites da máquina ou a altura real dos grampos. Over Z
deve superar a fixação com folga e caber no curso da máquina. Teste a seco necessário.
Drilling ainda não possui este painel; por enquanto é específico de Geometry/CNC.

Projetos Python com essas configurações **não são exportados**: o armazenamento
global de exclusões legado não tem mapeamento de projeto seguro implementado.
Use `.fcnproj` para conservar áreas e exporte o G-code gerado. Não é round-trip
universal nem equivalência total. Validação visual/física manual pendente.
