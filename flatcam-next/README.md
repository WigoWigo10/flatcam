# FlatCAM FX

Reimplementação gradual do FlatCAM Python/PyQt5 em Java 21 + JavaFX. O projeto
já oferece uma fatia funcional para carregar e exibir Gerber/Excellon, executar
operações CAM e gerar CNC Jobs; ele ainda cresce ao lado do aplicativo legado e
não o substitui por completo.

O diretório e o artefato Maven continuam chamados `flatcam-next` por
compatibilidade interna; esse nome não é mais a marca exibida pelo app.

Para estado detalhado, limitações e próximos passos, leia
[`CONTEXTO_E_PROGRESSO.md`](CONTEXTO_E_PROGRESSO.md). A arquitetura e estratégia
originais estão em [`../CONTEXTO_FLATCAM_FX.md`](../CONTEXTO_FLATCAM_FX.md), e o
inventário de paridade visual/funcional em [`UI_INVENTORY.md`](UI_INVENTORY.md).

## Requisitos

- JDK 21 (LTS). Testado com Temurin 21.0.11.
- Nenhuma instalação global de Maven: use `mvnw`/`mvnw.cmd`.

## Testar

No Windows PowerShell, a partir desta pasta:

```powershell
.\mvnw.cmd -q clean test
```

Em Linux/macOS:

```bash
./mvnw -q clean test
```

## Executar

Windows PowerShell:

```powershell
.\mvnw.cmd -q install -DskipTests
.\mvnw.cmd -q -pl flatcam-fx org.openjfx:javafx-maven-plugin:0.0.8:run
```

Linux/macOS:

```bash
./mvnw -q install -DskipTests
./mvnw -q -pl flatcam-fx org.openjfx:javafx-maven-plugin:0.0.8:run
```

Repita o `install` depois de alterar `flatcam-cam` ou `flatcam-application`.
Executar apenas `flatcam-fx` pode carregar snapshots antigos desses módulos a
partir do repositório Maven local e causar falhas tardias ao abrir uma
ferramenta.

O goal JavaFX foi escrito por extenso porque o prefixo curto `javafx:run` pode
não ser resolvido sem `pluginGroups` configurado no `settings.xml`.

## Módulos

- `flatcam-application` — projeto, jobs, progresso e cancelamento, sem JavaFX.
- `flatcam-cam` — parsing Gerber/Excellon, geometria JTS, operações CAM e G-code,
  sem JavaFX.
- `flatcam-fx` — interface JavaFX, árvore do projeto, Plot Area, temas e painéis
  de ferramentas.

O fluxo implementado inclui Gerber/Excellon, Isolation, Cutout, NCC inicial,
Geometry -> CNC, plot de trajetos e salvamento de G-code. Consulte o documento
de contexto para saber exatamente o que ainda não tem paridade com o Python.
