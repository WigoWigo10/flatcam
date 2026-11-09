# Prioridades de validação — Beta 7

Avaliação qualitativa de impacto e probabilidade, escala 1–5, em 2026-11-09.
Os escores priorizam ensaios; não representam taxas de incidentes medidas.
As cinco correções estão em `a5a83060`; o relatório final deve identificar a
fonte exata da candidata, resultados e exclusões, sem inferir homologação física.

| Risco / gatilho | I × P | Evidência exigida | Limite |
| --- | --- | --- | --- |
| Abrir editor pelo contexto esconde uma sessão anterior | 4 × 3 = 12 | Matriz de 12 trocas entre editores, painel/saída/histórico preservados; contraprova na Beta 6; probe dos JARs | Rotas automatizadas, sem revisão manual de todos os gestos |
| Paint usa origem/referência antiga ou publica após cancelar | 4 × 3 = 12 | Seleção parcial válida, remoção/substituição antes/durante cálculo, cancelamento entre worker e FX; fonte/época/handle revalidados | Casos sintéticos sincronizados, sem arquivo privado do relato |
| Unidade errada altera a medida física da fresa | 5 × 3 = 15 | Origem/referência filtradas por unidade; valor externo recusado; MM/IN e abertura sem seleção; valores precisos por fresa | Política fixa evita reinterpretação; não há conversão automática nem CNC física |
| Seletor ignora polígonos agrupados | 3 × 2 = 6 | Coleções unitárias aninhadas, MultiPolygon unitário, linhas e vazios | Não prova aceitação de todo arquivo de terceiros |
| Instalação/upgrade deixa JAR anterior ou afeta dados pessoais | 4 × 3 = 12 | Fonte/versão/hashes, runtime isolado, GPU/Software/JFR, MSI 0.1.60 → 0.1.70, projeto externo preservado | Recusar substituir uma instalação do usuário; EXE wizard/outro PC não ensaiados |
| Ajuda nova sem tradução/contraste | 2 × 2 = 4 | 3946 textos completos por idioma, auditorias de tema/tooltip e suíte integral | Não equivale a revisão nativa integral ES/FR |

Condições para gerar/disponibilizar a Beta: fonte commitada limpa, regressões
aprovadas, suíte integral e ensaios dos pacotes. Preserve a primeira falha e
as novas tentativas. Algoritmos Lines com repetições/regiões sem limpeza continuam
documentados; não são fechados por uma build aprovada.

Se o testador observar troca indevida de unidade, saída antiga após edição ou
perda de acesso ao editor, interromper esse fluxo e guardar arquivo/log/passos.
O portátil anterior pode ser usado em outra pasta, com cópias dos projetos;
não se promete downgrade automático de preferências ou recursos novos.
