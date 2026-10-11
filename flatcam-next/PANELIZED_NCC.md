# NCC depois da panelização — matriz e limites (2026-10-10)

## ISO por membro original — correção residual 2026-10-11

Incremento posterior a `9eb77ff6`, reunido nesta entrega (consultar git log),
sem push. Esta seção supera a pendência B_Cu da rodada inicial abaixo.
A divergência B_Cu
ISO/offset descrita na seção histórica abaixo foi localizada na ordem da
preparação: `camlib.Geometry.isolation_geometry` bufferiza cada membro
original (cópia do painel), depois une os envelopes. O FX unia todo o cobre
primeiro. Embora a área do cobre seja igual, a união muda a representação dos
anéis, e a simplificação dos arcos no buffer ISO pode escolher outros vértices.
O offset seguinte amplifica essa diferença. Não era erro no offset GEOS nem
na inversão da direção dos anéis: sobre o mesmo envelope, ambos coincidem.

O FX agora preserva os membros originais durante o buffer ISO e une só os
envelopes produzidos. Reutiliza esses envelopes por diâmetro para ISO e CLEAR;
o maior diâmetro continua definindo o keep-out, sem copiar o bug de usar apenas
a última ISO de uma lista invertida. Há verificação de cancelamento entre
membros/diâmetros. NCC sem ISO, Rest, conector e critérios não foram alterados.

Também conserva a segunda união do envelope escolhido, feita pelo
`get_tool_empty_area` original antes do offset/difference. Ela não muda a área,
mas muda os inícios dos anéis usados pela erosão. Só bufferizar por membro
passou ISO/offset, porém introduziu reprovação ISO/Rest B_Cu: a fresa de 1 mm
ficou a ~0,003954 mm do caminho Python (limite 0,003 mm). No diagnóstico dessa
primeira fresa, reproduzir a segunda união baixou a distância amostrada a zero.
Não se consertou isso girando anéis para coordenadas de um projeto, removendo
vértices ou relaxando a tolerância. A regressão pública também distingue os
inícios dos anéis com e sem essa etapa, mesmo quando a área é igual.

Diagnóstico privado apenas de preparação (não aprovação CAM/G-code):
diferença de offset B_Cu reduziu de 7,0222756e-5 para ~1,30e-14 mm²;
envelope ISO por membro difere ~9,56e-15 mm². Artefatos ignorados:
`target/iso-preparation-b-20261011` e `target/iso-preparation-member-b-20261011`.
Não publicar os WKT privados no Git. Regressão pública usa dois polígonos
sintéticos de arcos rasos, golden Shapely 1.8.5.post1/GEOS 3.10.3 em MM/IN,
e prova que a preparação antiga realmente difere; fonte permanece intacta.
47 testes NCC aprovados. A primeira rodada de build/host passou e o público
teve 34/34, mas o oráculo privado ficou em 5/6 por causa de ISO/Rest B_Cu;
`target/ncc-iso-members-host-20261011` é intermediário, não aprovação final.
Build final após a segunda união aprovado: 1592 registrados, 1575 aprovados,
17 skips opcionais, zero falhas/erros; log
`target/ncc-iso-members-full-final-20261011.log`. Os 55 auxiliares Python
passaram novamente. Host final público MM/IN e real também aprovados (três
testes), incluindo salvar/reabrir, ferramentas, furos/slots e contorno;
fonte SHA-256 intacta. Log: `target/ncc-iso-members-host-final-20261011.log`.
Público final: 34/34 MATCH_SAMPLED, strict 0 nos seis relatórios em
`target/ncc-iso-members-final-20261011/public-python`.

**Privado final: 6/6 MATCH_SAMPLED**, strict 0 nas duas faces, zero
DIFFERENT/GCODE_DIFFERENT/PARTIAL_DIFFERENCE/ORACLE_ERROR. Foram repetidas
ISO, ISO/Rest e ISO/offset, com área preparada, CAM/G-code de cada fresa,
ordem e contagem de falhas aprovados. B_Cu ISO/Rest passou também na fresa
de 1 mm; B_Cu ISO/offset tem delta de área ~1,29e-14 mm². Relatórios:
`target/ncc-iso-members-final-20261011/real-python/{f-cu,b-cu}/report.json`.
As contagens não misturam o privado integral anterior com essa rodada focada.
Não foram repetidos todos os 17 casos privados: os dois ORACLE_ERROR de offset
sem ISO da rodada anterior permanecem conhecidos, fora da correção ISO.
Matching de regiões não usinadas não significa remover todo o cobre, e estes
ensaios não certificam UI visual, segurança física, outras receitas/projetos
ou uma ordem ISO invertida. Fonte original preservada por SHA-256.

## Receitas ampliadas e preparação ISO/offset — 2026-10-11

Nova suíte opt-in `-AdvancedRecipes`, separada da matriz Standard/Rest/Connect
abaixo. São sete receitas: Lines e Lines/Rest com Reference Geometry; Combo
Itself; Standard com margem 0,6 mm e offset 0,1 mm; ISO/CLEAR, ISO/Rest e
ISO/offset com referência. ISO usa duas fresas, 0,1 e 0,3 mm nessa ordem;
CLEAR usa 0,5 mm, ou 1/0,2 mm no Rest. Valores são fixtures de comparação,
**não recomendações de usinagem**. IN recebe todos os comprimentos convertidos.

O host real MainWindow paneliza, publica CAM, conserva ferramentas no formulário
CNC, gera/exporta programas multi-tool e salva/reabre o nativo. Não automatiza
FileChooser, gestos visuais ou máquina. Furos/slots e contorno continuam sendo
checados pelo FX; Cutout mantém a área preenchida explicitamente compartilhada.
O público executa o ToolPanelize original sobre fontes sintéticas explícitas;
F/B sintéticos são iguais, não quatro fontes independentes.

A matriz encontrou um defeito na preparação do keep-out: o FX somava raio ISO
e offset e bufferizava uma vez a 64 segmentos/quadrante. O Python gera primeiro
o envelope ISO a 64, depois aplica offset com o padrão Shapely de 16. Agora são
duas operações, com as regras GEOS já portadas para o FX em ambas, inclusive
nos contornos ISO. Arcos densos também diferiam com o buffer nativo JTS na
primeira operação. Sem ISO/offset, a preparação não foi alterada. Rest, conector,
tolerâncias de comparação e código Python permanecem intactos.

Regressão pública de canto/furo MM/IN compara a área com golden independente
Shapely 1.8.5.post1/GEOS 3.10.3 e distingue a expansão antiga. O oráculo agora
executa o `generate_envelope` original, observa a ordem ISO antes de CLEAR e
compara CAM/G-code de **cada fresa**, incluindo ISO. Casos sem ISO continuam
usando os mesmos helpers. Há regressões de isolamento entre execuções e de
diâmetros ISO/CLEAR ambíguos; isso não normaliza a entrada do legado.

Reprodução, na raiz do worktree:

```powershell
.\flatcam-next\compare-panelized-flows.ps1 -Project 'C:\caminho\projeto.FlatPrj' -AdvancedRecipes -Strict
# Só o projeto privado; ainda confere original SHA-256 no finally.
.\flatcam-next\compare-panelized-flows.ps1 -Project 'C:\caminho\projeto.FlatPrj' -AdvancedRecipes -SkipPublic -Strict
```

`-Python` e `-DependencyPath` opcionais permitem usar um executável compatível
com dependências isoladas já existentes, sem instalar nada nem alterar o ambiente.
Os logs são gravados continuamente em UTF-8 e a conclusão de cada receita é
exibida no terminal; não é preciso esperar o processo inteiro para ler o log.
Sem `-AdvancedRecipes`, a matriz anterior de seis grades públicas permanece.
Com ele, o público cobre MM/IN 2x2, gaps 5 mm; o privado usa os parâmetros da
grade informada. O ValueError legado em offset sem ISO sobre [MultiPolygon]
continua sendo ORACLE_ERROR e reprova strict, nunca aprovação parcial automática.
Ordem ISO invertida, outros parâmetros/grades/métodos e segurança física não
estão certificados por essa suíte.

Público final: **34/34 MATCH_SAMPLED**, strict 0 nos seis relatórios (MM/IN,
duas rotas F/B e Cutout), zero diferenças/erros. São 28 casos NCC ampliados
e seis controles Isolation/Cutout repetidos, não 34 fontes/receitas distintas.
Antes da correção: 26/34, com oito diferenças em offset e ISO/offset. A etapa
intermediária com dois buffers nativos JTS ainda deixou quatro ISO/offset
diferentes; não conta como entrega aprovada. O teste com arcos densos também
exigiu portar o buffer ISO inicial para as regras GEOS.
Artefatos públicos: `target/advanced-panelized-public-final-20261011`;
log do host: `target/advanced-panelized-public-final-20261011.log`.
Os probes `advanced-offset-geos-probe-*` são diagnósticos, não resultados do host.

Projeto real, face F_Cu: **7 MATCH_SAMPLED + 1 ORACLE_ERROR**, sem divergência
CAM/G-code ou contagem/ordem por fresa. O erro é `ncc-offset-reference`,
ValueError original da lista de MultiPolygon sem ISO. ISO, ISO/Rest e ISO/offset
passam; neste último, distância amostrada combinada ~2,49e-8 mm e delta de área
~1,60e-6 mm², dentro dos critérios originais. O controle anterior desse caso
era PARTIAL_DIFFERENCE, com distância ~0,00307 mm e delta ~0,02432 mm².
Mesmo contando as 248 regiões não usinadas pelas duas implementações, os
caminhos/ordem/programas de cada fresa coincidem pelos critérios. Isto não
significa cobre completamente removido por essa receita. Relatório privado:
`target/advanced-panelized-real-final-20261011/f-cu/report.json`.
O host real passou native save/reopen, registro de furos/slots e ferramentas;
fonte SHA-256 intacta.

Resultado privado completo: **14 MATCH_SAMPLED + 1 DIFFERENT + 2 ORACLE_ERROR**
em 17 casos. B_Cu teve seis matches, o mesmo erro original de offset sem ISO
e uma diferença em `ncc-iso-offset-reference`; Cutout teve um match. Strict
terminou com exit 1: os dois erros do legado e a divergência não contam como
paridade. Resumo: `target/advanced-panelized-real-final-20261011/comparison-summary.json`.

Na divergência B_Cu, os caminhos e G-code de cada fresa (ISO 0,1/0,3 e CLEAR
0,5 mm), ordem e contagem de falhas passam. A área preparada ainda difere em
**7,0222756e-5 mm²**, acima do critério relativo existente de 1e-8. Distância
amostrada combinada dos caminhos ~3,40e-8 mm; 256 regiões não usinadas nas duas
implementações. Isso não autoriza ignorar a área nem afirmar equivalência
exata. A causa residual não foi estabelecida nesta rodada. Próxima investigação:
localizar a diferença de área nessa receita B_Cu e isolar uma regressão pequena
antes de alterar buffer/overlay ou repetir o oráculo privado demorado.
Relatório: `target/advanced-panelized-real-final-20261011/b-cu/report.json`.

Validação geral: build completo `target/advanced-main-flows-full-20261011.log`
aprovado (1591 testes registrados, 1574 aprovados, 17 skips opcionais), mais
55 testes auxiliares Python. A comparação numérica não certifica UI visual,
segurança física ou receitas fora da matriz. Incremento ainda sem commit/push.

## Correção da margem zero e dos resíduos Rest

Esta implementação sucede a matriz de `ab93fc18`. O histórico de reprovações
abaixo documenta o diagnóstico anterior, não o resultado da correção.

Três diferenças de preparação foram corrigidas, sem alterar `paint_connect`,
as coordenadas da origem ou as tolerâncias:

1. NCC prepara a margem mesmo quando ela é zero. Area/Reference Geometry
   executam `GeosBufferOp(0)` por membro, seguido da união compatível com a
   referência Windows. Itself/Reference Gerber também executam o buffer zero.
2. Rest constrói o MultiPolygon dos caminhos varridos com raio
   `tool/1.9999999` e só então aplica `buffer(+1e-7)`, resolução 16. Eliminar
   a união intermediária preserva os anéis residuais da segunda fresa.
   A política conservadora de Paint não foi alterada.
3. A panelização Gerber preserva as cópias traduzidas numa GeometryCollection,
   como a lista do Python, até o CAM. A união antecipada por subárvores
   descartava a ordem necessária ao NCC. Join Objects continua usando sua
   união normal. O contorno, apertures, shapes, furos/slots e layout não mudam.

### Resultado final de produção

**Público: 114/114 MATCH_SAMPLED, strict 0 em todos os 18 relatórios.**
As seis grades/unidades e as duas rotas F/B foram repetidas após a correção.
O export contém a origem e referência sintéticas ANTES da panelização; o
oráculo executa o handler original `ToolPanelize` para construir sua própria
lista de cópias, depois os métodos originais NCC/CAM/parser CNC. Uma WKT de
GeometryCollection Java não pode ser tratada como se fosse uma lista Python.
Esta rodada valida a panelização das geometrias sintéticas e CAM/G-code,
não a importação de arquivos Gerber. F/B sintéticos continuam idênticos.

**Real: 19/19 MATCH_SAMPLED, strict 0 em F_Cu, B_Cu e Cutout.** As quatro
variantes Connect passam nas duas faces, com clearing areas, ordem e caminhos
por ferramenta conferidos. Nos quatro Rest/Connect reais, as fresas de 1 e
0,2 mm têm CAM e G-code comparados SEPARADAMENTE; nenhum erro/diferença foi
ocultado na geometria combinada. Distância máxima entre esses caminhos por
fresa: ~1,32e-11 mm. Nenhum GCODE_DIFFERENT/PARTIAL_DIFFERENCE/ORACLE_ERROR.

| Connect real | Distância F_Cu (mm) | Distância B_Cu (mm) |
| --- | --- | --- |
| Reference Geometry | 2,01e-14 | 1,99e-13 |
| Rest Reference Geometry | 0 | 2,84e-14 |
| Itself | 2,23e-12 | 3,55e-14 |
| Rest Itself | 2,84e-14 | 0 |

O host gerou/exportou os 19 CAM/CNC, salvou/reabriu ferramentas/geometrias,
G-code e registro de furos/slots. A leitura pelo codec nativo também confirmou
os membros/ordem dos Gerbers F/B/Edge_Cuts exatamente preservados. A asserção
de preservação dos Gerbers foi incorporada ao teste permanente de roundtrip.
Arquivo nativo: 225.089.052 bytes. Save/reopen 88,38/99,90 s, com comparações
concorrentes: não comparar esses tempos como benchmark/FPS.
SHA256 original permanece `C41580F1AC0D1E0BAF93026E6AFED18719A5614E78DA197407790C82667AA10C`.

Build integral: **1.512 aprovados / 1.527 registrados / 15 opcionais ignorados,
zero falhas/erros**. Teste privado opt-in executado separadamente; 39 auxiliares
Python, parser PowerShell e diff check passam. Novas regressões: margem zero
com golden GEOS de vértices/ordem em MM/IN; construção do residual Rest; cópias
Gerber imutáveis; oráculo público usa a origem original, não o resultado FX.

Artefatos finais ignorados:

- `target/panelized-final-independent-public-20261010` (host/export);
- `target/panelized-final-independent-public-python-20261010` (18 relatórios);
- `target/panelized-connect-copies-real-20261010` (host/export e 3 relatórios);
- `target/ncc-boundary-rest-final-verify-20261010.log`.

Os controles `panelized-raw-copies-*` e as rodadas intermediárias não entram
nesses números. Permanecem os limites da matriz: Standard/40%/Contour,
margem zero, sem offset/ISO. O ValueError legado de offset sobre lista de
MultiPolygon do corpus anterior não foi alterado nem contado como aprovação.
Cutout ainda compara área preenchida compartilhada; Excellon/apertures/UI
Python, reconstrução independente de Edge_Cuts, renderização/FPS e segurança
física CNC não são certificados. Não declarar todos os fluxos 100% por isso.

## Histórico: matriz anterior à correção

Base commitada: `6fc150ee` (união do cobre compatível com Python Windows).
Esta etapa amplia a **validação**, não altera NCC, conectores nem tolerâncias
de produção. Não é uma declaração de paridade total.

## Cobertura

`MainPanelizedCamFlowTest` executa os jobs reais do MainWindow para panelizar
F_Cu/B_Cu, Excellon, contorno e área com a mesma referência, gerar Isolation,
oito variantes NCC e Cutout, exportar CNC e salvar/reabrir `.fcnproj`:

- Reference Geometry e Itself × Rest on/off × Connect on/off;
- Standard, overlap 40%, margem zero, Contour on, sem Copper offset/ISO;
- ferramenta simples 0,5 mm; Rest com 0,2 e 1 mm, digitadas nessa ordem,
  verificando que a execução passa à maior primeiro;
- fixtures públicas MM/IN, grades 2×2 (gaps 5/5 mm), 3×1 (0/0) e 1×3 (2/7);
- origem fora de (0,0), abertura interna, furo e slot com ID preservado;
- ferramentas publicadas, formulário CNC, diâmetros, ordem, geometrias e
  parâmetros preservados após salvar/reabrir.

Rest gera CNC multi-tool a partir das ferramentas publicadas, não pelo Tcl
`cncjob` single-tool. O gerador real é usado, o programa é inserido no host e
exportado por `writeGcode`; cada fresa também tem programa individual para o
oráculo comparar seus cortes. Isso não testa FileChooser nem preferências.
Os demais casos mantêm a rota Tcl CNC. Parâmetros são só de teste, não receitas
para uma máquina. A contenção usa overlay robusto **apenas nas asserções**;
o overlay clássico de linhas quase coincidentes lançava TopologyException.
Isso não repara/arredonda os caminhos que são exportados e comparados.

**Itself não equivale à área das placas.** Usa o convex hull do cobre e pode
limpar espaços entre cópias e aberturas sem cobre. Reference Geometry da área
preenchida compartilhada restringe o clearing às placas e respeita seus vazios.
O teste verifica ambas as semânticas, não transforma uma na outra.

## Resultado público

Seis cenários FX passaram, incluindo CNC/persistência e recortes internos
opt-in. Oráculo original Python 3.11.1 / Shapely 1.8.5.post1 / GEOS 3.10.3:

**114 execuções: 82 MATCH_SAMPLED, 32 DIFFERENT, zero GCODE_DIFFERENT,
PARTIAL_DIFFERENCE ou ORACLE_ERROR. Strict retorna 1.** F_Cu/B_Cu sintéticos
têm o mesmo cobre; as duas faces verificam rotas do host, não fontes distintas.
O cobre traduzido público é explicitamente compartilhado com Python: CAM e
parser CNC independentes, não decodificação/panelização Python independente.

Todas as diferenças são Connect:

| Variante | MM 2×2 / 3×1 / 1×3 | IN 2×2 / 3×1 / 1×3 |
| --- | --- | --- |
| Reference Geometry + Connect | diferente / diferente / diferente | diferente / diferente / diferente |
| Rest Reference Geometry + Connect | diferente / diferente / diferente | diferente / coincide / coincide |
| Rest Itself + Connect | diferente / diferente / diferente | diferente / diferente / diferente |

Isolation, Cutout, NCC sem Connect e NCC Itself simples com Connect passam.
Os programas preservam os caminhos **FX** dentro dos critérios; isso não faz
um caminho diferente do Python se tornar aprovado.

Controle diagnóstico no cenário 1×3, Reference Geometry + Connect: área com
diferença simétrica zero, mas coordenadas/ordem não idênticas. Quando o Python
recebe a mesma área FX, os caminhos coincidem (~7,3e-15 mm; zero em IN).
Não contar esse controle como paridade: ele aponta a investigação para a
preparação/ordenação da área antes do clearing, não justifica subir tolerâncias
ou canonicalizar inícios de anéis arbitrariamente. Rest ainda exige diagnóstico
das áreas residuais e dos caminhos de cada ferramenta.

Ponto concreto para o próximo probe: `NccGenerator.mitreBuffer` devolve a
geometria sem operação quando a margem é zero. O `ToolNCC.apply_margin_to_bounding_box`
original executa buffer(0) mesmo nesse caso; para Reference Geometry, por
componente, seguido de unary_union. Esta diferença de sequência foi constatada
no fonte. Um **candidato diagnóstico** aplica GeosBufferOp(0) por componente
e NccCopperUnion na referência, mantendo o NCC de produção intacto. Os dois
casos simples 1×3 MM/IN passam frente à área Python independente. Não foi
integrado nem validado como correção da matriz inteira/Rest; não somar esses
dois controles aos 82 aprovados em produção. Artefatos privados/temporários
em `target/panelized-reference-candidate-*-20261010*`.

Relatórios públicos ignorados: `target/panelized-public-matrix-20261010` e
`target/panelized-public-matrix-python-20261010`. O caso `ncc-reference-geometry-connect`
em MM/IN 1×3 é uma reprodução pública pequena do residual.

## Reprodução no projeto original

```powershell
.\flatcam-next\compare-panelized-flows.ps1 -Project 'C:\caminho\projeto.FlatPrj' -Strict
# Outra grade real; parâmetros de espaçamento são SEMPRE em milímetros.
.\flatcam-next\compare-panelized-flows.ps1 -Project 'C:\caminho\projeto.FlatPrj' `
  -Columns 3 -Rows 1 -SpacingXmm 0 -SpacingYmm 0 -SkipPublic -Strict
```

Sem `-SkipPublic`, também exporta e compara os seis cenários públicos. Com a
opção, executa só o teste privado e seus oráculos. Resultado detalhado por
cenário em `comparison-summary.json`; `report.json`/HTML mostram testemunhas,
área, caminhos, ordem, fresa e G-code. Não interpretar strict reprovado como
aprovação parcial automática. Pastas novas sob `target`, fonte somente lida,
hash verificado no finally; nenhum software é instalado ou legado modificado.
A matriz densa gera arquivos privados grandes (centenas de MB por face);
reserve espaço e não versionar esses relatórios/projetos/programas.

Projeto real 2×2 / gaps 5 mm: fluxo FX completo aprovado, incluindo registro
de furos/slots e persistência. Comparação **19 casos = 11 MATCH_SAMPLED +
8 DIFFERENT**, zero GCODE_DIFFERENT/PARTIAL_DIFFERENCE/ORACLE_ERROR; strict 1.
Em ambas as faces, Isolation e as quatro variantes NCC sem Connect passam.
Cutout exterior também passa. As quatro variantes com Connect divergem em
cada face (inclusive Itself simples, que passara no cobre sintético). Áreas
de clearing coincidem; as ordens de ferramentas Rest e o G-code preservam os
caminhos FX. Isso não aprova os conectores diferentes do Python:

| NCC com Connect | Distância F_Cu (mm) | Distância B_Cu (mm) |
| --- | --- | --- |
| Reference Geometry | 0,113421 | 0,122187 |
| Rest Reference Geometry | 0,285471 | 0,040188 |
| Itself | 0,142296 | 0,052326 |
| Rest Itself | 0,053594 | 0,223271 |

Os cobres reais são decodificados e panelizados independentemente pelo handler
original Python; cópia de apertures/export é stub, não aprovação de toda a UI
Python. Furos/slots são verificados no FX, não por panelização Excellon Python
independente. Cutout compara áreas preenchidas explicitamente compartilhadas,
não uma conversão Python independente de Edge_Cuts nem usinagem interna real.
Referência/aplicativo/arquivo original intactos, SHA256
`C41580F1AC0D1E0BAF93026E6AFED18719A5614E78DA197407790C82667AA10C`.
Dados privados em `target/panelized-ncc-matrix-real-final-20261010`.

O projeto nativo ampliado tem 224.586.312 bytes; salvar levou 74,28 s e reabrir
83,24 s. Contém muito mais caminhos/programas que o cenário anterior; não é
uma comparação de renderer/FPS. Tentativas anteriores falharam nas asserções
clássicas, corrigidas para overlay robusto; só a execução final conta.
Build completo: **1.509 aprovados de 1.524 registrados, 15 opcionais ignorados,
zero falhas/erros**, mais 37 testes auxiliares Python aprovados.

## Próxima correção registrada antes desta implementação (histórico)

Investigar os anéis/ordem da área Reference Geometry e os resíduos Rest usando
as fixtures públicas acima, mantendo o oráculo original e os critérios atuais.
Repetir strict público e real após qualquer correção. A correção da união do
cobre em `6fc150ee` continua comprovada no corpus anterior; não cobre por si só
essas novas combinações panelizadas. Renderização/FPS e validação física CNC
permanecem fora destes testes.
