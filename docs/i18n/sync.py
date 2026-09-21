"""Копирует docs/i18n/strings.json в Android (assets/i18n.json) и iOS (Resources/i18n.json)
и проверяет, что все S("…")/L("…") ключи из кода есть в таблице (кроме голосовых шаблонов).

    python docs/i18n/sync.py
"""
import json, re, os, glob, shutil

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SRC = os.path.join(ROOT, 'docs', 'i18n', 'strings.json')
DST = [
    os.path.join(ROOT, 'android', 'app', 'src', 'main', 'assets', 'i18n.json'),
    os.path.join(ROOT, 'ElectroRemote', 'Resources', 'i18n.json'),
]
table = json.load(open(SRC, encoding='utf-8'))
out = {k: v for k, v in table.items() if not k.startswith('_')}
for d in DST:
    os.makedirs(os.path.dirname(d), exist_ok=True)
    json.dump(out, open(d, 'w', encoding='utf-8'), ensure_ascii=False, separators=(',', ':'))
    print('->', os.path.relpath(d, ROOT), os.path.getsize(d), 'bytes')

# ключи из кода
keys = set()
pat = re.compile(r'(?<![A-Za-z])[SL]\("((?:[^"\\]|\\.)*)"')
for p in glob.glob(ROOT + '/android/app/src/main/java/**/*.kt', recursive=True) + glob.glob(ROOT + '/ElectroRemote/**/*.swift', recursive=True):
    s = open(p, encoding='utf-8', errors='ignore').read()
    for m in pat.finditer(s):
        k = m.group(1).replace('\\"', '"')
        if re.search('[А-Яа-яЁё]', k) and 'VoiceScreen' not in p: keys.add(k)
for lang, t in out.items():
    missing = sorted(k for k in keys if k not in t)
    extra = sorted(k for k in t if k not in keys)
    print(f'[{lang}] переводов {len(t)}, нет перевода: {len(missing)}, лишних: {len(extra)}')
    for k in missing: print('   MISSING:', k)
