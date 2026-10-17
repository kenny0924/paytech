# Client API 在 mor-test 联通 — 交付记录

## 适用阶段

评审稿：1—4、6—8（阶段 5 并入阶段 4），见 [requirement.md](./requirement.md) 第 8 节。

## 确认记录

| 阶段 | 日期 | 成果 | 用户原意 | 获准的下一步 |
|---|---|---|---|---|
| 0. 选题 | 2026-10-17 | — | “可以”（选择“Client API 在 mor-test 跑通全链路”） | 阶段 1 需求评审 |
| 1. 需求评审与范围确认 | 2026-10-17 | [requirement.md](./requirement.md) | “可以”：Q1 手工发布 PaymentSucceeded 作标注夹具；Q2 开放 13 个操作（不含存卡）；Q3 接受 Commerce 固定 Mock 为已知限制；Q4 test 内验收用 HTTPS 接收端；Q5 改共享环境前协调 | 阶段 2 现状差异与影响分析（只读） |
| 2. 现状差异与影响分析 | 2026-10-17 | [technique.md](./technique.md) 第 1—4 节 | “按建议”：D1 只开放设计内 10 条路由（Customer 另立需求）；D2 Idempotency-Key 原样转发、不重放；D3 test 上按台账流程启用 CLIENT_API；D4 test CA 证书 + Platform 合并信任库，只放行接收端；D5 Commerce 与 Platform 都登记 XXL-Job；D6 test 部署文件只提交相关段落 | 阶段 3 部署与路由方案 |
| 3. 部署与路由方案 | 2026-10-17 | [technique.md](./technique.md) 第 5—11 节（路由 9 条：结账查询未实现） | “继续” | 阶段 4 研发（本机编写部署文件、接收端、脚本；不碰 test） |
| 4. 研发 | 2026-10-17 | 见下文“阶段 4 研发结果” | “可以”（同时授权阶段 6 动作，见“动作授权”） | 阶段 6 test 发布 |

## 动作授权

| 动作 | 状态 |
|---|---|
| 只读核对 test 环境 | 阶段 1—2 范围内 |
| 本地提交（独立暂存区，不推送） | 2026-10-17 已授权 |
| test SQL：`mor_commerce` 17 表 Schema + 初始化数据；`mor_platform` 补 4 个 Scope（先备份） | 2026-10-17 已授权 |
| test 部署：XXL-Job Admin、Commerce、接收端；Platform 重建（镜像不变，加信任库 / 放行 / 执行器）；Nginx 加 8444 入口 | 2026-10-17 已授权（替换共享服务前协调其他会话） |
| 路由登记、XXL-Job 执行器与任务登记、验收（含 AC-05 暂停 Platform）、发布记录 | 2026-10-17 已授权 |
| 推送 | 未授权 |

## 当前阶段

阶段 6 test 发布进行中。

## 阶段 4 研发结果（2026-10-17，未提交）

| 文件 | 内容 |
|---|---|
| `test-environment/compose.applications.yaml` | 新增 `commerce-service`（172.30.73.28，Snowflake worker 28，`/mor-test`，补偿执行器 9999）；Platform 增加 `WEBHOOK_TRUSTED_NETWORKS=172.30.73.29/32`、合并信任库挂载与 `JAVA_TOOL_OPTIONS`、补偿执行器；Nginx 发布 `127.0.0.1:28444:8444` |
| `test-environment/compose.scheduler.yaml` | xxl-job-admin 增加 `--server.servlet.context-path=`（见偏离 2） |
| `test-environment/compose.acceptance.yaml`（新） | `webhook-receiver`（profile `acceptance`，`python:3.13-alpine` 固定 digest，只读根、非 root，172.30.73.29，不发布宿主机端口） |
| `test-environment/acceptance/webhook-receiver/webhook_receiver.py`（新） | HTTPS 接收端：每个 POST 原样落盘并返回 204，日志只记方法、路径、状态 |
| `test-environment/scripts/prepare_client_api_acceptance.sh`（新） | 用 test CA 签发 `webhook-receiver` 证书；生成 Platform 信任库（JDK cacerts 118 项 + test CA）；产物在 `private/`（不入库） |
| `test-environment/nginx/nginx.conf` | 新增 8444 ssl 入口：只代理 `^~ /api/v1/`，写 `X-Entry-Channel CLIENT_API`、`X-Real-IP`，清空转发头，其余 404 |
| `test-environment/scripts/test_nginx_gateway_problem_boundary.py`、`test_java_image_health_probe.py` | 合同测试纳入 Client API 入口与 Commerce 健康探针 |
| `db/dml-api-scope.sql`（新） | `t_api_scope` 补 4 个 Scope（ID `9300000000000002—05`），含执行前核对与回滚 |
| `verification/register_client_api.py`（新） | Target + 9 条路由：先建（DISABLED）→ 启用 Target → 逐条启用并等待 Gateway `gateway_snapshot_routes{channel="CLIENT_API"}` 加 1，超时停止；`--dry-run` 输出请求体 |
| `verification/client_api_flow.py`（新） | test 专用全链路验收：复用 `commerce_webhook_flow` 的步骤与比对，替换为 Client API 正式入口（Bearer 凭证）、test MySQL（`docker exec`）、test RabbitMQ（backend 网络内发布）与接收端落盘目录；`--outage` 验 AC-05 |

**本机验证**
- `nginx -t`（nginx:1.30.4-alpine，同一证书挂载）通过。
- `compose.sh -f compose.scheduler.yaml -f compose.acceptance.yaml --profile scheduler --profile acceptance config` 通过。
- 接收端：在临时 Docker 网络内用 test CA 校验访问 `https://webhook-receiver:8443/hook` → 204 并落盘 1 个文件；不信任 test CA 的客户端被拒。验证后已清理网络与文件。
- `prepare_client_api_acceptance.sh` 已在本机生成证书（SAN=`webhook-receiver`，`openssl verify` 通过）与信任库（119 项，含 `mor-test-ca`）。
- test-environment 脚本测试：nginx 合同 9/9、健康探针 19/19、部署脚本 31/31、Platform Redis 1/1、Vault 3/3；`test_risk_database_initialization.py` 3 项失败，改动前基线同样失败（既有，与本需求无关）。
- 两个 Python 脚本编译通过；`register_client_api.py --dry-run` 生成 9 条路由。未连 test 运行（阶段 6—7）。

**偏离方案**
1. 未改 `scripts/build.sh`、`deploy.sh`：两者只覆盖 Operations 首切片的固定服务与阶段，近期 CCT、Client Console 等发布都用“干净 worktree 构建 + 候选 Compose 覆盖文件 + `compose.sh`”并写发布记录；Commerce 沿用同一做法（D6 范围相应缩小）。
2. XXL-Job Admin 改为根路径：Foundation 执行器配置要求 Admin 地址不带 Context Path，而镜像默认 `/xxl-job-admin`；test Admin 未运行过，改动无存量影响。界面地址变为 `http://127.0.0.1:28090/`。
3. Nginx 不改写 `X-Request-Id`：Gateway 对 CLIENT_API 已校验并保留合法值、否则重新生成，Nginx 原样透传即可。
4. AC-05 改为“暂停 Platform 期间发布 PaymentSucceeded”：Gateway 凭证校验依赖 Platform，Platform 不可用时无法经 Client API 取消订单；改由 MQ 触发 `order.paid`，暂停时长约 10—40 秒，执行前须通知其他会话。
5. 接收端 `inbox` 目录权限 0777（容器用户 10001 写入宿主机目录），上级目录 0700。

**阶段 6 前须知**
- 需拉取 `xuxueli/xxl-job-admin:3.4.2`（amd64，Apple 芯片下仿真运行）。
- Platform 替换使用当前运行镜像，只改环境变量与挂载；替换前须取得 Webhook 会话等同意。
- Risk 结账评估在 test 上须有生效的 `CHECKOUT_PREPARE` 执行发布，否则建单会被风控拒绝；阶段 6 先只读核对。
