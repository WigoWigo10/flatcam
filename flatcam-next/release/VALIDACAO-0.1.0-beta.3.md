# Validação da distribuição 0.1.0-beta.3 — 2026-10-29

**Resultado: pronta para testes controlados da Beta**, com os limites abaixo.
Não constitui certificação em CNC nem publicação em servidor/remoto.

- Fonte dos binários: **c5ba0c41eaef3ad6e081777253250593a218c1a6**, árvore limpa.
- Tag da candidata: **v0.1.0-beta.3**, apontando para essa fonte efetivamente testada.
- App: `0.1.0-beta.3`; Windows Installer: `0.1.3`; plataforma Windows x64.
- Runtime incluído: Temurin 25.0.5+8. Pacotes sem assinatura digital.
- Pasta: `target/releases/0.1.0-beta.3-20261029-200530-724/`.

## Evidências por etapa

| Etapa | Resultado / evidência |
| --- | --- |
| Verify integral da candidata | PASS: 2.268 registros, 2.250 aprovados, 18 opcionais ignorados, zero falhas/erros. `verify.log`, `verify-summary.json`. |
| Launcher nativo e montagem dos pacotes | EXE/MSI/ZIP gerados, `native-build.log`, inventário `artifacts.json` e hashes da imagem. O comando inicial teve erro na gravação final do ponteiro, descrito abaixo. |
| Portátil fora do repositório | PASS, pasta temporária com espaços/acento; hashes e runtime próprio, sem Java do ambiente. |
| Recursos e fluxos nos JARs distribuídos | PASS: guia, importação, Outline, panelização com furos/rasgos, CAM/CNC/Drilling, G-code, `.fcnproj`/`.FlatPrj` e arquivos sintéticos originais preservados. |
| Correções da Beta 3 no pacote | PASS: confirmar Spinner sem arredondar, Delete de linha versus texto, gerar usando o diâmetro editado, Seed → Standard, retomada global NCC e rascunho NCC reaberto pelo projeto nativo. |
| GPU/Software e diagnósticos | PASS: launcher padrão e `--software`, UTF-8 do processo, JFR com runtime incluído. |
| Instalação limpa MSI por usuário | PASS: probes no instalado e desinstalação, em local temporário. |
| Upgrade Beta 2 → Beta 3 | PASS: MSI Beta 2 realmente entregue, mesma família; produto/JAR anteriores removidos; projeto externo com SHA-256 inalterado; probes no atualizado e desinstalação. |
| Limpeza dos produtos de teste | Conferência Windows Installer sem produtos da família ainda registrados. A instalação pessoal do usuário não foi substituída. |

`validation.json` identifica a candidata e o ensaio externo em
`%TEMP%/FlatCAM FX Beta teste-á-9789322c4ecf43a0bb2e336241dda02f/`.
Logs, `smoke.json` e diagnósticos de portátil/instalado/atualizado foram preservados.

## Falhas iniciais e correção de ferramenta

O build foi invocado da raiz do worktree. O `Push-Location` do script não muda
o diretório de processo usado por `[IO.File]::WriteAllText` com caminho relativo.
O comando terminou com erro ao gravar `target/beta-latest.txt`, **depois** de
concluir verify, runtime, imagem, MSI/EXE/ZIP e `artifacts.json`.

Em seguida, o verificador acabou lendo o ponteiro antigo e testando a Beta 2.
O novo probe recusou aquela versão pelo arredondamento do Spinner, antes de
qualquer instalação. Essa tentativa não conta como validação da Beta 3.
Evidência preservada: `%TEMP%/FlatCAM FX Beta teste-á-73fa7fdb87a743b5894cecfa70a46dcc/portable-smoke/ui-cam.log`.

A validação correta foi repetida com **BuildDirectory explícito da Beta 3**,
conferindo versão, commit e todos os hashes antes dos ensaios. Ela passou.
O ponteiro foi restaurado por caminho absoluto. O gerador agora usa
`$PSScriptRoot` para gravá-lo; o verificador padrão recusa uma versão diferente
do POM, exigindo `-BuildDirectory` para ensaios históricos. Esse novo guarda foi
testado com ponteiro temporariamente na Beta 2, recusado antes de extrair/instalar,
e restauração do ponteiro Beta 3 (`stale-pointer-guard.log`).

Essas correções posteriores são de scripts/documentação; os binários permanecem
os da candidata **c5ba0c41** efetivamente verificada. Não foi movida a identidade
de fonte do pacote para um commit que não o gerou.

## Artefatos

| Arquivo | Bytes | SHA-256 |
| --- | ---: | --- |
| FlatCAM-FX-0.1.0-beta.3-windows-x64-setup.msi | 68018828 | `c1c577a70ea06c05affa485040dd43d8e2dac0c0893cc6070a68b7c1ec134186` |
| FlatCAM-FX-0.1.0-beta.3-windows-x64-setup.exe | 68592640 | `2c6d5164d16a113691a99899ae046a23b2989401de80deb9217b1b1c6332b08d` |
| FlatCAM-FX-0.1.0-beta.3-windows-x64.zip | 64116671 | `08f0799d2b6528c50ed7fdbbb5d03dc657b1040aaccceebb2ca9b119785bc845` |

MSI UpgradeCode `{5AE0F49D-3943-495D-81E3-B4B945BE5356}`;
ProductCode `{B85E0FF9-1342-37C6-9E13-A261BFDE81B9}`.

## Não executado / limites

- Segunda máquina/VM, CNC física, benchmark novo, revisão manual exaustiva e
  todos os monitores/DPI/drivers não foram ensaiados nesta distribuição.
- O MSI foi instalado e atualizado de verdade. Os cliques do assistente EXE
  não foram automatizados; ele contém a mesma imagem validada.
- Não houve novo exame de segurança, assinatura digital ou publicação remota.
- Não foi exercitado downgrade automático. Para retorno, desinstale a Beta e
  use uma cópia de projeto compatível na versão anterior.
- A preservação de dados verificada se limita a projetos **fora da instalação**.
  Mova dados pessoais para Documentos antes de atualizar; o MSI anterior pode
  apagar arquivos extras da pasta do programa.

Para reproduzir, use os comandos de [BUILD_BETA.md](BUILD_BETA.md), sempre
identificando o diretório explícito da candidata e o MSI anterior correto.
