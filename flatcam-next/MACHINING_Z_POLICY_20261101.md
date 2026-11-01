# Políticas de coordenadas Z — 2026-11-01

O FX passa a oferecer as duas políticas do Python para Cut Z e Travel Z.
A escolha fica em **Preferências > CNC Job > CNC Job (geral) > Permitir
coordenadas Z livres (Machinist)**. Desmarcada por padrão. A política é
capturada ao gerar; mudar a preferência também afeta a próxima geração em
painéis já abertos.

| Entrada | Padrão | Machinist |
| --- | --- | --- |
| Cut Z −0,2 | −0,2 | −0,2 |
| Cut Z +0,2 | −0,2, com aviso | +0,2 |
| Cut Z 0 | Recusado | 0 |
| Travel Z −3 | +3, com aviso | −3 |
| Travel Z 0 | Recusado | 0 |
| NaN, infinito ou texto inválido | Recusado | Recusado |

## Alcance

- Geometry → CNC, incluindo parâmetros por ferramenta; Drilling; e Cutout,
  incluindo pontes Thin. Os painéis exibem a política e as conversões de sinal.
- Terminal: `cncjob` e `drillcncjob`, com registro das conversões na saída.
- Cut Z de Geometry agora é uma coordenada com sinal, como nos outros painéis.
  Fresas V continuam calculando a profundidade pela ponta, ângulo e largura;
  o campo manual é identificado como não aplicável a V.
- Machinist permite posições Z finitas assinadas também nos parâmetros comuns
  de início, troca e fim. As restrições próprias dos perfis, sondagem,
  roteamento e exclusões continuam valendo.
- Multi-Depth percorre passos até o alvo assinado exato; alvo zero em
  Machinist produz uma passada. Depth / Pass continua positivo, com o limite
  de 10.000 passadas. Não foi introduzido um parâmetro de superfície inicial:
  as passadas continuam referidas a Z0.
- Drilling mantém a convenção do Python: **Cut Z efetivo = Cut Z − Offset Z**.
  Offset positivo aprofunda o alvo. O resumo usa essa fórmula e o resultado
  assinado. No modo padrão o alvo efetivo precisa ficar abaixo de Z0.
- Thin Depth é uma coordenada acima do Cut Z completo: por exemplo, corte
  +0,2 e pontes +0,5 em Machinist. A ordem entre os dois valores continua
  sendo validada.

## Projetos, preferências e Tools Database

Os modelos continuam guardando `cutDepth`/`drillDepth` como a distância
assinada abaixo de Z0; `cutZ()` fornece a coordenada efetiva de entrada. Isso
mantém o significado dos valores positivos dos projetos antigos.

`zPolicy` é salvo nos parâmetros de Geometry, Drilling e posições comuns.
Ausência do campo significa Standard. Há testes de salvar/reabrir `.fcnproj`
e `.FlatPrj` pelo FX, incluindo coordenadas positivas, Travel Z negativo,
Offset e posições comuns. Esta rodada não executou o aplicativo Python
abrindo os novos arquivos; não confundir com o probe do gerador abaixo.

A preferência antiga `geometry.cutdepth@mm/@in` é migrada para
`geometry.cutz@mm/@in` invertendo o sinal. Exemplo: magnitude antiga
0,1981234567 vira Cut Z −0,1981234567, preservando a usinagem. A importação
de preferências antigas também migra; uma chave moderna explícita tem
prioridade. Restaurar padrões remove as duas formas.

Tools Database e importação Python preservam coordenadas assinadas como
rascunho, sem ativar a preferência global Machinist. Ao gerar, o FX aplica
a escolha atual do usuário. Coordenada presente inválida na base não é
substituída silenciosamente por um padrão. Projetos Machinist com valores
antes proibidos podem ser recusados por versões antigas do FX.

## Prévia

Programas G-code Machinist gerados pelo FX recebem comentários de metadados.
O parser usa movimentos de avanço/corte e rápidos, sem presumir que todo
movimento com Z positivo é deslocamento. Isso permite visualizar o programa
com outra origem Z; não determina onde há material.

**Roland RML-1 não preserva comentários.** A geometria do job recém-gerado
continua disponível, mas reabrir o arquivo ou editar seu código usa a
interpretação de superfície Z0 do parser nativo. O resumo avisa sobre essa
limitação. Nenhum comando ou comentário não suportado foi inserido em RML-1.

## Evidências e repetição

- `MachiningZPolicyTest`: sinais, zero, não finitos, passadas assinadas,
  G-code e prévia positiva, Offset na furação e Travel negativo.
- `MachiningZUiTest`: matriz dos três painéis × MM/IN × dois modos ×
  Cut Z negativo/zero/positivo; preferência real, migração, alteração com
  painel aberto, Thin positivo e aviso de prévia Roland.
- `MachiningZPersistenceTest`: roundtrip dos dois formatos pelo FX.
- `TclLiveHostTest`: comandos no host real; testes da DB verificam preservação
  de sinais e rejeição explícita de valores inválidos.
- Testes focados: `target/machining-z-focused-final.log`.
- Verify integral: **2.327 casos, 2.309 aprovados, 18 opcionais ignorados,
  zero falhas/erros**, `target/machining-z-verify-complete.log`.
- Após o verify integral, os dois campos Travel Z do editor da DB tiveram
  seus intervalos ampliados para rascunhos assinados. Verify focado dessa
  alteração, incluindo o editor real, modos Z, persistência e ajuda:
  **82 aprovados, zero falhas/erros**, `target/machining-z-db-final.log`.
  Os resultados não são somados ao total
  integral. Contagens em `target/machining-z-verification.json`.
- Catálogos PT-BR/EN: `python tools/write_ui_catalogs.py --strict --check`.

As tentativas anteriores (`machining-z-verify.log`, `verify2.log` e
`machining-z-verify-final.log`) registram expectativas antigas de rejeição de
Cut Z positivo e de textos de ajuda. A nova checagem de não finitos também
identificou o fallback silencioso da DB, corrigido com leitura estrita das
coordenadas presentes. Um teste de Terminal teve falha transitória ao limpar
arquivo temporário em uma rodada focada anterior; a repetição passou sem
alterar ou suprimir sua checagem de limpeza. Não foi identificada a causa do
bloqueio do arquivo.

O probe `tools/probe_machining_z_python.py` usa o `camlib.CNCjob` real com
app stub, sem abrir janelas ou gravar preferências. Executado com o ambiente
Python legado local: **12 casos aprovados**, um único tool, uma passada,
MM e `GRBL_11_no_M6`; dois modos × três Cut Z × dois Travel Z. Ele compara
os alvos de avanço Z e a recusa de Cut Z zero no modo padrão. Não prova
equivalência de todos os geradores Python nem dos casos Multi-Depth.
Relatórios: `target/machining_z_python_probe.json` e
`target/machining-z-python-final.log`.

```powershell
# Na raiz do worktree; use o Python com dependências do FlatCAM legado.
python .\flatcam-next\tools\probe_machining_z_python.py
# No diretório flatcam-next:
.\mvnw.cmd verify
```

Não houve teste em máquina CNC física. Versão e instaladores permanecem
os da Beta 3 entregue; estas mudanças ainda não foram empacotadas.
