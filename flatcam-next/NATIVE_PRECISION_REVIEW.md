# Precisão de saída na revisão CNC — 2026-10-24

Segunda das três etapas sobre `c52029c1`. A revisão de Geometry e Drilling
mostra os valores solicitados e os efetivamente codificados no Roland:
profundidade final, Travel Z, avanços XY/Z, primeiro passe e End Z. Exemplo:
125 → 126 mm/min; profundidade -0,6006 → -0,6000 mm. Os avisos oferecem acesso
ao campo correspondente. O rápido Roland aparece como V/avanço efetivo,
incluindo automático 900 mm/min, em vez da indicação incorreta de G0 sem F.

HPGL mostra o passo XY de 0,025 mm e a faixa numérica aceita pelo FX nas
unidades do objeto, sem confundir essa faixa com o curso físico da máquina.
Um ponto de cada ferramenta/grupo ilustra XY solicitado → arquivo, inclusive
em IN. O perfil não programa avanço. Coordenada amostrada fora da faixa ou
altura Roland que arredonda a zero bloqueia a revisão; a geração continua
recusando os demais vértices inválidos e caminhos HPGL que colapsam.

`NativeOutputPrecision` consulta os mesmos codecs de saída. Roland reproduz
também a formatação intermediária de quatro casas antes da conversão RML;
não usa apenas um arredondamento ideal do valor original. Nenhum gerador,
tolerância do comparador ou arquivo original foi modificado nesta etapa.

Para manter o custo da revisão limitado, **os exemplos não são uma varredura
de todos os vértices nem a primeira ordem do programa**. Geometry consulta um
ponto por grupo; Drilling reaproveita a passagem que já conta furos/rasgos.
A validação integral das coordenadas permanece no gerador em worker. Uma
revisão de programa já existente não inventa os parâmetros solicitados que
não constam do texto.

Passo textual Roland de 0,0025 mm não comprova resolução mecânica. O
[manual oficial MDX-20](https://downloadcenter.rolanddg.com/contents/manuals/MDX-20_USE_EN_R7.pdf)
publica resolução de software 0,025 mm/passo e mecânica 0,00625 mm/passo;
não foi feita certificação de coordenadas fracionárias em hardware. A unidade
de plotter de 0,025 mm/40 por mm/1016 por polegada é descrita na
[referência oficial HP](https://www.hp.com/ctg/Manual/bpl13211.pdf), seção 17-20.
Essa referência não certifica todas as variantes de controladores HPGL.

Testes `NativeOutputPrecisionTest` comparam exemplos com as ordens realmente
emitidas, incluindo arredondamento intermediário e limites MM/IN.
`CncNativePrecisionTest` verifica Geometry, Drilling com Offset Z, rápidos,
End Z, avisos, amostras e bloqueios. Os testes existentes de `CncPreflightTest`
também passaram. Log: `target/three-steps-precision-final-20261024.log`.

Verify integral final da série: 1867 aprovações Java/18 skips opcionais,
zero falhas/erros. Log `target/three-steps-full-final-20261024.log`.
Alterações sobre `c52029c1`, commitadas em `79f2aedd`, sem push.
