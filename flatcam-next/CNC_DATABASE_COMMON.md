# Tools Database → configurações comuns do CNC

Implementado em 2026-10-07. Complementa a transferência de corte por ferramenta
documentada em [FLUXO_PRINCIPAL.md](FLUXO_PRINCIPAL.md).

## Fluxos e campos

Isolation e NCC preservam sugestões da DB na Geometry de saída por índice
efetivo de ferramenta. Milling de furos/rasgos e Cutout também as preservam;
Cutout Thin transfere para as duas Geometries. M-Bites continua sendo Excellon
para um trabalho de Drilling separado, sem herdar opções de fresagem.
O painel Geometry → CNC também aceita aplicação direta da base.
Drilling usa Search DB e considera só as ferramentas selecionadas para gerar.

| Campo comum | Chave Milling/Isolation/NCC/Cutout | Chave Drilling |
|---|---|---|
| Preprocessor | `ppname_g` | `tools_drill_ppname_e` |
| Feed rapids | `feedrate_rapid` | `tools_drill_feedrate_rapid` |
| Tool change | `toolchange` | `tools_drill_toolchange` |
| Start Z | `startz` | `tools_drill_startz` |
| End Z | `endz` | `tools_drill_endz` |
| End X,Y | `endxy` | `tools_drill_endxy` |
| Tool change Z | `toolchangez` | `tools_drill_toolchangez` |
| Tool change X,Y | `toolchangexy` | `tools_drill_toolchangexy` |

São consumidas somente chaves presentes no JSON. Campo ausente não limpa o
rascunho atual; `None`, vazio ou `null` explícito nas posições significa
automático. Zero numérico não é ausência. Coordenadas aceitam pares JSON,
`(x,y)`, `x,y` ou `x;y` (este último também aceita vírgula decimal).
Não há conversão implícita de unidades: confira mm/in do trabalho e da base.

Perfis usam os nomes registrados no FX e os nomes legados Python equivalentes.
Perfis desconhecidos são recusados, não substituídos pelo padrão. Drilling
recusa perfis laser/plotter. Não é suporte a plugins Python arbitrários.
Rapid feed é preservado mesmo se o perfil não emite F em G0; o uso no programa
continua sendo determinado pelo perfil. Posições, clearance, sondagem e
limites do controlador continuam sujeitos às validações já existentes.
Drilling com End Z automático usa o maior Travel Z selecionado.

Algumas dessas chaves são dados legados sem controles na Tools Database,
assim como no editor Python. Este incremento não acrescenta controles globais
ao editor da base; a revisão/edição acontece nos painéis de geração CNC.

## Concordância e conflitos

Valores canonicamente iguais são aplicados automaticamente por campo.
Uma ferramenta sem a chave não veta outra que a forneça. Se duas ferramentas
discordarem, o campo conflitante não é sobrescrito e a geração fica bloqueada.
O painel informa o campo e os números das ferramentas; revise os campos comuns
e clique **Usar valores comuns exibidos** para escolher os valores do trabalho.
Isso não altera os cortes individuais. Nova importação da DB exige nova revisão.

No Drilling, selecionar uma broca sem conflito permite gerar só com ela;
selecionar novamente ambas restaura o conflito enquanto não confirmado.
Mudar origem ou restaurar padrões limpa as sugestões de Drilling. Confirmar
valores comuns escolhe o trabalho, não uma política permanente da base.

## Salvar e reabrir

O `.fcnproj` salva sugestões por ferramenta ainda antes de gerar CNC, inclusive
conflitos. Copiar/panelizar conserva índices; Join multi-Geometry os reindexa.
Projetos nativos antigos sem esses metadados continuam abrindo.

Após geração CNC bem-sucedida pela UI, as sugestões pendentes são substituídas
pelos valores efetivos revisados de perfil, rapids, troca e posições. Esses
valores permanecem ao reabrir. Cancelar/falhar não confirma a operação.
O Drilling salva configurações comuns após geração bem-sucedida, como antes;
seu rascunho/importação não submetido não é persistido.

Exportar `.FlatPrj` com sugestões pendentes de Geometry é recusado, mesmo se
concordantes: o formato Python não representa a revisão pendente. Salve
`.fcnproj` ou revise/gere o CNC antes de exportar. Nada é descartado em silêncio.
Comandos Tcl de geração com parâmetros explícitos continuam seu contrato
anterior; este incremento não implementa consumo da DB pelo Terminal.

## Validação e pendências

Testes de modelo cobrem ausência/None/zero, normalização, valores inválidos,
conflitos por campo, perfis Drilling e persistência/compatibilidade nativa.
Testes offscreen usam controles reais: bloqueio/confirmação, seleção de brocas,
troca de origem, IN e rapids desabilitados. Integração MainWindow testa
Isolation/NCC/Milling em MM/IN, índices efetivos, salvar/reabrir e geração/prévia.
Isolation verifica igualdade do G-code após reabrir valores revisados. Cutout
testa a passagem da DB pelo resultado do painel; Join testa reindexação.

Verificação completa: `mvnw.cmd -q verify`, 1455 registrados, 1443 aprovados,
12 opcionais ignorados, zero falhas/erros. Probes nativos offscreen aprovados
em Direct3D/Intel Arc e software forçado; não são ensaio de fluidez ou máquina.

Roteiro manual: carregar duas fresas com perfis/rapids diferentes; gerar
Isolation ou NCC; salvar/reabrir `.fcnproj` antes do CNC; revisar o conflito,
confirmar e gerar; reabrir novamente e conferir perfil/posições. Repetir
Drilling com uma e depois duas brocas selecionadas, e Cutout Thin com a base.

Ainda pendentes: validação manual com a base/projeto real, consumo explícito
pelo Tcl e outras opções globais (por exemplo, sondagem e exclusões da DB).
Nenhuma mudança no algoritmo CAM, na GPU ou garantia de segurança física CNC;
prévia não simula macros M6 nem substitui conferência e ensaio a seco.
