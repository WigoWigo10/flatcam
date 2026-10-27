# Formatos de importação Gerber/Excellon — 2026-10-27

Excellon agora configura inteiros IN/MM e zeros quando ausentes, além de unidades
e casas decimais já existentes. Padrões: IN 2:4, MM 3:3, TZ; unidade não declarada
continua RECUSAR, ou IN/MM por escolha explícita do usuário. Não se altera objeto
aberto nem se reinterpretam projetos em memória. Todos os caminhos que usam
ToolDefaults.excellonImport (Arquivo e Terminal) recebem estas opções.

## Prioridade e leitura

- Ponto decimal explícito: valor literal, independentemente de formato/zeros.
- FILE_FORMAT/Format em comentário ou máscara INCH/METRIC,00.0000: inteiros e
  decimais declarados prevalecem. Dígitos inteiros 1–9, decimais 0–9.
- LZ/TZ declarado junto das unidades, ou como instrução separada, prevalece.
- Só as informações ausentes usam as preferências da unidade correspondente.
- TZ mantém zeros à direita e pode omitir os iniciais: dividir por 10^decimais.
- LZ mantém zeros à esquerda e pode omitir os finais: dividir por
  10^(quantidade de dígitos recebidos - inteiros), sem contar o sinal.

Exemplo IN 2:4: X0012 significa 0,12 em LZ e 0,0012 em TZ. Antes o FX sempre
aplicava o divisor 10^decimais e ignorava LZ/TZ. Arquivos de largura completa
mantêm os resultados anteriores. A mesma leitura é usada por furos, G85,
roteamento linear e coordenadas modais; diâmetros de ferramenta são literais.
Formatos inválidos falham antes de devolver imagem parcial. Preferências inválidas
são recusadas ao salvar; dados antigos corrompidos usam o fallback por campo.

## Validação

48 controles públicos concordam exatamente com o ORIGINAL parse_number Python,
executado por AST sem alterar o corpo. Incluem sinais, zeros curtos/completos,
MM/IN e duas larguras inteiras. A comparação isola o decodificador, não a GUI
nem todo o parser Python. `tools/compare_excellon_import.py`; relatório
`target/excellon-import-python.json`. SHA-256 da fonte original:
3e9c843a1b3742744b2683afa8b89549ede2d5b71d359080a2deb0d39275ba5b.

Testes Java verificam prioridade, máscaras, comentários, slots G85/roteados,
corpus antigo, exportação/reimportação, preferências persistidas e Terminal.
Rodada focada: 42 CAM +136 UI aprovações e 1 skip opcional; log
`target/import-format-final.log`. Integral após itens 1–3: **2007 aprovações,
18 skips opcionais, zero falhas/erros**, 2025 registros;
`target/import-format-verify.log`.

## Limites que não foram copiados do Qt

Gerber já usa unidade de fallback e resolução de círculos/arcos; agora o intervalo
8–1024 também é validado ao salvar preferências. Buffering/Delayed Buffering
são opções de outro backend; não foram criados controles sem efeito no FX.
A estratégia gerber_use_buffer_for_union do Python também não foi transplantada:
a política de união robusta do FX foi mantida, sem novo buffer(0) ou alteração
do NCC. Isso não representa o catálogo integral de preferências Qt.
Excellon continua recusando G91 e arcos de roteamento não implementados.
