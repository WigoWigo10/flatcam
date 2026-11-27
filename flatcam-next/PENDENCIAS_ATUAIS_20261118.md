# Mapa consolidado de pendências — 18/11/2026

Base examinada: `c99d7ed0`. Escopo: o FX deste repositório, referência Python legada, distribuição Windows atual e evoluções já discutidas.

Este mapa reúne **42 pacotes de trabalho**, não 42 bugs nem 42 funcionalidades ausentes. Distingue implementação, otimização, investigação, decisões de produto e validações. Algumas diferenças do Python são deliberadas. Não há porcentagem global de paridade calculada aqui.

## Sequência recomendada

1. **PERF-01 e PERF-02:** otimizar os gargalos já medidos do Rest e Lines, um por vez, com referência congelada.
2. **CAM-01 / VAL-03 / REL-02:** repetir cobertura, arquivos/projetos e regressão da candidata com os resultados novos.
3. **VAL-02 e REL-01:** completar a conferência multitela e preparar a próxima Beta quando autorizada; a Beta 8 distribuída antecede o reparo DPI.
4. **CAM-02/03 e PAR-01/03/04:** ampliar as lacunas funcionais mais úteis: Paint/DB, Tcl e preferências.
5. **UX-02/03/04:** ampliar ajuda e conferência visual/linguística, junto às implementações.
6. **FUT/RND experimentais:** avaliar GEOS nativo, renderer próprio e recursos Evo com comparação independente, sem migração antecipada.

**VAL-01 corre em paralelo:** aguardar os dados do Yago; os controles atuais não reproduziram o relato. Não atribuir a causa à seleção sem confirmação.

## Como interpretar

- **P1:** fluxo principal, evidência de custo alto, investigação de relato ou validação da próxima entrega.
- **P2:** ampliar paridade, robustez e usabilidade.
- **P3:** evolução opcional ou experimento de menor prioridade atual.
- **Autônomo:** posso avançar com o código e os dados disponíveis. **Misto:** parte autônoma, parte manual/dados de terceiros. **Depende:** precisa de dados ou equipamento/operador.
- **Parcial/Implementar/Otimizar** não equivalem a **Validar/Investigar/Decidir/Experimento**. Cada item mantém essa classificação.

## Resumo por frente

| Frente | Pacotes |
|---|---:|
| Desempenho CAM | 5 |
| Relatos e validação | 3 |
| CAM e CNC | 7 |
| Paridade funcional | 7 |
| Projetos e dados | 4 |
| Interface e ajuda | 4 |
| Plot e renderização | 3 |
| Distribuição e validação | 4 |
| Evoluções futuras | 5 |

## Desempenho CAM

| ID · prioridade · tipo | Trabalho e critério de conclusão | Execução / referência |
|---|---|---|
| PERF-01 · P1 · Otimizar | **Buffers e listas da máscara Rest**. Conclui quando: Reduzir tempo/alocações nos casos congelados, preservando caminhos, raio, tolerâncias, desempates e área restante. | Autônomo. [GEOMETRY_HOTSPOTS_20261118.md](GEOMETRY_HOTSPOTS_20261118.md) |
| PERF-02 · P1 · Otimizar | **Recortes repetidos das linhas em Lines**. Conclui quando: Demonstrar ganho pareado nas Horizon simples/panelizada com recortes, ordem e direção preservados. | Autônomo. [GEOMETRY_HOTSPOTS_20261118.md](GEOMETRY_HOTSPOTS_20261118.md) |
| PERF-03 · P2 · Investigar | **Memória, cancelamento e orçamento de workers em cargas grandes**. Conclui quando: Medir pico/pressão de memória e resposta ao cancelar; coordenar pools sem prometer interrupção instantânea de uma primitiva JTS. | Autônomo. [GEOMETRY_EDITOR.md](GEOMETRY_EDITOR.md) |
| PERF-04 · P2 · Otimizar | **Volume de pontos e custo da Espiral densa**. Conclui quando: Avaliar tamanho do projeto/G-code, desenho e geração mantendo cobertura e precisão; publicar ganhos e perdas. | Autônomo. [SPIRAL_BUDGET_OPTIMIZATION.md](SPIRAL_BUDGET_OPTIMIZATION.md) |
| PERF-05 · P3 · Experimento | **Índice dos extremos do Connect**. Conclui quando: Só integrar se o ganho adicional justificar o custo e os caminhos brutos/desempates continuarem corretos. | Autônomo. [CONNECT_MEASUREMENT_20261118.md](CONNECT_MEASUREMENT_20261118.md) |

## Relatos e validação

| ID · prioridade · tipo | Trabalho e critério de conclusão | Execução / referência |
|---|---|---|
| VAL-01 · P1 · Aguarda dados | **Relato Rest com várias Clear do Yago**. Conclui quando: Obter versão, arquivo/receita e seleção; reproduzir antes de atribuir causa ou corrigir. | Depende do testador. [NCC_REST_MULTI_CLEAR_REVIEW.md](NCC_REST_MULTI_CLEAR_REVIEW.md) |
| VAL-02 · P1 · Validar | **Matriz ampliada de múltiplas telas e DPI**. Conclui quando: Conferir duas larguras salvas, movimentos repetidos, diálogos, Software e desconectar/reconectar monitores. | Misto. [MULTI_MONITOR_REVIEW_20261117.md](MULTI_MONITOR_REVIEW_20261117.md) |
| VAL-03 · P1 · Validar | **Fluxos visuais completos com arquivos variados**. Conclui quando: Percorrer importação, preparo/panelização, CAM, revisão, exportação e reabertura; testar cancelamento/edições concorrentes e seletores nativos. | Misto. [FLUXO_PRINCIPAL.md](FLUXO_PRINCIPAL.md) |

## CAM e CNC

| ID · prioridade · tipo | Trabalho e critério de conclusão | Execução / referência |
|---|---|---|
| CAM-01 · P1 · Validar | **Cobertura real dos caminhos por fresa**. Conclui quando: Ampliar corpus e comparar pegada, resíduos brutos/tolerados, pontes, rasgos e limites físicos do desenho. | Misto. [CLEARING_COVERAGE_VALIDATION.md](CLEARING_COVERAGE_VALIDATION.md) |
| CAM-02 · P2 · Parcial | **Perfil TT e parâmetros CNC da DB no Paint**. Conclui quando: Transportar os dados associados a cada fresa até Geometry/CNC e salvar/reabrir, sem substituir tudo por C1. | Autônomo. [TOOLS_DATABASE_TRANSFER.md](TOOLS_DATABASE_TRANSFER.md) |
| CAM-03 · P2 · Parcial | **Opções avançadas da Tools Database**. Conclui quando: Inventariar/consumir campos restantes, incluindo sondagem/exclusões e uso explícito no Terminal, com unidades confirmadas. | Autônomo. [CNC_DATABASE_COMMON.md](CNC_DATABASE_COMMON.md) |
| CAM-04 · P2 · Parcial | **Parâmetros restantes dos preprocessadores**. Conclui quando: Conferir Start Z separado na sondagem, compensações de mesa, opções individuais e metadados nativos; manter diferenças deliberadas explícitas. | Autônomo. [PREPROCESSADORES.md](PREPROCESSADORES.md) |
| CAM-05 · P2 · Parcial | **Alvos de cobertura após transformar/panelizar Geometry**. Conclui quando: Transportar ou recalcular alvos quando semanticamente válido, preservando invalidação de informações obsoletas. | Autônomo. [CNC_COVERAGE.md](CNC_COVERAGE.md) |
| CAM-06 · P2 · Parcial | **Classificação e reabertura do SolderPaste**. Conclui quando: Distinguir geometrias de dispensa das demais e recuperar a semântica dos jobs; ampliar seleção de fontes conforme contrato. | Autônomo. [PREPROCESSADORES.md](PREPROCESSADORES.md) |
| CAM-07 · P2 · Investigar | **Qualidade da ordem dos trajetos e levantamentos**. Conclui quando: Comparar programas finais em placas/painéis: deslocamentos, entradas e estimativas, sem confundir tempo de cálculo com tempo de máquina. | Autônomo. [CNC_ROUTING.md](CNC_ROUTING.md) |

## Paridade funcional

| ID · prioridade · tipo | Trabalho e critério de conclusão | Execução / referência |
|---|---|---|
| PAR-01 · P2 · Parcial | **Famílias de comandos FlatCAM no Tcl**. Conclui quando: Priorizar paint, panelize, milldrills/millslots, primitivas e abertura/exportação; verificar flags/resultados, não apenas nomes. | Autônomo. [TERMINAL_TCL.md](TERMINAL_TCL.md) |
| PAR-02 · P2 · Decidir | **Dialeto Tcl mais completo**. Conclui quando: Definir compatibilidade desejada para proc/catch/switch/string/array, quoting e listas; ampliar com testes de scripts reais. | Autônomo. [TERMINAL_TCL.md](TERMINAL_TCL.md) |
| PAR-03 · P2 · Implementar | **Preferências gerais de mouse e cursor**. Conclui quando: Expor os controles do legado que fizerem sentido: forma/tamanho/espessura/cor e mapeamento de botões, com efeito real. | Autônomo. [PREFERENCIAS.md](PREFERENCIAS.md) |
| PAR-04 · P2 · Inventariar | **Preferências restantes campo a campo**. Conclui quando: Mapear cada campo Python a um consumidor FX, diferença deliberada ou lacuna; não criar controles Qt/VisPy sem equivalente útil. | Autônomo. [PREFERENCIAS.md](PREFERENCIAS.md) |
| PAR-05 · P2 · Parcial | **Gestos, atalhos e opções avançadas dos editores**. Conclui quando: Conferir Geometry/Gerber/Excellon/G-code contra a referência, gesto por gesto; completar lacunas demonstradas. | Misto. [GEOMETRY_EDITOR.md](GEOMETRY_EDITOR.md) |
| PAR-06 · P2 · Implementar | **Demais idiomas do Python**. Conclui quando: Adicionar Alemão, Italiano, Romeno, Russo e Turco com auditoria de ajuda, layout e textos de máquina preservados. | Misto. [I18N.md](I18N.md) |
| PAR-07 · P2 · Parcial | **Subconjuntos ainda não importados de SVG/DXF**. Conclui quando: Avaliar texto e demais entidades recusadas/ignoradas e explicitar seus limites; não tratar os importadores existentes como ausentes. | Autônomo. [SvgImporter.java](flatcam-cam/src/main/java/org/flatcam/cam/svg/SvgImporter.java) |

## Projetos e dados

| ID · prioridade · tipo | Trabalho e critério de conclusão | Execução / referência |
|---|---|---|
| IO-01 · P2 · Parcial | **Semântica de opções avançadas e macros no FlatPrj**. Conclui quando: Ampliar o que pode ser editado/regenerado com fidelidade e testar mais projetos no Python real, preservando dados opacos não alterados. | Misto. [COMPATIBILIDADE_FLATPRJ.md](COMPATIBILIDADE_FLATPRJ.md) |
| IO-02 · P2 · Decidir | **Recusas do exportador legado**. Conclui quando: Decidir suporte explícito ou manter guardas para exclusões, sondagem, programas nativos, rasgos curvos e unidades mistas. | Autônomo. [COMPATIBILIDADE_FLATPRJ.md](COMPATIBILIDADE_FLATPRJ.md) |
| IO-03 · P2 · Parcial | **Scripts/documentos como objetos do projeto**. Conclui quando: Definir integração nativa de novos scripts/documentos; hoje scripts FX são arquivos em abas, não ScriptObject persistido. | Autônomo. [TERMINAL_TCL.md](TERMINAL_TCL.md) |
| IO-04 · P3 · Decidir | **Limite de 10 MB da Tools Database**. Conclui quando: Tornar ajustável ou rever após medir memória/tempo e validar entradas; esse limite não é uma recusa universal de Gerbers grandes. | Autônomo. [LegacyToolsDatabase.java](flatcam-application/src/main/java/org/flatcam/app/project/LegacyToolsDatabase.java) |

## Interface e ajuda

| ID · prioridade · tipo | Trabalho e critério de conclusão | Execução / referência |
|---|---|---|
| UX-01 · P1 · Investigar | **Clareza da seleção de ferramentas no Rest**. Conclui quando: Avaliar resumo de fresas selecionadas e geração única; não chamar essa hipótese de causa confirmada do relato. | Autônomo. [NCC_REST_MULTI_CLEAR_REVIEW.md](NCC_REST_MULTI_CLEAR_REVIEW.md) |
| UX-02 · P2 · Parcial | **Guia de uso das ferramentas secundárias**. Conclui quando: Complementar os 13 tópicos com Paint, Milling, Calibration, Rules Check, SolderPaste e utilitários; manter exemplos e traduções. | Autônomo. [USER_GUIDE.md](USER_GUIDE.md) |
| UX-03 · P2 · Validar | **Traduções e mensagens dinâmicas de backend**. Conclui quando: Revisão linguística nativa ES/FR e mensagens estruturadas traduzíveis; preservar nomes, coordenadas e código da máquina. | Misto. [I18N.md](I18N.md) |
| UX-04 · P2 · Validar | **Layouts, tabelas, tooltips e diálogos em todos os estados**. Conclui quando: Conferir painéis estreitos, fontes grandes, foco/edição, desabilitados, temas e DPI, ampliando a auditoria dos estados novos. | Misto. [TOOLTIPS.md](TOOLTIPS.md) |

## Plot e renderização

| ID · prioridade · tipo | Trabalho e critério de conclusão | Execução / referência |
|---|---|---|
| RND-01 · P3 · Experimento | **Backend gráfico opcional com buffers persistentes**. Conclui quando: Comparar um protótipo com Canvas/Prism, incluindo alpha/caps/joins/picking e fallback, sem substituir o backend antes de provar benefício. | Autônomo. [ARQUITETURA_RENDERIZACAO_FUTURA.md](ARQUITETURA_RENDERIZACAO_FUTURA.md) |
| RND-02 · P2 · Validar | **Latência fria e fluidez em diferentes GPUs/DPI**. Conclui quando: Repetir cenários equivalentes após mudanças, separando fila/pulso de FPS apresentado e custos frio/aquecido. | Misto. [BENCHMARK_PC_ARC155H.md](BENCHMARK_PC_ARC155H.md) |
| RND-03 · P2 · Investigar | **Orçamentos de cache/raster e camadas densas**. Conclui quando: Medir memória, invalidações e publicação de camadas grandes; preservar detalhes e geometria visível. | Autônomo. [PLOT_PERFORMANCE.md](PLOT_PERFORMANCE.md) |

## Distribuição e validação

| ID · prioridade · tipo | Trabalho e critério de conclusão | Execução / referência |
|---|---|---|
| REL-01 · P1 · Validar | **Próxima Beta com a correção multitela**. Conclui quando: Quando autorizada, versionar/buildar e conferir que MSI/EXE/portátil contêm o reparo posterior à Beta 8. | Autônomo. [MULTI_MONITOR_REVIEW_20261117.md](MULTI_MONITOR_REVIEW_20261117.md) |
| REL-02 · P1 · Validar | **Regressão integral e pacote final da candidata**. Conclui quando: Rodar suíte completa, corpus/round-trips, instalação/upgrade e smoke do artefato exato; não usar apenas resultados focados antigos. | Misto. [BUILD_BETA.md](release/BUILD_BETA.md) |
| REL-03 · P1 · Validar | **Controladores e usinagem física**. Conclui quando: Validar famílias realmente usadas, alturas, troca, spindle/laser, sondagem e fixação em ambiente controlado com equipamento disponível. | Depende de equipamento/operador. [PREPROCESSADORES.md](PREPROCESSADORES.md) |
| REL-04 · P3 · Decidir | **Assinatura digital e atualização automática**. Conclui quando: Definir distribuição, assinatura e política de atualização; instaladores e upgrade MSI já existem. | Autônomo. [BETA-0.1.0-beta.8.md](release/BETA-0.1.0-beta.8.md) |

## Evoluções futuras

| ID · prioridade · tipo | Trabalho e critério de conclusão | Execução / referência |
|---|---|---|
| FUT-01 · P3 · Experimento | **GEOS nativo via FFM e eventual camada Rust**. Conclui quando: Prototipar somente o gargalo escolhido e medir conversões, memória, cancelamento e resultados antes de integrar. | Autônomo. [GEOMETRY_HOTSPOTS_20261118.md](GEOMETRY_HOTSPOTS_20261118.md) |
| FUT-02 · P2 · Avaliar Evo | **Climb/Conventional por fresa NCC/Paint**. Conclui quando: Portar com orientação de ilhas/mirroring/Connect e múltiplas profundidades verificados; campo atual é de ISO, não direção Clear por fresa. | Autônomo. [FLATCAM_EVO_REVIEW.md](FLATCAM_EVO_REVIEW.md) |
| FUT-03 · P3 · Avaliar Evo | **Exclusões CAM vinculadas ao objeto**. Conclui quando: Definir transformação, persistência e panelização; não confundir com exclusões CNC Around/Over já existentes. | Autônomo. [FLATCAM_EVO_REVIEW.md](FLATCAM_EVO_REVIEW.md) |
| FUT-04 · P3 · Avaliar Evo | **Importação direta de KiCad PCB/Eagle/EasyEDA**. Conclui quando: Escolher um formato, corpus, licença e contrato de cobre/furos/unidades antes de portar. | Autônomo. [FLATCAM_EVO_REVIEW.md](FLATCAM_EVO_REVIEW.md) |
| FUT-05 · P3 · Avaliar Evo | **Compatibilidade com o novo esquema de projetos Evo**. Conclui quando: Manter referência Python antiga e validar separadamente o snapshot Evo; não prometer intercâmbio do esquema novo. | Autônomo. [FLATCAM_EVO_REVIEW.md](FLATCAM_EVO_REVIEW.md) |

## O que já existe e não deve voltar à lista de ausências

- Fluxo Gerber/Excellon → Geometry → CNC, panelização multicamada com prévia e referência compartilhada; 24 painéis do menu principal, com opções avançadas parcialmente cobertas.
- Leitura e escrita `.FlatPrj`, além do formato nativo; aplicativo Python real já abriu/regravou o projeto de referência. Isso não garante round-trip universal de recursos exclusivos.
- Editor da Tools Database e integrações CAM; cores, padrões dos editores e opções de importação/exportação nas preferências.
- Importação SVG/DXF, com subconjuntos documentados; não confundir comandos Tcl ausentes com ausência na interface.
- Ports dos 20 perfis legados incluindo Paste_1. O inventário de nomes está fechado; parâmetros avançados e validação física não.
- Recentes, impressão PDF, editor de código, Salvar/Salvar como, edição de diâmetros, splash, quatro temas, tooltips formatados e PT-BR/EN/ES/FR.
- GPU pelo Prism/D3D, caches/densidade e fronteiras da renderização já implementados; FPS apresentado já medido em Arc e GTX 1650. O renderer GPU próprio ainda é experimento.
- MSI/EXE/portátil e upgrade por MSI já entregues. A tarefa é validar uma nova candidata, não criar instalador do zero.

## Inventário conferido agora

- Registro de comandos FX: **39 nomes/aliases FlatCAM**, além dos básicos do interpretador e dos comandos da UI do Terminal. Python: **66 famílias com aliases**; 30 têm algum nome correspondente e 36 não têm. Isso é inventário nominal, não equivalência dos argumentos ou resultados. `clear_shell`, por exemplo, oferece a função de limpar com outro nome que o `clear` Python.
- Entre os nomes ainda sem correspondência estão `paint`, `panelize`, `milldrills`, `millslots`, `open_gcode`, `open_svg`, `open_dxf`, criação de primitivas e consultas/alterações do sistema. Parte dessas funções já existe na UI.
- `ToolDefaults`: **219 definições em 35 grupos**. Não é o total de todos os controles/preferences nem permite calcular paridade contra a quantidade de chaves Python.
- O guia tem **13 tópicos** no catálogo atual. Não representa documentação completa das 24 ferramentas; vários tópicos já cobrem mais de uma etapa.
- Inspeção de fonte confirma que Paint publica as ferramentas com `ToolProfile.C1`; faltam transporte do TT/CNC da DB e respectiva validação.
- O limite de **10 MB** encontrado é da Tools Database. Não é um limite global de importação de placas.

## Limites e documentação histórica

Este foi um inventário de código, registros em runtime, documentação e histórico, sem executar CAM de novo, abrir janelas ou alterar preferências/projetos do usuário. Os comandos foram registrados num host nulo e seus nomes foram lidos; nenhum comando Tcl foi executado. As classes Python de comando foram analisadas por AST sem importar/executar o legado.

Não substitui uma comparação campo a campo de todos os painéis Python — essa auditoria aparece em PAR-04/PAR-05 — nem a validação física. Contagens antigas de 116 padrões, idiomas só PT/EN, ausência de exportação ou FPS ainda não medido não descrevem o estado atual. Builds e matrizes antigos permanecem evidência histórica, não aprovação automática da revisão atual.

A lista separa o Python legado do snapshot Evo v1.09 já examinado. Nenhum item Evo foi integrado por este mapeamento; não se presume que esse snapshot seja a versão upstream mais recente.

Índice estruturado, evidências adicionais, aliases Python e critérios por item: [validation/pending-map-20261118.json](validation/pending-map-20261118.json). O inventário bruto e o probe local estão em `target/pending-map-20261118/`. Nenhuma implementação ou nova Beta foi feita nesta tarefa.
