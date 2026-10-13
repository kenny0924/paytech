"""外部 API 2e：无头浏览器走查。登录后依次打开各页面截图并收集浏览器控制台错误；只读取 E2E_RUNTIME 的合成账号。"""
import json, os, sys, time
from pathlib import Path
from playwright.sync_api import sync_playwright

runtime = Path(os.environ['E2E_RUNTIME'])
out = Path(sys.argv[1]); out.mkdir(parents=True, exist_ok=True)
platform = json.loads((runtime / 'platform.json').read_text())
origin = json.loads((runtime / 'web.json').read_text())['origin']
verification = json.loads((runtime / 'verification.json').read_text())
STD, INTEGRATION = verification['standardApi'], verification['integration']
errors, shots = [], []


def login(page, kind):
    page.goto(origin + '/')
    page.get_by_placeholder('name@company.com').fill(platform[kind])
    page.locator('input[type=password]').fill(platform['password'])
    page.locator('button[type=submit]').click()
    page.wait_for_selector('.console-sidebar', timeout=20000)


def shot(page, name, path, wait_text=None):
    time.sleep(2)  # 避免触发 Gateway 每主体速率限制
    page.goto(origin + path)
    found = True
    if wait_text:
        try:
            page.get_by_text(wait_text, exact=False).first.wait_for(timeout=20000)
        except Exception:
            found = False
    page.wait_for_load_state('networkidle')
    page.screenshot(path=str(out / f'{name}.png'), full_page=True)
    shots.append({'name': name, 'path': path, 'expectedTextFound': found})


with sync_playwright() as playwright:
    browser = playwright.chromium.launch()
    for kind in ['admin', 'reader']:
        page = browser.new_page(viewport={'width': 1440, 'height': 900})
        page.on('console', lambda message, k=kind: errors.append(f'{k}: {message.text}') if message.type == 'error' else None)
        page.on('pageerror', lambda error, k=kind: errors.append(f'{k}: {error}'))
        login(page, kind)
        prefix = f'{kind}-'
        shot(page, prefix + '01-standard-apis', f'/external-api/standard-apis?keyword={STD}', STD)
        shot(page, prefix + '02-standard-api-detail', f'/external-api/standard-apis/{STD}', '请求字段')
        shot(page, prefix + '03-integrations', f'/external-api/integrations?keyword={INTEGRATION}', INTEGRATION)
        shot(page, prefix + '04-workspace-basic', f'/external-api/integrations/{INTEGRATION}', '出站地址')
        shot(page, prefix + '05-workspace-apis', f'/external-api/integrations/{INTEGRATION}?tab=apis', 'CREATE_ORDER')
        shot(page, prefix + '06-workspace-scripts', f'/external-api/integrations/{INTEGRATION}?tab=scripts', '请求签名')
        shot(page, prefix + '07-workspace-implementations', f'/external-api/integrations/{INTEGRATION}?tab=implementations', STD)
        shot(page, prefix + '08-api-editor', f'/external-api/integrations/{INTEGRATION}/apis/CREATE_ORDER' + ('?mode=edit' if kind == 'admin' else ''), 'HTTP 传输')
        shot(page, prefix + '09-script-editor', f'/external-api/integrations/{INTEGRATION}/scripts/E2E_SIGN_{STD.split("_")[-1]}', '源码')
        shot(page, prefix + '10-flow-editor', f'/external-api/integrations/{INTEGRATION}/implementations/{STD}' + ('?mode=edit' if kind == 'admin' else ''), 'createOrder')
        if kind == 'admin':
            page.locator('.react-flow__node').filter(has=page.locator('strong', has_text='createOrder')).filter(has_not_text='开始').first.click()
            page.get_by_text('出口顺序').first.wait_for(timeout=10000)
            page.screenshot(path=str(out / 'admin-11-flow-node-panel.png'), full_page=True)
            page.locator('button', has_text='校').filter(has_text='验').first.click()
            page.get_by_text('校验结果').first.wait_for(timeout=15000)
            page.screenshot(path=str(out / 'admin-12-flow-validation.png'), full_page=True)
            shots += [{'name': 'admin-11-flow-node-panel'}, {'name': 'admin-12-flow-validation'}]
        manage_buttons = page.locator('button', has_text='编').filter(has_text='辑').count()
        shots.append({'name': prefix + 'edit-buttons-on-flow', 'count': manage_buttons})
        page.close()
    browser.close()

(out / 'browse.json').write_text(json.dumps({'shots': shots, 'consoleErrors': errors}, ensure_ascii=False, indent=2) + '\n')
print(json.dumps({'shots': len(shots), 'consoleErrors': len(errors)}, ensure_ascii=False))
for error in errors[:10]:
    print('ERR', error[:300])
