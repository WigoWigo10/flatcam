# Lines no NCC/Paint e navegação dos editores — 2026-11-09

Complemento posterior: [auditoria dos caminhos brutos](LINES_RAW_PATH_AUDIT.md).
Ela confirma repetições raster/Contour e recaminhamento Connect, e regiões
alcançáveis sem saída quando Contour está desligado. A igualdade espacial
dos controles anteriores não certifica ausência de trajetos duplicados.

## Relato e escopo

Relato do testador: Lines interrompido perto de pads redondos/retangulares,
pedido de ângulo da varredura, e editor inacessível depois de abrir outra
ferramenta. A ferramenta exata do primeiro relato não foi identificada;
foram exercitadas as entradas públicas de **NCC e Paint**. Não foi fornecido
o projeto nem o conjunto de parâmetros da captura.

Base da investigação: `07551a76`, Beta 5. Nenhum projeto privado foi alterado.
O commit que introduziu o problema de navegação não foi determinado.

## Editor: defeito reproduzido e corrigido

Reprodução mínima: criar um objeto, entrar no editor e abrir Calculators.
O painel era substituído, enquanto o controlador continuava em edição.
Acontecia com Geometry, Gerber, Excellon e G-code. Portanto não se tratava
necessariamente de bloqueio da thread: os controles para sair desapareciam.

O editor agora conserva o painel até salvar/aplicar ou descartar. Uma tentativa
de abrir outra ferramenta reapresenta o editor e informa que a edição precisa
ser concluída. A sessão, os dados e o histórico não são recriados. A proteção
abrange menus, botões de objetos, retomada de rascunhos e entradas diretas que
podem alterar prévias antes de abrir o painel. Fechar explicitamente o editor
continua permitido; depois disso as outras ferramentas abrem normalmente.

`EditorNavigationTest` usa uma MainWindow real com dados sintéticos e
preferências isoladas, sem janela na área de trabalho. Verifica preservação
do painel, estado ativo, alterações/Undo de Geometry e saída por Cancelar.

Evidência local em `target/`:

- `editor-navigation-red.log`: quatro casos vermelhos na implementação anterior.
- `editor-navigation-reverted-10.log`: MainWindow original recolocada
  temporariamente; 10 repetições de cada editor reproduziram o problema
  (40 casos). O script restaurou a fonte corrigida em `finally` e conferiu os bytes.
- `lines-editor-green-audit.log`: os mesmos 40 casos passaram com a correção.
  A rodada completa desse log falhou separadamente na auditoria de legendas:
  “Automático” coincidia em Português/Espanhol com outra chave de tradução.
  O novo controle passou a usar a legenda contextual “Direção automática”;
  nenhuma regra da auditoria foi relaxada.

## Lines: o que foi verificado e o que permanece em aberto

NCC e Paint compartilham `NccGenerator.linePaths`. O teste sintético contém
pad circular, retângulo com pista, retângulo arredondado e outra pista. Exercita
Connect ligado/desligado, Contour ligado, direção automática e ângulos fixos,
em MM e IN, pelas duas entradas públicas.

Não houve falha de polígono ou perda significativa de cobertura nesses casos:
o material alcançável que restou ficou abaixo de 0,1% da área alcançável.
O teste também verifica a preservação da fonte e que o envelope da fresa não
atravesse o interior dos pads. A margem desse último teste deriva da
simplificação padrão do buffer e da aproximação dos arcos; não é uma prova
de tolerância física nem permite afirmar sobrecorte geométrico exatamente zero.

**O defeito exato da imagem ainda não foi reproduzido.** Os quatro controles
automáticos já passavam antes da mudança no algoritmo. Não registrar esses
controles como uma correção de lacunas comprovada.

Uma linha de raster precisa terminar quando encontra cobre/obstáculo.
Connect une somente quando o deslocamento reto e a largura da fresa cabem
na área permitida; não contorna automaticamente o pad. Contour acrescenta
o passe periférico. Com Contour desligado, podem permanecer recortes de
acabamento nas bordas curvas. Unir visualmente todos os trechos poderia
produzir corte sobre o cobre e não é a solução adotada.

Se o projeto do relato ainda mostrar regiões alcançáveis sem usinagem ou
exceções, o próximo passo é guardar o projeto e os parâmetros efetivos de cada
fresa (método, diâmetro, overlap, margem, Connect, Contour e Rest) e comparar
a cobertura nesse caso. A captura sozinha não distingue recuo correto,
acabamento desligado, material inacessível ou defeito de geração.

## Ângulo de varredura

Nos painéis NCC e Paint, selecione **Lines** ou **Combo**. O campo fica
disponível para esses métodos:

1. **Direção automática** mantém a escolha histórica pela caixa de cada região.
2. Desative-a e informe de **0° até menos de 180°**; aceita vírgula decimal.
   0° é horizontal, 90° vertical, 45° diagonal; positivo é anti-horário.
3. A pequena amostra indica a direção manual; em Automático é simbólica.
   Não analisa o objeto nem representa uma prévia da cobertura.
4. O valor pertence à fresa. No NCC, Rest mantém o ângulo por fresa e continua
   compartilhando Connect/Contour/offset como antes. Aplicar a todas copia o
   ângulo. Histórico da tabela preserva a escolha em ambos os painéis.

O NCC conserva também o texto cru no rascunho salvo no projeto, inclusive
quando incompleto. Projetos antigos sem esse campo abrem em Automático.
O Paint mantém a escolha por fresa durante a sessão; esta alteração não cria
persistência de rascunhos Paint que não existia anteriormente.

O algoritmo rotaciona o sistema temporário de varredura e devolve os trajetos
às coordenadas do projeto; não rotaciona o objeto. Contornos continuam nas
coordenadas originais. Combo usa o ângulo na tentativa Lines. O padrão e os
construtores antigos permanecem automáticos, preservando o comportamento
anterior. Tooltips explicam ângulo, limites e a relação com Connect/Contour,
nos quatro idiomas.

## Validação

Testes adicionados: `LinesAngleTest`, `LinesPadCoverageTest`,
`EditorNavigationTest`, `RasterAnglePanelTest` e `RasterAngleThemeTest`.
Os testes existentes de edição Paint e reabertura de projeto NCC foram
ampliados para verificar o ângulo sem arredondamento.

`target/lines-angle-coverage.log`: 22 testes CAM e 15 testes de interface
aprovados. `target/lines-angle-ui3.log`: codec, Undo/Redo, campo incompleto,
validação e compatibilidade com rascunho anterior passaram em MM/IN.
As primeiras tentativas de teste do painel estão preservadas nos logs ui/ui2:
erro de tipo e tentativa de acrescentar uma Scene a um fixture que já a criava;
o fixture foi corrigido, sem enfraquecer as asserções.

Build integral aprovado em `target/lines-editor-full-verify.log`: **2406 casos,
2388 aprovados, 18 opcionais ignorados, zero falhas/erros**, 4:45.
Inclui auditoria dos quatro idiomas e tooltips, recortes internos, editores,
fluxos CAM e persistência. Os quatro temas passaram no teste de largura de
310 px; capturas de `flatcam-fx/target/lines-angle-review/` foram inspecionadas.
Catálogos estritos: 3941 chaves completas por idioma. Esse ensaio não substitui
um novo teste físico de múltiplos monitores/DPI ou os cliques do testador.

`build-native.cmd` concluiu e o probe nativo de bibliotecas/renderização
offscreen passou (`target/lines-editor-native-build.log`). Foram conferidas
106 classes/recursos dos JARs contra `target/classes`, e o JAR CAM copiado para
o runtime é idêntico ao recém-gerado. Resumo em
`target/lines-editor-verification.json`. O launcher local está sincronizado;
isso não atualiza automaticamente um FlatCAM instalado pelo MSI anterior.

Na etapa inicial acima não houve comparação nova com execução do Python.
O complemento abaixo acrescenta essa comparação; continuam sem usinagem física
ou validação manual do projeto do testador. Não há nova versão/MSI nesta etapa.

## Complemento: pads arredondados contra o Python — 2026-11-09

Pedido posterior: verificar também os pads arredondados, mostrar imagem e
comparar com Python. Acrescentados `tools/RoundedPadLinesProbe.java` e
`tools/compare_rounded_pad_lines.py`, com dados públicos sintéticos: dois
pads circulares com pistas, retângulo de cantos arredondados e pad oblongo.

São 16 controles: NCC/Paint × Connect ligado/desligado × Contour
ligado/desligado × MM/IN. Fresa de 0,8 mm (ou equivalente IN), overlap 40%,
margem/offset zero, direção automática e uma fresa, sem Rest. O NCC usa o
corpo original do inicializador GUI `ToolNCC.clear_copper` e seus auxiliares,
executados sem janela e com bindings de interface em memória. O Paint usa
`camlib.Geometry.clear_polygon3` original na mesma área explicitamente
selecionada. Não foi um novo ensaio interativo das janelas Python/FX.

Referência: Python 3.11.1, Shapely 1.8.5.post1, GEOS 3.10.3-CAPI-1.16.1.
Fontes Python preservadas, hashes conferidos. Resultados e três imagens
em `target/rounded-pads-20261109/`; arquivo decisivo: `comparison.json`.

Resultado: **8 MATCH_SAMPLED, 8 DIFFERENT, zero ORACLE_ERROR**. Todas as
divergências estão nos controles **com Connect ligado**. Sem Connect, os
oito controles coincidem pelos critérios espaciais existentes (distância
amostrada e comprimento/limites/cobertura). Isso não verifica a ordem das
passadas, levantamentos, movimentos repetidos ou G-code.

Com Contour ligado, o resíduo alcançável fica abaixo de **0,0001%** em ambas
as versões, incluindo os pads arredondados. Com Contour desligado e Connect
ligado, aproximadamente **2,87% a 2,89%** da área alcançável fica sem corte
nessa fixture; sem ambos são **3,10%**. Esses números são da placa sintética
inteira, não apenas dos pads e não uma estimativa de precisão física.
As imagens ampliadas mostram o passe de acabamento que falta quando Contour
está desligado. Isso é visualmente semelhante ao relato, mas os parâmetros
originais do testador continuam desconhecidos.

Os conectores diferentes não atravessaram o interior dos pads no controle
numérico utilizado. Ainda assim os oito casos Connect **não são paridade**:
a distância amostrada entre trajetos chega a cerca de **0,282 mm** em MM e
**0,222 mm** no equivalente IN. Com Contour ligado, as áreas varridas são
praticamente iguais apesar dos conectores diferentes. Próxima investigação:
isolar a ordenação/empates do conector Lines, por fresa, sem mudar o clipping
que coincidiu nos controles sem Connect. Nenhum algoritmo de produção foi
alterado neste complemento.

As figuras usam um renderizador comum na mesma escala, a partir dos trajetos
calculados; **não são screenshots das aplicações**. Verde é cobre; vermelho
é o centro da fresa. A distância visual até o pad corresponde ao raio da
fresa. A primeira legenda das figuras sobrepunha o rodapé; foi corrigida e
as três imagens foram inspecionadas.

A primeira tentativa do comparador aplicava a união espacial só no lado
Python (já devolvido assim pelo helper antigo), misturando comprimento bruto
FX com comprimento sem sobreposições Python. Preservada em
`initial-asymmetric-normalization-comparison.json`; **não usar seus 4/12
como resultado final**. A comparação final aplica a política existente
simetricamente; mantém o WKT FX bruto separado. Não removeu conectores nem
relaxou tolerâncias. Dois testes auxiliares verificam tanto a comparação
simétrica quanto a detecção de um conector realmente diferente.

Repetir, a partir de `flatcam-next`:

```powershell
java --class-path 'flatcam-cam/target/classes;flatcam-fx/target/dependency/*' tools/RoundedPadLinesProbe.java target/NEW-ROUNDED/fx.json
.\target\oracle-py311\Scripts\python.exe tools/compare_rounded_pad_lines.py --legacy-root .. --fx-export target/NEW-ROUNDED/fx.json --output target/NEW-ROUNDED --strict
.\target\oracle-py311\Scripts\python.exe -m unittest discover -s tools -p test_compare_rounded_pad_lines.py
```

O modo strict retorna 1 se persistirem as divergências, mesmo quando a
cobertura coincide. O ângulo fixo novo do FX não entra nessa comparação:
`clear_polygon3` desta referência Python escolhe a direção automaticamente.

## Corpus comunitário disponível para continuar

O usuário pediu placas reais variadas. Coleção salva em
[samples/community/README.md](samples/community/README.md): oito pacotes,
90 arquivos de fabricação, 88 objetos importados pelos parsers FX sem erro
no conjunto final. Há placas THT/SMD, de quatro camadas, teclados repetitivos,
eletrodos capacitivos e slots. As figuras da coleção mostram importação de
cobre/furos; **não são resultados NCC/Paint comparados com Python**.
Os arquivos brutos e a galeria permanecem locais; manifesto, fontes imutáveis,
hashes, licenças e scripts permitem repetir. Um grupo intermediário de nove
Gerbers do fornecedor foi recusado e preservado como caso de compatibilidade,
separadamente do pacote KiCad final. Não converter isso em aprovação silenciosa.
