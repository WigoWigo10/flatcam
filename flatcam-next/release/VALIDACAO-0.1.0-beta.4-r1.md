# Validação da Beta 4 — revisão 1 — 2026-11-01

**Pronta para testes controlados da Beta.** A revisão acrescenta edição de
diâmetros nas propriedades Excellon. Aceitação manual do testador permanece
pendente; não houve publicação remota nem ensaio físico CNC.

- Fonte dos binários: **0c14179b14ae0ccbfe8ada26da31df64a46b0489**, árvore limpa.
- Implementação: **6d3e69ef**; preparação da distribuição: **0c14179b**.
- Tag local: **v0.1.0-beta.4-r1**, nessa fonte; a tag Beta 4 original não mudou.
- App/Maven: `0.1.0-beta.4`; revisão: `1`; recurso nativo: `0.1.0-beta.4-r1`.
- MSI: **0.1.41**, Windows x64, Temurin 25.0.5+8 incluído, sem assinatura.
- Pasta: `target/releases/0.1.0-beta.4-r1-20261101-182140-197/`.
- Manifesto: `builtUtc=2026-11-01T18:25:52.8459139Z`.
- Validação: `2026-11-01T18:31:50.3971265Z` (14:31 em Manaus).

## Evidências

| Verificação | Resultado |
| --- | --- |
| Verify integral da candidata | PASS: 2.343 casos, **2.325 aprovados**, 18 opcionais ignorados, zero falhas/erros. `verify.log` e `verify-summary.json`. |
| Testes focados da implementação | PASS: 56 casos; regressão de tabelas CAM/teclado: 48 casos. Não somados novamente ao verify integral. |
| Pacotes e recursos nativos | PASS: runtime, ZIP, MSI e EXE gerados; versão nativa e propriedades MSI conferidas. |
| Portátil fora do repositório | PASS: hashes, diretório com espaços/acento, Java embutido e PATH/JAVA_HOME externos inutilizados. |
| Nova tabela nos JARs distribuídos | PASS: F2/Enter com `0,1981234567`, valor exato, posições preservadas, camada do Plot atualizada, Ctrl+Z/Y, reabertura `.fcnproj` e `.FlatPrj`, arquivo Excellon original intacto. |
| Fluxos anteriores | PASS: Outline, importação, painel 2×2 com referência comum, Isolation/NCC/Cutout/CNC/Drilling, 19 objetos, 8 furos/4 rasgos no painel e quatro programas. Políticas Z MM/IN, Salvar original/cópia, Cancelar e histórico CAM mantidos. |
| Instalação limpa MSI | PASS: instalação por usuário em diretório temporário, probes e desinstalação. |
| Beta 3 → Beta 4 r1 | PASS: MSI entregue 0.1.3 → 0.1.41; produto/JAR antigo removido, projeto externo preservado, probes no atualizado e desinstalação. |
| Beta 4 original → Beta 4 r1 | PASS: MSI entregue 0.1.4 → 0.1.41; produto anterior removido, JAR com o mesmo nome identificado pelo novo commit, projeto externo preservado, probes e desinstalação. |
| Launcher e diagnóstico | PASS: padrão/Software, manifesto UTF-8 e JFR com runtime incluído. Não é benchmark gráfico. |
| Limpeza e preservação | PASS: nenhuma instalação da família ficou registrada. MSI Beta 4 original mantém o SHA-256 anterior e seu pacote/tag foram preservados. |

Comandos: `build-beta.ps1`, sem pular testes, e `test-beta.ps1` com
`-BuildDirectory` explícito e `-PreviousMsi` contendo os MSIs Beta 3 e Beta 4
originais de Downloads. SHA-256 anteriores:

- Beta 3: `c1c577a70ea06c05affa485040dd43d8e2dac0c0893cc6070a68b7c1ec134186`.
- Beta 4: `260e4a3936e97854be5dde6089fe881d38bcfaf684fa28de2ca1057b6b2ce892`.

`validation.json` registra os dois upgrades e o diretório de evidências:
`%TEMP%/FlatCAM FX Beta teste-á-4655634c0b454fcca6d251eba118eb6d/`.
Relatórios `smoke.json` em `portable-smoke`, `installed-smoke`,
`upgrade-0.1.3/upgraded-smoke` e `upgrade-0.1.4/upgraded-smoke` identificam a
fonte exata. Os probes usam controles reais em janela invisível, runtime do
pacote e preferências em memória; não são aceitação visual manual.

Geração e validação desta candidata passaram na primeira rodada, sem falhas.
A primeira compilação do teste durante a implementação chamou uma API de
Plot inexistente; foi corrigida para inspecionar a camada real. O log inicial
foi preservado, conforme [registro da implementação](../EXCELLON_DIAMETER_TABLE_20261101.md).

## Entrega em Downloads

| Arquivo | Bytes | SHA-256 |
| --- | ---: | --- |
| FlatCAM-FX-0.1.0-beta.4-r1-windows-x64-setup.msi | 68063890 | `2bfda488e78661602525fe91b0c92d7fdc3b8c927453d5bc6a9bf6ae39bc320e` |
| FlatCAM-FX-0.1.0-beta.4-r1-windows-x64-setup.exe | 68637696 | `0c8a1e3a3b0fb3feccbf4103ee7c019b5cd1eb2afb89ce7dba1762679a2ccdc5` |
| FlatCAM-FX-0.1.0-beta.4-r1-windows-x64.zip | 64167397 | `3749d2c43fe9a84e6525598c6c51ec92f7f3af95c3337a00370d1293811d35e2` |

Hashes reconferidos após copiar. Notas, `artifacts.json` e `SHA256SUMS.txt`
também entregues com prefixo da revisão.
UpgradeCode: `{5AE0F49D-3943-495D-81E3-B4B945BE5356}`.
ProductCode: `{2DA52ACB-B5B8-3D11-A449-F4F06E967C99}`.

## Limites e retorno

- Editar afeta todos os furos/rasgos do ID, preservando posições. Saídas
  Geometry/CNC anteriores precisam ser regeneradas. O arquivo original não
  é sobrescrito pela tabela; salvar projeto/exportar continua necessário.
- Não foram executados ensaio físico CNC, segunda máquina/VM, benchmark,
  revisão manual exaustiva de monitores/DPIs, novo exame de segurança ou
  cliques no assistente EXE. O MSI foi instalado e atualizado de verdade.
- Dados preservados nos ensaios ficam **fora da instalação**. MSIs anteriores
  podem remover arquivos extras da pasta do programa; mover projetos para
  Documentos antes de atualizar.
- Downgrade automático não foi testado. Se houver perda de dados, mudança
  indevida de coordenadas ou falha de salvamento, interromper os testes e
  retornar ao instalador anterior preservado com cópia compatível do projeto.
  A sequência de desinstalar r1 e reinstalar anterior não foi validada como
  procedimento completo de retorno. Não há atualização automática.
- Limites de Machinist/Roland da Beta 4 original continuam aplicáveis.
  Não houve push, upload ou envio ao testador.

Este relatório entra em commit posterior; os binários e a tag continuam
associados à fonte **0c14179b**.
