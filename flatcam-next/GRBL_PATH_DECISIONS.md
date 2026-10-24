# Decisões sobre os trajetos GRBL — 2026-10-24

Primeira das três etapas sobre `c52029c1`. Reexecução independente dos perfis
`GRBL_11_NO_M6` e `GRBL_11`: **104 receitas MM/IN por perfil**, cada um com
49 execuções iguais, 19 rotas diferentes, 34 usinagens diferentes, 2 erros do
Python e zero erros FX. Nenhum desvio dos parâmetros solicitados ao FX.
Strict permanece reprovado. Fontes Python verificadas por hash, sem alterações.

## As 34 diferenças de usinagem, por receita

Cada linha abaixo ocorre em MM e IN; não são 34 defeitos do FX.

| Receitas por unidade | Quantidade | Evidência e decisão |
| --- | ---: | --- |
| Drilling nearest, peck-nearest, duas ferramentas nearest e seis variantes de cadeia de rasgos | 9 | Mesmos cortes dirigidos por ferramenta, com repetições, em outra ordem. Manter a opção de proximidade do FX; não normalizar como execução igual. |
| Geometry peck aberto/fechado, anel alternado e LineString fechado na mesma direção | 4 | O relatório encontra segmentos em sentido oposto; as políticas de retração/continuidade/direção estão explícitas. Manter a escolha do usuário e sua auditoria; não inverter automaticamente para copiar o legado. |
| Geometry Point com retração | 1 | FX executa os mergulhos intermediários pedidos em Multi-Depth; o Python visita só o Z final. Manter os passes pedidos. |
| Rasgo lateral single/peck | 2 | FX fresa lateralmente; o Python gera cadeia de furos. São operações diferentes. Para cadeia de furos há a opção explícita Drill slots; não substituir uma pela outra. |
| Drilling RPM zero | 1 | FX não comanda partida automática; o legado emite M3 S0 e não conserva a pausa solicitada neste controle. Não copiar esse comportamento. |

Total: 17 receitas × duas unidades = 34. Nenhum desses grupos deve voltar a
ser anunciado como uma correção genérica pendente sem nova evidência de
defeito, passe perdido, avanço incorreto ou rota inválida.

## As 19 diferenças restantes de execução

- Oito receitas por unidade têm cortes concordantes e diferenças de troca/
  deslocamento: posições de Drilling/Geometry, Around, Over sobreposto e
  quatro variantes de Geometry multi-tool/multi-path. A auditoria preserva
  os defeitos Python de altura de troca e Over abaixo da altura solicitada.
  Nos demais, as diferenças de trajeto entre cortes continuam visíveis.
- Três receitas adicionais em IN (`drilling-over`, `drilling-over-separated`
  e `geometry-over`) diferem na precisão do XY intermediário de Over: por
  exemplo, FX 0,133858 in versus Python 0,1338 in. Não reduzir a precisão
  emitida pelo FX nem aumentar tolerâncias para produzir igualdade.
- Os dois erros do Python são Dwell zero, um em cada unidade. Não contam
  como equivalência nem são corrigidos modificando as fontes do oráculo.

## Deslocamentos medidos

Distância XY dos rápidos nestes controles MM, sem inferir tempo de máquina:

| Receita | FX (mm) | Python (mm) |
| --- | ---: | ---: |
| Furos nearest, com/sem peck | 14 | 22 |
| Duas ferramentas nearest | 30 | 48 |
| Cadeia de rasgo single/peck/nearest/dwell | 12 | 13,25 |
| Cadeia sem último furo | 11,5 | 12,25 |
| Cadeia sem sobreposição | 12 | 13 |

Isso demonstra benefício nesses controles, não uma solução global de TSP
nem obrigação de terminar uma placa antes da seguinte. Geometry possui a
política explícita de agrupar por placa; a otimização de Drilling atual é
proximidade global. Uma nova política de Drilling por placa seria uma função
separada, não correção presumida de todos os casos acima.

## Evidência e limites

Relatórios:
`target/three-steps-grbl-grbl_11_no_m6-20261024/python/index.html` e
`target/three-steps-grbl-grbl_11-20261024/python/index.html`.
Cada receita inclui auditoria dos parâmetros, cortes dirigidos por ferramenta,
pares invertidos, amostras sem correspondência e métricas dos rápidos.

Regressões executadas: `GeometryPerToolTest`, `GeometryPassStrategyTest`,
`GeometryClosedPassStrategyTest`, `DrillSlotsTest`, `CncRoutingTest`.
23 aprovações, zero falhas/erros; log `target/three-steps-grbl-tests-20261024.log`. Nenhuma mudança do gerador
GRBL foi necessária nesta etapa. A revisão não cobre todos os projetos e
não certifica segurança física ou equivalência completa com o legado.
