import json, re, sys, site
from pathlib import Path
sys.path.append(site.getusersitepackages())
from playwright.sync_api import sync_playwright
exec(open(str(Path(__file__).parent / '_prelude.py')).read())
DETECT = r"""() => {
  const out = [];
  const els = document.querySelectorAll('.console-content th, .console-content td, .console-content .ant-tag, .console-content button, .console-content .ant-form-item-label label, .console-content .ant-tabs-tab');
  for (const el of els) {
    const walker = document.createTreeWalker(el, NodeFilter.SHOW_TEXT);
    let node;
    while ((node = walker.nextNode())) {
      const text = node.textContent; const re = /[A-Za-z][A-Za-z'-]{3,}/g; let m;
      while ((m = re.exec(text))) {
        const r = document.createRange(); r.setStart(node, m.index); r.setEnd(node, m.index + m[0].length);
        const tops = new Set([...r.getClientRects()].filter(x => x.width > 0).map(x => Math.round(x.top)));
        if (tops.size > 1) out.push((el.tagName === 'TH' ? 'th ' : el.tagName.toLowerCase() + ' ') + m[0] + ' @ ' + el.innerText.trim().slice(0, 50));
      }
    }
  }
  return [...new Set(out)].slice(0, 20);
}"""
res = {}
with sync_playwright() as p:
    br = p.chromium.launch(); ctx = br.new_context(viewport={'width': 1280, 'height': 900}); page = ctx.new_page()
    page.route(re.compile(r'^http://localhost:18311/api/operations/'), relay)
    page.goto(f'{BASE}/?lang=en'); settle(page)
    page.get_by_label(re.compile('Email')).first.fill(email); page.get_by_label(re.compile('^Password$')).fill(password)
    page.get_by_role('button', name=re.compile(r'^Sign In$')).click(); page.wait_for_selector('.console-layout', timeout=30000); settle(page, 1500)
    for w in (1280, 1440):
        page.set_viewport_size({'width': w, 'height': 900})
        for r in routes:
            page.evaluate("r => { window.history.pushState({}, '', r); window.dispatchEvent(new PopStateEvent('popstate')); }", r)
            settle(page, 2500)
            hits = page.evaluate(DETECT)
            if hits: res[f'{w} {r}'] = hits; print(w, r, hits[:4], flush=True)
    br.close()
(Path(__file__).parent / 'wordbreak.json').write_text(json.dumps(res, ensure_ascii=False, indent=1))
print('pages with breaks', len(res))
