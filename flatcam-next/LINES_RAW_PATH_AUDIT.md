# Lines: interrupções e caminhos sobrepostos — 2026-11-09

Pedido: verificar linhas interrompidas/sobrepostas no NCC e Paint, inclusive
nas placas comunitárias. Esta etapa é uma auditoria; não altera a geração de
produção, não cria release e não certifica usinagem física.

## Resultado

Há três fenômenos que não podem ser contados como a mesma falha:

1. **Overlap da largura da fresa:** intencional. Com 40% e fresa de 0,4 mm,
   os centros das passadas ficam separados por 0,24 mm; as faixas removidas
   precisam se sobrepor. Centros distintos não são caminhos duplicados.
2. **Interrupção no limite permitido:** necessária para contornar cobre,
   separar ilhas ou levantar a fresa quando não há conexão segura. Unir
   essas pontas por uma reta poderia atravessar cobre. Não foram encontradas
   pontas de caminhos abertos terminando no interior da área segura nos
   64 controles comunitários e nos 16 controles sintéticos, dentro da
   tolerância de 1% do diâmetro (0,004 mm na matriz comunitária).
3. **Repetição do mesmo caminho central:** confirmada. A união geométrica
   esconde essa multiplicidade; por isso a auditoria usa os caminhos brutos.
   Há duplicação entre raster e Contour, além de recaminhamento em Connect.

No exemplo público arredondado de 36 × 24 mm, fresa 0,8 mm, overlap 40%,
Connect desligado e Contour ligado, o mesmo segmento de **35,2 mm** aparece
na primeira passada do raster e no contorno externo. A interseção positiva
entre os dois caminhos está guardada no relatório. O núcleo original
Python `clear_polygon3`, na mesma área Paint explícita, também repete 35,2 mm.

Um programa FX de uma passada, sem Extra Cut ou Multi-Depth, foi gerado e
lido pelo parser original Python. Foram observados aproximadamente
**35,21 mm de corte XY repetido**, portanto esse exemplo não é só um
efeito de desenho do Plot. O teste usa o perfil portátil padrão; não cobre
todos os perfis, máquinas ou configurações. A pequena diferença em relação
à Geometry não foi atribuída a uma causa sem investigação adicional.

Imagem inspecionada: `target/lines-path-audit-20261109/repeated-centerline.png`.

## Placas da comunidade

Foram gerados **64 casos**: oito placas × NCC/Paint × Connect ligado/desligado
× Contour ligado/desligado. Uma face de cobre por placa, fresa **0,4 mm**,
overlap **40%**, direção automática, uma ferramenta, sem Rest. A referência
é explicitamente o retângulo dos limites do cobre, com margem zero; trata-se
de uma configuração diagnóstica, não do contorno físico de uma PCB pronta
para fresar. Originais preservados.

Todos os 64 geradores retornaram resultados, sem exceção ou cancelamento
pelo limite de 90 s por caso. Isso não significa que todos os polígonos
tenham produzido trajetos: regiões estreitas/pequenas estão contabilizadas.
**34/64** contêm comprimento central repetido acima de 0,0000001 mm.
Nenhum caso com Connect e Contour ambos desligados apresentou repetição
acima desse limiar.

Valores NCC em milímetros (Paint apresentou o mesmo padrão, com pequenas
diferenças de escolha dos conectores):

| Placa / face selecionada no inventário | Repetição só com Contour | Repetição com Connect + Contour |
|---|---:|---:|
| Breadboard Power Supply | 15,8663 | 63,0754 |
| Horizon MX | 9,4147 | 746,7692 |
| Horizon Choc | 19,5739 | 601,7798 |
| Air Quality Sensor | 34,1235 | 43,8804 |
| Audio Watermark | 77,5131 | 417,4130 |
| Modxo Adapter | 0,8659 | 30,6864 |
| Capacitive Keypad | abaixo do limiar | 4,3091 |
| Single-Sided 5V | 26,0700 | 96,9678 |

O máximo, Horizon MX NCC, corresponde a **3,6831% de 20.275,3293 mm** de
caminho bruto. Isso mede distância repetida, não tempo de máquina: um conector
pode repetir parte de uma área já cortada para economizar levantamentos em Z.
Um Contour também pode ser um passe de acabamento deliberado. A contagem não
prova que remover qualquer repetição melhore tempo ou acabamento.

## Regiões sem caminho e cobertura

Com Contour desligado há regiões que admitem o centro da fresa, mas não
recebem uma passada do raster. Isso é distinto de pontas soltas no meio de
uma linha. Na Breadboard Power Supply, 49 polígonos compõem a área:

- Quatro têm erosão vazia pelo raio da fresa e não admitem essa fresa.
- Sem Contour, 17 polígonos não produziram trajetos: **13 são alcançáveis**.
- Com Contour, só os quatro inacessíveis ficam sem trajetos.

O núcleo Lines original Python, na mesma área explícita dessa fonte,
reproduziu exatamente esses totais: 13 alcançáveis sem caminho sem Contour;
zero com Contour. Não é um novo ensaio de importação GUI Python da placa.

O diagnóstico por erosão encontrou também excesso de polígonos sem caminho
sem Contour no sensor (6), Horizon MX (7), Horizon Choc (7) e adaptador (1).
Com Contour, a contagem de falhas dessas oito entradas coincide com os
polígonos de erosão vazia. Isso não demonstra cobertura completa de todos
os polígonos que receberam algum caminho.

A cobertura por área varrida foi medida nos 16 controles arredondados e
permanece registrada no relatório anterior. **A matriz comunitária final
não mede cobertura integral por área**: tem `coverageStatus=NOT_MEASURED`.
Uma tentativa parcial de calcular todos os buffers foi interrompida por
custo, sem contar seus resultados como uma matriz concluída. Não trocar
essa ausência de medição por “zero regiões sem corte”.

## Método e evidência

- `LinesCommunityProbe.java`: exporta caminhos brutos e área de limpeza;
  preserva a fonte e escreve cada caso separadamente. Arquivos em
  `target/lines-path-audit-20261109/community/`.
- `audit_lines_paths.py`: comprimento bruto menos comprimento da união
  espacial mede repetição; a união é usada somente como comparação.
  Não deduplica a Geometry do aplicativo. Extremidades abertas são comparadas
  com a fronteira da área erodida; contornos fechados não são “pontas soltas”.
- A distância usa um índice de trechos da fronteira, mantendo todas as
  coordenadas. Não simplifica ou arredonda a geometria. Cache inclui hash
  SHA-256 da entrada, versão da auditoria e separação de modo raw/coverage.
- Três testes verificam detecção de repetição reversa, overlap de faixa sem
  duplicação de centro e distinção entre término correto no pad e ponta
  artificial truncada no interior. A primeira tentativa tinha um retângulo
  degenerado no fixture (ordem de argumentos de `box`); corrigido com
  verificação de área positiva, mantendo a asserção de truncamento.
- Controle Python: 3.11.1 / Shapely 1.8.5.post1 / GEOS 3.10.3, métodos
  originais, sem alterar fonte ou preferências. Os ensaios Python brutos
  são diagnósticos de kernel, não uma nova certificação dos fluxos GUI.

Arquivos decisivos em `target/lines-path-audit-20261109/`:

- `community-audit.json`: 64 casos, zero extremos inesperados, cobertura
  integral explicitamente não medida.
- `rounded-audit.json`: caminhos brutos e cobertura dos 16 controles sintéticos.
- `python-kernel-raw.json`: quatro controles Paint/MM do kernel original.
- `empty-regions.json`, `python-breadboard-empty-regions.json`: diagnóstico
  das regiões sem caminhos.
- `gcode-retrace.json`, `retrace-one-pass.nc`: verificação da repetição na
  exportação de uma passada.
- `summary.json`: resumo consolidado.

Para repetir os controles comunitários, depois de baixar/importar a coleção:

```powershell
java -Xmx4g --class-path 'flatcam-cam/target/classes;flatcam-fx/target/dependency/*' tools/LinesCommunityProbe.java samples/community/validation/fx-import.json target/NEW-LINES-AUDIT/community
.\target\oracle-py311\Scripts\python.exe tools/audit_lines_paths.py --community target/NEW-LINES-AUDIT/community --skip-coverage --output target/NEW-LINES-AUDIT/community-audit.json
.\target\oracle-py311\Scripts\python.exe -m unittest discover -s tools -p test_audit_lines_paths.py
```

## Refinamento recomendado

Preservar as interrupções necessárias e o overlap configurado. Investigar
a remoção do trecho de raster já coberto por um contorno que será executado,
mantendo o contorno de acabamento contínuo. Em Connect, comparar o custo do
recaminhamento com o de levantar a fresa; não remover uniões cegamente.
Para Lines sem Contour, informar as regiões alcançáveis sem trajetos e
avaliar preenchimento complementar, em vez de prometer cobertura completa.
Essas mudanças precisam de controles de cobertura, origem, ordem/levantamentos
e exportação; não foram implementadas nesta auditoria.
