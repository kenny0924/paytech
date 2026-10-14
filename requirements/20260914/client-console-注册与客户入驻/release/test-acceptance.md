# mor-test 客户入驻发布执行记录（2026-10-14）

用户批准 [release.md](./release.md)（回复“可以”，D1—D4 按建议），随后对仓库改动回复“提交，脚本执行了”（test CA 已加入登录钥匙串并信任）。

## 结果

| 步骤 | 结果 |
|---|---|
| 仓库改动 | 提交 `7e9eddc`（部署单元、Nginx、Vault 策略、脚本与 SQL）；执行中修复两处并提交 `06f46b9`（见“偏差”1、2）。均未推送 |
| 制品 | 干净 worktree 离线构建（JDK 25 `-DskipTests`；Platform/CCT/Risk 以 `install` 刷新本机 `~/.m3` 的 API 快照，客户端 BFF `package`）；两个前端 `npm ci && npm run build`。JAR SHA-256 前缀：Platform `4b0753fc7cce2d75`、CCT `37dc8eb48ae1810b`、Risk `a84049dc2129b2f2`、客户端 BFF `dc49425fe6290b71`。镜像 tag `client-onboarding-20261014`，ID 见 [compose-candidate.yaml](./compose-candidate.yaml)；构建 worktree 已删除 |
| 补跑测试 | Risk `verify` **1907/1907**、客户端 BFF `verify` **35/35**、客户端 Web **33/33**（测试前停止本机 e2e Risk 进程） |
| 复核 | 11 个容器、`mor_cct` 0 表、`mor_risk` 80 表且 10 张相关表 0 行、ACTIVE 路由 149、PLATFORM_ADMIN v21、所有预留 ID 无冲突，与清单一致 |
| 备份 | `~/Code-MoR-backups/20261014-client-onboarding-test/20261014T110300Z/`（0700/0600，附 SHA256SUMS）：`mor_platform`/`mor_risk`/`mor_cct` 结构，`mor_platform` 12 张配置表数据，原/新 Vault 策略、Nginx、两个 Compose 文件，容器镜像清单 |
| `mor_cct` | 完整 Schema（SHA-256 `b485ffd3…`）→ 38 表，[compare_schema.py](./compare_schema.py) 逐列/索引/注释 **0 差异** |
| `mor_risk` | 停旧 Risk → [risk-onboarding-alter.sql](../db/implementation/risk-onboarding-alter.sql)（`eb8c7dcb…`）→ [D2 改名](./risk-dispute-channel-rename.sql) → 81 表 **0 差异** |
| 初始化数据 | 各自试运行 ROLLBACK 核对后 COMMIT：CUSTOMER 签名公钥 1 行（`9200000000000201`）；扫描策略 1 + 要求 1；Risk 资料方案 3 / 版本 3（`approved_by=221546852372267008`）/ 要求 10 |
| 基础设施 | MinIO healthy，`minio-init` 建桶 `mor-platform-files`（及 `-test`）与 Platform 账号；CORS 预检：28443、28080 返回对应 Allow-Origin，其他来源不返回；Mailpit healthy |
| Vault | 新策略 `test-deployment-v2` 重启加载；就绪 200、原烟测 STABLE 记录幂等重放 200、未授权调用方访问 `EMAIL_ADDRESS_V1` 403 |
| 应用 | Risk → Platform → CCT → 客户端 BFF → Gateway → 运营 Web → 客户端 Web → Nginx 逐个 `--force-recreate` 至 healthy；16 个容器 healthy |
| 登记与启用 | `E2E_TARGET=test register_onboarding.py`：CLIENT 目标、5 权限、39 路由、5 页面全部 ACTIVE；[管理员授权](./grant-admin-onboarding-permissions.sql) 5 行、PLATFORM_ADMIN v21→v22；Gateway 快照 `targetCount=2, routeCount=188` |
| 验收 | `E2E_TARGET=test acceptance_flow.py --approve` **39 步全部 PASS**：注册→Mailpit 验证→登录/刷新 Cookie→个人申请→资料与联系人（CCT→Vault STABLE）→F01→直传 `minio.mor-test.localhost:29400`→F02→格式检查 AVAILABLE→C12→C16/C14→运营 O01/O02→O10 下载得 `%PDF`→O07 退回→客户看到退回→重提→O05→O03/O04→批准门禁→O08→客户看到批准 |
| 回归 | 运营端 30 条无路径参数的 GET 路由：27 条 200；3 条 400 均为缺必填查询参数（权限选项补 `usage` 后 200；流程列表需 `pointCode`，不存在的点返回 404） |
| 敏感信息 | 16 个容器 30 分钟日志中检索验收登录邮箱、联系邮箱、电话、CUSTOMER 私钥与 HMAC 片段：0 命中 |

## 与清单的偏差

1. **MinIO KMS 密钥。** `compose.sh` 用 bash `read` 逐行读 `.env.test`，值中只有末尾一个 `=` 时会被去掉，base64 填充丢失，MinIO 拒绝启动。改为 `private/minio-kms.env` 经 `env_file` 传入，并从 `.env.test` 删除该项；生成器同步（`06f46b9`）。**既有的 `OPERATIONS_ACCESS_LOG_HMAC_MATERIAL` 同样以单个 `=` 结尾，可能一直被截掉一位**，本次未改动，留给 Gateway/test 环境负责方核对。
2. **健康探针地址白名单。** `LocalHttpHealthProbe` 只允许固定 4 个就绪地址，CCT（18101）与客户端 BFF（19202）返回 64 而 unhealthy。白名单补两项（`06f46b9`），这两个镜像以新探针重建（JAR 相同）。
3. **Vault 烟测脚本。** `vault.py smoke` 借 Platform 容器的 `curl` 发请求，而渠道沙箱发布的新 Platform 镜像已不含 `curl`，脚本在 Nacos 检查后失败（与本次无关，换回旧镜像前就会出现）。改用 Nacos 容器 `curl` 人工核对关键三项（见上表）。脚本待改用其他探针容器。
4. **Vault 镜像。** 运行中的 Vault 镜像（`7906efea…`）与 `mor-test/vault-service:development` 标签（`651a780c…`）不同；本次只重启加载策略，并在候选 Compose 固定运行中的镜像 ID。
5. **`.localhost` 解析。** macOS 系统解析器不解析 `*.localhost`（Chrome 内置解析）；验收脚本在 `E2E_TARGET=test` 下把该后缀映射到 127.0.0.1。**Safari 等不内置解析的浏览器无法直传 MinIO**，浏览器验收请用 Chrome。

## 当前状态与遗留

- test 现运行候选镜像；回退见 [release.md](./release.md) 第 7 节，D2 回退前先执行 [改名回滚](./risk-dispute-channel-rename-rollback.sql)。
- 渠道沙箱候选中 Gateway 的两项 Client 占位已由正式 Client 配置取代；“通道关闭仍校验配置”的 Gateway 缺陷本身未修。
- `deploy.sh` 尚未覆盖 CCT、客户端 BFF/Web、MinIO、Mailpit；本次以候选 Compose 逐个启动。
- `mor_platform` 的 `t_gateway_route`、`t_gateway_target` 有 4 列注释仍为 `PROVIDER_CALLBACK` 旧文案（结构一致，仅注释），属命名统一的遗留。
- 浏览器人工抽查（Chrome：`https://localhost:28443` 注册/上传、`http://localhost:28080` 运营审核与下载）待用户执行。
- test 留有验收数据：`acceptance-20261014152624@example.test` 账号、1 份已批准申请与 2 个审核案件，属于 test 数据，不清理。
- 执行中 test 管理员密码曾出现在本会话的命令输出中（脱敏正则失效），建议用户改一次该密码。
