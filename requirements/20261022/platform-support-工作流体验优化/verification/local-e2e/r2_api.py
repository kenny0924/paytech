"""工作流体验优化第二轮复验：422 / 409 直接调 API（Nginx 18444 → Gateway → ops-bff → Platform，Bearer），绕过前端。

用 I2（setup 新建）：
  1 完成缺必填键 → 422 errors[].field = output.monthlyVolume，message 必填
  2 发起缺必填启动字段 → 422 startValues.channelCode，必填
  3 含数据的文案（不存在的文件地址）→ 「请检查此处填写」
  4 表单 + 上传完成 → APPROVING；再改必选表单 → 409，说明里是模板名称而非 key
结果写入 state["r2api"]；只打印状态码与响应体（不含口令、令牌）。
"""
import json
import sys

from seed_v2 import load, save, latest_runs, complete, w, f, Failure, log  # noqa: F401


def call(tag, fn):
    try:
        res = fn()
    except Failure as failure:
        log(tag, f"HTTP {failure.status} {failure.body[:700]}")
        try:
            body = json.loads(failure.body)
        except ValueError:
            body = {}
        return failure.status, body
    log(tag, f"成功 {res}")
    return 200, res


def field_errors(body):
    return [(e.get("field"), e.get("message")) for e in body.get("errors") or []]


def main():
    s = load()
    a = w.admin()
    iid = s["I2"]
    runs = latest_runs(w.detail(a, iid), "fill")
    form, upload = runs["merchantInfo"], runs["licenseFiles"]
    r = {}
    st, body = call("422-missing-key", lambda: complete(a, form["actionRunId"], {"merchantName": "缺月交易量"}))
    r["missingKey"] = {"status": st, "errors": field_errors(body)}
    st, body = call("422-start-missing", lambda: a.send("POST", "/workflow/v1/instances", {
        "definitionId": s["definitionId"], "versionNo": s["versionNo"], "title": f"V2 r2-missing {s['stamp']}", "startValues": {}}))
    r["startMissing"] = {"status": st, "errors": field_errors(body)}
    st, body = call("422-bad-file", lambda: complete(a, upload["actionRunId"], {"files": [f.ADDR("999999999999999999")]}))
    r["badFile"] = {"status": st, "errors": field_errors(body)}
    st, body = call("422-bad-start-file", lambda: a.send("POST", "/workflow/v1/instances", {
        "definitionId": s["definitionId"], "versionNo": s["versionNo"], "title": f"V2 r2-badfile {s['stamp']}",
        "startValues": {"channelCode": "X", "contract": [f.ADDR("999999999999999999")]}}))
    r["badStartFile"] = {"status": st, "errors": field_errors(body)}
    # 推进到 APPROVING
    fid = f.upload(a, f.pdf(f"v2-r2-{s['stamp']}.pdf", "r2"))
    f.wait_available(fid)
    complete(a, upload["actionRunId"], {"files": [f.ADDR(fid)]})
    complete(a, form["actionRunId"], {"merchantName": "API 商户", "monthlyVolume": "5000"})
    node = [x for x in w.detail(a, iid)["nodeRuns"] if x["nodeKey"] == "fill"][-1]
    r["nodeStatus"] = node["status"]
    st, body = call("409-locked", lambda: complete(a, form["actionRunId"], {"merchantName": "改", "monthlyVolume": "1"}))
    r["locked"] = {"status": st, "detail": body.get("detail"), "code": body.get("code")}
    s["r2api"] = r
    save(s)
    log("r2api", json.dumps(r, ensure_ascii=False))


if __name__ == "__main__":
    sys.exit(main())
