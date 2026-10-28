# FlatCAM FX 0.1.0-beta.1

Primeira Beta para testes práticos controlados — 28 de outubro de 2026.

## Instalar e abrir

No Windows x64, execute **FlatCAM-FX-0.1.0-beta.1-windows-x64-setup.exe**.
Escolha a pasta de instalação no assistente. O pacote inclui Java: o testador
não precisa instalar JDK, Maven, Python ou ferramentas de desenvolvimento.
Também é fornecido um `.msi` para instalação pelo Windows Installer.

A instalação é por usuário, com atalhos no menu Iniciar e na área de trabalho.
Ela usa a identidade **FlatCAM FX Beta**, separada de uma futura edição estável.
Para remover, use Aplicativos instalados do Windows. Preferências do aplicativo
e projetos do usuário não são apagados pelo instalador.

Alternativamente, extraia **todo** o ZIP portátil e abra `FlatCAM FX Beta.exe`.
Para renderização por software, use `Iniciar em Software.cmd`, ou execute o
aplicativo com `--software`. O modo efetivamente usado aparece na splash e
em **Ajuda > Sobre > Sistema**.

Os pacotes desta rodada não têm assinatura digital. Não há atualização
automática ou associação automática de extensões de projeto nesta Beta.

## O que testar

1. Abra uma cópia de um projeto ou importe Gerber, Excellon e Edge_Cuts.
   Confira unidades, escala, pads e furos.
2. Use **Contorno → Área** e a **Panelize Tool** com uma referência comum.
   Confira cobre, contorno, recortes internos, furos e rasgos em cada cópia.
3. Gere Isolation, NCC e Cutout usando os objetos corretos do painel. Confira
   ferramentas, região de trabalho, Rest/Connect e pontes conforme o caso.
4. Gere os CNC Jobs e revise profundidades, avanços, spindle, troca de
   ferramenta e posições de início/fim. Exporte o G-code separadamente.
5. Salve e reabra o projeto, confira o resultado e teste os temas, a seleção,
   o editor e o guia **Ajuda > Guia de uso / F1**.

A validação física dos programas em CNC faz parte desta fase de testes;
não foi concluída pelos ensaios automatizados. Trabalhe com cópias dos
projetos e mantenha os arquivos originais para comparação.

## Recursos presentes

- Importação Gerber/Excellon, projetos nativos `.fcnproj` e intercâmbio `.FlatPrj`.
- Panelização conjunta com referência comum e prévia por categoria.
- Isolation, NCC, Cutout, Drilling e geração/edição/exportação de G-code.
- As ferramentas do menu e os editores Geometry, Gerber, Excellon e G-code.
- Quatro temas, Português/English, preferências numéricas e de fontes.
- Guia local com treze assuntos, incluindo Outline/Edge_Cuts.
- Diagnósticos locais, identificação do renderizador e alternativa por software.

## Limites conhecidos

- Não é equivalência integral com o FlatCAM Python. Preferências de mouse,
  comportamentos avançados, outros idiomas e parte da automação Tcl ainda faltam.
- A matriz CAM mais ampla disponível usa um projeto real em três disposições
  e casos sintéticos. Dois casos Offset do NCC continuam com erro no oráculo
  Python; eles não contam como paridade comprovada.
- G-code e pós-processadores não foram certificados em máquina. A prévia
  não simula fixação, comprimento de ferramenta ou todos os comandos de controlador.
- O intercâmbio `.FlatPrj` tem limites para objetos editados, recursos que o
  outro aplicativo não representa e algumas exportações, com mensagens próprias.
- Fontes extremas podem exigir mais largura e rolagem. A cobertura de
  monitores/DPI, drivers e projetos não é exaustiva.
- O instalador foi concebido para Windows x64. O relatório da rodada distingue
  teste na máquina de desenvolvimento, teste fora do repositório e ensaios não feitos.

## Relatar um problema

Informe **0.1.0-beta.1**, o commit indicado no diagnóstico/manifesto do pacote,
o modo GPU/Software, os passos e o resultado esperado. Inclua o objeto e os
parâmetros envolvidos; se possível, um exemplo reduzido sem dados privados.

Use **Ajuda > Diagnósticos > Abrir pasta desta sessão** para localizar os logs.
Diagnósticos são locais, sem envio automático. Revise seu conteúdo antes de
compartilhar: podem conter caminhos, nomes de arquivos e trechos do trabalho.

## Identificação e retorno

O aplicativo mostra `0.1.0-beta.1`; o Windows Installer usa a versão numérica
`0.1.1`, que permite incrementar as próximas Betas na mesma família de upgrade.
O marcador `flatcam-release.json` contém versão, commit, runtime e estado da
árvore Git. A pasta de distribuição contém hashes SHA-256 dos artefatos e
`validation.json` com o que realmente foi testado.

Se houver corrupção de projeto, deslocamento inesperado de furos/camadas,
profundidade/avanço incorretos ou falha recorrente de abertura, interrompa o
uso da operação afetada e preserve os arquivos/logs. A Beta pode ser removida
pelo Windows; uma versão anterior deve ser reaberta a partir de uma cópia
compatível do projeto, sem presumir conversão reversível de todos os recursos.

## Licenças e fontes

O projeto conserva sua licença MIT e os créditos em Ajuda > Sobre. Os JARs
incluem seus avisos originais; o runtime OpenJDK conserva a pasta `legal` com
licenças e exceções aplicáveis. Esta Beta inclui Temurin 25.0.5+8, obtido do
[release oficial do Adoptium](https://github.com/adoptium/temurin25-binaries/releases/tag/jdk-25.0.5%2B8).
As fontes do aplicativo correspondem à tag `v0.1.0-beta.1` deste repositório;
as fontes do OpenJDK são mantidas em [Adoptium jdk25u](https://github.com/adoptium/jdk25u).
