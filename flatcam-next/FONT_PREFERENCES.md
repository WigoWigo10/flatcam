# Tamanhos de fonte — 2026-10-28

Em **Editar > Preferências > Tamanhos das fontes**, configure quatro tamanhos
independentes, em pixels lógicos do JavaFX:

| Região | Padrão FX | Faixa |
| --- | --- | --- |
| Interface | 12 | 8–40 |
| Texto e código | 13 | 8–40 |
| Números dos eixos | 13 | 0–40 |
| HUD | 11 | 8–40 |

Os exemplos locais acompanham os campos. **Aplicar** valida todos antes de
salvar; a interface usa a configuração na próxima abertura. Navegar entre
seções mantém os rascunhos. **Restaurar padrões** preenche o formulário, mas
só grava depois de aplicar. Valores antigos inválidos voltam individualmente
ao padrão. Trocar o tema conserva os tamanhos ativos.

Texto e código inclui `TextArea`, o conteúdo virtualizado do editor e seus
números de linha. As caixas de texto passam a compartilhar o padrão 13 do
editor; antes herdavam o tamanho 12 da interface. O código mantém a família
monoespaçada. A preferência muda tamanhos, não oferece seleção de famílias.

O tamanho zero dos eixos oculta apenas os números nas réguas. As faixas das
réguas continuam presentes e a grade mantém seu controle próprio. Fontes
maiores ampliam essas faixas; rótulos próximos são espaçados para evitar
sobreposição. Os cálculos de câmera, seleção e overlays usam os mesmos
recuos. Tabelas compactas de ferramentas aumentam a altura das linhas.

## Relação com o Python e limites

A referência é `appGUI/preferences/general/GeneralAPPSetGroupUI.py`: Notebook,
Axis, Textbox e HUD. No Python os padrões Qt são 12/8/10/8. O FX conserva
12/13/13/11 para os grupos correspondentes, com a padronização das caixas de
texto descrita acima. Interface afeta mais controles que apenas o notebook.

A splash, elementos compactos da barra inferior e tooltips têm tipografia
própria. Anotações e indicadores de passos CNC continuam com seus controles
específicos. Geometria, unidades, precisão numérica e conteúdo de código não
mudam. Tamanhos extremos podem exigir mais largura de painel e rolagem; esta
entrega não certifica todos os painéis a 40 nem todas as combinações físicas
de monitores/DPI.

## Verificação

`UiFontsTest` cobre faixas e padrões. `PreferencesPaneTest` confere rascunho,
validação, prévia local, aplicação na próxima abertura e reset sem escrita.
`UiFontsUiTest` inicia quatro JVMs isoladas com preferências em memória:
padrão, 20/24/28/22, máximo 40/40/40/40 e 16/18/0/14.

Os probes conferem fontes reais de controles, glifos e números de linha do
editor, HUD, persistência/fallback, quatro temas sem avisos CSS, altura das
linhas, conversão de coordenadas e edição/desfazer sem alterar o texto.
Pixels das réguas confirmam desenho com tamanho positivo e ausência dos
números com tamanho zero. Capturas em
`flatcam-fx/target/font-review/<modo>/<tema>.png`; logs `probe.log` na mesma
pasta. A página de preferências também tem capturas em Português/Inglês nos
quatro temas em `flatcam-fx/target/i18n-review`.

Comandos de repetição:

```powershell
.\mvnw.cmd -pl flatcam-fx -am "-Dtest=UiFontsTest,UiFontsUiTest,PreferencesPaneTest,I18nUiTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
python tools/write_ui_catalogs.py --strict --check
.\mvnw.cmd verify
.\build-native.cmd
```

Resultado integral e build nativo: ver o registro desta etapa em
[CONTEXTO_E_PROGRESSO.md](CONTEXTO_E_PROGRESSO.md).
