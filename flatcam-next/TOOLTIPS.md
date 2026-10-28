# Revisão dos tooltips

## Áreas recentes, ações de diálogo e auditoria por controle — 2026-10-27

Reutilizada a apresentação central (`TooltipContent`/`FluidTooltips`). Novos
rótulos em português, códigos G0–G3/G85/G90/G91, tipos de array e atalhos como
Ctrl+Shift+S recebem negrito. Os cabeçalhos Prévia, Resultado, Referência,
Exemplo e Progresso recebem o azul do tema; Limitação/Limitações usam o âmbar
dos avisos. As palavras continuam explicando seu significado sem depender da cor.
Quebras explícitas e separação de parágrafos são preservadas; não há interpretação
de HTML/Markdown. A política de atraso 1000/300 ms permanece a mesma.

Ajuda adicionada ou atualizada:

- Medição: significado de cada resultado, distância versus trajeto, diferenças
  dos ângulos, novo cálculo, ponto médio e fechamento.
- Jump/Localizar no objeto: coordenadas absolutas/relativas, sintaxe decimal,
  limites do objeto versus cobre, confirmação, cancelamento e atalhos.
- Exportação Gerber/Excellon: confirmar formato, cancelar, unidades/dígitos e
  recusa de G85 em rasgos curvos; curvas roteadas são representadas por segmentos.
- Cor/opacidade: ações de aplicar/cancelar e diferença entre transparência zero
  e desativar Plot.
- Arrays Excellon: títulos por campo, prévia versus confirmação, modo GRID de
  aplicação direta, desfazer/refazer e cancelamento de cálculo versus descarte.
- Progresso recolhido: porcentagem por etapa, fase indeterminada e cancelamento.
- Film, Etch, Transform, Cutout e propriedades Gerber/Excellon: campos internos
  de grupos e ações que antes não tinham sua própria descrição.
- Editor de script: estado do arquivo e diferença entre executar e salvar.

`PanelTooltips` agora também acompanha páginas dentro de SplitPane, inclusive
itens adicionados depois. Textos escritos especificamente para um controle
continuam tendo prioridade sobre o catálogo compartilhado.

### Auditoria e evidências

A auditoria percorre todas as páginas das preferências, as ferramentas/editores
anteriores, medição, script/progresso e dez variações de diálogos, incluindo seus
botões. Constrói os mesmos formulários usados pela aplicação sem mostrar janelas
ou executar ações CAM. Foram separadas construção e exibição dos diálogos de
exportação, aparência e navegação; confirmar/cancelar conserva o fluxo existente.

A regra antiga aceitava uma descrição em qualquer ancestral como cobertura do
controle. A regra atual exige ajuda no próprio controle: encontrou **15 lacunas**
em Etch, Film, Cutout, Transform e propriedades. Foram preenchidas com ajuda
específica, sem voltar à herança genérica. O estado com lacunas está registrado
em `target/tooltip-audit-gaps-20261027.txt`.

Inventário final focado: **1496 ocorrências, zero MISSING** em
`flatcam-fx/target/tooltip-audit.txt`; não são 1496 textos únicos. Há campos
ocultos/desabilitados no inventário; não são ignorados. Rótulos, containers e
internals dos controles nativos não são tratados como comandos sem ajuda.

Rodada focada aprovada: `target/tooltip-expansion-focused.log`. Testes exercitam
formatação literal, unidade/caminho decimal, códigos/atalhos, instalação dinâmica
em SplitPane e aviso acessível do seletor G85 desabilitado. Snapshots nos quatro
temas gerados; Branco puro e Preto puro foram inspecionados. Contraste de todos
os trechos da apresentação testado em pelo menos 4,5:1 contra o fundo do tooltip.

Suíte completa (`mvnw.cmd verify`): **2118 aprovações, 18 skips opcionais,
zero falhas/erros (2136 registros)**. Evidências:
`target/tooltip-expansion-verify.log` e `target/tooltip-expansion-verify.json`.
Launcher nativo reconstruído e probe aprovado:
`target/tooltip-expansion-native-build.log`.
A auditoria não cobre todas as combinações de perfil/seleção nem as janelas
nativas de arquivo e os controles internos do seletor de cores do JavaFX.
Ela não mede latência real do hover e não certifica trajetos de usinagem.

## Drill slots — 2026-10-14

Drilling tem ajuda explícita para ativar furos sobrepostos por ferramenta,
passo por diâmetro/sobreposição, limite de quantidade ajustável futuramente,
regra de último furo e possibilidade de material residual. Desativado preserva
fresagem lateral. Ordem por proximidade agora informa que inclui centros
convertidos, mas não reordena rasgos fresados. Revisão diferencia as operações.
Inventário: 1104 ocorrências, zero `MISSING` na rodada completa.

## Passes contínuos fechados — 2026-10-13

Estratégia de passes distingue retração, alternância e contínuo na mesma
direção para contornos fechados; explica recusa de abertos, pontos em Z final,
Preservar direção e incompatibilidades Extra Cut/sonda. Revisão reflete a
estratégia correta. Tempo e apresentação centralizados não mudaram.
Auditoria após build: 1099 ocorrências, zero MISSING no cenário construído.

## Altura individual e RPM zero — 2026-10-13

Novo seletor **Política de Travel Z** explica global/individual, altura comum
de troca e limite da prévia. End Z explica a última fresa ativa conforme a
política; RPM/potência e resumo explicam acionamento/parada manuais em zero,
sem prometer M5 final ou simular macros. Inventário atual: **1099 ocorrências,
zero MISSING**, sem mudar tempo/formatação. [CNC_PARAMETER_PARITY.md](CNC_PARAMETER_PARITY.md).

## Over segmentado e altura de troca — 2026-10-13

Strategy/Over Z descrevem transições na entrada/saída da região ampliada e
maior altura por trecho em sobreposição, sem baixar de Travel Z. A revisão
explica Start Z versus Tool change Z, com link para o campo correto. Inventário
atual: **1097 ocorrências, zero MISSING** no cenário construído; política de
tempo/formatação inalterada. [CNC_PARAMETER_PARITY.md](CNC_PARAMETER_PARITY.md).

## Cobertura NCC — 2026-10-13

A auditoria inclui o botão **Ver cobertura no Plot** e os controles do painel
de resíduos. Inventário atualizado: **1096 ocorrências, zero MISSING** no
cenário construído. Tempo e formatação seguem a política central existente.
Escopo e limites: [CNC_COVERAGE.md](CNC_COVERAGE.md).

## Revisão CNC — 2026-10-13

A auditoria também constrói os resumos Geometry e Drilling, incluindo links
por fresa, parâmetros comuns e botões Continuar/Voltar. Inventário atual:
**1090 ocorrências, zero MISSING** no cenário construído. Os novos controles
usam a política central de tempo e formatação; não houve alteração no atraso.
Isso não substitui visitar todos os estados no aplicativo real. Contrato e
pendências de cobertura/usinagem: [CNC_REVIEW.md](CNC_REVIEW.md).

## Atualização — 2026-10-12

### Tempo de exibição

Não existe um número universal para todos os aplicativos. O guia histórico
da [Microsoft](https://learn.microsoft.com/en-us/windows/win32/uxguide/ctrl-tooltips-and-infotips)
documenta 500 ms iniciais e 100 ms ao passar entre alvos; o
[JavaFX 25](https://openjfx.io/javadoc/25/javafx.controls/javafx/scene/control/Tooltip.html)
usa 1000 ms de atraso inicial. O próprio guia alerta que atrasos curtos demais
podem tornar as dicas involuntárias e incômodas.

Política centralizada em `TooltipTiming`, escolhida para o FX:

- Primeiro hover: **1000 ms**, antes 450 ms.
- Navegação entre dicas da apresentação animada: **300 ms**, antes 60 ms
  ou deslocamento imediato quando uma dica já estava aberta. Os 300 ms são
  uma escolha do FX, não uma exigência desses guias.
- Após clique, tecla ou rolagem: a dica fecha e o próximo hover volta a 1000 ms.
- Células nativas da árvore/tabelas: atraso inicial de 1000 ms, preservando
  duração de leitura e comportamento de fechamento/reexibição do JavaFX.
  A ajuda dinâmica da célula tem prioridade sobre a descrição geral da tabela,
  evitando duas dicas sobrepostas e mantendo atualizações ao reutilizar a célula.

Mantidas as animações, quebras de linha, negrito, cores por tema e accessibleHelp.
Não foi acrescentada uma preferência persistida de duração nesta entrega.

### Lacunas e descrições desatualizadas

- Preferências / padrões das ferramentas: ajuda específica para cada campo,
  suas unidades, opções, valor de fábrica e efeito de salvar/resetar. Alternar
  mm/in não converte o rascunho; os valores são independentes. Salvar não altera
  objetos nem painéis já abertos. Novos campos sem descrição reprovam o teste.
- Gerber Object: checkbox Plot sem texto e ações Non-copper/BBox. Gerar uma
  Geometry nessas ações não equivale a gerar NCC/Cutout ou G-code.
- Sobre: tabelas de créditos e colaboradores; selecionar uma linha não abre
  contato e os contatos históricos não são apresentados como suporte do FX.
- CNC e Tools Database: RPM zero não elimina o M5 da sequência de troca;
  altura de troca deve respeitar Travel Z mesmo sem XY. Start Z e Multi-Depth
  descrevem a sequência atual do FX e as diferenças de retração do Python.
- Drilling End XY: explicita o comportamento atual de estacionamento. Sem
  exclusões, o XY final ocorre em End Z; com exclusões, usa altura protegida
  antes de descer. **A trajetória não foi corrigida nesta revisão de tooltips**;
  a descrição não deve prometer que uma altura baixa é segura.
- Travessia de Accordions inclui conteúdo e painéis adicionados depois;
  ajuda de legendas também chega a checkboxes sem texto, sem contaminar
  botões de ação vizinhos.

### Verificação e limites

`TooltipAuditTest` agora também constrói Preferências, propriedades Gerber e
Excellon, Tools Database (inclusive os campos dentro do SplitPane), Sobre e
editor de código. Inventário final: **1035 ocorrências**, zero controles sem
ajuda no cenário construído, em `flatcam-fx/target/tooltip-audit.txt`.
São ocorrências, não 1035 textos únicos. Containers informativos, rótulos,
separadores, blocos somente leitura e internals de skins não são lacunas.

Build completo `verify` aprovado:
`target/tooltip-review-full-20261012.log`. A ampliação final da travessia do
inventário foi testada depois em `target/tooltip-review-final-audit-20261012.log`.
Testes verificam agendamento de 1000/300 ms, retorno ao atraso inicial,
prioridade/dinamismo de células nativas, conteúdo contextual e campos mm/in.
Não medem a latência real do hover na janela nem cobrem todos os estados,
menus nativos de arquivo e combinações possíveis de projetos/perfis.

Roteiro manual: hover inicial, passagem entre botões, clique/rolagem/tecla,
árvore/tabelas, campos desabilitados, alternância mm/in e os quatro temas.
Preferências do usuário não foram salvas e nenhuma ação CAM foi executada
pela auditoria. Alterações CNC anteriores permanecem separadas desta revisão;
esta entrega foi incluída no commit de consolidação de 2026-10-12, sem push.

## Histórico — 2026-10-07

## Resultado

Reutilizada a apresentação existente (`FluidTooltips` / `TooltipContent`),
completando ajuda contextual em vez de criar uma segunda implementação visual.
Não muda algoritmos CAM, parâmetros, comandos, persistência ou critérios de paridade.

Lacunas encontradas e preenchidas:

- Calibration: origem dos pontos, coordenadas alvo, deltas, coleta/cancelamento,
  fatores e geração de verificação. Alvo não é delta; campos que não participam
  do cálculo dizem isso explicitamente.
- Gerber/Excellon Editors: aberturas e D-code, dimensões, arrays, área, buffer,
  transformação, ferramenta de novos furos/slots, aplicar/descartar e barras.
- Isolation/NCC/Paint: ações de geração, ordem, referências, limites e campos
  da escolha explícita de ferramenta da base.
- Panelize/2-Sided: conjunto de camadas, referência comum, coordenadas e furos
  de alinhamento. A prévia não cria objetos; unidades divergentes são recusadas.
- Film, QRCode, Subtract, Invert, Extract/Punch, Fiducials, Corner Markers,
  Etch, Copper Thieving, Rules, Optimal, SolderPaste e Calculators: seletores,
  ações e campos secundários; cm/cm²/µm não são confundidos com mm/in.
- CNC: geração, posições, sondagem e edição de exclusões, com cuidados sobre
  folga, fixações e limites da prévia.
- Propriedades: ações de ferramentas, tipo de plot e inspeção/reprodução da rota.
- Menus da árvore/plot, terminal Tcl, busca/clipboard do editor, snap da barra
  inferior e diagnóstico em Sobre.
- Tools Database: inclusive Adicionar/Copiar/Excluir e seu menu contextual,
  agora com explicação do efeito em memória e no disco. Essa regra substitui
  a decisão anterior de omitir ajuda nessas três ações.

Copiar texto no editor, copiar ferramentas na DB e copiar objetos no projeto
têm descrições diferentes. Comandos ainda não implementados informam que não
executam uma operação. Ajuda escrita especificamente para um controle tem
prioridade sobre o catálogo compartilhado.

## Apresentação

- Título em negrito; parágrafos separados e opções mantidas em linhas distintas.
- Termos técnicos e atalhos destacados sem interpretar HTML/Markdown do usuário.
- Unidades/atalhos em azul adaptado ao tema; atenção/limitações em âmbar adaptado.
  O texto continua explicando o significado: a cor não é a única indicação.
- Quebra automática por largura, preservando valores decimais e caminhos.
- Mesma formatação para dicas originalmente criadas como `Tooltip` nativo ou
  propriedades simples. Células reutilizadas da árvore conservam tooltip nativo
  e atualizam texto/estado/cor, sem congelar dados de um objeto anterior.
- Rótulos guardam a ajuda de campos desabilitados; a seleção da cena permite
  localizar controles desabilitados. Descrições permanecem no accessibleHelp.
- Mantidas as animações e fechamento existentes; menus contextuais não acumulam
  instalação duplicada na mesma instância.

## Verificação e limites

`TooltipAuditTest` constrói 26 painéis (as 24 ferramentas do menu e os painéis
Milling/Geometry CNC) mais três editores e duas barras,
sem abrir uma janela do usuário ou executar as ações CAM. Gera
`flatcam-fx/target/tooltip-audit.txt`: 745 ocorrências de controles, todas com
ajuda própria ou herdada de uma linha pertinente no cenário construído.
Containers, rótulos, separadores e internals de skins não contam como lacunas.
O teste reprova se surgir um controle sem ajuda nesse inventário.

O inventário não equivale a visitar todos os estados possíveis da aplicação:
menus do sistema, diálogos nativos de arquivo, estados carregados de projetos,
perfis alternativos e troca de monitores ainda precisam de validação manual.
Os testes separados da Tools Database cobrem seus 63 campos e ações; testes
de conteúdo verificam contexto, unidades, preservação de descrições e atalhos.
O contraste dos spans é testado em pelo menos 4,5:1 nos quatro temas.

Capturas offscreen da dica formatada, inspecionadas nos quatro temas:
`flatcam-fx/target/tooltip-{CLASSIC_LIGHT,CLASSIC_DARK,ICE_LIGHT,ICE_DARK}.png`.
Para regenerar:

```powershell
# Dentro de flatcam-next
.\mvnw.cmd -q -pl flatcam-fx -am '-Dtest=TooltipAuditTest,TooltipCoverageTest,PanelTooltipsTest,TooltipContentTest,ThemeTooltipPaletteTest,ToolsDatabasePanelTest' '-Dflatcam.tests.snapshots=true' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Roteiro manual: percorrer os painéis e abas, deixar o mouse em campos/ações
desabilitados, mudar o tipo C/R/O/P no Editor Gerber, comparar dicas de Copiar
na DB/código/árvore, abrir submenus e alternar os quatro temas. Confirmar que a
dica não fica aberta ao clicar, rolar, fechar diálogo ou sair do aplicativo.

Incremento autorizado para commit na branch `flatcam-next`. Pendências CAM anteriores
(NCC Connect e referência Python compatível) não foram alteradas.

Build completo: `verify` passou com 1467 testes registrados, 1455 aprovados,
12 opcionais ignorados e zero falhas/erros. A auditoria ampliada com QRCode e
Subtract foi repetida e passou. Probes offscreen do launcher passam tanto em
Direct3D/Intel Arc quanto em software. Não foi feita uma sessão manual completa
de hover/cliques no aplicativo aberto do usuário.
