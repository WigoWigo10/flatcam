"""Fetch the pinned public PCB corpus without executing any upstream code.

Preserves original files, verifies Git blob SHA-1 and records SHA-256. Data stays
local under samples/community/boards; the small source manifest is versionable.
"""
import argparse
import concurrent.futures
import hashlib
import io
import json
import re
import urllib.parse
import urllib.request
import zipfile
from datetime import datetime, timezone
from pathlib import Path, PurePosixPath


def safe_path(root, relative):
    path = PurePosixPath(relative)
    if path.is_absolute() or any(p in (".", "..") or ':' in p or '\\' in p for p in path.parts):
        raise ValueError(f"Unsafe path: {relative}")
    result = root.joinpath(*path.parts).resolve()
    if not result.is_relative_to(root.resolve()):
        raise ValueError(f"Path escaped corpus: {relative}")
    return result


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def write_exact(path, data):
    if path.exists():
        if path.read_bytes() != data:
            raise ValueError(f"Refusing to overwrite a changed local file: {path}")
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)


def fetch(board, entry, root):
    repo, commit = board['repo'], board['commit']
    if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', repo) or not re.fullmatch(r'[a-f0-9]{40}', commit):
        raise ValueError('Expected a pinned public GitHub repository')
    url = f"https://raw.githubusercontent.com/{repo}/{commit}/" + urllib.parse.quote(entry['path'], safe='/')
    local = safe_path(root, board['id']+'/upstream/'+entry['path'])
    if local.exists():
        data = local.read_bytes()
    else:
        request = urllib.request.Request(url, headers={'User-Agent':'FlatCAM-FX-community-corpus/1.0'})
        with urllib.request.urlopen(request, timeout=30) as response:
            data = response.read(10*1024*1024+1)
    digest = hashlib.sha1(f'blob {len(data)}\0'.encode('ascii')+data).hexdigest()
    if len(data) != entry['bytes'] or digest != entry['gitBlobSha']:
        raise ValueError(f"Pinned blob mismatch: {repo}/{entry['path']}")
    write_exact(local, data)
    return dict(entry, url=url, sha256=sha256(data), localPath=str(local.relative_to(root.parent)).replace('\\','/'))


def fabrication_file(name, data):
    suffix = PurePosixPath(name).suffix.lower()
    if suffix in {'.gbr','.ger','.gerber','.gtl','.gbl','.gts','.gbs','.gto','.gbo','.gtp','.gbp',
                  '.gm1','.gko','.gml','.gpi','.gpb','.drl','.xln','.exc','.gbrjob'}:
        return True
    if suffix in {'','.txt','.tap'}:
        text = data[:4096].upper()
        return b'M48' in text or b'%FS' in text or b'%MO' in text
    return bool(re.fullmatch(r'\.g[0-9]+',suffix))


def prepare(board, records, corpus):
    target = safe_path(corpus, board['id']+'/fabrication')
    exported, skipped = [], []
    for record in records:
        if record['role'] != 'fabrication':
            continue
        origin = corpus.parent/record['localPath']
        if origin.suffix.lower() == '.zip':
            archive_input = origin
            member = board.get('fabricationArchiveMember')
            if member:
                with zipfile.ZipFile(origin) as outer:
                    if outer.getinfo(member).file_size>100*1024*1024:
                        raise ValueError('Unexpectedly large nested archive')
                    archive_input = io.BytesIO(outer.read(member))
            with zipfile.ZipFile(archive_input) as archive:
                entries = archive.infolist()
                if len(entries)>5000 or sum(e.file_size for e in entries)>100*1024*1024:
                    raise ValueError('Unexpectedly large fabrication archive')
                for entry in entries:
                    if entry.is_dir():
                        continue
                    output = safe_path(target, entry.filename)
                    data = archive.read(entry)
                    if not fabrication_file(entry.filename,data):
                        skipped.append(entry.filename)
                        continue
                    # Some supplier packages name Gerbers tl/bl/ko and drills drl,
                    # with no extension. Preserve the original ZIP and add only a
                    # chooser-friendly suffix to the extracted copy; bytes stay exact.
                    if not PurePosixPath(entry.filename).suffix:
                        suffix = '.drl' if b'M48' in data[:4096].upper() else '.gbr'
                        output = safe_path(target,entry.filename+suffix)
                    write_exact(output,data)
                    exported.append({'path':str(output.relative_to(corpus.parent)).replace('\\','/'),
                                     'bytes':len(data),'sha256':sha256(data),'source':record['path'],
                                     'archiveEntry':(member+'!' if member else '')+entry.filename})
        else:
            output = safe_path(target,origin.name)
            data = origin.read_bytes()
            if fabrication_file(origin.name,data):
                write_exact(output,data)
                exported.append({'path':str(output.relative_to(corpus.parent)).replace('\\','/'),
                                 'bytes':len(data),'sha256':sha256(data),'source':record['path']})
    if not exported:
        raise ValueError('No manufacturing files found')
    notice = f"# {board['name']}\n\nFonte: {board['url']}\n\nCommit: `{board['commit']}`\n\n"
    notice += f"Licença declarada pelo upstream: **{board['license']}** ({board['licenseBasis']}).\n\n"
    notice += board.get('licenseNote','')+'\n\n'
    notice += 'Arquivos originais em `upstream/`; cópias de fabricação em `fabrication/`. Nenhum código upstream foi executado.\n'
    notice += '\n'.join('- '+n for n in board.get('notes',[]))+'\n'
    write_exact(safe_path(corpus,board['id']+'/ORIGEM.md'),notice.encode('utf-8'))
    return exported, skipped


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--catalog',type=Path,default=Path(__file__).resolve().parents[1]/'samples/community/sources.json')
    args = parser.parse_args()
    config=json.loads(args.catalog.read_text(encoding='utf-8'))
    if config['schema']!=1:raise ValueError('Unsupported source catalog')
    corpus=args.catalog.resolve().parent/'boards'
    corpus.mkdir(parents=True,exist_ok=True)
    boards=[]
    for board in config['boards']:
        with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
            records=list(pool.map(lambda entry:fetch(board,entry,corpus),board['files']))
        exported,skipped=prepare(board,records,corpus)
        result=dict(board,downloads=records,fabricationFiles=exported,archiveEntriesSkipped=skipped)
        boards.append(result)
        print(json.dumps({'id':board['id'],'downloaded':len(records),'fabricationFiles':len(exported),
                          'bytes':sum(r['bytes'] for r in records)},ensure_ascii=False),flush=True)
    inventory={'schema':1,'fetchedAt':datetime.now(timezone.utc).isoformat(),'boards':boards}
    (corpus.parent/'inventory.json').write_text(json.dumps(inventory,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print('Saved inventory: '+str(corpus.parent/'inventory.json'),flush=True)


if __name__=='__main__':main()
