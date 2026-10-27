# Splash: resoluções, DPI e monitores — 2026-10-27

## Correção: redução/deslocamento no launcher JNI

O relato posterior mostrou a splash pequena no canto ao iniciar JavaFX. A
primeira rodada abaixo verificava JWindow hospedada em java.exe, não a transição
do executável JNI próprio. Os testes de coordenadas AWT podiam passar enquanto
o HWND mudava fisicamente. Esse limite foi reproduzido e agora tem regressão.

Ambiente: Windows 11 build 26200, JDK/JavaFX 25.0.4, Intel Arc, telas 200%/100%.
HEAD `b0a419f6` com implementação da splash ainda não commitada; não há um
commit anterior da splash para bisectar. O launcher existente não tinha
manifesto DPI; o novo caminho AWT-antes-de-Glass tornou essa ausência observável.

| Sem correção, na tela 200% | Posição física | Tamanho físico | DPI do HWND |
| --- | --- | --- | --- |
| Antes de Glass | (820, 552) | 1240 × 600 | 96 (virtualizado) |
| Ao iniciar Glass | (410, 276) | 620 × 300 | 192 |

Ao mesmo tempo os getters AWT continuavam relatando (410,276), 620×300. A
assertiva Java passava, mas a medição física Win32 falhava — exatamente o
sintoma do print. Com o manifesto, a tela 200% já inicia em DPI 192 e conserva
(820,552), 1240×600 durante toda a transição. A tela 100% conserva
(-1270,888), 620×300. GPU e Software passaram nas duas telas.

**Correção:** `native-launcher/FlatCAMFX.manifest` incorporado como RT_MANIFEST
pelo `FlatCAMFX.rc`/`windres` no build. Define PerMonitorV2 antes de carregar
JVM/AWT/Glass, com fallback PerMonitor. Não tenta reposicionar a splash em loop.
A declaração antecipada por manifesto segue a
[orientação da Microsoft](https://learn.microsoft.com/en-us/windows/win32/hidpi/setting-the-default-dpi-awareness-for-a-process).

Regressão `NativeSplashTransitionTest`: compila o código real do host JNI com
entrypoint/classpath de teste, mantendo opções e pistas de GPU. Observador C++
mede apenas janelas SunAwtWindow do próprio processo, em coordenadas físicas
constantes (contexto por thread), sem controlar a área de trabalho de terceiros.
Um sinal confirma que o observador mediu a janela antes de iniciar Glass.
A versão minimizada usa Canvas offscreen; não abre Stage de teste nem preferências.

Comando mínimo, em `flatcam-next`:

```powershell
.\mvnw.cmd -pl flatcam-fx -am "-Dtest=NativeSplashTransitionTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Evidências em `target/`:

- `native-splash-transition-red.log`: regressão falhou antes da correção.
- `native-splash-transition/before-fix-gpu-screen-0.log`: valores físicos acima.
- `native-splash-transition-green.log`: manifesto corrigiu quatro combinações.
- `native-splash-transition-revert.log`: omitir o manifesto fez falhar de novo.
- `native-splash-transition/green-*-screen-*.log`: quatro logs nativos estáveis.
- `native-splash-transition/repeat-0.log` até `repeat-9.log`: dez inicializações
  consecutivas estáveis na tela que reproduzia o defeito.
- `native-splash-dpi-build.log`: executável principal reconstruído/probe aprovado.

O opt-out de teste `-Dflatcam.test.omitDpiManifest=true` recompila somente o
fixture sem recurso e deve produzir a falha; não altera o executável principal.
O fixture final resolve jars pelo classpath Maven e executa em uma raiz isolada
sem dependências copiadas. A falha voltou sem o recurso também nesse arranjo
(`native-splash-clean-revert.log`) e desapareceu ao restaurá-lo
(`native-splash-clean-green.log`), com quatro combinações aprovadas.
O teste é opcional fora do Windows/MSYS2 UCRT64. Não altera DPI/resolução do
Windows, não carrega projetos e não modifica preferências do usuário.


## Verificação nativa neste PC

Sem alterar resolução, escala do Windows ou preferências do usuário. Os probes
abrem a própria JWindow da splash e consultam a geometria depois que a criação
e a exibição nativas retornam; fecham cada janela e encerram a JVM isolada.
Não são apenas contas sobre retângulos simulados.

| Tela conectada | Resolução física | Escala real | Área útil AWT (unidades lógicas) | Splash lógica | Splash física calculada |
| --- | --- | --- | --- | --- | --- |
| Principal | 2880 × 1800 | 200% | (0, 0), 1440 × 852 | (410, 276), 620 × 300 | 1240 × 600 |
| À esquerda, deslocada para baixo | 1920 × 1080 | 100% | (-1920, 522), 1920 × 1032 | (-1270, 888), 620 × 300 | 620 × 300 |

A criação da janela foi conferida nas duas telas em claro e escuro: dispositivo,
transformação DPI, limites nativos, posição na tela, centralização, área útil,
recorte arredondado e ausência de captura de foco/always-on-top. ID de monitor
inexistente caiu no principal. Logs: `flatcam-fx/target/startup-splash-displays/native-actual.log`.

Outras sete JVMs forçaram apenas a escala AWT do processo: **100%, 125%, 150%,
175%, 200%, 250% e 300%**. Não mudam o Windows nem representam sete configurações
físicas novas. Foram **40 verificações de janela**: duas telas × dois temas +
um fallback, em oito processos. O processo sem override cobre DPI misto real
(200%/100%); os demais usam a escala forçada nas duas telas. Logs `native-*.log`.

## Matriz simulada

- 800×600, 1024×768, 1280×720, 1366×768, 1920×1080, 2560×1440,
  2880×1800, 3840×2160, 1080×1920 (retrato) e 7680×4320.
- Sete escalas de 100% a 300%, incluindo intermediárias.
- Quatro origens de desktop, inclusive negativas e acima da principal.
- Barra de tarefas embaixo, em cima, à esquerda e à direita.

São **70 pares resolução/escala**, cada um com 16 disposições: **1120 verificações
de posicionamento/recorte**. Não contam como resoluções físicas ensaiadas.
A janela deve caber, manter proporções/margem, ficar centralizada e não aplicar
a escala duas vezes. Quando a área útil é pequena, reduz a splash inteira.

Renderização offscreen nos quatro temas, sete escalas e duas dimensões de cartão:
**56 imagens PNG**, em `flatcam-fx/target/startup-splash-displays/`. Incluem a
máscara arredondada usada pela janela. Título e fase devem ser desenhados dentro
da arte; o teste da fase compara pixels com/sem mensagem, considerando a
suavização dos glifos em tamanhos pequenos. Imagens de 125% e de cartão reduzido
a 300% foram conferidas. Isso não é uma avaliação humana de legibilidade de
todos os pixels/monitores nem uma medição de frames apresentados.

## Correção encontrada

O raio da máscara da janela era sempre 18 unidades, enquanto a borda da arte
reduzia junto com o cartão. Isso cortava trechos da borda em áreas úteis pequenas.
O teste de regressão falhou em **8 dos 70 pares** antes da correção:
`target/startup-splash-rounding-red.log`. Agora a máscara acompanha as duas
dimensões da arte; todos os pares passam. O comportamento normal a 620×300
não muda.

## Reproduzir

Na pasta `flatcam-next`:

```powershell
.\mvnw.cmd -pl flatcam-fx -am "-Dtest=StartupSplashTest,StartupSplashDisplayTest,WindowScreenMatcherTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Rodada focada: **80 testes aprovados**, zero falhas/erros/skips,
`target/startup-splash-displays.log`. O probe nativo adapta-se aos monitores
conectados no momento; resultados podem mudar se a topologia mudar.

Limites: não altera a resolução física, não simula remoção de cabo no meio da
abertura e não valida o restante da interface JavaFX ao arrastar janelas entre
monitores. A splash transitória não foi capturada isoladamente pela automação
de desktop; evidência nativa vem dos probes, junto da revisão dos renders.

A separação de coordenadas/dispositivos segue
[GraphicsConfiguration do JDK](https://docs.oracle.com/en/java/javase/25/docs/api/java.desktop/java/awt/GraphicsConfiguration.html).

Regressão integral posterior: **2109 aprovações, 18 skips opcionais, zero falhas
e erros** (2127 registros), `target/startup-splash-dpi-verify.log` e JSON homônimo.
Build nativo sincronizado e probe aprovado: `target/startup-splash-dpi-native-build.log`.

Regressão integral da correção: **2115 aprovações, 18 skips opcionais, zero falhas
e erros** (2133 registros), `target/native-splash-dpi-verify.log` e JSON homônimo.
A rodada focada posterior valida a independência do fixture de builds anteriores;
a correção de produção e o executável principal são os mesmos já aprovados.
