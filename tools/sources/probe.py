"""Second pass: for static sites pick a book page and a chapter page, fetch them, derive candidate selectors.
Writes probe.json and saves html/<host>__book.html, html/<host>__chapter.html."""
import concurrent.futures, json, os, re, ssl, sys, urllib.request, urllib.error, gzip, io, collections
from urllib.parse import urljoin, urlparse
from bs4 import BeautifulSoup

sys.stdout.reconfigure(encoding='utf-8')
HERE = os.environ.get("SURVEY_DIR") or os.path.dirname(os.path.abspath(__file__))  # SURVEY_DIR keeps html/ and *.json out of the repo
OUT = os.path.join(HERE, 'html')
UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36'
ctx = ssl.create_default_context(); ctx.check_hostname = False; ctx.verify_mode = ssl.CERT_NONE

survey = json.load(open(os.path.join(HERE, 'survey.json'), encoding='utf-8'))
CANDIDATES = [s['host'] for s in survey if s['status'] == 200 and s['bytes'] > 20000 and not any(h in s['hints'] for h in ('nuxt/spa',)) and 'wordpress.com' not in s['host']]
SKIP = {'www.chivi.app', 'storya.click', 'dammy.me', 'tieuthuyetmang.com', 'dichngay.com', 'sangtacviet.xyz', 'noveltoon.vn', 'webnovel.vn', 'khoaitay.cc', 'davulacam.info'}
CANDIDATES = [h for h in CANDIDATES if h not in SKIP]

EXCLUDE = re.compile(r'chuong|chapter|/chap|page|/trang|the-loai|theloai|tac-gia|tacgia|danh-sach|tim-kiem|search|login|register|dang-nhap|dang-ky|/tag|category|/list|/top|lien-he|contact|policy|chinh-sach|huong-dan|rss|feed|wp-|/user|/author|/genre|/sort|/filter|/account|/profile|facebook|twitter|\.(jpg|jpeg|png|gif|css|js|xml)|javascript:|mailto:|#|/the-loai|/truyen-(moi|hot|full|hay)|/bang-xep-hang|/quan-ly|/lich-su|/tu-truyen|/theo-doi|/bookmark|/history|/donate|/nap|/vip|/forum|/blog|/news|/tin-tuc|/gioi-thieu|/about|/faq|/dieu-khoan|/terms|/privacy|/sitemap|/the/|/author/|/tacgia/|/truyen-tranh|/comic|/manga|/audio|/nghe-', re.I)
CHAPTER_HINT = re.compile(r'chuong|chapter|/chap|/\d+\.html$|/\d+/?$|/c\d+|-\d+\.html$|/doc-|/read/', re.I)
NAV_CLASS = re.compile(r'nav|menu|comment|footer|header|sidebar|ads|banner|share|social|breadcrumb|pagination|related|recommend|toolbar|modal|popup|login|rating|vote|tag', re.I)

def fetch(url):
    req = urllib.request.Request(url, headers={'User-Agent': UA, 'Accept': 'text/html,*/*;q=0.8', 'Accept-Language': 'vi,en;q=0.8,zh;q=0.6', 'Accept-Encoding': 'gzip', 'Referer': url.split('/', 3)[0] + '//' + urlparse(url).netloc + '/'})
    with urllib.request.urlopen(req, timeout=25, context=ctx) as r:
        raw = r.read()
        if r.headers.get('Content-Encoding') == 'gzip': raw = gzip.GzipFile(fileobj=io.BytesIO(raw)).read()
        ct = r.headers.get('Content-Type', '')
        m = re.search(r'charset=([\w-]+)', ct, re.I) or re.search(rb'charset=["\']?([\w-]+)', raw[:4000], re.I)
        enc = (m.group(1) if isinstance(m.group(1), str) else m.group(1).decode()) if m else 'utf-8'
        if enc.lower() in ('gb2312', 'gbk'): enc = 'gb18030'
        try: html = raw.decode(enc, 'replace')
        except LookupError: html = raw.decode('utf-8', 'replace')
        return html, r.geturl()

def same_host(u, host):
    h = urlparse(u).netloc.lower()
    base = host.lower().removeprefix('www.').removeprefix('m.')
    return h.endswith(base)

def template(path):
    segs = [s for s in path.split('/') if s]
    out = []
    for s in segs:
        if re.fullmatch(r'\d+(\.html?)?', s): out.append('{n}')
        elif re.search(r'\d', s) and re.search(r'[a-z]', s, re.I): out.append('{slug}')
        elif len(s) > 12 or '-' in s: out.append('{slug}')
        else: out.append(s)
    return '/' + '/'.join(out)

def pick_book_link(soup, base, host):
    cands = collections.defaultdict(list)
    for a in soup.find_all('a', href=True):
        u = urljoin(base, a['href'].strip())
        if not u.startswith('http') or not same_host(u, host): continue
        p = urlparse(u).path
        if p in ('', '/') or EXCLUDE.search(u) or CHAPTER_HINT.search(p): continue
        t = template(p)
        if '{slug}' not in t and '{n}' not in t: continue
        text = a.get_text(' ', strip=True)
        cands[t].append((u, text))
    best = None
    for t, items in cands.items():
        distinct = {u for u, _ in items}
        titled = [u for u, tx in items if 5 <= len(tx) <= 80]
        score = len(distinct) + len(titled) * 0.5
        if best is None or score > best[0]: best = (score, t, items)
    if not best: return None, None
    _, t, items = best
    for u, tx in items:
        if 5 <= len(tx) <= 80: return u, t
    return items[0][0], t

def css_for(el):
    if el is None: return None
    if el.get('id') and re.fullmatch(r'[A-Za-z_][\w-]*', el['id']): return f"{el.name}#{el['id']}"
    classes = [c for c in (el.get('class') or []) if re.fullmatch(r'[A-Za-z_][\w-]*', c) and not re.fullmatch(r'(col|row|container|wrapper|clearfix|mb-\d|mt-\d|p-\d|px-\d|py-\d|w-\d+|text-\w+|d-\w+|flex|grid|block|hidden|active|show|fade|in|open)', c)]
    if classes: return f"{el.name}.{classes[0]}"
    return None

def ancestor_selector(el, max_up=6):
    cur = el
    for _ in range(max_up):
        if cur is None or cur.name in ('body', 'html', '[document]'): return None
        sel = css_for(cur)
        if sel: return sel, cur
        cur = cur.parent
    return None

def lca(nodes):
    paths = []
    for n in nodes:
        p = []
        cur = n
        while cur is not None and cur.name not in ('[document]',):
            p.append(cur); cur = cur.parent
        paths.append(list(reversed(p)))
    common = None
    for i in range(min(len(p) for p in paths)):
        if all(p[i] is paths[0][i] for p in paths): common = paths[0][i]
        else: break
    return common

def analyze_book(soup, url, host):
    res = {}
    h1 = soup.find('h1') or soup.find('h3', class_=re.compile('title', re.I)) or soup.find('h2')
    res['title'] = h1.get_text(' ', strip=True)[:80] if h1 else None
    res['title_sel'] = css_for(h1) or (h1.name if h1 else None)
    a = soup.find('a', href=re.compile(r'tac-gia|tacgia|author', re.I)) or soup.find(attrs={'itemprop': 'author'})
    res['author'] = a.get_text(' ', strip=True)[:60] if a else None
    res['author_sel'] = ('a[href*=tac-gia]' if a and a.name == 'a' and 'tac-gia' in a.get('href', '') else css_for(a) or (a.name if a else None))
    img = soup.find('img', src=re.compile(r'upload|cover|thumb|poster|images?/|/img/', re.I))
    res['cover'] = (img.get('src') or img.get('data-src'))[:120] if img else None
    res['cover_sel'] = (ancestor_selector(img) or (None, None))[0] if img else None
    desc = None
    for el in soup.find_all(['div', 'p', 'section'], class_=re.compile(r'desc|intro|summary|gioi-thieu|noi-dung|synopsis|content', re.I)):
        t = el.get_text(' ', strip=True)
        if 80 < len(t) < 6000 and (desc is None or len(t) > len(desc[1])): desc = (el, t)
    res['desc_sel'] = css_for(desc[0]) if desc else None
    # chapters
    groups = collections.defaultdict(list)
    bookpath = urlparse(url).path.rstrip('/')
    for an in soup.find_all('a', href=True):
        u = urljoin(url, an['href'].strip())
        if not same_host(u, host): continue
        p = urlparse(u).path
        if p.rstrip('/') == bookpath or EXCLUDE.search(u) and not CHAPTER_HINT.search(p): continue
        if CHAPTER_HINT.search(p) or p.startswith(bookpath + '/'):
            groups[template(p)].append((an, u))
    if not groups: res['chapters'] = 0; return res
    t, items = max(groups.items(), key=lambda kv: len({u for _, u in kv[1]}))
    anchors = [an for an, _ in items]
    res['chapters'] = len({u for _, u in items})
    res['chapter_template'] = t
    res['first_chapter'] = items[0][1]
    res['first_chapter_title'] = anchors[0].get_text(' ', strip=True)[:60]
    res['last_chapter_title'] = anchors[-1].get_text(' ', strip=True)[:60]
    common = lca(anchors[:3] + anchors[-2:]) if len(anchors) >= 3 else lca(anchors)
    anc = ancestor_selector(common) if common is not None else None
    if anc:
        sel, node = anc
        inside = [x for x in node.find_all('a', href=True)]
        res['toc_container'] = sel
        res['toc_links_in_container'] = len(inside)
        # token common to chapter hrefs
        tok = None
        for cand in ('chuong', 'chapter', 'chap', '/doc/', '/read/'):
            if all(cand in (u or '') for _, u in items): tok = cand; break
        res['toc_href_token'] = tok
    # pagination
    pag = []
    for an in soup.find_all('a', href=True):
        u = an['href']
        m = re.search(r'(?:[?&](?:page|p|trang)=(\d+))|/(?:trang|page)[-/](\d+)', u)
        if m: pag.append(int(m.group(1) or m.group(2)))
    res['pagination_max'] = max(pag) if pag else None
    tp = soup.find('input', id=re.compile('total', re.I))
    res['total_page_input'] = tp.get('value') if tp else None
    sel_el = soup.find('select', class_=re.compile('chap|page', re.I)) or soup.find('select', id=re.compile('chap|page', re.I))
    res['select_pages'] = len(sel_el.find_all('option')) if sel_el else None
    return res

def analyze_chapter(soup, url):
    res = {}
    for s in soup(['script', 'style', 'noscript']): s.decompose()
    best = None
    for el in soup.find_all(['div', 'article', 'section', 'main']):
        cls = ' '.join(el.get('class') or []) + ' ' + (el.get('id') or '')
        if NAV_CLASS.search(cls): continue
        if not css_for(el): continue
        # own text via p/br children only (avoid wrappers)
        text = el.get_text('\n', strip=True)
        paras = [p for p in text.split('\n') if len(p) > 20]
        if len(paras) < 4: continue
        total = sum(len(p) for p in paras)
        if best is None or total > best[0] * 1.02: best = (total, el, len(paras))
    if not best: res['content_sel'] = None; return res
    total, el, n = best
    # go deeper while a single child holds >= 90% of text
    cur = el
    while True:
        kids = [k for k in cur.find_all(['div', 'article', 'section'], recursive=False) if css_for(k)]
        moved = False
        for k in kids:
            if len(k.get_text(strip=True)) >= 0.9 * len(cur.get_text(strip=True)):
                cls = ' '.join(k.get('class') or []) + ' ' + (k.get('id') or '')
                if not NAV_CLASS.search(cls): cur = k; moved = True; break
        if not moved: break
    res['content_sel'] = css_for(cur)
    res['content_paragraphs'] = n
    res['content_sample'] = cur.get_text(' ', strip=True)[:120]
    h = None
    for tag in soup.find_all(['h1', 'h2', 'h3']):
        t = tag.get_text(' ', strip=True)
        if re.search(r'chương|chapter|第|章|quyển|hồi', t, re.I) or re.search(r'\d', t):
            h = tag; break
    res['title_sel'] = css_for(h) or (h.name if h else None)
    res['title'] = h.get_text(' ', strip=True)[:80] if h else None
    nxt = soup.find('a', string=re.compile(r'chương sau|chương tiếp|tiếp|next|下一章|sau', re.I)) or soup.find('a', attrs={'rel': 'next'}) or soup.find('a', class_=re.compile('next', re.I)) or soup.find('a', id=re.compile('next', re.I))
    prv = soup.find('a', string=re.compile(r'chương trước|trước|prev|上一章', re.I)) or soup.find('a', attrs={'rel': 'prev'}) or soup.find('a', class_=re.compile('prev', re.I)) or soup.find('a', id=re.compile('prev', re.I))
    res['next_sel'] = css_for(nxt) if nxt else None
    res['next_text'] = nxt.get_text(' ', strip=True)[:30] if nxt else None
    res['prev_sel'] = css_for(prv) if prv else None
    res['prev_text'] = prv.get_text(' ', strip=True)[:30] if prv else None
    return res

def probe(host):
    out = {'host': host}
    try:
        home = open(os.path.join(OUT, host + '.html'), encoding='utf-8').read()
        hs = BeautifulSoup(home, 'lxml')
        base = next((s['final'] for s in survey if s['host'] == host), 'https://' + host + '/')
        book_url, t = pick_book_link(hs, base, host)
        out['book_template'] = t
        if not book_url: out['error'] = 'no book link'; return out
        out['book_url'] = book_url
        bhtml, bfinal = fetch(book_url)
        open(os.path.join(OUT, host + '__book.html'), 'w', encoding='utf-8').write(bhtml)
        bs = BeautifulSoup(bhtml, 'lxml')
        out['book'] = analyze_book(bs, bfinal, host)
        ch = out['book'].get('first_chapter')
        if ch:
            chtml, cfinal = fetch(ch)
            open(os.path.join(OUT, host + '__chapter.html'), 'w', encoding='utf-8').write(chtml)
            out['chapter_url'] = cfinal
            out['chapter'] = analyze_chapter(BeautifulSoup(chtml, 'lxml'), cfinal)
    except Exception as e:
        out['error'] = type(e).__name__ + ': ' + str(e)[:150]
    return out

results = []
with concurrent.futures.ThreadPoolExecutor(max_workers=8) as ex:
    for r in ex.map(probe, CANDIDATES):
        results.append(r)
        b = r.get('book', {}); c = r.get('chapter', {})
        print(f"{r['host']:<26} toc={b.get('chapters','-'):>5} {str(b.get('toc_container'))[:28]:<28} pag={b.get('pagination_max') or b.get('total_page_input') or b.get('select_pages') or '-':<5} content={str(c.get('content_sel'))[:26]:<26} p={c.get('content_paragraphs','-'):>3} {r.get('error','')[:60]}")
json.dump(results, open(os.path.join(HERE, 'probe.json'), 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
