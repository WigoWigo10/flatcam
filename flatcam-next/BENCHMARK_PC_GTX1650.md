# Perfil de referência: PC-GTX1650-82CG

## A quais resultados este perfil se aplica

Os testes de FPS/desempenho de **2026-10-26** documentados em
[PLOT_PRESENTED_FPS.md](PLOT_PRESENTED_FPS.md),
[PLOT_CNC_LINE_CLIPPING.md](PLOT_CNC_LINE_CLIPPING.md) e
[PLOT_GTX1650_REVIEW.md](PLOT_GTX1650_REVIEW.md) foram executados **neste notebook**.
Seus números não representam outros PCs, a Intel Arc do outro notebook nem uma
garantia de desempenho de qualquer GTX 1650. Use o identificador
**PC-GTX1650-82CG** para esta máquina ao comparar novas execuções.

Não confundir com baselines anteriores, como os ensaios Intel Arc de 2026-10-10.
O objetivo no outro PC é repetir **a mesma carga e protocolo**, não obter
necessariamente o mesmo FPS. GPU, CPU, composição da tela, memória, driver e
energia podem alterar o resultado.

## Especificações e origem dos dados

| Item | PC-GTX1650-82CG | Evidência / limite |
| --- | --- | --- |
| Fabricante / modelo | LENOVO / 82CG | Inventário local CIM consultado ao escrever este perfil; não foi inferido nome comercial |
| CPU | Intel Core i5-10300H, identificação nominal @ 2,50 GHz | Modelo consta nos relatórios; frequência real/boost durante os testes não foi medida |
| Núcleos / threads | 4 físicos / 8 lógicos | CIM atual; Java registrou 8 processadores disponíveis |
| RAM física visível ao OS | 23,87 GiB, aproximadamente 24.443 MiB | Relatórios históricos; não é o heap Java; módulos, canais e frequência não foram registrados |
| GPU de renderização validada | NVIDIA GeForce GTX 1650 | `report.json → graphics.adapter` do Prism; não apenas a GPU pedida pelo launcher |
| VRAM da GTX | 4.096 MiB | `nvidia-smi` consultado nesta documentação; não é medição de uso durante os ensaios |
| Driver NVIDIA | Windows 32.0.16.1062; identificação NVIDIA 610.62 | Primeira identificação nos relatórios, segunda no inventário atual |
| GPU integrada presente | Intel UHD Graphics | CIM atual; não foi a GPU Prism das rodadas nativas aprovadas |
| Driver da integrada | 27.20.100.9664 | Inventário atual; não misturar com o driver da GTX |
| Sistema / arquitetura | Windows 11, versão de API 10.0 / amd64 | Runtime da sessão; build exato/atualizações do Windows não constam da baseline |
| Java | Oracle JDK 25.0.4.1; runtime informado 25.0.4.1+1-LTS-5 | `report.json` registra a versão curta; identificação completa informada na sessão |
| JavaFX | 25.0.4 | Dependência declarada/resolvida pelo projeto; alguns JSON históricos dizem `unknown`, uma lacuna de identificação, não comprovação de outra versão |
| Renderer GPU | `com.sun.prism.d3d.D3DPipeline`, Direct3D | Adaptador consultado para a janela do Plot, Prism #0 / DISPLAY1 |
| Tela consultada atualmente | 1.920×1.080, 60 Hz, saída relatada na Intel UHD | CIM atual, não registro histórico por fase; saída pela Intel não contradiz renderização Prism na NVIDIA |
| Área do benchmark | Scene 1.280×800 pixels lógicos | Não é a resolução inteira do monitor; barra/borda da janela não fazem parte da Scene |
| Escala efetiva de saída | 1,25×1,25, equivalente a 125% | Registrada em `viewport.outputScaleX/Y`; área física correspondente à Scene: 1.600×1.000 pixels |
| Tema | ICE_DARK | Mesmo tema em todos os controles comparados |
| Lançamento medido | `NATIVE_GPU_REQUEST` | Host JNI nativo solicita GPU de alto desempenho; confirmar resultado em cada máquina |
| Coletor de FPS | PresentMon 2.6.0 x64 | Mesmo executável/hash nos ensaios abaixo |

SHA-256 do PresentMon usado:
`b2a706bc6ad475749e3b7e3409263aa1e6906d45bdcf993f6dbc0f660188f1af`.
Display tracking ativo, GPU-work tracking/input tracking desativados; JFR ativo
nas baterias de comparação. Não comparar diretamente com uma rodada que tenha
outra instrumentação ou que reporte apenas Hz da UI como se fossem FPS.

RAM livre, heap e threads variam e são amostras, não requisitos para o outro PC.
Por exemplo, a execução `221813-829` registrou ~11,87 GiB de RAM livre e heap
máximo ~5,97 GiB. Não fixar `-Xmx` diferente só em uma máquina para tentar igualar
esse valor: mudanças de configuração da JVM devem ser declaradas como variante.

**Não foram registrados historicamente:** plano de energia, alimentação/bateria,
temperaturas, clocks instantâneos, modo térmico do fabricante, canais de RAM,
modelo do armazenamento, topologia física dos monitores e carga completa de
outros aplicativos. Não é possível reconstruí-los por consulta feita depois.
Nas novas comparações, registre esses fatores quando disponíveis; não atribua
toda diferença à GPU se eles forem distintos ou desconhecidos.

## Corpus: usar o mesmo arquivo, não só um projeto parecido

Projeto autorizado: `Project_20260917_160328.FlatPrj`.
SHA-256 dos arquivos usados nas baterias de referência:
`87b9af217f2089931df7b91ae083fcc63c8001e0708ed64b0ba52ae4a4f4f6a4`.

Objeto CNC exato: `Cobre_Morto_Bottom_cnc`. Geometry para o perfil A:
`isolation_bottom`. O filtro ativa somente o objeto escolhido, incluindo seus
sublayers; não trocar nome, visibilidade, conteúdo ou selecionar o projeto todo.
Copie o projeto privadamente para o outro PC; não é necessário manter o mesmo
caminho de diretório. Não versionar nem publicar projeto, capturas, CSV ou JFR.
Se o hash mudar, trate como **outro corpus**, não como comparação direta.

## Mesma revisão nos dois PCs

As medições históricas registram worktree dirty sobre `207f83b9` ou `32688546`;
os códigos foram consolidados em `37d1d851` e `b942ca47`, respectivamente.
Não reescrever os relatórios antigos para fingir que foram executados em um
commit posterior limpo. Há limites para compará-los a uma implementação futura.

Para uma comparação nova de hardware, use **a mesma revisão de implementação e
worktree limpo em ambos os PCs**, e repita primeiro neste notebook. A revisão
`b942ca47` contém o protocolo mais recente aqui descrito; commits apenas de
documentação não mudam a carga, mas terão outra identificação Git no relatório.
Se houver mudanças de código, estabeleça uma baseline nova em ambas as máquinas.
Não descartar alterações locais para mudar de revisão.

## Preparar o outro PC

1. Use Windows x64 para repetir este host nativo. O fluxo documentado não é um
   ensaio equivalente para Linux/macOS sem adaptação do harness.
2. Tenha JDK 25/JAVA_HOME e o build Maven do repositório funcionando. Para isolar
   hardware, prefira o mesmo JDK 25.0.4.1 e JavaFX 25.0.4; divergências de runtime
   devem constar da comparação.
3. Instale/disponibilize g++ UCRT64. O script procura
   `C:\msys64\ucrt64\bin\g++.exe`, depois `g++.exe` no PATH. Não exige que os
   diretórios do repositório ou do JDK sejam iguais aos deste notebook.
4. Disponibilize o mesmo PresentMon oficial, conforme
   [instruções e origem](PLOT_PRESENTED_FPS.md#reproduzir), e Python para os
   analisadores (biblioteca padrão, sem dependências extras). O script procura
   primeiro `..\.venv\Scripts\python.exe`, depois `python` no PATH.
5. Conecte o notebook à tomada e escolha condições de energia/térmicas iguais
   nas novas rodadas dos dois PCs. Registre o plano (`powercfg /getactivescheme`,
   somente leitura), bateria/AC, modo do fabricante e qualquer ajuste manual.
   Não suponha que isso foi documentado na sessão histórica.
6. Use área lógica, escala 125%, resolução/refresh e topologia de monitor tão
   semelhantes quanto possível. Não misture monitor interno/externo ou taxa de
   atualização diferente sem anotação. Confirme a escala real no JSON.
7. Feche cargas pesadas; não compile/rode outras suítes durante o benchmark.
   Mantenha a janela visível, sem minimizar, redimensionar ou interagir. Deixe
   estabilizar a condição térmica; repita a série, preservando todas as rodadas.
8. Verifique a GPU efetivamente usada em **cada** `report.json`, não só o pedido
   de alto desempenho. Não exigir NVIDIA no outro PC, mas registrar qual GPU e
   pipeline trabalharam. Uma rodada que caiu em SW não é um controle GPU válido.

Preflight em PowerShell, dentro de `flatcam-next`:

```powershell
git rev-parse HEAD
git status --short
java -version
& "$env:JAVA_HOME\bin\java.exe" -version
.\mvnw.cmd -version
Get-Command g++.exe -ErrorAction SilentlyContinue
powercfg /getactivescheme

$projetoTeste = 'D:\caminho\Project_20260917_160328.FlatPrj'
$presentMonTeste = 'target/fps-tools/PresentMon-2.6.0-x64.exe'
if ((Get-FileHash -LiteralPath $projetoTeste -Algorithm SHA256).Hash -ne
    '87B9AF217F2089931DF7B91AE083FCC63C8001E0708ED64B0BA52AE4A4F4F6A4') {
    throw 'Projeto diferente do corpus de referencia.'
}
if ((Get-FileHash -LiteralPath $presentMonTeste -Algorithm SHA256).Hash -ne
    'B2A706BC6AD475749E3B7E3409263AA1E6906D45BDCF993F6DBC0F660188F1AF') {
    throw 'PresentMon diferente do executavel de referencia.'
}
```

## Três perfis históricos — não misturar seus parâmetros

Execute os perfis **em série**, esperando o término de cada comando. Scripts não
instalam dependências nem alteram preferências/registro ou salvam o projeto.

### A. Diagnóstico de passes/apresentação — 120 passos, três repetições

```powershell
.\benchmark-plot-suite.ps1 -Project $projetoTeste -ObjectName Cobre_Morto_Bottom_cnc `
    -PresentMon $presentMonTeste -GeometryObjectName isolation_bottom `
    -Steps 120 -Repeats 3 -IncludeSoftware -Jfr
```

Repete FULL, omissões de linhas/imagens, ambos, PixelWriter, piso sem CNC,
FULL final, SW e Geometry. Baseline deste PC:
`target/plot-suite-20261026-214007-660`. Oito capturas FPS válidas; SW executou a
carga mas não produziu CSV de display utilizável. A série ficou INCOMPLETE por
isso. Se ocorrer no outro PC, **não converter a ausência em zero FPS** nem declarar
aprovação da captura. Reter a falha e comparar os controles válidos separadamente.
Os modos OMIT não têm desenho equivalente ao FULL e não são otimizações.

### B. Comparação principal de linhas — 240 passos, três repetições, dois pares

```powershell
.\benchmark-plot-suite.ps1 -Project $projetoTeste -ObjectName Cobre_Morto_Bottom_cnc `
    -PresentMon $presentMonTeste -CncLineComparison -LinePairs 2 `
    -Steps 240 -Repeats 3 -Jfr -Snapshots
```

Repete REFERENCE→CLIPPED→REFERENCE→CLIPPED, sem OMIT. Baseline deste PC:
`target/plot-suite-20261026-220612-397`, quatro capturas completas. Esse perfil é
o ponto de partida recomendado para a comparação entre computadores. O clipping
continua experimental/desativado por padrão porque o ganho não foi consistente.
A baseline histórica B ainda não possuía contadores CPU por thread; para comparar
esse campo, refaça B em ambos os PCs com a implementação atual ou use C.

### C. Integração CPU/Canvas — 120 passos, três repetições, um par

```powershell
.\benchmark-plot-suite.ps1 -Project $projetoTeste -ObjectName Cobre_Morto_Bottom_cnc `
    -PresentMon $presentMonTeste -CncLineComparison -LinePairs 1 `
    -Steps 120 -Repeats 3 -Jfr -Snapshots
```

Baseline deste PC: `target/plot-suite-20261026-221813-538`, duas capturas completas,
CPU por thread e Canvas snapshots. Os PNG de Canvas longe/perto foram idênticos
entre os dois modos neste PC. Não exigir hashes de imagem iguais entre GPUs ou
drivers diferentes sem investigar diferenças de rasterização; não mascarar
divergências de geometria. Comparar equivalência visual separadamente do FPS.

## Guardar e comparar

1. Após cada série, use o caminho **real impresso pelo runner**:

   ```powershell
   ..\.venv\Scripts\python.exe tools/summarize_plot_suite.py --suite 'target/plot-suite-<data>/suite.json'
   ```

   Se não usa essa venv, invoque seu Python correspondente. O comando não
   sobrescreve summaries existentes. Uma série incompleta produz resumo dos
   controles aceitos, mas mantém status incompleto/saída de erro.

2. Guarde `suite.json`, `summary.csv/md` e **todas as pastas de execução** referidas
   pelo manifest: `report.json`, `capture.json`, CSV/logs PresentMon, análises,
   JFR e snapshots quando pedidos. `suite.json` sozinho não contém os dados de
   cada execução. Guarde também commit, estado Git e notas de energia/tela/carga.
3. Gere o summary **na máquina de origem**, antes de transferir arquivos:
   `suite.json` contém caminhos absolutos locais. Copiar a pasta não remapeia
   esses caminhos; o resumo CSV é portátil para comparação tabular.
4. Identifique o outro computador com outro rótulo, por exemplo `PC-ARC-02`.
   Não sobrescreva nem renomeie metadata de hardware do PC de referência.
5. Compare mesmo perfil, caso, fase e repetição. Separe pan, zoom, carga inicial
   e edição. Use repetições 2/3 para o comportamento aquecido, **sem apagar a
   repetição 1**, que documenta custo frio/JIT. Compare também a variação entre
   séries, não apenas a melhor amostra.
6. Compare `averageFps`, `onePercentLowFps`, frame time p95/p99/máximo e pausas
   ≥50/100 ms no JSON. `uiPulseHz` não é FPS de display. CPU ms por thread e
   duração da fase ajudam a investigar gargalo; não são tempo GPU nem devem ser
   comparados sem levar em conta a duração/trabalho de cada fase.
7. Confira sourceSha256, runtime, tema, steps/repeats, viewport/escala,
   cncLineRendering, cncProbe/rasterProbe, pixelBuffer e configurações de
   densidade. O runner confere consistência **dentro da série**, não compara
   automaticamente duas máquinas. GPUs/CPUs diferentes são o fator estudado;
   software, corpus e protocolo devem permanecer controlados.
8. JSON ausente/FAILED, perda ETW, display insuficiente ou fallback inesperado
   não viram benchmark aprovado. FPS pode ficar limitado pela tela/VSync;
   resultado próximo ao teto de refresh não mede sozinho a potência máxima GPU.

Exemplo de leitura conjunta de dois summaries já gerados, sem alterar arquivos:

```powershell
$comparacoesPc = [ordered]@{
    'PC-GTX1650-82CG' = 'D:\resultados\PC-GTX1650-82CG\perfil-B\summary.csv'
    'PC-OUTRO-02'    = 'D:\resultados\PC-OUTRO-02\perfil-B\summary.csv'
}
$linhasPc = foreach ($rotuloPc in $comparacoesPc.Keys) {
    Import-Csv -LiteralPath $comparacoesPc[$rotuloPc] |
        Where-Object { $_.phase -eq 'zoom-in-out' -and [int]$_.repeat -ge 2 } |
        Select-Object @{Name='Computador';Expression={$rotuloPc}}, run, repeat,
            averageFps, onePercentLowFps, frameTimeMsP95, worstFrameTimeMs,
            quantumRendererCpuMs, uiQueueMsP95
}
$linhasPc | Format-Table -AutoSize
```

Os históricos privados em `target` **não acompanham o Git**. Para consultar a
baseline em outro PC, transfira privadamente os artefatos; as tabelas nos
documentos preservam os resultados principais, mas não substituem os dados brutos.
