"""S7-04（用户 2026-10-22 确认，主会话执行）：只把 mor-test 标准 API PAY 的 payment.subject 改为非必填。回退：同脚本 --revert。"""
import json, sys
sys.path.insert(0, __file__.rsplit('/', 1)[0])
import mt  # noqa: E402

KEYS = ('fieldLocation', 'fieldPath', 'fieldName', 'dataType', 'required', 'description')
ADMIN = '/external-api-admin/v1'
revert = '--revert' in sys.argv
token = mt.login()
st, cur = mt.ops(token, 'GET', f'{ADMIN}/standard-apis/PAY')
assert st == 200, (st, cur)
req = [{k: f.get(k) for k in KEYS} for f in cur['requestFields']]
target = [f for f in req if f['fieldPath'] == 'payment.subject']
assert len(target) == 1, target
print('before: version', cur['version'], 'subject.required', target[0]['required'])
want = True if revert else False
if target[0]['required'] == want:
    print('no change needed'); sys.exit(0)
target[0]['required'] = want
st, res = mt.ops(token, 'PUT', f'{ADMIN}/standard-apis/PAY', {
    'standardApiName': cur['standardApiName'], 'description': cur.get('description'),
    'requestFields': req, 'responseFields': [{k: f.get(k) for k in KEYS} for f in cur['responseFields']],
    'expectedVersion': cur['version'],
    'reason': '3DS 全链路阶段 7 S7-04：Payment 不传订单标题，payment.subject 改为' + ('必填（回退）' if revert else '非必填')})
print('PUT', st, json.dumps(res, ensure_ascii=False)[:400] if st != 200 else '')
st, after = mt.ops(token, 'GET', f'{ADMIN}/standard-apis/PAY')
print('after: version', after['version'], 'subject.required',
      [f['required'] for f in after['requestFields'] if f['fieldPath'] == 'payment.subject'])
