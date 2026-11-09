# FlatCAM FX Beta 7

9 de novembro de 2026. Versão para testes práticos em Windows x64, com Java
incluído. Corrige os cinco problemas reproduzidos na vistoria da Beta 6 e
mantém os recursos anteriores, incluindo ângulo Lines por fresa, recortes
internos e Português, English, Español e Français.

## Edição com uma sessão ativa

O menu **Editar** do Gerber agora segue a mesma proteção das outras entradas.
Se um editor Geometry, Gerber, Excellon ou G-code estiver ativo, o FX mantém
seu painel acessível e pede concluir ou descartar a edição primeiro. A sessão
e o histórico continuam disponíveis; cancelar não deve deixar outro editor
ativo sem seus comandos de saída.

## Paint: seleção, origem e cancelamento

- Polígonos selecionados deixam de ser usados quando a origem muda ou é removida.
  A mensagem orienta escolher o objeto e selecionar novamente.
- Uma origem ou referência alterada durante o cálculo faz o FX descartar a
  saída antiga, preservando o trabalho válido do projeto.
- Cancelar também é respeitado no intervalo entre o fim do cálculo e a
  publicação na interface. Isso não desfaz uma Geometry já publicada.
- Polígonos dentro de coleções com um único componente intermediário passam a
  ser reconhecidos pelo contador e pelos modos de seleção do Paint.
- Fechar ou substituir o Paint libera seus eventos de origem. Remover um objeto
  antigo não cancela uma seleção iniciada na ferramenta seguinte.

## Paint: unidade explícita do painel

Ao abrir o Paint, a unidade é definida pelo objeto inicial e exibida no painel.
Os seletores de origem e referência listam **apenas objetos nessa unidade**.
Diâmetros e margens digitados mantêm seus valores; trocar a origem não pode
reinterpretar 0,3 mm como 0,3 polegada (7,62 mm).

Para trabalhar em outra unidade, feche o Paint, selecione o objeto desejado na
árvore e abra o Paint novamente. Os tooltips explicam esse comportamento nos
quatro idiomas. A filtragem preserva rascunhos numéricos e não faz conversão
automática de parâmetros incompletos ou inválidos.

## Instalar e atualizar

- **MSI:** `FlatCAM-FX-0.1.0-beta.7-windows-x64-setup.msi`.
- **Instalador EXE:** `FlatCAM-FX-0.1.0-beta.7-windows-x64-setup.exe`.
- **Portátil:** extraia completamente `FlatCAM-FX-0.1.0-beta.7-windows-x64.zip`
  e abra `FlatCAM FX Beta.exe` na pasta extraída.

Feche o aplicativo e execute o instalador na mesma conta da instalação anterior.
O MSI **0.1.70** mantém a família de atualização da Beta 6 (0.1.60). Os pacotes
anteriores são preservados. Mantenha projetos e dados pessoais **fora da pasta
do aplicativo**: os desinstaladores anteriores podem remover arquivos extras
dela. O relatório de validação informa quais ensaios realmente foram executados.

## O que testar e relatar

Teste em cópias: abrir outro editor pelo menu da árvore enquanto há uma edição
ativa; concluir/cancelar essa edição; Paint individual/por referência; excluir
a origem ou referência; cancelar e tentar novamente; trabalhar com MM e IN;
abrir projetos e conferir parâmetros por fresa, tooltips e idiomas. Informe
versão, modo gráfico, passos, parâmetros e arquivo de reprodução.

## Limitações que continuam

Os trajetos repetidos e as regiões alcançáveis sem limpeza encontrados na
auditoria Lines **não foram corrigidos nesta entrega**. Os novos controles de
afastamento com pads arredondados não provam cobertura integral, eficiência
ótima, paridade completa com Python ou segurança física.

Esta Beta não certifica usinagem física. Confira fixação e G-code antes de usar.
Não há assinatura digital ou atualização automática. Outro computador, todos
os DPIs e cliques do assistente EXE ficam fora do ensaio automatizado local.
