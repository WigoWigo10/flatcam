# Placas da comunidade para testes do FlatCAM FX

Coleção local de **8 pacotes / 7 projetos upstream**, consultados em
2026-11-09. 90 arquivos de fabricação extraídos/copiados e
14.89 MiB de downloads originais (incluindo CAD/documentação).
Os originais e os Gerbers não foram modificados. O catálogo não afirma que os
projetos sejam eletricamente corretos ou que possam ser fresados com qualquer fresa.

![Galeria real da importação](gallery.png)

| Pacote local | Tipo / cobertura de teste | Furação disponível | Licença declarada na origem |
|---|---|---|---|
| [Breadboard Power Supply v2](boards/breadboard-power-supply/fabrication/) | Fonte de alimentação THT | 37 furos / 0 slots | [MIT](https://github.com/futureshocked/KicadLikeAPro-Project-1-BreadboardPowerSupply/tree/f65bb0fbea65addf6b35a963f8ddaca6ca131d32) |
| [Horizon MX](boards/horizon-mx/fabrication/) | Teclado / matriz repetitiva | 363 furos / 0 slots | [MIT](https://github.com/skarrmann/horizon/tree/80b319cd03beab5e4e4f083784ff7fabfe21f922) |
| [Horizon Choc](boards/horizon-choc/fabrication/) | Teclado compacto / outro padrão de pads | 363 furos / 0 slots | [MIT](https://github.com/skarrmann/horizon/tree/80b319cd03beab5e4e4f083784ff7fabfe21f922) |
| [Air Quality Sensor](boards/air-quality-sensor/fabrication/) | Sensor ambiental / quatro camadas | 269 furos / 0 slots | [CERN-OHL-S-2.0](https://github.com/roepkefeller/air-quality-sensor/tree/7c879dc19d37323527742d8d7aa56966825af953) |
| [Audio Watermark Generator](boards/audio-watermark/fabrication/) | Áudio + RF / quatro camadas | 170 furos / 4 slots | [MIT-declared-in-README](https://github.com/kristiangoystdal/Audio_Watermark_Generator/tree/6fc09014685454fb43bfb46b4ba1df1b49bb755e) |
| [Modxo RP2040-Zero Adapter](boards/modxo-adapter/fabrication/) | Adaptador / pads THT e SMD | 18 furos / 0 slots | [CERN-OHL-P-2.0](https://github.com/m4x10187/Modxo_RP2040_Zero_Adapter/tree/106a13563d424df5e17c31b75e66653578c3112a) |
| [Zigbee Touch Keypad Y24](boards/capacitive-keypad/fabrication/) | Teclado capacitivo / grandes eletrodos | 37 furos / 0 slots | [CERN-OHL-S-2.0](https://github.com/2dom/keypad/tree/0b67f440d518bbb81f984c9871afa257dd8921b4) |
| [Single-Sided 5V Power Supply](boards/single-sided-5v/fabrication/) | Fonte linear / caso parcial sem drill | Sem drill publicado | [MIT](https://github.com/Mimo-Mir/Single-Sided-5V-Power-Supply/tree/1b76af777e686318bf39467884db66879a75fe35) |

## Abrir no FX ou no Python

1. Entre em `boards/<id>/fabrication/` pelo link da tabela. Abra os Gerbers de cobre
   (`F_Cu`, `B_Cu`, `CuTop`, `CuBottom`, `.gtl` ou `.gbl`). Alguns pacotes têm uma subpasta.
2. Abra o contorno (`Edge_Cuts`, `Edge.Cuts`, `EdgeCuts`, `.gm1` ou `.gko`) e os `.drl`
   correspondentes. Arquivos `drl_map.gbr` são desenhos do mapa, não furação Excellon.
3. Confira unidades e alinhamento; salve projetos derivados em outra pasta de trabalho.
   Para Lines, comece com uma face por vez e compare Contour/Connect separadamente.

O primeiro ensaio dos **parsers de produção FX** processou 88 arquivos
selecionados sem erro no conjunto de fabricação final. Arquivos `.gbrjob` são metadados,
não objetos plotados. Relatório: [validation/fx-import.json](validation/fx-import.json).
Isso não valida cliques da interface, NCC/Paint/Isolation, panelização ou o Python em
todas essas placas. As matrizes de trajetos permanecem como próxima etapa.

## Origem, integridade e limites

- `sources.json`: manifesto pequeno versionável, com commits fixos, URLs, tamanhos e Git blob SHA-1.
- `inventory.json`: arquivos locais, origem individual e SHA-256; `SHA256SUMS.txt` reconferido.
- `boards/*/upstream/`: ZIP original, licença/README e arquivos CAD selecionados quando publicados.
- `boards/*/ORIGEM.md`: atribuição e detalhes próprios do projeto.
- Downloads/dados e relatórios são locais e ignorados pelo Git; o manifesto e os scripts permitem recuperá-los.
- Sensor e teclado capacitivo usam **CERN-OHL-S-2.0 para hardware**, mesmo quando o firmware é MIT.
  Texto de referência preservado em `licenses/`, com origem SPDX registrada.
- Áudio: MIT declarado no README; o upstream não inclui um arquivo LICENSE autônomo.
- Fonte Single-Sided: pacote incompleto de furação. Gerbers das duas faces existem; não inferir
  número de camadas efetivas pelo nome do projeto nem criar um drill que o autor não publicou.
- Teclado capacitivo: foi usado o ZIP KiCad `YG/PCB_2729439A_Y24.zip` que está dentro do ZIP
  original. O pacote também traz intermediários do fornecedor em `ok/`; nove foram recusados pelo
  parser FX por token `*` antes de `%FS`. Preservados em `validation/vendor-preview/` e no ZIP,
  com a primeira falha em `validation/fx-import-with-vendor-preview.json`. Não contar esse grupo
  como importação aprovada e não alterar silenciosamente seus bytes para fazê-lo passar.
- RP2040 Gamepad foi considerado, mas não incluído: o commit consultado não declara licença explícita.

## Repetir

A partir de `flatcam-next`, sem executar scripts dos projetos baixados:

```powershell
python tools/download_community_boards.py
java -Xmx3g --class-path 'flatcam-cam/target/classes;flatcam-fx/target/dependency/*' tools/CommunityBoardInventory.java samples/community/inventory.json samples/community/validation/fx-import.json
.\target\oracle-py311\Scripts\python.exe tools/render_community_boards.py
```

O downloader verifica cada blob do commit, extrai somente arquivos CAM e recusa sobrescrever
um arquivo local alterado. O gerador da galeria reconfere todos os hashes antes de renderizar.
Os arquivos continuam sujeitos às licenças dos respectivos autores; não herdam a licença do FX.
