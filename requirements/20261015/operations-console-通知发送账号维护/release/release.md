# Operations 通知发送账号维护：mor-test 发布记录

阶段 6/7。用户 2026-10-15 回复"可以"（D1 A、D2 A）后于 2026-10-16（UTC 2026-10-15 晚）执行，结果见第 6 节。当前状态以 [CURRENT_IMPLEMENTATION.md](../../../../agent-docs/CURRENT_IMPLEMENTATION.md) 为准。

**本次：mor-test 的 Platform、Operations BFF、Operations Web 换成「当前 main + 本需求」构建的镜像；`mor_platform` 加 6 列、补 `SES_PRIMARY` 类型与区域（1 行）、登记 56 行并启用；`mor_vault` 加 1 条规则；Platform 的 Compose 开启 SES 适配器；经 `http://localhost:28080` 验收后清理验收数据。**

## 1. 范围

| 项 | 内容 |
|---|---|
| 代码 | 运行中的三个镜像分别来自 `f84a012`（Platform）、`994d5b0`（BFF）、`88a7807`（Web）；`8d26229..HEAD` 三个目录没有其他提交，替换不会撤掉别人的功能。依赖模块在 `f84a012..HEAD` 只有新增的 Payment / Risk 接口类型与 Vault 客户端错误码（值格式需求已随 Vault 发布） |
| 数据库 `mor_platform` | [ddl.sql](../db/platform-support-service/ddl.sql)（加 6 列，旧 Platform 不受影响）→ [dml.sql](../db/platform-support-service/dml.sql)（`SES_PRIMARY` 补 `AMAZON_SES` 与 `me-central-1`，1 行）→ 初始化 DML 末尾注册块 56 行（默认停用）→ [test-activate.sql](./test-activate.sql)（4/2/1/1） |
| Vault `mor_vault` | 新增规则 `platform-support-service / PROTECT / NOTIFICATION_PROVIDER_CREDENTIAL_V1 / NOTIFICATION_PROVIDER_SETUP`，经管理接口添加（策略版本 11 → 12）；数据类型已是 JSON 值格式，本需求写入的值符合 |
| 配置（Compose） | Platform `NOTIFICATION_SES_ENABLED: "false"` → `"true"`，并更新注释（账号配置在页面维护）。BFF、Web 只换镜像 |
| 不动 | Gateway（新路由由快照动态加载）、Vault 程序、Risk、CCT、Nginx、Client 链路；不推送 Git 与镜像，不碰生产 |

## 2. 只读核对结果（2026-10-15）

| 项 | 结果 |
|---|---|
| 运行镜像 | Platform `493026bf…`（`f84a012`）、BFF `aa546ee6…`（`994d5b0`）、Web `e5df9ddc…`（`88a7807`）、Vault `42d1f5d6…`、Gateway `1cddfeab…` |
| `mor_platform` | `t_notification_provider` 7 列；只有 `SES_PRIMARY`（ACTIVE，版本 1，无发件地址与密钥）；`96xxx` ID 段、编码、路径冲突均为 0；PLATFORM_ADMIN v25；ACTIVE 路由 224；父菜单「发送账号」ACTIVE |
| `mor_vault` | 该数据类型只有 Platform 的 `REVEAL / NOTIFICATION_DELIVERY` 规则；`value_format=JSON`；策略版本 11 |
| Platform 配置 | `NOTIFICATION_ENABLED=true`、`NOTIFICATION_SES_ENABLED=false` |
| 认证邮件 | 开启 SES 后 `SES_PRIMARY` 仍缺发件地址与密钥，注册验证 / 找回密码邮件仍发不出（受理时报"配置不完整"，认证发送统一转为 503，与现在一样）；在页面录入真实发件地址与密钥后才能发出 |

## 3. 需要你决定

**D1 构建基线。**
- 选项 A（建议）：**先本地提交本需求**（阶段 8 的提交提前，只含本需求的行，不推送），再从该提交建干净 worktree 构建。部署的镜像能对应到一个提交号。
- 选项 B：从「当前 main + 本需求未提交改动」的独立工作区构建，阶段 7 之后再提交；镜像只能对应到差异内容。

**D2 验收时是否真的调用 SES。** 开启 SES 后，测试发送会真正请求 AWS SES。
- 选项 A（建议）：合成数据验收**不调用 SES**（只核对按钮可用与页面），真实测试发送留到你录入真实发件地址与密钥之后。
- 选项 B：用合成密钥调用一次 SES，验证"被拒绝"结果（会向 AWS 发出一次签名失败的请求；若 test 容器无外网则为"结果未知"）。

## 4. 执行顺序（任一步不符即停止）

1. **复核**：重跑第 2 节查询；运行镜像不变；冲突仍为 0。
2. **备份**：`mor_platform` 的 `t_notification_provider` 与 6 张登记表（权限、角色授权、角色、菜单、路由、请求头、限流）、`mor_vault` 的规则与策略变更表、`compose.applications.yaml` 副本、三个容器 inspect；存 `~/Code-MoR-backups/20261015-notification-provider-test/<UTC>/`（0700/0600，SHA256SUMS）。
3. **构建**（按 D1 的基线，干净 worktree）：Platform 与 `client-contract-api`、`paytech-payment-api`、`paytech-vault-client` 同 reactor；BFF 与 `platform-support-api`、`risk-api`、`client-contract-api`、`paytech-vault-client` 同 reactor；Web `npm run build`；健康探针从 `test-environment/java/*.java` 编译。镜像 tag `mor-test/<服务>:notification-provider-20261015`，记录镜像 ID 与 jar SHA-256。
4. **数据库（旧 Platform 仍在运行，兼容）**：DDL；DML 先试跑回滚核对 1 行再提交；注册块先试跑回滚核对 56 行再提交。
5. **Vault 规则**：经 `http://localhost:28080` 管理接口新增，核对策略版本 12。
6. **改 Compose 并替换 Platform**（`up -d --no-deps --no-build --pull never --wait`），中断约 30 秒；确认 healthy、日志无启动错误。
7. **替换 BFF，再替换 Web**，各自 healthy；确认 `index.html` 引用新资源。
8. **启用**：[test-activate.sql](./test-activate.sql) 核对 4/2/1/1、PLATFORM_ADMIN v26 后提交；等待 Gateway 加载新路由（ACTIVE 路由 224 → 228）。
9. **验收**：[local_acceptance.py](../verification/local_acceptance.py) 与 [local_browser.py](../verification/local_browser.py) 以 `E2E_TARGET=test` 运行（按 D2 调整测试发送用例）；回归管理员可见页面；Platform、BFF、Gateway 日志不含合成密钥。
10. **清理**：按 [acceptance-cleanup-local.sql](../db/platform-support-service/acceptance-cleanup-local.sql) 删除 `E2E_PROV_` 账号；审计与 Vault 合成密文保留。
11. **记录**：写入本文第 6 节，更新交付记录与台账。

## 5. 恢复

| 情况 | 处理 |
|---|---|
| 第 6 步 Platform 不健康 | 恢复 Compose 副本（SES 关闭），用 `493026bf…` 回退 Platform；新列对旧 Platform 无影响 |
| 第 7 步 BFF / Web 不健康 | 回退 BFF `aa546ee6…`、Web `e5df9ddc…`；新 Platform 兼容旧 BFF / Web（旧页面只读新字段之外的内容） |
| 启用后发现阻断缺陷 | [test-deactivate.sql](./test-deactivate.sql)（4/2/1/1）停用入口，再按上两行回退镜像；Vault 规则在 Vault 管理页停用 |
| 数据 | 新列、56 行登记与 `SES_PRIMARY` 的类型 / 区域保留（旧版本不读取）；回退 DDL 会丢失已录入的密钥 Token 引用，只有确认不再需要时再单独授权 |

## 6. 执行记录

| 步骤 | 结果 |
|---|---|
| 提交（D1 A） | 本地提交 `0301719`（108 个文件，只含本需求；其他会话的 Webhook、风控评估查询等未提交改动未纳入；未推送）。提交前 Payment 会话把 `4304340` 拆成 `35f0554`、`c542498`，本提交在其上 |
| 1 复核 | 三个运行镜像与第 2 节一致；冲突仍为 0 |
| 2 备份 | `~/Code-MoR-backups/20261015-notification-provider-test/20261015T195944Z/`（0700/0600，SHA256SUMS）：`mor_platform` 账号表与 6 张登记表数据、`mor_vault` 规则与策略变更表、Compose 副本、三个容器 inspect |
| 3 构建 | 干净 worktree `0301719`，Platform、BFF 与依赖 API 同 reactor 离线打包；Platform jar SHA-256 前缀 `35e5d947`、BFF `ca24e88c`；Web 构建 lock 与主工作区一致；健康探针现编译。镜像 `notification-provider-20261015`：Platform `sha256:070ccc41…`、BFF `sha256:ae568bd0…`、Web `sha256:6854f890…`，ID 见 [compose-candidate.yaml](./compose-candidate.yaml)；制品在 `test-environment/artifacts/notification-provider-20261015/`（已忽略） |
| 4 数据库 | DDL 完成；DML 试跑 1 → 回滚，正式 1 行（`SES_PRIMARY` → `AMAZON_SES` / `me-central-1`，v2；DML 文件不含 COMMIT，第一次正式执行因未提交而回滚，随后显式 COMMIT）；注册块试跑 56 → 回滚 0 → 正式 56，按钮中文名正常 |
| 5 Vault | 经管理接口新增 `platform-support-service / PROTECT / NOTIFICATION_PROVIDER_CREDENTIAL_V1 / NOTIFICATION_PROVIDER_SETUP`，策略版本 11 → 12 |
| 6 Platform | Compose `NOTIFICATION_SES_ENABLED` 改为 `"true"` 并更新注释；20:02:24Z 开始，20:02:49Z healthy（中断约 25 秒），启动日志无 ERROR |
| 7 BFF / Web | BFF 20:02:55Z–20:03:12Z、Web 20:03:12Z–20:03:24Z，均 healthy；入口 `index.html` 引用新构建 `index-CVn1yT7u.js` |
| 8 启用 | [test-activate.sql](./test-activate.sql) 试跑回滚后提交：路由 4、按钮 2、授权 1、角色 1；PLATFORM_ADMIN v26；ACTIVE 路由 224 → 228 |
| 9 验收（D2 A） | 接口 **14/14**（录入密钥经真实 Vault Protect；SES 适配器已注册，合成数据不调用 SES）；浏览器 **11/11**（[截图](../assets/stage7/)，测试发送只核对弹窗不发送）；只读回归 **28 页**无 ≥400 请求、无控制台错误；Platform、BFF、Gateway、Nginx 自验收开始的日志无合成密钥，Platform 无 ERROR |
| 10 清理 | 删除 `E2E_PROV_` 账号 2 个；账号表只剩 `SES_PRIMARY`（AMAZON_SES、me-central-1、无发件地址与密钥、ACTIVE）；审计事件与 Vault 合成密文保留 |

**当前 test 状态：** Platform / BFF / Web 为 `0301719` 镜像，SES 适配器已开启；运营后台「发送账号」可新建、编辑、录入密钥与测试发送。`SES_PRIMARY` 录入真实发件地址与密钥（并在 SES 侧完成发件身份验证）后，注册验证与找回密码邮件即可发出；在此之前与部署前一样发送失败只计指标。回退按第 5 节：Platform `493026bf…` 需配合备份中的 Compose 副本，BFF `aa546ee6…`、Web `e5df9ddc…`。
