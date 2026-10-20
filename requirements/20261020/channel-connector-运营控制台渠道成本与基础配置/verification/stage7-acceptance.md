# 阶段 7 mor-test 验收记录（release/acceptance.md 第 A–E 节）

- 执行：VER（Opus 5.5），2026-10-20 07:30–07:36Z。
- 环境：mor-test，运营入口 `http://localhost:28080`，`E2E_TARGET=test`（连接方式见 local-e2e `e2e_http.py` 的 test 分支）。凭据从 `test-environment/.env.test` 与 `private/access.md` 读入，未打印。
- 约束：未替换或重启任何容器；未改 Platform 接入方配置；未动块 92 状态（31 条路由仍为 ACTIVE）；未执行 git 写命令；未调用 Superpowers skill。
- 唯一新起的容器：私网探针 `PrivateHttpProbe`，按 e2e_http 既有做法一次性运行（`docker run --rm`，镜像 `mor-test/platform-support-service:channel-20261020`），用于调用 channel-connector 私网候选查询。

| 节 | 结果 |
|---|---|
| A 接口验收（31 条路由） | **105 / 105**（本机 103 项 + 能力组「重新启用 / 再停用」2 项） |
| B Payment 侧全链路 | 候选段 **2 / 2**；执行段为环境限制，未运行 |
| C 浏览器验收 | **35 / 35**，截图 `assets/stage7/`（15 张，对应画板 01–15） |
| D 既有页面回归 | **17 / 17**：商务 8 个菜单页 + 财务 7 个菜单页，另 2 项为目录检查。截图 `assets/stage7/regression/` |
| E 清理 | 完成（第 5 节） |
| 缺陷 | **1**（D1，第 6 节）：**已修复并重验通过** |

运行记录：`local-environment/e2e/runtime/channel-acceptance-test-20261020113057.json`（同一文件追加了 chain / browser / cleanup 段）。

## A. 接口验收

脚本：`verification/local_acceptance.py`，增加了 test 分支：日志读 `docker logs --since`；账号参数与凭据为每次随机生成的合成值；渠道编码 `PAYERMAX`。

| 规范项 | 结果 |
|---|---|
| 1. 未授权 | IAM 合成的只读审计人员导航不含 `OPERATIONS_CHANNEL*`；GET `/channels`、`/mids`、`/channel-cost-plans`、`/mid-capabilities/definitions` 与 POST `/channels` 全部 403 |
| 2. 渠道 | 列表、新建 PAYERMAX、激活成功；无接入方渠道 `NOINTEG_0D659F` 激活被拒，detail「Platform 不存在同编码接入方: NOINTEG_0D659F」；非法流转、关闭、关闭后操作均被拒 |
| 3. 账号 | AE001 新建；未配置时激活被拒；写入参数与凭据走 mor-test Vault 真实 Protect，`account_vault_token` 非空且 Vault 记录类型为 `CHANNEL_ACCOUNT_CONFIG_V1 / REFERENCE`；详情只有末 4 位；响应与 ops-bff / channel-connector 容器日志都不含凭据或参数值；之后激活 |
| 4. MID | 新建；同渠道同 OE 改绑并改回；跨 OE、跨渠道改绑被拒；激活；`externalMidCode` 前缀命中与不命中 |
| 5. 能力组 | 新建 7 条明细（ACTION.PAY / CAPTURE / AUTHORIZE / REFUND、AMOUNT.MIN / MAX、SECURITY.3DS_MODE）；六维重复 409；类型不一致 400；未注册代码 400；整组替换；停用后候选查询不再返回，重新启用后恢复，再停用 |
| 6. 成本 | 渠道不存在时建方案被拒 → 建方案 → v1 提交、同人批准被拒、摘要不一致被拒 → 第二审批人（IAM 合成）批准 → ACTIVE；v2 未到期批准 → APPROVED → 作废为 INACTIVE；v3 驳回 → REJECTED；双向版本对比；ACTIVE 作废被拒 |
| 7. 变更记录 | 渠道（CREATE / STATUS / UPDATE）、账号（CONFIGURATION / STATUS）、MID（REBIND ×2 / STATUS）、成本版本（CANCEL，含原因与操作者）四类都已查询 |

说明：规范第 6 项写的顺序是「先未到期批准作废，再到期批准为 ACTIVE」，脚本的顺序是「v1 到期即 ACTIVE，v2 未到期批准后作废」，覆盖的状态与断言相同。第二审批人用 IAM 合成人员，因为 `access.md` 里只有一个管理员账号。

## B. Payment 侧全链路（AC-B05 的 mor-test 部分）

脚本：`verification/local_chain.py`（test 分支）。

| 检查 | 结果 |
|---|---|
| 候选查询（Payment 选路调用的同一 channel-connector 合同 `POST /channel-connector/v1/mid-candidates`，AE001 / CARD / US / USD / 5999，经私网探针） | 返回控制台登记的 MID 238202335787065344、账号 238202328124071936，`[AUTHORIZE, CAPTURE, PAY, REFUND]` |
| 种子渠道 9500000000000001 不存在 | 0 行 |

- **Payment 进程：** mor-test 的 Payment 没有对外暴露候选查询接口，既有 E2E 脚本也没有直接调用 Payment 候选查询的入口。这里用的是 Payment 实际调用的 channel-connector 合同，与本机做法相同。
- **环境限制（执行段未运行）：** mor-test 接入方 PAYERMAX 的出站地址是外部 `https://pay-gate-uat.payermax.com/aggregate-pay/api/gateway`（PayerMax UAT），而账号凭据是验收用的合成值。渠道执行会向外部第三方发出请求，且签名必然无效。按主会话「不改接入方配置」，执行段没有运行。如果要在 mor-test 证明执行链路，需要另行决定：使用 mor-test 内部 mock 接入方，或者使用真实 UAT 凭据。

## C. 浏览器验收

脚本：`verification/local_browser.py ../assets/stage7`，35 / 35 通过。

- **截图：** 15 张，01-渠道列表 … 15-作废版本确认，文件名即画板编号。
- **覆盖：** 菜单与标题、空状态、四处内联校验（渠道编码、MCC、作废原因、未选能力代码时禁用保存）、凭据密码型与末 4 位、DRAFT / ACTIVE / CLOSED 的状态按钮、激活被拒 detail、自批被拒 detail、版本对比高亮、变更记录、编制页保存并提交、合成复核人批准后作废。

## D. 既有页面回归

脚本：`verification/stage7_regression.py`，按 Platform 导航返回的目录逐页打开。检查标题可见、无错误态、无失败通知、无 5xx；列表页有「详情」按钮时打开第一行。结果 17 / 17。

- **商务 8 个菜单：** 工作台、合同、价目、费率预览、价格试算、定价画像、产品目录、变更历史，均正常。规范写「9 页」，第 9 页是版本对比，它是详情路由而非菜单，C 节以外没有单独打开。
- **财务 7 个菜单：** 账务基础资料、账本查询、余额查询、明细账查询、调整、期间关账、记账规则，均正常。
- **首次运行的一次失败：** 「调整」页因脚本连续整页加载，导航接口被 Gateway 限流（`GATEWAY_RATE_LIMITED`）。这是验收节奏问题，不是页面回退；加了 3 秒间隔后重跑全部通过，未再触发限流。
- 本次替换 ops-bff / ops-web / Platform 镜像后，未发现商务、财务页面回退。

## E. 清理

- **命令：** `E2E_TARGET=test python3 -I local_acceptance.py --cleanup`，07:35:31Z 执行。
- **channel-connector：** 以 channel_connector_app 执行 `verification/acceptance-cleanup-test.sql`，先核对再删除。

| 核对 / 删除 | 行数 |
|---|---|
| 核对：渠道 / 账号 / MID / 能力组 / 方案 / 版本 / 他渠道方案 | 4 / 4 / 1 / 2 / 1 / 4 / 0 |
| 删除：变更记录 | 27 |
| 删除：成本组成 / 范围 / 版本 / 方案 | 6 / 5 / 4 / 1 |
| 删除：能力明细 / 能力组 / MID / 账号 / 渠道 | 9 / 2 / 1 / 4 / 4 |
| 复核：剩余渠道 / 方案 / 变更 | 0 / 0 / 0 |
| 复核：`mor_channel_connector` 渠道相关 8 张表 | 全部 0 行，与验收前一致 |

- **Platform（经 IAM 接口）：**
  - 停用 3 个合成人员：238202317793476608、238202320268115968、238202662103891968，均返回 204，库中为 DISABLED。
  - 清空 3 个合成角色的权限：238202317067862016、238202319701884928、238202661533466624，均返回 200，有效授权为 0。
- **Vault：** Token 不删。本次产生 1 个（同内容重放复用），`CHANNEL_ACCOUNT_CONFIG_V1` 记录共 1 条。
- **Platform 审计：** 不回滚。
- **残留（按规范保留）：** 3 个 DISABLED 合成人员、3 个无权限合成角色、1 个 Vault 记录、审计事件，以及管理员本人的登录会话。块 92 与 Vault PROTECT 规则保持启用。

## 6. 缺陷

**D1（Web，严重度低，展示错误）—— 状态：已修复并重验通过（2026-10-20 07:38Z）。** 原问题： 渠道详情「技术账号」表中，未配置账号的「配置」列显示红色 `REJECTED` 标签，而不是「未配置」。

- **位置：** `operations-console-web/src/pages/ChannelChannels.tsx:235`。未配置分支写成了 `<StatusTag status="REJECTED" />`：StatusTag 直接显示状态码，`REJECTED` 是成本版本的「驳回」状态，与账号配置无关。同文件已有 `tokenSuffixLabel` 会返回「未配置」，但未被使用。
- **复现：** 在任一渠道下新建账号且不写入配置 → 打开渠道详情的「技术账号」标签页 → 该行「配置」列为 `REJECTED`。证据：`assets/stage7/02-渠道详情-账号.png` 中 otherOe / sameOe 两行。
- **影响：** 只影响展示，运营会误读为「被驳回」。不影响接口与数据，AC-V03 的凭据掩码不受影响。
- **为何本机未发现：** 本机浏览器脚本只断言已配置账号的行。
- 未修改产品代码，交主会话决定。
- **修复与重验：**
  - **发布：** 主会话修复后重新发布 ops-web，镜像 `sha256:3ae27f3d3f75f0ba6ececa69a876f217d66121c594aa33d12791c9f329d7be09`，容器 07:37:18Z 启动，healthy。入口 `/assets/index-H8mAJBt-.js`，已用 `curl` 核对。
  - **重验：** 脚本 `verification/stage7_d1_reverify.py`（`E2E_TARGET=test`），07:38:01Z 运行，**7 / 7 通过**。
    - 新建 DRAFT 渠道 `D1CHK_070467` 与未配置账号（AE001，`configured=false`）。
    - 页面加载新入口包 `index-H8mAJBt-.js`。
    - 「配置」列文本为「未配置」，且为 `ant-tag-red` 红色标签；账号行不再出现 `REJECTED`。截图 `assets/stage7/D1-未配置账号-配置列.png`。
    - Web 替换后商务「合同」（`/commercial/agreements`）与财务「账务基础资料」（`/finance/setup`）打开正常。
  - **清理：** `E2E_TARGET=test python3 -I local_acceptance.py --cleanup`，按运行记录 `channel-acceptance-test-20261020113801.json` 的 ID，以 channel_connector_app 执行 `acceptance-cleanup-test.sql`。
    - 核对：渠道 1、账号 1，其余 0。
    - 删除：变更记录 2、账号 1、渠道 1。
    - 复核：剩余 0 / 0 / 0。
    - 本次没有创建 Platform 合成人员，也没有产生 Vault Token。

## 7. 其他说明

- 读取 `test-environment/private/access.md` 时，我在命令里写的脱敏正则没有生效，管理员口令出现在 VER 的工具输出中一次。已核对，该值没有写入任何报告、脚本、截图或运行记录。
- 新增脚本：`verification/stage7_regression.py`、`verification/acceptance-cleanup-test.sql`。`local_acceptance.py`、`local_chain.py`、`local_browser.py` 增加了 test 分支：运行记录前缀 `channel-acceptance-test-`、容器日志读取、合成凭据、test 清理流程、能力组启停。
