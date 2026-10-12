# Série dos quatro próximos passos — 2026-10-12

Branch `flatcam-next`, commits locais em sequência, sem push.

| Etapa | Entrega | Commit |
| --- | --- | --- |
| 1 | Clearance do estacionamento Drilling com/sem exclusões | `c66b8b5f` |
| 2 | Escolhas de ordem/direção/referência de placa e otimização de furos | `e3961bfe` |
| 3 | Estratégia de passes contínuos alternados, conservando retração | `9a774579` |
| 4 | Confirmação de projeto/rascunhos ao abrir/sair, proteção Tcl e revisão final | Commit final desta série; consultar git log |

O item 3 **não fecha toda a paridade CNC**: a estratégia faltante foi implementada
e validada, mas discrepâncias deliberadas e erros do legado permanecem. Não
tratar a existência dos quatro commits como aprovação total dos fluxos.

## Verificação final

- `mvnw.cmd -q verify` aprovado no código final:
  `target/series-delivery-final-20261012.log`.
- Contagens dos reports que têm classes de teste no source atual: **1653
  registrados, 1635 aprovados, 18 opcionais ignorados, zero falhas/erros**.
  Não inclui reports antigos de classes removidas presentes em target.
- Auditoria de tooltips: **1042 ocorrências, zero MISSING** no inventário
  construído. Não equivale a visitar todos os estados e diálogos do aplicativo.
- Python original GRBL: corpus ampliado 38, **22 MATCH_EXECUTION,
  8 ROUTING_DIFFERENT, 6 MACHINING_DIFFERENT, 2 ORACLE_ERROR, 0 FX_ERROR**;
  strict **1**. Os quatro casos novos contínuos concordam, sem remover receitas
  anteriores, editar Python ou relaxar tolerâncias. Relatório:
  `target/series-3-cnc-20261012/python/index.html`.
- Revisão final também corrigiu seleção do próximo trajeto com o endpoint real
  após número par de passes alternados; evita uma cópia extra das coordenadas
  no roteamento legado. Perfis sem suporte normalizam a política enviada, mas
  mantêm a seleção no painel para retornar à fresagem. Saída diferida usa a
  janela atual se houver recriação por monitor/DPI durante o salvamento.

Contratos: [CNC_ROUTING.md](CNC_ROUTING.md),
[CNC_PARAMETER_PARITY.md](CNC_PARAMETER_PARITY.md),
[PROJETO_NAO_SALVO.md](PROJETO_NAO_SALVO.md).

## Análise do que ficou de fora

### CNC: divergências e cobertura

Continuam diferenças da primeira troca (Start Z versus Tool change Z), rotas
Around/Over e RPM zero/troca desligada. As quatro receitas do modo com retração
continuam distintas do contínuo Python por escolha explícita. Dwell zero causa
dois erros originais do Python; não foram copiados para o FX nem ocultados.
Falta comparar Geometry por várias fresas, muitos furos/slots, geometrias reais,
LinearRing/Polygon, Extra Cut contínuo, sonda, outros preprocessadores e efeitos
do controlador. Não há simulação física/certificação de usinagem.

### Ordenação e panelização

Agrupamento usa referência explícita de área e **caixas**, por fresa. Não agrupa
automaticamente cobre, não realiza todas as fresas de uma placa de cada vez
e não agrupa Drilling por referência de placa. Furos usam nearest-neighbour
opcional; slots não entram nessa otimização. Não considera custo dos desvios
Around/Over na escolha da sequência, não resolve TSP global e permanece
quadrático. Falta índice para corpos muito grandes e benchmark de geração.
Prioridade interno/externo cobre anéis fechados e fechados antes de pontes
abertas; não reconstrói semanticamente perfis internos abertos após edições
arbitrárias. Referência, origem, unidades e margem exigem conferência do usuário.

### Proteção de dados e interface

Não são autosave/recuperação após crash, lock/detecção de edição externa ou
confirmação de rascunhos de Preferências. Os testes de UI simulam escolhas;
falta clicar nos diálogos/FileChooser de verdade, testar cancelamento/falha
com duas telas e validar os novos controles no projeto real. Não foi feito
teste de máquina nem impressão física. Opções novas são persistidas no FX
atual; não foi certificada sua leitura por versões antigas do FX ou equivalência
em exportações Python ainda recusadas. O corpus privado completo de todas as
receitas anteriores não foi repetido nesta série.

### Confiabilidade e renderização (itens 5 e 6 da análise anterior)

A ocorrência intermitente de temporários/limpeza JUnit no Windows não foi
investigada nem declarada corrigida; os builds desta série passaram. Continua
um próximo item técnico, sem atribuir causa a antivírus/indexador sem evidência.
Renderer translúcido permanece em fallback Canvas; orçamento global de memória,
outros projetos/máquinas e novos benchmarks continuam pendentes. Não foi adotado
backend GPU novo, nem alegado ganho de FPS nesta série.

## Próxima ordem sugerida

1. Validação manual dos novos controles e confirmações no projeto real, com
   dois monitores, sem executar máquina antes de revisar alturas/trajetos.
2. Ampliar o corpus CNC para várias fresas/slots e definir as políticas restantes
   sem confundir compatibilidade do Python com segurança física.
3. Investigar a confiabilidade dos temporários e medir trajetos muito grandes.
4. Só depois, reavaliar transparência/memória e arquitetura gráfica com métricas.
