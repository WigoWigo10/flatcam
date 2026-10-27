# Proteção de alterações não salvas — série 4/4, 2026-10-12

## Fluxos protegidos

- Scripts (2026-10-26): fechar a aba, Arquivo > Sair e fechar a janela oferecem
  Salvar / Descartar / Cancelar para cada script não salvo. Cancelar ou falhar ao
  salvar impede a saída, sem remover abas já examinadas. Scripts são arquivos
  independentes: salvar o projeto não os salva. Aprovações são revalidadas para
  não descartar textos alterados ou novas abas criadas durante diálogos modais.
  Projeto Novo/Abrir conservam as abas de scripts; não as descartam.

- Abrir projeto pelo menu e Recentes: Salvar projeto / Não salvar / Cancelar.
  O arquivo escolhido só substitui o atual depois de validação completa.
  Consentir em não salvar não apaga o projeto atual se o alvo falhar ao carregar.
- Arquivo > Novo Projeto (Ctrl+N, 2026-10-17): mesma confirmação; depois remove todos
  os objetos e esquece o arquivo atual. Preferências, layout e variáveis do Terminal
  ficam, como o interpretador Tcl do Python. Projeto vazio e limpo não pergunta.
- Fechar pela janela ou Arquivo > Sair: mesma confirmação; também conserva
  a confirmação existente da Tools Database e a gravação de layout/monitor.
- Rascunhos de Gerber/Excellon/Geometry/G-code: Voltar e aplicar / Descartar
  rascunho / Cancelar. Não aplica nem gera cortes automaticamente ao tentar sair.
  Ao voltar, aplicar o editor e salvar o projeto são passos separados.
- Se há operação CAM ou Terminal em andamento, a transição é bloqueada com
  mensagem no status; aguardar ou cancelar a operação antes de continuar.

Salvar Projeto (Ctrl+S) grava direto no `.fcnproj` atual, como `on_file_saveproject`
do Python; sem arquivo atual, ou com um `.FlatPrj` importado do Python, abre o destino
(o original Python nunca é substituído em silêncio). Salvar Projeto Como (Ctrl+Shift+S)
sempre pergunta. O diálogo de salvar abre o FileChooser, sugere nome/formato/pasta do projeto atual e exige
`.fcnproj` ou `.FlatPrj`. Cancelar o destino, falha na escrita/compatibilidade
ou alterações durante a gravação **não** permitem a abertura/saída pendente.
Após uma gravação válida, só a versão efetivamente serializada vira baseline;
uma edição posterior continua não salva. Callback obsoleto/duplicado não pode
concluir outra tentativa de salvamento. A UI não espera o worker sincronicamente.

## O que conta como alteração

Objetos adicionados/removidos, nomes, versões da geometria/G-code, parâmetros
CNC persistidos e aparência dos objetos (visibilidade, cores, Solid/Multi-Color,
Follow e opções CNC persistidas). Zoom, pan, seleção, passos da inspeção de
trajeto e mudanças de tema/layout não são alterações do projeto.

`ProjectChangeTracker` compara stamps de identidade/versão e configurações,
não serializa nem compara todos os vértices a cada pulso. Versões de objetos
do baseline são referências fracas para não reter uma segunda geometria/programa
grande depois de editar. Não instala um timer contínuo. Alterações que geram
uma nova versão idêntica podem continuar exigindo salvar (conservador).

Abrir/salvar com sucesso registra baseline e arquivo atual. Salvamento Tcl
também atualiza só o baseline serializado, sem gravar histórico/pastas do usuário.
O estado limpo de uma carga não impede detectar uma alteração posterior.

## Terminal

`open_project arquivo` recusa projeto modificado. Usar `save_project` primeiro
ou consentir explicitamente ao descarte:

```tcl
open_project {C:/pasta/projeto.fcnproj} -force true
```

`force` é extensão FX; não deve ser usada automaticamente em scripts do Python.
Aceita booleano validado (True/False, 1/0). Não ignora rascunhos, operação CAM
em andamento, erro de arquivo, cancelamento ou edição durante o carregamento.
Os testes antigos que descartavam dados de propósito agora dizem `force true`;
as verificações de publicação tardia/arquivo malformado continuam executadas.

## Verificação e limites

ProjectChangeTrackerTest: identidade/nome/aparência/configurações, remoção,
epoch, versão anterior salva e retorno ao baseline.
UnsavedChangesGuardTest: limpo/cancelar/descartar, Save assíncrono, falha,
edição posterior, reentrância e callback obsoleto.
ProjectUnsavedUiTest: save real pelo worker, decisões de fechamento, aparência
versus câmera, proteção Terminal e rascunho real apagado/descartado sem perder
o objeto original. Recentes e toda a suíte TclLiveHost reexecutados.
Logs: target/series-4-guard-final-20261012.log e target/series-4-full-20261012.log.
Build integral final e teste de saída diferida com ação da janela atual:
target/series-delivery-final-20261012.log, aprovado. Salvamento durante troca
de monitor/DPI não usa uma ação de persistência capturada de uma janela antiga.

Não é autosave, recuperação após crash nem bloqueio de arquivo. Não detecta
edições externas no disco. Não protege automaticamente rascunhos de Preferências
ou variáveis transitórias do Tcl como se fossem parte do projeto. A janela
de confirmação, FileChooser e fechamento nativo ainda precisam de teste manual;
os testes simulam decisões, não clicam em diálogos no desktop do usuário.
