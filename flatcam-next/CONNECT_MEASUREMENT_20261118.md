# Connect: medição atual em placas densas e painéis — 18/11/2026

## Conclusão

O Connect já otimizado não domina o tempo total nestes quatro controles.
Dentro de duas gerações Horizon ocupa 16–18% do tempo; nos dois controles Rest
com três Clear, 0,9–2,4%. A busca pelos extremos pesa dentro do Connect, mas
representa só cerca de 6–7% das amostras totais de CPU nas Horizon. Um índice
dos extremos pode ajudar, mas não sustenta a expectativa de outro ganho global
de várias vezes. A geração dos próprios trajetos concentra 70–80% das amostras.

Não houve alteração no algoritmo, limites da fresa, ordenação ou predicados.
A otimização histórica de `CONNECT_OPTIMIZATION.md` continua sendo uma comparação
distinta contra o conector anterior; esta rodada mede o código atual.

## Protocolo e ambiente

Base `7d7110a8`, Intel Core Ultra 7 155H (16 núcleos/22 threads), 31,4 GiB de RAM,
Windows 11 build 26200, Oracle Java 25.0.4.1, heap máximo 4 GiB. Notebook ligado
à energia, plano Equilibrado; sem mudar clocks, afinidade ou encerrar outros apps.

- Quatro casos × três pares ligado/desligado, alternando a ordem: **24 JVMs**.
- Três aquecimentos pequenos idênticos e GC explícito antes de cada medição;
  primeira execução da placa grande e seu JIT ainda fazem parte do tempo.
- Uma rodada separada por caso com agente de diagnóstico + JFR: **quatro JVMs**.
  O agente insere contadores na entrada/saída de `connectSafePaths` e
  `connectOrderedPaths`; não substitui decisões ou geometrias. Usa a
  [Class-File API do Java 25](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/classfile/CodeTransform.html).
- Cronômetro abrange somente `NccGenerator.generate`, incluindo preparação NCC,
  trajetos, Rest e composição do resultado. Leitura, cópias da placa, aquecimento,
  compilação, partida do processo, WKT e hashes ficam fora desse cronômetro.
- JFR grava só a geração. CPU/alocações vêm da thread geradora; não incluem CPU
  das threads de JIT/GC. Tempo decorrido inclui suas pausas e agendamento do SO.
  Alocações são cumulativas, **não** pico de RAM ou heap residente.
- Contour ligado, overlap 40%, limite retangular do cobre e margem zero.
  Painéis são cópias Gerber trasladadas 2×2 com 3 mm entre caixas do cobre.
  É entrada geométrica de diagnóstico, não uma receita física de CNC nem
  uma medição da operação/UI de panelização. ISO 0,2 mm nos casos Rest;
  Clear 1,2 / 0,8 / 0,4 mm. Demais casos: Clear 0,4 mm.
- Fonte pública preservada. Cobre/layout/unidades/parâmetros são os mesmos
  entre os lados de cada par. Connect pode alterar caminhos e o resíduo Rest,
  portanto os lados não representam trabalho idêntico depois dessa etapa.

## Geração completa sem instrumentação

Mediana de três amostras; entre parênteses, mínimo–máximo, em segundos:

| Caso | Connect desligado | Connect ligado |
|---|---:|---:|
| Horizon Choc, Lines | 3,52 (3,47–3,55) | 5,30 (4,04–6,04) |
| Horizon Choc, painel 2×2, Lines | 22,30 (20,77–22,63) | 18,96 (16,42–22,12) |
| Audio Watermark, Lines, Rest/3 Clear | 18,41 (12,29–22,26) | 14,62 (13,75–18,39) |
| Breadboard Supply, painel 2×2, Standard, Rest/3 Clear | 2,69 (2,42–3,03) | 2,66 (2,62–2,83) |

Há variação ampla e sobreposição. **Não interpretar as medianas menores com
Connect como prova de aceleração causada pelo conector.** Além do ambiente/JIT,
no Rest as ligações modificam a área varrida e o trabalho das fresas seguintes.
A subtração dos tempos ligado/desligado não é o tempo do Connect.

## Custo dentro do Connect — diagnóstico separado

Uma amostra instrumentada por caso, não as medianas da tabela anterior:

| Caso | Geração | Dentro do Connect | Parcela | Chamadas |
|---|---:|---:|---:|---:|
| Horizon Choc | 6,520 s | 1,176 s | 18,0% | 220 |
| Horizon 2×2 | 15,281 s | 2,399 s | 15,7% | 869 |
| Audio Rest/3 Clear | 20,798 s | 0,503 s | 2,4% | 5 |
| Breadboard 2×2 Rest/3 Clear | 3,197 s | 0,030 s | 0,9% | 36 |

O agente e o JFR têm custo e podem influenciar o JIT; esses números localizam
o trabalho, sem servir como comparação de desempenho com as JVMs sem agente.
Não se descontou uma estimativa artificial de overhead.

Na Horizon simples, 33 de 77 amostras dentro do Connect estavam na busca dos
extremos (43%); no painel, 108 de 266 (41%). Em relação à geração inteira,
33/589 = 5,6% e 108/1598 = 6,8%. Os demais gastos incluem buffer da ligação e
predicado de contenção. Categorias de pilha podem se sobrepor.

A classificação exclusiva dos estágios aponta 474/589, 1158/1598,
1153/1449 e 271/387 amostras na geração dos trajetos: **80,5%, 72,5%, 79,6%
e 70,0%**, respectivamente. Buffers, interseções/noding e cópias de arrays
aparecem entre os métodos amostrados. Não são tempos exatos de cada fase.

## Efeito nos caminhos e verificações

Na Horizon simples, caminhos separados caíram de **9.874 para 1.754**;
no painel, de **40.060 para 7.021**. Comprimento percorrido em corte cresce
com as ligações: 16.238 → 18.537 mm e 72.752 → 82.225 mm. Menos caminhos
separados não certifica, por si só, menor tempo real de máquina.

- Hashes dos caminhos brutos e dados por fresa idênticos nas três repetições
  **de cada variante**; nenhum caso ausente, cancelado ou normalizado como acerto.
- Os quatro resultados instrumentados coincidem com suas referências ligadas.
- Fonte preservada; nenhuma inferência de paridade entre Connect ligado e desligado.
- 16 testes focados do Connect e três testes Python do comparador passaram.
- A ferramenta final passou ainda um par + perfil Breadboard usando snapshot
  CAM congelado; os caminhos de ambos os modos coincidem com esta campanha.

Não houve nova medição de cobertura integral, CNC físico, FPS, renderer ou GPU.

## Próximo trabalho fundamentado

Prioridade: aprofundar buffers e interseções da geração dos trajetos, antes de
alterar a busca do Connect. Um índice de extremos permanece candidato secundário,
com exigência de preservar desempates, direção e resultados brutos.

GEOS nativo via FFM é uma hipótese futura a comparar em um protótipo pequeno:
mesmas entradas, conversões incluídas, tempo/memória/cancelamento/resultados.
A [API C do GEOS](https://libgeos.org/usage/c_api/) oferece interface binária estável,
e o [Java FFM](https://docs.oracle.com/en/java/javase/25/core/foreign-function-and-memory-api.html)
permite chamá-la. Não implica ganho automático, paralelismo ou GPU. Nenhum backend
nativo foi instalado ou integrado nesta medição.

Para uma eventual camada própria nova, Rust é candidato pela segurança de
memória do código seguro; se o trabalho for usar GEOS pronto, começar por sua
API C diretamente evita uma camada sem necessidade demonstrada. Alterar o
próprio GEOS aponta para C++. Chamadas a código externo continuam exigindo
cuidados explícitos também em [Rust/FFI](https://doc.rust-lang.org/book/ch20-01-unsafe-rust.html).
Nenhuma comparação Java × GEOS × Rust foi executada nesta campanha.

## Evidências e repetição

Resumo portátil: `validation/connect-measurement-20261118.json`.
Dados/JFR/classes/agente e fontes congeladas da campanha original:
`target/connect-measurement-20261118-runs/`. Ver `report.json`, `environment.json`,
diretórios por caso, `sources/` e `clearing-phases.json`.
Conferência da ferramenta final: `target/connect-measurement-tool-final-20261118/`.
Logs de regressão: `target/connect-measurement-20261118-tests.log` e
`target/connect-measurement-20261118-comparator-tests.log`.

Na pasta `flatcam-next`, após compilar CAM e copiar as dependências:

```powershell
python tools/measure_connect.py --output target/NOVA-MEDICAO-CONNECT --pairs 3
```

O diretório precisa ser novo. A ferramenta final congela `cam-snapshot.jar`,
limpa overrides Java do ambiente dos subprocessos e exige saídas determinísticas.
A campanha original usou classes CAM locais sem alterações; suas fontes estão
preservadas em `sources/`. Esta medição não altera o algoritmo nem gera nova
versão ou instalador.
