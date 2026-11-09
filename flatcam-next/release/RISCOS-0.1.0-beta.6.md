# Escopo de validação — Beta 6

Avaliação qualitativa em 2026-11-09. Impacto e probabilidade usam escala 1–5;
os produtos abaixo priorizam ensaios, não representam incidência medida.
Não há teste físico CNC nesta entrega.

| Risco / gatilho | I × P | Detecção e mitigação nesta rodada | Limite da evidência |
| --- | --- | --- | --- |
| Ângulo Lines incorreto afeta trajetos NCC/Paint e parâmetros por fresa | 5 × 3 = 15 | Regressões geométricas para seis ângulos, Auto/Combo e Rest; painel MM/IN, precisão e reabertura NCC; probe dos JARs empacotados | Não prova todos os controladores nem corte físico; repetições/áreas sem limpeza da auditoria continuam abertas |
| Outra ferramenta oculta os comandos de um editor e impede sair da sessão | 4 × 3 = 12 | Regressões nos quatro editores, preservação de alterações/Undo e saída; probe de navegação e Cancelar nos pacotes | Ensaio automatizado de rotas selecionadas, sem testar todos os gestos manuais |
| Atualização instala runtime/JAR incorreto ou afeta arquivos do usuário | 4 × 3 = 12 | Commit/versão/hashes vinculados ao pacote; runtime isolado GPU/Software/JFR; instalação e upgrade do MSI Beta 5 em pasta própria | Não substituir instalação existente do usuário; preservação cobre projeto fora da pasta instalada; EXE wizard e outro PC não ensaiados |
| Texto/controle sem contraste ou tradução | 2 × 2 = 4 | Catálogos completos em quatro idiomas; testes de tema e painel estreito | Não equivale a revisão humana integral de todas as telas |

O relatório `VALIDACAO-0.1.0-beta.6.md` deve registrar o resultado real, primeiro
erro e qualquer nova tentativa. Riscos conhecidos de Lines não são marcados
como corrigidos por um build aprovado. Os binários são candidatos a testes
práticos; não se declara aprovação de produção ou segurança física.
