"""外部 API 页面与 Pencil 画板对照截图（偏差清单用）。只读取 E2E_RUNTIME 中的合成账号与 3e 数据，1440×1100 视口。

少数状态为对照而构造：空状态新建一个无实现的接入方；加载失败与流程校验失败用浏览器请求拦截返回固定响应。
构造只发生在本脚本的浏览器会话中，不进入测试或产品代码。"""
import json, os, re, sys, time, urllib.request, uuid
from pathlib import Path
from playwright.sync_api import sync_playwright

runtime = Path(os.environ['E2E_RUNTIME'])
out = Path(sys.argv[1]); out.mkdir(parents=True, exist_ok=True)
platform = json.loads((runtime / 'platform.json').read_text())
origin = json.loads((runtime / 'web.json').read_text())['origin']
v = json.loads((runtime / 'verification-3e.json').read_text())
STD, I = v['standardApi'], v['integration']
SCRIPT = 'E3_SIGN_' + STD.split('_')[-1]
SA, IN = '/external-api/standard-apis', '/external-api/integrations'
shots = []
ORDER = ['QsmsL', 'aWSB5', 'zM5lQ', 'rDRrX', 'B6SIWQ', 'ajM1s', 'H8MVL7', 'D6g5lH', 'X9Bdwk', 'SBF41', 'NtUrV', 'lIt5B',
         'OHYDb', 'KYgmt', 'q1TwH', 't02TpP', 'EHoOg', 'QLWO0', 'Hx8aB', 'HJbVI', 'XTG38', 'BwTsU', 'q940Qq', 'CCcAX']
ONLY = set(filter(None, os.environ.get('ONLY', '').split(',')))


def button(page, text):
    return page.get_by_role('button', name=re.compile('^' + r'\s?'.join(text) + '$'))


def login(page, kind):
    page.goto(origin + '/')
    page.get_by_placeholder('name@company.com').fill(platform[kind])
    page.locator('input[type=password]').fill(platform['password'])
    page.locator('button[type=submit]').click()
    page.wait_for_selector('.console-sidebar', timeout=20000)


def goto(page, path, wait_text=None):
    time.sleep(1.5)  # 避免 Gateway 每主体速率限制
    page.goto(origin + path)
    if wait_text:
        page.get_by_text(wait_text, exact=False).first.wait_for(timeout=20000)
    page.wait_for_load_state('networkidle')


def shot(page, board, name, note=''):
    time.sleep(0.5)
    file = f'{ORDER.index(board) + 1:02d}-{board}-{name}.png'
    page.screenshot(path=str(out / file), full_page=True)
    shots.append({'board': board, 'name': name, 'file': file, 'note': note})


def api_token():
    body = json.dumps({'loginIdentifier': platform['admin'], 'password': platform['password']}).encode()
    request = urllib.request.Request(origin + '/api/operations/auth/v1/login', data=body, method='POST',
                                     headers={'Content-Type': 'application/json', 'Origin': origin})
    return json.loads(urllib.request.urlopen(request).read())['accessToken']


def create_empty_integration():
    code = 'E3_EMPTY_' + uuid.uuid4().hex[:6].upper()
    body = {'integrationCode': code, 'integrationName': '空接入方（对照）', 'description': None,
            'endpoints': [{'endpointCode': 'DEMO_GATEWAY', 'baseUrl': 'https://gateway.example.test/api'}], 'reason': '页面对照'}
    request = urllib.request.Request(origin + '/api/operations/external-api-admin/v1/integrations', data=json.dumps(body).encode(),
                                     method='POST', headers={'Content-Type': 'application/json', 'Origin': origin,
                                                             'Authorization': 'Bearer ' + api_token(), 'Idempotency-Key': uuid.uuid4().hex})
    urllib.request.urlopen(request).read()
    return code


def tab(page, label):
    page.get_by_role('tab', name=re.compile('^' + label)).first.click()
    page.wait_for_load_state('networkidle')


def node(page, text):
    page.locator('.react-flow__node').filter(has_text=text).first.click()
    time.sleep(0.5)


def run(page, board, name, steps, note=''):
    if ONLY and board not in ONLY:
        return
    for attempt in range(3):
        try:
            steps()
            shot(page, board, name, note)
            return
        except Exception as failure:  # Gateway 限流等偶发失败重试；仍失败则记录并继续其余画板
            error = str(failure)[:300]
            time.sleep(8)
    shots.append({'board': board, 'name': name, 'file': None, 'error': error})


with sync_playwright() as playwright:
    browser = playwright.chromium.launch()
    page = browser.new_context(viewport={'width': 1440, 'height': 1100}).new_page()
    login(page, 'admin')
    run(page, 'QsmsL', 'standard-api-list', lambda: goto(page, SA, STD))

    def edit_field():
        goto(page, f'{SA}/{STD}?mode=edit', '请求字段')
        page.locator('.xapi-table-wrap button', has_text='编辑').nth(1).click()
    run(page, 'aWSB5', 'standard-api-edit-field', edit_field)
    run(page, 'zM5lQ', 'integration-list', lambda: goto(page, IN, I))
    run(page, 'rDRrX', 'workspace-basic', lambda: goto(page, f'{IN}/{I}', '出站地址'))

    api = f'{IN}/{I}/apis/CREATE_ORDER?mode=edit'
    run(page, 'B6SIWQ', 'api-interface', lambda: goto(page, api, 'HTTP'))
    run(page, 'ajM1s', 'api-status-rules', lambda: (goto(page, api, 'HTTP'), tab(page, '状态规则')))
    run(page, 'H8MVL7', 'script-edit', lambda: goto(page, f'{IN}/{I}/scripts/{SCRIPT}?mode=edit', '源码'))

    flow = f'{IN}/{I}/implementations/{STD}?mode=edit'
    def invalid_flow():
        page.route('**/implementations/validate', lambda route: route.fulfill(status=200, content_type='application/json',
            body=json.dumps({'valid': False, 'issues': [{'severity': 'ERROR', 'code': 'OUTPUT_FIELD_UNCOVERED',
                'location': 'flowDefinition.returns[0].outputs', 'message': '必填响应字段未赋值：paymentStatus'}]})))
        goto(page, flow, 'createOrder')
        button(page, '校验').first.click()
        page.get_by_text('paymentStatus', exact=False).first.wait_for(timeout=10000)
        node(page, 'createOrder')
        page.get_by_text('编辑出口顺序').first.click()
    run(page, 'D6g5lH', 'flow-edit-invalid', invalid_flow, '校验失败由浏览器拦截构造')
    page.unroute('**/implementations/validate')
    run(page, 'X9Bdwk', 'flow-node-values', lambda: (goto(page, flow, 'createOrder'), node(page, 'createOrder'),
                                                     page.locator('.xflow-row-button').nth(2).click()))
    run(page, 'SBF41', 'api-request-fields', lambda: (goto(page, api, 'HTTP'), tab(page, '请求字段')))
    run(page, 'NtUrV', 'api-params-fixed', lambda: (goto(page, api, 'HTTP'), tab(page, '调用参数')))
    run(page, 'lIt5B', 'api-scripts', lambda: (goto(page, api, 'HTTP'), tab(page, '脚本')))
    run(page, 'OHYDb', 'api-outputs', lambda: (goto(page, api, 'HTTP'), tab(page, '输出')))
    run(page, 'KYgmt', 'workspace-apis', lambda: goto(page, f'{IN}/{I}?tab=apis', 'QUERY_ORDER'))
    run(page, 'q1TwH', 'workspace-scripts', lambda: goto(page, f'{IN}/{I}?tab=scripts', SCRIPT))

    def new_implementation():
        goto(page, f'{IN}/{I}?tab=implementations', STD)
        page.get_by_role('button', name=re.compile('新建')).first.click()
        time.sleep(1)
    run(page, 't02TpP', 'workspace-implementations-new', new_implementation)

    def delete_protected():
        goto(page, f'{IN}/{I}?tab=apis', 'QUERY_ORDER')
        page.locator('tr', has_text='QUERY_ORDER').get_by_role('button', name='更多操作').click()
        page.get_by_role('menuitem', name=re.compile('删除')).first.click()
        page.get_by_text('无法删除', exact=False).first.wait_for(timeout=10000)
    run(page, 'EHoOg', 'delete-protected', delete_protected)

    def conflict():
        other = page.context.new_page()
        login(other, 'admin')  # 登录态按标签页保存，第二个页面单独登录
        goto(page, api, 'HTTP')
        goto(other, api, 'HTTP')
        for target, suffix in ((other, '（对照 A）'), (page, '（对照 B）')):
            target.locator('.xapi-panel input').first.fill('下单' + suffix)
            button(target, '保存').first.click()
            target.get_by_label('变更原因').fill('页面对照')
            target.locator('.ant-modal').get_by_role('button', name=re.compile('^保\\s?存$')).click()
            time.sleep(2)
        page.get_by_text('已被他人修改', exact=False).first.wait_for(timeout=10000)
        other.close()
    run(page, 'QLWO0', 'conflict', conflict, '两个页面先后保存同一原子 API 版本')

    empty = create_empty_integration() if not ONLY or 'Hx8aB' in ONLY else None
    run(page, 'Hx8aB', 'empty-implementations', lambda: goto(page, f'{IN}/{empty}?tab=implementations', '标准 API 实现'))

    def load_failed():
        page.route('**/external-api-admin/v1/standard-apis?**', lambda route: route.fulfill(status=503,
            content_type='application/problem+json', headers={'X-Request-Id': 'req-ui-review'},
            body=json.dumps({'type': 'about:blank', 'title': 'Service Unavailable', 'code': 'DEPENDENCY_UNAVAILABLE',
                             'detail': '依赖暂时不可用', 'requestId': 'req-ui-review', 'timestamp': '2026-10-13T00:00:00Z'})))
        goto(page, SA + '?keyword=PAY')
        time.sleep(2)
    run(page, 'HJbVI', 'load-failed', load_failed, '列表接口由浏览器拦截返回 503')
    page.unroute('**/external-api-admin/v1/standard-apis?**')

    def unsaved_leave():
        page.route('**/implementations/validate', lambda route: route.fulfill(status=200, content_type='application/json',
            body=json.dumps({'valid': False, 'issues': [{'severity': 'ERROR', 'code': 'OUTPUT_FIELD_UNCOVERED',
                'location': 'flowDefinition.returns[0].outputs', 'message': '必填响应字段未赋值：paymentStatus'}]})))
        goto(page, flow, 'createOrder')
        button(page, '校验').first.click()
        page.get_by_text('paymentStatus', exact=False).first.wait_for(timeout=10000)
        page.locator('.xflow-budget input').fill('19000')
        sidebar = page.locator('.console-sidebar')
        if not sidebar.get_by_text('接入方').first.is_visible():
            sidebar.get_by_text('外部 API').first.click()
        sidebar.get_by_text('接入方').first.click()
        time.sleep(1.5)
    run(page, 'BwTsU', 'unsaved-leave', unsaved_leave, '校验失败由浏览器拦截构造')
    page.unroute('**/implementations/validate')
    run(page, 'q940Qq', 'flow-return', lambda: (goto(page, flow, 'createOrder'), node(page, 'paidAfterQuery')))

    def initializer():
        goto(page, flow, 'createOrder')
        page.get_by_text('channelAccount', exact=False).first.click()
        time.sleep(0.5)
    run(page, 'CCcAX', 'flow-initializer', initializer)

    reader = browser.new_context(viewport={'width': 1440, 'height': 1100}).new_page()
    login(reader, 'reader')
    run(reader, 'XTG38', 'reader-detail', lambda: goto(reader, f'{IN}/{I}/apis/CREATE_ORDER', 'HTTP'))
    browser.close()

previous = json.loads((out / 'capture.json').read_text()) if ONLY and (out / 'capture.json').exists() else []
shots = sorted([s for s in previous if s['board'] not in ONLY] + shots, key=lambda s: ORDER.index(s['board']))
(out / 'capture.json').write_text(json.dumps(shots, ensure_ascii=False, indent=2) + '\n')
print(json.dumps({'captured': sum(1 for s in shots if s.get('file')), 'failed': [s for s in shots if not s.get('file')]},
                 ensure_ascii=False))
