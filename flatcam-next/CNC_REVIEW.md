# Revisão antes de salvar CNC — 2026-10-13

## Programa existente e cobertura transportada — 2026-10-14

Salvar como de CNC Job passa por revisão assíncrona antes do FileChooser e
publicação atômica, revalidando versão/cancelamento. Relatório é extraído do
programa/statísticas, não dos rascunhos: campos não inferidos ficam Consultar
código, sem links de edição falsos. Terminal mostra resumo separado do retorno
export_gcode/write_gcode, incluindo pre/postâmbulos, e revalida antes de publicar.
Isso não simula F/S/Z modal completo/macros nem impede toda gravação de rascunho
ou saída de ferramenta auxiliar. Nenhum diálogo é aberto em automações Tcl.

Cobertura passa a usar alvos conhecidos NCC e PAINT, mantidos em affine,
cópia e painel; escala não escala fresa e recalcula a área residual. Buffer/
edição arbitrária invalidam referência. Perfis laser/plotter/sonda não oferecem
o botão de cobertura de fresagem. Geometry genérica/antiga permanece desconhecida.
Evidência e limites: [CNC_SIX_STEP_REVIEW.md](CNC_SIX_STEP_REVIEW.md).

## Incremento posterior: Drill slots — 2026-10-14

Detalhes por broca mostram operação dos rasgos (furos sobrepostos ou fresagem),
overlap e último furo. Aviso para Drill slots explica que círculos podem deixar
material e não equivalem à fresagem integral; link abre `drill-slots`.
Não mantém o aviso de corte lateral quando a ferramenta vai apenas mergulhar.
Os números Furos / rasgos no resumo são da origem, não o total convertido;
conversão/validação final ocorre no worker antes de gravar. Prévia continua 2D.

## Incremento posterior: passes contínuos fechados

Estratégia exibe também **Contínua, mesma direção (contornos fechados)**.
Resumo/tooltip explicam a restrição de caminhos abertos; não acusa alternância
Climb/Conventional nesse modo. A validação geométrica ocorre no worker antes de
gravar, inclusive após compensação. Pontos usam Z final nos modos contínuos.
Corpus e limites: [CNC_PARAMETER_PARITY.md](CNC_PARAMETER_PARITY.md).

## Incremento posterior: política de altura e acionamento manual

Geometry permite escolher altura global (padrão) ou individual por fresa.
Resumo mostra política, alturas efetivas e estacionamento da última fresa
ativa, com link para `cnc-travel-clearance`. RPM zero explicita acionamento/
parada manuais e que não garante M5 final. Limites e oito controles diferenciais
novos equivalentes: [CNC_PARAMETER_PARITY.md](CNC_PARAMETER_PARITY.md).

## Incremento posterior: Over e Start Z

Novo aviso distingue Start Z da altura da primeira troca, com link para Tool
change Z. Over explica subida/descida fora da região ampliada e altura máxima
por trecho quando áreas se sobrepõem. Tooltips de Strategy e Over Z atualizados.
Mudança do gerador e comparação: [CNC_PARAMETER_PARITY.md](CNC_PARAMETER_PARITY.md).

## Incremento posterior: Travel Z efetivo

O detalhe por fresa distingue o Travel Z configurado da retração/altura XY
efetiva, que usa o maior Travel Z do trabalho. O gerador agora respeita essa
altura também entre trajetos e retornos de passes, mesmo sem exclusões. A
prévia 2D sozinha não detectava a inconsistência. Regressões e comparação de
46 controles GRBL: [CNC_PARAMETER_PARITY.md](CNC_PARAMETER_PARITY.md).

## Incremento posterior: cobertura nominal NCC

Novos NCC conservam a área-alvo global e individual por CLEAR. O resumo oferece
**Ver cobertura no Plot**, calculado em segundo plano, com resíduos individuais,
acumulados e final em laranja. Voltar mantém os parâmetros revisados. Registros
antigos/genéricos continuam sem alvo e sem inferência de cobertura. Contrato,
invalidação e testes: [CNC_COVERAGE.md](CNC_COVERAGE.md).

## Fluxo da interface

Geometry → CNC e Drilling agora abrem **Revisar CNC antes de salvar** entre
validar o painel e escolher o destino do G-code. Voltar, Esc e fechar a janela
não abrem o seletor de arquivo, não iniciam o worker e não substituem arquivos.
Continuar abre o FileChooser; as validações e a publicação temporária existentes
continuam em vigor depois da escolha do destino.

O resumo é somente de leitura e não gera/bufferiza a geometria na thread FX.
Ele exibe:

- origem, perfil, unidades e ordem efetiva das ferramentas;
- diâmetro, Cut Z efetivo, Travel Z, avanços XY/Z e RPM/potência por ferramenta;
- passes, dwell, compensação e Extra Cut, além dos dados de ponta V;
- posições comuns de início, troca e fim, exclusões e política de ordenação;
- avisos sobre estacionamento em altura maior antes de descer para End Z,
  spindle sem comando automático e compensação adicional;
- furos/rasgos por fresa e quantidade de hits excluídos pela seleção Drilling.

Cut Z aparece com sinal negativo: Drilling inclui Offset Z e a ponta V usa a
profundidade calculada, não o rascunho de Cut Z. O resumo não faz nova conversão
MM/IN nem promete precisão física acima do arredondamento do G-code. Travel Z
individual é a retração configurada; Geometry também informa a altura comum
dos deslocamentos XY, que usa o maior Travel Z do trabalho. No GRBL, a velocidade
G0 é do controlador: o valor de Feed rapids não é apresentado como limite ativo.
Dwell Geometry com spindle não comandado é informado como inativo.

Os parâmetros são links para o campo correspondente no painel. Geometry usa
índice de fresa; Drilling usa o ID Excellon, não a posição visual da linha.
Para editar uma fresa Drilling, a seleção anterior para exportação é guardada
e restaurada no próximo Gerar; o painel informa esse conjunto preservado durante
a edição. Uma seleção manual posterior o substitui explicitamente. Edição e
navegação não copiam parâmetros para todas.
Mudança de projeto/painel durante o diálogo invalida a continuação.

Diâmetros Geometry diferentes sem troca e trabalhos inteiramente vazios
bloqueiam Continuar. Isso é uma proteção da revisão **GUI**, não alteração do
contrato histórico de geração programática/Tcl com troca desativada. Erros de
parâmetros e conflitos da Tools Database ainda são recusados pelo painel antes
de abrir a revisão. A geração também mantém suas próprias validações.

## Limites que continuam

O mapa nominal está disponível para NCC novos com referência preservada, não
para toda Geometry. Objetos antigos/genéricos podem ter perdido a área-alvo;
sem ela não é correto declarar cobertura total. O diálogo mantém
**Cobertura não calculada** nesses casos. Transporte de alvos em transformações
e outros CAM continua pendente; o mapa não simula remoção física de material.

O Plot é uma prévia 2D: não simula remoção de material, grampos, limites da
máquina, sensores, G92 ou movimentos internos de macros M6. A revisão não é
certificação de segurança, nem confirma qual ferramenta está fisicamente montada.
Continuam necessários teste a seco e validação de origem, fixação e alturas.
Este incremento não insere a revisão nas exportações genéricas de um CNC Job
já existente nem no Terminal; os dois fluxos GUI de geração foram integrados.

## Correção CNC encontrada pela comparação

`.9 / .3` podia produzir quatro passadas no FX, porque a soma binária acumulada
ficava imediatamente abaixo da profundidade final. A quarta passada cortava na
mesma profundidade arredondada e invertia novamente o endpoint no modo contínuo.

`MachiningPasses` agora calcula a contagem antes de montar a lista: produtos por
índice, última profundidade exatamente igual à solicitada, correção somente de
ruído de quatro ULPs na razão (não tolerância mecânica). Resto real continua
gerando outro passe; limite de 10 mil passos é explícito. A revisão e o gerador
usam a mesma contagem. Drilling e Geometry compartilham essa correção.

O comparador GRBL ganhou quatro controles multigeo: duas fresas com diâmetros,
profundidades, avanços, RPM e dwell diferentes, single/contínuo × MM/IN. Chama
`CNCjob.geometry_tool_gcode_gen` original com `is_first/is_last`, como o fluxo
multigeo Python, em vez de concatenar programas single-tool já terminados.
Todos os 38 controles anteriores permanecem no corpus. A auditoria também
conta travessias XY nos LineStrings públicos sem compensação: uma passada extra
no mesmo Z já não escapa por não ter outro mergulho efetivo.

Resultado: **42 controles; 22 MATCH_EXECUTION, 12 ROUTING_DIFFERENT,
6 MACHINING_DIFFERENT, 2 ORACLE_ERROR, 0 FX_ERROR**. Os quatro novos controles
concordam na assinatura de usinagem e passam nas auditorias dos parâmetros;
divergem na sequência de deslocamentos/alturas. Strict continua 1; não se
declarou paridade completa nem se alterou o Python/tolerância de coordenadas.
Relatório: `target/cnc-review-oracle-fixed-20261013/python/index.html`.

## Testes e próximos passos

`CncPreflightTest`, `CncReviewDialogTest` e `MachiningPassesTest` cobrem valores
individuais, MM/IN, V-Tip/Offset Z, posições comuns, seleção de ferramentas,
retorno ao campo, cancelamento/continuação, temas, resize e passes redundantes.
Screenshots em `flatcam-fx/target/cnc-review-*.png` com
`-Dflatcam.tests.snapshots=true`. Os 16 testes auxiliares do leitor/auditoria
Python passam; relatórios de builds registram também tentativas que encontraram
regressões locais, sem apagá-las ou confundi-las com validação final.

Build integral aprovado em `target/cnc-review-final-validated-20261013.log`:
**1672 testes registrados, 1654 aprovados, 18 opcionais ignorados, zero
falhas/erros**, considerando somente XML com fontes de teste atuais. Após o
aviso visual sobre a seleção Drilling, a verificação/empacotamento direcionada
está em `target/cnc-review-package-final-20261013.log`. A auditoria de tooltips
inclui os dois resumos: **1090 ocorrências, zero MISSING** no cenário construído.
Layouts claro/escuro foram renderizados pelos testes e inspecionados; isso não
substitui sua validação manual no projeto real.

Próximos: validar o diálogo no projeto real; transportar área-alvo/resíduos CAM
para revisão visual; ampliar controles com múltiplos trajetos/furos/slots e
outros perfis; definir as diferenças de rota restantes antes de alterar políticas
conservadoras do FX. Não copiar comportamentos de máquina só para aumentar o
placar contra o Python.
