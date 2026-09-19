# FlatCAM Next (skeleton)

Fase 1 do plano em `../CONTEXTO_FLATCAM_FX.md`: esqueleto JavaFX + Java,
crescendo ao lado do app Python/PyQt5 existente (não o substitui ainda).

## Requisitos

- JDK 21 (LTS). Testado com Temurin 21.0.11.
- Sem instalação de Maven necessária - use o wrapper (`mvnw`/`mvnw.cmd`).

## Rodar

```bash
./mvnw.cmd -q install -DskipTests   # primeira vez, ou apos mudar flatcam-application
./mvnw.cmd -q -pl flatcam-fx org.openjfx:javafx-maven-plugin:0.0.8:run
```

(o prefixo curto `javafx:run` não resolve por padrão sem um `settings.xml`
com `pluginGroups` configurado - por isso o goal totalmente qualificado acima.)

## Testes

```bash
./mvnw.cmd -q test
```

## Módulos

- `flatcam-application` - modelo de projeto, sistema de jobs/cancelamento
  (`JobExecutor`, `Job`, `JobHandle`). Sem dependência de JavaFX de propósito:
  a UI depende deste módulo, nunca o contrário.
- `flatcam-fx` - janela, menus, painéis, temas claro/escuro. O painel central
  (viewport) é um placeholder - o viewport GPU real é Fase 2. Não há
  parsing de Gerber/Excellon ainda - isso é a Fase 3 (fatia vertical
  "abrir -> interpretar -> exibir -> selecionar -> inspecionar").

Os demais módulos descritos na seção 5 do documento de contexto
(`flatcam-cam`, `flatcam-renderer`, `flatcam-scheduler`, `flatcam-cli`,
`flatcam-compat`, `flatcam-native`, `flatcam-tests`, `benchmarks`) ainda não
existem - propositalmente, para não antecipar abstração antes de precisar
dela (regra 12, seção 13 do documento de contexto).
