# Validação da Beta 5 — 2026-11-05

**Pronta para testes controlados da Beta.** Geração e ensaio da distribuição
aprovados. Aceitação manual do testador e usinagem física permanecem pendentes.

- Fonte dos binários: **8e2fcc186c20b5934927981e336fd1753447f552**, árvore limpa.
- Implementação dos recortes: **617f2791**; idiomas/SVGs: **68da8707**.
- Tag anotada local: **v0.1.0-beta.5**, apontando para a fonte dos binários.
- Maven/app/nativo: `0.1.0-beta.5`, revisão 0; Windows Installer **0.1.50**.
- Temurin 25.0.5+8 incluído, Windows x64, pacotes sem assinatura digital.
- Pasta: `target/releases/0.1.0-beta.5-20261105-183738-780/`.
- Validação: `2026-11-05T18:53:56.9929046Z` — 14:53 em Manaus.

## Resultados da candidata

| Verificação | Evidência e resultado |
|---|---|
| Verify integral | **PASS: 2374 casos, 2356 aprovados, 18 opcionais ignorados, zero falhas/erros.** `verify.log`, `verify-summary.json`; duração 10:57. A falha de limpeza temporária de uma rodada anterior da implementação não apareceu nesta candidata. |
| Pacotes | PASS: ZIP, MSI e EXE gerados com runtime; `artifacts.json` registra tamanho/hash. POMs, versão nativa e MSI conferidos. |
| Portátil isolado | PASS: ZIP extraído fora do repositório, pasta com espaço/acento, ambiente sem Java externo, hashes do inventário conferidos. |
| Novo fluxo nos JARs distribuídos | PASS: painel de recortes internos real, recusa da fresa inviável, prévia com faixa sem esvaziar o miolo, legenda/resumo/tooltips, geração mantendo o painel aberto, CNC e reabertura nativa/Python com diâmetro preciso e fonte intacta. |
| Idiomas e SVGs | PASS: catálogos dos quatro idiomas e ícones vetoriais carregados do pacote. A suíte da candidata também exerceu os idiomas em JVMs separadas e os quatro temas. |
| Fluxos anteriores | PASS: importação, Outline, painel 2×2 alinhado, Isolation/NCC/Cutout/CNC/Drilling, edição Excellon, rascunho NCC, políticas Z MM/IN, Salvar/Salvar como, Cancelar e histórico CAM. Cada probe produziu 22 objetos, 8 furos/4 rasgos panelizados e cinco programas. |
| GPU/Software e diagnóstico | PASS: probes nativos nos dois modos, manifesto UTF-8 e JFR usando o runtime incluído. Não é benchmark de FPS. |
| MSI limpo | PASS: instalação por usuário em diretório temporário, probes nos instalados e desinstalação. |
| Beta 4 r1 → Beta 5 | PASS: MSI entregue **0.1.41 → 0.1.50**, produto anterior removido, projeto externo preservado por SHA-256, probes nos atualizados e desinstalação. |
| Limpeza final | PASS: consulta Windows Installer encontrou zero produtos da família registrados após os testes. |
| Entrega | PASS: EXE/MSI/ZIP copiados para Downloads e SHA-256 reconferido. |

Comandos executados: `build-beta.ps1`, sem `-SkipTests`/`-AllowDirty`, e
`test-beta.ps1 -PreviousMsi` com o MSI da Beta 4 r1. O MSI anterior foi conferido
contra seu `artifacts.json`: SHA-256
`2bfda488e78661602525fe91b0c92d7fdc3b8c927453d5bc6a9bf6ae39bc320e`.

`validation.json` registra a fonte, resultado e evidências externas em
`%TEMP%/FlatCAM FX Beta teste-á-844ec03bde5b45dab946752edd7af152/`.
Há `smoke.json` e logs em `portable-smoke`, `installed-smoke` e
`upgrade-0.1.41/upgraded-smoke`. Os probes usam controles reais em janela
invisível e preferências em memória; não são uma nova aceitação visual manual.
Geração e ensaio da distribuição passaram na primeira rodada.

## Arquivos em Downloads

| Arquivo | Bytes | SHA-256 |
|---|---:|---|
| FlatCAM-FX-0.1.0-beta.5-windows-x64-setup.exe | 68977664 | `663665561cc4a4af13acf6ed6db76efd90c1200fb95fb3d30be14ebd4c537f4a` |
| FlatCAM-FX-0.1.0-beta.5-windows-x64-setup.msi | 68403855 | `f9002841b181bc1dfb52b1c8b1d96497b12815349197f622595f723da1d49da2` |
| FlatCAM-FX-0.1.0-beta.5-windows-x64.zip | 64509983 | `2719e8309d45ab8889e19e631f485ba01523641d11365e2d56fb89d9f969e6e2` |

Notas, `artifacts.json`, `validation.json` e `SHA256SUMS.txt` também copiados com
prefixo `FlatCAM-FX-0.1.0-beta.5-`. ProductCode:
`{6C867D3D-729E-3CF1-ABA5-00D0E5568102}`. UpgradeCode preservado:
`{5AE0F49D-3943-495D-81E3-B4B945BE5356}`. A Beta 4 r1 e sua tag foram preservadas.

Não foram executados: clique no assistente EXE, outro computador/VM, revisão
exaustiva de todos os monitores/DPIs, novo benchmark, auditoria de segurança,
certificação física CNC ou downgrade automático. A preservação de dados testada
é **fora da pasta instalada**. Não houve push, upload ou envio ao testador.

Recomendação: disponibilizar para testes controlados com cópias dos projetos,
mantendo os limites das notas. A autorização do usuário para gerar a Beta não
representa aceitação manual ou homologação física. Este relatório entra em
commit posterior; os binários e a tag continuam associados à fonte **8e2fcc18**.

## Plano inicial de validação — registro anterior à execução

Data local: 5 de novembro de 2026. Escopo: criar pacotes Windows x64 para testes
práticos; não publicar remotamente ou instalar por cima do aplicativo do usuário.
A identidade exata da candidata e os resultados devem ser acrescentados depois
da geração. Esta preparação não aprova testes ainda não executados.

## Prioridades de risco

Os escores abaixo são avaliações técnicas de impacto/probabilidade (1..5), não
taxas de incidentes medidas. Base: `.agents/qa-project-context.md`, implementação
e resultados locais registrados em `CONTEXTO_E_PROGRESSO.md`.

| Área | Impacto | Probabilidade | Escore | Evidência exigida / lacuna |
|---|---:|---:|---:|---|
| Compensação e seleção de recortes internos | 5 | 3 | 15 | MM/IN, faixa pelo raio, sem perímetro externo, recusa de fresa inviável, cancelamento; novo probe deve executar o painel e CNC nos JARs distribuídos. Ensaio físico permanece fora do escopo. |
| Atualização/instalação | 4 | 3 | 12 | Instalar/desinstalar isoladamente e atualizar o MSI entregue da Beta 4 r1; conferir produto antigo removido e projeto externo preservado. Arquivos dentro da pasta instalada não são protegidos pelos instaladores legados. |
| Salvar/reabrir projeto e precisão | 4 | 2 | 8 | Testes de persistência e probe com `.fcnproj`/`.FlatPrj`, diâmetro integral e G-code preservados. Projetos sintéticos não representam todos os arquivos privados. |
| Idiomas e tooltips | 2 | 3 | 6 | Catálogos completos, SVGs no pacote, auditoria por idioma, quatro temas. Revisão nativa integral ES/FR não realizada. |
| Inicialização GPU/Software | 3 | 2 | 6 | Probes fora do repositório com Java do pacote, caminho acentuado, ambiente reduzido e JFR. Outro PC/VM e todos os DPIs não exercitados. |

Falha de maior impacto no recorte: selecionar/compensar a abertura errada pode
produzir CNC indevido. Gatilhos considerados: troca de origem, cancelamento,
unidades e fresa maior que a abertura. Detecção: asserções de limites, comprimento,
precisão e recusa atômica, mais o fluxo real do painel na distribuição. Alcance:
usuários que geram esse CNC; nenhuma prévia certifica a operação física.

Falha de maior impacto no upgrade: versão/família incorreta deixa JAR antigo ou
remove dados extras da pasta instalada. Detecção: teste MSI isolado, comparação
de produto/commit e SHA-256 do projeto externo. O testador deve manter dados fora
da pasta do aplicativo. O ensaio recusa substituir uma instalação existente.

## Critérios desta rodada

| Checagem | Comando/evidência | Estado nesta preparação |
|---|---|---|
| Fonte versionada limpa | `git status`, POMs e recurso nativo 0.1.0-beta.5 / MSI 0.1.50 | Preparada; commit final deve ser registrado |
| Suíte completa da candidata | `build-beta.ps1` → `verify.log` | Pendente |
| Catálogos | `tools/write_ui_catalogs.py --strict --check` | 3936 entradas completas por idioma na implementação |
| Portátil, GPU/Software, CAM/projetos/JFR | `test-beta.ps1` | Pendente |
| MSI limpo e upgrade Beta 4 r1 | `test-beta.ps1 -PreviousMsi ...` | Pendente |
| Hashes e conteúdo distribuído | `artifacts.json`, `flatcam-release.json`, `SHA256SUMS.txt` | Pendente |
| Assistente EXE, outro computador e CNC física | Teste prático posterior | Não executado nesta preparação |
| Assinatura, auditoria de segurança, benchmark novo | Fora do escopo desta rodada | Não executado |

Recomendação nesta preparação: aguardar o build e o ensaio da candidata. A
autorização do usuário é para criar a Beta de testes; não equivale a homologação
física, revisão de todos os controles ou aprovação de produção.

## Recuo e acompanhamento

Se o testador observar abertura/compensação incorreta, falha ao salvar/reabrir ou
crash reproduzível, interromper o uso desse fluxo e conservar logs/projeto de
reprodução. Comparar com o ZIP portátil da Beta anterior em outra pasta, usando
cópias. Não se declara downgrade automático do MSI, preferências ou recursos
novos de projeto. Pacotes/tags anteriores devem permanecer intactos.

Distribuição inicial prevista para os testadores escolhidos pelo usuário. A
expansão depende do relato de importação, CAM, revisão/exportação e persistência
sem perda de dados. Envio, acompanhamento externo e teste em máquina não foram
executados pelo agente nesta preparação.
