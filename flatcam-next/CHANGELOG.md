# Changelog do FlatCAM FX

Histórico das versões Beta do FlatCAM FX, da primeira distribuição Windows
até a versão atual. O histórico do FlatCAM Python fica no changelog da raiz
do repositório.

## [0.1.0-beta.11](release/BETA-0.1.0-beta.11.md) · 2026-11-27

- Geometry → CNC Job exporta grupos de fresas em programas separados,
  preservando a numeração de origem na revisão e no arquivo.
- Editor de Geometry identifica a fresa dos trajetos e filtra a tabela e o
  Plot. A seleção em lote pode ser removida ou atribuída a outra fresa com
  Desfazer.
- Editor Excellon filtra furos e rasgos por ferramenta e oferece seleção em
  lote dos elementos visíveis.
- Solder Paste usa os bicos explicitamente marcados para gerar os trajetos.
- O changelog acompanha o pacote. MSI 0.1.110.

## [0.1.0-beta.10](release/BETA-0.1.0-beta.10.md) · 2026-11-27

- Importar Gerber ou Excellon para um projeto existente converte as medidas
  para a unidade do projeto. Escalar um Excellon também atualiza os diâmetros.
- A prévia de CNC Job usa a escala do Plot, inclusive após reabrir o projeto.
  Mudanças de unidade após posicionamento deixam um aviso na prévia.
- O Rules Check normaliza as unidades das camadas e dos limites. O 2-Sided
  usa a unidade do Plot para os furos de alinhamento.
- Escala uniforme do Gerber atualiza as dimensões das aberturas. O Paint
  oferece prévia e geração somente com as fresas selecionadas. MSI 0.1.100.

## [0.1.0-beta.9](release/BETA-0.1.0-beta.9.md) · 2026-11-21

- Novo monograma FX na janela, splash, diálogo Sobre e executáveis Windows,
  com seis resoluções de ícone.
- Corrigida a restauração da janela entre monitores com escalas diferentes.
  MSI 0.1.90.

## [0.1.0-beta.8](release/BETA-0.1.0-beta.8.md) · 2026-11-14

- Refinamentos de cobertura em Lines, Standard e Rest. Connect passou a
  reutilizar índices geométricos nas verificações de ligação.
- Estratégias experimentais Lines por regiões e Espiral adaptativa,
  com otimização da espiral em geometrias densas.
- Prévia cancelável de NCC e Paint com cortes, deslocamentos e resumo por fresa.
- Filtros Gerber ampliados e correções para comandos legados, BOM UTF-8 e
  supressão de zeros. MSI 0.1.80.

## [0.1.0-beta.7](release/BETA-0.1.0-beta.7.md) · 2026-11-09

- O menu Editar do Gerber mantém o painel do editor já ativo acessível.
- Paint invalida seleção e resultados quando origem ou referência muda.
  Cancelamento tardio e liberação de seleção ao trocar de ferramenta
  receberam correções.
- Paint reconhece polígonos dentro de coleções e exibe a unidade do painel
  antes de escolher origem e referência. MSI 0.1.70.

## [0.1.0-beta.6](release/BETA-0.1.0-beta.6.md) · 2026-11-09

- NCC e Paint ganharam ângulo por fresa em Lines e Combo, inclusive com Rest.
  O rascunho NCC salva a escolha no projeto nativo.
- A troca de ferramenta mantém o acesso ao painel de edição ativo até concluir
  ou descartar a sessão. MSI 0.1.60.

## [0.1.0-beta.5](release/BETA-0.1.0-beta.5.md) · 2026-11-05

- Cutout ganhou o fluxo Recortes internos, com desenho ou seleção de aberturas,
  prévia compensada e geração de Geometry para CNC.
- A interface ganhou espanhol e francês e o seletor passou a exibir ícones
  SVG para os quatro idiomas. MSI 0.1.50.

## [0.1.0-beta.4-r1](release/BETA-0.1.0-beta.4-r1.md) · 2026-11-01

- Propriedades Excellon ganhou edição direta do diâmetro por ferramenta,
  com atualização do Plot e Desfazer/Refazer. MSI 0.1.41.

## [0.1.0-beta.4](release/BETA-0.1.0-beta.4.md) · 2026-11-01

- Geometry → CNC passou a aceitar profundidade negativa diretamente. A
  política Z padrão orienta os sinais, e o modo Machinist aceita Z livre.
- Editor de G-code ganhou Salvar no original e Salvar como cópia, com atalhos
  e tooltips para o destino e para Aplicar ao CNC Job.

## [0.1.0-beta.3](release/BETA-0.1.0-beta.3.md) · 2026-10-29

- Precisão de diâmetros no NCC e Isolation, retomada de formulários e
  persistência de rascunhos NCC no projeto nativo.
- Resumo e comparação de gerações NCC, escopo de Connect/Contour no Rest
  e edição de diâmetro no Paint.
- Delete e navegação por teclado nas tabelas. Os diálogos de salvamento
  passaram a sugerir uma pasta fora da instalação.

## [0.1.0-beta.2](release/BETA-0.1.0-beta.2.md) · 2026-10-28

- Ferramentas CAM mantêm o painel e o rascunho após gerar. Geometry → CNC
  pode repetir a geração no mesmo painel.
- Diâmetros de fresa editáveis em NCC e Isolation, com validação e uso do
  valor digitado no cálculo.

## [0.1.0-beta.1](release/BETA-0.1.0-beta.1.md) · 2026-10-28

- Primeira Beta Windows x64 com instaladores MSI/EXE, ZIP portátil e Java incluído.
- Importação Gerber/Excellon, projetos nativos `.fcnproj` e intercâmbio `.FlatPrj`.
- Panelização com referência comum, Isolation, NCC, Cutout, Drilling e
  geração, edição e exportação de G-code.
- Quatro temas, português e inglês, guia de uso offline, diagnósticos e
  renderização por GPU ou software.
