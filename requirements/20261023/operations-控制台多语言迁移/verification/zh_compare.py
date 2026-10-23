"""AC-01: capture visible zh text of every menu page (+ first-row detail) for baseline (18312) and migrated (18311, zh mocked).
usage: python3 -I zh_compare.py base|new   (read-only relay to mor-test; non-GET except login/refresh blocked)"""
import json, re, sys, site
from pathlib import Path
sys.path.append(site.getusersitepackages())
from playwright.sync_api import sync_playwright
mode = sys.argv[1]; port = 18312 if mode == 'base' else 18311
WEB = Path('/Users/kenny/Desktop/Workspace/Code-MoR-opsi18n/operations-console-web')
ROOT = Path('/Users/kenny/Desktop/Workspace/Code-MoR')
OUT = Path(__file__).parent / f'zhcmp-{mode}.json'
routes = re.findall(r'"frontendRoute": "([^"]+)"', (Path('/Users/kenny/Desktop/Workspace/Code-MoR-opsi18n-base/operations-console-web/src/navigation/pageCapabilities.ts')).read_text())
B = {loc: json.loads((WEB / f'i18n-out/operations-console.{loc}.json').read_text())['namespaces'] for loc in ('en', 'zh-CN')}
manifest = {'application': 'operations-console', 'releaseNo': 999, 'defaultLocale': 'en',
            'locales': [{'code': 'en', 'nativeName': 'English', 'direction': 'LTR'}, {'code': 'zh-CN', 'nativeName': '简体中文', 'direction': 'LTR'}],
            'bundles': {loc: {ns: {'hash': 'cmp', 'url': f'http://localhost:{port}/__i18n/{loc}/{ns}.json'} for ns in B[loc]} for loc in B}}
text = (ROOT / 'test-environment/private/access.md').read_text()
email = re.search(r'账号[:：]\s*(\S+)', text).group(1); pw = re.search(r'密码[:：]\s*(\S+)', text).group(1)
blocked = []
def relay(route):
    req = route.request; path = req.url.split(f'localhost:{port}', 1)[1]
    if path.startswith('/api/operations/i18n/v1/manifest'):
        if mode == 'new': return route.fulfill(status=200, content_type='application/json', body=json.dumps(manifest, ensure_ascii=False))
    if req.method != 'GET' and path.split('?')[0] not in ('/api/operations/auth/v1/login', '/api/operations/auth/v1/refresh'):
        blocked.append(path); return route.fulfill(status=503, body='{}')
    h = {k: v for k, v in req.headers.items() if k.lower() not in ('host', 'origin', 'referer')}; h['origin'] = 'http://localhost:28080'
    route.fulfill(response=route.fetch(url='http://localhost:28080' + path, headers=h, max_redirects=0))
def bundle(route):
    _, loc, ns = route.request.url.rsplit('/', 2)
    route.fulfill(status=200, content_type='application/json', body=json.dumps(B[loc][ns[:-5]], ensure_ascii=False), headers={'access-control-allow-origin': '*'})
GRAB = """() => { const pick = s => [...document.querySelectorAll(s)].map(e => e.innerText).join('\\n');
  return { lang: document.documentElement.lang, trail: pick('.console-trail'), main: pick('.console-content'),
           overlay: pick('.ant-drawer-open .ant-drawer-content, .ant-modal-wrap:not([style*="display: none"]) .ant-modal-content') }; }"""
res = {}
with sync_playwright() as p:
    br = p.chromium.launch(); ctx = br.new_context(viewport={'width': 1440, 'height': 900}, timezone_id='Asia/Dubai', locale='zh-CN'); page = ctx.new_page()
    page.route(re.compile(rf'^http://localhost:{port}/api/operations/'), relay)
    page.route(re.compile(rf'^http://localhost:{port}/__i18n/'), bundle)
    page.goto(f'http://localhost:{port}/?lang=zh-CN'); page.wait_for_timeout(3000)
    res['__login'] = page.evaluate("() => document.body.innerText")
    page.get_by_label(re.compile('账号')).first.fill(email); page.get_by_label(re.compile('^密码$')).fill(pw)
    page.get_by_role('button', name=re.compile(r'^登\s?录$')).click(); page.wait_for_selector('.console-layout', timeout=30000); page.wait_for_timeout(2500)
    for r in routes:
        page.evaluate("r => { window.history.pushState({}, '', r); window.dispatchEvent(new PopStateEvent('popstate')); }", r)
        page.wait_for_timeout(3500)
        entry = {'page': page.evaluate(GRAB)}
        row = page.query_selector('.console-content .ant-table-tbody tr.clickable-row, .console-content .ant-table-tbody tr[data-row-key]')
        if row:
            try:
                row.click(timeout=4000); page.wait_for_timeout(2500)
                entry['detail'] = page.evaluate(GRAB); entry['detailUrl'] = page.url
            except Exception as e: entry['detailError'] = str(e)[:120]
            page.keyboard.press('Escape'); page.wait_for_timeout(500)
        res[r] = entry; print(mode, r, len(entry['page']['main']), 'detail' if 'detail' in entry else '', flush=True)
    br.close()
OUT.write_text(json.dumps({'results': res, 'blocked': blocked}, ensure_ascii=False, indent=1))
print('blocked', len(blocked))
