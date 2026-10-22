# 阶段 5 报告：第二段本机端到端

> 任务卡：`tasks/P2-stage5.md`。依据：P2 报告第 4、5、8 节，P2-web 报告，P2.md 第 6 节，第一段 `stage5-report.md`。执行日期 2026-10-22（UTC）。
>
> 执行顺序：
> 1. A 基线：第一段 JAR，04:18–04:20Z。
> 2. 打包：04:20Z。
> 3. B1–B2 备份与 channel-connector 迁移：04:21–04:22Z。
> 4. C 换 JAR 并重启：04:22–04:23Z。
> 5. B3 Platform 注册：04:23–04:24Z。
> 6. D 回归：04:24Z。
> 7. E 新能力：04:28–04:32Z。
> 8. F 截图：04:33–04:37Z。
> 9. G 修复后复验（主会话追加）：04:41–04:44Z，结束于 04:43:57Z。
>
> 执行约束：
> - 未 install、未提交、未推送，未修改任何代码。
> - 主工作区只新增本目录下的文件，worktree 没有改动。
> - 只执行了 B 节的 SQL，只作用于 `mor_channel_connector_v2e2e` 与 `mor_platform_v2e2e`。
> - 只停启了 card、channel-connector、platform、ops-bff 四个进程，另有 E7 的一次临时重启，以及我自己启动并已关闭的回显服务。

## 1. 结论

- **回归一致。** 与第一段基线逐项对照：
  - 链路 5 项一致：候选查询、REFUND、合成票据 PAY、两个 NOT_SENT。
  - 真实读卡 PAY 一致，沙箱用例一致。
  - 外部 API 管理读取一致：除去本次新建对象后，44 个 GET 的调用集合相同。
  - 只有两类差异，都是合表设计的预期结果，见 3.3：接入方名称改为渠道名称；`CHANNEL_ACCOUNT_CONFIG.entityNo` 改为非必填。
  - 渠道管理读取只多出第二段新增的字段。
  - 接入方 5 个 CRUD 全部 404。
- **读卡收紧生效。**
  - 用真实有效票据、以 `platform-support-service` 直接读卡：返回 404，票据保持未使用。
  - 用同一票据经 channel-connector 执行 PAY：SUCCESS。
- **迁移与注册结果符合预期。**
  - precheck 第 1–5 段全部为 0 行；verify 全部符合。
  - 10 类定义、脚本、流程与运行快照的内容 SHA-256 在迁移前后完全一致。
  - 块 129 第 2、3 段的影响行数符合预期。
  - Gateway 自动加载了新 Snapshot：OPERATIONS 路由从 317 变为 316。
- **第二段新能力全部通过（E2–E8，43 项）**：服务渠道全流程、调用授权、入口隔离、出站地址守卫、暂停、执行分舱、导航。
- **发现 2 个代码缺陷**（第 6 节），已通知主会话。我没有改代码，修复由主会话完成，**两个缺陷均已修复，第 11 节「缺陷修复复验」通过**：
  - **D-1（低）**：出站地址变更记录的 before / after 摘要用了不同的 JSON 序列化，两者无法比较。**已修复。** 复验：连续 3 次修改，每条记录的 before 都等于前一条的 after；after 与本地按写入格式复算的结果一致；内容相同的两次 after 摘要也相同。
  - **D-2（中）**：渠道列表页同时发出 4 个列表请求，超过 Gateway Route 92401 每账号并发 2 的上限，必定出现 429。表现为页面加载失败，或分段计数为空。**已修复**（前端改为依次计数，块 92 的 Guard 未调整）。复验：三个分段页计数齐全，列表路由同一时刻最多 2 个请求，0 个 429。
- 新版本保持运行：card、platform、ops-bff 为第二段 JAR；channel-connector 为 G 步重新打包、含 D-1 修复的 JAR。四个服务 Health 均为 UP，channel-connector 为默认并发。回显服务已关闭。

## 2. 环境与执行内容

| 步骤 | 内容 | 产物 |
|---|---|---|
| 开工核对 | 四个服务运行第一段 JAR（10-21 07:23）；gateway、payermax-mock、nginx、billing、ledger、account、ops-web 在运行且未动 | — |
| A1 | `PAYERMAX_MOCK` 渠道、账号、MID、能力组（5 项明细）、`t_integration` 1 行、原子 API 4、脚本 2、实现 4、标准 API 4 都在，**没有补登** | — |
| A2 | `stage5p2_e2e.py run baseline3`（第一段版本），再执行 `cardpay` 补跑真实读卡（原因见第 7 节第 1 项） | `stage5p2-baseline.json` |
| C1 | 在 worktree `Code-MoR-ccu2/paytech` 执行 `./mvnw -o -q -pl card-app, channel-connector-app, platform-support-app, operations-console-bff-app -am package -DskipTests`，成功（04:20:01–04:20:31Z）。channel-connector JAR 中有 `ChannelCallGrant*`、`ChannelAddressGuard`、`ChannelExecutionCompartments` | — |
| B1 | 先停 channel-connector 冻结写入，再整库 mysqldump 两个库 | 第 5 节 |
| B2 | precheck → migrate → verify → 删除两张辅助表 | `stage5p2-sql/10–40`、`hash-before/after.txt` |
| C2 | 当前 JAR 改名为 `*.pre-ccu2`，复制新 JAR（SHA-256 与 worktree 产物一致）；依次重启 card → channel-connector → platform → ops-bff | — |
| B3 | Platform 注册第 1 段（只读）→ 第 2 段 → 第 3 段；等待 Gateway 刷新 Snapshot | `stage5p2-sql/50–53` |
| D | `stage5p2_e2e.py run after`，再用 `stage5p2_compare.py` 对照 | `stage5p2-after.json`、`stage5p2-compare.json` |
| E | `stage5p2_service.py setup-and-run` 完成 E2–E6、E8 与 CRUD 探针；E7 临时重启后执行 `compartment` | `stage5p2-service.json` |
| F | `stage5p2_screens.mjs`，playwright-core 1.58.1，无头 Chromium | `stage5p2-screens/` |

启动器 `local-e2e/run-service.sh` 未复制、未修改：

- 它已带 `CHANNELCONNECTOR_PROTOCOL_EXECUTION_ALLOWEDSCHEMES=https,http` 与 `…_TRUSTEDNETWORKS=127.0.0.1/32`。
- E7 只在调用前导出 `CHANNELCONNECTOR_PROTOCOL_EXECUTION_SERVICEMAXCONCURRENCY=1`，由子进程继承。
- 恢复默认后已确认进程环境中没有该键。
- 两个舱的并发上限在其余时间保持默认（payment 64 / service 16）。

**顺序偏差：** B3 放在 C 之后执行，原因是注册 SQL 的前置条件写明"channel-connector、ops-bff 已是第二段版本"。本机在这段时间没有外部流量，结果与卡片顺序等价。

**Snapshot 发布方式：** 本机 Gateway 每 5 秒从 Platform 拉取一次 Snapshot，这就是本机的既有方式，没有手动发布动作。加载确认依据两项：
- 04:24:04Z 的日志从 `routeCount=373` 变为 `372`；
- `gateway_snapshot_routes{channel="OPERATIONS"}` 从 317 变为 316（启用 4 条、停用 5 条），`gateway_snapshot_valid=1`。

## 3. A / D 对照（`stage5p2-compare.json`）

### 3.1 链路

| 项 | 基线（第一段） | 新版本（第二段） | 结论 |
|---|---|---|---|
| 候选查询 `/mid-candidates` | 200，1 个候选，`[PAY, REFUND]`，0.01–100000 | 相同 | 一致 |
| REFUND（ORIGINAL_TRANSACTION） | 200 SUCCESS / `REFUND_SUCCESS`；合成 PayerMax 流水 1 条 refund `APPLY_SUCCESS`，验签、外层、时间、金额均有效；Vault REVEAL 1 次，调用方 channel-connector-service | 相同 | 一致 |
| PAY（合成票据） | 200 NOT_SENT / `INTEGRATION_INITIALIZATION_FAILED`；流水 0；账号 Reveal 1 次 | 相同 | 一致 |
| NOT_SENT：标准 API 不存在（QUERY） | 200 NOT_SENT / `STANDARD_API_NOT_FOUND` | 相同 | 一致 |
| NOT_SENT：输入缺 `refund.refundId` | 200 NOT_SENT / `VALIDATION_ERROR` | 相同 | 一致 |
| 真实读卡 PAY（card 建工具 + 签发票据） | 200 SUCCESS / `SUCCEEDED`，流水 +1 orderAndPay `APPLY_SUCCESS`；票据已用；同一票据再读返回 400 `BUSINESS_CHECK_FAILED`；card 读卡请求的 requestId 与 channel-connector 本次 PAY 相同（共享 1 个）；card 与 channel-connector 日志中无卡号 | 相同 | 一致 |
| card 调用方探针（合成票据，三种调用方） | 都是 404 | 都是 404 | 一致。card 已有表，未知票据也返回 404，所以这一项不能区分调用方；改用下一行的真实票据探针 |
| **以 platform-support-service 用真实有效票据直接读卡** | 未做（第一段仍接受该调用方，会真实读出卡数据） | **404 `NOT_FOUND`，未返回卡字段，票据 `used_at` 仍为空**；之后同一票据经 channel-connector PAY 成功 | 符合 D4：第二段只允许 channel-connector |

读卡调用方：card 没有专门的读卡审计记录，这与第一段报告第 8 节的结论相同。依据有两条：

- card 访问日志中的读卡请求与 channel-connector 本次 PAY 共享同一个 requestId；
- 以 Platform 身份读卡已被拒绝。

据此确认读卡调用方为 channel-connector-service。

### 3.2 沙箱

| 项 | 基线（`S5_P2BASELINE3_*`） | 新版本（`S5_P2AFTER_*`） | 结论 |
|---|---|---|---|
| 新建验证账号 / 用例 | 200，version 1 / 200，version 1 | 相同 | 一致 |
| 运行 | RUNNING → PASSED；步骤 1 PASSED、SUCCESS，5 条断言全过；证据卡号 `444433******1111`；流水 +1 | 相同 | 一致 |
| 运行详情、新账号与用例详情 | — | 与基线逐字段相同。比较时忽略编码、运行号、名称、渠道订单号与签名 | 一致 |

### 3.3 外部 API 管理读取（经 Nginx 18444 → Gateway → ops-bff）

| 项 | 结果 |
|---|---|
| 调用集合 | 基线 46 个，新版本 47 个。基线去掉接入方列表与详情后为 44 个；新版本去掉本次新建的 3 个对象后也是 44 个，两边相同。全部 200 |
| 接入方列表与详情 | 基线 200；新版本经 Gateway 返回 404 `GATEWAY_ROUTE_NOT_FOUND`，属于允许差异 |
| 字段差异（9 处，2 类，均已解释） | ① `standard-apis` 列表与 4 个详情中 `implementedBy` / `implementations` 的 `integrationName`：由接入方名称 `PayerMax（PAYERMAX_MOCK）` 变为渠道名称 `本机合成 PayerMax（阶段 5 10210318）`，共 8 处。原因是合表时 MATCHED 行保留 `t_payment_channel` 的名称（technique 5.2），接入方名称不再保存。字段名保持不变（决定 5），值来自渠道，归入"接入方相关改为渠道"。② `editor-capabilities` 中 `CHANNEL_ACCOUNT_CONFIG` 的参数 `entityNo` 由 `required:true` 改为 `false`，共 1 处。这是 P2 §2 的 Vault 负载规则，服务渠道账号不带 OE |
| 其余差异 | 无 |

### 3.4 渠道管理读取（新增覆盖）

共 11 个 GET：渠道列表与详情、账号列表与详情、MID 列表与详情、能力定义，以及 4 类变更记录。差异如下：

- 渠道列表多出 `channelType`、`endpointCount`、`activeGrantCount`；
- 渠道详情多出 `channelType`、`description`、`endpoints`、`version`。

其余 9 个 GET 没有差异，**没有未解释的差异**。

### 3.5 已删除的接入方 CRUD（D2）

| 探针 | 列表 | 详情 | 新建 | 修改 | 删除 |
|---|---|---|---|---|---|
| 经 Gateway | 404 | 404 | 404 | 404 | 404 |
| 直连 ops-bff 18201 | 404 | 404 | 404 | — | 404 |
| 直连 channel-connector 18110 | 404 | 404 | 404 | — | 404 |

经 Gateway 时，5 个接口的错误码都是 `GATEWAY_ROUTE_NOT_FOUND`。

## 4. E：第二段新能力（`stage5p2-service.json`，43 项全部 ok）

回显服务由 `stage5p2_echo_server.py` 提供：只用标准库，监听 127.0.0.1 的随机端口，凭据只记录是否存在与末 4 位，已关闭。通用入口直接调用 `127.0.0.1:18110`，调用方经 `X-Caller-Service` 传入。

### E1–E2 服务渠道全流程（控制台 API）

| 项 | 结果 |
|---|---|
| 类型筛选 | 全部 2 = PAYMENT 1（`PAYERMAX_MOCK`，`activeGrantCount=null`）+ SERVICE 1（`S5P2_ECHO`）；每个分段只含对应类型 |
| 新建 `S5P2_ECHO` | 201，SERVICE / DRAFT / version 1 / `endpoints=[]` |
| 未配置出站地址时激活 | 400 `BUSINESS_CHECK_FAILED`「渠道未配置出站地址: S5P2_ECHO」 |
| 写入出站地址 | 200，version +1，`[{ECHO, http://127.0.0.1:<port>, sandbox false}]` |
| 激活 | 200 ACTIVE。此时没有凭据、实现与授权，说明激活只要求已配置出站地址 |
| 凭据 | 账号创建不带 OE，库中 `entity_no` 为 NULL。写入配置时有 1 条 Vault 事件：channel-connector-service `PROTECT CHANNEL_ACCOUNT_CONFIG_V1 / CHANNEL_CONFIGURATION SUCCESS`。账号列表只返回 `vaultTokenSuffix`（等于库中 Token 的末 4 位），不含完整 Token，也不含凭据原值 |
| 标准 API `S5P2_ECHO_PING`、原子 API `ECHO_CALL`（`credentialFields: BODY:apiKey`）、实现（`CHANNEL_ACCOUNT_CONFIG` 初始化项读取注入的 `input.channelAccount`） | 均为 200 |

第一次执行在"激活凭据账号"一步中止：脚本先建账号、后激活渠道，而账号激活要求渠道已是 ACTIVE，返回 400「渠道须为 ACTIVE 且账号已写入配置」。这属于业务规则，不是缺陷。调整顺序后重跑并沿用已建渠道，第一次的 201 / 400 结果原样记录在输出中。

### E3 调用授权

| 项 | 状态 / 错误码 | 证据 |
|---|---|---|
| 未授权的调用方 `counterparty-service` | **403 `CHANNEL_CALL_NOT_GRANTED`** | 回显请求 0，访问日志 0 行 |
| 缺少调用方头 | 400 `INVALID_REQUEST`「缺少有效的调用方身份 X-Caller-Service」 | 回显请求 0 |
| 创建授权（counterparty-service × S5P2_ECHO × S5P2_ECHO_PING） | 201，ACTIVE，v1，返回 `standardApiName` | — |
| 授权后调用 | **200 SUCCESS**，`outputs.echo` 等于发送的 ping | 回显收到 `POST /echo`，请求体键为 `apiKey`、`ping`。**`apiKey` 已注入，末 4 位与本次合成凭据一致**。Vault 事件：channel-connector-service `REVEAL / CHANNEL_EXECUTION SUCCESS`。访问日志：`channel.outbound.access : callerService=counterparty-service channelCode=S5P2_ECHO channelType=SERVICE standardApiCode=S5P2_ECHO_PING apiCode=ECHO_CALL executeStatus=SUCCESS httpStatus=200 elapsedMs=18 requestId=…` |
| 其他调用方 `risk-decision-service` | 403 `CHANNEL_CALL_NOT_GRANTED` | 回显请求 0 |
| 重复创建 | **409** `CONCURRENT_REQUEST`「调用授权已存在: 授权 ID …, 状态 ACTIVE；停用的授权请改为再启用」 | — |
| 停用（expectedVersion 1） | 200，DISABLED，v2 | 停用后调用返回 403，回显请求 0 |
| 携带过期版本再次停用 | 409 | — |
| 启用（expectedVersion 2） | 200，ACTIVE，v3 | 启用后调用 200 SUCCESS |
| 变更记录 `CHANNEL_CALL_GRANT` | 3 条：CREATE、STATUS（ACTIVE→DISABLED）、STATUS（DISABLED→ACTIVE），操作者为会话管理员 | 库中 CREATE 的 `change_detail` 记录 `objectCode=S5P2_ECHO/S5P2_ECHO_PING/counterparty-service` |
| 调用方名不合法：`Counterparty_Service`、`x`、`counterparty.service` | 均为 **422** `VALIDATION_ERROR`，`errors[].field=callerService` | — |
| 授权列表 / `activeGrantCount` | 1 条，v3；渠道列表 `activeGrantCount=1` | — |

### E4 入口隔离

| 项 | 结果 |
|---|---|
| `PAYERMAX_MOCK` 经通用入口 | **400** `BUSINESS_CHECK_FAILED`「支付渠道只能经支付入口执行: PAYERMAX_MOCK」；回显与合成 PayerMax 请求均为 0 |
| `S5P2_ECHO` 经支付入口 `/channel-connector/v1/operations`（REFUND） | **200 NOT_SENT / `INTEGRATION_CONFIG_INVALID`**，与 P2 报告第 4 节口径一致；回显与合成 PayerMax 请求均为 0 |

### E5 出站地址

| 项 | 结果 |
|---|---|
| 新增 `https://10.1.2.3` | **422**，`errors[0].field=endpoints[1].baseUrl` |
| 改为 `https://localhost:8443` | **422**，`endpoints[0].baseUrl` |
| 改为 `https://169.254.169.254`（链路本地，追加用例） | 422，`endpoints[0].baseUrl` |
| 删除仍被引用的 `ECHO` | **409**「出站地址仍被原子 API 引用，不能删除: ECHO_CALL（ECHO）」 |
| 携带过期 version | **409** |
| 以上拒绝之后 | 版本与地址都不变 |
| 变更记录 | 地址修改记为 `CHANNEL` + `CONFIGURATION`，`change_detail` 的字段为 `ENDPOINTS_DIGEST`（缺陷 D-1 即出在这里） |

### E6 暂停

| 项 | 结果 |
|---|---|
| 暂停 | 200 SUSPENDED |
| 暂停后调用 | **400** `BUSINESS_CHECK_FAILED`「渠道未激活: S5P2_ECHO, 状态: SUSPENDED」，回显请求 0 |
| 恢复 | 200 ACTIVE；恢复后调用 200 SUCCESS |

### E7 分舱（已做）

以 `service-max-concurrency=1` 临时重启 channel-connector，回显延迟设为 3 秒，然后发起：

- 通用入口调用 #1；
- 0.3 秒后发起通用入口调用 #2；
- 同一时段经支付入口发起一次 REFUND。

| 调用 | 结果 |
|---|---|
| #1 | 200 SUCCESS，耗时 3048 ms |
| #2 | **503 `CHANNEL_EXECUTION_BUSY`**「渠道执行繁忙」，59 ms 返回，不排队 |
| REFUND | **200 SUCCESS**，流水 +1。访问日志中 REFUND 的 `elapsedMs=82`，先于 #1 完成，说明不受服务舱占满的影响。脚本记录的耗时 1928 ms 中含固定等待 1.5 s |

回显服务只收到 1 个请求。验证后已恢复默认并重启：进程环境中没有该键，Health 为 UP。

### E8 导航（新会话）

| 项 | 结果 |
|---|---|
| 接入方页面与按钮 | 导航中不含 `OPERATIONS_EXTERNAL_INTEGRATIONS` 和 `OPERATIONS_EXTERNAL_INTEGRATION_MANAGE` |
| 页面 92302 | 显示名为「渠道」，路由 `/channel/channels` |
| 调用授权按钮 | `OPERATIONS_CHANNEL_CALL_GRANT_MANAGE`（129301）为 BUTTON，父资源 92302，显示名「调用授权」 |
| 标准 API 页 | 仍在：`/external-api/standard-apis`，显示名「标准 API」 |

## 5. 执行过的 SQL 与备份

全部在本机 `mor-local-mysql-1`（13306）上以 root 执行，口令经 `MYSQL_PWD` 传入。SQL 文件中的 `USE mor_channel_connector;` 先用 sed 改为 `USE mor_channel_connector_v2e2e;`，其余内容逐字不变（diff 确认只差这一行）。

| UTC | 库 | 文件 / 语句 | 影响 |
|---|---|---|---|
| 04:21:07 | 两库 | `mysqldump --single-transaction --routines --triggers --set-gtid-purged=OFF --no-tablespaces`（只读） | channel-connector 28 表，platform 57 表 |
| 04:21:26 | `mor_channel_connector_v2e2e` | `phase2-10-precheck.sql`（只读） | 第 1–5 段全部为空。第 6 段 1 行：MATCHED，integration 238184963231453184 → channel 238501215359533056 `PAYERMAX_MOCK` ACTIVE，endpoint 1 个。第 7 段：payment_channel 1、integration 1、api 4、script 2、std 4、verify account / case / run / step 各 6、config_change 16；没有 `INTEGRATION*` 变更记录 |
| 04:21:41–43 | 同上 | `phase2-20-migrate.sql` | 守卫插入 0 行（通过）；对应关系 MATCHED 1；行数辅助表 14；RENAME 8 表；14 条 ALTER；CREATE `t_channel_call_grant`。事务内：t_channel 1、新建渠道 0、api 4、script 2、std 4、verify account / case / run 各 6、变更类型改写 0 / 0 / 0 / 0。去掉默认值，DROP `t_integration`。无错误 |
| 04:21:57 | 同上 | `phase2-30-verify.sql`（只读） | `table_count=28`、`legacy_count=0`、`phase2_table_count=9`；15 行行数全部 matched，grant 为 0；引用完整性为空；对应关系 `problem=NULL`，`t_channel` 中没有对应关系外的行；变更类型中没有 `INTEGRATION*`；`t_channel` 12 列顺序正确，`channel_code` 为 ascii_bin，`channel_type`、`version` 无默认值，`entity_no` 可空；新索引齐全，旧索引已删除。`integration_*` 列查询只命中辅助表 `tmp_phase2_channel_map` |
| 04:22:14 | 同上 | verify 第 7 段清理：`DROP TABLE tmp_phase2_channel_map; DROP TABLE tmp_phase2_row_count;`（P2 报告第 8 节"全部符合后手工删除"） | 0 / 0。之后 28 表，`integration_*` 列为 0 |
| 04:23:53 | `mor_platform_v2e2e` | `phase2-registration.sql` 第 1 段（只读） | 块 129 各项均为 0；块 92 两个权限 ACTIVE、菜单「渠道与账号」ACTIVE、列表 Route ACTIVE、管理员授权 2；71006–71010 共 5 条且全部 ACTIVE；71303 / 71305 共 2 条且 ACTIVE；target 1；PLATFORM_ADMIN version 30。**块 92 已激活，因此执行激活段** |
| 04:24:00 | 同上 | 第 2 段（块 129） | permission 1、role_permission 1、console_resource 1；92302 改名 1；71303 / 71305 停用 2；71006–71010 停用 5；route 4、header 20、query 7、guard 24 |
| 04:24:00 | 同上 | 第 3 段（激活） | routes 4 / resources 1 / grants 1 / role 1，与预期 4/1/1/1 一致。**块 71 的激活语句未重放** |

迁移前后内容核对（`stage5p2-sql/hash-before.txt` 与 `hash-after.txt` 的 diff 为空）：

- 原子 API 4 个、脚本 2 个、实现 4 个的 `definition_json`、`source_code`、`flow_definition_json` 以及版本；
- 6 个运行的 `case_snapshot_json` 与 `config_versions_json`；
- 接入方的版本、`endpoints`、描述与操作人，迁移后进入 `t_channel`；
- 渠道的名称、状态与时间。

`PAYERMAX_MOCK` 迁移后为 `channel_type=PAYMENT`、`version=2`，`endpoints` 取自原接入方：`[{PAYERMAX_GATEWAY, http://127.0.0.1:18190/aggregate-pay/api/gateway, sandbox true}]`。

本机 `t_channel_config_change` 迁移前没有任何 `INTEGRATION_*` / `INTEGRATION` 记录，所以 4 条改写语句都影响 0 行，`CHANNEL_*` 改名路径在本机没有样本。新类型的写入已由 E3 的 `CHANNEL_CALL_GRANT` 与 E5 的 `CHANNEL` + `CONFIGURATION` 验证。

**备份位置：** `/Users/kenny/Desktop/Workspace/Code-MoR/local-environment/e2e/backup/20261022T042107Z-ccu2-stage5/`，包含：

- `mor_channel_connector_v2e2e.sql`（28 表）；
- `mor_platform_v2e2e.sql`（57 表）。

两份都以 `Dump completed` 结尾。

**旧 JAR：** `local-environment/e2e/jars/paytech-{card,channel-connector,platform-support,operations-console-bff}-app-exec.jar.pre-ccu2`，即第一段 10-21 07:23 版本。`*.pre-ccu` 未覆盖。

## 6. 发现的缺陷（未修改代码）

### D-1（低）出站地址变更摘要的 before / after 无法相互比较 —— 已修复（主会话），G 复验通过

- 位置：worktree `paytech/channel-connector/paytech-channel-connector-protocol/.../service/processor/UpdateChannelEndpointsProcessor.java:81–82`。
  - before：`codec.sha256(channel.getEndpoints())`，即对 MySQL JSON 列读回的文本取摘要。MySQL 会重排键并在 `": "` 和 `", "` 处加空格。
  - after：`codec.sha256(codec.endpointsJson(...))`，是紧凑 JSON、按插入顺序。
- 证据：S5P2_ECHO 的两条记录。
  - v1→2 的 after = `21ef8a09…`，对应 `[{"endpointCode":"ECHO","baseUrl":"http://127.0.0.1:62611"}]`。
  - 紧接着 v2→3 的 before = `f844be4d…`，对应同一内容的 MySQL 形式 `[{"baseUrl": "http://127.0.0.1:62611", "endpointCode": "ECHO"}]`。
  - 内容相同，摘要不同。两种序列化已由本地 SHA-256 复算确认。
- 同一写法在第一段已经存在：`UpdateImplementationProcessor:65` 的 `codec.sha256(row.getFlowDefinitionJson())`，修改流程时也会出现同样的偏差。
- 影响：只影响留痕摘要的可比性，不影响执行、校验与数据。
- 建议：before 先经 codec 解析，再以同一规范序列化后取摘要；也可以统一改为对规范化 JSON（如 RFC 8785）取摘要。

### D-2（中）渠道列表页的并发请求超过 Gateway 并发上限，必定出现 429 —— 已修复（主会话），G 复验通过

- 位置：主工作区 `operations-console-web/src/pages/ChannelChannels.tsx:72`。`useTypeCounts` 用 `Promise.all` 并发发出 3 个 `total()`（全部、PAYMENT、SERVICE），再加上列表本身，共 4 个同时请求的 `GET /channel-admin/v1/channels`。
- 限制：Route 900000000000092401（块 92）的 Guard 为 `CONCURRENCY AUTHENTICATED_PRINCIPAL = 2`。
- 证据：
  - 同一 Token 用脚本并发请求：2 个时为 `[200, 200]`，3 个时为 `[200, 200, 429]`。
  - 截图 `02-list-payment.png` 中，整页显示「暂时无法加载，请重试 / 操作过于频繁，请稍后再试」。
  - `01-list-all.png` 中，支付和服务分段的计数为空。
  - `03` 也受影响。
  - `browser-log.json` 中，`/channel-admin/v1/channels` 在每个列表页都出现 2 次 429。页面之间间隔 9 秒，所以不是速率限制。
  - P2-web 只用 Mock 预览，没有经过 Gateway，因此没有暴露这个问题。
- 建议（任选一）：
  - 前端改为串行计数，或用一个请求返回各类型计数；
  - 块 129 追加 UPDATE，提高 92401 的 `AUTHENTICATED_PRINCIPAL` 并发上限（需同步 mor-test 注册 SQL 与初始化数据）。

两个缺陷都已通过消息通知主会话。修复内容与复验见第 11 节。原截图 01–03 保留，作为缺陷证据。

## 7. 偏差、观察与遗留

1. **两次基线运行作废**，第三次 `baseline3` 作为基线。
   - 第 1 次（04:18:13Z，编码 `S5_P2BASELINE_*`）与第 2 次（04:18:55Z，编码 `S5_P2BASELINE2_*`）在写盘前的卡数据检查处中止。原因是检查规则误把 `editor-capabilities` 说明文字中的 `cvv` 键当成 CVV 值；修正为"cvv 键且值为 3–4 位数字"后，才写出第 3 次的结果。
   - 两次作废运行已经执行了链路与沙箱：合成 PayerMax 流水与验证账号、用例、运行都已写入，作为历史数据保留，两次快照都包含这些数据，不影响对照。
   - 第 3 次运行中签发票据时 `ttlSeconds=120` 超出上限，返回 422。改为 90 后，用 `cardpay` 子命令只重跑了真实读卡 PAY（04:19:49Z），仍属于第一段版本。
2. **Platform 读卡的合成票据探针无法区分调用方**：card 已有表，未知票据也返回 404。D4 改用真实有效票据验证，见 3.1。
3. **服务地址卡片的文案**：出站地址编辑弹窗与概览写着「只允许 https」，但本机按配置放行 http，页面文案是静态的。这不是缺陷，只是提示。
4. **版本冲突文案**：调用授权和渠道出站地址的版本冲突都返回 409「外部 API 配置已被其他操作修改，请刷新后重试」，沿用了外部 API 的共用文案，对象名称不准确。这是低优先级的文案问题。
5. **沙箱访问日志**：`channel.outbound.access` 中 `callerService=VERIFY` 的行 `requestId=null`，因为运行在异步处理线程上，没有请求上下文。合同未要求沙箱必须有 requestId，如需关联可改用运行号。
6. **auto 测试库需要第二段 DDL**：经 `information_schema` 只读确认，`mor_channel_connector` 仍是第一段结构，有 `t_payment_channel` / `t_integration`，没有 `t_channel` / `t_channel_call_grant`。第二段代码在主工作区跑 auto 测试前，需要对该库执行第二段 schema 或迁移。本次没有改动它。`mor_channel_connector_ccu2` 已是第二段结构。
7. **服务渠道无法建立沙箱验证账号**：`t_channel_verify_account.entity_no` 仍为 NOT NULL，这是 P2-web 报告第 6 节的遗留，本次未覆盖。
8. **日志被截断**：启动器在重启时会覆盖 `local-environment/logs/<服务>.log`。本次重启后，第一段时期以及 E7 之前的 channel-connector 日志已不在文件中，相关证据都已提取到 JSON 输出：访问日志行、读卡 requestId 关联、无卡号检查。当前四个服务的日志中没有 ERROR。ops-bff 的 WARN 来自本次直连探针的 404。
9. **本机残留数据**（e2e 库，可保留）：
   - 服务渠道 `S5P2_ECHO`：ACTIVE，version 7，出站地址指向已关闭的回显端口；
   - 凭据账号 ACTIVE、授权 ACTIVE v3、标准 API `S5P2_ECHO_PING`、原子 API `ECHO_CALL` 与实现；
   - `S5_P2*` 验证账号、用例与运行。

   回显服务运行目录：`local-environment/e2e/runtime/stage5p2-echo/`，包含 `state.json` 与回显流水（凭据只记末 4 位）。
10. **输出扫描**：本报告与 `stage5p2*` 全部输出、`stage5p2-sql/`、`browser-log.json` 都已逐值扫描，不含以下内容：
    - 合成 PayerMax 密钥、管理员口令；
    - 账号 Vault Token、本次合成凭据；
    - appId、merchantNo、卡号。

    截图中只出现 Token 末 4 位与脱敏邮箱。

11. **本次不修（主会话决定）：** 第一段迁入的 `UpdateImplementationProcessor:65` 对 `flowDefinitionJson` 也是同样写法：before 是 `codec.sha256(row.getFlowDefinitionJson())`，即对数据库 JSON 文本取摘要。所以修改标准 API 实现流程时，变更记录的 before / after 摘要仍无法比较。

## 8. F：页面截图（`stage5p2-screens/`）

截图使用 playwright-core 1.58.1 与无头 Chromium，只读浏览，出站地址编辑弹窗只打开后取消。

| 截图 | 内容 | 结果 |
|---|---|---|
| 01 / 02 / 03 | 渠道列表：全部、支付渠道、服务渠道 | 01 显示两行，类型、出站地址、凭据、授权列正确，但分段计数缺失；02 整页报错 429；03 也受影响。均为缺陷 D-2 |
| 04 / 05 / 06 | 支付渠道：概览、协议配置、沙箱验证 | 页签为概览、技术账号 1、MID 1、协议配置、沙箱验证、变更记录；协议配置的实现 4、原子 API 4、脚本 2；沙箱列表正常 |
| 07 / 08 | 服务渠道：概览、凭据 | 页签为概览、凭据、协议配置、沙箱验证、调用授权 1、变更记录；激活条件全部打勾；凭据只写不读，显示 Token 末 4 位，读取方为 channel-connector-service |
| 09 / 10 | 调用授权：矩阵、列表 | 矩阵：counterparty-service × S5P2_ECHO_PING 为 ACTIVE；列表 1 条，v3，有停用与留痕按钮 |
| 11 | 出站地址编辑弹窗 | 配置版本 v4；ECHO 被 1 个原子 API 引用，提示不能删除或改编码 |
| 12 | 旧地址 `/external-api/integrations/PAYERMAX_MOCK` | 跳转到 `/channel/channels/238501215359533056`，即 PAYERMAX_MOCK 详情 |

浏览器控制台共 22 条：

- antd `Spin tip` 弃用警告 13 条，属于既有问题；
- `Space direction` 弃用警告 1 条；
- 429 资源加载失败 8 条，都与 D-2 有关：列表页 6 条；协议配置页与授权列表页各 1 条，这两处页面最终正常渲染。

## 9. 回滚方法

1. 停止新版本。注意：当前运行的 channel-connector JAR 是 G 步重新打包的版本，`*.pre-ccu2` 仍是第一段版本，未被覆盖。命令：`verification/local-e2e/run-service.sh stop ops-bff`、`stop platform`、`stop channel-connector`、`stop card`。
2. 恢复 JAR：把 4 个 `*.pre-ccu2` 改回 `*-exec.jar`（第一段版本），再用同一启动器依次启动 card → channel-connector → platform → ops-bff。
3. 恢复 channel-connector 库：按 `db/channel-connector/phase2-rollback.md` 从备份恢复，库名替换为 `mor_channel_connector_v2e2e`，备份文件为 `…/20261022T042107Z-ccu2-stage5/mor_channel_connector_v2e2e.sql`。先 DROP / CREATE 数据库，再导入；之后核对 28 表中含 `t_payment_channel` / `t_integration`，并确认 `channel_connector_app` 的库级授权。新版本期间写入的 `S5P2_*` 数据会随之丢弃。
4. Platform：按 `phase2-registration.sql` 头部的回滚段处理。
   - 第 3 段反向：4 条 Route 与按钮置为 DISABLED，授权 129201 置为 REVOKED。
   - 按 ID 删除块 129 的行，顺序为 guard_rule → query_param → route_header → gateway_route → console_resource → role_permission → permission。
   - 92302 的显示名恢复为「渠道与账号」。
   - 71006–71010、71303、71305 恢复为 ACTIVE（各 version +1）。
   - 递增 PLATFORM_ADMIN 的版本。
   - 本机 Gateway 会在 5 秒内自动加载，OPERATIONS 路由数应回到 317。

   也可以直接用备份 `mor_platform_v2e2e.sql` 整库恢复，但会丢弃备份之后 Platform 库的其他写入。

## 10. 产物清单（主工作区，本目录新增）

| 类别 | 文件 |
|---|---|
| 脚本 | `stage5p2_e2e.py`（A / D）、`stage5p2_compare.py`、`stage5p2_service.py`（E）、`stage5p2_echo_server.py`、`stage5p2_screens.mjs`（F）、`stage5p2_g.py` 与 `stage5p2_screens_fix.mjs`（G） |
| 结果 | `stage5p2-baseline.json`、`stage5p2-after.json`、`stage5p2-compare.json`、`stage5p2-service.json` |
| SQL 记录 | `stage5p2-sql/`：10-precheck、20-migrate、30-verify、40-cleanup、50/52/53-registration、hash-before / after |
| 截图 | `stage5p2-screens/`：12 张 png 与 `browser-log.json`；G 复验 3 张 `*-after-fix.png` 与 `browser-log-after-fix.json` |
| 报告 | 本报告 |

## 11. 缺陷修复复验（G 步，主会话追加，04:41–04:44Z）

### G1 D-1 出站地址摘要

1. 在 worktree 执行 `./mvnw -o -q -pl channel-connector/paytech-channel-connector-app -am package -DskipTests`，成功（04:41:57–04:42:06Z）。用 javap 确认 JAR 内的 `UpdateChannelEndpointsProcessor` 含 `endpointsDigest(String)`。
2. 停止 channel-connector，只替换 `paytech-channel-connector-app-exec.jar`。`*.pre-ccu2`（10-21 07:23）与 `*.pre-ccu` 未动。
3. 用原启动器、同一套参数重启：环境中带 allowed-schemes 与 trusted-networks，服务舱为默认并发。04:42:34Z 时 Health 为 UP。
4. 运行 `stage5p2_g.py`。脚本先启动回显服务，再经控制台对 `S5P2_ECHO` 连续修改出站地址 3 次（v4→5→6→7）：
   - 第 1 次：ECHO 指向本次回显端口；
   - 第 2 次：保留 ECHO，新增 ALT `https://echo-alt.example.test`；
   - 第 3 次：去掉 ALT，恢复为只有 ECHO。

| 记录 | before（前 12 位） | after（前 12 位） |
|---|---|---|
| v4→5 | `266b2792adce` | `aecbf8de9854` |
| v5→6 | `aecbf8de9854` | `97e9b661ce58` |
| v6→7 | `97e9b661ce58` | `aecbf8de9854` |

- 每条记录的 **before 都等于前一条的 after**。
- 3 个 after 与本地按写入格式（`endpointCode`、`baseUrl`；`sandbox` 仅在为 true 时输出）复算的 SHA-256 一致。
- v5 与 v7 的内容相同，after 摘要也相同。
- 3 次 PUT 都是 200。重启后通用入口调用一次：200 SUCCESS，回显收到 1 个请求，访问日志正常。

结果记录在 `stage5p2-service.json` 的 `G.endpointsDigestChained` 与 `G.serviceCallAfterRestart`。回显服务已在脚本结束时关闭，没有残留进程。

### G2 D-2 渠道列表 429

ops-web 是 vite 开发服务器，直接读取主工作区，修复已生效，没有重启。用 `stage5p2_screens_fix.mjs` 依次打开三个分段页，页面之间间隔 9 秒；新截图另存为 `*-after-fix.png`，原图保留。

| 页面 | 分段计数 | 列表 | 列表路由请求 |
|---|---|---|---|
| 01 全部 | 全部 2 / 支付渠道 1 / 服务渠道 1 | 2 行 | 4 次，**同一时刻最多 2 个**，全部 200 |
| 02 支付 | 同上 | PAYERMAX_MOCK 1 行 | 4 次，最多 2 个并发，全部 200 |
| 03 服务 | 同上 | S5P2_ECHO 1 行 | 4 次，最多 2 个并发，全部 200 |

- 三页都没有出现"暂时无法加载"提示，HTTP >= 400 的请求为 0。
- 浏览器控制台只有 antd `Spin tip` / `Space direction` 弃用警告，属于既有问题。
- 块 92 的 Guard 并发上限未调整。

**结束时间：2026-10-22T04:43:57Z。** 回显服务已关闭，本次没有在后台留下任何进程。四个服务保持运行（第二段版本）。gateway、nginx、payermax-mock 等其他会话的进程未动。
