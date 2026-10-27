# 国旗图标（runbook 第 8 节；用户 2026-10-27 授权；mor-test 写入只由主会话执行）

工具：`flags.py`（fetch / convert / upload / emit-sql）+ `render_png.mjs`（Playwright 无头 Chromium，本机已装，无新安装）。取代 `db/platform-support/generator/upload_flags.py` 的桩。

## 已在本机完成（rel-dev，离线）

工作目录 `/Users/kenny/mor-backups/refdata-20261027/flags-work/`：
- `fetch`：npm `flag-icons@7.5.0`（MIT，`LICENSE.flag-icons`），注册表 integrity `sha512-kd+MNXvi…` 核对通过，tarball sha256 `c0b80bf0e08006a60f56621d6bc49f8c7131f4d1fef6737a165a673431f4b518`；271 个 4x3 SVG（另有 22 个非国家旗不用，如 eu、un、xk、gb-eng）。
- `convert`：249 / 249 个国家都有上游国旗（missing 无），PNG 120×90、透明背景，299—11849 字节，合计 566077 字节；`manifest.json` 记录每个国家的 SVG / PNG sha256 与字节数。
- `emit-sql` 用合成映射在断网 MySQL（mor-test 12:56Z 备份 + sql/10）上验证：apply 249 行、重跑空操作、verify 249 / 249、rollback 后 0。

## 主会话执行（mor-test 写入）

```bash
F="/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261027/platform-support-参考数据目录与client-master迁移/release/flags/flags.py"
W=/Users/kenny/mor-backups/refdata-20261027/flags-work
OUT="/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261027/platform-support-参考数据目录与client-master迁移/release/flags/sql"
cd /Users/kenny/Desktop/Workspace/Code-MoR; set -a; . ./test-environment/.env.test; set +a
sqlr() { docker exec -i -e MYSQL_PWD="$MOR_MYSQL_ROOT_PASSWORD" mor-test-mysql-1 mysql -uroot --default-character-set=utf8mb4 -t "$@"; }
# 0. 前置（只读）：期望 total 249、with_logo 0
echo "SELECT COUNT(*), SUM(logo_file_address IS NOT NULL) FROM t_ref_country;" | sqlr mor_platform
# 1. 上传（约 249 × 2 × 6.5 秒 ≈ 55 分钟，另加扫描轮询；可中断，重跑从 upload-progress.json 继续）
python3 -I "$F" upload "$W" --i-am-main-session
# 2. 生成 SQL（离线；核对 mapping 与 manifest 一致）
python3 -I "$F" emit-sql "$W" "$OUT"
# 3. 回填与核对（root）：apply 期望末行 249；verify 期望 249 / 249、matching 249、not_public 0
sqlr mor_platform < "$OUT/apply.sql"; sqlr mor_platform < "$OUT/verify.sql"
# 4. 页面：运营"参考数据 · 国家"与客户入驻国家下拉显示国旗；/reference-data/countries 的 logoPublicUrl 非空
# 回退：sqlr mor_platform < "$OUT/rollback.sql"（只改回本次设置的地址；文件保留在公开桶，无害）
```

说明：
- 上传走运营控制台公开上传（`/api/operations/file-upload/v1/uploads` → 预签名 PUT → `/completion`），来源 operations-console-bff → PUBLIC → PUBLIC_DOCUMENT（收 png）。请求体只有 filename / mimeType / sizeBytes，**原因码由 BFF 填写，不能指定**。文件名 `flag-<CC>.png`。
- 节流：开始 / 完成每次调用间隔 ≥ 6.5 秒（每主体 10 次 / 分钟）；429 / 502 / 503 指数退避（最长 120 秒）；401 重登一次。完成调用 504 / 500 不重传，按同一 uploadId 重调（Platform 对已完成上传返回原回执），之后轮询文件到 AVAILABLE 且有 publicUrl，并核对 mime 与字节数。
- 进度每步落盘；PUT 失败的国家重跑时重新开始上传（旧的未完成上传会过期，无害）。
