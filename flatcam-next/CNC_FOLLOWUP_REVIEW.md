# CNC — trajetos, exportações e perfis adicionais

Revisão posterior: Marlin foi commitado em `4fcc2a3a`; ISEL Remote teve correção
de Dwell para ms, término M30 e modelo de M01 atualizado conforme documentação
do fabricante. As conclusões históricas abaixo sobre M01 opcional universal
foram substituídas por [ISEL_PARITY.md](ISEL_PARITY.md). O relato anterior de
Marlin também foi refinado por configuração em [MARLIN_PARITY.md](MARLIN_PARITY.md).

## 1. Investigação por ferramenta — 2026-10-23

Comparador agora publica `toolpathReview`: contagem de cortes dirigidos sem
correspondência (preservando repetições), até três exemplos de cada lado,
pares com sentido oposto, parâmetros solicitados, Z mínimo, feeds, estados de
spindle e distâncias/menor altura dos rápidos XY por ferramenta. Avisos comuns
do trabalho ficam separados dos avisos atribuídos a T1/T2/etc. Distância não
é estimativa de tempo. Reversão é diagnóstico, nunca equivalência aceita.

Reexecução pública `DEFAULT_NO_M6`, 104 casos MM/IN:
49 MATCH_EXECUTION, 19 ROUTING_DIFFERENT, 34 MACHINING_DIFFERENT,
2 ORACLE_ERROR, 0 FX_ERROR. Auditoria FX sem problemas de parâmetros.
Strict continua reprovado (exit 1), sem alterar tolerâncias/geradores legados.
Relatório: `target/cnc-path-review-20261023/index.html`.

Conferência dos casos diferentes, repetidos em MM/IN:

- Nove receitas de Drilling por unidade (incluindo duas ferramentas e cadeias
  de rasgos) têm os mesmos cortes dirigidos, mas em outra ordem. Não copiar
  a ordem Python só para melhorar o placar; os deslocamentos são mensurados.
- Quatro receitas Geometry por unidade têm segmentos em sentidos opostos:
  peck aberto/fechado, anel alternado e LineString fechado mesma direção.
  A escolha explícita de passes FX não é normalizada para escondê-la.
- Point retraído executa mergulhos intermediários; o Python visita só Z final.
- Dois casos por unidade com rasgos fresados no FX continuam operações
  distintas das cadeias de furos Python.
- RPM zero não comanda partida no FX. Python emite M3 S0 e perde a pausa
  solicitada neste controle. Isso não deve ser copiado.
- Auditor também mantém os defeitos Python de altura de troca e Over
  sobreposto. Os dois ORACLE_ERROR são dwell zero no gerador original.

Não foi identificado defeito adicional do gerador FX neste corpus. Não é
prova para todos os projetos, otimização global nem segurança de máquina.
30 regressões auxiliares Python passaram, incluindo sentido oposto, mudança
de feed, passe perdido, ordem diferente e evidência de altura dos rápidos.

## 2. Revisão dos exportadores restantes — 2026-10-23

Rascunho do editor, Calibration, SolderPaste e Salvar como CNC Job agora usam
o mesmo fluxo: preparar/analisar em worker, revisar na UI, escolher destino,
revalidar origem/época e publicar o texto exato via temporário/rename atômico
quando suportado. Cancelar/recusar antes da publicação preserva o destino.
Salvar rascunho não aplica alterações ao CNC Job. Falhas da prévia posterior
não são relatadas como perda do arquivo que já foi salvo.

SolderPaste também gera programa e prepara geometria visual em worker, com
cancelamento cooperativo na seleção de caminhos e emissão. Nenhum CNC Job é
inserido antes de salvar. Mudança da Geometry impede publicar uma prévia velha.
Calibration avisa sobre pontos/origem/altura; Paste_1 avisa que M3/M4 comandam
dispensação/reversão, não fresagem. A tabela de programas existentes mostra
"Z mínimo (prévia)", não promete Cut Z validado nem infere feeds constantes.
Prévia indisponível continua aviso explícito, não certificado de segurança.

Regressões: CncReviewedFileTest, MainReviewedExportTest (inclui entrada real do
editor, recusa e origem alterada durante análise/seletor), SolderPasteTest,
CalibrationTest, CncExportReviewTest, GCodeEditorControllerTest, TerminalPanelTest
e TclLiveHostTest. 111 passaram, um skip opcional, zero falhas/erros em
`target/cnc-export-followup-final2.log`. Testes de fluxo injetam decisão/destino
e não gravam preferências reais. Diálogo/seletor nativos ainda exigem teste
visual manual; testes não simulam a máquina.

## 3. Mais pós-processadores no comparador — 2026-10-23

`compare-cnc-parameters.ps1 -Profile` acrescenta MARLIN, LINE_XYZ e ISEL_CNC aos
quatro perfis anteriores. Continua executando as classes originais Python com
SHA-256 conferido antes/depois, sem corrigir algoritmos ou esconder exceções.

- Marlin: o leitor assume explicitamente unidades/minuto nativas (o cabeçalho
  legado não emite G94). M400 é evento de sincronização, não linha ignorada.
  O feed de G0 é registrado/comparado e auditado contra Feed rapids solicitado.
  Falta de feed ou valor errado é acusado; não é normalizado para concordar.
- ISEL_CNC: G71 significa MM, M01 é evento de parada **opcional** distinto de
  M0. Isso não garante que o controlador parará. M6 permanece evento opaco,
  sem simular movimentos internos da macro. FX recusa IN, e as 52 receitas IN
  excluídas são declaradas no relatório, não contadas como equivalências.
- line_xyz: eixos explicitamente iguais não criam movimentos fictícios;
  deslocamento simultâneo/posição errada continua visível. A classe original
  falha neste ensaio com `end_xy` ausente, coordenadas não definidas e dwell
  zero. Os 104 ORACLE_ERROR não são aprovação, nem justificativa para alterar
  o código legado. O perfil permanece uma limitação comparativa conhecida.

O corpus agora exporta Feed rapids efetivo nas unidades de origem. Zero na
configuração FX significa automático (1500 mm/min ou equivalente em IN), não
F0. A primeira rodada Marlin detectou essa falha do próprio harness: ao passar
zero ao Python gerava F0. Corrigida apenas a configuração enviada pelo ensaio,
não o preprocessor/algoritmo; rodada inicial preservada em `target/cnc-marlin-20261023`.

Resultados finais (execução igual / rota diferente / usinagem diferente /
erro Python / erro FX):

| Perfil | Casos | Resultados | Auditoria de parâmetros FX |
| --- | ---: | --- | --- |
| MARLIN | 104 MM/IN | 0 / 68 / 34 / 2 / 0 | Sem problemas |
| ISEL_CNC | 52 MM | 0 / 34 / 17 / 1 / 0 | Sem problemas |
| LINE_XYZ | 104 MM/IN | 0 / 0 / 0 / 104 / 0 | Sem problemas; sem comparação válida Py |
| GRBL_11_NO_M6 (regressão) | 104 MM/IN | 49 / 19 / 34 / 2 / 0 | Sem problemas |

Marlin Python emite alguns rápidos de troca sem Feed rapids (os 102 programas
que puderam ser lidos têm ao menos um feed divergente/ausente); FX emite o
feed em todos os G0 modelados. Também há diferenças M400 e falhas anteriores
de troca/Over. ISEL Python não posiciona a troca nas alturas/XY solicitadas
neste corpus. Não copiar as falhas para aumentar MATCH_EXECUTION.

Artefatos em `target/cnc-marlin-effective-20261023/python`,
`target/cnc-isel-20261023/python`, `target/cnc-line-xyz-20261023/python` e
`target/cnc-grbl-regression-20261023/python`. Todos strict exit 1 esperado;
nenhuma tolerância ou regra de equivalência foi relaxada. 33 regressões Python
passam, incluindo M400/feed, G71/M01, eixos simultâneos e comandos fora do perfil.
Laser/FAN, Repetier, Berta, ICP/HPGL/Roland e sonda continuam fora deste leitor;
o suporte de geração já existente não significa validação por este ensaio.

## Limites remanescentes

Testar os diálogos/seletor nativos visualmente; ensaiar receitas/projetos mais
variados; validar demais famílias de controladores; investigar line_xyz no
aplicativo Python real antes de decidir como compará-lo. Não houve ensaio
físico, mudança em projetos privados/preferências reais nem push.

## Fechamento da série

Etapas 1 e 2 commitadas respectivamente em `11f1ab93` e `e46df638` antes de
iniciar a seguinte. Verify completo final aprovado: 1745 registros, 1727
passaram, 18 skips opcionais, zero falhas/erros. Log:
`target/cnc-three-step-full-20261023.log`. 33 regressões Python também passam.
Comparadores diferenciais strict seguem reprovados pelas diferenças descritas;
build verde não transforma essas diferenças em equivalência aprovada.
