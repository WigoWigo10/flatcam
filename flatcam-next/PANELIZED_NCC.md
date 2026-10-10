# NCC depois da panelização — matriz e limites (2026-10-10)

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

## Próxima correção

Investigar os anéis/ordem da área Reference Geometry e os resíduos Rest usando
as fixtures públicas acima, mantendo o oráculo original e os critérios atuais.
Repetir strict público e real após qualquer correção. A correção da união do
cobre em `6fc150ee` continua comprovada no corpus anterior; não cobre por si só
essas novas combinações panelizadas. Renderização/FPS e validação física CNC
permanecem fora destes testes.
