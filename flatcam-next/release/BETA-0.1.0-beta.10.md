# FlatCAM FX Beta 10

27 de novembro de 2026. Beta para testes práticos em Windows x64, com Java
incluído.

- Arquivos Gerber e Excellon importados para um projeto existente usam a
  unidade do projeto. Coordenadas, rasgos e diâmetros do Excellon são
  convertidos juntos. A escala aplicada ao Excellon também atualiza os
  diâmetros das ferramentas.
- CNC Jobs carregados em outra unidade são desenhados na escala do Plot,
  inclusive depois de reabrir o projeto. O G-code mantém a unidade original.
  A prévia avisa quando um programa troca de unidade após já usar coordenadas.
- O Rules Check usa a mesma unidade para as camadas e os limites de
  verificação. O 2-Sided usa a unidade do Plot para o diâmetro e as coordenadas
  dos furos de alinhamento.
- Escalar um Gerber uniformemente atualiza as dimensões das aberturas usadas
  na tabela, na edição e na verificação de regras.
- No Paint, marque **Usar somente fresas selecionadas** para limitar a prévia
  e a geração a uma ou mais linhas da tabela. A opção começa desmarcada.

## Instalar ou atualizar

Feche o FlatCAM FX. Na mesma conta usada para instalar a Beta 9, execute
o MSI `FlatCAM-FX-0.1.0-beta.10-windows-x64-setup.msi` ou o instalador EXE.
O MSI 0.1.100 mantém a família de atualização da Beta 9 (MSI 0.1.90).
Guarde projetos e arquivos pessoais fora da pasta de instalação.

Para usar a versão portátil, extraia inteiramente o ZIP e execute
`FlatCAM FX Beta.exe` na pasta extraída. Os pacotes são distribuídos sem
assinatura digital.
