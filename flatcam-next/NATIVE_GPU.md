# GPU de alto desempenho no Windows

`run.cmd` agora prefere o executável nativo quando encontra C++17 no Windows;
sem compilador, mantém o iniciador Java anterior. Para pedir automaticamente
a GPU de alto desempenho sem alterar o `java.exe` compartilhado com outros
programas, o caminho nativo também pode ser invocado diretamente:

```powershell
.\run-native.cmd
```

Para forçar o iniciador Java anterior em uma sessão PowerShell:

```powershell
$env:FLATCAM_FX_JAVA_ONLY = '1'
.\run.cmd
```

Depois, remova a variável com `Remove-Item Env:FLATCAM_FX_JAVA_ONLY`.

O script exige JDK 21 em `JAVA_HOME` e C++17 (testado com MSYS2 UCRT64 `g++`),
compila o reactor, copia as dependências de execução e cria
`target\native\FlatCAMFX.exe`. O executável carrega a JVM **dentro do próprio
processo** e inicia o JavaFX. Ele exporta `NvOptimusEnablement=1` e
`AmdPowerXpressRequestHighPerformance=1` como pistas para os drivers híbridos.
O valor da NVIDIA segue a [nota técnica oficial do Optimus](https://developer.download.nvidia.com/devzone/devcenter/gamegraphics/files/OptimusRenderingPolicies.pdf).
A pista AMD ainda não foi validada em um equipamento AMD híbrido.
Na falta de uma GPU dedicada compatível, o JavaFX tenta a integrada via D3D;
se o pipeline acelerado não inicializar, tenta o software. Esta sequência é
`-Dprism.order=d3d,sw`, igual ao [padrão do OpenJFX 21 no Windows](https://github.com/openjdk/jfx/blob/jfx21/modules/javafx.graphics/src/main/java/com/sun/prism/impl/PrismSettings.java).

Para diagnosticar o adaptador efetivamente usado:

```powershell
.\run-native.cmd --verbose-gpu
```

Procure `D3D Driver Information` no terminal e confirme o processo
`FlatCAMFX.exe` no Gerenciador de Tarefas (`GPU engine`) ou em `nvidia-smi pmon`.
Para testar o fallback SW sem depender de uma falha do driver:

```powershell
.\run-native.cmd --software --verbose-gpu
```

Para comparar fluidez com os mesmos instrumentos do Plot Area:

```powershell
cmd /c ".\profile-plot-native.cmd 2>&1" | Tee-Object -FilePath ".\plot-native-$(Get-Date -Format 'yyyyMMdd-HHmmss').log"
```

Esta preferência não é garantia de escolha: opções explícitas do Windows,
perfis do driver, modo de economia de energia e restrições do OEM podem
prevalecer. Não é possível escolher à força a GTX 1650 pela API pública do
JavaFX 21; `prism.order` escolhe **pipeline** (D3D/SW), não a GPU física.
Também não há aceleração do cálculo CAM pela GPU: parsing, JTS e preparação
das formas continuam na CPU. A comparação deve observar `D3D Driver Information`
e os valores `[UI-FLUIDITY]`/`[PLOT-PROFILE]`, não só o uso da GPU na tela.

O launcher atualmente é um fluxo de desenvolvimento para Windows. Distribuir
um aplicativo autônomo ainda requer empacotar o runtime Java e as dependências
de modo independente da árvore `target/`.
