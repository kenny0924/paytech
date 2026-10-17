# Client Webhook 投递：mor-test 发布清单

阶段 6（评审稿，2026-10-16）。确认后执行阶段 7，结果写入第 6 节。当前状态以 [CURRENT_IMPLEMENTATION.md](../../../../agent-docs/CURRENT_IMPLEMENTATION.md) 为准。

**本次：mor-test 的 Platform 换成从 `ee469e0` 所在 HEAD 构建的镜像；`mor_platform` 建 5 张 Webhook 表、登记并启用 2 个运营权限；`mor_vault` 新增 1 个数据类型与 2 条规则；Platform 的 Compose 开启 Webhook 并登记 `commerce-service` 调用方；经私网直接调用 Platform 完成验收后清理验收数据。**

## 1. 范围

| 项 | 内容 |
|---|---|
| 代码 | 运行中的 Platform 来自 `21a1a51`（`client-onboarding-20261016`，`sha256:b76d2b4e…`）。`21a1a51..HEAD` 在 Platform 及其依赖目录（Foundation、Build、Vault Client、CCT/Payment API、Risk）只有本需求的 `ee469e0`，替换不会撤掉别人的功能 |
| 数据库 `mor_platform` | `changes/2026-10-15-webhook-delivery.sql`（建 5 张空表，旧 Platform 不受影响）→ 初始化 DML 末尾「Client Webhook management permissions」块（4 行，权限 ACTIVE、授权 REVOKED）→ [test-activate.sql](./test-activate.sql)（授权 2、角色 1） |
| Vault `mor_vault` | 经运营后台 Vault 管理接口新增数据类型 `WEBHOOK_SIGNING_SECRET_V1`（REFERENCE、SECRET、REGEX `^whsec_[A-Za-z0-9_-]{43}$`，不允许人工加解密），以及规则 `platform-support-service / PROTECT` 与 `/ REVEAL`，用途均为 `WEBHOOK_SIGNING`；策略版本 +3 |
| 配置（Compose） | Platform 增加 `WEBHOOK_ENABLED: "true"`，以及 `SPRING_APPLICATION_JSON` 登记调用方 `commerce-service` → V1 八种事件类型（为后续 Commerce 接入准备）。`worker-id` 不需要（已开启人工认证，共用其生成器）；补偿执行已开启 |
| 不动 | Gateway、Operations BFF / Web（无新路由或页面）、Vault 程序、CCT、Risk、Nginx、Client 链路；不推送 Git 与镜像，不碰生产 |

## 2. 只读核对结果（2026-10-16）

| 项 | 结果 |
|---|---|
| 运行镜像 | Platform `b76d2b4e…`（`21a1a51`），healthy |
| `mor_platform` | 无 `t_webhook_*` 表；权限 ID `9000000000000901xx/902xx` 与编码 `WEBHOOK_READ/MANAGE` 冲突 0；PLATFORM_ADMIN 版本 27；补偿任务表为空 |
| `mor_vault` | 无 `WEBHOOK_*` 数据类型 |
| Platform 配置 | `COMPENSATION_ENABLED=true`、`OPERATIONS_AUTHENTICATION_ENABLED=true`，未设置任何 `WEBHOOK_*` |
| 网络 | Platform 在 `mor-test-backend`（`172.30.73.0/24`）；test 无可用的公网 HTTPS 接收端 |

## 3. 需要你决定

**D1 投递成功路径怎么验收。** 生产规则只投递到公网 HTTPS 地址，test 里没有这样的接收端（不向外网真实网站推送）。
- 选项 A：只验收受理、管理、密钥轮换、内网地址拦截与失败重试，不验证“送达成功”。
- 选项 B（建议）：在 `mor-test-backend` 临时起一个接收容器（`nginx:1.30.4-alpine`，固定 `172.30.73.240`，`POST /hook` 返回 204 并在访问日志记录签名 Header），Compose 临时放行 `http` 与 `172.30.73.240/32` 完成送达、测试事件与 Replay 验收；验收后撤掉放行项并再重启一次 Platform、删除接收容器。多一次约 30 秒中断。
- 选项 C：同 B，但把接收容器与放行项长期保留在 test，方便以后 Commerce 联调。

**D2 验收数据。** 建议插入一个合成 CLIENT 租户（`880000000000079001`，owner `880000000000079101`）做验收，结束后按 [acceptance-cleanup.sql](./acceptance-cleanup.sql) 删除其 Webhook 数据与租户；审计事件与 Vault 合成密文保留。不碰真实 test Client。

## 4. 执行顺序（任一步不符即停止）

1. **复核**：重跑第 2 节查询；运行镜像不变；冲突仍为 0；本机无其他会话的 Platform 测试或部署在进行。
2. **备份**：`mor_platform` 的 `t_permission`、`t_role_permission`、`t_role`，`mor_vault` 的数据类型、规则与策略变更表，`compose.applications.yaml` 副本，Platform 容器 inspect；存 `~/Code-MoR-backups/20261016-webhook-test/<UTC>/`（0700/0600，SHA256SUMS）。
3. **构建**：从 HEAD 建干净 worktree，Platform 与依赖 API 同 reactor 离线打包（JDK 25，`-DskipTests -Djacoco.skip=true`），健康探针从 `test-environment/java/*.java` 编译；镜像 tag `mor-test/platform-support-service:webhook-20261016`，记录镜像 ID 与 jar SHA-256，写入 `compose-candidate.yaml`。
4. **数据库（旧 Platform 仍在运行，兼容）**：建表 → 核对 5 表 56 字段；注册块先试跑回滚核对 4 行再提交。
5. **Vault**：经 `http://localhost:28080` 管理接口新增数据类型与 2 条规则，核对策略版本 +3。
6. **改 Compose 并替换 Platform**（`up -d --no-deps --no-build --pull never --wait`）：加 `WEBHOOK_ENABLED`、`SPRING_APPLICATION_JSON`（D1 选 B/C 时同时加放行项）；确认 healthy、日志无启动错误。
7. **启用**：[test-activate.sql](./test-activate.sql) 试跑回滚核对 2/1 后提交；PLATFORM_ADMIN 版本 28。
8. **验收**（经接入 `mor-test-backend` 的临时 curl 探针容器直连 Platform；管理接口用 test 管理员登录得到的账号与会话标识作为可信 Header）：
   - 管理：新建端点返回一次密钥；内网地址（如 `http://10.0.0.1/hook`）保存被拒；列表不含密钥；修改、启停；无权限 403。
   - 受理：`commerce-service` 提交合法事件 204、重复 204、改内容 409、未登记调用方 400、未开通 Client 400。
   - 投递（D1 B/C）：送达 204、接收端日志有 `Code-MoR-Signature`；轮换后测试事件带两个 `v1`；Replay 生成新投递并送达。（D1 A：仅验证解析到非公网地址的端点记 `BLOCKED_ADDRESS` 并进入重试。）
   - 日志：Platform 自验收开始的日志不含密钥明文，无 ERROR。
9. **收尾**：D1 选 B 时撤掉放行项并重启 Platform、删除接收容器；按 [acceptance-cleanup.sql](./acceptance-cleanup.sql) 清理合成租户。
10. **记录**：写入第 6 节，更新交付记录与台账。

## 5. 恢复

| 情况 | 处理 |
|---|---|
| 第 6 步 Platform 不健康 | 恢复 Compose 副本，用 `b76d2b4e…` 回退 Platform；5 张新表与权限行对旧 Platform 无影响 |
| 启用后发现阻断缺陷 | [test-deactivate.sql](./test-deactivate.sql)（2/1）停用运营授权；Compose 去掉 `WEBHOOK_ENABLED`（默认关闭，受理与投递都不启用）后重启；必要时回退镜像 |
| Vault | 数据类型与规则在 Vault 管理页停用 |
| 数据 | 5 张表与权限行保留（旧版本不读取）；删除表只在确认不再需要时另行授权 |

## 6. 执行记录（2026-10-16 阶段 7，用户“可以”：D1=B 临时接收端，D2 合成租户）

| 步骤 | 结果 |
|---|---|
| 1 复核 | 运行 Platform 仍为 `b76d2b4e…`（`21a1a51`）；冲突 0；无其他会话 Platform 测试或部署 |
| 2 备份 | `~/Code-MoR-backups/20261016-webhook-test/20261015T211115Z/`（权限/角色表、Vault 三表、Compose 副本、容器 inspect，SHA256SUMS） |
| 3 构建 | 干净 worktree（`3cbbfce`，Platform 及依赖与 `ee469e0` 一致）离线打包；jar SHA-256 前缀 `d308d78353b04d87`；镜像 `mor-test/platform-support-service:webhook-20261016` = `sha256:63ae4cde4c098110ee8302554cc2a8f102e0c29f8b60abed8c387781585775a3`，写入 [compose-candidate.yaml](./compose-candidate.yaml) |
| 4 数据库 | 建 5 表、核对 56 字段；权限块试跑回滚核对 4 行后提交。一次截取范围过大误带入通知会话的注册块，因主键冲突整体失败并回滚，核对数据未变 |
| 5 Vault | 经 Operations 管理接口新增 `WEBHOOK_SIGNING_SECRET_V1` 与 PROTECT/REVEAL 两条规则，策略版本 12→15 |
| 6 替换 Platform | 21:14:16–21:14:40 UTC healthy，启动无 ERROR；临时接收容器 `mor-test-webhook-receiver`（`172.30.73.240`，`/hook`→204、`/fail`→503）与两行临时放行 |
| 7 启用 | [test-activate.sql](./test-activate.sql) 试跑后提交：授权 2 行 ACTIVE，PLATFORM_ADMIN 版本 27→28 |
| 8 验收 | 合成租户 `880000000000079001`；探针容器直连 Platform，**24/24 通过**（见下） |
| 9 收尾 | 删除两行放行并重启（21:22:11 UTC healthy，镜像不变，无 ERROR），重启后接收端 http 地址保存被拒 400；删除接收容器；执行 [acceptance-cleanup.sql](./acceptance-cleanup.sql)，5 表、Webhook 补偿任务与合成租户均为 0 行 |

**验收项（24/24）：** 缺会话 Header 被拒；内网 `http://10.0.0.1/hook` 保存 400；新建 201 返回一次密钥且 `no-store`；列表不含密钥；测试事件 204；`commerce-service` 提交 204、送达 SUCCEEDED、重复 204、改内容 409、未登记调用方 400、未开通 Client 400；接收端收到 `t=…,v1=…` 签名与 `Code-MoR-Webhook/1` UA；宽限轮换 rotationNo=2 且 `no-store`，之后测试事件带两个 `v1`；投递列表/详情；Replay 201 并送达；改地址到 `/fail` 后尝试记 FAILED/503/HTTP_STATUS、投递进入 RETRY_WAIT；停用 200；验收期间 Platform 日志无密钥明文、无 ERROR。

**未覆盖与发现：**
- 无权限 403：test 没有不带 Webhook 权限的 Operations 账号，未在环境验证（自动化测试覆盖）。
- 签名值未在环境用密钥重算（接收端只记 Header）；HMAC 正确性由自动化测试独立验签覆盖。
- **mor-test 未配置 XXL-Job 补偿扫描调度**：投递首次尝试由提交后唤醒执行，进入 RETRY_WAIT 后到期也不会再执行（验收中到期 2 分半仍未重试）。Commerce 接入或需要验证重试/DEAD 前须先为 Platform 配置扫描调度。
- 验收审计事件与 Vault 中两条合成密钥密文按 D2 保留。

**当前 mor-test 状态：** Platform `63ae4cde…`（Webhook 开启，仅 https 公网地址，调用方 `commerce-service`）；5 张表空；运营授权 ACTIVE。回退按第 5 节。

**后续（2026-10-16）：** Client Console 会话将 mor-test Platform 替换为 `sha256:86819a6e…`（从 `9ae668a` 构建，含 `ee469e0`），Compose 中 Webhook 配置保留，启动后 40 分钟内日志无 Webhook / Vault ERROR（该会话核对）。
