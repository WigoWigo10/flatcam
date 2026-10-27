# End Z: reprodução e contorno da busca de texto da JVM — 2026-10-27

## Causa observada

Em `4ccd21e5`, `CncNativePrecisionTest` passava sozinho, na família CNC e com
os 15 testes anteriores, mas falhava na suíte integral. O valor e o rótulo eram
corretos. Diagnóstico por code points confirmou `3.00125 → 3.0025 mm` e igualdade
do rótulo; `text.contains("3.0025")` retornava **false** para esse texto.

Um programa Java independente (`tools/JdkStringSearchProbe.java`), sem JavaFX,
JTS ou FlatCAM, reproduziu o falso negativo após aquecimento. Ambiente:
Windows x64, Oracle HotSpot **25.0.4.1+1-LTS-5**, `UseAVX=2`, `UseSSE=4`,
CompactStrings ativo. Não atribuir isto ao cálculo CNC ou à GPU.
O introduzidor upstream e outras versões de JDK não foram determinados;
não há um commit FlatCAM identificado por bisect como causa da JVM.

## Prova repetível

A partir de `flatcam-next`:

```powershell
javac -encoding UTF-8 -d target tools/JdkStringSearchProbe.java
java -Xbatch -cp target JdkStringSearchProbe
java -Xbatch -XX:+UnlockDiagnosticVMOptions -XX:DisableIntrinsic=_indexOfUL -cp target JdkStringSearchProbe
```

Sem contorno: **10/10 execuções falharam**, no programa independente.
Com contorno: **10/10 passaram** (500.000 buscas cada). Desligar todo o AVX
como contraprova também fez passar, mas essa opção ampla não foi adotada.
Logs locais `target/string-probe-red-*.log`, `target/string-probe-green-*.log`.
Não há fontes de rede, relógio, preferências, geometria privada ou aleatoriedade.

## Contorno aplicado

Desativada **somente** a intrinsic `_indexOfUL`, que corresponde à busca de
substring Latin1 em texto UTF16. O mapeamento está no
[código oficial OpenJDK](https://github.com/openjdk/jdk/blob/jdk-25%2B36/src/hotspot/share/classfile/vmIntrinsics.hpp).
A implementação Java normal segue ativa. Os demais caminhos de String,
CompactStrings, AVX e aceleração gráfica continuam habilitados. Não é uma
correção dentro da JVM nem uma afirmação de defeito de hardware.

Aplicado ao launcher nativo, launcher Maven, host nativo de benchmark e JVMs
Surefire. A propriedade Maven `flatcam.jvm.stringSearchWorkaround` pode ser
esvaziada **para diagnóstico**, permitindo reproduzir a falha. Ao atualizar o
JDK, repetir o probe sem flags antes de retirar o contorno. Outros fornecedores
ou máquinas não foram certificados nesta rodada. Comparações de benchmark
posteriores devem registrar estas opções de JVM.

## Regressão

`JvmStringSearchTest` exercita buscas positivas e negativas, Unicode e ASCII,
após aquecimento. Foi executado sem o contorno para demonstrar vermelho; a
asserção original de End Z foi preservada literalmente. Não foi substituída por
uma comparação mais permissiva nem houve mudança do arredondamento CNC.
A suíte integral com o contorno está registrada em
`target/end-z-workaround-verify.log`; conferir resultado no contexto atualizado.
O diagnóstico original está preservado em `target/end-z-full-diagnostic-before.log`.

Resultado integral: **1989 aprovações, 18 skips opcionais, zero falhas/erros**
(2007 registros). O teste original de End Z passou dentro da suíte inteira.
