# Preferências por categoria — 2026-10-27

Abra **Editar > Preferências** ou a engrenagem da barra inferior. A aba agora usa navegação lateral e uma área de
edição que acompanha a largura disponível, em vez do formulário único limitado
a 460 px. As páginas são criadas quando abertas e mantidas enquanto a aba existir.

## Categorias disponíveis

| Categoria | Configurações já consumidas pelo FX |
| --- | --- |
| Geral | Quatro temas da interface |
| Plot Area | Grade, snap, passos X/Y vinculados ou independentes, eixos, HUD e referência A4 |
| Gerber | Unidades ausentes no arquivo e resolução dos arcos na importação |
| Excellon | Unidades ausentes e precisão decimal na importação |
| Geometry | Resolução de novos arcos/círculos do editor |
| CNC Job | Padrões gerais de perfil, Drilling, Geometry CNC e Excellon Milling |
| Ferramentas | Todos os demais grupos registrados em ToolDefaults: Isolation, NCC, Cutout, Paint, Panelize e outros |

A árvore é gerada a partir do cadastro existente: nenhum grupo de padrões é
omitido ou duplicado entre as páginas. Ícones usam os mesmos recursos do app,
incluindo a variante escura quando a janela troca de tema.

## Buscar e navegar

A busca ignora acentos e maiúsculas. Todas as palavras digitadas precisam ocorrer
no nome, chave ou descrição do campo: por exemplo `ncc sobreposicao` ou
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

## Verificação e limites de paridade

Dez cenários novos verificam roteamento de todos os grupos, busca e navegação,
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

É um incremento da paridade visual/organização, não port completo das preferências
Python. Continuam faltando grupos completos de cores/estilo e opções avançadas
por objeto/editor, exportação e outras preferências gerais do legado. Não foram
criados controles sem efeito para representar esses campos. Falta validação da
janela real em diferentes DPI/monitores e comparação visual lado a lado com Qt.
