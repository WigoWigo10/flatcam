# FlatCAM FX 0.1.0-beta.3

Atualização Beta para testes práticos controlados — 29 de outubro de 2026.

## O que mudou desde a Beta 2

- **Precisão:** confirmar o campo de nova fresa em NCC/Isolation não arredonda
  o valor antes de adicionar. Ponto/vírgula e MM/IN são aceitos. Parâmetros
  importados da Tools Database mantêm a precisão usada pelo cálculo.
- **Retomar:** NCC e Isolation recuperam o formulário da sessão ao reabrir pelo
  botão ou alternar ferramentas. Origem removida/substituída e troca de projeto
  invalidam entradas antigas. Fechar explicitamente descarta aquele formulário.
- **NCC:** mostra os parâmetros enviados para o cálculo e avisa quando o
  rascunho muda. Permite copiar o resumo e comparar versões geradas. Mostrar
  uma versão oculta somente outras saídas NCC da mesma origem, sem excluí-las.
- **Projeto nativo:** salvar `.fcnproj` guarda o rascunho NCC, ferramentas,
  seleção, parâmetros locais da DB e resumos dos resultados. A reabertura
  confere origem/referência antes de reaplicar os valores.
- **Rest:** Connect/Contour comuns respeitam as preferências na abertura e no
  Reset. O painel explica o escopo comum em relação aos parâmetros por fresa.
- **Paint:** diâmetro editável preservando os demais parâmetros da fresa.
  Listas aceitam `0,198; 0,25` ou `0.198; 0.25`; prefira ponto e vírgula.
- **Tabelas:** Delete/contexto usam a ação de remover onde ela é suportada.
  Dentro de um campo, Delete apaga apenas texto. F2/Enter/duplo clique editam
  diâmetro; Enter confirma, Esc cancela e Tab/Shift+Tab percorrem as linhas,
  saindo da tabela nas extremidades. Erros permanecem disponíveis para correção.
- **Foco e contraste:** campo de edição destacado nas quatro paletas, retorno
  à linha vizinha depois de remover e atalhos da tabela separados do Plot.
- **Arquivos:** diálogos de salvar evitam a pasta instalada e sugerem Documentos.

## Atualizar pelo MSI

**Antes de atualizar, mova para Documentos ou outra pasta externa todos os
projetos, G-code e arquivos pessoais que estiverem na pasta do aplicativo.**
O instalador anterior pode remover essa pasta durante atualização/desinstalação.
A nova proteção dos diálogos não move arquivos antigos e não altera o MSI antigo.

Feche o FlatCAM FX Beta e execute
**FlatCAM-FX-0.1.0-beta.3-windows-x64-setup.msi** na mesma conta do Windows usada
na instalação anterior. A família de atualização por usuário foi mantida.
Confira **0.1.0-beta.3** em **Ajuda > Sobre** depois de atualizar.

O pacote inclui Java. Não é necessário instalar JDK, Maven ou Python.
Também são gerados um instalador EXE e um ZIP portátil; no portátil, extraia
todo o conteúdo antes de abrir `FlatCAM FX Beta.exe`. A alternativa
`Iniciar em Software.cmd` usa renderização por software. O modo ativo aparece
na splash e em Ajuda > Sobre > Sistema.

Os pacotes não têm assinatura digital nem atualizador automático. O teste
de distribuição confere os artefatos realmente gerados; as evidências ficam
em `validation.json` e nos logs da pasta de distribuição.

## Roteiro para o testador

1. Use uma cópia do projeto e confira Gerbers, furos, rasgos, contorno e unidades.
2. No NCC/Isolation, adicione e edite uma fresa com diâmetro `0,1981234567`.
   Confirme com Enter e confira o valor. Gere, mude apenas um parâmetro e repita.
3. Alterne ferramentas e retorne pelo botão. No NCC, confira o resumo da geração
   e o aviso de rascunho alterado; compare a versão atual e a anterior.
4. Selecione linhas e use Delete. Teste também Delete dentro do campo em edição,
   Tab/Shift+Tab, Esc e os quatro temas. A regra de manter uma fresa CAM continua
   igual à dos botões de remover; a Tools Database mantém sua confirmação.
5. Salve `.fcnproj`, feche/reabra o projeto e confira o rascunho NCC e as receitas
   nas Propriedades das Geometries. Use salvar explicitamente para guardar o rascunho.
6. Confira panelização alinhada e o fluxo Isolation/NCC/Cutout → Geometry → CNC,
   além de Drilling. Revise profundidades, avanços, spindle e posições antes de
   exportar G-code. Compare resultados com os da Beta anterior.

## Limites desta Beta

- Não é paridade integral com o Python nem certificação física em CNC. Não foram
  alterados os algoritmos de trajetórias nesta atualização. A prévia não simula
  fixação, comprimento real da ferramenta ou todos os comandos do controlador.
- Rascunho persistente implementado para NCC; não é um sistema de presets de
  todas as ferramentas. Alterar só o formulário CAM não muda o aviso de objetos
  não salvos; salvar explicitamente o projeto guarda o rascunho atual.
- Prefira `.fcnproj` para essa continuidade. `.FlatPrj` tem limitações e a extensão
  de dados do FX pode ser ignorada/perdida ao regravar no Python. Uma representação
  de origem/referência incompatível abre os padrões com mensagem, preservando objetos.
- A receita descreve a geração original. Edições posteriores na Geometry não
  recalculam o resumo. As ações de comparação do formulário usam a sessão atual;
  ao reabrir o projeto, consulte os resumos nas Propriedades dos resultados.
- A remoção de fresas CAM não ganhou histórico de desfazer. Geometry/Excellon
  mantêm o histórico de seus editores. Valores iguais não devem apagar outra
  linha que não tenha sido selecionada.
- Tabelas de consulta não permitem apagar metadados de trajetos já calculados.
- O aviso de pasta instalada atua nos diálogos; caminhos explícitos de
  Terminal/API continuam exigindo escolha de uma pasta externa.
- A validação automatizada ocorre no Windows x64 de desenvolvimento e separa
  ensaios realizados, ignorados e não executados. Não substitui segundo PC/VM,
  teste físico CNC ou avaliação manual de todos os fluxos, monitores e drivers.

## Identificação e relato de problemas

A versão exibida é **0.1.0-beta.3**; o Windows Installer usa **0.1.3**, mantendo
a família de atualização das Betas anteriores. O manifesto
`flatcam-release.json` identifica commit, runtime e estado da árvore Git.
`artifacts.json` registra tamanho e SHA-256 dos instaladores e do ZIP.

Ao relatar, informe versão/commit, GPU ou Software, passos, objeto e resultado
esperado. No NCC, use **Copiar parâmetros da geração**. Logs e JFR ficam em
**Ajuda > Diagnósticos > Abrir pasta desta sessão**. Nenhum arquivo é enviado
automaticamente por essa função.

Se houver corrupção, deslocamento inesperado ou parâmetros incorretos, interrompa
o uso da operação e preserve arquivos/logs. Para voltar a uma Beta anterior,
remova a instalação e use uma cópia compatível do projeto. Não presuma que todos
os novos metadados serão preservados por leitores antigos.

## Licenças e fontes

O projeto mantém licença MIT e créditos em Ajuda > Sobre. JARs e runtime mantêm
os respectivos avisos legais. O pacote usa OpenJDK Temurin 25.0.5+8 e as fontes
do aplicativo correspondem à tag **v0.1.0-beta.3**. A publicação em servidor ou
repositório remoto é uma etapa separada da geração local destes pacotes.
