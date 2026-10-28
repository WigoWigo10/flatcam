# Unidades iniciais e precisão das leituras — 2026-10-28

**Editar > Preferências > Unidades e precisão** configura a unidade inicial
MM/IN e casas decimais independentes para cada unidade, de **2 a 16**. Os
padrões são MM e quatro casas em ambas, conferidos em `defaults.py` e
`GeneralAppPrefGroupUI.py` do Python.

Aplicar valida a seção inteira antes de salvar. Um campo inválido conserva o
rascunho e impede gravação parcial. Navegar e buscar preserva os campos;
Restaurar padrão prepara MM/4/4, sem salvar. As escolhas valem após reabrir o
aplicativo, como a precisão do Python. Não há reinício automático.

## Uso e precedência

- Uma sessão/projeto vazio começa na unidade configurada. Novos objetos
  Geometry e Excellon usam a unidade corrente da sessão.
- Importar arquivos ou abrir projetos continua respeitando suas próprias
  unidades. A preferência não converte objetos já criados.
- Limpar/criar projeto vazio volta à unidade inicial ativa. Uma escolha ainda
  aguardando reinício não muda o projeto atual.
- A precisão afeta coordenadas da barra inferior/HUD (X, Y, Dx, Dy), medição,
  tabelas de aberturas Gerber e ferramentas Excellon/CNC, diâmetros listados
  nas propriedades Geometry e distância percorrida nas propriedades CNC.
- Geometria, campos de entrada CAM, profundidades, avanços, parâmetros salvos,
  precisão do controlador, arquivos Gerber/Excellon, G-code e CSV permanecem
  independentes. Reduzir casas na leitura não reduz a precisão do cálculo.

`UiNumbers` faz somente apresentação, com `Locale.ROOT`; não deve ser usado
por serializadores ou algoritmos. `AppPreferences` persiste três chaves próprias;
valores ausentes/corrompidos usam os padrões. `MainApp` ativa a configuração
antes de criar a janela; salvar a preferência não troca essa configuração ativa.
HUD e status mantêm uma única chamada de formatação por leitura, como antes.

## Validação

`UiNumbersTest`: faixas, unidades, casas independentes, números pequenos,
dezesseis casas, valor original e preservação de `Locale`.
`PreferencesPaneTest`: rascunhos, erro em cada campo, gravação atômica,
restauração sem gravar e ausência de mudança na configuração ativa.

`UiNumbersUiTest` cria processos novos com preferências em memória, comparando
MM/4/4 com IN/2/8. Verifica abertura, novos objetos, retorno ao projeto vazio,
leituras reais, tabelas e importação MM sem arredondar coordenadas. O mesmo
caso GRBL gera G-code e CSV comparados byte a byte entre as configurações.
Evidências em `flatcam-fx/target/numeric-review/`.

`I18nUiTest` também constrói a nova página e gera capturas em Português/Inglês
nos quatro temas: `flatcam-fx/target/i18n-review/*-numbers.png`. A auditoria exige
ajuda própria nos controles novos. Catálogos revisados nos dois idiomas.

Suíte completa: **2128 aprovações, 18 skips opcionais, zero falhas/erros**
(2146 registros), em `target/numeric-verify.log` e JSON. O ajuste final da
medição sem painel, após trocar a unidade por importação, passou também em
`target/numeric-cross-units.log`. Auditorias com 1502 ocorrências e zero
MISSING em cada idioma. Build/probe nativos em `target/numeric-native-build.log`.

## Limites deste incremento

Não equivale a todos os controles numéricos do Python: painéis especializados,
tempos, revisão de precisão do controlador e alguns resumos conservam seus
próprios formatos. Os campos editáveis continuam preservando o texto/valor
completo, em vez de arredondá-lo ao número de casas de leitura. Não altera
tolerâncias geométricas, exportação ou arredondamento do G-code.

Próximas preferências gerais de maior efeito visual: fontes independentes da
interface/HUD/eixos e personalização de mouse/cursor. O inventário atualizado
está em [PREFERENCIAS.md](PREFERENCIAS.md).

As fontes foram implementadas na etapa seguinte: [FONT_PREFERENCES.md](FONT_PREFERENCES.md).
