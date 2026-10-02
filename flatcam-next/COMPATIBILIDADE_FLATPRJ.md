# Projetos Python 8.994 e FX

No diálogo **Salvar Projeto**, o FX oferece o formato nativo `.fcnproj` e o formato
de compatibilidade `.FlatPrj`. O formato nativo continua recomendado como arquivo
de trabalho e backup. A exportação de compatibilidade usa publicação por arquivo
temporário: uma falha de validação não sobrescreve o destino.

## O que é exportado

- Gerber: geometria resolvida, aperturas/elementos disponíveis, cores e visibilidade.
- Excellon: diâmetros, furos, slots, sólidos por ferramenta e parâmetros de furação disponíveis.
- Geometry: caminhos por ferramenta, perfil, ponta V e parâmetros CNC disponíveis.
- CNC Job: G-code integral e geometria de prévia compatível com o leitor Python.

Os objetos ficam na lista `objs`, não apenas numa extensão privada que o Python
ignoraria. JSON e XZ são suportados. Metadados privados preservam configurações
nativas na reabertura direta pelo FX. O Python descarta os metadados privados da
raiz ao salvar; os campos CAM padronizados continuam disponíveis no reimportador.
Em Geometry sem ferramentas associadas, o diâmetro/perfil CNC fica nas configurações
e não transforma o objeto em uma Geometry multigeo com diâmetro fixo.

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
