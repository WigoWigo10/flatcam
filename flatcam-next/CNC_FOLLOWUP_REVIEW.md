# CNC — trajetos, exportações e perfis adicionais

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

## Próxima etapa desta série

3. Comparação com mais pós-processadores, modelando comandos adicionais
   explicitamente; comandos não suportados devem continuar recusados.
