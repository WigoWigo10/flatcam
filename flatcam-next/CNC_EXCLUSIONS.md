# Áreas de exclusão CNC (Geometry e Drilling)

No painel Geometry → CNC Job, abra **Áreas de exclusão CNC**. Desenhe retângulo
ou polígono no plot (Esc cancela), ou informe um retângulo por coordenadas.
Selecione uma linha para editar Strategy/Over Z e clique Aplicar estratégia.
Ativar exclusões usa as áreas no trabalho; desmarcar conserva o rascunho, sem
usá-lo. Áreas e ativação são comuns a todas as ferramentas do objeto. A configuração
da última geração bem-sucedida pode ser salva em `.fcnproj`; fechar um formulário
sem gerar não salva seu rascunho automaticamente.

## Drilling

Abra **Drilling Tool**, escolha o Excellon e expanda **Áreas de exclusão CNC**,
na seção de parâmetros comuns. É o mesmo editor de áreas usado em Geometry:
retângulo/polígono desenhado no Plot (Esc cancela), retângulo numérico, seleção
com destaque, edição de Strategy/Over Z e exclusão da área. Os botões usam os
ícones originais e acompanham o tema.

Ao gerar, **Around** desvia os movimentos entre furos/slots em XY; **Over** sobe
antes de atravessar e só volta a Travel Z no destino. A validação inclui a
saída inicial, retornos entre passes de slots, mudanças de ferramenta e
estacionamento final. No estacionamento, não baixa para End move Z abaixo de
Travel Z antes do deslocamento XY. Sem Tool change X,Y, a troca continua na
posição atual: uma broca nova mais larga deve caber também na posição final
da anterior. Com posição explícita, o trajeto usa a broca instalada e o destino
precisa acomodar ambas; a prévia inclui esse deslocamento. Tool change Z não
pode ficar abaixo do maior Travel Z quando há exclusões ou posição de troca.

### Start Z e posição de troca

Em **Common Parameters**, Start Z aceita `None` (comportamento anterior) ou
uma altura inicial finita >= 0. Antes de qualquer XY, retorna a Travel Z;
Start Z não substitui a altura de deslocamento nem a altura de todas as trocas.
Tool change X,Y aceita `None` (posição atual) ou `X;Y`/`X,Y`; use ponto decimal
com vírgula separadora, ou ponto-e-vírgula quando houver vírgulas decimais.
Valores estão nas unidades do Excellon (MM/IN), não são convertidos implicitamente.

Posição explícita exige Tool change ativado ou seleção automática de ferramenta
(ICP); vale também para a primeira ferramenta do perfil portable. Spindle para
antes de viajar, sobe a Tool change Z, aplica Around/Over, troca/pausa e restaura
G90/Travel Z. Roland e Mach3 com sonda recusam estes dois campos opcionais;
limpe-os com `None` ao mudar para esses perfis. Não são ignorados silenciosamente.
Macros e movimentos internos de troca dependem da máquina e não são simulados.

Os campos são restaurados por objeto após geração bem-sucedida e salvamento
`.fcnproj` JSON/XZ. Reset os limpa; trocar a fonte não herda as posições anteriores.
Arquivos nativos antigos continuam com valores ausentes (`None`) e G-code anterior.
Exportação `.FlatPrj` sem exclusões escreve também as chaves comuns Python em
`options` e `tools.data`, além do snapshot FX. A reabertura direta no FX preserva
as posições; regeneração pela UI Python pode aplicar suas preferências globais.
Depois de o Python salvar novamente/remover os metadados FX, a importação das
posições comuns de Drilling ainda é parcial. Não é um round-trip universal.

Furos são testados com seu raio; slots com todo o segmento, não só os extremos.
Qualquer interseção com a área ampliada recusa o trabalho inteiro, inclusive
em Over. Não se remove um furo silenciosamente nem se trunca um slot. Só as
ferramentas selecionadas participam; exige diâmetro real positivo, sem usar o
diâmetro fictício da prévia para decidir segurança. MM e IN são suportados.

Geração, rotas, prévia e gravação rodam no worker. Progresso percentual conta
furos/slots validados e gerados e linhas lidas pelo parser, por fase; trabalho
sem fração conhecida fica indeterminado. Há cancelamento cooperativo; uma
chamada JTS ou escrita individual não é interrompida no meio. A origem/projeto,
nome, defaults e estado de edição são revalidados antes de publicar. O arquivo
existente é substituído só após preparar um temporário, com tentativa de rename
atômico e fallback. Cancelar depois da publicação não desfaz o arquivo; a
revalidação FX e o rename no worker não são uma transação indivisível.

Trocar o Excellon restaura as áreas daquele objeto e cancela o desenho atual;
Reset Tool limpa o rascunho de exclusões. Desativar conserva a lista. Ao reabrir
a ferramenta, a última configuração gerada/salva é restaurada. Diferente do
armazenamento global do Python (`app.exc_areas`), as áreas FX pertencem ao objeto:
não são compartilhadas automaticamente com outros Excellons/Geometries.

Referência local: `appTools/ToolDrilling.py` (`check_intersection` e painel
Exclusion Areas), `camlib.py` e `appCommon/Common.py` (`ExclusionAreas`). O FX
reutiliza a política conservadora já documentada de Geometry, não promete
igualdade de rotas/ordem com o legado nem validação física.

## Regras e limites comuns

- **Around**: menor rota do grafo de visibilidade em XY, contornando a união das
  regiões. Mantém altura segura, inclusive retornos entre passes, troca e fim.
- **Over**: sobe antes de XY para o maior Over Z necessário em todo o deslocamento;
  nunca baixa Travel Z. Só retorna à altura segura no destino fora da exclusão.
  É mais conservador que o Python, que pode subir somente perto da entrada.
- As regiões são ampliadas pelo raio da ferramenta + 0,1 mm (ou equivalente em in),
  como o legado. Em Geometry, troca com posição explícita usa o maior diâmetro
  no trajeto; em Drilling, usa a broca instalada no trajeto e ambas no destino.
  Cortes que atinjam essa área ampliada são recusados, não truncados.
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
Em Drilling, Roland e Mach3 com sonda também recusam exclusões ativas.

Projetos Python com essas configurações **não são exportados**: o armazenamento
global de exclusões legado não tem mapeamento de projeto seguro implementado.
Use `.fcnproj` para conservar áreas e exporte o G-code gerado. Não é round-trip
universal nem equivalência total. Validação visual/física manual pendente.
