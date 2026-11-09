# Validação da Beta 7 — 2026-11-09

**Pacotes aprovados nos ensaios locais para testes controlados da Beta.**
Os cinco defeitos da vistoria receberam correções; aceitação manual do testador,
outro computador e CNC física continuam pendentes.

- Fonte exata dos binários/tag local `v0.1.0-beta.7`: **b64ef4dc435e0334f21f9ccc62670972113b4cdb**, árvore limpa.
- Vistoria e reproduções: `1940fbf4`; cinco correções: `a5a83060`;
  preparação da versão: `2a1f2b8c`; liberação de eventos Paint: `b64ef4dc`.
- App/Maven/nativo **0.1.0-beta.7**, revisão 0; MSI **0.1.70**.
- Runtime incluído Temurin 25.0.5+8, Windows x64; sem assinatura digital.
- Candidata final: `target/releases/0.1.0-beta.7-20261109-184101-491/`.
- Validação: `2026-11-09T18:49:46.9359415Z`.

## Resultados da fonte final

| Verificação | Resultado / evidência |
| --- | --- |
| Verify integral | **2435 casos, 2417 aprovados, 18 opcionais ignorados, zero falhas/erros**. `verify.log`, `verify-summary.json`; sem SkipTests/AllowDirty. |
| Regressões focadas | **92 aprovadas**, incluindo 12 trocas entre editores, fontes/referências Paint removidas/substituídas, seleção parcial válida, cancelamento, unidades, coleções e liberação do painel. `target/beta7-focused-final.log`. |
| Repetição/contraprova | **13 cenários × 10 execuções aprovadas**, com classes corrigidas; os mesmos contratos ainda falham em oito cenários nos JARs intactos da Beta 6. Não há erros finais do instrumento ou exclusões nessa contraprova. |
| Portátil | PASS: runtime próprio, ambiente sem Java externo, extração fora do repositório em pasta com espaço/acento, inventário de hashes e commit/versão conferidos. |
| Segurança de estado CAM nos pacotes | PASS: **13 cenários, sem exclusões**, em cada uma das três imagens (portátil, instalada e atualizada), com fonte exata dos JARs conferida e sem override de classes. Inclui os cinco defeitos e seleção da ferramenta seguinte. |
| Fluxos anteriores nos pacotes | PASS: Outline, painel 2×2, Isolation/NCC/Cutout/recortes internos/CNC/Drilling, diâmetros precisos, Save/Save as, políticas Z, NCC/Lines/rascunho, quatro idiomas e reabertura nativa/Python. Cada probe: 23 objetos, 5 programas, 8 furos/4 rasgos panelizados. |
| Distâncias Lines nos pacotes | PASS: **96 controles** NCC/Paint MM/IN, pads redondos/arredondados/cápsulas, duas fresas/ângulos, Rest/Connect/Contour. Tolerância 0,5% do diâmetro; não prova cobertura integral nem ausência de repetições. |
| GPU/Software/JFR | PASS: probes nativos, manifesto UTF-8 e diagnóstico/JFR com runtime incluído. Exceção intencional do probe de diagnóstico é esperada; não é crash incidental nem benchmark. |
| MSI limpo | PASS: instalação por usuário em pasta própria, probes e desinstalação. |
| Beta 6 → Beta 7 | PASS: MSI entregue **0.1.60 → 0.1.70**, produto/JAR anterior removido, projeto externo preservado por SHA-256, probes e desinstalação. |
| Estado final | PASS: zero produtos da família registrados depois dos testes; `final-install-state.json`. |
| Entrega | PASS: EXE/MSI/ZIP em Downloads, hashes reconferidos; pacotes anteriores intactos. |

`build-beta.ps1` rodou com a árvore limpa. `test-beta.ps1 -PreviousMsi` usou o
MSI efetivamente entregue da Beta 6, SHA-256
`c4fae6675a9668f070d4fbbf6497091604ba08f9ad77cf8cced3d6bbc595a6e2`.
Catálogos: **3946 entradas completas por idioma**, com ajuda nova multilinha
e formatação. A política escolhida fixa as unidades do painel Paint e filtra
origens/referências, preservando valores; não os converte automaticamente.

`validation.json` e `distribution-evidence/` preservam os resumos e logs dos
pacotes; os dados completos dos ensaios estão em `C:/Users/higor/AppData/Local/Temp/FlatCAM FX Beta teste-á-2bfbef055e3648e1a45ce830fadb2929/`.
Os controles reais foram exercitados sem janela visível e com preferências
isoladas. Nenhum projeto privado foi usado/modificado nessa validação.

## Primeiras falhas e novas tentativas

1. As regressões foram escritas antes das correções: a rodada vermelha de 73
   casos teve 16 reprovações por asserções (algumas propagadas como ExecutionException
   pela thread FX), preservadas em `target/beta7-red-tests.log` e `beta7-red-reports/`.
2. A primeira rodada corrigida expôs um null de seleção inicial numa lista imutável.
   Foi corrigido sem relaxar asserções; abertura IN sem seleção recebeu controle.
   Logs `beta7-focused-green.log`, `beta7-focused-green-2.log` e `beta7-focused-final.log`.
3. A primeira pré-candidata `0.1.0-beta.7-20261109-183536-712` foi **interrompida
   deliberadamente**, sem instaladores, após reproduzir um evento de Paint fechado
   cancelando a seleção seguinte. Essa execução não conta como suíte aprovada.
   O probe isolado permanece vermelho em `beta7-cleanup-red.log` e verde com a
   correção em `beta7-cleanup-green.log`; o caso entrou em JUnit e no probe empacotado.
4. A candidata final reexecutou integralmente a suíte e os ensaios da distribuição.
   Ajuste do runner para ausência de override de classes corrigiu um erro do
   instrumento; não foi contado como falha do aplicativo.

## Arquivos em Downloads

| Arquivo | Bytes | SHA-256 |
| --- | ---: | --- |
| FlatCAM-FX-0.1.0-beta.7-windows-x64-setup.exe | 68989952 | `e4c411d98d4be3517ff9c6e7f17e4472e9cf08fcaa5552a9efc581713454d80f` |
| FlatCAM-FX-0.1.0-beta.7-windows-x64-setup.msi | 68416143 | `e50c6592aff9a8b395f8b62de02b92684d03323fc435ad46b0c49bd7d5014a97` |
| FlatCAM-FX-0.1.0-beta.7-windows-x64.zip | 64522627 | `3854b6febb72c8851ee6d464feca97286de088f5299717b3d695df48aebc3291` |

Notas, hashes, `artifacts.json` e `validation.json` também foram copiados com
prefixo `FlatCAM-FX-0.1.0-beta.7-`. Família MSI preservada:
`{5AE0F49D-3943-495D-81E3-B4B945BE5356}`.

## Limites e recuo

As pendências anteriores de trajetos repetidos e regiões sem limpeza do Lines
**continuam abertas** (`LINES_RAW_PATH_AUDIT.md`). Esta entrega corrige os cinco
defeitos de UI/estado da vistoria e o efeito adicional de liberação de eventos;
não muda o algoritmo de limpeza, nem declara paridade integral.

Não executados: assistente EXE por cliques, outro PC/VM, todos os monitores/DPIs,
revisão nativa integral ES/FR, novo benchmark ou CNC física. Preservação de dados
testada **fora da pasta instalada**; desinstaladores anteriores podem remover
arquivos extras nela. Para recuar, use o portátil anterior em outra pasta e cópias
dos projetos; não há promessa de downgrade automático.

Nenhum envio ao testador ou comando push foi executado pelo agente. Os registros
de validação entram em commit posterior; os binários e a tag mantêm a fonte
**b64ef4dc**. Tags e instaladores anteriores foram preservados.
