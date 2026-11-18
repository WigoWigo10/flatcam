# NCC Rest com várias fresas Clear — 18/11/2026

## Resultado

**Relato ainda não reproduzido no código atual.** Não foi aplicada uma alteração
no algoritmo para um defeito não confirmado. Foram adicionados controles de
regressão e uma auditoria opcional com dois Gerbers públicos locais.

Relato de Yago: uma ISO + uma Clear funcionam, mas várias Clear aparentam gerar
trajetos independentes. A versão do testador, o método, o projeto, os diâmetros
e a seleção da tabela não estão disponíveis. Não há versão conhecida como boa
para um bisect confiável. Base examinada: `7d7110a8`, Windows, Java 25.
O gerador NCC e seu painel não mudaram entre a fonte da Beta 8 e essa base.

## O que foi verificado

- Uma ISO de 0,2 mm e três Clear de 1,2 / 0,8 / 0,4 mm, inseridas fora de ordem.
  Rest efetivamente executa as Clear da maior para a menor.
- Standard, Seed, Lines, Combo, Lines por regiões e Espiral.
- MM e IN, Connect ligado/desligado, Contour ligado, limite por área nos
  sintéticos; limite Itself + margem de 1 mm nos dois Gerbers públicos.
- Uma faixa interna já removida pela primeira Clear não recebe novamente
  trajetos de corte das Clear menores no caso sintético sem Connect.
- A área entregue à próxima Clear desconta o material removido pela anterior,
  inclusive na terceira Clear. A comparação usa uma varredura física dos
  trajetos emitidos, com arcos de 64 segmentos por quadrante. Admite 1% de
  diferença de área para a aproximação histórica do Seed, sem aceitar que a
  área restante seja reiniciada.
- Controle negativo: o mesmo verificador acusa a limpeza independente quando
  Rest está **desligado**, e aceita Rest ligado em dez repetições. Isso valida
  o verificador; não reproduz o defeito do testador com Rest ligado.
- O painel envia três Clear + uma ISO selecionadas; os trajetos individuais
  sobrevivem à publicação da Geometry, salvar/reabrir `.fcnproj` e abertura do
  formulário CNC. Esse teste dispara os controles JavaFX sem uma janela visível.
  Não é uma rodada manual de mouse nem prova de usinagem física.

Os primeiros testes do painel detectaram somente diferença de arredondamento
WKT na última casa binária (~8e-17). Comparar trajetos com tolerância 1e-12
preserva ordem e coordenadas e evita tratar isso como troca de geometria.

## Placas públicas

Gerbers originais da coleção `samples/community`, preservados sem alteração.
Os seis métodos passaram nas duas placas (12 combinações), com Connect e
Contour ligados. Áreas entregues às Clear, em mm², no método Standard:

| Placa | Clear 1,2 mm | Clear 0,8 mm | Clear 0,4 mm |
|---|---:|---:|---:|
| Single-Sided 5V Power Supply | 692,8604 | 33,1035 | 1,9498 |
| Breadboard Power Supply v2 | 520,9213 | 178,1990 | 87,4698 |

Os valores são o material restante **antes** de cada fresa, não o resíduo final
depois da terceira. O Seed pode deixar uma fresa intermediária sem trajetos
quando não cabe no resíduo; essa área permanece disponível para a menor.

## Ponto de atenção na interface

O botão Gerar considera **as linhas selecionadas**, não todas as linhas da
tabela. Adicionar ou selecionar uma fresa para editar pode deixar somente essa
linha selecionada. Para coordenar várias Clear, selecione todas as fresas
desejadas (Ctrl+A com foco na tabela), marque Rest e gere em uma única operação.
Gerar objetos separados, cada um com uma Clear, não compartilha o estado Rest.

Isso pode produzir o aspecto descrito, mas é uma hipótese de uso, não uma causa
confirmada. Passagens por espaço já limpo também podem ocorrer para conectar
trajetos ou posicionar a fresa menor; isso não significa reiniciar toda a área.
O caso original continua pendente de um projeto/receita ou versão identificável.

## Repetir

Na pasta `flatcam-next`, usando `cmd.exe`:

```bat
mvnw.cmd -pl flatcam-fx -am -Dtest=NccMultiClearRestTest,NccMultiClearRestPanelTest -Dflatcam.rest.audit.public=true -Dsurefire.failIfNoSpecifiedTests=false test
```

A auditoria pública exige os dois Gerbers já baixados na coleção. Sem a
propriedade `flatcam.rest.audit.public=true`, somente esse teste opcional é
ignorado; os controles sintéticos e do painel continuam ativos.

Primeira rodada completa dos novos controles: **29 testes aprovados**, zero
falhas/erros/ignorados. Logs locais: `target/ncc-multi-rest-final.log` e
`target/ncc-multi-rest-related.log`. Os testes relacionados abrangem também
fidelidade G-code e os fluxos NCC já existentes: **109 registrados, 108 aprovados,
um opcional ignorado**, zero falhas/erros. Evidência resumida em
`validation/ncc-multi-rest-20261118.json`.
Sem alteração no algoritmo, bump ou MSI nesta investigação.
