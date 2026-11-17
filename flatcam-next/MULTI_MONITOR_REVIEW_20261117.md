# Multitela: falha reproduzida e correção validada

Os 80 controles focados de splash/posicionamento passaram em 14/11. Eles não
certificam a movimentação da janela completa entre monitores. O ensaio ao vivo
de 17/11 encontrou uma limitação existente na Beta 8.

## Ensaio real

JARs distribuídos da Beta 8, fonte `0b910a9e`, com o mesmo manifesto PerMonitorV2
e runtime incluído. Fixture JNI isolado e `MultiMonitorLiveProbe.LiveApp`:
herda `MainApp.start/stop` e seu tratamento de DPI; adiciona somente telemetria
e carga de uma placa pública por APIs do aplicativo. As preferências ficam em
arquivo dentro de `target/multi-monitor-live`, sem usar as preferências do
usuário no Windows. Nenhuma classe de produção foi substituída ou corrigida.

- Tela principal: 2880×1800, escala 200%.
- Tela à esquerda: 1920×1080, escala 100%, com deslocamento vertical.
- Objeto: cobre frontal público da Audio Watermark, salvo em projeto de teste.

Ao arrastar a janela maximizada da tela de 100%, restaurá-la e continuar o
arraste para a tela de 200%, o Windows passou o HWND para DPI **192**, mas a
janela JavaFX conservou **outputScale/renderScale 1**. O tamanho informado
chegou a **3826×1588**, com interface deformada/cortada. A telemetria Win32 é
somente leitura e consulta exclusivamente janelas do processo de teste.

Posteriormente, a maximização na tela principal acionou a recriação já existente:
escala **2**, nova Stage, mesma Scene e objeto preservados, projeto sem alterações
pendentes. Isso confirma a recuperação desse caminho, não aprovação de toda a
matriz. Na Beta 8 distribuída, mover uma janela não maximizada não era coberto
pelo tratamento de DPI. A correção abaixo está no código de desenvolvimento.

## Correção no código — 17/11

`WindowsDpi` consulta a janela deste processo via Win32/FFM do Java 25. Resolve
o monitor pelo identificador nativo do Glass, evitando misturar origens AWT/FX
ou usar o monitor do mouse. Não instala bibliotecas nem altera telas do Windows.
Após estabilizar o arraste, compara a escala da janela com a tela de destino.
Se estiver incorreta, recria somente a Stage, reaproveitando Scene, controles,
projeto e foco. Conserva o estado restaurado/maximizado e limita a geometria
à área útil da tela. Mantém o caminho anterior como fallback quando a ponte
nativa não está disponível. Os launchers autorizam o acesso nativo da aplicação.

O reparo espera a janela estar em foco, fora de arraste, sem botão do mouse
pressionado e sem diálogo/popup próprio aberto. Assim não fecha diálogos nem
descarta seus rascunhos. A verificação ocorre a cada 400 ms e exige três
amostras estáveis; há também a tolerância inicial de 1 segundo após abrir.

Ensaio com classes atuais, mesmo runtime/manifesto/dependências da Beta 8 e
preferências isoladas em `target/multi-monitor-fixed-20261117`:

- Recuperação real 100% → 200%: Stage restaurada em escala 2, HWND DPI 192.
- Recuperação real 200% → 100%: Stage restaurada em escala 1, HWND DPI 96.
- Mesma Scene nas recriações; `Placa DPI` preservada e projeto sem alterações.
- Ajuste manual da lateral na tela principal persistiu. Fechamento normal e
  reabertura restauraram `Display0`, escala 2 e proporção da lateral ~0,3045.
- Nove testes novos: conversão de coordenadas (incluindo origem negativa e
  escala fracionária), consulta nativa das duas telas, recriação restaurada/
  maximizada, recuperação automática após escala obsoleta e diálogo com rascunho.
- `mvnw.cmd verify`: 2.681 casos, 2.661 aprovados, 20 opcionais ignorados,
  zero falhas/erros. `build-native.cmd` e seu probe passaram.

Logs: `target/multi-dpi-full-verify.log`, `target/multi-dpi-recovery-tests.log`,
`target/multi-dpi-native-build.log`. O executável de desenvolvimento foi
atualizado; não houve bump, novo MSI nem alteração dos pacotes publicados.

## Pendências e limites do ensaio

Ainda faltam a matriz extensa de movimentos/maximização repetidos, duas larguras
manuais distintas com ida/volta, desconectar/reconectar fisicamente, a matriz
completa de menus/diálogos e repetição em Software. O teste automatizado de
diálogo prova a preservação do rascunho e o adiamento do reparo, não seu aspecto
visual em todas as telas.

O instrumento teve uma primeira falha de classpath JNI, corrigida usando a
resolução de JARs de produção. O conector de UI também limitou interação com
diálogos nativos e arrastes fora dos limites da janela. Na captura final houve
divergência entre a superfície retornada e a janela selecionada: os inputs foram
interrompidos e somente o processo isolado, conferido pelo caminho, foi encerrado.
Esses eventos não foram contados como defeitos do aplicativo nem como aprovações.
Na rodada corrigida também houve avisos de entrada concorrente, sobreposição do
Snap do Windows e uma captura de superfície divergente após reabrir. Nenhum
input foi enviado à superfície divergente; apenas o fixture com caminho
conferido foi encerrado. A reabertura foi confirmada por telemetria da aplicação,
não por essa captura. Não se infere aprovação visual da matriz completa.

Dados: `validation/multi-monitor-live-20261117.json`. Logs, projeto isolado e
captura da falha: `target/multi-monitor-live/`. Estado atual: **correção no código
validada nas duas telas, com matriz ampliada pendente**. A Beta 8 já distribuída
continua sendo o artefato anterior à correção.
