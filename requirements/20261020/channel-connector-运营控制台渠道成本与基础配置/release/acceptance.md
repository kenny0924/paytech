# mor-test 验收步骤（阶段 7 第 7 步）

入口：运营控制台 `http://localhost:28080`（经 Nginx → Gateway → ops-bff）；管理员账号与第二审批人账号照 `test-environment/private/access.md`。脚本：复用本需求 `verification/local_acceptance.py`、`local_chain.py`、`local_browser.py`，`E2E_TARGET=test`（连接目标按 `local-e2e/e2e_http.py` 的 test 分支）。结果写 `verification/stage7-acceptance.md`，截图放 `assets/stage7/`。

## A. 接口验收（31 条路由，预期 103 项全部通过）

1. 未授权账号：`/api/operations/channel-admin/v1/channels` 403；菜单目录无「渠道」。
2. 管理员：渠道列表空 → 新建渠道（编码取 mor-test Platform 已登记的接入方，如 `PAYERMAX`）→ 激活成功；另建 `NOEXIST_X` 激活被拒，detail 为「Platform 不存在同编码接入方」。
3. 账号：新建（OE `AE001`）→ 写入参数与凭据（mor-test Vault Protect；`t_channel_account.account_vault_token` 非空，详情只显示末 4 位）→ 激活；未配置账号激活被拒。
4. MID：新建 → 改绑同渠道同 OE 账号 → 激活；跨 OE 改绑被拒；`externalMidCode` 前缀筛选。
5. 能力组：新建（ACTION.PAY / AUTHORIZE / CAPTURE / REFUND、AMOUNT.MIN / MAX、SECURITY.3DS_MODE）→ 六维重复 409 → 类型不一致 400 → 整组替换 → 停用 / 启用。
6. 成本：建方案（渠道不存在被拒）→ 建版本 → 提交 → 同人批准被拒 → 第二审批人批准（未到期 → APPROVED）→ 作废（INACTIVE）→ 再建版本并批准到期即 ACTIVE → 版本对比 → 变更记录（含 CANCEL）。
7. 变更记录：渠道 / 账号 / MID / 成本版本四类查询。

## B. Payment 侧全链路（AC-B05 的 mor-test 部分）

用 A 中登记的渠道 / 账号 / MID / 能力：`E2E_TARGET=test` 跑 `local-e2e/acceptance_flow.py` 的最小支付路径（或 `local_chain.py`）：Payment 候选查询返回该 MID；渠道执行经 Platform 标准 API 到 mor-test 的 payermax 接入方（沙箱 / mock，以 mor-test 现有接入方配置为准）。若 mor-test 的 PAYERMAX 接入方未配置可执行的 PAY，记录为环境限制，不改接入方配置。

## C. 浏览器验收（AC-V01–V04，预期 35 项）

三页入口与面包屑、空状态、表单内联校验、凭据密码型与掩码、状态按钮仅显示允许迁移、CLOSED 后无操作、错误 detail 展示、版本对比高亮、变更记录区；每页截图并标注画板编号。

## D. 既有页面回归

商务（9 页）与财务（7 页）各打开一次，确认菜单、列表与详情正常（本次替换了 ops-bff / ops-web / Platform 镜像，必须证明未回退 b04b77f1 的页面）。

## E. 清理

按 ID 删除验收产生的渠道、账号、MID、能力、成本、变更记录（`verification/acceptance-cleanup-test.sql`，先 SELECT 核对再 DELETE）；合成 Platform 用户停用、合成角色清权限；Vault Token 不删，记录数量；Platform 审计不回滚。
