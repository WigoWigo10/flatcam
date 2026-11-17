# FlatCAM Evo como referência de evolução — 2026-11-17

Avaliação inicial solicitada após a Beta 8. Nenhum recurso do Evo foi integrado
ao FX nesta etapa, e os arquivos Python usados pelos comparadores foram mantidos.

## Referência preservada

- Repositório: https://github.com/MariusAdrian88/FlatCAM_Evo
- Release consultada: `v1.09`, publicada em 15/11/2026.
- Commit: `18049df9d1984e45dab4b6a28a767265384373ff`.
- Cópia Git separada, sem checkout: `target/evo-review-20261117`.
- LICENSE consultado: MIT, com atribuições a Marius Stanciu e Juan Pablo Caram.
  Revisar também os avisos específicos dos arquivos efetivamente reaproveitados.

Fontes primárias: [release 1.09](https://github.com/MariusAdrian88/FlatCAM_Evo/releases/tag/v1.09),
[README](https://github.com/MariusAdrian88/FlatCAM_Evo/blob/v1.09/README.md),
[licença](https://github.com/MariusAdrian88/FlatCAM_Evo/blob/v1.09/LICENSE).

## Primeiros candidatos

| Área do Evo | Avaliação para o FX |
|---|---|
| Direção Climb/Conventional por fresa em NCC/Paint | Prioridade alta. O `NccToolSettings` atual não possui esse parâmetro. Conferir ilhas, mirroring, Connect, ordenação CNC e múltiplas profundidades. Código puro em `appPlugins/Common/clearing/direction.py`. |
| Exclusões vinculadas ao objeto | Avaliar persistência, transformações e uso em Isolation/NCC/Paint. O FX já tem exclusões CNC; não confundir esses domínios. O Evo declara que Panelize não replica as zonas: decidir explicitamente o comportamento desejado no FX. |
| Importação direta de placas CAD | Há parsers como KiCad PCB, Eagle e EasyEDA Pro. Portar um formato por vez, com corpus e comparação de cobre/furos/unidades. Não executar os parsers baixados nesta auditoria. |
| Comandos Tcl e novos preprocessadores | Inventariar diferenças reais antes de portar; o FX já implementa parte desses recursos. |
| Documentos e atualizador | Evoluções de produto para avaliar separadamente do núcleo CAM. |

## Compatibilidade e integração

Evo é Python/PyQt6; o FX é Java/JavaFX. A integração deve acontecer por recursos,
algoritmos e contratos testados. Um merge de código Python não porta sua interface
ou serviços para Java. Preservar as correções próprias e os métodos experimentais
já medidos no FX; comparar os mesmos arquivos e parâmetros em cada candidato.

A release 1.09 mudou a representação de Geometry/CNCJob e removeu as distinções
single/multi-geometry e single/multi-tool. A compatibilidade `.FlatPrj` atual do
FX não foi validada com esse esquema: criar testes próprios antes de prometer
intercâmbio com o Evo. Manter a referência Python antiga separada para não mudar
silenciosamente os oracles de paridade existentes.

Próxima etapa sugerida: inventário por versão/arquivo com situação no FX,
dependências e regressões necessárias; primeira implementação candidata é a
direção de corte NCC/Paint. A falha multitela e sua correção posterior estão
registradas separadamente em `MULTI_MONITOR_REVIEW_20261117.md`.
