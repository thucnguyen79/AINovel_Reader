"""Compact structural report of saved book + chapter pages for one host (for hand-writing rules)."""
import json, os, re, sys, collections
from urllib.parse import urljoin, urlparse
from bs4 import BeautifulSoup

sys.stdout.reconfigure(encoding='utf-8')
HERE = os.environ.get("SURVEY_DIR") or os.path.dirname(os.path.abspath(__file__))  # SURVEY_DIR keeps html/ and *.json out of the repo
OUT = os.path.join(HERE, 'html')
host = sys.argv[1]
probe = {p['host']: p for p in json.load(open(os.path.join(HERE, 'probe.json'), encoding='utf-8'))}[host]

def sel(el):
    if el is None or el.name in (None, '[document]'): return ''
    i = el.get('id'); c = [x for x in (el.get('class') or []) if re.fullmatch(r'[A-Za-z_][\w-]*', x)]
    s = el.name
    if i and re.fullmatch(r'[A-Za-z_][\w-]*', i): s += '#' + i
    elif c: s += '.' + '.'.join(c[:2])
    return s

def chain(el, n=4):
    out = []; cur = el
    while cur is not None and len(out) < n and cur.name not in ('[document]', 'html'):
        out.append(sel(cur)); cur = cur.parent
    return ' < '.join(out)

print('=====', host, '| book:', probe.get('book_url'), '| chapter:', probe.get('chapter_url'))
bp = os.path.join(OUT, host + '__book.html'); cp = os.path.join(OUT, host + '__chapter.html')
if os.path.exists(bp):
    s = BeautifulSoup(open(bp, encoding='utf-8').read(), 'lxml')
    for t in s(['script', 'style', 'noscript']): t.decompose()
    print('--- BOOK headings:', [ (sel(h), h.get_text(' ', strip=True)[:50]) for h in s.find_all(['h1', 'h2', 'h3'])[:6] ])
    print('--- author cands:', [ (chain(a, 2), a.get_text(' ', strip=True)[:30], (a.get('href') or '')[:50]) for a in s.find_all(['a', 'span', 'div'], attrs={'itemprop': 'author'})[:3] + s.find_all('a', href=re.compile(r'tac-gia|tacgia|author|/tg/', re.I))[:3] ])
    print('--- label rows:', [ (chain(x, 2), x.get_text(' ', strip=True)[:60]) for x in s.find_all(string=re.compile(r'Tác giả|Trạng thái|Thể loại|Tình trạng|Tác Giả|Author|作者|状态', re.I))[:6] if x.parent is not None for x in [x.parent] ])
    imgs = [ (chain(i, 2), (i.get('src') or i.get('data-src') or '')[:70]) for i in s.find_all('img') if re.search(r'upload|cover|thumb|poster|book|truyen|story|/img', (i.get('src') or '') + (i.get('data-src') or ''), re.I) ]
    print('--- cover cands:', imgs[:3])
    descs = []
    for el in s.find_all(['div', 'p', 'section', 'article']):
        cls = ' '.join(el.get('class') or []) + ' ' + (el.get('id') or '')
        if re.search(r'desc|intro|summary|gioi-thieu|noi-dung|synopsis|tom-tat|detail-content|story-content', cls, re.I):
            t = el.get_text(' ', strip=True)
            if 60 < len(t) < 8000: descs.append((chain(el, 2), len(t), t[:50]))
    print('--- desc cands:', descs[:4])
    # TOC: group chapter-like links
    base = probe.get('book_url') or ''
    groups = collections.defaultdict(list)
    for a in s.find_all('a', href=True):
        u = urljoin(base, a['href']); p = urlparse(u).path
        if re.search(r'chuong|chapter|/chap|/\d+\.html$|/\d+/?$|-\d+\.html$|/doc-|/read/|/c\d+', p, re.I): groups[chain(a.parent, 3)].append((u, a.get_text(' ', strip=True)[:40]))
    for k, v in sorted(groups.items(), key=lambda kv: -len(kv[1]))[:4]:
        print(f'--- TOC group ({len(v)}): parent chain = {k}'); print('      first:', v[0]); print('      last :', v[-1])
    pag = [ (a.get('href'), a.get_text(' ', strip=True)[:15], chain(a, 2)) for a in s.find_all('a', href=re.compile(r'[?&](page|p|trang)=\d+|/(trang|page)[-/]\d+', re.I)) ]
    print('--- pagination links:', pag[:3], '... total', len(pag))
    print('--- inputs:', [ (i.get('id'), i.get('name'), (i.get('value') or '')[:20]) for i in s.find_all('input') if re.search(r'page|total|book|story|truyen|id', (i.get('id') or '') + (i.get('name') or ''), re.I) ][:6])
    print('--- selects:', [ (sel(x), len(x.find_all('option'))) for x in s.find_all('select') ][:4])
    print('--- ajax hints:', sorted(set(re.findall(r'(?:ajax|api)[\w/-]*[?/][\w?=&/-]{0,40}', open(bp, encoding='utf-8').read())))[:6])
if os.path.exists(cp):
    s = BeautifulSoup(open(cp, encoding='utf-8').read(), 'lxml')
    for t in s(['script', 'style', 'noscript']): t.decompose()
    print('--- CHAPTER headings:', [ (chain(h, 2), h.get_text(' ', strip=True)[:50]) for h in s.find_all(['h1', 'h2', 'h3'])[:6] ])
    cands = []
    for el in s.find_all(['div', 'article', 'section', 'main']):
        if not sel(el) or sel(el) == el.name: continue
        ps = [p for p in el.get_text('\n', strip=True).split('\n') if len(p) > 20]
        if len(ps) >= 4: cands.append((sum(len(p) for p in ps), len(ps), chain(el, 3), ps[0][:40]))
    cands.sort(key=lambda x: -x[0])
    seen = set(); shown = 0
    for total, n, ch, first in cands:
        key = ch.split(' < ')[0]
        if key in seen: continue
        seen.add(key); print(f'--- content cand: chars={total} paras={n} {ch} | {first}'); shown += 1
        if shown >= 5: break
    nav = [ (a.get_text(' ', strip=True)[:20], chain(a, 2), (a.get('href') or '')[:60]) for a in s.find_all('a') if re.search(r'chương trước|chương sau|trước|sau|tiếp|prev|next|上一章|下一章', a.get_text(' ', strip=True), re.I) or re.search(r'prev|next', (' '.join(a.get('class') or [])) + (a.get('id') or ''), re.I) ]
    print('--- nav:', nav[:6])
    print('--- removable in content:', sorted({ sel(x) for x in s.select('div, p, span') if re.search(r'ads|banner|quang-cao|adsbygoogle|box-notice|notice|alert', ' '.join(x.get('class') or []) + ' ' + (x.get('id') or ''), re.I) })[:8])
