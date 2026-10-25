# 阶段 5 本机联调报告（后端，2026-10-25）

结论：44 项检查全部通过（明细与证据见 `stage5-result.json`、`evidence/*.json`）。本轮没有改代码。发现一个数据前提：PayFac Partner 合同的 `PLATFORM_SIGNATORY` Party 必须在 client-master 中存在，并且有已批准的资料。

## 1. 运行环境

本机 e2e 共享栈 `Code-MoR/local-environment/e2e`，库为 `mor_<svc>_v2e2e`，vhost 为 `/mor-e2e`，Redis 用 database 3，服务都注册在 Nacos `mor-local`。

| 服务 | 端口 / 管理端口 | 处理方式 |
|---|---|---|
| compliance | 18112 / 19112 | 新启动（worktree JAR），用 V2 启动器 `run-service.sh start compliance` |
| client-master | 18101 / 19101 | 新启动，同上 |
| commercial | 18108 / 19108 | 新启动，同上；另加 `COMMERCIAL_SNOWFLAKE_REGION_ID=0`、`COMMERCIAL_SNOWFLAKE_WORKER_ID=7`（启动器没有这两个值）；快照开关保持默认 false |
| ops-bff | 18201 / 19201 | 替换：旧 JAR 备份为 `*.pre-relsnap`，新 JAR 用临时文件 + rename 安装，按旧进程抓取的 argv 和环境重启（`swap.py`） |
| acquiring + risk-decision | 18102 / 18104 | 两个服务一起替换，停机约 26 秒（06:02:05—06:02:31Z），中间执行库变更。acquiring 先以 REAL 运行做验证，结束时已重启回 MOCK（默认值） |

三个服务库 `mor_client_master_v2e2e`、`mor_commercial_v2e2e`、`mor_compliance_v2e2e` 在本步之前已不存在。本次按 V2 `00-e2e-databases.sql` 的字符集重建，表结构用 worktree 的 schema。应用账号的库级授权原本就在。没有使用 `mor_*` 库，因为 auto 测试独占这些库。

## 2. 库变更与种子（`setup.sh`，可重复执行）

执行前备份到 scratchpad：`backup/relsnap-stage5/`，包括 acquiring 的 `t_order_context` 和 `t_commerce_order`，risk 的 `t_risk_variable_member` 和 `t_checkout_input_snapshot`，以及 Platform 块 137 涉及的 8 张表。

1. client-master：先执行 `10-client-master-seed.sql`。内容是 3 个 Party 加平台签约方 Party，两个 Client（MOR `237161687952666625`，PSP `900000000001380201`），分类 RETAIL / 5999，AE001（PAYFAC，此时来源仍为 PLATFORM），Scope US / USD，`en` 法律文件 3 份，以及卖方披露和根 BCR。然后执行正式数据文件 `2026-10-24-relationship-snapshot-data.sql`，结果符合预期：AE001 变为 PARTNER，指向 `LP-PAYFAC-AE001`（900000000001370001，Party 与 AE001 相同），并新增 OE_PROVIDER 和 party_role 行。
2. commercial：先执行 `20-commercial-seed.sql`，建 MOR 与 PSP 两份 Client 合同，含 ACQUIRING、CARD + CURRENCY=USD、2.5% 费率和画像 RELSNAP_CARD。然后带合成变量执行正式草稿 `2026-10-24-payfac-partner-agreement.sql`，建 PARTNER 合同、PARTNER_SHARE 与 PLATFORM_SERVICE_CHARGE 两个方案，费率为 0。
3. acquiring（`mor_acquiring_v2e2e`）：执行 `2026-10-24-order-relationship-snapshot.sql`。再执行 `remove-platform-entity-source.sql`，带 `@payfac_local_partner_id`，把 9 笔 PLATFORM 订单改写为 PARTNER / 900000000001370001。
4. risk-decision（`mor_risk_decision_v2e2e`）：执行 `remove-platform-entity-source.sql`，删除目录行 PLATFORM，把 9 个输入快照改写为 PARTNER。
5. Platform（`mor_platform_v2e2e`，本机 e2e Gateway 读的是这个库，块 136 也在这里）：执行 `registration-block137.sql`（150 行）和 `test-activate.sql`（影响行数 11 / 6 / 6 / 1）。Gateway 快照路由数从 464 变为 475。

## 3. 资格登记

- AE001 ENTITY_UNDERWRITING、PayFac Partner PARTNER_DUE_DILIGENCE、MOR Client ORGANIZATION_KYB：直连 compliance Admin API `/compliance-admin/v1`，带合成 Actor 头。
- PSP Client ORGANIZATION_KYB：走运营入口 B10（Gateway → ops-bff），会先经 B7 核对主体。

## 4. 检查结果

- **commercial 直连解析**（`X-Caller-Service: acquiring-service`）：
  - MOR（C1）成功。8 个角色，`fundFlow=THROUGH_CUSTODIAN`，`effectiveModelVersion=0`，`snapshotModelVersion=1`；卖方文本与披露一致；PLATFORM 角色的 Party 是平台签约方。
  - PSP（C2）成功。9 个角色，其中 SELLER、TAX_RESPONSIBLE、ACQUIRER_SUB_MERCHANT 为 Client。
  - 失败路径：未登记资格的 Client 返回 422 CLIENT_NOT_ELIGIBLE，OE 资格未登记时也是这个码；撤销 OE 资格返回 422 NO_ELIGIBLE_ENTITY（日志 `subject-not-eligible`），重新登记后成功；停 compliance 返回 503 OWNER_UNAVAILABLE，重启后成功。
  - 收单资质改为 MERCHANT_RESALE_APPROVED 后，PSP 返回 NO_ELIGIBLE_ENTITY，MOR 仍成功；改回 PAYFAC 后 PSP 恢复。
- **acquiring REAL 建单**：入口是直连 `/commerce/client-gateway-api/v1/checkouts`，手工带上 Gateway 会加的 `X-Gateway-Client-Id` 与 `X-Gateway-Credential-Id`。本机没有可用的 Client API 凭证明文，client-bff 也没在运行，所以没有走 Gateway。
  - MOR：201，READY。`t_commerce_order` 为 AE001 / PARTNER / 900000000001370001；`t_order_context` 为 THROUGH_CUSTODIAN / 0 / 1，带 Partner 两个版本；`t_order_relationship_binding` 8 行；`t_order_legal` 的 `public_*` 等于卖方文本，并带 Partner 合同。
  - PSP：201，绑定 9 行，SELLER 为 Client。
  - Client 资格撤销后建单返回 422 CHECKOUT_NOT_ELIGIBLE，重新登记后恢复。
  - 切回 MOCK 后建单 201：AE001 / PARTNER / 107，fund_flow 与 snapshot 版本为 NULL，生效版本为 0，没有绑定行。
- **运营 B1—B11**：全部经 Gateway 以 E2E 管理员会话调用。
  - 读接口：B1 / B2 / B4 / B5 / B7 / B8 / B9 都通过；B2 的资格聚合为 AVAILABLE。
  - 写接口：B3 两次修改成功，过期 changeVersion 返回 409；B6 增减角色成功，仍在提供时去掉 OE_PROVIDER 返回 422；B10 登记成功，过期版本返回 409；B11 撤销成功。

## 5. 发现与后续

- **平台签约方 Party 必须在 client-master 中有已批准资料。** commercial 校验签署时，会经 client-master party-profiles 查询全部签署方资料，包括 `PLATFORM_SIGNATORY`。第一次解析返回 409 CCT_CONTEXT_AMBIGUOUS，原因就是种子里没有这个 Party；补上后通过。阶段 6 在 mor-test 执行 `payfac-partner-agreement.sql` 之前，要确认占位 Party 880000000701 及其已批准资料在 mor-test client-master 中存在，否则 REAL 解析会对每个 AE001 候选报冲突。
- 切换 REAL：`python3 swap.py restart acquiring ACQUIRING_COMMERCIAL_RESOLUTION_MODE=REAL`；切回 MOCK：`python3 swap.py restart acquiring`。也可以在启动环境里设置 `ACQUIRING_COMMERCIAL_RESOLUTION_MODE=REAL`，修改后需要重启。
- 回退：把 `jars/*.pre-relsnap` 改名覆盖回去后重启。库变更的回退见各 SQL 文件头部；acquiring 与 risk 的改写须与 JAR 一起回退。
