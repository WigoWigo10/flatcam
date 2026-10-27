# Preferências por categoria — 2026-10-27

Abra **Editar > Preferências** ou a engrenagem da barra inferior. A aba agora usa navegação lateral e uma área de
edição que acompanha a largura disponível, em vez do formulário único limitado
a 460 px. As páginas são criadas quando abertas e mantidas enquanto a aba existir.

## Categorias disponíveis

| Categoria | Configurações já consumidas pelo FX |
| --- | --- |
| Geral | Quatro temas da interface |
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
  seção. Os dois passos da grade são validados antes de qualquer aplicação;
  zero, negativos e valores não finitos são recusados. Aceita ponto/vírgula decimal.
- **Restaurar padrão** em Geral/Plot prepara o rascunho. Aplicar confirma.
  O tema inicial é Branco gelo; Plot usa os padrões existentes do FX.
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
Python. Continuam faltando cores/estilo CNC, opções avançadas completas de
importação/editores, exportação e outras preferências gerais do legado. Buffering
e Delayed Buffering do Gerber Python não foram copiados para o backend FX, que
possui outra preparação/renderização. Não foram
criados controles sem efeito para representar esses campos. Falta validação da
janela real em diferentes DPI/monitores e comparação visual lado a lado com Qt.
