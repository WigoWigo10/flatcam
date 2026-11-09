# Refinamento de Lines no NCC e no Paint

Rodada de 9 de novembro de 2026, posterior à Beta 7. Complementa
`LINES_RAW_PATH_AUDIT.md`; os relatórios e instaladores daquela versão foram preservados.

## Comportamento

- **Contour ligado:** retira do raster somente a interseção colinear de
  comprimento positivo com o contorno de acabamento. Mantém as voltas completas
  do Contour, a direção das passadas e a sobreposição definida pelo usuário.
  Não une trajetos através de cobre e não faz união espacial dos caminhos brutos.
- **Contour desligado:** verifica cada componente do espaço seguro para o centro
  da fresa. Se nenhum trajeto o alcança, acrescenta uma passada paralela pelo seu
  ponto interior, recortada ao próprio componente. Mantém a direção automática
  ou o ângulo escolhido, sem ativar Contour. No Combo, preserva o fallback para
  Seed/Standard quando o raster inteiro está vazio.
- **Connect:** mantém a política de conexão segura. Pode repetir um trecho para
  reduzir levantamentos; este refinamento não promete eliminar esses retornos.
- **Rest:** mantém o cálculo das áreas efetivamente varridas, sua ordem de
  preparação e a subtração antes da próxima fresa. Fora do Rest, evita calcular
  buffers e a união dessas áreas, pois nenhum consumidor os utiliza.

São melhorias deliberadas sobre o comportamento legado do Python, que também
reproduziu o raster coincidente e as pequenas regiões omitidas. Igualdade de
caminhos com o Python deixa de ser o critério de aprovação nesses casos; os
critérios são os trajetos, os espaços seguros, o acabamento e a exportação.

Commits de implementação: **0c3aaf3e** (núcleo) e **57cb94f3** (interface).

## Diagnóstico na interface

NCC e Paint apresentam **Regiões da última execução — Lines / Combo**, por fresa:

1. **Fresa não cabe:** o desconto do raio elimina o espaço para o centro da
   ferramenta naquele polígono. Uma fresa menor do Rest pode alcançá-lo depois.
2. **Varredura complementada:** componentes que receberam a passada adicional.
3. **Alcançáveis sem trajeto:** componentes com espaço para a fresa, mas sem
   passada produzida pelo método.

Os botões mostram uma categoria por vez no Plot Area. O painel informa que se
refere aos parâmetros enviados na última execução, não a mudanças posteriores
nos campos. Nova geração e troca/fechamento da ferramenta limpam os destaques.
As descrições, quebras de linha e ajuda acessível existem nos quatro idiomas.

O destaque de complemento mostra o **espaço do centro da fresa**, não toda a
área usinada. Ter uma passada não comprova cobertura integral: use a análise de
cobertura do CNC Job para avaliar o material remanescente. Contour desligado
pode deixar bordas sem acabamento. A contagem genérica do console passou a
dizer “polígonos sem trajetos”, sem chamar toda região estreita de falha.

## Evidências

- `LinesRefinementTest`: 26 casos incluindo NCC/Paint, MM/IN, ângulos, ilha
  pequena isolada, componente desconectado após erosão, diferenciação entre
  fresa grande e região omitida, Rest por fresa e exportação/releitura de G-code.
- Os 16 casos iniciais de reprodução falharam antes da correção e passaram
  depois. O primeiro rascunho do fixture NCC usava cobre vazio e foi corrigido
  antes desse registro válido; seu log não conta como reprodução.
- A mesma sonda arredondada, executada contra os JARs intactos da Beta 7,
  confirma os **35,2 mm** repetidos com Connect desligado e Contour ligado.
  No candidato, NCC e Paint apresentam **zero** repetição nesse controle.
- Nos 16 controles arredondados, os testes geométricos não encontraram
  interseção com o interior dos pads, segundo a tolerância documentada pela
  auditoria. Com Contour ligado, a maior área alcançável descoberta foi
  aproximadamente **0,000000185%**. Isso é uma medição desse fixture, não de
  todas as placas nem de uma máquina física.
- Na Breadboard Power Supply, as **13** regiões alcançáveis antes omitidas
  receberam complemento. As **quatro** regiões onde a fresa de 0,4 mm não cabe
  continuam separadas no diagnóstico, em NCC e Paint.
- A otimização de footprint manteve WKT bruto e diagnósticos idênticos nos
  **12** resultados disponíveis para comparação direta antes/depois.

### Matriz comunitária e tempo

Os **64 casos** geraram trajetos: oito placas × NCC/Paint × Connect × Contour,
nas mesmas referências diagnósticas do inventário original. Na primeira rodada
otimizada, **60/64** concluíram dentro do orçamento cooperativo de 90 segundos.
Os quatro casos Connect de Horizon Choc foram repetidos com 300 segundos e
concluíram em aproximadamente **137, 111, 112 e 120 segundos**. Isso continua
sendo um limite de desempenho, mesmo havendo resultado válido. Não é um teste
de FPS nem uma garantia de tempo em outros computadores.

A auditoria independente final examinou os **64 resultados**:

- **Zero** componentes alcançáveis sem trajeto além da tolerância numérica
  declarada abaixo. Quatro configurações de Horizon Choc retêm o contador
  exato de uma componente com diferença de ULP e sua classificação separada.
- **Zero** pontas abertas inesperadas no interior, na tolerância do auditor
  de 1% do diâmetro. Isso não substitui simulação nem cobertura por área.
- Casos com repetição de centro acima de **0,000001 mm**: **34 → 22**.
  Os 22 restantes têm Connect ligado; todos os controles com Connect desligado
  ficaram abaixo desse limite. O número é de configurações diagnósticas,
  não de peças ou programas de máquina.
- Cobertura integral de material: **NOT_MEASURED** em todos os casos comunitários.
  A medição de cobertura por área desta rodada limita-se ao fixture arredondado.

Seleção dos resultados e SHA-256 de cada tentativa:
`target/lines-refinement-20261109/community-input-manifest.json`.
Dados escolhidos em `validated-community/`; avaliação em
`community-final-audit.json`. As primeiras tentativas e seus limites de tempo
permanecem em `optimized-community/`, com repetições em `connect-retry/`.

Com fresa equivalente a 0,4 mm, Connect e Contour desligados, o diagnóstico NCC
mostrou estas contagens por placa:

| Placa | Fresa não cabe | Componentes complementados |
| --- | ---: | ---: |
| Breadboard Power Supply | 4 | 13 |
| Horizon MX | 0 | 7 |
| Horizon Choc | 0 | 19 |
| Air Quality Sensor | 172 | 65 |
| Audio Watermark | 1 | 15 |
| Modxo Adapter | 10 | 4 |
| Capacitive Keypad | 15 | 0 |
| Single Sided 5V | 0 | 0 |

As contagens de complemento são de **componentes após descontar o raio**.
O relatório anterior contava polígonos inteiros sem nenhum caminho: um polígono
podia ter algumas passadas e ainda abrigar outras ilhas sem trajeto. São
critérios diferentes, não uma mudança no diâmetro ou arredondamento da entrada.

### Precisão do auditor independente

O GEOS marcou um componente de Horizon Choc como exatamente sem interseção,
mas havia uma passada de aproximadamente **0,2059 mm** ao longo de sua borda,
a **3,55 × 10⁻¹⁵ mm** dela. O auditor preserva esse contador exato e registra
separadamente componentes atendidos dentro da tolerância numérica:
`max(diâmetro × 10⁻¹⁰, 32 × ulp(maior magnitude das coordenadas ou 1))`.
Exige ainda um trecho maior que `max(16 × tolerância, diâmetro × 10⁻⁶)`;
um simples contato de ponta não é aceito como passada.

O auditor v4 tem **seis testes**, incluindo a diferença de uma ULP e o contato
de ponta. O teste de uma ULP falha com o auditor v3 intacto e passa no atual.
Esse tratamento só afeta o relatório: não desloca, arredonda ou acrescenta
nenhum ponto aos trajetos do FX.

### Validação do código e interface

O reator completo executou **2.468 casos**. Uma falha foi do auxiliar de busca
do teste da referência NCC ao percorrer um `TitledPane` vazio; depois da correção,
**86 casos afetados** passaram em uma nova execução de `verify`. Os **sete**
casos do diagnóstico foram repetidos após o ajuste visual final, também com
`BUILD SUCCESS`. A consolidação por classe desta rodada tem **2.450 aprovados,
18 opcionais ignorados e zero falhas/erros**. Isso não é uma alegação de que
aquele primeiro log integral terminou verde: os logs e a reexecução estão
preservados. O XML antigo de `LauncherConfigurationTest`, não executado nesta
rodada, fica fora dessa soma.

Logs: `target/lines-refinement-final-verify-2.log`,
`target/lines-refinement-ui-final-verify.log` e
`target/lines-refinement-ui-labels-verify.log`.

As **96 verificações de distância** passaram com as classes finais, incluindo
Rest, duas fresas, ângulos e MM/IN. O inventário de tradução tem **3.956 chaves**
completas nos quatro idiomas. Capturas dos quatro temas em
`flatcam-fx/target/clearing-region-review/`; os temas escuro e claro foram
inspecionados, além das asserções de largura de 340 px.

Dados da rodada: `target/lines-refinement-20261109/`. O diretório `community`
contém a primeira tentativa, interrompida ao investigar o custo das áreas
varridas; os limites de tempo registrados permanecem ali. A matriz otimizada
fica em `optimized-community`. Não confundir tentativa interrompida ou caso
que atingiu orçamento com aprovação. A primeira suíte completa foi interrompida
para integrar a otimização; a seguinte revelou um teste reflexivo com a assinatura
privada antiga de `clearArea`. O teste foi atualizado para solicitar explicitamente
o footprint do Rest, mantendo seu mesmo oráculo geométrico.
Um auxiliar de busca de controles do teste `MainCamFlowTest` também passou a
aceitar `TitledPane` sem conteúdo antes da primeira geração, um estado válido
do JavaFX. As verificações do objeto de referência permanecem inalteradas.

## Repetir

No diretório `flatcam-next`, em PowerShell, após compilar:

```powershell
.\mvnw.cmd verify
java -Xmx4g --class-path 'flatcam-cam/target/classes;flatcam-fx/target/dependency/*' tools/LinesCommunityProbe.java samples/community/validation/fx-import.json target/NEW-REFINEMENT/community
target\oracle-py311\Scripts\python.exe tools/audit_lines_paths.py --community target/NEW-REFINEMENT/community --skip-coverage --output target/NEW-REFINEMENT/community-audit.json
target\oracle-py311\Scripts\python.exe -m unittest discover -s tools -p test_audit_lines_paths.py
.\tools\audit-cam-related.ps1 -Lines -ClassOverrides .\flatcam-cam\target\classes -OutputDirectory .\target\NEW-REFINEMENT\distance
python tools/write_ui_catalogs.py --strict --check
```

A sonda aceita `-Dflatcam.audit.caseFilter=...` para selecionar IDs por expressão
regular e `-Dflatcam.audit.caseBudgetSeconds=...` para orçamento por caso entre
zero exclusivo e 600 segundos. Use um diretório novo: resultados existentes
não são sobrescritos. O orçamento é cooperativo; uma operação geométrica
individual pode demorar para devolver o controle ao cancelamento.

O auditor mede duplicação antes da união, pontas internas e componentes sem
trajetos de forma independente com GEOS. `--skip-coverage` deixa a área total
varrida explicitamente como `NOT_MEASURED`, inclusive quando todos os componentes
recebem trajetos. Os limites físicos do corpus continuam sendo retângulos
diagnósticos dos limites do cobre, não contornos de PCBs prontos para fabricar.
