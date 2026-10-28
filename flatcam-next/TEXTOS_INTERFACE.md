# Revisão dos textos da interface — 2026-10-27

Revisão do português já presente no FlatCAM FX, com correções de acentuação,
grafia e alguns rótulos genéricos em inglês. Esta rodada prepara os textos
atuais; a seleção de idiomas e os catálogos de tradução ficam para outra etapa.

## Alterações

- Menus, árvore, propriedades, ferramentas CAM e editores: correções como
  Opções, Preferências, Padrão, seleção, posição, diâmetro, parâmetros e prévia.
- Mensagens de progresso, validação e ajuda: acentos e concordância, incluindo
  textos dos editores e do Terminal.
- Rótulos genéricos: Nome, Tipo, Marcar todos, Sólido, Multicor, Transformações,
  Gerar Geometry, Gerar CNC Job, Importar base, Exportar base e Salvar base.
- Tools Database: títulos das seções e descrição da transferência CAM revisados.
  O aviso antigo enumerava apenas parte dos fluxos integrados; agora remete à
  ajuda de cada campo, sem prometer transferência de todos os parâmetros.
- Catálogos de tooltips, ícones e ajuda ajustados para reconhecer os novos
  rótulos. As abreviações Cód./Tam. na tabela Gerber evitam alargar a lateral.

Nomes técnicos como Gerber, Excellon, Geometry, CNC Job, Plot Area e perfis
Python foram preservados. Ainda há nomes de métodos e campos técnicos em inglês;
não se trata de uma tradução integral de todos os termos do produto.

## Codificação e compatibilidade

Os fontes foram lidos e gravados em UTF-8, sem BOM. A inspeção não encontrou
os padrões de corrupção de acentos procurados. A saída do PowerShell pode
mostrar caracteres diferentes quando lê UTF-8 como ANSI; isso, isoladamente,
não demonstra corrupção dos fontes.

As alterações de produção abrangem textos e seus vínculos de ajuda/ícones.
As chaves persistidas, comandos Tcl, nomes de perfis e cálculos CAM não foram
traduzidos. Os testes existentes que comparam mensagens foram atualizados;
a verificação de fases da geração Geometry também passou a exigir que ambas
as fases existam antes de conferir sua ordem.

## Validação

Suíte completa (`mvnw.cmd verify`): 2.115 aprovações, 18 skips opcionais,
zero falhas/erros (2.133 registros). Evidências locais em
`target/text-review-verify.log` e `target/text-review-verify.json`. A auditoria
de ajuda passou após atualizar os vínculos de Isolation e Drilling.
Snapshots da Tools Database em quatro temas foram gerados pelos testes;
as versões Branco puro e Preto puro foram inspecionadas visualmente.

Auditoria de fontes: 58 arquivos de produção revisados, sem UTF-8 inválido,
BOM ou padrões de corrupção pesquisados; `target/text-review-source-audit.json`.
Os dez arquivos de testes alterados acompanham os novos textos e a verificação
de fases. A primeira execução detectou uma expectativa antiga para preâmbulo;
a execução final completa passou após sua atualização.

Launcher nativo reconstruído e probe aprovado: `target/text-review-native-build.log`.
