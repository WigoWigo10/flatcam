# FlatCAM FX 0.1.0-beta.4

Atualização Beta para testes práticos controlados — 1 de novembro de 2026.

## O que mudou desde a Beta 3

- **Profundidade de corte:** Geometry → CNC agora aceita Cut Z negativo, como
  Drilling e Cutout. Corrige a recusa `cutDepth must be positive` ao informar
  valores como −0,1.
- **Duas políticas Z:** o modo padrão converte Cut Z positivo para negativo
  e Travel Z negativo para positivo, com aviso; zero é recusado. Para manter
  coordenadas positivas, negativas ou zero, ative **Preferências > CNC Job >
  CNC Job (geral) > Permitir coordenadas Z livres (Machinist)**. A escolha
  vale na próxima geração, inclusive em painéis abertos.
- **Passadas e prévia:** coordenadas assinadas chegam ao G-code e ao resumo.
  Drilling mostra Cut Z efetivo = Cut Z − Offset Z. As configurações antigas
  são migradas preservando o corte. Parâmetros por fresa e projetos preservam
  a política; a importação não ativa Machinist automaticamente.
- **Salvar no editor G-code:** Salvar (Ctrl+S) grava no arquivo original
  indicado no painel. Sem original disponível, pede o primeiro destino.
  Salvar como (Ctrl+Shift+S) grava uma cópia e mantém o original associado.
  Aplicar ao CNC Job continua atualizando o objeto e a prévia em memória.
- **Tooltips:** Cancelar edição destaca que descarta o rascunho, mas não
  desfaz arquivos já gravados. Os dois botões de salvar explicam o destino,
  os atalhos e a diferença para Aplicar, em português e inglês.
- **Histórico CAM:** Ctrl+Z/Ctrl+Y nas tabelas de NCC, Isolation e Paint,
  preservando seleção e parâmetros. Dentro de um campo, Undo continua local
  ao texto. O histórico das tabelas vale na sessão.
- **Proteção do rascunho NCC:** aviso ao fechar o aplicativo ou trocar de
  projeto com alterações não salvas; salvar/carregar protege edições
  concorrentes. Restauração após mudança de TT mantém a revisão dos dados
  importados incompatíveis antes de gerar.

## Atualizar pelo MSI

Feche o FlatCAM FX Beta e execute
**FlatCAM-FX-0.1.0-beta.4-windows-x64-setup.msi** na mesma conta do Windows
da instalação anterior. MSI **0.1.4**, mesma família de atualização.
Confira **0.1.0-beta.4** em **Ajuda > Sobre** depois de atualizar.

Antes de atualizar, mova para Documentos ou outra pasta externa os projetos,
G-code e arquivos pessoais que estiverem dentro da pasta instalada. O
desinstalador anterior pode remover arquivos extras dessa pasta. A atualização
não move esses dados por conta própria.

Java está incluído. Também são gerados EXE instalador e ZIP portátil; extraia
todo o ZIP antes de abrir `FlatCAM FX Beta.exe`. `Iniciar em Software.cmd`
oferece renderização por software. O modo ativo aparece na splash e em Sobre.
Os pacotes não têm assinatura digital nem atualizador automático.

## Roteiro de teste

1. Em uma cópia do projeto, abra Geometry → CNC e gere com Cut Z **−0,1**.
   No modo padrão, confira também a conversão de **+0,1** para **−0,1**, com
   aviso. Zero deve ser recusado nesse modo.
2. Ative Machinist, volte ao painel já aberto e confira Cut Z positivo e zero
   no resumo e no código. Repita Drilling/Cutout e revise Travel Z e Offset.
   Escolha a origem Z e os parâmetros conforme a configuração da máquina.
3. Edite um CNC Job de teste. Salvar deve atualizar o arquivo indicado;
   Salvar como deve gravar outra cópia, mantendo o original. Cancelar depois
   de salvar não pode apagar nem reverter esses arquivos. Aplicar atualiza
   o objeto do projeto, separadamente.
4. Nas tabelas CAM, adicione/edite/remova uma fresa, desfaça e refaça. Dentro
   do campo de texto, confira o histórico do próprio texto.
5. Modifique um rascunho NCC, tente fechar/trocar o projeto e confira o aviso.
   Salve e reabra o projeto nativo para conferir o rascunho e seus parâmetros.
6. Repita panelização alinhada e o fluxo Isolation/NCC/Cutout → Geometry →
   CNC e Drilling com as camadas corretas, usando uma cópia dos dados.

## Limites

- Roland RML-1 não guarda o modo Machinist. Reabrir ou editar esse código
  pode classificar a prévia conforme a superfície Z0; o resumo informa isso.
- Projetos Machinist com coordenadas antes proibidas podem ser recusados por
  versões antigas do FX. Mantenha uma cópia anterior para retornar à Beta 3.
- A prévia não simula material, fixação, macros ou todos os recursos dos
  controladores. Os ensaios automatizados não validam uma máquina CNC física.
- O ensaio da distribuição registra o que passou e o que não foi executado
  em `validation.json` e nos logs. Não representa validação em toda GPU/DPI,
  outro computador ou VM limpa.

Detalhes de desenvolvimento: `MACHINING_Z_POLICY_20261101.md`,
`GCODE_SAVE_20261101.md` e `CAM_TABLE_HISTORY_20261030.md` no repositório.
