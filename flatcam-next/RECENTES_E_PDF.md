# Recentes e Imprimir como PDF — 2026-10-12

## Uso

- **Arquivo > Recentes > Projetos recentes / Arquivos recentes**: até dez entradas
  por lista, mais novas primeiro, persistidas entre sessões. O tipo e o caminho
  completo aparecem na ajuda. Abrir novamente promove a entrada; limpar uma
  lista não apaga arquivos e não limpa a outra lista.
- **Arquivo > Imprimir como PDF… / Ctrl+P**: selecione um ou mais objetos na
  árvore ou no Plot, escolha papel, orientação e preto/branco ou cores, depois
  escolha o arquivo de destino. A geração roda em worker com progresso e
  cancelamento; a interface não faz a escrita/renderização do PDF.

## Contratos e diferenças deliberadas

Referência local Python: `app_Main.py::register_recent`, `setup_recent_items`,
`on_file_save_objects_pdf` e `save_pdf`; `defaults.py::global_recent_limit` = 10.

Recentes cobre aberturas/importações **pela interface** de Projeto FX/Python,
Gerber, Excellon, G-code (incluindo os subconjuntos CNC suportados), SVG e DXF
como Geometry/Gerber, HPGL2 e PDF. Projetos salvos pela interface também entram.
Não registra cancelamentos/falhas, cópias/objetos derivados nem fontes internas
de um projeto carregado. Comandos Tcl não alimentam estas listas neste incremento.
SVG/DXF guardam a intenção Geometry/Gerber para evitar inferi-la pela extensão;
os parâmetros de importação e unidades continuam sendo os atuais do aplicativo,
não uma receita histórica congelada. Um arquivo ausente é reportado pela abertura
normal, sem remoção automática da lista. Projeto recente segue as mesmas
validações de carregamento da abertura comum, inclusive rascunhos do editor.
Salve alterações do projeto antes de abrir outro: não foi criada detecção nova
de projeto não salvo. Histórico em um subnó Preferences exclusivo do FX;
`recent.json` e `recent_projects.json` do Python não são lidos nem modificados.

Imprimir gera **vetores dos objetos selecionados**, mesmo ocultos; não captura
grade, eixos, HUD, prévias, seleções ou zoom do Plot. Gerber/Excellon usam a
geometria sólida; Geometry preserva desenho preenchido/contorno; CNC Job usa
a prévia de movimentos suportados, deslocamentos antes dos cortes. CNC sem
prévia suportada recusa a impressão, não é silenciosamente omitido. Rascunhos
pendentes devem ser aplicados/descartados antes. Não simula segurança de máquina.

- Escala física **1:1**, coordenadas MM/IN convertidas para mm sem alterar os objetos de origem.
  A seleção inteira recebe uma translação comum para centralizar na página;
  preserva distâncias/alinhamento entre objetos. Não é reprodução da posição
  absoluta na folha usada pelo Python nem igualdade binária de PDFs.
- Página Bounds: limites físicos + 1 mm por lado. A0–A6, Letter, Legal e
  Tabloid: margem mínima 5 mm, retrato/paisagem, sem corte ou redução automática.
  Seleção grande demais recusa com orientação para escolher outra página/Bounds.
  Limite da implementação PDF 1.4: 14400 pontos (5080 mm) em cada dimensão.
- Preto e branco é padrão para legibilidade em papel; cores opcionais usam uma
  cor por objeto/camada, opaca, não transparência nem Multi-Color por abertura.
  Vazios internos são paths even-odd, não áreas pintadas de branco sobre outras
  camadas. Traços de Geometry sem espessura usam 0,02 mm físicos.
- PDF de página única e stream comprimido, sem fonte, bitmap ou dependência nova.
  Criação temporária e publicação por rename (atômico quando suportado): erro ou
  cancelamento antes da publicação mantém o destino anterior intacto.
  Cancelar após a publicação não desfaz o arquivo já concluído.
- No visualizador/impressora, escolher **Tamanho real / 100%**, não Ajustar à página.
  A ação salva PDF; não abre um diálogo de impressora física nem imprime papel.

## Verificação

`RecentFilesTest`: persistência com nova instância, limites independentes,
normalização, deduplicação, intenção de importação e entradas antigas inválidas.
Preferences de teste são isoladas, sem gravar o histórico real do usuário.

`RecentPrintUiTest`: menu real habilitado, Ctrl+P, callbacks/caminhos/limpeza,
diálogo com ajuda, seleção oculta sem incluir outros objetos, abertura real
de Gerber via Recentes e falhas de arquivo/projeto preservando os objetos atuais.

`PrintPdfExporterTest`: escala MM/IN, coordenadas negativas, cor, recortes,
linhas/contornos, encaixe/orientação, entradas vazias/NaN/tamanho excessivo,
cancelamento/destino preservado, xref e reabertura como vetores pelo importador.
Prova opcional com `-Dflatcam.tests.pdfProof=true` gera
`flatcam-cam/target/print-pdf-proof.pdf`. `pdfinfo` e `pdftoppm` (Poppler)
abriram/renderizaram a prova; PNG inspecionado, vazios, posições e margem corretos.
É prova sintética, não validação física em impressora ou de todos os projetos.

Logs em `target/recent-print-targeted-20261012.log`,
`target/recent-print-integration-20261012.log` e
`target/recent-print-full-20261012.log`.
Build completo aprovado: 1626 registrados, 1608 aprovados, 18 opcionais ignorados,
zero falhas/erros; a prova visual opcional foi executada separadamente.
Após o build, o teste de UI foi repetido com herança de tema do diálogo,
aprovado em `target/recent-print-final-ui-20261012.log`.
Falta teste manual do hover dos submenus, FileChooser e impressão em papel.
Incluído no commit de consolidação de 2026-10-12, sem push;
mudanças anteriores de CNC e tooltips preservadas.
