# Tela de abertura — 2026-10-27

A splash do FlatCAM FX aparece antes da inicialização do JavaFX, com marca,
versão, etapa atual, modo gráfico e barra animada. Usa um dos quatro temas
salvos e o monitor lembrado, se ainda existir; caso contrário usa o principal.
A posição usa coordenadas AWT, sem misturar escalas/coordenadas JavaFX.
Sem escolha salva, usa Branco puro (CLASSIC_LIGHT), o mesmo padrão da janela.
Branco puro e Preto puro têm fundos uniformes #FFFFFF e #000000 na splash,
com logotipo/textos contrastantes; os dois temas Gelo mantêm suas paletas.
Escolhas já salvas continuam respeitadas.

## Uso e tempo de leitura

Vem ativada. Em **Editar > Preferências > Geral**, desmarque **Exibir tela de
abertura** e aplique para desativar nas próximas execuções. A opção é local,
fora do projeto. Restaurar padrão prepara a opção ativada, sem salvar sozinho.

Depois que a janela principal inicia, o launcher deixa passar dois pulsos de
layout/render e consulta o renderizador. A splash permanece por **5 segundos
a partir do primeiro desenho das informações completas**, não do começo do
carregamento. **Clique na splash para continuar antes**, quando ela indicar
que a área de trabalho está pronta. Cliques durante a preparação não fecham.
O rodapé mostra a contagem. A interface JavaFX segue respondendo; nenhum sleep
é usado para bloquear sua thread. Saída ou falha fecha a splash imediatamente.
O desenho/pulsos não são uma medição de apresentação física de frames.

- `--no-splash`: desativa somente nessa execução, sem espera de leitura.
- `-Dflatcam.splash.enabled=false` ou `true`: sobrepõe a preferência na JVM.
- Headless e probes não mostram a janela, mesmo com a propriedade ativada.

A barra continua indeterminada, sem porcentagem inventada. Os scripts de
desenvolvimento compilam **antes** de abrir a JVM: o tempo de Maven não é
coberto pela splash. O novo tempo de leitura foi solicitado depois da primeira
implementação, que fechava logo após os pulsos da cena principal.

## Modo gráfico ativo

`GraphicsRuntimeInfo.query(janelaPrincipal)` faz a leitura do Prism no
QuantumRenderer, sem bloquear JavaFX. Usa o adaptador da janela mostrada,
sem inferir pela ordem gráfica solicitada ou pela lista de placas instaladas.

- **GPU (aceleração gráfica)** e **Placa em uso: <nome>** quando Direct3D está ativo.
- **Software (compatibilidade)** e **Processamento gráfico pela CPU** no Prism SW.
- **Identificação indisponível** quando o backend/acesso não permite confirmar.

A API atual não comprova se a placa é integrada ou dedicada. O texto diz GPU
e mostra seu nome; não classifica uma Intel Arc integrada como dedicada.
O probe deste PC confirmou `Intel(R) Arc(TM) Graphics`. A consulta tem timeout
de 3 segundos; indisponibilidade também recebe o tempo de leitura.

## Acentuação

Fontes Unicode com fallback para a família lógica SansSerif quando necessário.
Mensagens, nomes e versão são normalizados para NFC, incluindo acentos
combinantes. Controles de texto são substituídos por espaços; truncamento
respeita clusters Unicode, sem cortar acentos/pares substitutos no meio.
Metadados de build são lidos explicitamente em UTF-8, como os fontes do projeto.
Textos longos recebem reticências; o tooltip conserva o modo e o nome completos.
Não se tenta adivinhar ou reparar bytes já decodificados com charset errado.

## Implementação

`StartupSplash` usa JWindow/Swing no EDT, independente de JavaFX, para continuar
animando durante a criação da interface. Não captura foco nem fica sempre
sobre as outras janelas; é trazida à frente quando a principal abre e quando
a identificação termina. Fechamento idempotente interrompe o timer e dispõe
a janela. Um fechamento anterior à criação impede uma splash atrasada.
Não adiciona dependências Java. Java e executável nativo usam `FlatCamLauncher`.
O executável incorpora um manifesto PerMonitorV2 antes de iniciar a JVM; isso
impede Glass de mudar a escala de uma splash AWT já criada. O build Windows
usa `windres` além de g++. Regressão física do HWND está no relatório DPI.

A arte Java2D reutiliza o logotipo do app. Dimensões evitam sombreamento pelos
campos WIDTH/HEIGHT de ImageObserver; a máscara arredondada acompanha a redução
da arte. Resoluções, duas telas reais e testes 100–300% estão detalhados em
[STARTUP_SPLASH_DPI.md](STARTUP_SPLASH_DPI.md), com os limites de cada ensaio.

## Validação da leitura/modo/acentuação

`StartupSplashReadingTest`: backend ativo versus placa instalada, desconhecido,
acentos portugueses, NFC, fallback de fonte, truncamento por cluster, relógio
controlado (40 s de carga não consomem os 5 s), clique antes/depois de pronta e
passagem real do launcher em JVMs GPU e Software. Preferências do usuário não
são carregadas por esses probes. Verifica que callbacks JavaFX continuam sendo
atendidos durante a leitura; esse contador não representa FPS.

Focada inicial: **110 testes aprovados**, `target/startup-splash-reading-focused.log`.
Probes: `flatcam-fx/target/startup-splash-reading/gpu.log` e `software.log`.
Nessa rodada: GPU 5067 ms/250 callbacks FX; Software 5047 ms/252 callbacks FX.
Renders finais de GPU/Software nos quatro temas, incluindo nomes acentuados e
metadados de avaliação, em `flatcam-fx/target/startup-splash-reading/*.png`.
A matriz DPI também verifica texto do modo e nome acentuado no cartão reduzido.

Regressão integral final: **2114 aprovações, 18 skips opcionais, zero falhas/erros**
(2132 registros), `target/startup-splash-reading-verify.log` e JSON homônimo.
A repetição manual dos probes com módulo JavaFX e as mesmas exports/opens do
launcher também passou: `module-gpu.log` (5049 ms, Intel Arc, 263 callbacks FX)
e `module-software.log` (5066 ms, CPU, 265 callbacks FX). São evidências de leitura
e responsividade nesses processos, não benchmark de FPS. Renders de GPU/Software
claro/escuro foram conferidos, incluindo textos acentuados.
Build nativo sincronizado e probe aprovado: `target/startup-splash-reading-native-build.log`.

## Revisão dos temas puros

Padrão de fábrica e Restaurar padrão agora usam Branco puro. IDs persistidos
CLASSIC_LIGHT/CLASSIC_DARK são mantidos; os nomes visíveis ganham o sufixo puro.
A splash clássica usa fundos #FFFFFF/#000000, sem gradiente cinza/azulado.
Tema já salvo continua tendo precedência. Focada: **102 aprovações**, zero falhas
e skips (`target/pure-themes-focused.log`). A matriz 100–300% e as verificações
de leitura continuam aprovadas; os renders Branco puro/Preto puro foram
conferidos em `flatcam-fx/target/startup-splash-reading/classic_*-gpu.png`.
Integral dos temas puros: **2114 aprovações, 18 skips opcionais, zero falhas/erros**
(2132 registros), `target/pure-themes-verify.log` e JSON homônimo. Executável
nativo sincronizado/probe aprovado (`target/pure-themes-native-build.log`).
