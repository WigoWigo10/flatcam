# Preferências por categoria — 2026-10-27

Abra **Editar > Preferências** ou a engrenagem da barra inferior. A aba agora usa navegação lateral e uma área de
edição que acompanha a largura disponível, em vez do formulário único limitado
a 460 px. As páginas são criadas quando abertas e mantidas enquanto a aba existir.

## Categorias disponíveis

| Categoria | Configurações já consumidas pelo FX |
| --- | --- |
| Geral | Português (Brasil)/English, quatro temas da interface e tela de abertura |
| Plot Area | Grade, snap, passos X/Y vinculados ou independentes, eixos, HUD e referência A4 |
| Gerber | Plot/Solid/Multi-Color, cores/transparência, Follow, abertura da tabela e opções de importação |
| Excellon | Plot/Solid/Multi-Color, cores/transparência e opções de importação |
| Geometry | Plot/Multi-Color, cor/transparência das linhas e resolução de novos arcos/círculos |
| CNC Job | Padrões gerais de perfil, Drilling, Geometry CNC e Excellon Milling |
| Ferramentas | Todos os demais grupos registrados em ToolDefaults: Isolation, NCC, Cutout, Paint, Panelize e outros |

A árvore é gerada a partir do cadastro existente: nenhum grupo de padrões é
omitido ou duplicado entre as páginas. Ícones usam os mesmos recursos do app,
incluindo a variante escura quando a janela troca de tema.

## Buscar e navegar

A busca ignora acentos e maiúsculas. Todas as palavras digitadas precisam ocorrer
no início de palavras do nome, chave ou descrição do campo: por exemplo `ncc sobreposicao` ou
`cutout diametro`. Filtra a navegação e os campos de padrões; Geral e Plot são
localizados pelos seus controles. A busca vazia mostra novamente as categorias.
Sem resultados, a aba informa isso sem apagar o conteúdo anteriormente editado.

Trocar seção, buscar e alternar mm/in preserva os rascunhos **nesta aba**.
Isso não equivale a salvar: fechar a aba descarta o que não foi confirmado.
Os valores em mm/in continuam independentes, sem conversão ao alternar unidades.

## Aplicar, salvar e restaurar

- Geral e Plot: **Aplicar e salvar** atualiza a interface e persiste somente essa
  seção. O idioma é aplicado na próxima abertura; a confirmação informa quando
  é preciso reiniciar. Os dois passos da grade são validados antes de qualquer aplicação;
  zero, negativos e valores não finitos são recusados. Aceita ponto/vírgula decimal.
- **Restaurar padrão** em Geral/Plot prepara o rascunho. Aplicar confirma.
  Os padrões iniciais são Português (Brasil), Branco puro e tela de abertura ativada;
  Plot usa os padrões existentes do FX.
- Padrões: **Salvar esta seção** salva todos os rascunhos dessa seção, inclusive
  os ocultos pela busca e nas duas unidades. Os valores inválidos permanecem
  pendentes com mensagem; os demais podem ser salvos, como no editor anterior.
- **Restaurar padrões de fábrica** nos padrões exige a confirmação inline e
  restaura somente a seção escolhida, nas duas unidades, descartando seus
  rascunhos. Não limpa outros grupos nem as configurações de aparência/Plot.

Salvar padrões afeta os próximos painéis/operações; não reescreve objetos do
projeto nem parâmetros de painéis já abertos. Importação/exportação `.properties`
pelo menu Backup continua usando o cadastro inteiro. Painéis abertos preservam
os valores com que foram abertos, como antes desta reorganização.

## Incremento: exibição e opções avançadas do Python

Quinze padrões foram acrescentados, com valores de fábrica conferidos em
`defaults.py` e nos grupos Gerber/Excellon/Geometry do Python:

| Preferência Python | Campo FX |
| --- | --- |
| gerber_plot / solid / multicolored | Gerber > Exibição: Plot, Solid, Multi-Color |
| gerber_plot_fill / plot_line | Gerber > Exibição: preenchimento/linhas |
| excellon_plot / solid / multicolored | Excellon > Exibição: Plot, Solid, Multi-Color |
| excellon_plot_fill / plot_line | Excellon > Exibição: preenchimento/linhas |
| geometry_plot / multicolored / plot_line | Geometry > Exibição: Plot, Multi-Color, linhas |
| gerber_follow | Gerber > Avançadas: Follow inicial |
| gerber_aperture_display | Gerber > Avançadas: abrir tabela expandida |

As cores possuem seletor visual e hexadecimal `#RRGGBB` ou `#RRGGBBAA`.
AA define opacidade de 00 a FF; preenchimento e linha são independentes.
Hexadecimal inválido continua no rascunho, com erro ao salvar. Busca e navegação
preservam a edição; seções sem comprimentos não mostram unidades mm/in.
O menu de cores > Padrão usa as cores atualmente configuradas para a categoria.

Plot, Solid, Multi-Color, cores e Follow são usados ao adicionar novos objetos.
Alterar esses padrões não muda objetos existentes. Cópias conservam a aparência
e o modo Follow da origem. Ao reabrir um projeto, os valores salvos prevalecem
mesmo se os padrões atuais estiverem diferentes; a restauração não modifica as
preferências para os objetos criados depois. Follow conserva a cor ao alternar
para as linhas centrais e de volta, sem modificar o cobre usado no CAM.

A tabela de aberturas agora fica em uma seção recolhível. O padrão Python é
recolhida; clique em **Apertures Table** para expandir. Seu estado inicial é
aplicado ao abrir o painel de propriedades e não altera painéis já abertos.
Diferença explícita: a expansão é um padrão do painel FX, não uma opção persistida
por objeto como no Python. Plot/Solid/Multi-Color/cores/Follow são estados dos objetos.

O Multi-Color de Geometry ganhou persistência nativa e leitura/escrita da opção
Python `multicolored`. O campo nativo é opcional, com false para arquivos antigos;
construtores anteriores continuam disponíveis. O teste também lê o legado sem
metadados FX, além da reabertura dos dois formatos pelo host real MainWindow.
Não houve nova execução do aplicativo Qt nesta etapa.

Essas preferências do FX são salvas no seu store e no backup `.properties`.
A tabela acima identifica equivalência dos campos; não afirma importação automática
das preferências globais de um `.FlatPrj` para o store do FX.

## Verificação e limites de paridade

### Série seguinte, item 1: CNC Job

`CNC Job > CNC Job: Exibição` configura cores/transparência independentes de
corte/deslocamento, Plot inicial, All/Cut/Travel, anotações, setas e fonte/cor
das anotações. A fonte padrão de novos jobs é 9, como no Python, limitada a
1–72 pixels lógicos. A cor padrão adapta o contraste ao tema; cores personalizadas
são usadas como escolhidas. Isso só modifica a apresentação, nunca o G-code.
Jobs existentes mantêm os valores ao copiar, editar código ou reabrir projetos.
O estado visual é opcional no formato nativo, mantendo leitura de arquivos antigos.
No `.FlatPrj`, os detalhes por objeto são uma extensão FX `fx_plot_settings`;
não se afirma que o Python use todos esses detalhes visuais ao abrir o arquivo.
Controles All/Travel/Cut e anotações/setas das propriedades continuam funcionando.
37 testes focados e empacotamento passaram; log `target/cnc-display-commit-check.log`.


Na etapa por categorias (`ae16ddb5`), dez cenários novos verificam roteamento de todos os grupos, busca e navegação,
rascunhos mm/in, erros ocultos pela busca, reset restrito, aplicação do Plot,
tema e layout de 680 px nos quatro temas. Os testes usam store em memória e
Host substituto: não escrevem as preferências reais nem movem o mouse.
Capturas JavaFX offscreen opt-in: `flatcam-fx/target/preferences-<tema>.png`.

Empacotamento e regressão focada: **55 testes passaram**, sem falhas/erros,
incluindo os editores, tooltips, campos e consumo dos padrões existentes.
Logs: `target/preferences-regression.log`; primeira rodada em
`target/preferences-focused.log`. Rodada integral final: **1975 registros,
1956 aprovados, 18 skips opcionais e uma falha**, a End Z já conhecida em
`CncNativePrecisionTest`; os dez testes novos passaram. Integral permanece
reprovado, sem alteração da asserção. Log em `target/preferences-verify.log`.

O incremento avançado tem 16 novos casos (persistência, host e UI/modelo), com
empacotamento e **55 testes focados finais aprovados**, sem skips, em
`target/preferences-advanced-persistence.log`. A rodada anterior de regressão
do Terminal teve 118 aprovações e um skip opcional; não somar contagens repetidas.
Capturas dos seletores de cor nos quatro temas ficam em
`flatcam-fx/target/preferences-colors-<tema>.png`. A verificação integral final
consta em `target/preferences-advanced-final-verify.log`: **1991 registros,
1972 aprovados, 18 skips opcionais e somente a falha End Z conhecida de
CncNativePrecisionTest**, sem alterar sua asserção. Os 16 casos novos passaram;
a suíte integral continua reprovada.

É um incremento da paridade visual/organização, não port completo das preferências
Python. Esta observação é histórica: importação/editores foram ampliados nas
seções seguintes. As lacunas gerais atuais estão no inventário abaixo. Buffering
e Delayed Buffering do Gerber Python não foram copiados para o backend FX, que
possui outra preparação/renderização. Não foram
criados controles sem efeito para representar esses campos. Falta validação da
janela real em todos os DPI/monitores e comparação visual lado a lado com Qt.

## Inventário atualizado das lacunas gerais — 2026-10-27

Cores de objetos e editores, exibição CNC, exportação Gerber/Excellon,
importação Excellon/limites Gerber, padrões dos editores e seleção de idioma já
existem. Não devem mais aparecer em listas como totalmente ausentes.

Comparação com `GeneralAppPrefGroupUI.py`, `GeneralAPPSetGroupUI.py` e
`GeneralGUIPrefGroupUI.py` do Python:

| Área | Situação atual no FX |
| --- | --- |
| Idioma | Português (Brasil) e English; demais catálogos do Python pendentes. Veja [I18N.md](I18N.md). |
| Unidades/precisão globais | Existem unidades por objeto/fluxo e formatos CAM configuráveis; falta a configuração geral de partida e precisão MM/IN de toda a UI. |
| Fontes da interface | Fonte/tamanho de anotações CNC existem; preferências independentes de fonte do notebook, eixos, caixas de texto e HUD ainda não. |
| Mouse/cursor | Snap, seleção, pan e cursores funcionam; faltam os controles gerais de forma/tamanho/espessura/cor e remapeamento de pan/seleção do Python. |
| Aparência/interação gerais | Quatro temas e tooltips com política compartilhada existem; faltam os ajustes de cores de hover/seleção por direção e várias preferências de comportamento geral do legado. |
| Backend gráfico | Escolha GPU/Software pelo launcher e identificação no Sobre/splash; opções Qt/VisPy e Buffering não têm equivalência direta e não devem virar controles sem efeito. |

Esta tabela prioriza as lacunas gerais conferidas no código; não é uma contagem
campo a campo de todos os grupos Python nem uma porcentagem de paridade.


### Série seguinte, item 2: padrões dos editores

Cores de desenho/seleção nos três editores; limite de destaque detalhado em
Geometry/Excellon; tamanho/tipo de abertura Gerber, quantidade/passo/ângulos de
array e fator de escala/buffer; diâmetro inicial e colunas/linhas/passos Excellon.
Valores de abertura (0,8 mm), quantidade Gerber (5), passo Gerber (0,1 mm) e
passo Excellon (2,54 mm) seguem o Python. Arrays Excellon continuam retangulares;
não foram adicionados controles inertes para os arrays circulares do legado.
Os padrões só são lidos ao abrir o editor. Ferramentas existentes têm prioridade.
Cores incluem opacidade; limites de seleção só simplificam o destaque, sem
limitar a seleção. Os limites FX existentes (2000/500) foram mantidos.
Validação, roundtrip mm/in e consumo na UI cobertos por 56 testes focados e
empacotamento aprovados em `target/editor-defaults-final.log`.


### Série seguinte, item 3: exportação CAM

Gerber/Excellon têm seções Exportação: unidades IN/MM, dígitos inteiros/decimais
(1–6), zeros L/T no Gerber e LZ/TZ no Excellon, formato dec/ndec e slots
ROUTED/G85. Valores de fábrica conferidos no Python. O diálogo confirma e salva
os mesmos valores usados por Salvar como e pelo Terminal; cancelar não salva.
Ao iniciar pela primeira vez esta versão, os formatos antes lembrados pelo
antigo diálogo são migrados uma única vez. Exportar/importar/reset de preferências
inclui estes padrões. Não há conversão do objeto em memória.
A precisão escolhida arredonda o arquivo: o padrão Python IN 2:4 é diferente do
antigo Salvar como FX, que usava precisão automática. Escolha mais decimais nas
preferências quando necessário. Excellon decimal ignora a opção de zeros.
Foram escritos/reabertos arquivos nas 4 combinações Gerber e 16 Excellon,
conferindo limites, furos, diâmetros e finais de slots. Os reparos do exportador
FX nos slots e supressão declarada permanecem: isto não afirma igualdade textual
com as saídas defeituosas do legado. Regressão: 126 aprovações/1 skip opcional
mais exporters CAM (`target/export-preferences-final.log`); repetição com arquivos
temporários reais aprovada (`target/export-preferences-files.log`).


### Série seguinte, item 4: transformações do Editor Geometry

Escala X/Y, espelhos X/Y, inclinação e offset usam prévia/confirmar/cancelar,
com o mesmo cálculo em worker e histórico da rotação. Origem/centro/cantos/ponto
servem de pivô; deslocamento é independente dele e oculta esses controles.
Documentação e limitações em [GEOMETRY_EDITOR.md](GEOMETRY_EDITOR.md).
Não foram adicionadas preferências inertes para operações ainda ausentes.
Última verificação focada: 37 testes, sem falhas ou skips, e pacote aprovados.
Suíte integral da série: 1987 aprovações/18 skips/1 falha End Z já conhecida,
2006 registros; log `target/parity-preferences-series-verify.log`. O último ajuste
somente do modo offset foi coberto pela rodada `target/editor-affine-final.log`.
O integral continua reprovado; não foi alterada a asserção de End Z.


### Arrays Excellon novos — 2026-10-27

Editor Excellon tem LINEAR/CIRCULAR/GRID, quantidade, passo/eixo/ângulo linear,
raio/início/passo/sentido circular, comprimento/ângulo do slot e rotação com o
círculo. Os 12 padrões adicionais são lidos na abertura; mm/in independentes.
Campos sem efeito no modo atual ficam ocultos/desabilitados e seus rascunhos
não impedem gerar outro modo. Comprimento é total, incluindo tampas redondas.
[EXCELLON_ARRAYS.md](EXCELLON_ARRAYS.md) descreve evidências e diferenças Qt.


### Importação Excellon — 2026-10-27

Dígitos inteiros IN/MM e convenção LZ/TZ quando ausente agora têm efeito no
leitor, inclusive nos slots. Máscaras/comentários e cabeçalhos declarados têm
prioridade; ponto explícito é literal. Save recusa valores fora dos intervalos.
Gerber mantém suas duas opções existentes; buffering Qt não ganhou controles
inertes. [IMPORT_FORMATS.md](IMPORT_FORMATS.md) contém exemplos e evidências.

## Tela de abertura

Em **Geral**, **Exibir tela de abertura** controla a próxima inicialização.
Aplicar salva a escolha; navegar não salva e Restaurar prepara a opção ativada.
A splash usa o tema salvo e mostra etapa, modo gráfico ativo e placa em uso.
Depois de pronta, mantém as informações por 5 segundos; clique nela para
continuar antes. Acentos Unicode são preservados.
Detalhes e opções de execução: [STARTUP_SPLASH.md](STARTUP_SPLASH.md).

## Padrão Branco puro

**Branco puro** (tema clássico claro) é o padrão de fábrica, compartilhado pela
janela, splash e tooltips sem proprietário. **Restaurar padrão** em Geral prepara
essa escolha; aplicar confirma. Temas já salvos continuam respeitados. Os nomes
na interface são Branco puro, Preto puro, Branco gelo e Preto gelo; identificadores
persistidos CLASSIC_LIGHT/CLASSIC_DARK permanecem compatíveis.

A splash usa fundo uniforme #FFFFFF em Branco puro e #000000 em Preto puro,
com textos/logotipo contrastantes e os mesmos dados de GPU/Software e leitura.
