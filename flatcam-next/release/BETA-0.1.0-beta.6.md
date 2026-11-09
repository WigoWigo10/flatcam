# FlatCAM FX Beta 6

9 de novembro de 2026. Versão para testes práticos em Windows x64, com Java
incluído. Mantém os recursos da Beta 5, incluindo recortes internos e quatro
idiomas, e acrescenta os ajustes abaixo.

## Ângulo da limpeza Lines

Em **NCC Tool** e **Paint Tool**, escolha **Lines** ou **Combo**, desmarque
**Direção automática** e informe um ângulo de 0° a menos de 180°. Zero gera
varredura horizontal; 90°, vertical. Os ângulos positivos giram no sentido
anti-horário. Cada fresa mantém seu próprio valor, inclusive no Rest.

O modo automático mantém a escolha anterior pela caixa do objeto. A opção de
ângulo fica desabilitada nos métodos que não usam Lines. Os tooltips explicam
esses controles. O rascunho NCC salvo no projeto nativo preserva a direção e o
texto digitado; rascunhos antigos continuam abrindo no automático. O rascunho
Paint dura enquanto o painel permanece aberto.

## Acesso ao editor durante uma edição

Ao tentar abrir outra ferramenta com um editor Geometry, Gerber, Excellon ou
G-code ativo, o FX mantém o painel de edição acessível e pede concluir ou
descartar a edição primeiro. Isso evita perder os comandos de saída, mantendo
as alterações e o histórico da sessão. Não há descarte automático.

## Resultado da auditoria Lines: limitações que continuam

A auditoria com oito placas públicas encontrou repetições de linha central em
34 de 64 combinações diagnósticas. O maior caso medido repetiu cerca de 747 mm
(3,68% do comprimento). Também há regiões alcançáveis sem trajetos em um caso
sem Contour. **Esta Beta não corrige esses problemas do algoritmo.**

Interrupções junto ao cobre podem ser necessárias para evitar cortar pads;
sobreposição entre faixas vizinhas é esperada para remover material. Repetir o
mesmo trajeto e deixar regiões alcançáveis sem limpeza exigem análise própria.
A comparação com Python também encontrou repetições no legado, sem comprovar
equivalência completa de execução. A cobertura das placas públicas não foi
medida integralmente. Consulte `LINES_RAW_PATH_AUDIT.md` no repositório.

## Instalar e atualizar

- **MSI:** `FlatCAM-FX-0.1.0-beta.6-windows-x64-setup.msi`.
- **Instalador EXE:** `FlatCAM-FX-0.1.0-beta.6-windows-x64-setup.exe`.
- **Portátil:** extraia completamente `FlatCAM-FX-0.1.0-beta.6-windows-x64.zip`
  e abra `FlatCAM FX Beta.exe` na pasta extraída.

Feche o aplicativo e instale na mesma conta da versão anterior. O MSI
**0.1.60** mantém a família de atualização da Beta 5 (0.1.50). Os pacotes antigos
continuam preservados. Mantenha projetos e dados pessoais **fora da pasta do
aplicativo**: os desinstaladores anteriores podem remover arquivos extras dela.
As verificações efetivamente executadas ficam no relatório de validação.

## O que testar e relatar

Teste em cópias: Lines automático e em ângulos explícitos em NCC/Paint, pads
redondos/arredondados, Connect/Contour, parâmetros por fresa e reabertura do
rascunho NCC. Durante uma edição, tente abrir outra ferramenta; confira que o
painel e os comandos de salvar/descartar continuam disponíveis. Informe versão,
modo gráfico, parâmetros, passos e resultado esperado, junto ao arquivo de teste.

Esta Beta não certifica usinagem física nem paridade integral. Confira a fixação
e o G-code antes de executar. A prévia não simula todas as funções do controlador.
Não há assinatura digital ou atualização automática. Outros PCs, todos os DPIs
e cliques do assistente EXE ficam fora do ensaio automatizado local.
