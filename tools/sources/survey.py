"""Fetch each site's homepage, record status/title/theme hints and sample links. Output: survey.json + html/<host>.html"""
import concurrent.futures, json, os, re, ssl, sys, urllib.request, urllib.error, gzip, io

sys.stdout.reconfigure(encoding='utf-8')
HERE = os.environ.get("SURVEY_DIR") or os.path.dirname(os.path.abspath(__file__))  # SURVEY_DIR keeps html/ and *.json out of the repo
OUT = os.path.join(HERE, 'html')
os.makedirs(OUT, exist_ok=True)
UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36'
ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

SITES = [l.strip() for l in open(os.path.join(HERE, 'sites.txt'), encoding='utf-8') if l.strip()]

def fetch(host):
    for scheme in ('https://', 'http://'):
        url = scheme + host + '/'
        req = urllib.request.Request(url, headers={'User-Agent': UA, 'Accept': 'text/html,*/*;q=0.8', 'Accept-Language': 'vi,en;q=0.8,zh;q=0.6', 'Accept-Encoding': 'gzip'})
        try:
            with urllib.request.urlopen(req, timeout=20, context=ctx) as r:
                raw = r.read()
                if r.headers.get('Content-Encoding') == 'gzip':
                    raw = gzip.GzipFile(fileobj=io.BytesIO(raw)).read()
                ct = r.headers.get('Content-Type', '')
                m = re.search(r'charset=([\w-]+)', ct, re.I) or re.search(rb'charset=["\']?([\w-]+)', raw[:4000], re.I)
                enc = (m.group(1) if isinstance(m.group(1), str) else m.group(1).decode()) if m else 'utf-8'
                if enc.lower() in ('gb2312', 'gbk'): enc = 'gb18030'
                try: html = raw.decode(enc, 'replace')
                except LookupError: html = raw.decode('utf-8', 'replace')
                return {'host': host, 'url': url, 'final': r.geturl(), 'status': r.status, 'html': html, 'bytes': len(raw)}
        except urllib.error.HTTPError as e:
            body = b''
            try: body = e.read()
            except Exception: pass
            return {'host': host, 'url': url, 'final': e.geturl() if hasattr(e, 'geturl') else url, 'status': e.code, 'html': body.decode('utf-8', 'replace'), 'bytes': len(body)}
        except Exception as e:
            last = {'host': host, 'url': url, 'final': None, 'status': 0, 'html': '', 'error': type(e).__name__ + ': ' + str(e)[:120]}
    return last

HINTS = [
    ('madara', r'madara|wp-manga|manga_id'), ('wordpress', r'wp-content|wp-includes'), ('truyenfull-like', r'truyen-list|list-chapter|chapter-c|#total-page|total-page'),
    ('nuxt/spa', r'__NUXT__|id="__nuxt"|data-server-rendered|id="app"|id="root"|__NEXT_DATA__'), ('cloudflare', r'cf-browser-verification|Just a moment|challenge-platform|cf_chl'),
    ('laravel', r'laravel_session|csrf-token'), ('metruyenchu-like', r'metruyenchu|get/listchap|nh-section'), ('sangtacviet', r'sangtacviet'), ('yy', r'truyenyy'),
    ('ptcms/ssr-cn', r'/book/\d+/\d+\.html|bookinfo|readcontent|txtnav'), ('wattpad', r'wattpad'),
]

def analyze(r):
    h = r.get('html', '')
    title = re.search(r'<title[^>]*>(.*?)</title>', h, re.I | re.S)
    hints = [name for name, rx in HINTS if re.search(rx, h, re.I)]
    gen = re.search(r'<meta name="generator" content="([^"]+)"', h, re.I)
    links = re.findall(r'href="(https?://[^"]+|/[^"]+)"', h)
    cands = [l for l in links if re.search(r'(truyen|novel|book|story|sach|chuong|chapter|/read/|/doc-truyen|/tieu-thuyet)', l, re.I)]
    return {
        'host': r['host'], 'status': r['status'], 'final': r.get('final'), 'bytes': r.get('bytes', 0),
        'title': re.sub(r'\s+', ' ', title.group(1)).strip()[:90] if title else None,
        'hints': hints, 'generator': gen.group(1)[:60] if gen else None,
        'sample_links': list(dict.fromkeys(cands))[:8], 'error': r.get('error'),
    }

results = []
with concurrent.futures.ThreadPoolExecutor(max_workers=12) as ex:
    for r in ex.map(fetch, SITES):
        if r.get('html'):
            with open(os.path.join(OUT, r['host'] + '.html'), 'w', encoding='utf-8') as f: f.write(r['html'])
        a = analyze(r)
        results.append(a)
        print(f"{a['status']:>3} {a['host']:<32} {str(a['bytes']):>7} {','.join(a['hints'])[:40]:<40} {a['title'] or a['error'] or ''}")
with open(os.path.join(HERE, 'survey.json'), 'w', encoding='utf-8') as f: json.dump(results, f, ensure_ascii=False, indent=1)
