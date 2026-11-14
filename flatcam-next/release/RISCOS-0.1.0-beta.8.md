# Prioridades de validação — Beta 8

14/11/2026. Priorização qualitativa para os testes controlados desta Beta.

| Risco | Verificação exigida | Limite |
|---|---|---|
| Importação incorreta de Gerber legado | Suíte dos formatos/comandos/zeros/regiões, corpus público e importação do probe empacotado | Não cobre todo arquivo externo; ZIP privado não é distribuído |
| Ligação atravessa cobre ou passa fora da área | Testes de distância pelo raio nominal, Connect/Contour/Rest MM/IN; controles nos JARs | Não comprova segurança física ou fixação |
| Omitir material alcançável | Regressões Lines/Standard/Rest, seis métodos em placa pública e GRBL relido | Resíduos brutos e precisão continuam explícitos; alvo diagnóstico não é Outline |
| Espiral densa esgota orçamento ou demora | Testes do limite/retry/cancelamento, medição anterior preservada, ensaio no pacote | Outros casos ainda podem cair na radial; sem ganho universal |
| Preview antiga ou ferramenta perde edição | Suíte UI e 13 cenários CAM de estado por imagem | Não substitui todos os gestos manuais/DPI |
| Pacote usa JAR antigo ou perde dados no upgrade | Commit/versão/hashes, Java incluído, MSI 0.1.70 → 0.1.80, projeto externo preservado | Arquivos pessoais devem ficar fora da pasta instalada |

Condições locais: árvore limpa e commitada, suíte integral e resultados do
portátil/MSI/upgrade registrados. Falha inicial e repetição são preservadas;
verificação não executada não conta como aprovada. Recomendação limitada a
testes da Beta; não significa homologação de máquina CNC.

Se surgirem alterações indevidas na origem, unidades incorretas, trajetos fora
da área ou perda do editor, interromper esse fluxo e guardar arquivo, parâmetros
e logs. Usar o portátil anterior em outra pasta com cópias dos projetos; não
desinstalar nem sobrescrever dados do usuário automaticamente para testar.
