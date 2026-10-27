#!/usr/bin/env python3
"""国旗图标：下载、转 PNG、经运营控制台公开上传、生成回填 SQL（runbook 第 8 节；用户 2026-10-27 授权，mor-test 写入由主会话执行）。

取代 db/platform-support/generator/upload_flags.py 的桩（其 download / emit-sql 思路保留，国家清单同源）。
子命令：
  fetch   <工作目录>                      离线前置：下载 npm 包 flag-icons@7.5.0（lipis/flag-icons，MIT），按注册表 sha512 integrity 核对，
                                          只解出 flags/4x3/*.svg 与 LICENSE 到 <工作目录>/svg/，写 fetch.json（每个 SVG 的 sha256）。
  convert <工作目录> [--width 120 --height 90]
                                          本机：249 个国家（与 t_ref_country 种子 ID 2001—2249 同一清单）中有上游 SVG 的，用 Playwright 无头
                                          Chromium 渲染成 PNG（透明背景）到 <工作目录>/png/，写 manifest.json（国家、SVG / PNG sha256、字节数、尺寸）；
                                          上游没有的国家列入 missing，保持 NULL。
  upload  <工作目录>                      **mor-test 写入，只由主会话执行**：按 refdata_mortest.py 的方式登录运营管理员，逐个 PNG 调
                                          POST /api/operations/file-upload/v1/uploads → 预签名 PUT → POST /uploads/{id}/completion，再轮询
                                          GET /file-upload/v1/files/{fileId} 到 AVAILABLE 且有 publicUrl。开始 / 完成两条路由每主体 10 次 / 分钟：
                                          每次调用间隔 ≥ 6.5 秒，429 指数退避；进度写 <工作目录>/upload-progress.json（每步落盘，重跑跳过已完成国家，
                                          已拿到 uploadId 未完成的只重做完成）；完成调用 504 / 超时不重传，按同一 uploadId 重调完成（Platform 对已完成
                                          的上传返回原回执），再轮询文件状态。输出 <工作目录>/mapping.json（CC → fileId、fileAddress、publicUrl）。
  emit-sql <工作目录> <输出目录>          离线：由 mapping.json 生成 apply.sql（只改 logo_file_address 仍为 NULL 的行）、rollback.sql（只回退本次设置的
                                          地址）与 verify.sql，并核对 mapping 与 manifest 一致（PNG sha256、国家集合）。
原因码：运营控制台开始上传的请求体只有 filename / mimeType / sizeBytes，原因码、访问级别与上传人由 operations-console-bff 填写
  （来源 operations-console-bff → PUBLIC 路由 → PUBLIC_DOCUMENT 策略，收 image/png）；本工具无法指定 REFERENCE_DATA_FLAG 之类的原因码。
凭据只在内存中使用，不打印、不写文件。
"""
import argparse
import base64
import hashlib
import io
import json
import os
import re
import subprocess
import sys
import tarfile
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

sys.dont_write_bytecode = True
HERE = Path(__file__).resolve().parent
REQ = HERE.parents[1]
COUNTRY_SEED = REQ / "db/platform-support/generator/sources/existing-country-region-seed.sql"
PACKAGE = "flag-icons"
VERSION = "7.5.0"
TARBALL = f"https://registry.npmjs.org/{PACKAGE}/-/{PACKAGE}-{VERSION}.tgz"
INTEGRITY = "sha512-kd+MNXviFIg5hijH766tt+3x76ele1AXlo4zDdCxIvqWZhKt4T83bOtxUOOMlTx/EcFdUMH5yvQgYlFh1EqqFg=="
FILE_UPLOAD = "/file-upload/v1"
PACE_SECONDS = 0.15  # 2026-10-27 用户授权临时放宽上传路由限流到 500 次 / 分钟（原 6.5）
MAX_BACKOFF_SECONDS = 120
ID = re.compile(r"[1-9][0-9]{0,18}")


def now():
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def write_json(path, value):
    tmp = path.with_suffix(path.suffix + ".tmp")
    tmp.write_text(json.dumps(value, ensure_ascii=False, indent=1) + "\n")
    os.replace(tmp, path)


def country_codes():
    codes = re.findall(r"^\s*\(\d+, '([A-Z]{2})'", COUNTRY_SEED.read_text(encoding="utf-8"), re.M)
    if len(codes) != 249 or len(set(codes)) != 249:
        raise SystemExit(f"国家清单应为 249 个不重复代码，实际 {len(codes)}")
    return codes


# ---------------------------------------------------------------- fetch / convert（离线）

def fetch(work):
    work.mkdir(parents=True, exist_ok=True)
    svg_dir = work / "svg"
    if svg_dir.exists() and any(svg_dir.iterdir()):
        raise SystemExit(f"{svg_dir} 已有内容；请换一个空工作目录")
    with urllib.request.urlopen(TARBALL, timeout=60) as response:
        data = response.read()
    algorithm, expected = INTEGRITY.split("-", 1)
    if base64.b64encode(hashlib.new(algorithm, data).digest()).decode() != expected:
        raise SystemExit("tarball integrity 与 npm 注册表不符，停止")
    (work / f"{PACKAGE}-{VERSION}.tgz").write_bytes(data)
    svg_dir.mkdir()
    files = {}
    with tarfile.open(fileobj=io.BytesIO(data), mode="r:gz") as archive:
        for member in archive.getmembers():
            name = member.name
            match = re.fullmatch(r"package/flags/4x3/([a-z0-9-]+)\.svg", name)
            if member.isfile() and match:
                body = archive.extractfile(member).read()
                if not body.lstrip().startswith((b"<svg", b"<?xml")):
                    raise SystemExit(f"不是 SVG：{name}")
                (svg_dir / f"{match.group(1)}.svg").write_bytes(body)
                files[match.group(1)] = sha256(body)
            elif member.isfile() and name == "package/LICENSE":
                (work / "LICENSE.flag-icons").write_bytes(archive.extractfile(member).read())
    write_json(work / "fetch.json", {"package": PACKAGE, "version": VERSION, "license": "MIT", "tarball": TARBALL,
                                     "integrity": INTEGRITY, "tarballSha256": sha256(data), "fetchedAt": now(),
                                     "svgCount": len(files), "svgSha256": dict(sorted(files.items()))})
    print(f"fetched {PACKAGE}@{VERSION}: {len(files)} 个 4x3 SVG，tarball sha256 {sha256(data)}")


def png_size(data):
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise SystemExit("不是 PNG")
    return int.from_bytes(data[16:20], "big"), int.from_bytes(data[20:24], "big")


def convert(work, width, height):
    fetched = json.loads((work / "fetch.json").read_text())
    svg_dir, png_dir = work / "svg", work / "png"
    png_dir.mkdir(exist_ok=True)
    codes = country_codes()
    present = [cc for cc in codes if cc.lower() in fetched["svgSha256"]]
    missing = [cc for cc in codes if cc.lower() not in fetched["svgSha256"]]
    for start in range(0, len(present), 50):
        batch = present[start:start + 50]
        subprocess.run(["node", str(HERE / "render_png.mjs"), str(svg_dir), str(png_dir), str(width), str(height), ",".join(batch)],
                       check=True)
    items = []
    for cc in present:
        svg = (svg_dir / f"{cc.lower()}.svg").read_bytes()
        if sha256(svg) != fetched["svgSha256"][cc.lower()]:
            raise SystemExit(f"{cc}: SVG 与 fetch.json 不符")
        png = (png_dir / f"{cc}.png").read_bytes()
        w, h = png_size(png)
        if (w, h) != (width, height):
            raise SystemExit(f"{cc}: PNG 尺寸 {w}×{h}，期望 {width}×{height}")
        items.append({"countryCode": cc, "svgSha256": sha256(svg), "pngFile": f"png/{cc}.png", "pngSha256": sha256(png),
                      "pngBytes": len(png), "width": w, "height": h})
    manifest = {"source": {k: fetched[k] for k in ("package", "version", "license", "tarball", "integrity", "tarballSha256")},
                "renderer": "Playwright headless Chromium (render_png.mjs, deviceScaleFactor 1, transparent background)",
                "convertedAt": now(), "countries": len(codes), "converted": len(items), "missing": missing,
                "unusedUpstream": sorted(set(fetched["svgSha256"]) - {cc.lower() for cc in codes}), "items": items}
    write_json(work / "manifest.json", manifest)
    sizes = [i["pngBytes"] for i in items]
    print(f"converted {len(items)} / {len(codes)}；missing {missing or '无'}；PNG {min(sizes)}—{max(sizes)} 字节，合计 {sum(sizes)}")


# ---------------------------------------------------------------- upload（mor-test 写入，主会话执行）

class Ops:
    """运营管理员会话：登录方式与 release/acceptance/refdata_mortest.py 相同（e2e_http test 目标、access.md 凭据）。"""

    def __init__(self):
        sys.path.insert(0, str(REQ / "release/acceptance"))
        import refdata_mortest  # noqa: E402
        self.module = refdata_mortest.load()
        self.E, self.OA = self.module.E, self.module.OA
        secrets = self.E.secrets()
        self.session = self.OA.ops_login(secrets["E2E_ADMIN_EMAIL"], secrets["E2E_ADMIN_PASSWORD"])
        self.last = 0.0

    def relogin(self):
        secrets = self.E.secrets()
        self.session = self.OA.ops_login(secrets["E2E_ADMIN_EMAIL"], secrets["E2E_ADMIN_PASSWORD"])

    def request(self, method, path, body=None, paced=True, attempts=8, json_empty=False):
        """paced：开始 / 完成路由按 PACE_SECONDS 间隔；429 / 502 / 503 指数退避；401 重新登录一次。"""
        delay = PACE_SECONDS
        for attempt in range(attempts):
            if paced:
                wait = self.last + PACE_SECONDS - time.monotonic()
                if wait > 0:
                    time.sleep(wait)
            status, payload = (self.session.get(path) if method == "GET" else self.session.call(method, path, body, json_empty=json_empty))
            if paced:
                self.last = time.monotonic()
            if status == 401 and attempt == 0:
                self.relogin()
                continue
            if status in (429, 502, 503) and attempt < attempts - 1:
                print(f"  {method} {path} → {status}，{delay:.0f} 秒后重试", flush=True)
                time.sleep(delay)
                delay = min(delay * 2, MAX_BACKOFF_SECONDS)
                continue
            return status, payload
        return status, payload


def put_presigned(ops, authorization, data):
    """按开始上传回执的 authorization 原样 PUT（只带回执中的头）；返回 HTTP 状态。"""
    if authorization.get("method") != "PUT" or not re.match(r"^https?://", authorization.get("url") or ""):
        raise SystemExit("开始上传回执的 authorization 不是 PUT 预签名地址")
    request = urllib.request.Request(authorization["url"], data=data, method="PUT", headers=dict(authorization.get("headers") or {}))
    for attempt in range(4):
        try:
            with urllib.request.urlopen(request, context=ops.E.TLS, timeout=60) as response:
                return response.status
        except urllib.error.HTTPError as failure:
            if failure.code >= 500 and attempt < 3:
                time.sleep(5 * (attempt + 1))
                continue
            return failure.code
        except urllib.error.URLError:
            if attempt < 3:
                time.sleep(5 * (attempt + 1))
                continue
            raise


def wait_available(ops, file_id, timeout=300):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        status, body = ops.request("GET", f"{FILE_UPLOAD}/files/{file_id}", paced=False)
        if status == 200 and body.get("scanStatus") == "AVAILABLE" and body.get("publicUrl"):
            return body
        if status == 200 and body.get("scanStatus") not in (None, "PENDING_SCAN", "SCANNING", "AVAILABLE"):
            raise SystemExit(f"文件 {file_id} 检查未通过：{body.get('scanStatus')}")
        time.sleep(5)
    raise SystemExit(f"文件 {file_id} 在 {timeout} 秒内未变为 AVAILABLE")


def upload(work):
    manifest = json.loads((work / "manifest.json").read_text())
    progress_path, mapping_path = work / "upload-progress.json", work / "mapping.json"
    progress = json.loads(progress_path.read_text()) if progress_path.exists() else {"startedAt": now(), "countries": {}}
    ops = Ops()
    status, constraints = ops.request("GET", f"{FILE_UPLOAD}/constraints", paced=False)
    if status != 200 or not constraints.get("configured") or "image/png" not in (constraints.get("allowedMimeTypes") or []):
        raise SystemExit(f"公开上传未配置或不收 image/png：HTTP {status} {constraints}")
    total = len(manifest["items"])
    for index, item in enumerate(manifest["items"], 1):
        cc = item["countryCode"]
        state = progress["countries"].setdefault(cc, {})
        if state.get("done") or state.get("fileId"):
            continue
        data = (work / item["pngFile"]).read_bytes()
        if sha256(data) != item["pngSha256"]:
            raise SystemExit(f"{cc}: PNG 与 manifest 不符")
        if not state.get("uploadId") or state.get("putStatus") not in (200, 201):
            status, started = ops.request("POST", f"{FILE_UPLOAD}/uploads",
                                          {"filename": f"flag-{cc}.png", "mimeType": "image/png", "sizeBytes": str(len(data))})
            if status not in (200, 201) or not ID.fullmatch(str(started.get("uploadId") or "")):
                raise SystemExit(f"{cc}: 开始上传失败 HTTP {status} {started}")
            state.update(uploadId=started["uploadId"], expiresAt=started.get("expiresAt"), startedAt=now(), putStatus=None)
            write_json(progress_path, progress)
            state["putStatus"] = put_presigned(ops, started["authorization"], data)
            write_json(progress_path, progress)
            if state["putStatus"] not in (200, 201):
                raise SystemExit(f"{cc}: 预签名 PUT 失败 HTTP {state['putStatus']}（重跑会重新开始上传）")
        receipt = None
        for attempt in range(6):   # 完成：504 / 超时不重传文件，按同一 uploadId 重调（已完成则返回原回执）
            status, body = ops.request("POST", f"{FILE_UPLOAD}/uploads/{state['uploadId']}/completion", json_empty=True)
            if status == 200 and ID.fullmatch(str(body.get("fileId") or "")) and body.get("fileAddress") == f"/files/v1/{body['fileId']}":
                receipt = body
                break
            if status in (504, 500, 409) or status is None:
                print(f"  {cc}: 完成 HTTP {status}，按同一 uploadId 重调", flush=True)
                time.sleep(10)
                continue
            raise SystemExit(f"{cc}: 完成上传失败 HTTP {status} {body}（上传 {state['uploadId']} 已记在进度文件）")
        if receipt is None:
            raise SystemExit(f"{cc}: 完成上传多次 504（上传 {state['uploadId']}）；稍后重跑本命令，会继续完成该上传")
        state.update(fileId=receipt["fileId"], fileAddress=receipt["fileAddress"], completedAt=receipt.get("completedAt"))
        write_json(progress_path, progress)
        print(f"[{index}/{total}] {cc} 已完成上传 → {receipt['fileAddress']}（扫描结果最后统一核对）", flush=True)
    # 扫描结果统一核对（不占开始 / 完成路由的限速额度）
    for item in manifest["items"]:
        cc = item["countryCode"]
        state = progress["countries"].get(cc, {})
        if state.get("done") or not state.get("fileId"):
            continue
        size = len((work / item["pngFile"]).read_bytes())
        detail = wait_available(ops, state["fileId"])
        if detail.get("mimeType") != "image/png" or str(detail.get("sizeBytes")) != str(size):
            raise SystemExit(f"{cc}: 文件记录与上传内容不符 {detail.get('mimeType')} {detail.get('sizeBytes')}")
        state.update(publicUrl=detail["publicUrl"], done=True, doneAt=now())
        write_json(progress_path, progress)
        print(f"  {cc} AVAILABLE", flush=True)
    mapping = {cc: {k: s[k] for k in ("fileId", "fileAddress", "publicUrl", "uploadId")}
               for cc, s in sorted(progress["countries"].items()) if s.get("done")}
    write_json(mapping_path, {"generatedAt": now(), "count": len(mapping), "missing": manifest["missing"], "countries": mapping})
    print(f"mapping：{len(mapping)} / {total} → {mapping_path}")


# ---------------------------------------------------------------- emit-sql（离线）

def emit_sql(work, out):
    manifest = json.loads((work / "manifest.json").read_text())
    mapping = json.loads((work / "mapping.json").read_text())["countries"]
    progress = json.loads((work / "upload-progress.json").read_text())["countries"]
    expected = {i["countryCode"] for i in manifest["items"]}
    if set(mapping) != expected:
        raise SystemExit(f"mapping 与 manifest 国家集合不同：缺 {sorted(expected - set(mapping))[:10]}，多 {sorted(set(mapping) - expected)[:10]}")
    for cc, row in mapping.items():
        if not ID.fullmatch(row["fileId"]) or row["fileAddress"] != f"/files/v1/{row['fileId']}" or not progress.get(cc, {}).get("done"):
            raise SystemExit(f"{cc}: mapping 行不合法 {row}")
    if len({r["fileId"] for r in mapping.values()}) != len(mapping):
        raise SystemExit("mapping 中 fileId 重复")
    out.mkdir(parents=True, exist_ok=True)
    head = (f"-- 国旗回填（{PACKAGE}@{VERSION}，MIT；manifest {sha256((work / 'manifest.json').read_bytes())[:16]}），只在 mor_platform 执行，客户端须 utf8mb4。\n"
            "-- 由 release/flags/flags.py emit-sql 生成，勿手改。\n")
    codes = ", ".join(f"'{cc}'" for cc in sorted(mapping))
    apply_sql = [head, f"-- 预期：{len(mapping)} 行；只改 logo_file_address 仍为 NULL 的国家（重跑为空操作）。上游无国旗的 {manifest['missing'] or '无'} 保持 NULL。\n",
                 "SET NAMES utf8mb4;\nSTART TRANSACTION;\nSET @flag_time = UTC_TIMESTAMP(6);\n"]
    rollback_sql = [head, f"-- 回退：只把本次设置的地址改回 NULL（地址被后来者改过的行不动）；预期 {len(mapping)} 行。\n",
                    "SET NAMES utf8mb4;\nSTART TRANSACTION;\nSET @flag_time = UTC_TIMESTAMP(6);\n"]
    for cc in sorted(mapping):
        address = mapping[cc]["fileAddress"]
        apply_sql.append(f"UPDATE t_ref_country SET logo_file_address = '{address}', version = version + 1, update_time = @flag_time "
                         f"WHERE country_code = '{cc}' AND logo_file_address IS NULL;\n")
        rollback_sql.append(f"UPDATE t_ref_country SET logo_file_address = NULL, version = version + 1, update_time = @flag_time "
                            f"WHERE country_code = '{cc}' AND logo_file_address = '{address}';\n")
    count_set = f"SELECT COUNT(*) AS flagged FROM t_ref_country WHERE country_code IN ({codes}) AND logo_file_address IS NOT NULL;\n"
    apply_sql += [count_set, "COMMIT;\n"]
    rollback_sql += [f"SELECT COUNT(*) AS still_flagged FROM t_ref_country WHERE country_code IN ({codes}) AND logo_file_address IS NOT NULL;\n", "COMMIT;\n"]
    rows = ", ".join(f"ROW('{cc}', '{mapping[cc]['fileAddress']}')" for cc in sorted(mapping))
    verify_sql = [head, "-- 只读核对。\n",
                  "SELECT COUNT(*) AS total, SUM(logo_file_address IS NOT NULL) AS with_logo FROM t_ref_country;\n",
                  f"-- 期望：total 249，with_logo {len(mapping)}（回退后 0）\n",
                  f"SELECT COUNT(*) AS matching_mapping FROM t_ref_country c JOIN (VALUES {rows}) AS m(cc, addr) "
                  "ON m.cc = c.country_code AND m.addr = c.logo_file_address;\n",
                  f"-- 期望：{len(mapping)}\n",
                  "SELECT COUNT(*) AS logo_not_public_available FROM t_ref_country c JOIN t_file_object f ON c.logo_file_address = CONCAT('/files/v1/', f.file_id) "
                  "WHERE f.access_level <> 'PUBLIC' OR f.scan_status <> 'AVAILABLE' OR f.mime_type <> 'image/png';\n", "-- 期望：0\n"]
    (out / "apply.sql").write_text("".join(apply_sql))
    (out / "rollback.sql").write_text("".join(rollback_sql))
    (out / "verify.sql").write_text("".join(verify_sql))
    print(f"apply.sql / rollback.sql / verify.sql → {out}（{len(mapping)} 个国家）")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("fetch").add_argument("work")
    conv = sub.add_parser("convert")
    conv.add_argument("work")
    conv.add_argument("--width", type=int, default=120)
    conv.add_argument("--height", type=int, default=90)
    up = sub.add_parser("upload")
    up.add_argument("work")
    up.add_argument("--i-am-main-session", action="store_true", required=True,
                    help="上传是 mor-test 写入；只有主会话在获得授权后执行")
    em = sub.add_parser("emit-sql")
    em.add_argument("work")
    em.add_argument("out")
    args = parser.parse_args()
    work = Path(args.work).resolve()
    if args.command == "fetch":
        fetch(work)
    elif args.command == "convert":
        convert(work, args.width, args.height)
    elif args.command == "upload":
        upload(work)
    else:
        emit_sql(work, Path(args.out).resolve())


if __name__ == "__main__":
    main()
