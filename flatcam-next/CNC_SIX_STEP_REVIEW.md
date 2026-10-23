# Série de fechamento CNC — 2026-10-14

Execução serial autorizada: seis pontos listados após Drill slots (`b467b9bd`).
Usar projetos públicos/sintéticos e ambientes isolados; não alterar preferências
ou projetos privados. Registrar diferenças deliberadas e defeitos do legado,
sem reduzir a exigência do comparador ou alegar validação em máquina.

1. Verificado no aplicativo real, com restrições explícitas.
2. Testado com rasgos diagonais, duas ferramentas e painel MM/IN.
3. Auditor ampliado: diagnóstico de cortes/ordem por ferramenta, sem mudar strict.
4. Comparação ampliada a quatro perfis XYZ; não a todas as famílias de máquina.
5. Revisão integrada a Salvar como de CNC Job e export_gcode/write_gcode Tcl.
6. Transporte de alvos conhecidos e expansão para Paint implementados/testados.

## 1 — Aplicativo Python real

O primeiro ensaio abriu/salvou sem erro, mas perdeu TODOS os ajustes Drilling:
`ExcellonObject.set_ui` copia `app.options` ao objeto e `build_ui` substitui cada
`tools[id].data` pelas opções comuns. Espelhar somente nos dados por ferramenta
ou no objeto não bastava. Exportação agora escreve parâmetros uniformes nas
três representações: ferramenta, objeto e opções globais. Também importa de
volta perfil/posições/ordem comuns válidos, sem inferir configuração de sonda.

Novos/alterados com configurações diferentes entre brocas/objetos são recusados
antes de gravar `.FlatPrj`; use nativo/arquivos separados. Projetos legados
heterogêneos inalterados continuam preserváveis por identidade/mesclagem, sem
prometer corrigir o comportamento destrutivo do próprio aplicativo Python.

MM e IN abriram/regravaram no aplicativo real, em Qt offscreen e pasta de
preferências isolada. FX recuperou parâmetros, perfil/posições e unidades,
regenerou G-code idêntico e reabriu a cópia nativa. Coordenadas IN comparadas
com 1e-14 por diferenças de um ULP na serialização decimal; não altera o
limite do comparador CNC. Em IN é PRECONDIÇÃO a preferência inicial Python
estar em IN: set_ui também sobrescreve unidades a partir dos defaults.
O harness expõe `--initial-units IN`, registra a condição e não muda defaults
do usuário. Não chamar isso de prova de importação IN com preferência MM.

Artefatos públicos: `flatcam-application/target/slots-live-global-20261014`;
logs `target/slots-live-global-{mm,in}.log`, `target/six-live-final-reopen.log`.
Regressões: DrillSlotsPersistenceTest, PythonProjectWriterTest/PythonLegacyMergeTest.

## 2 e 3 — Rasgos e trajetórias

DrillSlotsTest combina painel 2×2, diagonal, rasgo fresado, furos normais,
duas ferramentas, peck, proximidade e exclusões Over/Around, em MM e IN.
Verifica 64 mergulhos, oito cortes laterais, IDs/dados originais e proteção.
Mantém seis centros na diagonal cujo conversor legado produz apenas as pontas.

`cutDifferenceByTool` distingue ORDERED_CUTS_MATCH,
SAME_CUTS_DIFFERENT_ORDER e CUTS_OR_PARAMETERS_DIFFER. Mantém multiplicidade,
direção, ferramenta, profundidade, feed e spindle. Diagnóstico NÃO muda status
estrito nem certifica a ordem/rapidez de movimentos. Perda de passe não pode
virar "mera ordem". Não foram copiadas rotas mais longas nem bugs do legado.

## 4 — Pós-processadores

`compare-cnc-parameters.ps1 -Profile` aceita GRBL_11_NO_M6 (default), GRBL_11,
DEFAULT e DEFAULT_NO_M6. Cada um compara 104 controles MM/IN com a classe
preprocessadora ORIGINAL selecionada e seu SHA-256, inclusive M0/M6 explícitos.
Resultados de cada perfil: 49 MATCH_EXECUTION, 19 ROUTING_DIFFERENT,
34 MACHINING_DIFFERENT, dois ORACLE_ERROR, zero FX_ERROR. Strict segue 1.
Usinagem igual em outra ordem não é promovida a execução igual. M6 interno,
G31/G92, laser/FAN, Marlin/Repetier, ICP/HPGL/Roland e máquina NÃO são validados
por este leitor; famílias restantes permanecem trabalho posterior.

Relatórios: target/six-cnc-default-no-m6-20261014/python,
target/six-cnc-default-20261014/python, target/six-cnc-grbl-m6-20261014/python,
target/six-cnc-grbl-no-m6-20261014. 28 testes Python do leitor/auditor passaram.

## 5 — Revisão de programas existentes

Salvar como de CNC Job analisa fora da thread FX (reusa stats válidas), mostra
o diálogo antes do destino e publica via temporário/rename, com cancelamento
e revalidação de identidade/revisão do projeto. Campos desconhecidos ficam
"Consultar código", não herdados de rascunhos CAM. Sem links de edição falsos.
Prévia indisponível é AVISO, não aprovação de segurança; programa vazio bloqueia.

Terminal revisa o texto realmente solicitado, incluindo pre/postâmbulo; imprime
um aviso separado sem contaminar o retorno G-code (inclusive atribuições Tcl).
write_gcode revalida a origem antes de publicar. Não abre diálogo em automações.
Isso não cobre ainda todas as gravações de rascunhos soltos, G-code de
Calibration/SolderPaste, validação física ou variação modal completa de F/S/Z.

Regressões: CncExportReviewTest, TerminalPanelTest, TclLiveHostTest. O diálogo
e FileChooser do novo caminho GUI ainda precisam de teste manual.

## 6 — Cobertura conhecida

MachiningTarget transporta área e alvos por ferramenta, confere assinatura dos
trajetos anteriores/transformados e recalcula o vínculo. Offset, rotação,
espelho, skew, escala invertível, cópia e panelização preservam objetivos
conhecidos. Escala não escala o diâmetro real da fresa: cobertura é RECALCULADA,
não se reutiliza o percentual antigo. Panel usa união de áreas replicadas,
sem contar duas vezes regiões sobrepostas. Buffer, edição arbitrária, mudança
de ferramentas/ordem ou vínculo inválido não inventam alvos novos.

Paint agora guarda a área realmente pedida (com margem/Rest/ajustes individuais),
como PAINT; NCC continua NCC. UI mostra cobertura CAM, e nativo JSON/XZ aceita
PAINT. Não infere objetivos de Geometry genérica, Isolation ou Cutout, nem
reconstrói alvos ausentes de projetos antigos. Rotas/posições CNC e referências
de placa não são automaticamente transportadas como se fossem material.

Regressões: MachiningTargetTransportTest (MM/IN, área/coverage escalada,
panel, vínculos inválidos/cancelamento), MainNccCoverageTest (publicação Paint,
nativo e transformação NCC). Prévia continua nominal 2D, não simula máquina.

## Verificação e próximos limites

Verify completo: 1737 registrados, 1719 passaram, 18 skips opcionais,
zero falhas/erros: `target/six-final-full-20261014.log`.
Conferência final de UI/cobertura em `target/six-final-ui-adjustments.log`.
Sem alterações em projetos privados, instalação de dependências ou testes
em máquina. Sem push; novas alterações ainda não commitadas.

Restam: famílias de pós-processadores fora do leitor; projetos/receitas mais
variados; validação visual/manual; seleção/otimização exclusivas FX no legado;
outros exportadores/rascunhos e alvos genéricos. A série reduz seis lacunas,
não declara "100% dos fluxos" nem porcentagem global de paridade.

Nenhuma destas etapas, isoladamente, certifica segurança física ou paridade total.
