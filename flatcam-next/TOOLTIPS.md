# Revisão dos tooltips — 2026-10-07

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
