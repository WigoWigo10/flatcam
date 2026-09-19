# CONTEXTO DO PROJETO — FlatCAM FX / FlatCAM Next

> Documento-base para humanos e IAs/LLMs. Leia-o integralmente antes de propor arquitetura, código, cronograma ou mudanças no projeto.

## 1. Resumo executivo

O projeto pretende criar uma modernização gradual do FlatCAM, provisoriamente chamada **FlatCAM FX** ou **FlatCAM Next**. A principal referência de experiência visual e responsividade é a interface **UGS-FX**, do Universal G-Code Sender (repositório `winder/Universal-G-Code-Sender`).

A intenção não é apenas trocar a aparência do FlatCAM. O objetivo é obter uma aplicação CAM desktop moderna, modular, fluida e testável, capaz de continuar responsiva enquanto processa arquivos Gerber, Excellon, geometrias, toolpaths e G-code pesados.

Decisão arquitetural atual:

- **Interface principal:** JavaFX.
- **Linguagem principal da nova aplicação:** Java.
- **Viewport:** renderizador dedicado e acelerado por GPU; não representar placas grandes com centenas de milhares de `Node`s JavaFX.
- **Geometria inicial:** JTS, sempre que for funcional e suficientemente rápida.
- **Código nativo:** C++ apenas para hotspots comprovados por profiling e benchmarks.
- **Python:** mantido temporariamente como implementação de referência e backend legado durante a migração; removido progressivamente, não de uma vez.
- **Estratégia:** migração incremental por fatias verticais, mantendo versões executáveis e comparáveis em todas as fases.

## 2. Motivação

O FlatCAM atual possui grande valor funcional, mas sua arquitetura e sua interface em Python/PyQt podem apresentar problemas de responsividade, acoplamento, uso limitado de paralelismo e dificuldade de evolução. Operações CAM pesadas não devem bloquear a interface nem fazer o sistema parecer travado.

O usuário teve uma boa impressão do UGS-FX e quer reproduzir principalmente:

- fluidez visual;
- sensação de aplicação desktop nativa;
- resposta imediata de menus, painéis, zoom e pan;
- organização modular;
- separação entre interface e trabalho pesado;
- viewport eficiente;
- comportamento previsível durante jobs demorados.

O UGS-FX é uma **referência de UX, arquitetura modular e desempenho percebido**, não uma especificação a ser copiada literalmente. O FlatCAM possui um fluxo CAM mais complexo que um G-code sender e precisa adaptar a experiência às suas próprias operações.

## 3. Objetivos do produto

### 3.1 Objetivos principais

1. Criar uma interface JavaFX moderna e coerente, inspirada na fluidez do UGS-FX.
2. Manter a interface responsiva durante parsing, geração de geometria, otimização e geração de G-code.
3. Migrar gradualmente o backend de Python para Java.
4. Usar C++ somente quando medições mostrarem vantagem relevante.
5. Separar claramente UI, aplicação, domínio CAM, renderização e infraestrutura.
6. Permitir GUI, CLI e, futuramente, API sobre o mesmo núcleo.
7. Preservar a compatibilidade funcional por meio de testes diferenciais contra o FlatCAM atual.
8. Adaptar uso de CPU, memória e paralelismo ao hardware disponível sem comprometer a estabilidade.

### 3.2 Não objetivos iniciais

- Reescrever todo o FlatCAM de uma só vez.
- Migrar tudo para C ou C++.
- Criar inicialmente uma aplicação web, Electron ou React.
- Copiar o código, marca ou identidade visual do UGS-FX.
- Criar um sender CNC completo antes de reconstruir o fluxo CAM essencial.
- Otimizar sem profiling ou escolher tecnologia nativa apenas por expectativa teórica.
- Garantir compatibilidade perfeita com todos os plugins e comportamentos históricos desde o primeiro protótipo.

## 4. Princípios arquiteturais

### 4.1 Java como centro da aplicação

Java deverá concentrar:

- JavaFX e lógica de apresentação;
- modelo de projeto;
- comandos, jobs e filas;
- undo/redo;
- parsing de Gerber e Excellon;
- gerenciamento de ferramentas;
- geração de geometrias e G-code;
- configurações e persistência;
- cache;
- scheduler e gerenciamento de recursos;
- CLI;
- coordenação do renderizador.

O objetivo é evitar uma arquitetura definitiva com duas runtimes. Durante a transição poderá existir comunicação Java–Python, mas ela será uma ponte temporária.

### 4.2 C++ somente em hotspots medidos

C++ poderá ser usado em tarefas como:

- otimização intensiva de caminhos;
- clipping e offsets massivos;
- operações geométricas específicas que JTS não execute adequadamente;
- geração e preparação de buffers para a GPU;
- algoritmos numéricos cuja versão Java seja comprovadamente insuficiente.

Antes de criar uma implementação nativa, deve existir:

1. benchmark reproduzível;
2. profiling que identifique o gargalo;
3. implementação Java correta como referência, quando viável;
4. teste de equivalência entre as versões;
5. ganho significativo que compense JNI/FFM, empacotamento e manutenção multiplataforma.

Preferir C++ a C puro para o núcleo geométrico, devido a RAII, containers, algoritmos e bibliotecas adequadas ao domínio. Nunca assumir que código nativo terá menos bugs; segurança de memória e concorrência exigem cuidado adicional.

### 4.3 Trabalho pesado nunca na thread JavaFX

A JavaFX Application Thread deve executar apenas tarefas curtas relacionadas à interface. Parsing, operações CAM, geração de buffers e leitura pesada devem ocorrer em workers.

Requisitos dos jobs:

- progresso observável;
- cancelamento cooperativo;
- tratamento de falhas;
- limites de memória e concorrência;
- resultados imutáveis ou transferidos de modo seguro;
- atualização incremental da UI sem tempestade de eventos.

### 4.4 Viewport separado da árvore de controles

JavaFX organiza janela, menus, painéis e ferramentas. A geometria da placa deve ser desenhada por uma superfície/renderizador especializado.

Evitar:

```java
for (Segment segment : segments) {
    pane.getChildren().add(new Line(/* ... */));
}
```

Para geometrias grandes, utilizar conceitos como:

- desenho em lotes;
- vertex/index buffers;
- culling;
- level of detail (LOD);
- índice espacial;
- cache de camadas;
- seleção/picking eficiente;
- atualizações parciais;
- renderização acelerada pela GPU.

A tecnologia gráfica concreta ainda deve ser validada por protótipo. Não presumir que JavaFX Canvas, OpenGL ou Vulkan é automaticamente a escolha definitiva.

## 5. Arquitetura-alvo conceitual

```text
flatcam-next/
├── flatcam-fx/           # JavaFX, view models, painéis, temas e comandos de UI
├── flatcam-application/  # casos de uso, jobs, undo/redo, projetos e eventos
├── flatcam-cam/          # Gerber, Excellon, geometrias, toolpaths e G-code
├── flatcam-renderer/     # viewport, buffers, LOD, picking e GPU
├── flatcam-scheduler/    # CPU/RAM, filas, prioridades e cancelamento
├── flatcam-cli/          # automação sem interface
├── flatcam-compat/       # ponte temporária para o backend Python legado
├── flatcam-native/       # C++ opcional para hotspots comprovados
├── flatcam-tests/        # unidade, integração, golden files e diferenciais
└── benchmarks/           # corpus, cenários, métricas e resultados versionados
```

Fluxo principal pretendido:

```text
JavaFX UI
    ↓ comandos / estado observável
Application Layer
    ↓ casos de uso e jobs
CAM Core em Java + JTS
    ↓ somente quando necessário
Native Core em C++
```

Consumidores possíveis do núcleo:

```text
CAM Core
├── FlatCAM FX
├── CLI
└── API futura
```

## 6. Organização da interface

A primeira interface poderá seguir esta organização conceitual:

```text
┌──────────────────────────────────────────────────────────┐
│ Menu e barra de ferramentas                              │
├──────────────┬──────────────────────────┬────────────────┤
│ Projeto      │                          │ Ferramenta /   │
│              │       VIEWPORT GPU       │ Propriedades   │
│ Gerbers      │                          │                │
│ Excellon     │                          │ Parâmetros     │
│ Geometry     │                          │ e ações        │
│ CNC Jobs     │                          │                │
├──────────────┴──────────────────────────┴────────────────┤
│ Jobs / progresso / mensagens / console                   │
└──────────────────────────────────────────────────────────┘
```

Diretrizes visuais:

- painéis redimensionáveis e recolhíveis;
- viewport como elemento central;
- temas claro e escuro;
- CSS JavaFX consistente;
- ícones SVG próprios ou devidamente licenciados;
- hierarquia visual simples;
- pouca borda e ruído visual;
- feedback claro de progresso, erro e cancelamento;
- atalhos de teclado documentados;
- acessibilidade e escalonamento HiDPI;
- layouts que funcionem em telas menores e maiores.

O fluxo deve refletir as etapas típicas:

```text
Gerber/Excellon → inspeção → ferramenta → operação CAM
→ geometria/toolpath → CNC Job → G-code/exportação
```

## 7. Metas iniciais de desempenho e UX

Estas são metas de engenharia a validar, não medições já alcançadas:

- pan e zoom próximos de 60 FPS em projeto médio no hardware de referência;
- latência de interação comum inferior a aproximadamente 50 ms;
- menus, seleção de abas e expansão de árvore sem bloqueio perceptível;
- resize contínuo sem congelamento;
- mostrar/ocultar camada já carregada de forma praticamente instantânea;
- operações CAM nunca bloqueiam a JavaFX Application Thread;
- cancelamento reconhecido rapidamente nos pontos seguros do algoritmo;
- carregamento grande informa progresso e permite abortar;
- uso de memória medido e limitado por políticas explícitas;
- degradação controlada em máquinas mais fracas por LOD, batching e limites de workers.

Não inventar números de aceleração. Tabelas anteriores com tempos Python/Java/C++ eram meramente ilustrativas. Todo resultado futuro deve registrar hardware, sistema operacional, runtime, arquivo de teste, commit, configuração e método de medição.

## 8. Estratégia de migração

### Fase 0 — Baseline e corpus

- identificar a versão/fork exato do FlatCAM usado como referência;
- mapear módulos, dependências, licenças e fluxos essenciais;
- criar corpus legalmente utilizável de Gerber, Excellon, projetos e G-code;
- registrar comportamento, screenshots, tempos e consumo de memória do legado;
- escolher uma versão LTS/suportada do Java com base em compatibilidade real;
- documentar critérios de aceitação.

### Fase 1 — Esqueleto JavaFX

- janela, menus, painéis, tema e estado básico;
- modelo de projeto mínimo;
- sistema de comandos/jobs;
- logging, diagnóstico e empacotamento inicial;
- integração temporária com funções do backend Python, se necessário.

### Fase 2 — Protótipo do viewport

- carregar um formato intermediário simples;
- pan, zoom, fit, camadas, seleção e medição;
- comparar alternativas gráficas em um benchmark representativo;
- validar GPU, HiDPI, drivers e plataformas-alvo;
- decidir a tecnologia final do renderer somente após os testes.

### Fase 3 — Primeira fatia vertical

Uma fatia recomendada:

```text
abrir Gerber → interpretar → exibir → selecionar camada → inspecionar propriedades
```

Criar parser Java e comparar seu resultado com o parser Python legado.

### Fase 4 — Excellon e modelo geométrico

- parsing de Excellon;
- ferramentas/furos;
- integração com JTS;
- transformações, unidades, bounds e índices espaciais;
- testes diferenciais e golden files.

### Fase 5 — Operação CAM completa

Implementar uma operação ponta a ponta, por exemplo isolamento, incluindo parâmetros, preview, job cancelável, geometria resultante e geração de G-code.

### Fase 6 — Expansão funcional

- drilling;
- cutout;
- copper clearing;
- edição e transformações;
- CNC Job;
- exportação;
- CLI.

### Fase 7 — Profiling e otimização nativa

- medir gargalos reais;
- otimizar estruturas e algoritmos Java primeiro;
- introduzir C++ somente onde o ganho compense;
- manter fallback e testes de equivalência quando apropriado.

### Fase 8 — Desativação do legado

- atingir paridade definida;
- documentar incompatibilidades deliberadas;
- remover a ponte Python por módulos;
- manter caminho de migração de projetos e configurações;
- só então declarar o frontend PyQt e o backend Python como descontinuados.

## 9. Validação e prevenção de regressões

Durante a transição, executar o legado e o novo núcleo sobre as mesmas entradas.

Comparar, conforme o caso:

- unidades e precisão;
- aperturas e ferramentas;
- quantidade e tipos de primitivas;
- bounding boxes;
- áreas com tolerância numérica explícita;
- topologia e validade geométrica;
- polígonos, furos e ilhas;
- toolpaths;
- movimentos, feeds, profundidades e spindle;
- trocas de ferramenta;
- arcos e sentidos;
- saída G-code normalizada.

Não depender apenas de comparação textual. Resultados geometricamente equivalentes podem ter ordem, segmentação ou formatação diferentes.

Camadas de teste desejadas:

- testes unitários;
- testes de propriedades/fuzzing para parsers;
- golden files;
- testes diferenciais Python × Java;
- testes de integração;
- testes de UI essenciais;
- benchmarks de desempenho;
- testes de memória e cancelamento;
- testes multiplataforma.

## 10. Gerenciamento adaptativo de recursos

O sistema deve detectar os recursos disponíveis, mas não simplesmente ocupar todos os núcleos.

O scheduler deve considerar:

- CPUs lógicas e físicas quando disponível;
- memória livre e orçamento do processo;
- tipo, custo e paralelizabilidade do job;
- prioridade interativa versus batch;
- concorrência entre jobs;
- pressão do garbage collector;
- tamanho do projeto;
- cancelamento e checkpoints;
- configuração manual do usuário.

Uma reserva de capacidade deve manter UI, sistema operacional e renderização responsivos. Virtual threads podem ajudar em I/O e coordenação, mas não tornam automaticamente algoritmos CPU-bound mais rápidos. Para CPU intensiva, usar pools dimensionados e particionamento medido.

## 11. Decisões ainda abertas

Estas questões não devem ser tratadas como definidas:

1. Nome definitivo do projeto e identidade visual.
2. Fork/versão exata do FlatCAM que será a referência funcional.
3. Versão exata do Java/JDK e política de atualização.
4. Tecnologia do viewport: JavaFX Canvas, integração OpenGL, Vulkan ou outra.
5. Mecanismo de ponte temporária com Python: processo separado/API/IPC ou outra opção.
6. Biblioteca de injeção de dependência, eventos e serialização, se alguma.
7. Formato novo de projeto e estratégia de compatibilidade.
8. Sistemas operacionais oficialmente suportados na primeira versão.
9. Sistema de build/empacotamento e instaladores.
10. Escopo funcional mínimo para o primeiro release utilizável.
11. Política de plugins/extensões.
12. Licenças e obrigações específicas do código reutilizado.

Toda decisão deve ser registrada por ADR (Architecture Decision Record) com contexto, opções, consequências e data.

## 12. Riscos principais

- **Reescrita longa sem versão utilizável:** mitigar com fatias verticais pequenas.
- **Paridade funcional difícil:** manter corpus, oracle legado e testes diferenciais.
- **Renderer incompatível entre GPUs/OS:** prototipar cedo e possuir fallback.
- **JNI/FFM aumentar complexidade:** só introduzir após profiling.
- **JTS não atender algum algoritmo CAM:** medir, adaptar ou usar implementação especializada.
- **Precisão numérica e topologia:** definir tolerâncias, escalas e invariantes explicitamente.
- **Consumo de memória por modelos orientados a objetos:** usar estruturas compactas e buffers em dados massivos.
- **Concorrência causar resultados não determinísticos:** estados claros, imutabilidade e testes repetidos.
- **Copiar aparência do UGS de modo indevido:** inspirar-se em padrões de UX, criando identidade e ativos próprios.
- **Mudança simultânea de UI e core esconder regressões:** comparar cada fatia com o legado.

## 13. Regras para IAs/LLMs que colaborarem

Ao ajudar neste projeto, uma IA deve:

1. Ler este documento antes de sugerir alterações.
2. Distinguir decisões, hipóteses, metas e resultados medidos.
3. Não propor reescrita total como primeiro passo.
4. Não trocar JavaFX por Electron/React sem nova decisão explícita do responsável.
5. Não mover código para C++ sem benchmark e profiling.
6. Manter tarefas pesadas fora da thread JavaFX.
7. Evitar milhares de Nodes JavaFX para geometria CAM.
8. Preservar testes de equivalência com o legado.
9. Considerar licenças antes de copiar código ou ativos.
10. Explicar trade-offs e registrar decisões arquiteturais relevantes.
11. Usar dados reais e reproduzíveis em afirmações de desempenho.
12. Preferir um protótipo pequeno e verificável a uma abstração prematura.
13. Não confundir o FlatCAM FX com um simples G-code sender: ele é primeiro um ambiente CAM para PCB.
14. Atualizar este documento quando uma decisão de alto nível mudar.

## 14. Primeiros entregáveis recomendados

1. `ADR-0001-javafx-java-cpp.md`: formalizar JavaFX + Java + C++ sob demanda.
2. `BASELINE.md`: versão do FlatCAM, hardware e medições do legado.
3. `COMPATIBILITY.md`: funções essenciais e critérios de paridade.
4. `BENCHMARKS.md`: corpus, metodologia e métricas.
5. Projeto multi-módulo mínimo que abra a janela JavaFX.
6. Sistema de jobs com progresso e cancelamento demonstrável.
7. Spike comparativo do renderer com uma placa grande.
8. Primeira fatia vertical de Gerber com teste diferencial.

## 15. Critério de sucesso do projeto

O projeto será bem-sucedido quando o FlatCAM FX puder executar o fluxo CAM essencial com resultados corretos e verificáveis, oferecendo uma experiência comparável ao UGS-FX em fluidez para operações equivalentes, sem bloquear a interface durante trabalhos pesados e sem depender do backend Python para as funções migradas.

Em uma frase:

> **Construir um FlatCAM desktop moderno, visualmente fluido como o UGS-FX, com JavaFX na interface, Java no núcleo principal, GPU no viewport e C++ apenas onde medições provarem necessidade.**

---

## Metadados do documento

- Status: contexto inicial aprovado conceitualmente.
- Idioma principal: português brasileiro.
- Natureza: documento vivo; decisões futuras devem atualizá-lo.
- Referência externa principal: UGS-FX como inspiração de UX, responsividade e modularidade.
