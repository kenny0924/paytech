# VER 报告 · 阶段 5 本机综合验证

- 执行：VER（Opus 5.5），主工作区 `/Users/kenny/Desktop/Workspace/Code-MoR`，2026-10-20 06:00–06:33Z。
- 约束遵守：未执行任何 git 写命令，未调用 Superpowers skill，未碰 mor-test、Nacos 共享实例与 RabbitMQ 共享 vhost，未改产品代码。只停启了主会话批准的 platform、channel-connector、ops-bff 三个启动器进程，另起本人的 payermax-mock。
- 结论：Platform 定向 37/37、全量 auto 884/884；本机 SQL 全部按预期执行；接口验收 **103/103**；全链路 **5/5**，执行真实到达 payermax-mock；浏览器验收 **35/35**，截图 15 张对应画板 01–15；验收数据已清理。**未发现产品缺陷**，有 2 条观察（第 7 节）。

## 1. 单元与门禁（步骤 1）

| 项 | 命令（`paytech/` 下，JDK 25，`-o`） | 结果 |
|---|---|---|
| Platform 定向 11 类 | `./mvnw -o -pl platform-support/paytech-platform-support-test -am -Dtest=IntegrationLookupApiTest,IntegrationLookupInternalAuthTest,InternalAuthDualNotificationTest,InternalAuthDualOutboundTest,InternalAuthDualWebhookTest,InternalAuthRequiredExemptionTest,InternalAuthRequiredNotificationTest,InternalAuthRequiredWebhookTest,InternalJwksTest,InternalTokenIssueTest,StandardApiExecutionTest -Dsurefire.failIfNoSpecifiedTests=false test` | **37 例，0 失败 / 0 错误**，BUILD SUCCESS（06:02:11–06:02:51Z） |
| Platform 全量 auto | 同上，`'-Dtest=%regex[com/paytech/platform/support/auto/.+Test.class]'` | **884 例，0 失败 / 0 错误 / 0 跳过**，BUILD SUCCESS（06:02:51–06:09:16Z） |
| PDManer | `node scripts/pdmaner/generate-channel-connector-model.mjs --check` | `Channel connector model is up to date (18 tables)` |
| requirements-site | `npm --prefix requirements-site test` | 137 例 127 通过 10 失败；同样 10 例在 `git archive HEAD` 干净导出上也失败（Commerce Client API、Payment 退款、CURRENT_IMPLEMENTATION 快照、测试指南等文档断言），**不是本需求引入** |
| ops-web build | `npm --prefix operations-console-web run build` | 通过（既有 chunk > 500 kB 警告） |

channel-connector-test 187、arch-rules 64、ops-web 1136、ops-bff 1711 由主会话在主工作区跑过，见 delivery.md；本线没有重跑。

运行前 `pgrep -f platform-support-test` 命中 3 个进程，都是 10-13 / 10-14 起的旧 e2e harness（`PlatformE2e.java`、`PlatformPayerMaxE2e.java`、`PayerMaxMock.java`），不是 Platform 测试 JVM。另一会话 Code-MoR-fin4 当时在跑 ledger / billing / BFF 测试，用的是 `*_fin3` 库，不涉及 `mor_platform`。

## 2. 构建制品与服务（步骤 2、4）

- `./mvnw -o -q -pl channel-connector/paytech-channel-connector-app,platform-support/paytech-platform-support-app,operations-console-bff/paytech-operations-console-bff-app -am -DskipTests package` 通过；`npm --prefix operations-console-web run build` 通过。
- 主会话批准后，用 `run-service.sh stop` 停了 10-19 起的旧实例：platform 64440、channel-connector 63547、ops-bff 64775。只替换这三个 exec JAR 到 `local-environment/e2e/jars/`，再用 `run-service.sh start` 启动，Health 均为 UP。gateway 64850、ops-web 64950、billing / ledger / account 未动。

| 服务 | 新 PID | JAR sha256 |
|---|---|---|
| platform | 50814 | `51b5b8a91fcbeb5a9e3a1c1f15d626e82a061987e749abde5c10e60dee8a0e04` |
| channel-connector | 50957 | `d2fd27c69ff70c8579dd060648d919a24d53ca246ad5fa44863156c7dd77ccf4` |
| ops-bff | 51063 | `36fb4714127a22f1ce10d3532ff1e46f810971de11914d7a6225016c9ecdde73` |
| payermax-mock | 46869 | 本人启动（`requirements/20261014/.../PayerMaxMock.java`，端口 18190） |

- 本机缺 `mor_payment_v2e2e`、`mor_acquiring_v2e2e`、`mor_card_v2e2e`、`mor_client_master_v2e2e`，本机 v2e2e 只有 platform / channel_connector / billing / ledger / account。payment、acquiring、card、client-master 因此没有启动，见第 5 节。

## 3. 本机 SQL 执行记录（步骤 3）

客户端 `mysql -h127.0.0.1 -P13306 -uroot --default-character-set=utf8mb4`，root 口令从 `.env.local` 读入 `MYSQL_PWD`，不打印。

| 时间（UTC） | 库 | 文件 | 前置核对 | 结果 | 回滚 |
|---|---|---|---|---|---|
| 06:03:38 | （误入）`mor_channel_connector` | `db/channel-connector/ddl.sql` 原文 | — | 文件第 8 行 `USE mor_channel_connector;` 把会话切到 auto 测试库；`CREATE` 因表已存在报 1050，**未做任何修改** | 不需要 |
| 06:03:45 | `mor_channel_connector_v2e2e` | 同文件去掉 `USE` 行 | 17 张表，无 `t_channel_config_change` | CREATE 1 张，0 行；现 18 张表，中文注释正常 | `DROP TABLE t_channel_config_change;` |
| 06:03:59 | `mor_vault` | `db/vault/dml.sql` | 规则 9100000000000720 与 caller/action/type/purpose 组合均不存在；最大策略版本 20 | 访问规则 1 行、策略变更 1 行（版本 21） | `DELETE FROM t_vault_access_rule WHERE access_rule_id=9100000000000720; DELETE FROM t_vault_policy_change WHERE policy_change_id=9100000000000720;` |
| 06:04:16 | `mor_platform_v2e2e` | `db/platform-support-service/registration-dml.sql`（块 92） | 块 92 ID 段 0 行；角色 900000000000000510 PLATFORM_ADMIN 为 ACTIVE | 402 行：权限 7、授权 7、资源 9、路由 31、Header 138、Query 24、限流 186 | 按 ID 900000000000092101–092999 先子后父删除（见文件头注） |
| 06:04:23 | `mor_platform_v2e2e` | `db/platform-support-service/activation-local.sql` | 路由与资源为 DISABLED，授权为 REVOKED | **31 / 9 / 7 / 1**，与预期一致 | 同组 ID 反向置 DISABLED / REVOKED，version+1，角色 version+1 |
| 06:21:42 | `mor_platform_v2e2e` | local-e2e `sql/51-platform-external-api-routes.sql`（主会话另行授权；在仓库根执行，SOURCE 用相对路径） | 0 / 0 / 1 / 1，与文件头一致 | 注册 30 + 14 条路由；启用 **routes 44 / resources 5 / grants 2 / role 1** | 文件头注：`20260914/.../local-e2e/deactivate.sql` 的语句（库名改为本库），再按两个注册文件头注删除 |

- 本机 Vault 容器经 `VAULT_DB_DSN` 从 `mor_vault` 读策略（没有挂载 JSON），所以执行了 DML。`policy.local.json` 的改动保持不变。
- Gateway Snapshot 路由数从 257 依次变为 288（加块 92）、332（加 51）。
- 经运营控制台做的 Platform 配置（`verification/platform_payermax_setup.py`，复用 `channel_setup.py` 的 Platform 半段，80 次管理调用）：
  - 先经真实入口删除了复制库里不完整的旧配置：接入方 `PAYERMAX` 及其 2 个实现、2 个原子 API、2 个脚本，标准 API `PAY` / `PAY_QUERY`。
  - 再登记接入方 `PAYERMAX_MOCK`：出站地址 `http://127.0.0.1:18190/aggregate-pay/api/gateway`，标记沙箱。
  - 再登记标准 API PAY（卡直付）、PAY_QUERY、REFUND、REFUND_QUERY。
  - 这些 Platform 配置保留在本机，供后续 e2e 使用。

## 4. 控制台接口验收（步骤 5）

脚本：`verification/local_acceptance.py`。入口为 Nginx `https://127.0.0.1:18444`，链路 Gateway → Operations BFF → channel-connector / Platform / Vault。主渠道编码为 `E2E_CHANNEL_CODE=PAYERMAX_MOCK`，等于 Platform 接入方编码。

- **结果：103 / 103 通过**。运行记录 `local-environment/e2e/runtime/channel-acceptance-20261020102625.json`。
- **31 条路由全覆盖：**
  - 渠道 R01–R05：新建、重复 409、422、无接入方激活被拒并给出 detail、DRAFT→SUSPENDED 被拒、激活、改名、列表筛选、未登记 Query 400、改名带 channelCode 400、关闭、关闭后改名或激活被拒。
  - 账号 R06–R11：新建、OE 422、关闭渠道下新建被拒、无 Token 激活被拒、写入参数与凭据、同内容重放复用 Token、详情无凭据、编辑、携带 entityNo 400、激活、列表。
  - MID R12–R16：新建、外部 MID 重复 409、OE 不一致被拒、跨 OE 改绑被拒、跨渠道改绑被拒、带 entityNo 400、同 OE 改绑、账号未激活时 MID 激活被拒、改回后激活、externalMidCode 前缀命中与不命中、按渠道筛选。
  - 能力 R17–R20：目录、新建 5 明细、六维重复 409、类型不一致、未注册代码、MCC 422、整组替换、停用前后候选查询对比、重复停用被拒、MID 详情能力组。
  - 变更 R21：渠道 CREATE / STATUS / UPDATE；账号 CONFIGURATION / STATUS；MID REBIND ×2 / STATUS；操作者为会话 Account ID；缺 targetId 400。
  - 成本 R22–R31：
    - 渠道不存在时建方案被拒；建方案；编制人等于会话账号；提交。
    - 列表的待审批计数与渠道名联查；状态筛选。
    - 自批被拒；摘要不一致被拒；复核人（无 COST_MANAGE）编制返回 403；另一账号批准后为 ACTIVE；ACTIVE 作废被拒。
    - V2 未到期批准后为 APPROVED；双向对比；作废为 INACTIVE，库中状态核对；作废原因进变更记录。
    - V3 驳回为 REJECTED；方案详情的版本状态与计数；版本详情。
- **凭据：**
  - 合成 PayerMax 的 appId、merchantNo、keyVersion、payermaxPublicKey 与 merchantPrivateKey 经真实 Vault Protect 写入。
  - 库里只有 Token，整行不含明文；`mor_vault.t_vault_record` 有记录，类型为 `CHANNEL_ACCOUNT_CONFIG_V1`。
  - 响应与详情只有末 4 位；ops-bff 与 channel-connector 日志不含任何参数或凭据值。
- **授权：**
  - 管理员导航含「渠道」目录、3 页与 5 个按钮，子菜单顺序正确。
  - IAM 合成的只读审计人员（权限 `OPERATIONS_AUDIT_READ`）看不到任何 `OPERATIONS_CHANNEL*`；对 4 个 GET 和 1 个 POST 调用都返回 403。
- 前两次运行因脚本自身问题失败，已修正：第一次是 `size=1` 不是合法分页，第二次是范围组成在无固定费时仍带币种。两次运行的数据都用 `--cleanup` 清除并核对过。

## 5. 替代种子的全链路（步骤 6，AC-B05）

脚本：`verification/local_chain.py`。读取上一步的运行记录，**不执行** `50-channel-connector-payermax-mock.sql`，**不运行** `channel_setup.py`。

| 检查 | 结果 |
|---|---|
| 候选查询（Payment 选路调用的同一合同 `POST /channel-connector/v1/mid-candidates`，AE001 / CARD / US / USD / 5999） | 返回控制台登记的 MID 238186207887032320、账号 238186201436192768，`[PAY, REFUND]` |
| 种子渠道 9500000000000001 不存在 | 0 行 |
| 渠道执行 REFUND（Payment 执行调用的同一合同 `POST /channel-connector/v1/operations`，冻结渠道、账号、MID） | `SUCCESS`、`REFUND_SUCCESS`；Platform 以 `CHANNEL_EXECUTION` Reveal 控制台写入的 Token |
| payermax-mock 流水 | 收到 1 笔 refund，`signatureValid=true`、`envelopeValid=true`：Platform 用控制台 Protect 的私钥签名 |
| 渠道执行 PAY | 到达 Platform，返回 `NOT_SENT / INTEGRATION_INITIALIZATION_FAILED`。PAYMENT_CARD 初始化器要读 card-service，本机未运行，属预期 |

**结果：5 / 5 通过。**

**限制：** 本机没有 Payment / acquiring / card / client-master 的 v2e2e 库，建库与建表不在本需求 SQL 授权内，因此 Payment 服务进程没有参与。本步按 Payment `PaymentChannelClientImpl` / `PayInput` 的请求形态直接调用它使用的两个 channel-connector 合同。按主会话决定，**AC-B05 的 Payment 侧（真实卡支付经 Payment 选到该 MID）留到 mor-test 阶段 7 验证**。

**既有脚本硬编码的差异：** `card_smoke.py` 等脚本经 `channel_setup.CHANNEL_ID / ACCOUNT_ID / MID_ID` 写死了种子 ID 9500000000000001 / 101 / 201。最小改法是改为读取 `channel-acceptance-*.json` 的 `channelIds.main` / `accountIds.main` / `midIds.main`，或像 `local_chain.py` 一样直接传入。原脚本没有改。

## 6. 浏览器验收（步骤 7）

脚本：`verification/local_browser.py`（Playwright Chromium，1440×900）。**结果：35 / 35 通过**。截图在 `assets/stage5/`，文件名与 Pencil 画板编号一一对应：

| 截图 | 画板 | 验证点 |
|---|---|---|
| 01-渠道列表 | 01 | 列表含 PAYERMAX_MOCK（ACTIVE），行主动作为「暂停」；空列表文案 |
| 02-渠道详情-账号 | 02 | 账号行显示已配置与 Token 末 4 位；标题区 ACTIVE 显示暂停 / 关闭，无激活 |
| 03-新建渠道 | 03 | 非法编码提交前内联提示；创建后进入 DRAFT 详情，只有激活 / 关闭 |
| 04-账号配置-参数与凭据 | 04 | 已配置 + Token 末 4 位 +「重新填写」；凭据为 password 输入；没有任何回显 |
| 05-渠道激活被拒 | 05 | 弹窗顶部显示「Platform 不存在同编码接入方: BROWSER_…」 |
| 06-MID列表 | 06 | 外部 MID 前缀筛选命中 |
| 07-MID详情-能力组与明细 | 07 | USD 为 ENABLED，EUR 为 DISABLED |
| 08-新建能力组 | 08 | MCC 非四位数字内联提示；未选能力代码时「保存」禁用 |
| 09-渠道成本方案列表 | 09 | 方案在列表中 |
| 10-方案详情-版本列表 | 10 | ACTIVE / INACTIVE / REJECTED 行都没有「作废」 |
| 11-版本编制-范围与组成 | 11 | 添加范围 / 组成；MID 下拉限本渠道；保存并提交审批后为 PENDING_APPROVAL |
| 12-版本对比 | 12 | 新增 / 修改行底色与标签，字段级差异 |
| 13-渠道详情-变更记录 | 13 | 创建、状态变更 DRAFT→ACTIVE、修改，含操作者与时间 |
| 14-批准版本确认 | 14 | 内容摘要；编制人自批时弹窗显示「复核人不能与编制人相同」 |
| 15-作废版本确认 | 15 | 原因必填的内联提示；IAM 合成复核人批准后出现「作废」；作废后为 INACTIVE |

- **其余检查：** 「渠道」菜单与三个子项及顺序；三页标题与路由；CLOSED 渠道没有任何编辑或状态按钮（AC-V04）。
- **控制台错误：** 只出现 2 次预期的 400（激活被拒、自批被拒），以及既有 `App.tsx` 的 antd Spin `tip` 弃用警告，后者不是本需求代码。
- **调试经过：** 前三次运行因选择器或合成角色编码重复失败。第三次留下一个 PENDING 的 v5，已由合成复核人驳回为 REJECTED。全部数据最终统一清理。

## 7. 清理（步骤 8）

- **命令：** `python3 -I verification/local_acceptance.py --cleanup`，06:32:44Z 执行 `verification/acceptance-cleanup-local.sql`。只按运行记录里的 ID 删除：channel-connector 段以 channel_connector_app 执行，Platform 段以 platform_app 执行。
- **channel-connector：** 删除 7 个渠道（含浏览器新建的）及其账号、MID、能力组与明细、方案 PMX_E2E_0F2D21 的版本 / 范围 / 组成，以及全部相关变更记录。核对后 10 张相关表均为 0 行，与运行前一致。
- **Platform：** 删除 7 个 IAM 合成人员（两份运行记录合计）的会话、刷新代际、挑战、口令、成员与成员角色、账号，以及 7 个合成角色和它们的授权。核对均为 0 行。管理员本人的登录会话与 `t_audit_event` 审计留痕保留。
- **Vault：** 不删。本次两轮验收各产生 1 个 Token（同内容重放复用同一 Token）；`mor_vault.t_vault_record` 中 `CHANNEL_ACCOUNT_CONFIG_V1` 现有 3 条。
- **保留：** 块 92 + 激活（31 条路由仍为 ACTIVE）、Vault PROTECT 规则、sql/51 路由，以及 Platform `PAYERMAX_MOCK` 接入方与标准 API 配置。

## 8. 验收标准映射

| AC | 结论 | 证据 |
|---|---|---|
| AC-A01 | 本机通过；「mor-test 现有合成渠道的方案」留阶段 7 | R22 筛选、待审批计数、渠道名；R23 详情；截图 09、10 |
| AC-A02 | 通过 | R26 编制、编制人、范围增删；R27 提交为 PENDING；MID 下拉限本渠道（截图 11） |
| AC-A03 | 通过 | API 与浏览器的自批被拒 detail；到期批准为 ACTIVE，未到期为 APPROVED |
| AC-A04 | 通过（到期 Job 未在本机运行） | 未到期 APPROVED 作废为 INACTIVE，ACTIVE 没有入口且接口拒绝；库中 INACTIVE 不会被 `selectDueApproved`（只选 APPROVED）命中，Job 行为由 CC-A auto 覆盖；本机 XXL 执行器已禁用 |
| AC-A05 | 通过 | R31 双向对比；截图 12 |
| AC-A06 | 通过 | 渠道不存在返回「渠道不存在」 |
| AC-B01 | 通过 | 有接入方时激活成功，无接入方被拒（API + 截图 05） |
| AC-B02 | 通过 | 真实 Vault Protect，库中只有 Token，不回显，无 Token 不能激活，日志无泄漏 |
| AC-B03 | 通过 | 同 OE 改绑成功，跨 OE / 跨渠道被拒，OE 字段被拒（400） |
| AC-B04 | 通过 | 六维重复 409、类型不一致、未注册代码；停用后 EUR 候选查询不再返回 |
| AC-B05 | 本机通过（channel-connector → Platform → mock）；Payment 进程侧留阶段 7 | 第 5 节 |
| AC-B06 | 通过 | R21 与截图 13（操作者、时间、状态变化） |
| AC-C01 | 本机部分通过 | 自动测试与构建见第 1 节和 delivery.md；未授权账号无菜单且返回 403；mor-test 页面回归留阶段 7 |
| AC-V01 | 通过 | 菜单、标题、空状态文案（截图 01–15） |
| AC-V02 | 通过 | 渠道编码、OE（API 422）、MCC、作废原因的内联提示；提交失败显示 Owner detail |
| AC-V03 | 通过 | password 输入、已配置 + 末 4 位、「重新填写」（截图 04） |
| AC-V04 | 通过 | DRAFT 显示激活 / 关闭，ACTIVE 显示暂停 / 关闭，CLOSED 无操作 |

## 9. 缺陷与观察

本次验证**未发现产品缺陷**。观察如下（不阻塞，列给主会话）：

1. `db/channel-connector/ddl.sql` 第 8 行 `USE mor_channel_connector;` 是 mor-test 执行文件的目标库，按主会话决定保留。在本机 v2e2e 执行时去掉该行，是正确的本机做法（见第 3 节）。
2. 浏览器中任何 4xx 都会被 antd / 浏览器记为 console error，所以预期的业务拒绝也会出现在控制台。此外 `App.tsx:35` 的 `Spin tip` 已弃用（既有代码，非本需求）。

## 10. 遗留

1. AC-B05 的 Payment 侧、AC-A01 的 mor-test 合成渠道、AC-C01 的 mor-test 页面回归，都留到阶段 6–7。
2. 本机 e2e 若要跑真实卡支付链路，需要另行授权：建立 payment / acquiring / card / client-master 等 v2e2e 库（`apply-e2e.sh databases schemas`）。
3. 部署前置（沿用 delivery.md 遗留 3）：内部 Token 开启的环境要把 `channel-connector-service` 加进 Platform `internal-token.issuer.callers`，并让 channel-connector 入站接受 `operations-console-bff`。本机 internal-auth 为 OFF，未验证这一项。
4. 本线运行期间，本机 Vault 库口令曾在 VER 的一次工具输出中出现。已用该值核对本需求目录、截图、运行记录与临时目录，均未写入任何文件，无需清除；如需要可轮换本机口令。

## 11. 交付文件

- `verification/local_acceptance.py`：接口验收与 `--cleanup`
- `verification/local_chain.py`：AC-B05
- `verification/local_browser.py`：浏览器验收
- `verification/platform_payermax_setup.py`：Platform 侧 PayerMax mock 配置
- `verification/acceptance-cleanup-local.sql`
- `assets/stage5/*.png`（15 张）
- 本报告
