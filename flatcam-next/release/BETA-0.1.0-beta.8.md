# FlatCAM FX Beta 8

14 de novembro de 2026. Versão para testes práticos em Windows x64, com Java
incluído. Reúne as correções e os recursos desenvolvidos após a Beta 7.

## Limpeza de cobre e Rest

- Lines recebeu refinamentos dos passes e diagnóstico das regiões não limpas.
  O ângulo de varredura por fresa continua disponível em NCC/Paint.
- Rest pode usar espaço já limpo para posicionar a fresa menor e alcançar
  cantos antes omitidos, sem reduzir o diâmetro nominal.
- Standard acrescenta passes locais onde os offsets sucessivos deixavam
  material alcançável, incluindo cantos e lóbulos. Isso corrige também um
  comportamento reproduzido no Python; alguns programas ficam mais longos
  porque passam a cortar material anteriormente omitido.
- O Connect reutiliza índices geométricos para acelerar as verificações de
  ligação. A fresa inteira deve caber na área autorizada.

## Novas estratégias experimentais

**Lines por regiões:** organiza a varredura por bolsões e integra o acabamento
ao Connect. Pode reduzir subidas, embora o comprimento de corte ou os rápidos
possam aumentar. Continua disponível o Lines comum.

**Espiral adaptativa:** acompanha as bordas e conclui ramos de um bolsão antes
de visitar os seguintes. Áreas quase circulares mantêm o planejamento radial.
São voltas ligadas pelo Connect; não há promessa de curva suave ou carga de
corte constante. Offsets densos receberam otimização para evitar crescimento
excessivo de vértices. Se o orçamento for excedido, a fresa inteira é refeita
pela radial, com aviso no painel. O limite de um milhão de amostras permanece.

Na Audio Watermark, o cálculo da espiral caiu de 4,79 para 2,59 segundos na
medição local pareada. Ganhos dependem da placa e do computador. Standard teve
a menor estimativa de usinagem na comparação dessa placa, mas gerou um arquivo
maior. Não existe método vencedor para todos os casos.

## Prévia NCC/Paint

Prévia em segundo plano, cancelável, sem criar objetos de projeto. Cortes,
deslocamentos e entradas têm cores/legenda próprias e estatísticas por fresa.
Alterar parâmetros ou trocar/fechar a ferramenta invalida a prévia antiga.
A estimativa da prévia é simplificada; a do CNC Job usa o programa gerado.
Nenhuma delas inclui toda a dinâmica real da máquina.

## Importação Gerber

Filtros unificados incluem GKO e outras extensões legadas/camadas internas,
maiúsculas e a opção Todos os arquivos. Arquivos editados conservam a extensão
da camada. Foram corrigidas leituras legadas específicas de comandos isolados,
BOM UTF-8 e equivalência decimal na supressão de zeros. Regiões malformadas e
transformações de imagem não suportadas são recusadas explicitamente, evitando
uma importação silenciosamente incorreta. Não é suporte irrestrito a todo
formato Gerber. Os originais não são regravados pela importação.

## Instalar e atualizar

- MSI: `FlatCAM-FX-0.1.0-beta.8-windows-x64-setup.msi`.
- Instalador EXE: `FlatCAM-FX-0.1.0-beta.8-windows-x64-setup.exe`.
- Portátil: extraia completamente `FlatCAM-FX-0.1.0-beta.8-windows-x64.zip`
  e abra `FlatCAM FX Beta.exe` na pasta extraída.

Feche o aplicativo e execute o instalador na mesma conta da instalação anterior.
O MSI **0.1.80** mantém a família de atualização da Beta 7 (**0.1.70**).
Mantenha projetos e arquivos pessoais **fora da pasta do aplicativo**:
desinstaladores anteriores podem remover arquivos extras dela. Os pacotes
anteriores permanecem disponíveis; o portátil anterior pode ser usado em outra
pasta, com cópias dos projetos. Não há promessa de downgrade automático.

## O que testar e relatar

Em cópias dos projetos, compare Lines/Standard/Lines por regiões/Espiral, com e
sem Connect, Contour e Rest. Confira cada fresa, MM/IN, rascunhos e cancelamento,
prévia e CNC Job exportado. Importe os Gerbers que apresentaram problemas.
Relate versão, modo gráfico, passos, arquivo e parâmetros; guarde os logs.

## Limitações

Ainda podem existir recaminhamentos para reduzir subidas, programas grandes e
regiões inalcançáveis pela fresa escolhida. Há limites de orçamento e precisão.
Os ensaios comunitários usam a caixa do cobre como limite diagnóstico, não o
Outline físico. Núcleo residual zero na análise não significa sobra bruta zero.
As mudanças não certificam usinagem física nem paridade integral com Python.
Não há assinatura digital ou atualização automática. Os resultados de build,
instalação e upgrade realmente executados constam no relatório de validação;
outro PC/VM, todos os DPIs e CNC física permanecem fora do ensaio local.
