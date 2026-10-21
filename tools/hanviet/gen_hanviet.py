"""Build assets/dicts/hanviet.txt: Thieu Chuu > phienam (community) > Unihan kVietnamese > traditional-variant fallback.
Usage: gen_hanviet.py [build|missing]"""
import zipfile, os, sys, re, io, collections
sys.stdout.reconfigure(encoding='utf-8')
here = os.path.dirname(os.path.abspath(__file__))
DL = os.environ.get('HANVIET_DL', os.path.join(here, 'dl_hanviet'))
tmp = os.environ.get('TMP') or os.environ.get('TEMP')
z = zipfile.ZipFile(os.path.join(tmp, 'unihan', 'Unihan.zip'))
rd = z.read('Unihan_Readings.txt').decode('utf-8')
var = z.read('Unihan_Variants.txt').decode('utf-8')

HAN = re.compile(r'^[\u3400-\u4dbf\u4e00-\u9fff\U00020000-\U0002ffff]$')

def is_han(c):
    return bool(HAN.match(c))

# 1. Unihan kVietnamese
viet = {}
for line in rd.splitlines():
    if '\tkVietnamese\t' in line:
        cp, _, val = line.split('\t', 2)
        viet[chr(int(cp[2:], 16))] = val.split()
# variants: simplified -> traditional
trad = collections.defaultdict(list)
for line in var.splitlines():
    if '\tkTraditionalVariant\t' in line or '\tkSemanticVariant\t' in line or '\tkZVariant\t' in line:
        cp, key, val = line.split('\t', 2)
        for v in val.split():
            m = re.match(r'U\+([0-9A-F]+)', v)
            if m:
                trad[chr(int(cp[2:], 16))].append((key, chr(int(m.group(1), 16))))

# 2. Thieu Chuu (public domain): "字=âm1, âm2 [pinyin]\n..."
thieu = {}
for line in io.open(os.path.join(DL, 'thieuchuu.txt'), encoding='utf-8-sig'):
    if '=' not in line:
        continue
    k, v = line.rstrip('\n').split('=', 1)
    k = k.strip()
    if not (len(k) == 1 and is_han(k)):
        continue
    v = re.sub(r'\([^)]*\)', '', v.split('[', 1)[0])
    readings = [r.strip().lower() for r in v.split(',') if re.fullmatch(r'[^\W\d_]+', r.strip())]
    if readings and k not in thieu:
        thieu[k] = readings[0]

# 3. community phien am list "字=âm"
phienam = {}
for line in io.open(os.path.join(DL, 'phienam.txt'), encoding='utf-8-sig'):
    if '=' not in line:
        continue
    k, v = line.rstrip('\n').split('=', 1)
    k = k.strip(); v = v.strip().lower()
    if len(k) == 1 and is_han(k) and re.fullmatch(r'[^\W\d_]+( [^\W\d_]+)*', v) and k not in phienam:
        phienam[k] = v

overrides = {}
ov_path = os.path.join(here, 'hanviet_overrides.txt')
if os.path.exists(ov_path):
    for line in io.open(ov_path, encoding='utf-8'):
        line = line.strip()
        if line and not line.startswith('#') and '=' in line:
            k, v = line.split('=', 1)
            overrides[k] = v

out = {}
src = collections.Counter()
def put(c, reading, tag):
    if c not in out and reading:
        out[c] = reading
        src[tag] += 1

for c, r in thieu.items():
    put(c, r, 'thieuchuu')
for c, r in phienam.items():
    put(c, r, 'phienam')
for c, rs in viet.items():
    put(c, rs[0], 'unihan')
# simplified or variant forms without a reading: borrow the traditional form's reading
for c, vs in trad.items():
    if c in out:
        continue
    for key, t in sorted(vs, key=lambda kv: 0 if kv[0] == 'kTraditionalVariant' else 1):
        if t != c and t in out:
            put(c, out[t], 'variant')
            break
for k, v in overrides.items():
    out[k] = v
print('thieuchuu', len(thieu), 'phienam', len(phienam), 'unihan', len(viet), '->', dict(src), 'overrides', len(overrides), 'total', len(out))

mode = sys.argv[1] if len(sys.argv) > 1 else 'build'
if mode == 'missing':
    cnt = collections.Counter()
    root = 'D:/Projects/AINovel_Reader/data/source/src/test/resources/fixtures/'
    for sid, enc in (('jjwxc', 'gb18030'), ('52shuku', 'utf-8')):
        for name in ('chapter.html', 'book.html'):
            txt = io.open(root + sid + '/' + name, encoding=enc, errors='ignore').read()
            txt = re.sub(r'<script.*?</script>|<style.*?</style>|<[^>]+>', ' ', txt, flags=re.S)
            for ch in txt:
                if is_han(ch):
                    cnt[ch] += 1
    missing = [(c, n) for c, n in cnt.most_common() if c not in out]
    print('distinct han chars', len(cnt), 'missing', len(missing), 'missing occurrences', sum(n for _, n in missing), 'of', sum(cnt.values()))
    print(''.join(c for c, _ in missing[:400]))
    for c in '第说着这没来的了还开间无笑看眼死每百':
        print(c, out.get(c), '| thieu', thieu.get(c), '| phienam', phienam.get(c), '| unihan', viet.get(c))
    sys.exit()

target = 'D:/Projects/AINovel_Reader/data/translate/src/main/assets/dicts/hanviet.txt'
with io.open(target, 'w', encoding='utf-8', newline='\n') as f:
    for k in sorted(out):
        f.write(k + '=' + out[k] + '\n')
print('wrote', target, len(out))
