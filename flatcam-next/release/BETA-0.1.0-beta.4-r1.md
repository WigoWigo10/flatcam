# FlatCAM FX Beta 4 — revisão 1

1 de novembro de 2026. Inclui todas as mudanças da Beta 4 original e acrescenta
a edição direta do diâmetro nas propriedades Excellon.

## Editar os diâmetros dos furos

1. Selecione o objeto Excellon na árvore e abra suas propriedades.
2. Na tabela Ferramentas, dê duplo clique em **Diameter**, ou selecione a linha
   e pressione **F2/Enter**.
3. Digite o diâmetro positivo com ponto ou vírgula. **Enter confirma**, Esc
   descarta o texto e Tab/Shift+Tab percorrem as ferramentas.
4. Aguarde a atualização do Plot. **Ctrl+Z/Ctrl+Y**, com a tabela em foco,
   desfazem/refazem as alterações confirmadas.
5. Salve o projeto ou exporte Excellon para guardar a mudança.

A alteração vale para todos os furos e rasgos do ID escolhido, mantendo as
posições, os IDs e os parâmetros de usinagem. Ferramentas de diâmetros iguais
não são juntadas. A edição usa a precisão digitada; a exibição usa as casas
configuradas. As linhas de totais não são editáveis.

O cálculo roda em segundo plano. Cancelamento ou troca da origem/projeto
descarta o resultado pendente. Dentro do campo, Ctrl+Z desfaz apenas o texto.
Outra edição/transformação do objeto invalida o histórico de diâmetros, que
vale na sessão e não é salvo no projeto.

O arquivo original não é sobrescrito por editar a tabela. Geometries e CNC
Jobs anteriores também não são regenerados: gere novamente as saídas que
precisarem refletir os novos diâmetros. A precisão do arquivo Excellon
exportado depende das opções de exportação.

## Atualizar

Use **FlatCAM-FX-0.1.0-beta.4-r1-windows-x64-setup.msi**, com o aplicativo
fechado, na mesma conta da instalação anterior. A versão do aplicativo
continua **0.1.0-beta.4**; esta é sua **revisão 1**. O MSI agora usa **0.1.41**
para atualizar tanto a Beta 3 quanto a Beta 4 original (MSI 0.1.4), mantendo
a mesma família de atualização. O pacote original permanece preservado.

O runtime e os JARs da revisão ficam identificados pelo commit de origem e
por `betaRevision=1` no manifesto. Arquivos da distribuição têm `-r1` no nome.
Também há EXE instalador e ZIP portátil com Java incluído.

Mantenha projetos, G-code e dados pessoais fora da pasta instalada: os MSIs
anteriores podem remover arquivos extras dessa pasta durante o upgrade.
No ZIP portátil, extraia todo o conteúdo antes de abrir o executável.

As correções anteriores permanecem: políticas Z Standard/Machinist, Salvar
original/Salvar como cópia no editor G-code, tooltip de Cancelar e histórico
CAM. Detalhes na [nota da Beta 4 original](BETA-0.1.0-beta.4.md).

## Validação e limites

A geração executa a suíte completa e o ensaio da distribuição confere a nova
tabela nos JARs do portátil e dos instalados. Os relatórios identificam o
commit, os hashes e os upgrades efetivamente executados. A compilação inicial
do teste de Plot teve uma chamada inexistente, corrigida e registrada na
documentação de desenvolvimento.

Não há assinatura digital, atualização automática, publicação remota ou
certificação em máquina CNC. Roland e projetos Machinist antigos mantêm as
limitações descritas na Beta 4 original. Testes físicos, todos os monitores/
DPIs e cliques do assistente EXE não fazem parte da validação automatizada.
