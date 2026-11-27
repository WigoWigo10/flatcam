# FlatCAM FX Beta 11

27 de novembro de 2026. Versão para testes práticos em Windows x64, com Java
incluído.

- **Geometry → CNC Job:** marque **Incluir** para escolher quais fresas
  entram em cada arquivo. É possível exportar, por exemplo, dois passes de
  uma fresa em V juntos e a limpeza de 1 mm em outro programa, a partir da
  mesma Geometry. A revisão e o programa usam a numeração da origem.
- **Editor de Geometry:** a tabela identifica a fresa de cada trajeto. O
  filtro vale também no Plot Area. **Selecionar visíveis** permite apagar
  ou atribuir os trajetos do grupo a outra fresa em uma ação com Desfazer.
- **Editor Excellon:** filtre por ferramenta para ver e selecionar em lote
  seus furos e rasgos na tabela e no Plot. A edição em lote entra no histórico.
- **Solder Paste:** as caixas de seleção definem quais bicos participam da
  criação dos trajetos. O CNC Job usa os trajetos gravados na Geometry
  escolhida.
- O pacote inclui `CHANGELOG.md` com o histórico desde a primeira Beta do FX.

## Instalar ou atualizar

Feche o FlatCAM FX e execute o MSI `FlatCAM-FX-0.1.0-beta.11-windows-x64-setup.msi`
ou o instalador EXE, na mesma conta do Windows da instalação anterior. O
MSI 0.1.110 usa a família de atualização da Beta 10 (MSI 0.1.100).
Mantenha projetos e arquivos pessoais fora da pasta do aplicativo.

Para usar a versão portátil, extraia todo o ZIP e abra `FlatCAM FX Beta.exe`.
Os pacotes são distribuídos sem assinatura digital.

## O que conferir

Em uma cópia do projeto, gere dois programas a partir de uma Geometry com
várias fresas e confira os identificadores e os parâmetros de cada passe.
No Excellon, teste a seleção em lote com dois diâmetros e confira o resultado
ao desfazer. No Solder Paste, gere trajetos com um único bico marcado e confira
os pads atendidos.
