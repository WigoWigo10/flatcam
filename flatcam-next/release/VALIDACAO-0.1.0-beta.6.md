# Validação da Beta 6 — 2026-11-09

**Pacotes gerados e aprovados nos ensaios locais para testes controlados da Beta.**
Aceitação manual do testador, outro computador e usinagem física continuam pendentes.

- Fonte exata dos binários: **c28019b72b16ee65534c62ba9f7e4fdf02814f95**, árvore limpa.
- Implementação e auditorias: **9e87ea55**; versão e probes: **c28019b7**.
- Tag anotada local: **v0.1.0-beta.6**, vinculada à fonte dos binários.
- Maven/app/nativo: **0.1.0-beta.6**, revisão 0; MSI **0.1.60**.
- Runtime incluído: Temurin 25.0.5+8, Windows x64; sem assinatura digital.
- Candidata: `target/releases/0.1.0-beta.6-20261109-153800-491/`.
- Validação concluída: `2026-11-09T15:47:01.0159804Z`.

## Resultados

| Checagem | Resultado e evidência |
| --- | --- |
| Verify completo | **PASS: 2406 casos, 2388 aprovados, 18 opcionais ignorados, zero falhas/erros**; duração 5:07. `verify.log` e `verify-summary.json`. |
| Recursos novos na suíte | PASS: seis ângulos geométricos, Auto/Combo/Rest, NCC MM/IN, rascunho/Undo e quatro temas; quatro editores mantêm sessão, painel e histórico ao navegar e conseguem sair. |
| Portátil | PASS: ZIP extraído fora do repositório em pasta com espaço/acento; Java externo indisponível; hashes internos e versão/commit dos JARs conferidos. |
| Recursos novos empacotados | PASS: NCC Seed → Standard → Lines com ângulo explícito e reabertura nativa do rascunho; Paint leva o ângulo preciso aos segmentos gerados; navegação nos quatro editores mantém painel e permite Cancelar. |
| Fluxos anteriores nos pacotes | PASS: importação, Outline, painel 2×2 alinhado, Isolation/NCC/Cutout, recortes internos, CNC/Drilling, precisão Excellon, políticas Z, Salvar/Salvar como, histórico e reabertura nativa/Python. Cada probe produziu 23 objetos e 5 programas, com 8 furos/4 rasgos panelizados. |
| GPU/Software e diagnóstico | PASS: launcher nativo nos dois modos, manifesto UTF-8, runtime incluído e JFR; a exceção intencional do probe de diagnóstico é esperada. Não é benchmark de FPS. |
| MSI limpo | PASS: instalação por usuário, probes dos instalados e desinstalação em diretório temporário próprio. |
| Beta 5 → Beta 6 | PASS: MSI entregue **0.1.50 → 0.1.60**, produto/JAR anterior removido, projeto externo preservado por SHA-256, probes no atualizado e desinstalação. |
| Estado final | PASS: zero produtos da família registrados depois dos ensaios (`final-install-state.json`). |
| Entrega | PASS: EXE, MSI e ZIP copiados para Downloads e SHA-256 reconferido; Betas anteriores preservadas. |

`build-beta.ps1` rodou sem `-SkipTests` ou `-AllowDirty`; `test-beta.ps1` usou
`-PreviousMsi` com o MSI realmente entregue da Beta 5, SHA-256
`f9002841b181bc1dfb52b1c8b1d96497b12815349197f622595f723da1d49da2`.
Geração e ensaio da distribuição passaram na primeira rodada. Catálogos: 3941
entradas completas por idioma; oito testes auxiliares da auditoria aprovados
com o Python/Shapely do ambiente de comparação. A invocação preliminar desses
auxiliares pelo Python global sem Shapely falhou por ambiente; não foi falha da
candidata. Ajustes nos leitores locais de resumo não alteraram os testes/build.
Uma segunda cópia de entrega recusou sobrescrever o EXE já idêntico em Downloads;
o arquivo foi mantido e reconferido, e a entrega dos documentos foi concluída.
Primeiro erro preservado em `target/beta6-delivery-repeat-first-error.log`.

`validation.json` registra a identidade e resultados. Relatórios/logs selecionados
dos três probes foram preservados em `distribution-evidence/` na candidata.
Os logs completos de MSI e os diagnósticos ficam em
`C:/Users/higor/AppData/Local/Temp/FlatCAM FX Beta teste-á-507cb18607634108b3864c2b78b8f4e0/`. Os controles reais foram exercitados em janela invisível,
com preferências em memória; isso não substitui uma revisão visual manual nova.

## Arquivos em Downloads

| Arquivo | Bytes | SHA-256 |
| --- | ---: | --- |
| FlatCAM-FX-0.1.0-beta.6-windows-x64-setup.exe | 68985856 | `daf5719740191ffbcf6d29185cb2c2f4b117f9e0670c177dcaacfdfe9a9a7bdb` |
| FlatCAM-FX-0.1.0-beta.6-windows-x64-setup.msi | 68412047 | `c4fae6675a9668f070d4fbbf6497091604ba08f9ad77cf8cced3d6bbc595a6e2` |
| FlatCAM-FX-0.1.0-beta.6-windows-x64.zip | 64518836 | `f6a913822d73f56d7ce34e15614f93b64a3dc811882c28edd0ce8cf0908dd9f3` |

Notas, hashes e JSONs também foram copiados com prefixo `FlatCAM-FX-0.1.0-beta.6-`.
A família de upgrade continua `{5AE0F49D-3943-495D-81E3-B4B945BE5356}`.

## Limites e acompanhamento

As repetições e regiões sem limpeza registradas em `LINES_RAW_PATH_AUDIT.md`
**não foram corrigidas nesta Beta**. Não se afirma cobertura completa das placas
públicas, paridade integral com Python ou melhora de desempenho de renderização.
As prioridades e lacunas de evidência estão em `RISCOS-0.1.0-beta.6.md`.

Não executados: cliques do assistente EXE, outro PC/VM, todos os monitores/DPIs,
revisão nativa integral ES/FR, novo benchmark, auditoria de segurança ou CNC física.
O teste de preservação cobre projetos **fora da pasta instalada**; instaladores
anteriores podem remover arquivos extras dentro dela. Para recuar, use o portátil
anterior em outra pasta e cópias dos projetos; não se declara downgrade automático.

Nenhum envio ao testador ou comando push foi executado diretamente pelo agente.
Ao verificar as referências durante a rodada, `fork/flatcam-next` já apontava para
`c28019b7`, com reflog `update by push`; esse é o estado observado, sem atribuir a
origem da atualização. A tag não foi enviada por esta sessão.

Este relatório entra em commit posterior. Os binários e a tag continuam vinculados
ao commit **c28019b7**, e as tags/pacotes anteriores não foram alterados.
