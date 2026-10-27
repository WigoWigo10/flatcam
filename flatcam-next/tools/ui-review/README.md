# Revisão manual com dados isolados

Executa os aplicativos originais usando artefatos locais já compilados.
Não contém automação de cliques. Requer Windows, JAVA_HOME/JDK, compilador C#
do .NET Framework e ambiente Python do FlatCAM com suas dependências instaladas.

Na pasta `flatcam-next`, após `mvnw.cmd verify` e `build-native.cmd`:

```powershell
.\tools\ui-review\prepare.ps1 -Project 'C:\caminho\projeto.FlatPrj' -Python 'C:\caminho\venv\Scripts\pythonw.exe'
```

Abra `target/ui-review/FxReview.exe` e `PythonReview.exe`. Pela interface,
abra somente `project-fx.FlatPrj` e `project-python.FlatPrj` desse diretório.
O wrapper termina ao lançar o aplicativo; ferramentas de captura devem selecionar
a janela do javaw/pythonw filho, não a do wrapper. Falhas de lançamento são
gravadas em `.launch.error`.

Feche as sessões antes de preparar outra rodada: preparar substitui as cópias,
launchers e manifesto no destino. Nunca use o diretório de revisão para dados
que precisam ser preservados. O destino deve estar dentro do reactor.

`ReviewPreferencesFactory` troca somente java.util.prefs por armazenamento em
memória. `python_window.py` direciona APPDATA/SHGetFolderPath e substitui somente
o construtor QSettings de configuração por INI explícito no destino de revisão,
depois executa o FlatCAM.py original. `setDefaultFormat` sozinho não isolou o
construtor de duas strings deste PyQt. Construtores não suportados são recusados;
use `--probe` no script para conferir o destino e a leitura/escrita sem abrir GUI.
Geometria, leitores,
renderização e comandos do aplicativo não são substituídos. O isolamento não
promete impedir alterações nos recentes dos seletores nativos do Windows.

`manifest.json` guarda o caminho/hash inicial do original. Compare-o ao final
com `Get-FileHash -Algorithm SHA256` no original e nas cópias. Não salve sobre
o projeto original. `short-lz.drl` é uma entrada sintética IN,LZ com máscara
2:4 para ensaios adicionais; sua criação não significa que foi importada na UI.

O FX usa classes do build atual e dependências sincronizadas por build-native.
Inclui o contorno de runtime documentado em ../../JVM_STRING_SEARCH.md.
Os launchers locais servem exclusivamente à revisão; não alteram a instalação.
Resultado e lacunas desta rodada: [UI_PARITY_REVIEW.md](../../UI_PARITY_REVIEW.md).
