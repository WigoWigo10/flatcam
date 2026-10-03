# Plano de paridade: FlatCAM FX e FlatCAM Python

Atualizado em **2026-10-03**. Base inicial: revisão `91d3ce50` do FX e o checkout
Python deste repositório. Este documento registra a sequência futura e as
entregas explicitamente verificadas; não declara todas as etapas concluídas.

## Objetivo e escopo

Consolidar primeiro o fluxo normal **Gerber/Excellon → Geometry → CNC Job →
salvar/reabrir**, com resultados comparáveis, interação previsível e limitações
explícitas. Depois, completar opções avançadas, preferências e automação.

Paridade envolve quatro dimensões: função, resultado CAM/CNC, persistência e
UI/UX. Ter uma ferramenta no menu ou um teste headless aprovado não comprova
que seu fluxo completo equivale ao Python. Diferenças deliberadas de segurança
devem ser documentadas, não removidas apenas para imitar o legado.

## Ponto de partida (antes da etapa 1)

- O FX já possui importação Gerber/Excellon, editores, operações CAM, geração
  CNC, banco de ferramentas, conversões/junções e compatibilidade de projetos.
  As 24 ferramentas do menu Ferramentas têm implementações, com cobertura
  parcial das opções e dos fluxos avançados.
- A última comparação no projeto real autorizado resultou em **7
  `MATCH_SAMPLED`, 3 `DIFFERENT` e nenhum `ORACLE_ERROR`**, entre dez casos.
  Isso não significa 70% de paridade do aplicativo nem igualdade geométrica.
- NCC Standard, NCC Seed e Paint Standard ainda divergem. As maiores distâncias
  amostradas nesse exemplo são aproximadamente 0,02059 mm, 0,12928 mm e
  0,02159 mm, respectivamente. A causa definitiva das diferenças restantes
  ainda não foi estabelecida.
- A regressão funcional registrou 787 testes: 776 aprovados e 11 opcionais
  ignorados, **com limpeza de TempDir desativada somente naquela execução**.
  A suíte normal ainda apresentou falhas de limpeza no Windows; não está
  declarada aprovada.
- Os probes de renderização D3D na GTX 1650, software e Java/Maven passaram.
  Isso verifica inicialização/renderização básica, não fluidez em placas
  grandes ou desempenho dos cálculos CAM.

Detalhes e ressalvas: [estado do projeto](CONTEXTO_E_PROGRESSO.md),
[comparação CAM](COMPARACAO_CAM.md) e [inventário de UI](UI_INVENTORY.md).

## Sequência de implementação

### 1. Estabilizar a infraestrutura de testes — prioridade imediata

Investigar as falhas intermitentes de limpeza dos diretórios JUnit no Windows,
isolando recursos abertos, comportamento do sistema de arquivos e versões das
dependências. Fixar/documentar o ambiente de comparação Python, inclusive a
dependência isolada necessária para executar os algoritmos legados.

**Critério de conclusão:** suíte normal aprovada em execuções repetidas, com
limpeza ativa; comparação reproduzível sem modificar o algoritmo Python nem
o ambiente principal do usuário. Não resolver desativando permanentemente
limpeza, testes ou verificações.

**Entrega local em 2026-10-03:** suporte de testes compartilhado, JUnit 6.1.3
via BOM e Surefire 3.5.4; repetição limitada para diretórios Windows já vazios,
sem ocultar falhas persistentes. Três execuções finais da suíte normal passaram
com limpeza ativa: 807 registrados, 796 aprovados, 11 opcionais ignorados.
Os 20 testes novos incluem controles de arquivo bloqueado e junctions.
Detalhes em [TESTES.md](TESTES.md). A causa exata da falha histórica e a
validação em outros sistemas continuam abertas; os casos CAM divergentes não
foram reclassificados nem alterados nesta entrega.

### 2. Resolver ou caracterizar Standard/Paint/Seed — prioridade imediata

Criar reproduções sintéticas mínimas a partir das divergências já localizadas.
Comparar erosões sucessivas em Standard/Paint e ponto interior, arcos e término
dos anéis em Seed. Separar defeitos de implementação de diferenças dos kernels
JTS/GEOS ou limitações do próprio método legado.

**Critério de conclusão:** cada divergência reproduzida recebe correção com
regressão, ou explicação demonstrada com limite explícito e orientação ao
usuário. Manter os critérios numéricos existentes; cobertura próxima não
autoriza classificar trajetos diferentes como iguais.

**Investigação em 2026-10-03:** diferenças dos buffers Standard/Paint e
instabilidade da scan-line Seed reproduzidas em casos sintéticos. Protótipo
isolado passa Standard/Paint na placa real, mas Seed ainda difere com entrada
decodificada independentemente.

**Correção em 2026-10-03:** a parte Standard/Paint da investigação foi
incorporada à aplicação (`org.flatcam.cam.ncc.geosbuffer`, um buffer próprio
do projeto que alinha só as duas regras que divergiam do GEOS 3.10.3, sem
tocar na dependência JTS global). No projeto real autorizado, `ncc-standard`
e `paint-standard` passaram de `DIFFERENT` (~0,0206 mm e ~0,0216 mm) para
`MATCH_SAMPLED` (~0,0006 mm e ~0,0000011 mm); no probe público, a distância
contra o oráculo GEOS caiu a ruído de ponto flutuante. Seed continua
`DIFFERENT` - a instabilidade do ponto inicial (item 3 da investigação) não
foi resolvida nesta entrega. Nenhuma outra etapa foi avançada. Detalhes,
testes e limites em [INVESTIGACAO_CAM.md](INVESTIGACAO_CAM.md).

### 3. Ampliar a validação e completar opções CAM

Adicionar casos para Isolation/NCC com Rest, múltiplas ferramentas, Connect,
referências e áreas; Isolation Follow e exceções; Cutout Free-form, Thin,
M-Bites, gaps manuais e margem negativa; operações em MM e IN.
Implementar ou corrigir as lacunas demonstradas por esses casos.

**Critério de conclusão:** comparar caminhos e pegada da ferramenta, além de
pontes de fixação, regiões não alcançadas e associação dos parâmetros por
ferramenta. Conferir também o fluxo visual dos painéis. Preservar as recusas
de geração quando um padrão Cutout perde pontes solicitadas.

Referências: [comparação CAM](COMPARACAO_CAM.md) e [Cutout](CUTOUT.md).

### 4. Completar Drilling e validar CNC além de XY

Priorizar exclusões em Drilling, parâmetros avançados por ferramenta e seu
consumo a partir da Tools Database. Revisar os limites dos pós-processadores
já portados e as opções ainda sem equivalente, incluindo compensação e
posições de troca/finalização quando aplicáveis.

**Critério de conclusão:** verificar alturas Z, movimentos de aproximação,
retração e deslocamento, avanços, spindle, troca de ferramenta e posições
finais, incluindo combinações incompatíveis. Testar prévia e limites de
tamanho sem retirar proteções. Parser de G-code e desenho XY não comprovam
segurança física; teste a seco na máquina exige validação própria.

Referências: [Geometry/CNC](GEOMETRY_CNC.md),
[exclusões CNC](CNC_EXCLUSIONS.md) e [pós-processadores](PREPROCESSADORES.md).

### 5. Consolidar compatibilidade e persistência de projetos

Ampliar round-trips FX → Python → FX para dados avançados, macros Gerber,
parâmetros por ferramenta e configurações de objetos. Preservar rascunhos no
formato FX quando necessário e avaliar separadamente o que o legado consegue
representar. Incluir abrir/salvar pela interface Python, além dos testes de
serialização.

**Critério de conclusão:** nenhuma perda silenciosa. Dados não representáveis
devem produzir aviso ou recusa explícita conforme o risco. Manter o arquivo
original intacto nos ensaios e testar os formatos FX e legado.

Referência: [compatibilidade FlatPrj](COMPATIBILIDADE_FLATPRJ.md).

### 6. Fechar lacunas dos editores e da UI/UX

Comparar os editores Gerber, Geometry e Excellon existentes, sem recomeçá-los.
Inventariar ferramentas e gestos ausentes; conferir seleção visual/tabelas,
Delete, cancelamento, desfazer/refazer, foco e atalhos. Revisar organização
dos painéis, estados habilitados, feedback de progresso e tooltips.

**Critério de conclusão:** executar roteiros manuais equivalentes nos dois
aplicativos, com temas claros/escuros e escalas de tela relevantes. Registrar
diferenças intencionais de UX e impedir perda de seleção ou alterações por
ações inesperadas. Testes headless complementam, mas não substituem essa etapa.

### 7. Completar preferências e automação

Inventariar preferências globais, defaults por ferramenta, persistência e
menus parciais. Depois, definir o escopo de scripts/CLI e a compatibilidade
pretendida com a automação Python antes de implementar novos comandos.

**Critério de conclusão:** preferências sobrevivem ao reinício e afetam os
fluxos correspondentes; comandos cobertos têm argumentos, erros e resultados
documentados e testados. Recursos sem equivalente ficam identificados.

## Desempenho: trabalho transversal

Medir tempo CAM, memória, carregamento, latência de seleção e navegação nas
mesmas placas e condições. Distinguir custo de geometria, preparação do plot
e renderização; usar a GPU dedicada não acelera automaticamente operações JTS.

Otimizações devem preservar precisão, cancelamento e responsividade. Uma nova
arquitetura GPU/nativa depende de gargalos medidos e de comparação com a
solução atual; não é pré-requisito para corrigir paridade funcional.
Referências: [desempenho do plot](PLOT_PERFORMANCE.md),
[launcher/GPU](NATIVE_GPU.md) e
[arquitetura futura](ARQUITETURA_RENDERIZACAO_FUTURA.md).

## Entregas e acompanhamento

Executar **as etapas 1 e 2 primeiro, em commits separados**. As demais etapas
podem exigir vários incrementos pequenos; um commit deve representar uma
mudança coerente e verificada, não uma etapa artificialmente completa.

Para cada incremento, registrar:

1. Lacuna observada e comportamento esperado no Python.
2. Implementação ou diferença deliberada, com teste de regressão.
3. Comandos, ambiente e resultados da verificação; pendências manuais à parte.
4. Atualização dos documentos afetados e identificação do commit.

Manter projetos privados, relatórios, WKT/SVG e G-code de comparação fora do
Git, em `target/`. Não alterar o oráculo Python para obter aprovação.
Este plano não estabelece prazo nem porcentagem global de paridade: acompanhar
cenários cobertos e pendências demonstráveis por etapa.
