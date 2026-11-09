"""Create a local gallery/catalog from the actual FX parser output, not upstream screenshots."""
import argparse
import hashlib
import json
from pathlib import Path


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[1]/'samples/community')
    args=p.parse_args();root=args.root.resolve()
    inventory=json.loads((root/'inventory.json').read_text(encoding='utf-8'))
    validation=json.loads((root/'validation/fx-import.json').read_text(encoding='utf-8'))
    parsed={b['id']:b for b in validation['boards']}
    checks={}
    for board in inventory['boards']:
        for file in board['downloads']+board['fabricationFiles']:
            path=file.get('localPath',file.get('path'))
            actual=hashlib.sha256((root/path).read_bytes()).hexdigest()
            if actual!=file['sha256']:raise ValueError('Changed corpus file: '+path)
            checks[path]=actual
    (root/'SHA256SUMS.txt').write_text(''.join(f'{h}  {path}\n' for path,h in sorted(checks.items())),encoding='utf-8')

    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    from matplotlib.patches import PathPatch
    from matplotlib.path import Path as MplPath
    from shapely import wkt,affinity
    from shapely.geometry.polygon import orient
    from compare_cam_python import leaves,lines
    fig,axes=plt.subplots(4,2,figsize=(13,15),layout='constrained')
    fig.suptitle('Placas da comunidade • coleção local para testes',fontsize=20,fontweight='bold')
    def polygon(ax,geometry,color):
        for poly in leaves(geometry):
            if poly.geom_type!='Polygon':continue
            poly=orient(poly,sign=1)
            vertices,codes=[],[]
            for ring in [poly.exterior,*poly.interiors]:
                pts=list(ring.coords);vertices.extend(pts)
                codes.extend([MplPath.MOVETO]+[MplPath.LINETO]*(len(pts)-2)+[MplPath.CLOSEPOLY])
            ax.add_patch(PathPatch(MplPath(vertices,codes),facecolor=color,edgecolor='none'))
    for ax,board in zip(axes.flat,inventory['boards']):
        result=parsed[board['id']];preview=result.get('preview')
        ax.set_facecolor('#10151b');ax.set_aspect('equal');ax.tick_params(labelsize=8)
        ax.grid(color='#28333d',alpha=.4,linewidth=.5)
        if preview:
            scale=25.4 if preview['units']=='IN' else 1
            copper=affinity.scale(wkt.loads(preview['wkt']),xfact=scale,yfact=scale,origin=(0,0))
            polygon(ax,copper,'#a9cb58')
            for layer in result['drillLayers']:
                factor=25.4 if layer['units']=='IN' else 1
                polygon(ax,affinity.scale(wkt.loads(layer['wkt']),xfact=factor,yfact=factor,origin=(0,0)),'#10151b')
            if 'outlineWkt' in result:
                outline=affinity.scale(wkt.loads(result['outlineWkt']),xfact=scale,yfact=scale,origin=(0,0))
                for line in lines(outline):
                    x,y=line.xy;ax.plot(x,y,color='#67c7ed',linewidth=.65)
            x1,y1,x2,y2=copper.bounds;pad=max(x2-x1,y2-y1)*.04
            ax.set_xlim(x1-pad,x2+pad);ax.set_ylim(y1-pad,y2+pad)
            name=Path(preview['path']).name
            if len(name)>46:name=name[:43]+'…'
            ax.set_title(board['name']+'\n'+name,loc='left',fontsize=10)
            ax.set_xlabel(f'{(x2-x1):.1f} × {(y2-y1):.1f} mm • {result["totalDrills"]} furos • {result["totalSlots"]} slots',fontsize=9)
        else:ax.text(.5,.5,'Importação pendente',transform=ax.transAxes,ha='center')
    fig.supxlabel('Geometria importada pelos parsers do FX; cada placa tem escala própria.\nVerde: cobre de uma face • Azul: contorno • Furos sobrepostos dos arquivos Excellon disponíveis.\nNão é uma validação elétrica, de fabricação ou de usinagem CNC.',fontsize=10)
    fig.savefig(root/'gallery.png',dpi=150,facecolor='white');plt.close(fig)

    raw_bytes=sum(f['bytes'] for b in inventory['boards'] for f in b['downloads'])
    cam_count=sum(len(b['fabricationFiles']) for b in inventory['boards'])
    rows=[]
    for board in inventory['boards']:
        result=parsed[board['id']]
        notes='Sem drill publicado' if board['id']=='single-sided-5v' else f'{result["totalDrills"]} furos / {result["totalSlots"]} slots'
        rows.append(f'| [{board["name"]}](boards/{board["id"]}/fabrication/) | {board["category"]} | {notes} | [{board["license"]}]({board["url"]}/tree/{board["commit"]}) |')
    readme=f'''# Placas da comunidade para testes do FlatCAM FX

Coleção local de **{len(inventory['boards'])} pacotes / 7 projetos upstream**, consultados em
2026-11-09. {cam_count} arquivos de fabricação extraídos/copiados e
{raw_bytes/1024/1024:.2f} MiB de downloads originais (incluindo CAD/documentação).
Os originais e os Gerbers não foram modificados. O catálogo não afirma que os
projetos sejam eletricamente corretos ou que possam ser fresados com qualquer fresa.

![Galeria real da importação](gallery.png)

| Pacote local | Tipo / cobertura de teste | Furação disponível | Licença declarada na origem |
|---|---|---|---|
'''+ '\n'.join(rows)+f'''

## Abrir no FX ou no Python

1. Entre em `boards/<id>/fabrication/` pelo link da tabela. Abra os Gerbers de cobre
   (`F_Cu`, `B_Cu`, `CuTop`, `CuBottom`, `.gtl` ou `.gbl`). Alguns pacotes têm uma subpasta.
2. Abra o contorno (`Edge_Cuts`, `Edge.Cuts`, `EdgeCuts`, `.gm1` ou `.gko`) e os `.drl`
   correspondentes. Arquivos `drl_map.gbr` são desenhos do mapa, não furação Excellon.
3. Confira unidades e alinhamento; salve projetos derivados em outra pasta de trabalho.
   Para Lines, comece com uma face por vez e compare Contour/Connect separadamente.

O primeiro ensaio dos **parsers de produção FX** processou {validation['parsed']} arquivos
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
.\\target\\oracle-py311\\Scripts\\python.exe tools/render_community_boards.py
```

O downloader verifica cada blob do commit, extrai somente arquivos CAM e recusa sobrescrever
um arquivo local alterado. O gerador da galeria reconfere todos os hashes antes de renderizar.
Os arquivos continuam sujeitos às licenças dos respectivos autores; não herdam a licença do FX.
'''
    (root/'README.md').write_text(readme,encoding='utf-8')
    print(json.dumps({'boards':len(inventory['boards']),'fabricationFiles':cam_count,'fxParsed':validation['parsed'],
                      'fxErrors':validation['errors'],'verifiedLocalHashes':len(checks),'originalMiB':raw_bytes/1024/1024,'gallery':str(root/'gallery.png')},ensure_ascii=False))


if __name__=='__main__':main()
