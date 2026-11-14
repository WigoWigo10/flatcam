# Validação da Beta 8 — 2026-11-14

**Pacotes aprovados nos ensaios locais para testes controlados da Beta.**
Não equivale a homologação de usinagem física ou aceitação manual do testador.

- Fonte exata dos binários/tag local `v0.1.0-beta.8`: **0b910a9e40e0996b90c423fd713645f1d7adb0ea**, árvore limpa.
- App/Maven/nativo **0.1.0-beta.8**, revisão 0; MSI **0.1.80**.
- Runtime incluído Temurin 25.0.5+8, Windows x64; sem assinatura digital.
- Candidata: `C:\Users\higor\Documents\VSCode\FlatCAM\flatcam-next-worktree\flatcam-next\target\releases\0.1.0-beta.8-20261114-174311-010`.
- Validação dos pacotes: `2026-11-14T17:51:44.5507638Z`.

| Verificação | Resultado / evidência |
|---|---|
| Verify integral | **2672 casos, 2652 aprovados, 20 opcionais ignorados**, zero falhas/erros. `verify.log`; sem SkipTests/AllowDirty. |
| Portátil | PASS: runtime próprio, extração fora do repositório em pasta com espaço/acento, Java externo indisponível, versão/commit e hashes conferidos. |
| CAM/editores/projetos nos pacotes | PASS: 13 cenários de estado por imagem (portátil, instalada e atualizada), além de Outline/painel/Isolation/NCC/Cutout/recortes internos/CNC/Drilling, edição de diâmetros, Save/Save as, políticas Z, idiomas e reabertura de projetos. |
| GPU/Software/JFR | PASS: probes nativos nos dois modos e gravação JFR com runtime incluído. Exceção intencional do diagnóstico é esperada. Não é benchmark de renderização. |
| MSI limpo | PASS: instalação por usuário em pasta temporária, probes e desinstalação. |
| Beta 7 → Beta 8 | PASS: MSI 0.1.70 → 0.1.80, produto/JAR anterior removido, projeto externo preservado por SHA-256, probes e desinstalação. |
| Limpeza com JARs distribuídos | PASS: seis métodos na Audio Watermark, fonte intacta, GRBL relido, nenhum fallback radial e núcleo residual estável zero no critério declarado. WKT dos seis idênticos aos revistos em `90762165`. Não significa sobra bruta zero. |
| Distâncias nos JARs distribuídos | PASS: 32 Standard + 32 Espiral + 96 Lines por regiões, incluindo MM/IN, NCC/Paint, duas fresas, Rest/Connect/Contour e ângulos quando aplicáveis. Sem override de classes; versão e commit conferidos. |
| Entrega local | PASS: MSI/EXE/ZIP em Downloads com SHA-256 reconferido; entregas anteriores preservadas. |

Dados completos: `validation/beta8-distribution-20261114.json`, com os hashes dos artefatos, manifesto, relatórios dos pacotes e campanha CAM. Logs detalhados e projetos de ensaio ficam nas pastas indicadas pelo JSON.

## Exclusões e limites

Não executados: assistente EXE por cliques, outro computador/VM limpa, todos os monitores/DPIs, revisão nativa integral ES/FR ou CNC física. A comparação dos seis métodos usa caixa do cobre como alvo diagnóstico, não Outline físico. Tempos de geração das auditorias incluem contexto de validação e não são um novo benchmark isolado.

A suíte integral mantém avisos CSS já presentes nos painéis de teste isolados de CAM; as variáveis de prévia estão definidas nos quatro temas. Não houve mudança de CSS nesta entrega; revisão visual integral continua pendente. A candidata final passou na primeira rodada de build, testes de distribuição e auditorias CAM adicionais; não houve retentativa desses comandos.

Recaminhamentos, limites de orçamento e programas grandes continuam possíveis. As correções não declaram paridade integral com Python nem remoção física perfeita. Projetos devem ficar fora da pasta instalada: desinstaladores anteriores podem remover arquivos extras dela. Para recuar, usar o portátil anterior em outra pasta, com cópias dos projetos; não há promessa de downgrade automático.

Skill aplicada: `release-readiness`. Usados os scripts de build/validação do próprio projeto; o CLI agregador opcional `qa.mjs` não está disponível neste checkout. Não foram enviados commits/tags ao remoto nem mensagens ao testador. A documentação de resultados entra em commit posterior; os binários e a tag mantêm a fonte indicada acima.
