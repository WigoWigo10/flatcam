# FlatCAM FX Beta 5

5 de novembro de 2026. Versão para testes práticos em Windows x64, com Java
incluído. Acrescenta recortes internos de PCB e amplia os idiomas da interface.

## Recortes internos sem Paint

1. Converta o Outline/Edge_Cuts na área preenchida do contorno físico da placa.
2. Abra **Cutout Tool > Recortes internos…**.
3. Selecione aberturas na tabela ou no Plot, ou desenhe retângulos, círculos e
   polígonos. Use **Enquadrar seleção** para conferir os detalhes.
4. Defina diâmetro, margem, Cut Z e passadas. A prévia distingue abertura
   desejada, centro da fresa e faixa nominal removida. O resumo mostra as
   configurações e o comprimento por passada.
5. Use **Gerar recortes internos**. Abra a Geometry `_internal_cutout` gerada e
   siga para CNC Job, revisão e exportação.

A origem e o perímetro externo permanecem intactos. A fresa usa a precisão
digitada. Prévia e compensação rodam em segundo plano; o botão Gerar aguarda
prévia e parâmetros válidos. Erros destacam a abertura na tabela e no Plot.
Delete remove apenas desenhos do rascunho. O painel permanece aberto após gerar.

Os contornos internos são fechados e **sem pontes**: confira a fixação do miolo.
Este modo não esvazia um bolso nem simula profundidade, perfil 3D ou colisões.
A margem deve ser não negativa. Desenhos/seleção temporários duram enquanto o
painel está aberto; as Geometries geradas são salvas no projeto. A opção anterior
de incluir recortes internos junto ao perímetro externo continua disponível.

Tooltips e guia offline explicam os gestos, margem, compensação e próximos passos.
O contraste da prévia e da legenda acompanha os quatro temas.

## Idiomas

Português (Brasil), English, Español e Français em **Editar > Preferências >
Geral**. Use **Aplicar e salvar**; o idioma entra em vigor na próxima abertura.
O seletor exibe ícones SVG locais do Circle Flags, com atribuição em Ajuda > Sobre.
O idioma não altera unidades, números de G-code ou arquivos do usuário.

## Instalar e atualizar

- **MSI:** `FlatCAM-FX-0.1.0-beta.5-windows-x64-setup.msi`.
- **Instalador EXE:** `FlatCAM-FX-0.1.0-beta.5-windows-x64-setup.exe`.
- **Portátil:** extraia completamente `FlatCAM-FX-0.1.0-beta.5-windows-x64.zip` e
  abra `FlatCAM FX Beta.exe` dentro da pasta extraída.

Feche o aplicativo e execute o instalador na mesma conta da instalação anterior.
A versão MSI **0.1.50** mantém a família de atualização das Betas anteriores,
incluindo a Beta 4 revisão 1 (0.1.41). Os pacotes antigos são preservados.
Mantenha projetos e dados pessoais **fora da pasta instalada**, pois os
desinstaladores anteriores podem remover arquivos extras dessa pasta.

Se houver um problema impeditivo, preserve uma cópia dos projetos e use o ZIP
portátil da Beta anterior em outra pasta. Não se declara downgrade automático
de preferências nem de projetos contendo recursos novos.

## O que testar e relatar

Teste primeiro em cópias: abrir/salvar projetos, recortes internos detectados e
desenhados, troca de fresa/margem, cancelamento, temas e idiomas. Informe a versão,
o modo gráfico, os passos e o resultado esperado. O guia é acessível sem internet.

Os relatórios da geração e da distribuição identificam commit, testes, hashes,
instalação e upgrades realmente exercitados. Esta Beta não certifica usinagem
física. A prévia não substitui revisão do G-code e da fixação. Não há assinatura
digital ou atualização automática. Todos os controladores, monitores/DPIs, outros
computadores e cliques do assistente EXE não fazem parte do ensaio automatizado.
