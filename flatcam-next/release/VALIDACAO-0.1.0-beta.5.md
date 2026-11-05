# Validação da Beta 5 — candidata em preparação

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
