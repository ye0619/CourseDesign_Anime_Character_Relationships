# -*- coding: utf-8 -*-
"""Read two official ZIP streams; retain only characters already in this project."""
import collections
import datetime
import html
import json
from pathlib import Path
import re
import sys
import zipfile

ROOT = Path(__file__).resolve().parent.parent


def save(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def main(archive_path):
    source = json.loads((ROOT / 'data/normalized/bangumi-sample.json').read_text(encoding='utf-8-sig'))
    nodes = {str(node['sourceId']): node for node in source['entities'] if node['type'] == 'Character'}
    appearances = collections.defaultdict(set)
    for edge in source['relations']:
        if edge['type'] == 'APPEARS_IN':
            appearances[str(edge['from']['sourceId'])].add(str(edge['to']['sourceId']))
    definitions = json.loads((ROOT / 'backend/src/main/resources/fixtures/character-relation-types.json').read_text(encoding='utf-8'))
    types = {item['bangumiId']: item for item in definitions if not item['legacy']}
    api_types = {}
    for path in (ROOT / 'data/raw/bangumi').glob('v0-subjects-*-characters.json'):
        for node in json.loads(path.read_text(encoding='utf-8-sig')):
            api_types[str(node['id'])] = node.get('type', 1)
    snapshot = Path(archive_path).name
    source_url = 'https://github.com/bangumi/Archive/releases/download/archive/' + snapshot
    metadata, relations, skipped_types = [], {}, collections.Counter()
    verification_pairs = []
    with zipfile.ZipFile(archive_path) as archive:
        names = {Path(name).name.lower().replace('_', '-'): name for name in archive.namelist()}
        character_entry = next((original for name, original in names.items() if name in ('character.jsonlines', 'characters.jsonlines', 'character.jsonl')), None)
        relation_entry = next((original for name, original in names.items() if name in ('person-relations.jsonlines', 'person-relation.jsonlines', 'person-relations.jsonl')), None)
        if not character_entry or not relation_entry:
            raise ValueError('Expected character and person-relations streams; ZIP entries: ' + ', '.join(names))
        with archive.open(character_entry) as stream:
            for line in stream:
                record = json.loads(line)
                identity = str(record['id'])
                if identity not in nodes:
                    continue
                wiki = html.unescape(record.get('infobox') or '')
                chinese = re.search(r'^\s*\|\s*简体中文名\s*=\s*([^\r\n]*)', wiki, re.M)
                chinese_name = chinese.group(1).strip() if chinese else ''
                # Complex wiki values and placeholder names require manual review.
                if any(token in chinese_name for token in ('{{', '}}', '<', '>', '|')) or chinese_name in ('无', '暂无', '未知'):
                    chinese_name = ''
                node = nodes[identity]
                enriched = dict(node, characterType=api_types.get(identity, 1))
                if record.get('summary') and len(record['summary']) <= 20000:
                    enriched['summary'] = record['summary']
                if chinese_name:
                    enriched.update(nameCn=chinese_name, nameCnSource=f'https://bgm.tv/character/{identity}')
                metadata.append(enriched)
        with archive.open(relation_entry) as stream:
            for line in stream:
                record = json.loads(line)
                if record.get('person_type') != 'crt':
                    continue
                owner, related = str(record['person_id']), str(record['related_person_id'])
                if owner not in nodes or related not in nodes or owner == related:
                    continue
                category = int(record['relation_type'])
                if owner in ('86246', '86247', '89180') and related in ('86246', '86247', '89180'):
                    verification_pairs.append(record)
                if category not in types:
                    skipped_types[category] += 1
                    continue
                definition = types[category]
                # Archive relation labels describe the associated character's role
                # relative to the owner (verified against Frieren's teacher/student).
                # CRUSH is explicitly unilateral: owner admires the related character.
                start, end = (owner, related) if definition['code'] == 'CRUSH' else (related, owner)
                code = definition['code']
                # Store inverse pairs once using one canonical semantic direction.
                inverse_to_canonical = {'STUDENT': 'TEACHER', 'SUBORDINATE': 'SUPERIOR', 'SERVANT': 'MASTER', 'PET': 'OWNER', 'PROTOTYPE': 'ADAPTATION', 'PREDECESSOR': 'EVOLUTION'}
                if code in inverse_to_canonical:
                    code = inverse_to_canonical[code]
                    start, end = end, start
                canonical = next(item for item in definitions if item['code'] == code)
                if not canonical['directed']:
                    start, end = sorted((start, end))
                for work in sorted(appearances[owner] & appearances[related]):
                    identity = f'{work}:{code}:{start}:{end}'
                    props = {'animeUid': f'anime:bangumi:{work}', 'relationCode': code, 'label': canonical['label'], 'sourceRelationId': canonical['bangumiId'], 'spoiler': bool(record.get('spoiler')), 'ended': bool(record.get('ended')), 'sourceName': 'Bangumi 社区角色关联 · 官方快照', 'sourceUrl': f'https://bgm.tv/character/{owner}', 'evidence': f'Bangumi角色页 {owner} 的关联角色 {related}；快照 {snapshot}。角色全局关联，仅投影至双方均出场的作品，不代表本季剧情时点。', 'description': f'{nodes[start].get("nameCn") or nodes[start]["name"]} → {nodes[end].get("nameCn") or nodes[end]["name"]}；具体方向见关系说明。'}
                    if identity in relations:
                        # Do not hide spoilers or an ended flag when two directions disagree.
                        props['spoiler'] |= relations[identity]['properties']['spoiler']
                        props['ended'] |= relations[identity]['properties']['ended']
                    relations[identity] = {'type': 'CHARACTER_RELATION', 'fromUid': f'character:bangumi:{start}', 'toUid': f'character:bangumi:{end}', 'properties': props}
    if not metadata:
        raise ValueError('No project characters found in snapshot')
    # Direction verification must match known site evidence before importing.
    teacher_rows = [r for r in verification_pairs if str(r['person_id']) == '86246' and str(r['related_person_id']) == '89180']
    if not teacher_rows or int(teacher_rows[0]['relation_type']) != 2012:
        raise ValueError('Archive relation direction differs from verified site teacher example; review before import')
    save(ROOT / 'data/normalized/bangumi-character-enrichment.json', {'schemaVersion': 1, 'source': 'bangumi', 'entities': metadata, 'relations': list(relations.values())})
    report = {'snapshot': snapshot, 'sourceUrl': source_url, 'checkedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'characters': len(metadata), 'verifiedChineseNames': sum(bool(n.get('nameCn')) for n in metadata), 'scopedRelations': len(relations), 'relationCodes': dict(collections.Counter(r['properties']['relationCode'] for r in relations.values())), 'unknownCategoryCounts': dict(skipped_types), 'directionVerification': verification_pairs}
    save(ROOT / 'data/samples/bangumi-character-enrichment-verification.json', report)
    print(json.dumps({key: report[key] for key in ('snapshot', 'characters', 'verifiedChineseNames', 'scopedRelations', 'relationCodes', 'unknownCategoryCounts')}, ensure_ascii=False))


if __name__ == '__main__':
    main(sys.argv[1])
