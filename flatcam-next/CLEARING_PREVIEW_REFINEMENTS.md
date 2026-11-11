# Prévia e Connect da espiral — 2026-11-11

Implementação e testes: `2b2315fb`.

Escopo autorizado: itens 1 e 2 após o protótipo `fc981a5b`. Sem nova Beta/MSI.
Padrões e IDs Python preservados; métodos legados e Region Lines não são alterados.

## Plano e critérios

Entrada: árvore limpa em `81a640c3`, evidência anterior registrada em SPIRAL.md.
Prioridade maior: ligações que removam cortes ou atravessem cobre; publicação de
prévia obsoleta após edição/troca de ferramenta. Prioridade média: métricas,
contraste e leitura no painel estreito. Esforço planejado: 40% UI/rota, 25% Connect,
15% validação/documentação, 20% reservados para investigação e correções.
Ambiente: Windows, Java 25/JavaFX, testes reais de UI sem Stage visível, amostras
públicas já salvas. Nenhuma configuração pessoal ou placa de usuário é gravada.

| Requisito | Verificação |
|---|---|
| Corte, deslocamento e entrada distintos | Papéis de prévia e contraste nos quatro temas; captura nativa |
| Resumo por fresa/unidades/estimativa | ClearingRoutePreviewTest: MM/IN, SOURCE/NEAREST e GRBL real |
| NCC e Paint sem criar objetos | ClearingPreviewUiTest / SpiralPreviewUiTest |
| Edição, cancelamento, troca e retomada | Preview queued/edit, troca para Paint e retomada do rascunho NCC |
| Legenda não altera receita | Toggle visível sem novo job, receita ou objeto |
| Connect e acabamento preservam cortes | Testemunhas geométricas, intervalo contínuo de cada segmento e exportação |
| Conexões respeitam cobre/borda/Rest | Disco nominal, corte proibido, 32 controles NCC/Paint MM/IN e corpus público |
| Benefício/custo mensuráveis | Mesmos alvos/fresas/GRBL antes/depois, sobras brutas e núcleos com tolerância declarada |

Saída exigida: testes focados e build completo sem falhas; comparações com origem
e parâmetros preservados; resultados desfavoráveis continuam publicados. Não
atribuir tempo de máquina real/FPS a estimativas de percurso.

## Prévia

Paint e NCC calculam no worker uma rota nominal de uma passada: cortes em lilás,
deslocamentos em ciano e entradas em âmbar, com legenda para ocultar cada camada.
As cores mudam com o tema; entradas são marcadores de tamanho em pixels.
Resumo: entradas/levantamentos, comprimentos de corte e deslocamento, tempo XY e detalhes por
fresa. Cálculo usa o mesmo ordenamento SOURCE/NEAREST do gerador, sem criar G-code
ou buffers de largura de ferramenta apenas para desenhar os centros.

**Condições explícitas:** origem (0,0); uma passada; corte a 300 mm/min, rápidos
a 1500 mm/min, convertidos para IN. Exclui Z, aceleração, troca/posição de troca,
múltiplas profundidades e retorno final. Não usa os parâmetros finais do CNC Job.
O parser de G-code não desenha o primeiro rápido se a posição anterior da máquina
é desconhecida; a prévia nominal inclui esse trecho desde a origem declarada.
Tooltips explicam essas condições nos quatro idiomas. Diagnóstico de regiões onde
a fresa não cabe permanece separado da estimativa, com destaque no Plot.

Alterar entradas cancela a prévia pendente e limpa o resumo. Trocar de ferramenta
também cancela; o rascunho NCC pode ser retomado. A legenda só muda o desenho.

## Connect

A espiral mantém os cortes abertos na ordem e direção originais. O candidato
primeiro tenta o segmento reto seguro; quando impossível, considera o menor
percurso na mesma borda da área de centros, limitado a dez diâmetros. Cada ligação
nova precisa conter o disco nominal inteiro na área original autorizada. A prova
usa a distância contínua dos segmentos à borda poligonal, indexada e sem reduzir
o raio. Assim, a simplificação de um buffer curvo não rejeita falsamente um
percurso válido. Quando a borda aproximada não admite esse disco, a ligação é
recusada. A viabilidade de cada volta é guardada para não repetir a mesma
verificação em todos os contatos.

Com Contour, uma volta de acabamento pode ser inserida no contato já existente
da espiral com a borda. A volta completa é preservada e retorna ao mesmo contato.
Contornos sem contato aproveitável começam no ponto projetado mais próximo
quando existe ligação segura; caso contrário, ficam separados. Sem Contour, não
se acrescentam voltas inteiras de acabamento. Os fragmentos de acabamento Rest
continuam caminhos explícitos; não se inventam bordas completas a partir deles.

Este passo não deforma a espiral nem divide bolsões (itens 3/4 ainda fora do escopo).

O acabamento que não puder ser integrado permanece na posição original da
sequência: termina-se o bolsão antes de visitar o próximo, inclusive em áreas
de centros desconectadas. Entre o candidato e o Connect anterior, escolhe-se
localmente a menor contagem de entradas; no empate, o menor custo XY nominal
(corte + deslocamento/5). Isso não é uma otimização global do CNC Job.

## Resultado da comparação

Mesmos alvos, fresas de 0,4 mm, overlap 40%, Contour/Connect ligados, três placas
públicas. Comparam-se o núcleo congelado anterior e o final. O G-code é realmente
exportado/relido em GRBL_11_no_M6, com SOURCE preservado nos dois lados.

| Placa | G0 Z antes → depois | Minutos estimados antes → depois |
|---|---:|---:|
| Breadboard Power Supply v2 | 935 → 830 | 22,84 → 21,94 |
| Modxo Adapter | 240 → 234 | 8,93 → 8,84 |
| Single-sided 5V | 484 → 475 | 18,19 → 18,08 |

O contador G0 Z inclui início/fim do programa além dos levantamentos por
trajeto. O resumo da UI conta uma entrada e um levantamento por trajeto na
referência de uma passada, sem esses comandos extras. A redução corresponde a
105, 6 e 9 comandos, respectivamente. Os tempos do programa incluem Z conforme
os parâmetros do probe; são diferentes da referência somente XY da prévia.

**9/9 comparações passaram:** intervalos contínuos de todos os cortes exigidos
preservados a 1e-8 mm; Lines/Seed permanecem idênticos; nenhuma contagem de
levantamentos aumentou; núcleos estáveis de sobra permanecem zerados após a
tolerância declarada. O comparador exige as espirais e o acabamento **sem
Connect** como referência; atalhos opcionais antigos não são cortes obrigatórios.
Quatro controles independentes protegem o auditor contra lacunas entre amostras.

Sobras nominais brutas não são apagadas da medição: após o refinamento, de
0,004402 a 0,015716 mm² nos três casos Spiral. As diferenças incluem franjas da
aproximação; diâmetros e alvos não mudam. O maior desvio amostrado de exportação
foi 0,000069875 mm, dentro do arredondamento. **32/32 controles de distância**
NCC/Paint × MM/IN × Rest × Connect × Contour passaram, com fresas 0,8/0,4 mm e
folga declarada de 0,5% do diâmetro para a geometria aproximada anterior.

Continua experimental e ainda perde para Lines nos três exemplos. Não inferir
ganho universal, FPS ou tempo real de máquina. Geração observada antes/depois:
4293/5232 ms, 88/156 ms e 222/343 ms. Esses tempos foram sequenciais, com JVM
aquecendo e validações concorrentes; **não são benchmark isolado**. O novo
planejamento exige trabalho adicional, mesmo com índices e cache.

Snapshots em `validation/spiral-connect-refinement-20261111.json` e
`validation/spiral-connect-distance-20261111.json`, com hashes das classes e da
entrada. Evidência completa em `target/spiral-links-*-20261111/`, baseline
congelada em `target/spiral-connect-before-20261111-classes/`. Os testes novos
incluem preservação do acabamento e finalização de um bolsão antes do próximo.

## Repetir a auditoria

As classes congeladas foram copiadas antes de alterar o Connect. Após um clean,
reconstrua o núcleo de `81a640c3` separadamente para obter a baseline. Use sempre
diretórios novos nas medições; o probe retoma arquivos já existentes.

```powershell
# Na raiz flatcam-next; o probe atual também é executado contra as classes da baseline.
& "$env:JAVA_HOME/bin/java.exe" -Xmx3g --class-path 'flatcam-cam/target/classes;flatcam-fx/target/dependency/*' tools/SpiralCommunityProbe.java samples/community/validation/fx-import.json target/spiral-new-after
# Para as trajetórias obrigatórias da baseline, use '-Dflatcam.spiral.connect=false'.
& "$env:JAVA_HOME/bin/javac.exe" -cp 'flatcam-cam/target/classes;flatcam-fx/target/dependency/*' -d target/spiral-connect-auditor tools/CompareRegionCncProbe.java tools/CompareSpiralConnectProbe.java tools/TestRegionCncCoverage.java
& "$env:JAVA_HOME/bin/java.exe" -cp 'target/spiral-connect-auditor;flatcam-cam/target/classes;flatcam-fx/target/dependency/*' CompareSpiralConnectProbe target/spiral-links-before-20261111 target/spiral-new-after target/spiral-new-comparison.json target/spiral-links-required-20261111
```

Captura nativa opt-in: `SpiralCommunityPreviewTest` com
`-Dflatcam.spiral.visual=true`. Gera imagens Paint/escuro e NCC/claro em
`flatcam-fx/target/spiral-preview-review/`, verificando publicação do desenho
antes de capturar. Não abre Stage visível nem altera preferências ou placas.

## Validação final

Critérios de saída atendidos: **mvnw verify passou**, 2.537 casos, 2.518 aprovados,
19 opcionais ignorados, nenhuma falha/erro (20 suporte, 1.060 CAM, 185 aplicação
e 1.272 FX). A captura opt-in Paint/escuro e NCC/claro passou à parte. Nove
comparações comunitárias, 32 controles de distância e quatro controles do auditor
passaram. Não há requisito deste escopo sem cenário/resultado; adaptação às bordas
e decomposição de bolsões pertencem aos itens seguintes e permanecem fora do escopo.
