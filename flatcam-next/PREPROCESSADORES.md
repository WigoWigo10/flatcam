# Preprocessadores de G-code no FlatCAM FX

Primeira etapa da portabilidade dos preprocessadores do FlatCAM Python. A escolha
fica nas telas **Drilling Tool** e **Geometry → CNC Job**. O perfil padrão é
**FX portable (atual)**, que mantém a saída anterior sem alterações.

| Perfil no FX | Referência Python | Diferenças relevantes |
| --- | --- | --- |
| FX portable (atual) | Gerador anterior do FX | `G0/G1`, `M3/M5`; pausa `M0` entre ferramentas |
| default (Mach3, M6) | `preprocessors/default.py` | `G00/G01`, `M03/M05`, `Tn`, `M6`, `M0` |
| Default_no_M6 (Mach3) | `preprocessors/Default_no_M6.py` | Como acima, sem `M6` |
| grbl_11 (M6) | `preprocessors/grbl_11.py` | Como `default`, com `G17` |
| GRBL_11_no_M6 | `preprocessors/GRBL_11_no_M6.py` | Como `grbl_11`, sem `M6` |

Os perfis Python são código executável; o FX porta explicitamente os comandos
essenciais de fresagem, **não** executa os módulos Python nem reproduz seus
cabeçalhos completos. `Tn` identifica as ferramentas pela ordem do objeto de
Geometry ou pelo número do Excellon. Os perfis de laser, Marlin, Roland, HPGL,
dispensador de pasta e outros, bem como as telas de isolamento/cutout que ainda
não oferecem escolha de perfil, continuam pendentes.

**Antes de enviar a uma máquina:** confira unidades, alturas Z, ordem de
ferramentas, comportamento de `M0` e suporte de `M6` no controlador. Faça uma
simulação e um teste a seco. O nome `grbl_11` reproduz o perfil do Python,
mas não garante que todo firmware GRBL aceite `M6`.
