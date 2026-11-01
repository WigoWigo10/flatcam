# Validação da distribuição 0.1.0-beta.4 — 2026-11-01

**Recomendação técnica: pronta para testes controlados da Beta**, com os
limites abaixo. Preparação e geração solicitadas por Higor nesta conversa;
aceitação manual pelo testador ainda pendente. Não houve publicação remota.

- Fonte dos binários: **6b45a634c336b62d712fe2b522d94e262bdfef65**, árvore limpa.
- Tag anotada local: **v0.1.0-beta.4**, apontando para essa fonte.
- App/nativo: `0.1.0-beta.4`; Windows Installer: `0.1.4`; Windows x64.
- Runtime incluído: Temurin 25.0.5+8; pacotes sem assinatura digital.
- Pasta: `target/releases/0.1.0-beta.4-20261101-173648-508/`.
- Geração: manifesto `builtUtc=2026-11-01T17:41:13.8399357Z`.
- Validação concluída: `2026-11-01T17:43:20.6099902Z` (13:43 em Manaus).

## Evidências

| Verificação | Resultado / evidência |
| --- | --- |
| Verify integral da candidata | PASS: 2.334 casos, 2.316 aprovados, 18 opcionais ignorados, zero falhas/erros. `verify.log`, `verify-summary.json`. |
| Build nativo e pacotes | PASS: runtime, ZIP, MSI e EXE gerados, versão dos recursos do EXE conferida. `native-build.log`, `flatcam-release.json`, `artifacts.json`. |
| Portátil fora do repositório | PASS: pasta temporária com espaços/acento, hashes da imagem, Java embutido e PATH/JAVA_HOME externos inutilizados. |
| Fluxos nos JARs distribuídos | PASS: guia Outline, importação Gerber/Excellon, conversão de contorno, painel 2×2 com referência comum, Isolation/NCC/Cutout/CNC/Drilling e reabertura nativa/Python. 19 objetos, 8 furos e 4 rasgos no painel; quatro programas conferidos. |
| Políticas Z | PASS: três painéis × MM/IN × Standard/Machinist × Cut Z negativo/zero/positivo, com emissão real de G-code em Geometry. |
| Salvar / Salvar como | PASS: original atualizado, cópia com texto diferente sem alterar o original; Cancelar fecha o editor sem reverter os arquivos. Tooltip de Cancelar conferido no painel real. |
| Tabelas CAM | PASS: precisão de nova fresa, Delete de linha versus texto, Undo/Redo NCC/Isolation, receita mantida após gerar, retomada global e rascunho NCC reaberto pelo projeto. |
| Launcher e diagnósticos | PASS: opções padrão e `--software`, manifesto UTF-8 e gravação JFR com runtime incluído. Não é uma medição de desempenho gráfico. |
| Instalação limpa MSI | PASS: instalação por usuário em diretório temporário, probes no instalado e desinstalação. |
| Upgrade real Beta 3 → 4 | PASS: MSI Beta 3 entregue, mesma família, produto/JAR antigos removidos, probes no atualizado, projeto de teste externo com hash inalterado e desinstalação. |
| Limpeza | PASS: nenhuma instalação da família permaneceu registrada após os ensaios; instalação pessoal não foi substituída. |

Os comandos executados foram `build-beta.ps1` e `test-beta.ps1` com
`-BuildDirectory` explícito da candidata e `-PreviousMsi` apontando para o
MSI Beta 3 de Downloads. O SHA-256 do anterior foi reconferido:
`c1c577a70ea06c05affa485040dd43d8e2dac0c0893cc6070a68b7c1ec134186`.

`validation.json` registra todos os resultados e o diretório externo:
`%TEMP%/FlatCAM FX Beta teste-á-18193a5cef184069a957720be0dda563/`.
`portable-smoke`, `installed-smoke` e `upgrade/upgraded-smoke` contêm os
relatórios `smoke.json`, logs dos processos e diagnósticos/JFR. Os probes são
compilados contra os JARs extraídos do pacote e executados com seu próprio
runtime; janelas ficam invisíveis e preferências em memória.

Não houve falha nem repetição para obter aprovação na geração e validação
**desta candidata**. A falha de assinatura antiga do teste durante a implementação
do editor está registrada em `GCODE_SAVE_20261101.md`; não é ocultada nem
confundida com o ensaio de distribuição.

## Artefatos entregues em Downloads

| Arquivo | Bytes | SHA-256 |
| --- | ---: | --- |
| FlatCAM-FX-0.1.0-beta.4-windows-x64-setup.msi | 68047500 | `260e4a3936e97854be5dde6089fe881d38bcfaf684fa28de2ca1057b6b2ce892` |
| FlatCAM-FX-0.1.0-beta.4-windows-x64-setup.exe | 68621312 | `c4fbaa2ff0ac8a920f2ffb24c0dfe0b5ed4bd30451d90823b1f35af876d318aa` |
| FlatCAM-FX-0.1.0-beta.4-windows-x64.zip | 64151110 | `18e333964787820bdf46b30124d2a4b643cd176b0931d5e3e79b4575d203a555` |

Os hashes foram reconferidos após copiar. Também foram entregues notas,
`artifacts.json` e `SHA256SUMS.txt` com prefixo de versão.
MSI UpgradeCode `{5AE0F49D-3943-495D-81E3-B4B945BE5356}`;
ProductCode `{0574EF72-ABC2-3010-A1A2-6A12F7CD680A}`.

## Limites e retorno à versão anterior

- Não foram executados ensaio físico CNC, segunda máquina/VM, novo benchmark,
  revisão manual exaustiva, todos os DPIs/drivers ou novo exame de segurança.
- O MSI foi instalado e atualizado de verdade. Os cliques do assistente EXE
  não foram automatizados; o EXE usa a mesma imagem empacotada.
- Não foi testado downgrade automático. Diante de perda de projeto,
  divergência de coordenadas ou falha no salvamento, interromper os testes
  práticos e retornar à Beta 3 com uma cópia anterior compatível do projeto.
  A alternativa é desinstalar a Beta 4 e instalar o MSI Beta 3 preservado.
  Essa sequência de retorno não foi executada nesta rodada; instalação da
  Beta 3 e desinstalação da Beta 4 foram exercitadas nas etapas do upgrade.
- Projetos Machinist com coordenadas antes proibidas podem ser recusados no
  FX antigo. A compatibilidade reversa desses novos valores não é prometida.
- A preservação de dados testada se limita a arquivos **fora da instalação**.
  O desinstalador anterior pode remover dados extras da pasta do programa;
  mover projetos pessoais para Documentos antes de atualizar.
- Não há atualização automática, assinatura, upload, push ou envio ao testador.
  Este relatório registra a recomendação técnica para o pacote de testes;
  não inventa aprovação de uso em máquina ou aceitação do testador.

Documentação posterior não muda a identidade dos binários: a tag continua
apontando para **6b45a634**, mesmo quando o relatório entra em outro commit.
