# channel-connector 统一对外渠道出口（第二段）：mor-test 发布清单

- 授权：用户 2026-10-22 选择"部署 mor-test"，并要求先出清单、确认后再执行。阶段 7 的停启、root SQL、XXL 都由主会话执行。
- 本页是评审稿，执行记录写在第 9 节。

## 1. 基线与制品

| 项 | 内容 |
|---|---|
| 源码 | 干净 worktree `Code-MoR-ccu2-rel`，检出 main HEAD。要求文件存储会话先把改动提交到 main（`654cb14f` 之上）。<br>预期 HEAD = 数据查询 `9a209e6f` + 第二段 `654cb14f` + 文件存储提交。<br>发布前逐文件对比 `Code-MoR-dataquery-rel` 的工作树：数据查询与文件存储的改动必须全部包含，差异只能是第二段的 hunk。 |
| 时机 | 数据查询会话（code-mor-76）通知"验收通过"之后。 |
| 构建 | worktree 内执行 `./mvnw -o -pl <card-app, channel-connector-app, platform-support-app, operations-console-bff-app> -am package -DskipTests`，不 install；ops-web 按其 Dockerfile 构建。<br>镜像 tag 为 `ccu2-20261022`，记录 sha256，compose 一律固定 sha256。 |
| 替换 | card、channel-connector、platform-support、operations-console-bff、operations-console-web，共 5 个。 |
| 当前运行（回滚目标） | card `aee93799…`、channel-connector `1571ce18…`、platform `985d00ea…`、ops-bff `d221817d…`、ops-web `e29197ba…` |

## 2. 环境变量

- 候选覆盖文件以数据查询的 `requirements/20261022/platform-support-数据查询服务/release/compose-candidate.yaml` 为起点，保留其中全部 env：文件存储的各项，以及 Platform 的 `DATAQUERY_HTTP_CALLERS_0: operations-console-bff`。在此基础上只把 5 个镜像换成新的 sha256。
- channel-connector 第二段新增的配置键都有默认值：
  - `trusted-networks` 默认为空。mor-test 的 7 个出站地址都是公网域名，没有私网地址，因此不放行任何网段。
  - 两个执行舱的并发上限分别为 64 和 16。
  - 不新增 env。

## 3. mor-test 现状（只读核对，2026-10-22 05:5xZ）

- `phase2-10-precheck.sql` 第 1–5 段全部为空。对应关系共 8 行：
  - MATCHED 1 行：DEMO_NI；
  - INTEGRATION_ONLY 6 行：ALIPAY_PLUS、DLOCAL、I_MPGS_NI、PAYBY、PAYERMAX、TOSS_PAY，迁移后为 DRAFT 支付渠道，ID 沿用原接入方 ID；
  - CHANNEL_ONLY 1 行：AAAA（DRAFT）。
- 迁移前行数：

  | 表 | 行数 |
  |---|---|
  | payment_channel | 2 |
  | integration | 7 |
  | api | 25 |
  | script | 13 |
  | std | 26 |
  | verify account | 7 |
  | verify case | 32 |
  | verify run | 123 |
  | verify run step | 182 |
  | config_change | 40 |

  其中 `INTEGRATION*` 类型的变更记录共 14 条，全部可以映射。
- Platform：
  - 块 92 已激活，92302 为 ACTIVE；
  - 块 71 的接入方页面 71303 / 71305 与路由 71006–71010 为 ACTIVE（5/5）；
  - 块 129 尚不存在。

## 4. 执行步骤（任一步不符合预期即停，并按第 7 节回滚）

| 步 | 内容 | 预期 |
|---|---|---|
| 0 | 基线验收（旧版本）：渠道管理与外部 API 管理 GET 快照；DEMO_NI 的两个 NOT_SENT 场景；沙箱用例运行 1 次；运营导航 | 结果写入 `acceptance/results/phase2-before*.json` |
| 1 | 备份：`mysqldump --set-gtid-purged=OFF` 整库备份 `mor_channel_connector`、`mor_platform`；生成 `compose-rollback-phase2.yaml`，固定上表 5 个旧 sha256 | 备份写入 `~/Code-MoR-backups/<UTC>-ccu2-mor-test/` |
| 2 | 替换 card：新版本只接受 channel-connector 读卡；当前 channel-connector 已经以该身份读卡 | Health UP；Platform 身份读卡返回 404 |
| 3 | 停止 channel-connector，执行 `phase2-20-migrate.sql`、`phase2-30-verify.sql`，删除两张辅助表 | verify 全部符合；共 28 表；行数与第 3 节一致 |
| 4 | 启动新 channel-connector | Health UP；启动日志没有缺表或缺列报错 |
| 5 | Platform 执行 `phase2-registration.sql`：先跑第 1 段只读检查，再执行第 2 段（块 129）和第 3 段（激活）。**不重放块 71 的激活语句** | 影响行数与本机一致（第 3 段为 4 / 1 / 1 / 1） |
| 6 | 替换 platform、ops-bff、ops-web | Health UP |
| 7 | 确认 Gateway 拉取到新的 Snapshot，OPERATIONS 路由数减 1（新增 4 条，停用 5 条） | 新路由可访问；接入方的 5 个增删改接口返回 404 |
| 8 | 回归与新能力验收，见第 5 节 | 全部通过 |
| 9 | 清理第 8 步创建的验收数据（服务渠道和授权都不能删除：渠道置为 CLOSED，授权置为 DISABLED，并在报告中列出） | — |

预计停机：channel-connector 从第 3 步到第 4 步约 2–3 分钟，期间 Payment 调用渠道返回 NOT_SENT。

## 5. 验收

- **回归（与第 0 步对照）**：
  - 管理 GET 快照：除接入方名改为渠道名、新增字段外一致；
  - DEMO_NI 的两个 NOT_SENT 场景一致；
  - 沙箱用例运行 PASSED；
  - Vault Reveal 的调用方仍为 channel-connector。
- **第二段新能力**：
  - 渠道列表三个分段计数正常，没有 429；
  - 新建服务渠道 `S7P2_SVC`，出站地址为公网域名；
  - 未授权调用通用入口返回 403 `CHANNEL_CALL_NOT_GRANTED`；授权后调用到达出站阶段（域名不可达，按约定返回结果，不判为成功）；停用授权后再次被拒；
  - 出站地址写成私网地址返回 422；
  - 支付渠道走通用入口被拒；
  - 导航中没有接入方页面，有调用授权按钮。
- **同期服务冒烟**：数据查询与文件存储页面接口返回 200，币种、对账、商务页面接口返回 200（沿用第一段冒烟脚本，改为第二段的导航断言）。
- **浏览器（只读）**：渠道列表、支付 / 服务渠道详情、调用授权矩阵、旧接入方地址跳转。

## 6. 本次只执行的 SQL

- `mor_channel_connector`：`phase2-20-migrate.sql`、`phase2-30-verify.sql`，以及删除两张辅助表 `tmp_phase2_channel_map`、`tmp_phase2_row_count`。precheck 已只读执行。
- `mor_platform`：`phase2-registration.sql` 第 1–3 段。
- 不改动其他库，不写 XXL（第二段没有新任务）。

## 7. 回滚

1. 用 `compose-rollback-phase2.yaml` 把 5 个服务恢复为第 1 节的旧 sha256。
2. `mor_channel_connector`：从第 1 步备份整库恢复（DDL 无法事务回滚），见 `db/channel-connector/phase2-rollback.md`。
3. `mor_platform`：按注册 SQL 头部的回滚段处理：
   - 删除块 129 的行；
   - 92302 改回「渠道与账号」；
   - 71006–71010、71303、71305 恢复为 ACTIVE；
   - PLATFORM_ADMIN 版本 +1。
4. 确认 Gateway 拉取到回滚后的 Snapshot。

## 8. 风险

- 第 3 步之后无法只回退 SQL，只能整库恢复。备份完成之前不开始第 3 步。
- I_MPGS_NI 正由另一个会话做配置级接入。迁移后它成为 DRAFT 支付渠道，配置与 ID 不变。旧接入方页面的地址会跳转到渠道详情。
- 服务渠道在 mor-test 没有可达的合成服务，真实出站成功已在本机阶段 5 验证；mor-test 只验证授权、守卫与隔离。

## 9. 执行记录

2026-10-22，主会话执行。用户 "可以，按清单执行"。前置条件：文件存储会话提交 `346a1822`；数据查询会话验收通过，并提交 `4b5426e5`。

**制品**
- 干净 worktree `Code-MoR-ccu2-rel` 检出 `4b5426e5`。与 `Code-MoR-dataquery-rel` 逐文件比对，代码只差第二段；另有 2 个文件不同，不进镜像，已由文件存储会话解释。
- 候选 JAR 与运行 JAR 逐条目（含嵌套 JAR）比对：
  - platform：只差 `console-resource-capabilities.json`；
  - ops-bff：89 项，全部与渠道相关；
  - channel-connector：331 项，全部与渠道相关，另有 `application.yml`；
  - card：`RevealChannelCardMaterialProcessor` 1 项，另有基线带来的 `YamlEventCatalog`。
- 镜像内 JAR 的 SHA-256 与构建产物一致；Entrypoint、User、WorkingDir、Env 与运行镜像相同。
- 用候选覆盖文件渲染出的环境变量，5 个服务都与运行容器逐项相同。

| 服务 | 回滚目标 | 新镜像（tag `ccu2-20261022`） |
|---|---|---|
| card | `aee93799…` | `542a16f0567d57c547734aa9c9ef65f77aa0846c96823e1d4539fc20e48df244` |
| channel-connector | `1571ce18…` | `798940ead0ec7900f2e62c1c08434291fd745d441784e4309f621997d51de366` |
| platform-support | `985d00ea…` | `d50f28864d6c153bf0f77df6dd3df43b442b26852cd988e7eca3ccffc43f5671` |
| ops-bff | `d221817d…` | `0abb662a764aa2af09f7dec546396b616f81fe7d8b56ebacc5434e3aa5a6e65f` |
| ops-web | `bcadc589…` | 首版 `fb29b533…`；第二版 `9eaa85211107a08bc1f4825517ec167444643e8f923363627d344978599cd9bc`（`ccu2-20261022b`，D-3 修正） |

| 步 | UTC | 结果 |
|---|---|---|
| 0 基线 | 06:2x–06:47 | `chain before`：两个 NOT_SENT（IMPLEMENTATION / STANDARD_API）；`rerun before` 7/7 PASSED；重跑后重新采集 `snapshot before`，355 次调用全部 200；`nav before` 仍有接入方页面 |
| 1 备份 | 06:34:10；06:48:09 | `~/Code-MoR-backups/20261022T063410Z-ccu2-mor-test/`：两库整库备份；停 channel-connector 时再备份一次 `mor_channel_connector-at-stop.sql`；另有 `compose-rollback-phase2.yaml` |
| 2 card | 06:47:11–06:47:43 | `542a16f0…` healthy |
| 3 迁移 | 06:47:52 停 channel-connector；06:48:10 执行 migrate / verify；06:48:38 删除辅助表 | verify 全部符合：28 表、legacy 0、15 表前后行数一致（verify_run 130、step 189，含第 0 步新增的 7 次运行）；对应关系 8 行，problem 全部为空；变更记录中已无 `INTEGRATION_*`。输出见 `sql/phase2-*-mortest.out` |
| 4 channel-connector | 06:48:50 | `798940ea…` healthy，日志无缺表、缺列；停机约 58 秒 |
| 5/6 换 Web 与注册（顺序对调，见下） | 06:49:06–06:50:04 替换 platform、ops-bff、ops-web；06:50:35 执行注册 | 注册第 1 段核对全部符合（PLATFORM_ADMIN 版本 42）。第 2 段：permission 1、role_permission 1、console_resource 1、92302 改名 1、71303/71305 停用 2、71006–71010 停用 5、route 4、header 20、query 7、guard 24。第 3 段：4/1/1/1。未重放块 71 激活 |
| 7 Gateway | 06:50:56 | 自动刷新（≤60 秒）。`nav after`：没有接入方页面；「渠道」页有调用授权按钮，标准 API 页仍在 |
| 8 验收 | 06:51–07:12 | 见下 |
| 9 清理 | 07:12:43 | S7P2_SVC 置为 CLOSED；授权 DISABLED（不删除）。测试标准 API S7P2_PING、原子 API 与实现保留（授权按 standard_api_id 引用）；变更记录与访问日志保留 |

**顺序偏差。** 注册 SQL 自身的前置条件要求 ops-bff / ops-web 先换成第二段版本，所以第 5、6 步对调。本机阶段 5 也是同样顺序。

**验收结果**
- 快照对比：after 388 次调用，386 次 200，另 2 次 404 是已下线的接入方列表与详情。差异都属预期：
  - 6 个 INTEGRATION_ONLY 迁入为 DRAFT 支付渠道，渠道 total 由 2 变为 8；
  - 渠道新增字段；
  - `CHANNEL_ACCOUNT_CONFIG.entityNo` 改为非必填；
  - 36 项新增读取都属于新渠道和 AAAA；
  - 变更记录 14 条改名、26 条不变，0 条不一致。
- `chain`、`rerun` 与 before 逐项一致，Vault 调用方为空，两次相同。
- `service` 18/18 通过，结果在 `results/phase2-service.json`：
  - 私网地址返回 422，字段为 `endpoints[1].baseUrl`；
  - 未授权返回 403 `CHANNEL_CALL_NOT_GRANTED`；
  - 授权后进入执行阶段，返回 200、`ERROR`（`.invalid` 域名不可达，按约定不判成功）；停用授权后再次 403；授权留痕 2 条；
  - 支付渠道走通用入口返回 400（"支付渠道只能经支付入口执行"）；
  - 接入方 5 个增删改接口返回 404 `GATEWAY_ROUTE_NOT_FOUND`；
  - 分段计数：首轮失败。原因是脚本把三次计数并发发出（页面本身是串行），改为串行后通过：全部 9 = 支付 8 + 服务 1。
- 冒烟 31/31：渠道、调用授权、数据查询、文件存储、币种、对账、商务均正常。
- 浏览器（只读，`ccu2_browser.py`）：首轮 13/13 页面通过，但出现 2 个 429，记为缺陷 **D-3**：渠道详情对同一路由同时发 3 个请求——协议配置页签的实现列表，以及调用授权页签的授权列表——超过 Gateway 每人并发 2。修正为详情页把已读数据传给子页签，不再重复请求（3 个文件），本地 tsc 与 Web 1308/1308 通过。重建 ops-web `9eaa8521…` 并于 07:11:48 替换。复验 13/13，控制台错误 0，4xx / 5xx / 失败请求 0。
