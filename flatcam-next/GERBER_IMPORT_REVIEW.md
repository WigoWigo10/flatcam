# Importação Gerber/Excellon — correções do relato de Yago — 2026-11-14

Por solicitação do usuário, **a implementação/otimização de usinagem e Espiral
fica pausada**. A prioridade desta rodada é corrigir importação e erros próximos.
Não há bump de versão ou novo instalador; permanece a Beta 7 para a próxima
entrega conjunta.

## Relato e reprodução

Entrada: `Gerber_RELE_MODULE_PCB_RELE_MODULE_2026-11-13.zip` fornecido pelo
usuário. SHA-256 `eea32b7a2a39c64475b8bfde76159333e3920fcf9e2f925b5ff5741d6e8a7c62`.
Baseline `1a93f3dd`, árvore inicialmente limpa. O ZIP e os arquivos originais
não foram alterados nem adicionados ao Git. Apenas metadados de diagnóstico e
casos sintéticos mínimos entram no repositório.

Antes da correção: dos sete Gerbers, cinco abriam. `Gerber_TopSilkscreenLayer.GTO`
(linha 392) e `Gerber_DocumentLayer.GDL` (linha 469) eram recusados por `G01`
isolado sem `*`. O Python abre os sete. Uma cópia com somente esse terminador
acrescentado reproduziu a causa sem modificar qualquer coordenada.

O GKO original **não reproduziu um erro na leitura de zero**: contém oito
elementos desenhados, em MM, com limites dos centros X=0..43,99991 e
Y=0..32,004. `X0`, `Y0`, `I0` e `J0` são aceitos. A cópia preenchida com zeros
à esquerda produz a mesma geometria. O seletor da interface, porém, não listava
GKO/GTO/GDL/GTS/GBS, embora GKO/GTS/GBS já fossem suportados pelo parser.

## Correções e revisão próxima

| Caso | Alteração |
|---|---|
| Extensões ausentes | Inventário central em `CamFileFilters`, usado em Abrir, exportar e salvar fonte; inclui extensões do Python, GDL, camadas internas/mecânicas e maiúsculas. Seletor mantém filtro principal e oferece Todos os arquivos. |
| Sufixo depois de editar | `placa.GKO_edit` sugere `placa_edit.GKO`, em vez de acrescentar `.gbr` a um nome composto. O conteúdo continua sendo validado pelo parser. |
| Modo sem terminador | Aceita apenas `G1/G01/G2/G02/G3/G03` isolados, sem desenhar/repetir um flash. Não completa coordenadas ou comandos arbitrários sem `*`. |
| UTF-8 BOM | Gerber e Excellon removem a marca somente no início do arquivo. Uma marca dentro de um comando não é apagada silenciosamente. Não é detecção genérica de UTF-16. |
| Regiões inválidas | G37 sem G36, G36 aninhado e região sem G37 causam `GerberParseException`; não desaparecem silenciosamente nem produzem NullPointerException. |
| Fim do arquivo | M02/M30 terminam a leitura, inclusive antes de um trailer inválido. M00/M01 continuam sem encerrar o arquivo. |
| Equivalência LZ/TZ | A conversão decimal exata é feita uma vez, com BigDecimal. Coordenadas completas idênticas não dependem de multiplicar ou dividir uma aproximação binária de potência de dez. |
| Transformações legadas | OF/SF/AS/IP neutros continuam aceitos. Deslocamento, escala, troca de eixos ou imagem negativa ainda não implementados agora são recusados explicitamente, em vez de importar cobre sem aplicar a transformação. |

A investigação LZ/TZ encontrou uma diferença de um ULP: `43.99991` virava
`43.99991000000001` no outro modo. No GKO equivalente, isso alterava a escolha
de segmentos dos arcos; a diferença entre as aproximações poligonais chegava
a cerca de 0,002282 mm. Isso **não era uma mudança da escala da placa**, nem
reprodução da falha alegada no GKO LZ original. A correção dá arredondamento
consistente e as variantes LZ preenchida, TZ e formato completo sem supressão
agora produzem geometria idêntica no projeto relatado.

Não se implementaram transformações Gerber novas, step-and-repeat ou toda a
linguagem. Ampliar o filtro de extensões não torna válido todo conteúdo com
esses sufixos. O objetivo é abrir os arquivos do relato e impedir falhas ou
interpretações silenciosas nos caminhos revisados, sem adivinhar precisão/unidades.

## Validação

Build final completo: **2.668 casos, 2.648 aprovados e 20 opcionais ignorados**,
sem falhas/erros, em 6:00. O teste optativo com o ZIP privado é executado à parte.
A rodada completa anterior às duas últimas correções também passou; o log
`final-full-verify.log` corresponde ao código final, não àquela etapa intermediária.


- Sete Gerbers originais e dois DRLs passam no parser corrigido. O DRL principal
  tem 21 furos em cinco ferramentas. O DRL Via não tem coordenadas: vazio esperado.
- `GerberRelayImportTest`: execução optativa com o ZIP original pelas filas reais
  da interface, em uma Session sem Stage e com preferências de teste em memória.
  Confere sete objetos novos, unidades, geometria válida, dois Excellons e 21 furos.
  O diálogo nativo do sistema não é aberto; seus filtros são testados como objetos
  JavaFX efetivamente usados pelo MainWindow.
- `CamFileFiltersTest`: 23 casos; 21 falhavam antes da correção. Padrões, fallback,
  nomes sugeridos e maiúsculas verificados.
- `GerberImportRobustnessTest`: 25 casos; 15 expõem os erros antigos; demais são
  controles de rejeição, zero explícito vs eixo omitido e stops opcionais.
- `GerberZeroSuppressionEquivalenceTest`: dois casos MM/IN; ambos falhavam antes.
- `GerberLegacyTransformValidationTest`: 14 casos; sete falhavam antes, sete
  protegem os cabeçalhos neutros já aceitos.
- Os mesmos corpos de 41 testes CAM foram executados dez vezes contra classes
  originais congeladas e depois corrigidas: 240 falhas em 410 execuções antigas,
  zero em 410 corrigidas. Não se adicionou um interruptor de produção para ocultar
  a correção. Classes antigas correspondem às fontes baseline dos três parsers.
- Acervo público: 88 arquivos, oito placas, zero erros; metadados semanticamente
  iguais e as oito prévias WKT exatamente iguais às anteriores. A comparação
  dos mapas de ferramentas considera pares chave/valor, não a ordem variável
  de `Map.copyOf().toString()` entre processos JVM. Nenhuma coordenada foi arredondada.

Casos novos são determinísticos, sem rede, relógio aleatório ou máquina física.
Sem versão conhecida anterior que não tivesse esses comportamentos para usar
como ponto bom de bisect; não foram atribuídos às recentes otimizações NCC.

Evidências compactas: `validation/gerber-import-review-20261114.json`.
Logs, variantes e WKT privados ficam em `target/gerber-relay-audit-20261114`.
O estado inicial do relatório `AUDITORIA_IMPORTACAO_RELE.md` nessa pasta registra
**antes** da correção; não confundir com o resultado final deste documento.

## Repetir

No diretório `flatcam-next`:

```powershell
cmd /c ".\mvnw.cmd -pl flatcam-fx -am -Dtest=GerberImportRobustnessTest,GerberZeroSuppressionEquivalenceTest,GerberLegacyTransformValidationTest,CamFileFiltersTest -Dsurefire.failIfNoSpecifiedTests=false test"
```

Para a fila real com o arquivo do relato, passar o caminho local explicitamente;
o ZIP não é dependência de builds normais:

```powershell
$relayZip = 'C:\caminho\relato.zip'
.\mvnw.cmd "-Dflatcam.test.relayZip=$relayZip" -pl flatcam-fx -am -Dtest=GerberRelayImportTest -Dsurefire.failIfNoSpecifiedTests=false test
```

O ambiente Surefire do FX usa `PreferencesFactory` em memória; não executar o
fixture de UI fora desse ambiente sem isolar preferências. O teste copia somente
as entradas esperadas para `@TempDir`, verificando nomes/tamanho e limites da pasta.
