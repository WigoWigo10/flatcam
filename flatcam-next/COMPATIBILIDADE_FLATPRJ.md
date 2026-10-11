# Projetos Python 8.994 e FX

## Abrir e salvar de volta sem perder nada — 2026-10-10

Um `.FlatPrj` guarda muito mais do que o FX modela: ~550 preferências da aplicação, dezenas de opções por objeto,
macros de abertura, formato do Excellon, parâmetros do CNC Job, objetos de script e documento. Até aqui, salvar pelo
FX reescrevia só o que ele modela: no projeto real de referência sobravam 1 das 550 preferências e 164 das 1121 opções
dos objetos, e 12 campos de objeto sumiam.

Agora o FX **lembra o arquivo que abriu** (`ProjectFile.PythonLegacy`, os bytes originais) e, ao salvar em `.FlatPrj`,
compara três versões (`PythonLegacyMerge`): o arquivo original, o que o FX escreveria para o projeto exatamente como
abriu, e o que escreve agora. Onde as duas últimas coincidem, o FX não mexeu e vale o valor original, como estava;
onde diferem, vale o do FX. A comparação desce em `options`, `tools` e nos dados de cada ferramenta.

- **Projeto não alterado:** todos os valores do original são mantidos (projeto real: 21 objetos, 1541 valores de
  objeto e 550 preferências, zero diferenças), na ordem original dos objetos.
- **Objeto alterado no FX:** só o que mudou é substituído. Ex.: ocultar um Gerber muda só `options.plot`; deslocar um
  Excellon muda `solid_geometry` e os furos/sólidos das ferramentas, e remove `source_file` (o texto-fonte deixaria de
  descrever os furos). O resto do objeto fica como estava.
- **Renomear, excluir, criar:** o objeto renomeado é reconhecido e mantém o que tinha; o excluído sai; o novo entra
  no fim, escrito pelo FX.
- **Scripts e documentos do Python** (`kind` `script`/`document`): o FX não os exibe (avisa ao abrir), mas eles
  continuam no arquivo, no mesmo lugar. Antes, um projeto com eles nem abria.
- **Passando pelo formato nativo:** salvar em `.fcnproj` leva junto o original Python; reabrir e salvar em `.FlatPrj`
  continua preservando tudo.
- O diálogo Salvar Projeto já propõe `.FlatPrj` quando o projeto aberto veio do Python.

Validação (sem abrir janelas do Python):

- `tools/compare_flatprj_roundtrip.py original salvo --strict`: comparação JSON valor a valor, ignorando só os
  metadados do FX (`_fx_format`, `_java`). Projeto real aberto e salvo pelo aplicativo FX ao vivo (teste
  `optionalRealPythonProjectSurvivesALiveSave`): 0 diferenças; versão editada (um Gerber ocultado, um Excellon
  deslocado, um CNC Job excluído, uma Geometry nova): só as diferenças esperadas.
- `tools/validate_flatprj_python.py` com Python 3.11 + Shapely 1.8.5: os serializadores reais do Python
  (`dict2obj`, `from_dict`, `to_dict`, parser de G-code) aceitam o original, o salvo e o editado. O validador foi
  corrigido: ele rejeitava o próprio projeto original (macros de abertura, ferramenta sem `slots`, geometria de
  ferramenta que não é lista).
- Testes: `PythonLegacyMergeTest` (núcleo) e três casos novos em `TclLiveHostTest` (aplicativo ao vivo).

O que ainda **não** é compatibilidade total:

- O FX continua adicionando seus metadados (`_fx_format`, `_java`); o Python os ignora e os descarta ao salvar. O
  arquivo fica maior (projeto real: 1,9 MB → 4,9 MB).
- O que o FX altera é escrito do jeito dele: um objeto editado no FX perde, nas partes editadas, detalhes que só o
  Python tinha (por exemplo, o `source_file`).
- Continuam recusados na exportação: exclusões de Drilling/CNC, sondagem, programas ICP/HPGL/Roland e projetos com
  unidades mistas, mesmo que o objeto não tenha sido tocado.
- As preferências do projeto são preservadas, **não aplicadas**: o FX não passa a usar as 550 opções do Python.
- A conferência **visual** na janela do FlatCAM Python continua por fazer: o aplicativo real foi executado fora da
  tela (seção abaixo), o que prova abrir, listar, calcular limites e salvar, não como o plot aparece.

## Conferido no aplicativo FlatCAM Python real — 2026-10-10

`tools/open_in_python_app.py` inicia o **aplicativo** (`app_Main.App`, com a coleção de objetos e os próprios
`open_project`/`save_project`), não só os serializadores. Roda fora da tela (plataforma Qt "offscreen", `--headless=1`)
e isolado: a pasta de configurações e o QSettings são redirecionados para uma pasta temporária, então as preferências
e as listas de recentes do usuário não são lidas nem gravadas (conferido pelas datas dos arquivos em `%APPDATA%\FlatCAM`).

Cadeia executada com o projeto real (21 objetos), Python 3.11 + Shapely 1.8.5:

1. O FX (aplicativo ao vivo, em teste) abre o original e salva `real-live-roundtrip.FlatPrj` e uma versão editada
   (Gerber ocultado, Excellon deslocado, CNC Job excluído, Geometry nova).
2. O aplicativo Python abre cada um, sem mensagens de erro, com as contagens certas, calcula os limites de todos os
   objetos e **salva o projeto de novo**.
3. O que o Python grava depois de abrir o arquivo do FX é idêntico ao que grava depois de abrir o original
   (0 diferenças em 21 objetos / 1541 valores / 550 preferências).
4. O FX reabre os arquivos regravados pelo Python e os salva outra vez sem perder nada (0 diferenças).

Dois defeitos reais apareceram nessa cadeia e foram corrigidos:

- **Geometry vazia criada no FX:** o Python abria o projeto, mas não conseguia mais salvá-lo ("Out of range float
  values are not JSON compliant"): com `solid_geometry: []` ele calcula limites infinitos. O FX agora escreve a
  Geometry vazia como o próprio Python escreve a dele (`solid_geometry: null`, multigeo, limites zero).
- **Geometry vazia criada no Python ("New Geometry"):** o FX recusava o projeto inteiro ("Geometry sem caminhos da
  ferramenta 1"). Agora abre como uma Geometry vazia.

```powershell
& target\oracle-py311\Scripts\python.exe tools\open_in_python_app.py --legacy-root .. <arquivo.FlatPrj> `
    --resave target\saida.FlatPrj --report targetelatorio.json
python tools\compare_flatprj_roundtrip.py <original.FlatPrj> <salvo.FlatPrj> --strict
```

## Formatos no diálogo Salvar Projeto

No diálogo **Salvar Projeto**, o FX oferece o formato nativo `.fcnproj` e o formato
de compatibilidade `.FlatPrj`. O formato nativo continua recomendado como arquivo
de trabalho e backup. A exportação de compatibilidade usa publicação por arquivo
temporário: uma falha de validação não sobrescreve o destino.

## O que é exportado

- Gerber: geometria resolvida, aperturas/elementos disponíveis, cores e visibilidade.
- Excellon: diâmetros, furos, slots, sólidos por ferramenta e parâmetros de furação disponíveis.
- Drilling com Start Z/posição de troca explícitos (sem exclusões): chaves comuns
  Python em `options` e `tools.data`, além do snapshot nativo FX. A reabertura
  direta no FX preserva as posições. A UI Python pode sobrescrever parâmetros
  com preferências globais, e reimportação após remover metadados FX ainda não
  recupera todas as posições comuns de Drilling. Não declarar round-trip universal.
- Geometry: caminhos por ferramenta, perfil, ponta V e parâmetros CNC disponíveis.
- Geometry/CNC: compensação Path/In/Out/Custom e posições comuns de início,
  fim e troca; campos CAM padrão são recuperados mesmo sem metadados privados FX.
- CNC Job: G-code integral e geometria de prévia compatível com o leitor Python.

Os objetos ficam na lista `objs`, não apenas numa extensão privada que o Python
ignoraria. JSON e XZ são suportados. Metadados privados preservam configurações
nativas na reabertura direta pelo FX. O Python descarta os metadados privados da
raiz ao salvar; os campos CAM padronizados continuam disponíveis no reimportador.
Em Geometry sem ferramentas associadas, o diâmetro/perfil CNC fica nas configurações
e não transforma o objeto em uma Geometry multigeo com diâmetro fixo.
Alturas automáticas de fim/troca são exportadas como valores explícitos para o
Python. O FX faz o estacionamento XY em altura segura antes de descer ao End Z;
o Python pode executar esses movimentos em outra ordem. Ver `GEOMETRY_CNC.md`.

## Limitações e validação

Não é um round-trip universal e sem perdas de todas as preferências e extensões
do Python: opções globais, configurações exclusivas FX e opções avançadas não
representadas no modelo não têm equivalência completa. A definição original de
macros Gerber não é retida pelo modelo atual; geometria resolvida não equivale à
fidelidade integral do editor de macros. Mantenha o original e uma cópia nativa.

Programas ICP/HPGL/Roland, prévias não seguras e Geometry configurada com sondagem
são recusados pelo exportador legado; use o formato nativo. Ferramentas V precisam
de configuração explícita da ponta. Projetos realmente mistos MM/IN são recusados,
pois o Python converte objetos para a unidade global ao abrir.

Importação Excellon: `units` descreve as coordenadas atuais do objeto;
`excellon_units` pode conservar a unidade do cabeçalho original. A prioridade foi
corrigida após o projeto real apresentar `units=MM` e `excellon_units=INCH`.

Validação automatizada realizada sem abrir janelas:

- Testes Java: JSON/XZ, quatro tipos de objeto, campos CNC individuais, preservação
  de G-code, cores e geometrias; falha não sobrescreve destino.
- Python 3.11.0 + Shapely 2.1.2: `camlib.dict2obj`, `FlatCAMObj.from_dict`,
  `to_dict`, cálculo de bounds e parser real de G-code. Campos serializados e
  geometrias são comparados após reserialização (tolerância geométrica 1e-9).
- Projeto autorizado do usuário: 3 Gerbers, 3 Excellons, 3 Geometries e 9 CNC Jobs.
  O arquivo original não foi modificado; artefatos ficam em `target/python-compat`.
- Arquivo de exemplo salvo novamente pelos serializadores Python e reaberto pelo FX.
- Exemplo Geometry/CNC com Custom Offset, Start/End e posições de troca
  reserializado pelo Python e reaberto pelo FX com os mesmos parâmetros/G-code.

O teste Python usa o código real de serialização e parsers com o stub já existente
no legado, não a janela completa. A conferência visual no aplicativo Python ainda
é necessária antes de declarar equivalência completa de UI/usinagem.

## Reproduzir

Os testes normais não dependem do projeto privado. Para gerar artefatos de integração,
execute `PythonProjectWriterTest` com `-Dflatcam.compat.output=<diretorio>` e,
opcionalmente, `-Dflatcam.compat.realProject=<projeto-original>`.

Com o ambiente Python legado, na raiz de `flatcam-next`:

```powershell
& ..\.venv\Scripts\python.exe tools/validate_flatprj_python.py --legacy-root .. <arquivo.FlatPrj>
```

`--resaved <arquivo>` grava um artefato reserializado pelo Python. O teste
`optionalPythonResavedArtifactRestoresPerToolCncAndAllObjects` verifica o exemplo
de quatro objetos com `-Dflatcam.compat.resaved=<artefato>`.
# Consolidação dos fluxos (2026-10-07)

Gerber e Geometry também normalizam os rótulos MM/METRIC e IN/INCH, sem
reescalar coordenadas já gravadas na unidade atual do objeto. Unidades
desconhecidas são recusadas, não assumidas como mm. Cut Z positivo opcional
em Geometry/Drilling não é invertido: geometria permanece, defaults inválidos
são ignorados e o aviso de restauração parcial de dados CAM continua presente.
`PythonMainFlowImportTest` cobre oito regressões e persistência nativa/legada.
