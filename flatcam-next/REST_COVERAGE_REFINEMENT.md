# Lines/Rest: correção dos trajetos e da medição — 2026-11-10

Continuação dos itens 1 e 2 solicitados após `513cbccb`. Desenvolvimento para
a próxima Beta conjunta; sem bump, tag ou instalador nesta rodada.

## Reprodução e causas

Havia três questões diferentes nos números anteriores:

1. **Buffer de contorno fechado minúsculo.** O analisador de cobertura usava
   diretamente `LineString.buffer`. Em um anel quase colapsado da placa Air
   Quality Sensor, o buffer descartava parte do disco da fresa. Um teste mede
   **0,1134001 mm²** incorretamente restantes dentro de um disco de raio 0,19 mm
   centrado em um vértice de uma trajetória com fresa de 0,4 mm. O programa já
   passava pelo vértice; acrescentar cortes nesse caso seria corrigir a medição
   com usinagem desnecessária.
2. **Rest limitava os centros à sobra erodida.** Num retângulo 10×4 mm, uma fresa
   0,8 deixa cantos que a 0,4 alcança. A sobra isolada não admite o disco inteiro
   da 0,4; o centro precisa passar por espaço já limpo. O código antigo impedia
   isso. O ponto (0,09; 0,09) fornece uma testemunha independente dos buffers.
3. **Encaixe exato e ponto flutuante.** Canais de largura igual à fresa podem
   produzir uma área de centros com poucos ULPs de largura. A abertura nominal
   não é prova de acesso com interior estável. Esses casos continuam nos dados
   brutos; não são escondidos nem classificados automaticamente como limpos.

Os testes foram escritos e executados antes das correções. Não havia uma revisão
boa conhecida para bisect: são limitações reproduzidas em `513cbccb`. Os logs
`red.log` e `connect-red.log` preservam as falhas. A contraprova com o JAR congelado
encontra **7/7 falhas**; os mesmos sete controles passam nas classes finais.

## Correção

`CutterSweep` divide um caminho fechado em duas cadeias abertas para preservar
as tampas circulares, unindo seus buffers. O interior de um polígono continua
sem ser considerado usinado: somente suas trajetórias de contorno contam.
Nenhuma coordenada de corte é alterada para resolver o defeito do analisador.

No Rest de **Lines, Combo e Lines por regiões**, as trajetórias candidatas são
calculadas na área permitida original de cada fresa, incluindo margem/offset.
Uma máscara derivada da sobra seleciona os trechos necessários. O centro pode
sobrepor espaço já removido; cobre e borda originais continuam proibidos.
Connect também usa esses limites originais. Fresamento, diâmetro e ângulo não
são arredondados ou reduzidos para fazer um canal caber.

A área contabilizada no Rest usa a fresa nominal e buffers Q64, sem a expansão
de raio e reparo positivo do NCC legado. Uma tolerância proporcional à flecha
Q64 remove franjas muito finas **da seleção de trabalho**, não dos relatórios
brutos nem dos limites que autorizam cortar. Standard/Seed conservam sua
política histórica; o teste de MultiPolygon.buffer(+1e-7) continua protegendo-a.
Isso é uma melhora intencional em relação ao Rest raster do Python, não uma
alegação de igualdade de trajetos com o legado.

`MachiningReachability` mantém acesso nominal, inalcançável e núcleo nominal.
Acrescenta acesso estável e acesso sensível à precisão. A tolerância de centros
é `max(diâmetro × 1e-8, 64 ULPs da magnitude das coordenadas)`; a área estável é
obtida exigindo esse pequeno interior adicional, mantendo o raio físico na
dilatação. Não é uma certificação de folga mecânica ou arredondamento CNC.

## Validação

Comparação antes/depois com **o mesmo analisador corrigido** nos dois lados:

- 16 controles sem Rest: oito placas × NCC/Paint, Connect/Contour ligados.
  Reutilizam os caminhos anteriores, que não foram modificados por esta correção.
- 32 controles Rest: oito placas × NCC/Paint × MM/IN, fresas 0,8/0,4 mm,
  Connect/Contour, ângulos 37,5°/90° em combinação pareada.
- Exportação GRBL_11_no_M6 por fresa e combinada, com medição da sobra do G-code.
- Alvos e diâmetros devem permanecer iguais antes/depois. O relatório reprova
  matriz parcial, erro, mudança de alvo/fresa e aumento de núcleo estável.

**48/48 pares concluídos**, sem erros, mudança de alvo/diâmetro ou aumento de
núcleo estável. Nos 32 Rest, o núcleo estável após a tolerância caiu a zero na
Geometry. No conjunto, o maior núcleo estável antes era **38,1023 mm²**; depois,
**0,000003764 mm²** (controle sem Rest). No G-code, o maior núcleo final é
**0,000004632 mm²**. As sobras brutas continuam no relatório: não chamar esses
números de remoção física perfeita.

Os canais sensíveis à precisão continuam representados na sobra nominal,
chegando a cerca de **3,75 mm²** nesta matriz. Não foram feitos cortes forçados
em canais de encaixe exato. Uma fresa efetivamente menor pode ser necessária.

Dados finais: `validation/clearing-stock-fix-20261110.json` e
`target/clearing-connect-fix-20261110/report/index.html`.

Suíte integrada dos dois itens, após o refinamento do Connect: **2.511 casos**,
2.493 aprovados, 18 opcionais ignorados,
zero falhas/erros (6min15s). Após a explicação do Rest nos quatro idiomas,
**12/12 verificações FX/i18n** passaram novamente. As duas auditorias sintéticas
somam **192/192 controles de distância** aprovados (Lines e regional, NCC/Paint,
duas fresas, Rest, ângulos, MM/IN), com a margem de análise anterior explícita de
0,5% do diâmetro. Seis guardas Python protegem o relatório; quatro guardas Java
protegem a verificação contínua dos segmentos no comparativo CNC.

O auditor não simula fixação, material, flexão, aceleração ou máquina física.
O limite de cada placa é a caixa do cobre do corpus, não seu Outline real.
As sobras nominais e as sensíveis à precisão permanecem visíveis.

## Reprodução

O JAR de controle foi preservado antes de editar o código. Para comparar outras
revisões, mantenha o gerador de referência separado das classes de análise: não
use um analisador defeituoso num lado e o corrigido no outro.

Diretório de evidências: `target/clearing-connect-fix-20261110/`.

- `baseline-cam.jar`: gerador de `513cbccb`.
- `stage1-cam.jar`: gerador corrigido de Lines/Rest; o ajuste posterior de Combo
  não altera os casos Lines desta matriz.
- `audit-engine/`: classes corrigidas de cobertura/acesso compartilhadas.
- `coverage-before/`, `coverage-after/`: medições dos dois geradores.
- `air-gaps.json`, `air-rest-gaps.json`: reprodução e inspeção geométrica.
- `verify.log`, `counterproof.log`, `final-regression.log`, `final-ui.log`:
  execução integral, contraprova e controles finais.
- `distance-lines/`, `distance-regions/`: controles independentes de distância.

```powershell
python tools/summarize_clearing_fix.py target/clearing-connect-fix-20261110/coverage-before target/clearing-connect-fix-20261110/coverage-after target/clearing-connect-fix-20261110/cnc-final target/clearing-connect-fix-20261110/report
python -m unittest discover -s tools -p test_summarize_clearing_fix.py
```

Os probes preservam relatórios existentes para retomada. Use diretórios novos
quando mudar o código. As operações geométricas indivisíveis podem ultrapassar
o orçamento cooperativo; isso não é contado como aprovação.
