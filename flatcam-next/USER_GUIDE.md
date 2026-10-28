# Guia de uso do FlatCAM FX — 2026-10-28

Abra **Ajuda > Guia de uso** ou pressione **F1**. O guia abre uma única aba
fechável ao lado do Plot Area, preservando o artigo e a busca ao voltar à aba.
Reabrir depois de fechá-la começa pelo fluxo principal. A leitura não altera
objetos, parâmetros ou arquivos do projeto.

O conteúdo funciona sem internet, em Português (Brasil) ou English, conforme
o idioma escolhido para a interface. Não usa navegador ou WebView. Não
substitui os tooltips dos campos nem a lista de atalhos existente.

## Conteúdo inicial

| Assunto | Conteúdo |
| --- | --- |
| Fluxo principal | Importar, preparar/panelizar, gerar Geometry, gerar CNC Job e exportar |
| Abrir e navegar | Tipos de objetos, unidades, seleção, propriedades, zoom, pan e snap |
| Outline / Edge_Cuts | Contorno físico, conversão em área, recortes internos e uso como referência |
| Panelização | Conjunto de camadas, caixa comum, grade, espaçamento e prévia |
| Duas faces | Espelhamento, referência comum e furos de alinhamento |
| Isolation | Diâmetro/perfil, passes, sobreposição e passagem para CNC |
| NCC | Área, margem, ferramentas, Rest, Connect e cobertura |
| Cutout | Formato, placa/painel, pontes e parâmetros de corte |
| Drilling | Ferramentas, alturas, avanços, troca e exclusões |
| Geometry → CNC Job | Parâmetros por ferramenta, preprocessador, revisão e saída |
| Editor G-code | Rascunho, aplicar, salvar cópia, cancelar e limites da prévia |
| Projetos | `.fcnproj`, intercâmbio `.FlatPrj` e distinção das saídas de máquina |
| Diagnóstico | Visibilidade, alinhamento, regiões não usinadas e parâmetros CNC |

Cada artigo contém finalidade, preparação, passos numerados, pontos para
conferir, um detalhe específico e links para assuntos relacionados. Negrito
destaca controles e conceitos. Blocos de observação e seleção usam as cores
do tema. Os artigos explicam procedimentos; não prescrevem valores de corte
para uma máquina/material que não foram informados.

### Outline

O artigo distingue três representações: linhas do perímetro, área preenchida
da placa e trajeto compensado da fresa. O comando atual é **Editar > Converter
> Contorno → Área**, que cria uma Geometry `_area` a partir de um Gerber ou
Geometry com contornos fechados. A orientação descreve a conservação de
regiões separadas/recortes e a recusa de segmentos abertos, sem prometer reparo
automático do desenho.

A caixa de referência da panelização determina o passo; o contorno da prévia
determina o desenho mostrado. A área pode servir de referência ao NCC, e o
Cutout gera o trajeto de recorte. A utilidade Caixa delimitadora é diferente
da reconstrução do formato real.

## Navegação e layout

**Ctrl+F** leva à busca. A busca cobre título e conteúdo, ignora acentos e
exige todas as palavras. Sem correspondência, mostra uma orientação para
reformular a consulta. Limpar restaura o índice; um link relacionado limpa
a busca antes de abrir o artigo de destino.

O índice usa ícones das ferramentas, incluindo as variantes escuras. Em
larguras menores que 650 pixels lógicos, a lista passa para cima do artigo;
as duas áreas mantêm rolagem própria. Texto quebra linhas na largura
disponível. Os atalhos gerais que alteram o Plot não são disparados pela
navegação do guia.

`help-guide.css` é carregado nos quatro temas por `ThemeOption`. Isso é
necessário porque os temas clássicos não carregam `components.css`. A versão
inicial sem essa folha compartilhada deixava os `Text` do corpo pretos nos
temas clássicos; a regressão confere a cor efetiva após renderizar.

## Manutenção e verificação

`HelpGuideCatalog` registra IDs estáveis, categorias, ícones e relações.
As sete partes de cada artigo ficam nos catálogos `guide.topic.<id>.*` em
`tools/ui_text_catalog.json` e `tools/ui_text_en.json`. Edite as duas versões
e gere os recursos com `python tools/write_ui_catalogs.py --strict`.
Somente `**negrito**` é interpretado; o conteúdo não executa HTML/scripts.

`HelpGuideCatalogTest` verifica traduções, ícones, passos e navegação entre
todos os artigos. `HelpGuidePaneTest` confere busca, estado vazio, links,
reutilização/fechamento da aba, F1 declarado no menu, layout estreito e cores
nos quatro temas. `I18nUiTest` confere os dois idiomas em processos isolados
com preferências em memória, inclusive ausência de efeito do atalho G no
Plot quando o guia está selecionado. A auditoria inclui os controles e links
de todos os artigos.

Capturas reproduzíveis de Panelização, NCC e Outline em
`flatcam-fx/target/i18n-review/<idioma>-<tema>-guide-<assunto>.png`; a variação
estreita termina em `guide-narrow.png`. Elas usam uma janela JavaFX invisível, fora da tela, com pulsos entre as
trocas de artigo. Snapshots sem janela conservavam a imagem antiga do
viewport; essa versão não serviu como evidência da troca visual. As capturas
finais não certificam todos os monitores/DPI físicos. O menu/atalho foi testado
na cena; a rodada não automatizou a tecla física do sistema operacional.

Verificação integral: **2137 aprovações, 18 skips opcionais, zero falhas/erros**
(2155 registros), em `target/guide-verify.log`/JSON. A revisão adicional da
janela invisível passou em `target/guide-window-review.log`. Probe do launcher
existente aprovado em `target/guide-native-probe.log`; não há novas
dependências ou alterações no executável nativo. Resultado consolidado em
[CONTEXTO_E_PROGRESSO.md](CONTEXTO_E_PROGRESSO.md).

Ainda não é um manual completo das 24 ferramentas: tópicos dedicados de
Paint, Milling, calibração, checagem de regras e demais utilitários podem ser
adicionados ao mesmo índice. Links do guia consultam artigos; não iniciam
operações de usinagem.
