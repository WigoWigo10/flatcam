# NCC: ponto inicial do Seed

No painel **NCC Tool**, selecione uma única ferramenta CLEAR e o método Seed
(ou Combo). O campo **Seed inicial** oferece:

- **Estável (FX)**: padrão anterior preservado. Busca um ponto interior mais
  afastado das bordas, reduzindo mudanças bruscas dos anéis.
- **Representativo (Python)**: usa a mesma regra de scan-line/ponto
  representativo do legado, sem arredondar ou normalizar a geometria de origem.

A escolha pertence à ferramenta. Trocar de linha recupera sua escolha;
“Aplicar parâmetros a todas as ferramentas” também a copia. Rest Machining
preserva essa política por fresa; Connect/Contour/Offset seguem os controles
comuns Rest existentes. Standard e Lines não usam este campo. Combo o usa
somente se precisar recorrer a Seed. Importar ferramentas da base Python usa
o padrão estável; o Python não possui esse parâmetro adicional.

O modo Python reproduziu exatamente os caminhos do corpus sintético MM/IN na
referência Python 3.11 / Shapely 1.8.5.post1 / GEOS 3.10.3. Isso **não garante**
anéis iguais em todo projeto: diferenças minúsculas de coordenadas podem mudar
a scan-line e deslocar o ponto inicial. Veja [INVESTIGACAO_CAM.md](INVESTIGACAO_CAM.md).
O modo estável continua deliberadamente diferente; não conta como equivalência
literal dos caminhos Python. Nenhum modo é uma validação de segurança de máquina.

Esta escolha é um parâmetro da geração no painel, não uma preferência global
nem um novo campo do projeto nativo. As geometrias geradas, seus parâmetros CNC
e programas continuam usando a persistência existente.
