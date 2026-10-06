# FlatCAM FX

Reimplementação gradual do FlatCAM Python/PyQt5 em Java 25 + JavaFX. O projeto
já oferece uma fatia funcional para carregar e exibir Gerber/Excellon, executar
operações CAM e gerar CNC Jobs; ele ainda cresce ao lado do aplicativo legado e
não o substitui por completo.

O diretório e o artefato Maven continuam chamados `flatcam-next` por
compatibilidade interna; esse nome não é mais a marca exibida pelo app.

Para estado detalhado, limitações e próximos passos, leia
[`CONTEXTO_E_PROGRESSO.md`](CONTEXTO_E_PROGRESSO.md). A arquitetura e estratégia
originais estão em [`../CONTEXTO_FLATCAM_FX.md`](../CONTEXTO_FLATCAM_FX.md), e o
inventário de paridade visual/funcional em [`UI_INVENTORY.md`](UI_INVENTORY.md).

A sequência priorizada para alcançar a paridade, com critérios de conclusão e
pendências de validação, está em [`PLANO_PARIDADE.md`](PLANO_PARIDADE.md).
Os critérios e roteiro manual dos fluxos principais estão em
[`FLUXO_PRINCIPAL.md`](FLUXO_PRINCIPAL.md).

## Requisitos

- JDK 25 (LTS). Testado com Java 25.0.4 (Oracle) e Temurin 25.0.2; `JAVA_HOME` deve apontar para um JDK 25.
- Nenhuma instalação global de Maven: use `mvnw`/`mvnw.cmd`.
- Opcional, no Windows: o `g++` do MSYS2 UCRT64 (`pacman -S mingw-w64-ucrt-x86_64-gcc`), para o launcher nativo
  que pede a GPU de alto desempenho (ver [`NATIVE_GPU.md`](NATIVE_GPU.md)). Sem ele `run.cmd` usa o `javafx:run`.
- Depois de mudar o `JAVA_HOME`, reabra o terminal e o VS Code: eles herdam as variáveis na abertura.

## Testar

No Windows PowerShell, a partir desta pasta:

```powershell
.\mvnw.cmd -q clean test
```

Em Linux/macOS:

```bash
./mvnw -q clean test
```

Para comparar caminhos CAM com as rotinas reais do Python e gerar sobreposições
HTML/SVG locais, consulte [COMPARACAO_CAM.md](COMPARACAO_CAM.md). O corpus privado
e os relatórios ficam fora do Git; a comparação não substitui teste a seco.

Detalhes da infraestrutura, limpeza de temporários Windows e ressalvas de
validação: [TESTES.md](TESTES.md).

## Executar

Windows PowerShell:

```powershell
.\run.cmd
```

Linux/macOS:

```bash
./mvnw -q install -DskipTests
./mvnw -q -pl flatcam-fx org.openjfx:javafx-maven-plugin:0.0.8:run
```

No Windows, `run.cmd` instala os módulos atuais antes de abrir o aplicativo. Se encontrar o `g++`, ele compila e
usa o launcher nativo (`.\run-native.cmd --verbose-gpu` força esse caminho e mostra o adaptador D3D); com
`FLATCAM_FX_JAVA_ONLY=1` mantém o `javafx:run`.
Se preferir executar Maven diretamente, rode `install -DskipTests` antes de
`-pl flatcam-fx ...:run`, especialmente depois de alterar `flatcam-cam` ou
`flatcam-application`. Executar apenas `flatcam-fx` pode carregar snapshots
antigos do repositório Maven local e causar falhas tardias ao usar ferramentas.

O goal JavaFX foi escrito por extenso porque o prefixo curto `javafx:run` pode
não ser resolvido sem `pluginGroups` configurado no `settings.xml`.

Logs e JFR agora são coletados localmente, em uma pasta por execução. Em
**Ajuda > Diagnosticos**, abra a pasta da sessão ou solicite uma captura manual.
No Windows, a raiz padrão é `%LOCALAPPDATA%\FlatCAMFX\diagnostics`.
Use `--no-diagnostics` para desativar; heap dump é opcional. Veja limites,
privacidade, detecção de travamentos e probes em [DIAGNOSTICOS.md](DIAGNOSTICOS.md).

## Módulos

- `flatcam-test-support` — infraestrutura JUnit compartilhada, somente no
  classpath de testes; não faz parte da execução do aplicativo.
- `flatcam-application` — projeto, jobs, progresso e cancelamento, sem JavaFX.
- `flatcam-cam` — parsing Gerber/Excellon, geometria JTS, operações CAM e G-code,
  sem JavaFX.
- `flatcam-fx` — interface JavaFX, árvore do projeto, Plot Area, temas e painéis
  de ferramentas.

O fluxo implementado inclui Gerber/Excellon, Isolation, Cutout, NCC, Paint, Geometry -> CNC, plot de trajetos
(com navegação passo a passo) e salvamento de G-code, além das 24 ferramentas do menu Ferramentas do Python
(2-Sided, Align Objects, Optimal, Rules Check, Copper Thieving, Calibration, Extract Drills, Panelize, Film, SolderPaste, Subtract, QRCode, Fiducials, Punch Gerber,
Invert Gerber, Corner Markers, Etch Compensation e outras). Todas estão portadas. O Plot Area desenha geometrias com centenas de milhares de traços por um LOD de densidade assíncrono
(ver [`PLOT_PERFORMANCE.md`](PLOT_PERFORMANCE.md)). Consulte o documento de contexto para saber exatamente o que
ainda não tem paridade com o Python.

Dependências de execução além do JavaFX: JTS (geometria), `org.json` e ZXing `core` (só o QRCode Tool).
