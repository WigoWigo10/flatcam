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

- Dez receitas de Drilling por unidade (incluindo duas ferramentas e cadeias
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

## Próximas etapas desta série

2. Revisão antes de salvar rascunhos, Calibration e SolderPaste.
3. Comparação com mais pós-processadores, modelando comandos adicionais
   explicitamente; comandos não suportados devem continuar recusados.
