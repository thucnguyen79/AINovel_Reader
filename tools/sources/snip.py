"""snip.py host file css [n] [maxchars] - print outer HTML of matches (scripts/styles stripped)."""
import sys,io,re,os
HERE = os.environ.get('SURVEY_DIR') or os.path.dirname(os.path.abspath(__file__))
from bs4 import BeautifulSoup
sys.stdout.reconfigure(encoding='utf-8')
def run(host,file,css,n=1,maxc=1200):
    p=os.path.join(HERE, 'html', f'{host}__{file}.html')
    if not os.path.exists(p): print(f'## {host} {file}: MISSING'); return
    s=BeautifulSoup(io.open(p,encoding='utf-8').read(),'lxml')
    for t in s(['script','style','noscript','svg']): t.decompose()
    els=s.select(css)
    print(f'## {host} {file} [{css}] -> {len(els)}')
    for e in els[:n]:
        print('   ',re.sub(r'\s+',' ',str(e))[:maxc])
if __name__=='__main__':
    a=sys.argv[1:]
    run(a[0],a[1],a[2],int(a[3]) if len(a)>3 else 1,int(a[4]) if len(a)>4 else 1200)
