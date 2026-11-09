# Vistoria de NCC/Paint, tabelas e editores — 2026-11-09

Atualização: os cinco problemas receberam correções no commit **a5a83060**,
em preparação para a [Beta 7](release/BETA-0.1.0-beta.7.md). Os resultados
originais da Beta 6 abaixo são preservados como evidência anterior à correção.

**Cinco problemas confirmados, ainda sem correção de produção nesta vistoria.**
Foram exercitados os controles e ações reais dos JARs distribuídos da Beta 6,
com dados sintéticos e preferências em memória. Não foi uma sessão manual com
máquina CNC: nenhum projeto privado, configuração real ou instalador foi alterado.

## Identidade e resultados

- Fonte dos binários Beta 6: `c28019b72b16ee65534c62ba9f7e4fdf02814f95`.
- Controle anterior Beta 5: `8e2fcc186c20b5934927981e336fd1753447f552`.
- **12 cenários de UI × 10 repetições:** oito cenários falharam em 10/10;
  quatro passaram em 10/10; zero erros do instrumento na rodada final.
- Os oito cenários falhos correspondem a **cinco problemas distintos**;
  as três entradas de editor compartilham uma causa, assim como as duas
  entradas de Paint com fonte removida.
- Os mesmos oito cenários também falharam na Beta 5, em uma execução por caso.
  São problemas preexistentes; não foram introduzidos pelo novo ângulo Lines.
  O teste de ângulo, recurso ausente na Beta 5, foi explicitamente excluído dela.
- Sem baseline anterior comprovadamente bom, o commit introdutor não foi localizado;
  não se atribui a origem a uma alteração recente sem evidência.

Prioridades abaixo representam impacto observado e possibilidade de erro no
fluxo, não frequência medida de relatos ou classificação de um acidente físico.

| Prioridade | Problema | Resultado observado |
| --- | --- | --- |
| P1 | Menu **Editar** do Gerber contorna a proteção de outro editor | Dois editores ativos; painel original substituído e não restaurado ao cancelar o Gerber |
| P1 | Paint usa seleção antiga após remover o objeto de origem | Fonte realmente ausente da árvore/mapa, mas uma Geometry nova é publicada com a seleção anterior |
| P1 | Paint troca MM → IN sem converter a fresa | **0,3 mm passa a 0,3 in = 7,62 mm**; a Geometry gerada confirma unidade IN e diâmetro 0,3 |
| P2 | Cancelamento do Paint não é verificado imediatamente antes da publicação | Pedido de cancelar registrado antes da publicação na UI, mas a Geometry ainda é criada |
| P2 | Seleção Paint ignora coleções unitárias aninhadas | UI encontra zero polígonos onde o gerador aceita e processa um polígono preenchido válido |

## 1. Entrada alternativa sobrepõe editores

**Reprodução:** editar uma Geometry, Excellon ou CNC Job; selecionar um Gerber
na árvore; usar seu menu de contexto **Editar**. A ação continua habilitada.

**Esperado:** manter um único editor e seu painel, orientando concluir/descartar
a sessão anterior. **Observado:** dois controladores permanecem ativos e o
painel anterior é substituído. Cancelar o Gerber não restaura esse painel.
Não foi comprovado crash de processo; foi comprovada a inconsistência de sessão
e a perda da rota normal de acesso ao painel de edição.

**Causa:** `MainWindow.java:5913` chama `gerberEditor.start(...)` diretamente,
enquanto as outras entradas usam as verificações do MainWindow. Colocar uma
barreira apenas na abertura do painel é tarde: o controlador já pode ter criado
sua sessão e ocultado o objeto.

**Correção proposta:** centralizar todas as entradas de edição e verificar o
editor ativo **antes** de criar outra sessão. Cobrir o menu de contexto dos quatro
tipos, além dos menus principais/atalhos já exercitados.

**Probes:** `editor-context-to-Gerber-{Geometry,Excellon,GCode}`, 10/10 por rota.

## 2. Paint não invalida a origem/seleção antiga

**Reprodução mínima:** abrir Paint; escolher seleção individual e clicar em um
polígono; remover o objeto pelo seu menu da árvore; clicar **Gerar** no painel
que permaneceu aberto. Não é necessário deixar outro trabalho ocupado.

**Esperado:** invalidar a seleção e recusar a origem removida. **Observado:**
o trabalho é submetido e publica uma Geometry usando o polígono antigo.
Também reproduzido removendo a origem enquanto o cálculo estava na fila:
Paint publica a saída; **NCC rejeita a saída**, no controle equivalente.

**Causa:** `PaintToolPanel.java:375` usa a lista `chosen` anterior e
`MainWindow.java:5244` não captura/revalida a identidade do objeto como NCC faz.
O combo é uma lista montada ao abrir a ferramenta, sem invalidação da seleção
quando o objeto sai do projeto.

**Correção proposta:** guardar a identidade/versão da origem completa e invalidar
a seleção quando mudar ou for removida; revalidar antes de submeter e antes de
publicar. Os polígonos selecionados são um subconjunto: não se deve compará-los
diretamente à identidade da geometria completa em `captureCamInput`.

**Probes:** `paint-removed-picked-source`, `source-removed-during-Paint`;
controle `source-removed-during-NCC`. Todos consistentes em 10/10.

## 3. Troca silenciosa da unidade do diâmetro Paint

**Reprodução:** com objetos MM e IN no projeto, abrir Paint em um objeto MM com
fresa de 0,3; escolher o objeto IN no combo de origem; gerar.

**Esperado:** converter os comprimentos preservando a medida física, ou restringir
explicitamente a escolha às mesmas unidades, como o NCC já faz.
**Observado:** o campo continua 0,3, agora interpretado em polegadas. A saída
efetiva tem `units=IN` e `toolDiameter=0.3`: equivale a **7,62 mm**, 25,4 vezes
a medida física anterior. O teste confirmou a Geometry, não apenas o texto.
Não foi gerado/executado G-code físico nesta reprodução.

**Causa:** `openPaintTool` inclui todos os objetos, e o listener de troca de
origem (`PaintToolPanel.java:196`) limpa apenas a seleção do Plot. Os números
continuam iguais, e Gerar utiliza `host.units(item)` da nova origem.

**Correção proposta:** fixar a unidade do painel e filtrar origens/referências,
ou converter todos os comprimentos por fresa — incluindo margem e rascunhos —
com uma indicação clara. Converter somente o campo da fresa deixaria outros
parâmetros inconsistentes.

**Probe:** `paint-source-unit-switch`, 10/10; confirmado também na Geometry
gerada em dez execuções adicionais. O ensaio de abertura sem seleção inicial
seguiu a preferência vigente e não demonstrou um bug independente.

## 4. Cancelamento na janela entre cálculo e publicação

**Reprodução determinística:** gerar Paint; manter a thread FX ocupada enquanto
o worker termina; chamar a mesma ação de Cancelar do aplicativo antes de a fila
FX publicar o resultado; liberar a thread FX. A coordenação usa o futuro do
trabalho, sem esperar um tempo arbitrário para acertar a corrida.

**Esperado:** nenhuma saída publicada depois do cancelamento solicitado.
**Observado:** `isCancelled=true`, mas Paint cria uma Geometry. O NCC equivalente
recusa a publicação, 10/10. Não se trata de desfazer um resultado já publicado.

**Causa:** a conclusão Paint (`MainWindow.java:5267`) confere apenas se o handle
ainda é o trabalho atual; não verifica sua marca de cancelamento. Essa marca
pode chegar depois da conclusão do worker e antes do callback FX.

**Correção proposta:** utilizar a mesma barreira de publicação do restante do
CAM e manter callbacks de erro/progresso vinculados à operação que os iniciou.

**Probes:** `late-cancel-Paint` e controle `late-cancel-NCC`, 10/10 cada.

## 5. Polígono válido indisponível na seleção Paint

**Entrada mínima:** `GeometryCollection(GeometryCollection(Polygon))`, contendo
um retângulo preenchido de 10 × 8 mm. O núcleo Paint gera trajetos nessa entrada.

**Esperado:** contar e permitir selecionar o polígono. **Observado:**
`PaintToolPanel.polygonsOf` encontra zero. Retirar uma camada de coleção faz a
contagem voltar a um; o Polygon direto também conta um. Não afeta todo Gerber
ou todo pad: depende de como a geometria está agrupada.

**Causa:** `PaintToolPanel.java:116` só recorre quando o membro tem **mais de um**
componente; uma coleção intermediária com exatamente um componente é descartada.

**Correção proposta:** recursão por tipo de coleção, incluindo coleções com um
único componente, mantendo descarte de linhas/objetos vazios. Usar o mesmo
critério de polígonos preenchidos no contador, na seleção e no gerador.

**Probe:** `paint-single-member-collection-selection`, 10/10; controle mínimo
adicional com Polygon direto e uma única camada passou na contagem esperada.

## O que também foi verificado

- Trocar de fresa e **Aplicar a todas** no Paint preservou método Lines e ângulo
  preciso `37,5123456` nas duas fresas: 10/10.
- NCC recusou resultados com fonte removida e com cancelamento antes da
  publicação: 10/10 em cada controle.
- **96 controles de trajetos**, NCC/Paint, MM/IN, duas fresas 0,8/0,4 mm,
  ângulos 0°, 37,5123456° e 90° na maior e 135° na menor, Rest, Connect e Contour.
  Foram avaliadas 168 contribuições não vazias de ferramentas, em pads redondos,
  retângulos arredondados e cápsulas. Zero exceções ou falhas dos critérios.
- Critério geométrico: distância das linhas centrais ao cobre e à borda física
  do retângulo diagnóstico, com tolerância de **0,5% do diâmetro** para
  aproximação de arcos. O maior déficit relativo ao raio nominal medido junto
  ao cobre foi aproximadamente 0,0038% do diâmetro, dentro desse limite.

Isso **não** mede cobertura integral, trajetos duplicados, eficiência de todos
os deslocamentos, FPS ou segurança de execução física. Algumas contribuições
Rest menores ficaram vazias; isso não foi automaticamente contado como falha.
As pendências de `LINES_RAW_PATH_AUDIT.md` continuam abertas. Não houve nova
comparação com o Python nesta rodada; a comparação histórica foi Beta 5 × Beta 6.

## Repetir e consultar evidências

```powershell
# Códigos 1 são esperados enquanto os contratos acima estiverem violados.
.\tools\audit-cam-related.ps1 -Repeats 10
.\tools\audit-cam-related.ps1 -Lines

# Controle anterior; não executar o teste de ângulo ausente na Beta 5.
.\tools\audit-cam-related.ps1 `
  -BuildDirectory target\releases\0.1.0-beta.5-20261105-183738-780 `
  -Repeats 1 -CaseFilter '^(?!paint-settings).*'
```

O script compila apenas o probe, carrega os JARs/runtime da distribuição e grava
relatórios em pasta nova de `target`. `-CaseFilter` permite isolar um caso;
`excludedCases` torna as exclusões explícitas. Os testes não integram a suíte
Maven como falhas pendentes; são reproduções diagnósticas executáveis. Não foram
alteradas as asserções para transformar os bugs confirmados em aprovação.

Evidências preservadas:

- `target/cam-related-audit-20261109-routes/ui-audit.json`: 120 execuções UI.
- `target/cam-related-audit-20261109-impact/ui-audit.json`: ausência real da origem
  e diâmetro/unidade da Geometry gerada, dez repetições por caso.
- `target/cam-related-audit-20261109-beta5-routes/ui-audit.json`: Beta 5.
- `target/cam-related-audit-20261109-minimal-collection/ui-audit.json`: minimização.
- `target/cam-related-audit-20261109-lines-final/lines-audit.json`: 96 controles
  geométricos, métricas por contribuição de fresa.

As primeiras rodadas do instrumento também foram preservadas. A primeira UI
teve seis erros do probe (texto nulo de separador e montagem CSS sem Stage),
que não foram contados como bugs do aplicativo. Um erro de compilação do probe
geométrico e a adaptação de API opcional para o controle Beta 5 foram corrigidos
no instrumento. Todas as rodadas finais acima têm **zero erros do instrumento**.

Não foram executados nesta vistoria: suíte Maven completa novamente (produção
não mudou), novo MSI, revisão manual de todas as telas, todos os arquivos da
comunidade ou CNC física. Não há confirmação de que esses cinco casos sejam a
causa do arquivo privado do relato original, que não foi fornecido.

## Ordem recomendada de correção

1. Impedir sobreposição de editores pelas entradas alternativas.
2. Alinhar Paint à captura/revalidação de origem e cancelamento já usada no NCC.
3. Resolver a política de unidade do Paint para origem, referência e fresas.
4. Corrigir a extração recursiva dos polígonos selecionáveis.
5. Retomar a deduplicação e o aviso de regiões sem limpeza da auditoria Lines.

Cada correção deve manter a reprodução vermelha antes da mudança, portar o
contrato para uma regressão normal da camada apropriada e confirmar verde e
contraprova sem a correção. Os pacotes entregues da Beta 6 permanecem intactos.
