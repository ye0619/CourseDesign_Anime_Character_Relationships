"""Build a reusable subject-scoped index from an official ZIP, without extracting it.

Only crt rows are indexed. Original records remain intact: no inferred relations.
Output is private data, not a licensed redistribution of the official archive.
"""
import argparse
import collections
import datetime
import hashlib
import json
import os
from pathlib import Path
import zipfile


def build(archive, output, expected):
    digest = hashlib.sha256()
    with archive.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(chunk)
    revision = digest.hexdigest()
    if revision != expected.removeprefix('sha256:').lower():
        raise ValueError('Official checksum mismatch; existing index unchanged')
    target = output / revision
    target.mkdir(parents=True, exist_ok=True)
    members = collections.defaultdict(set)
    subjects = collections.defaultdict(set)
    counts = collections.Counter()
    relations = collections.defaultdict(list)
    required = ['subject', 'character', 'person', 'subject-characters', 'subject-persons', 'person-characters', 'person-relations']
    with zipfile.ZipFile(archive) as source:
        entries = {Path(name).name: name for name in source.namelist()}
        samples = {}
        for name in required:
            entry = entries[name + '.jsonlines']
            with source.open(entry) as stream:
                samples[name] = sorted(json.loads(stream.readline()).keys())
        with source.open(entries['subject-characters.jsonlines']) as stream:
            for line in stream:
                row = json.loads(line)
                subject, character = int(row['subject_id']), int(row['character_id'])
                members[character].add(subject)
                subjects[subject].add(character)
                counts['appearanceRows'] += 1
        with source.open(entries['person-relations.jsonlines']) as stream:
            for line in stream:
                row = json.loads(line)
                counts['personType:' + str(row.get('person_type'))] += 1
                if row.get('person_type') != 'crt':
                    continue
                owner, related = int(row['person_id']), int(row['related_person_id'])
                if owner == related:
                    counts['selfRowsSkipped'] += 1
                    continue
                for subject in sorted(members[owner] & members[related]):
                    relations[subject].append(row)
                    counts['scopedRawRecords'] += 1
    buckets = [dict() for _ in range(256)]
    for subject, characters in subjects.items():
        buckets[subject % 256][str(subject)] = {'characterIds': sorted(characters), 'records': relations.get(subject, [])}
    # Complete buckets replace staging files individually; CURRENT changes only after all succeed.
    for number, bucket in enumerate(buckets):
        path = target / (str(number) + '.json')
        temporary = path.with_suffix('.tmp')
        temporary.write_text(json.dumps(bucket, ensure_ascii=False, separators=(',', ':')), encoding='utf-8')
        os.replace(temporary, path)
    report = {'schemaVersion': 1, 'revision': revision, 'archive': archive.name,
              'sourceUrl': 'https://github.com/bangumi/Archive/releases/download/archive/' + archive.name,
              'importedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'streams': samples,
              'counts': dict(counts), 'subjects': len(subjects), 'subjectsWithRelations': len(relations),
              'scope': 'Global character relations projected only where both characters appear; not season-specific evidence',
              'license': 'No explicit archive-data redistribution license confirmed; keep source and index private'}
    (target / 'manifest.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    pointer = output / 'CURRENT.tmp'
    pointer.write_text(revision, encoding='ascii')
    os.replace(pointer, output / 'CURRENT')
    print(json.dumps(report, ensure_ascii=False))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('archive', type=Path)
    parser.add_argument('--sha256', required=True)
    parser.add_argument('--output', type=Path, default=Path(__file__).resolve().parent.parent / 'data/index/bangumi')
    args = parser.parse_args()
    build(args.archive, args.output, args.sha256)
