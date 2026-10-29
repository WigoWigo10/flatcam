# Gerar e validar a Beta Windows

Execute na pasta `flatcam-next`, em Windows x64. São necessários JDK 25 com
`JAVA_HOME` configurado, Git, MSYS2 UCRT64 g++/windres e acesso ao Maven Central
e aos downloads oficiais de ferramentas. O testador recebe o runtime pronto;
esses requisitos são só de quem gera a distribuição.

```powershell
.\build-beta.ps1
.\test-beta.ps1
```

Para validar também a atualização da Beta anterior, use o MSI realmente
distribuído na rodada anterior:

```powershell
.\test-beta.ps1 -PreviousMsi "C:\caminho\FlatCAM-FX-0.1.0-beta.2-windows-x64-setup.msi"
```

O teste recusa substituir uma instalação do usuário. Instala a versão antiga
em uma pasta temporária, atualiza pela mesma família de upgrade, confere a
remoção do produto/JAR antigo e a preservação de um projeto de teste, executa
os probes no atualizado e desinstala. Sem `-PreviousMsi`, essa verificação fica
explicitamente `NOT_RUN` em `validation.json`.

O projeto do ensaio fica fora da pasta instalada. Um ensaio inicial dentro
dessa pasta foi apagado pela remoção recursiva do MSI da Beta 1 (FileRemove
confirmado no log). Isso é uma limitação real, documentada nas notas da Beta 2:
copiar arquivos pessoais para fora da pasta do aplicativo antes de atualizar.
A validação não afirma preservar arquivos extras dentro da pasta instalada.
Registro e contexto por usuário são conferidos pela API Windows Installer,
sem presumir o local do registro exibido em Aplicativos instalados.

`build-beta.ps1` exige árvore Git limpa. Roda `mvnw verify`, sincroniza o
launcher nativo/jars e gera um runtime reduzido com `jlink`. Downloads fixados:
Temurin 25.0.5+8 e WiX 3.14.1, com SHA-256 conferido antes da extração.
O JDK Oracle local não é copiado para o pacote. Pode-se fornecer outro OpenJDK
GPL 25 x64 com `-RuntimeJdk`, registrado como override no manifesto.
O módulo FX executa os testes com PreferencesFactory em memória por padrão,
evitando dependência ou gravação nas preferências reais do Windows.

O `jpackage` cria a imagem e os instaladores MSI/EXE. O launcher da imagem
é substituído pelo host JNI do FX, compilado sem janela de console. Ele mantém
manifesto DPI, exports de GPU híbrida, seleção D3D/Software e diagnósticos.
No pacote, usa **exclusivamente** `runtime` e `app` ao lado do executável:
não resolve Java no PATH/JAVA_HOME nem procura classes na árvore de desenvolvimento.

O manifesto declara UTF-8 para o processo antes de iniciar a JVM. Isso alinha
os caminhos UTF-8 com a codificação usada nas opções da API JNI e permite
instalações em pastas acentuadas. A declaração é por processo, sem mudar a
configuração do Windows. Referência: [manifestos do Windows](https://learn.microsoft.com/en-us/windows/win32/sbscs/application-manifests).

Saídas em `target/releases/<versão>-<UTC>/`; `target/beta-latest.txt` aponta
para a última geração. A pasta contém:

- ZIP portátil, EXE instalador e MSI.
- `artifacts.json`: tamanho e SHA-256 de cada artefato.
- `verify.log` e `native-build.log`: resultados completos da geração.
- `validation.json`: resultado posterior do teste de distribuição.

Cada imagem contém `flatcam-release.json`, licença, notas da Beta, guia e
`SHA256SUMS.txt` com o inventário dos arquivos. O commit também é filtrado em
`build-info.properties` dentro do JAR para aparecer no diagnóstico.

O processo é reproduzível por comando com dependências fixadas; **não** se
afirma identidade binária byte a byte: horários, assinatura ausente e metadados
de compilação/instalador podem mudar entre gerações.

## Validação da distribuição

`test-beta.ps1` confere os hashes e extrai o ZIP em uma pasta temporária fora
do repositório, com espaços e acento no nome. Os processos de teste recebem
PATH reduzido, JAVA_HOME e FLATCAM_FX_JAVA_HOME inválidos e nenhum classpath
de desenvolvimento. O launcher é exercitado em GPU e Software.

Um probe externo é compilado contra **os JARs do pacote** e executado pelo
Java incluído. Ele abre a interface em janela invisível, verifica a versão e
o commit, abre o guia Outline, importa arquivos sintéticos Gerber/Excellon,
converte contorno em área, paneliza com referência comum, confere furos/rasgos,
gera Isolation/NCC/Cutout/CNC/Drilling, exporta e reabre `.fcnproj`/`.FlatPrj`.
Também exerce os formulários reais de NCC/Isolation, a edição precisa do
diâmetro e a troca Seed → Standard com preservação do rascunho após gerar.
Os arquivos originais sintéticos não podem mudar. Preferências ficam em memória.
O probe de diagnósticos também deve produzir uma gravação JFR válida com o
runtime incluído, tanto no portátil quanto no instalado.

O MSI é instalado por usuário em pasta temporária, com log detalhado; os
mesmos probes rodam no instalado e ele é desinstalado. O teste recusa substituir
uma Beta já instalada pelo usuário. As pastas/logs da rodada são preservadas
para revisão. O EXE é gerado com a mesma imagem; os cliques do assistente EXE
não são automatizados por esse teste MSI.

Isso não substitui uma VM limpa, um segundo computador, teste físico CNC,
auditoria de segurança ou avaliação manual de todos os diálogos/DPI. Esses
itens ficam explicitamente fora da aprovação automatizada desta primeira Beta.

## Opções para desenvolvimento

`-AllowDirty` no gerador identifica o pacote como dirty; não deve receber tag
de release. `-SkipTests` pula a suíte para investigar empacotamento e não conta
como validação integral. `-PortableOnly` não gera instaladores. No verificador,
`-SkipInstallation` registra instalação como NOT_RUN, nunca como aprovada.

Nenhuma opção publica arquivos ou envia commits/tags ao remoto. Após todas
as verificações, crie uma tag anotada no commit limpo efetivamente testado e
registre no texto da tag os resultados e os hashes. A publicação é uma etapa
separada.
