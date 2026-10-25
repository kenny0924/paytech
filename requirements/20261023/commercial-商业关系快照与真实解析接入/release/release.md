# commercial：商业关系快照与真实解析接入 · mor-test 发布包

阶段 6（2026-10-25 11:00—12:10Z）。本阶段只做了以下几类操作：
- 只读核对：`docker inspect`；用临时容器从**镜像**取出 JAR / dist / health-probe；`Config.Env` 只比较、不输出值；六个库只读 SELECT；`mysqldump --single-transaction` 只读备份；读 `docker logs`。
- 构建：在干净 worktree `Code-MoR-relsnap-rel` 里构建 7 个候选镜像（[build-report.md](./evidence/build-report.md)）。
- 数据盘点（[data-inventory.md](./evidence/data-inventory.md)）与离线演练：断网一次性 MySQL 副本上按发布顺序执行全部 SQL 与全部回滚，并用现网 Platform JAR 的 Gateway 快照编译器编译块 137 四种状态（[rehearsal.md](./evidence/rehearsal.md)）。
- 编写发布包与脚本，并全部干跑。

**本阶段没有做的事：** 没有停 / 起 / 替换 mor-test 任何容器；没有对 mor-test 执行写 SQL；没有登录，也没有调用任何业务接口或 Nacos；没有提交；没有 `mvn install`；没有改 `:development` tag；没有 `docker cp` 进运行容器。

阶段 7 须用户授权，由主会话按第 6 节执行，结果写第 10 节。当前实现状态以 [CURRENT_IMPLEMENTATION.md](../../../../agent-docs/CURRENT_IMPLEMENTATION.md) 为准。

**本次内容：**
- **换镜像（7 个）：** compliance、commercial、client-master、acquiring、risk-decision、operations-console-bff、operations-console-web。Platform 不换镜像。
- **SQL（5 个库）：** compliance DDL + Client 资格回填；client-master DDL + AE001 主档种子 + 迁移数据 + AC-11 资料补丁；acquiring DDL + PLATFORM 改写；risk-decision PLATFORM 删除；Platform 块 137（150 行，先休眠后激活）。
- **数据（经运营接口）：** PayFac Partner 合同与两个方案；AE001 / PayFac Partner 资格手工登记；多语言 2 个命名空间、220 条 key / 翻译、发布 #8。
- **新增环境变量：** `ACQUIRING_COMMERCIAL_RESOLUTION_MODE=MOCK`、`COMMERCIAL_RELATIONSHIP_SNAPSHOT_ENABLED=false`；AC-11 窗口内临时切 REAL / true（D-7）。
- **不动：** Gateway、Nginx、Nacos、主 compose、其余容器、payment / billing / ledger 配置。

**阶段 6 的两个阻断发现（详见 data-inventory 第 0 节）：**
- **F-1：** mor-test client-master 没有任何 OE / Local Partner 主档，正式迁移文件在现状下建不出 PayFac Partner，所以需要种子 21（D-1）。
- **F-2 GAP-PROFILE：** 既有缺陷。入驻审核通过后资料从未定稿，所有真实 Client 在 REAL 下都不合格，所以需要补丁 23（D-3）与后续需求。

## 1. 源码与镜像

详见 [build-report.md](./evidence/build-report.md) 第 1 节。

- **源码：** 开发目录 `/Users/kenny/Desktop/Workspace/Code-MoR-relsnap`（detached `8a86a31c` + 本需求未提交改动，419 个文件，补丁 SHA-256 `118a4b0aac5e6e32…`）。构建目录 `/Users/kenny/Desktop/Workspace/Code-MoR-relsnap-rel`（`8a86a31c` + 同一补丁），两者逐目录相同。
- **基线：** 7 个运行容器与 `8a86a31c` 基线比较，自身模块全部 0 差异；其余差异都落在 Foundation 或其他 Owner 的 api 模块，均为已提交改动。候选镜像不回退任何现网内容。

| 服务 | 候选（tag `relsnap-20261025a`） | 回滚（现网） |
|---|---|---|
| compliance-service | `sha256:ca1585452f9c…` | `sha256:2f62a26ad46c…`（现网按 `:development` 启动，回滚只认 ID） |
| commercial-service | `sha256:e5c1ee6b0d03…` | `sha256:c6c9b8f663ae…` |
| client-master-service | `sha256:eeed4e63235d…` | `sha256:2e495b40adee…` |
| acquiring-service | `sha256:e0a1f618a050…` | `sha256:f6d55ade25d1…` |
| risk-decision-service | `sha256:288b0ff6a607…` | `sha256:d69ca678416a…` |
| operations-console-bff | `sha256:feefe73f0aa3…` | `sha256:0bc674106d7b…` |
| operations-console-web | `sha256:4f44725eb26f…` | `sha256:70d3fdfbb6be…` |

完整 ID 见 [compose-candidate.yaml](./compose-candidate.yaml) 与 [compose-rollback.yaml](./compose-rollback.yaml)。

**包的有效前提：**
- 开发目录在 11:23Z 之后没有改动；
- 阶段 7 开始时 7 个运行容器仍是回滚列的 ID。

任一条件不满足，本包作废，按 build-report 第 1.1 节重建。

## 2. SQL

文件在 [sql/](./sql/)，`SHA256SUMS` 同目录，执行包装为 [scripts/apply_sql.sh](./scripts/apply_sql.sh)：
- 缺省为演练（dry），带 `--apply` 才执行；
- 纯事务步骤的 dry 会把 COMMIT 换成 ROLLBACK；
- 含 DDL 的步骤 dry 不连库，只打印将要执行的语句。

| 步骤 | 文件 | 库 | 说明 |
|---|---|---|---|
| precheck / postcheck | 00 / 99 | 跨库只读 | 期望值写在标签里 |
| cmp-ddl | 10 | mor_compliance | case_id 可空、role_id 字符型、basis_note |
| cmp-backfill | 11 | mor_compliance | 37 个 Client 资格投影 |
| cm-ddl | 20 | mor_client_master | 收单资质列、3 张新表、注释 |
| cm-seed | 21 | mor_client_master | **D-1**：AE001 = Party 880000000702、平台 = 880000000701、准入范围、法律文件、卖方披露（测试占位）、7 个 Affiliation |
| cm-data | 22 | mor_client_master | 正式迁移文件原文：PayFac Partner 900000000001370001，AE001 → PARTNER，角色回填 |
| cm-ac11 | 23 | mor_client_master | **D-3**：只补 DEMO-CL-001 资料定稿 |
| com-fallback | 30 | mor_commercial | 兜底，D-2 已确认走运营 API，**默认不执行**；含 CARD_STANDARD 与 APPLE_PAY_STANDARD 两条 0 费率价格（D-4） |
| acq-ddl / acq-platform | 40 / 41 | mor_acquiring | 新表与三列；46 条订单改写 |
| risk | 50 | mor_risk_decision | 46 条快照改写、目录行删除 |
| plt-reg / activate / deactivate / rollback | 60 / 61 / 62 / 69 | mor_platform | 块 137 |
| rb-* | 80—89 | 各库 | 回滚（第 7 节） |

离线演练结果（[rehearsal.md](./evidence/rehearsal.md)）：
- 全部步骤成功，postcheck 全等于期望值；
- 结构漂移：迁移前对 `8a86a31c` 为 0，迁移后对 worktree schema 为 0；
- 快照编译：基线 579/574、休眠 590/574、激活 590/585（块 137 新增 11 条）、停用 590/574、回滚 579/574；
- 全量回滚后 216 张表与备份相同，只有 PLATFORM_ADMIN 版本 +3（设计行为）。

**注意：** 带断言的文件（21、22、23、81）中止时会留下 `relsnap_*` 存储过程。先 `DROP PROCEDURE IF EXISTS relsnap_…`，再排查。文件开头也会先 DROP，所以重跑不受影响。

## 3. 配置

详见 [build-report.md](./evidence/build-report.md) 第 3 节。

- **环境变量：** 候选与回滚文件渲染后，与运行容器逐键、逐值比较：
  - commercial、acquiring 各只多一个新开关；
  - commercial 原样带上历史覆盖文件中的 19 个键与 depends_on（主 compose 缺这些键，commercial 起不来）。
- **服务发现：** 新的调用 acquiring → `lb://commercial-service`、commercial → `compliance-service` / `client-master-service`、ops-bff → 同两者，都经 Nacos，不新增 base-url。启动日志显示这些服务均已注册；实时实例列表未核实（需要登录 Nacos）。
- **其余：** 挂载、IP、内存、entrypoint、healthcheck 都不变；Snowflake Worker 不改。client-master 与 channel-connector 的 26 号重号是既有问题，不阻塞。
- **AC-11 窗口开关：** [compose-ac11-window.yaml](./compose-ac11-window.yaml) 只设 `ACQUIRING_COMMERCIAL_RESOLUTION_MODE=REAL` 与 `COMMERCIAL_RELATIONSHIP_SNAPSHOT_ENABLED=true`，与候选文件叠加使用。已渲染核对：键数不变、镜像不变、只有这两个值变化。

## 4. 经运营接口的数据（阶段 7）

| 项 | 做法 | 前提 |
|---|---|---|
| PayFac Partner 合同（D-2） | 经 Gateway → ops-bff → commercial：<br>1. `POST /commercial-admin/v1/agreements`：PARTNER 合同；localPartnerId 900000000001370001；覆盖 AE001；签约方 LOCAL_PARTNER 与 OPERATING_ENTITY 均为 880000000702 / 881000000702，PLATFORM_SIGNATORY 为 880000000701 / 881000000701；一份 SIGNING_DOCUMENT（合成文件 ID 与 SHA-256，同现有演示合同）。<br>2. 签署 → 激活。<br>3. 建 PARTNER_SHARE、PLATFORM_SERVICE_CHARGE 两个方案 → 版本 → 价格（D-4：每个方案 AE001 / USD 两条，CARD_STANDARD priority 1、APPLE_PAY_STANDARD priority 2，费率 0）→ 提交 → 第二个账号批准 → 激活。<br>**生效时间取执行当时**，之后的验收订单时间都要晚于它 | 种子 21 已执行；需要第二个运营账号做 checker |
| 资格手工登记 | 新资格页（B10）为 AE001 登记 OPERATING_ENTITY / ENTITY_UNDERWRITING、为 PayFac Partner 登记 LOCAL_PARTNER / PARTNER_DUE_DILIGENCE，有效期不设终点，依据写“mor-test 演示主体（2026-10 阶段 7）”。DEMO-CL-001 已由 11 回填 | 块 137 已激活 |
| 多语言（D-5） | 1. 建命名空间 `qualification`（sort 220）、`master-data`（230）。<br>2. KEY_IMPORT 上传 worktree 英文全集：预览新增 219、基准变更 1（`risk.legacyCatalog.fieldHelp.entity_source_type`）、废弃 0。<br>3. TRANSLATION_IMPORT 只含这 220 个 key 的 zh-CN，**不含** 3 条 `nav.sidebar.*`。<br>4. 核对已有翻译快照不变，然后发布 **#8**。<br>脚本 [acceptance/rs_bundles.py](./acceptance/rs_bundles.py)（做法同文件上传入口 `fu_bundles.py`，只增不改）：缺省干跑只构建导入文件到 [i18n-bundles/](./i18n-bundles/)（英文全集 8813 key / 23 个命名空间、zh-CN 增量 220 条，`SHA256SUMS`、`plan.json`），`--apply` 执行。比对见 [i18n-diff-vs-mortest.txt](./evidence/i18n-diff-vs-mortest.txt) | ops-web 新镜像已上 |

## 5. 备份

- **阶段 6 只读备份：** scratchpad `backup/relsnap-stage6-mortest/`（六个库，11:12:17Z，`SHA256SUMS`）。仅作盘点与演练依据，**不作阶段 7 回滚基线**。
- **阶段 7 第 ① 步重新备份：** `sqlx backup` 写入 `~/Code-MoR-backups/<时间>-relsnap-mor-test/`，记下 SHA-256。回滚与不可逆部分的查阅都以这份为准。

## 6. 执行步骤（阶段 7，主会话执行）

在主工作区根目录 `/Users/kenny/Desktop/Workspace/Code-MoR` 执行：

```
R="/Users/kenny/Desktop/Workspace/Code-MoR-relsnap/requirements/20261023/commercial-商业关系快照与真实解析接入/release"
O="$R/compose-candidate.yaml"; RB="$R/compose-rollback.yaml"; W="$R/compose-ac11-window.yaml"
BK="$HOME/Code-MoR-backups/$(date -u +%Y%m%dT%H%M%SZ)-relsnap-mor-test"
up()  { ./test-environment/compose.sh -f "$O" up -d --no-deps --no-build --pull never --wait --wait-timeout 180 "$@"; }
upw() { ./test-environment/compose.sh -f "$O" -f "$W" up -d --no-deps --no-build --pull never --wait --wait-timeout 180 "$@"; }
chk() { for s in "$@"; do docker inspect mor-test-$s-1 --format "$s {{.Image}} {{.State.Health.Status}} rc={{.RestartCount}}"; done; }
errs() { docker logs --since "$2" mor-test-$1-1 2>&1 | grep -c ' ERROR '; }
keys() { docker exec mor-test-$1-1 printenv | cut -d= -f1 | sort; }   # 替换前后各取一次比较（只看键名）
sqlx() { "$R/scripts/apply_sql.sh" "$BK" "$@"; }
```

**通用规则：**
- 每换一个服务，都要满足 `chk` 为候选 ID、healthy、rc=0，`errs` 为 0，`keys` 与替换前相同（commercial、acquiring 各多一个开关）。
- 任一步不符合预期就停，按第 7 节回滚。
- 每个 SQL 步骤先 dry 再 `--apply`（DDL 步骤的 dry 只打印）。

1. **① 协调与预检**
   - 通知第 11 节的会话：本窗口替换 7 个服务、写 5 个库。
   - 确认 7 个运行容器的 `.Image` 仍等于回滚列。
   - `sqlx backup`（记下 SHA-256），然后 `sqlx precheck`：与 [data-inventory.md](./evidence/data-inventory.md) 的值逐项一致，特别是 PLATFORM 订单 / 快照 46 / 46、client-master OE 相关表 0 行、块 137 空闲。记下角色 510 的版本与生效路由数。
   - 若 mor-test 已出现新的 PLATFORM 订单，或 OE 数据已有人写入，先停，重新盘点。
2. **② compliance**
   - `sqlx cmp-ddl --apply` → `up compliance-service` → `chk` / `errs` / `keys`。
   - `sqlx cmp-backfill` → `sqlx cmp-backfill --apply`。
   - 期望：postcheck 的 `cmp.*` 为 37 / 37。
3. **③ client-master SQL**（旧镜像仍在运行，acquiring 是 MOCK，没有调用方）
   - `sqlx cm-ddl --apply`；
   - `sqlx cm-seed` → `--apply`（D-1）；
   - `sqlx cm-data --apply`；
   - `sqlx cm-ac11` → `--apply`（D-3）；
   - `sqlx postcheck`：`cm.*` 全部等于期望值，**`cm.payfac_partner_party_eq_ae001 = 1`**（41 / 50 的前提，R-6）。
4. **④ commercial → client-master，紧接着连续执行**
   - `T4=$(date -u +%FT%TZ)`；`up commercial-service` → 检查；`up client-master-service` → 检查。
   - 两者之间的窗口内，商业解析会 503（acquiring 仍是 MOCK，不影响建单）。
   - 之后 `errs` 两个服务均为 0。
5. **⑤ acquiring**（必须先于 risk-decision；40 之后旧镜像无法建单，所以三步紧接着执行）
   - `sqlx acq-ddl --apply` → `sqlx acq-platform --apply`（记下输出的 `relsnap_rewrite_time`）→ `up acquiring-service` → 检查。
   - postcheck：`acq.*` 为 0 / 46 / 0 / 0 / 0。
   - `cd "$R/acceptance"`；`python3 -I stage7_relsnap.py --step S-0`（MOCK 建单冒烟，第 8 节）：201，`entity_source_type=PARTNER`、local_partner_id 107。
   - 注意：MOCK 订单引用占位 LP 107 与占位版本 108 / 109，支付成功后 billing 取 Partner 价格会像以前的 Mock 104 / 105 一样 404 进 DLQ（technique 4.5），属既有现象，切 REAL 后消失；勿重放。
6. **⑥ risk-decision**
   - `sqlx risk --apply`（记下 `relsnap_rewrite_time`）→ `up risk-decision-service` → 检查。
   - postcheck：`risk.*` 为 0 / 46 / 2。
   - 再做一次 MOCK 建单冒烟：风控评估不报参数错误。
7. **⑦ ops-bff → ops-web**
   - `up operations-console-bff` → 检查；`up operations-console-web` → 检查，入口 200。
8. **⑧ Platform 块 137 与多语言**
   - 注册：`sqlx plt-reg` → `--apply` → postcheck（6/6/6/11/38/17/66，REVOKED / DISABLED）。等 10 秒，`docker logs --since <时刻> mor-test-api-gateway-1 2>&1 | grep -c CONTROL_PLANE_UNAVAILABLE` 为 0。
   - 多语言：按第 4 节建命名空间、导入并发布 #8（D-5）。
   - 激活：`sqlx plt-activate` → `--apply`（11 / 6 / 6 / 1）→ postcheck 全部 ACTIVE、角色版本 +1、生效路由 +11。等 10 秒，Gateway 同样无 `CONTROL_PLANE_UNAVAILABLE`。
9. **⑨ 运营数据与浏览器验收（AC-01、02、12）**
   - 资格登记与 PayFac Partner 合同按第 4 节；
   - `python3 -I stage7_relsnap.py --step preflight`（门禁只读核对，同时记下 billing DLQ 基线）；浏览器验收 `node stage7_browser.mjs --dry-run && node stage7_browser.mjs`（第 8 节 B-1）。
10. **⑩ AC-11 窗口开启（D-7）**
    - 门禁，全部满足才继续：
      - PayFac 合同与两个方案 ACTIVE；
      - AE001、PayFac Partner、DEMO-CL-001 的资格都是 APPROVED；
      - AE001 × US × en 卖方披露存在；
      - D-4 价目已就位：两个方案的 ACTIVE 版本各有 CARD_STANDARD 与 APPLE_PAY_STANDARD 的 USD 价格（只读 SQL 核对 `t_commercial_term_price`）。
    - **开窗前**通知其他会话（D-7）：窗口内只有 DEMO-CL-001 能建单（R-3），给出预计结束时间。
    - `T10=$(date -u +%FT%TZ)`；`upw acquiring-service`（REAL）→ 检查，`printenv ACQUIRING_COMMERCIAL_RESOLUTION_MODE` 为 REAL。
    - `python3 -I stage7_relsnap.py --step S-1`、`--step S-2`、`--step S-3`；可选 S-4：人工完成一笔 APPLE_PAY 支付后 `--step S-4 --since <支付前 UTC>`（快照不生效，版本 0）。
11. **⑪ 快照生效验证**
    - `upw commercial-service`（true）→ 检查；
    - `python3 -I stage7_relsnap.py --step S-5`：新订单 `effective_relationship_model_version = 1`，S-2 的订单仍为 0。
12. **⑫ 窗口关闭**
    - `up commercial-service`（false）→ `up acquiring-service`（MOCK）→ 检查两者的开关值；
    - `python3 -I stage7_relsnap.py --step S-6`（MOCK 建单冒烟）与 `--step AC-13`（只读）；
    - 通知其他会话窗口结束。
13. **⑬ 记录与通知**
    - 填第 10 节；`delivery.md` 补环境操作记录；
    - 通知各会话：新镜像 ID 与源码基线（这 7 个服务今后的候选须包含本需求，直到提交）；语言包 #8；块 137 已用（下一个从 138 起）；mor-test 已有 AE001 / PayFac 主档。

## 7. 回滚

`upr() { ./test-environment/compose.sh -f "$RB" up -d --no-deps --no-build --pull never --wait --wait-timeout 180 "$@"; }`

**触发条件（任一满足）：**
- 替换后 healthy 超时或 rc>0；
- 自替换起 ERROR 日志持续出现；
- Gateway `CONTROL_PLANE_UNAVAILABLE` 在 30 秒后仍出现；
- MOCK 冒烟建单失败；
- postcheck 与期望值不符；
- REAL 窗口内出现预期之外的 5xx。

REAL 窗口内的问题，**先做第 ⑫ 步（回到 MOCK / false）**，通常不需要回滚镜像。

| 范围 | 做法 | 不可逆部分 |
|---|---|---|
| 窗口开关 | `up commercial-service`、`up acquiring-service`（只用候选文件） | 窗口内冻结的快照与版本 1 的订单保留（开关只影响新订单） |
| 运营入口 | `sqlx plt-deactivate --apply`；需要彻底删除时再 `sqlx plt-rollback --apply` | 无（演练回到 579/574） |
| 语言包 | 一般不回滚（#8 只增 key）。确需时切回 #7（`current-release`，`{"releaseNo": 7, ...}`）；**不要切回 #6**（会丢掉侧栏中文） | key 保留，只能经导入标为废弃 |
| ops-web → ops-bff | 先停用入口，再 `upr operations-console-web`、`upr operations-console-bff` | — |
| risk-decision | `upr risk-decision-service`，然后执行 89（先把 `@relsnap_rewrite_time` 改为第 ⑥ 步的输出）。单独回滚 risk 可以（旧 risk 也接受 PARTNER）；**回滚 acquiring 之前必须先回滚 risk**：旧 acquiring 的 Mock 发 PLATFORM，新 risk 会拒绝 | 之后新建的 PARTNER 快照保留 |
| acquiring | 依次 `upr acquiring-service` → `sqlx rb-acq-platform --apply`（改回 46 条）→ `sqlx rb-acq-ddl --apply`。三步紧接着执行：旧镜像在 88 执行前无法建单 | 快照冻结表与三列丢弃；新 Mock 订单（LP 107）保留为 PARTNER |
| commercial + client-master（数据一起回） | 1. `upr commercial-service`、`upr client-master-service`。<br>2. `sqlx rb-cm-ac11` / `rb-cm-data` / `rb-cm-seed` / `rb-cm-ddl --apply`（82 → 83 → 84 → 85）。<br>3. PayFac 合同若经运营 API 建立，就经运营入口终止合同、停用方案（保留审计）；SQL 兜底时执行 86。<br>须与 acquiring / risk 回滚同一窗口（旧代码认 PLATFORM，新代码不认） | Admin 修改与快照随 83 丢弃；REAL 订单引用的主档 ID 会悬空（84 头部有核对 SQL）；合成 hash 已冻结进订单 |
| compliance | `upr compliance-service` → `sqlx rb-cmp-backfill --apply` → `sqlx rb-cmp-ddl --apply` | 手工登记的资格与依据、新代码写入的投影全部丢弃（只能查阶段 7 备份） |

**回滚顺序：** 开关 → 入口 → ops-web → ops-bff → risk-decision → acquiring → commercial + client-master → compliance。

## 8. mor-test 验收

**Client 侧调用方式：**
- mor-test 没有可用的 Client API 密钥（不新建）；
- 同前例 `acquiring-到期后支付结果确认/release/acceptance/accept_result_after_expiry.py`：经一次性私网探针容器直连 acquiring `/client-gateway-api/v1`，带 Gateway 注入的可信 Header（`X-Gateway-Client-Id=237575207592763393`）；
- 商业解析直连 commercial `POST /checkout-context/v1/resolutions`（`X-Caller-Service: acquiring-service`）。

**验收约束：**
- 金额 USD、消费者 US、locale `en`；
- 所有业务时间晚于 PayFac 合同 / 方案的生效时间（mor-test-release-card-lessons）。

脚本（阶段 6 只做了离线核对：语法、`--help`、`--dry-run`；没有调用 mor-test）：
- [acceptance/stage7_relsnap.py](./acceptance/stage7_relsnap.py)：`--step preflight|S-0|S-1|S-2|S-3|S-4|S-5|S-6|AC-13 [--dry-run] [--since <UTC>]`。每步单独执行，并先核对本步要求的开关值，开关不符即停。库只读 SELECT；S-3 的撤销 / 重登经运营入口，并在 finally 中按原有效期恢复。结果写 `stage7-relsnap-result.json` 与 `stage7-relsnap.txt`，`chk_…` 令牌脱敏。
- [acceptance/stage7_browser.mjs](./acceptance/stage7_browser.mjs) + [stage7_browser_api.py](./acceptance/stage7_browser_api.py)：B-1 英文 + 中文两轮。
  - Q0：经页面正式登记 AE001 / PayFac 的资格。
  - 撤销与版本冲突只用 Client 236087245667409921，结束时恢复原有效期。
  - 收单资质与 REFERRER 往返后恢复原值。
  - 结果写 `stage7-browser-result.json`，截图写 `screens/`。
- [acceptance/rs_bundles.py](./acceptance/rs_bundles.py)：见第 4 节。

| 编号 | 检查 | AC |
|---|---|---|
| S-0 | MOCK 建单 201，冻结 AE001 + PARTNER（LP 107），绑定表 0 行、版本 0 | AC-07（MOCK 不变）、AC-13 |
| B-1 | 浏览器（英文 + `?lang=zh-CN`）：<br>- 经营主体列表 / AE001 详情：准入范围 US/USD/RETAIL/5999、提供方 PayFac、卖方披露；<br>- 收单资质 PAYFAC → MERCHANT_RESALE_APPROVED → PAYFAC 往返；<br>- Partner 列表 / PayFac 详情：OE_PROVIDER 锁定，REFERRER 增删；<br>- 资格页：Q0 经页面登记 AE001 与 PayFac 两条资格（正式数据）；筛选、查找主体、历史时间线、按角色限定资格类型；<br>- 验收用 Client 236087245667409921（不是 DEMO-CL-001）：真实撤销、旧版本 409 + 刷新、撤销后重登，结束时经接口恢复原有效期 | AC-01、02、12 |
| S-1 | REAL：commercial 直连解析 DEMO-CL-001 → 200；快照为 C1（MOR）角色绑定、`fund_flow=THROUGH_CUSTODIAN`；sourceType PARTNER、partnerTerms 为 PayFac 两版本、PLATFORM 角色 partyId 880000000701；卖方公开文本；不传支付方式时返回全部 PAYMENT_METHOD 开通项 | AC-04、05、06 |
| S-2 | REAL：私网 Client API 建单 201：<br>- `t_order_relationship_binding` 每角色一行、`fund_flow`、`effective_relationship_model_version=0`、`snapshot_model_version`；<br>- `t_order_legal.public_*` = 卖方披露；<br>- 冻结合同 / 版本为真实 ID | AC-07、08、11（C1） |
| S-3 | 失败映射（合成，不留数据）：<br>- 其他没有 Affiliation 的 Client 建单 → `CLIENT_CONFIGURATION_NOT_READY` 或 `CHECKOUT_NOT_ELIGIBLE`，不落单；<br>- 临时撤销 DEMO-CL-001 资格 → `CHECKOUT_NOT_ELIGIBLE`，随后重新登记 | AC-09、04 |
| S-4 | 用一笔 APPLE_PAY 支付走到成功（可选，需要渠道可用）：billing 按 PARTNER 计费、DLQ 无新增 | 7.7 影响核对 |
| S-5 | 快照开关打开后再建一单：`effective_relationship_model_version=1`；S-2 的订单仍为 0 | AC-08 |
| S-6 | 回到 MOCK 后，S-0 再走一次 | AC-07 |
| C2 | mor-test 没有 PSP Client，引用阶段 5 本机证据（`verification/local-e2e/stage5-report.md`：PSP 9 个角色绑定）（D-6） | AC-06、11（C2） |
| AC-13 | 只读：五个库 PLATFORM 来源 0 行，risk 目录行 2 | AC-13 |
| AC-03 / 05 / 10 的其余矩阵 | 由阶段 4 auto 测试与阶段 5 本机联调覆盖，mor-test 不重复 | — |

**合成写入与清理：**
- S-2 / S-5 / S-6 / S-0 的订单保留为验收订单，在第 10 节记下 order_no；
- S-3（DEMO-CL-001）与 B-1（236087245667409921）撤销后重新登记并恢复原有效期，资格历史各多几条、决定来源变为 MANUAL，保留（审计）；
- B-1 的收单资质往返恢复为 PAYFAC、REFERRER 删除恢复，版本号会增大；
- 如需临时 checker 账号，验收后禁用并清空其角色（同 2026-10-20 演示种子做法）。

## 9. 决定

| 编号 | 内容 | 状态 |
|---|---|---|
| D-1 | 执行种子 21。卖方披露公开文本（AE001 Demo Operating Entity FZ-LLC / MORTEST-REG-AE001 / Demo Address, Dubai / support-ae001@example.invalid）**仅供测试，不是真实法律信息** | 已确认（用户 2026-10-25） |
| D-2 | PayFac Partner 合同与两个方案经运营 API 建立，方案版本由第二个账号批准；SQL 30 只在运营 API 不可用且用户另行同意时兜底 | 已确认（用户 2026-10-25） |
| D-3 | 补丁 23 只补 DEMO-CL-001；GAP-PROFILE（审核通过时资料定稿：清草稿指针、计算两个 hash）另立 client-master 需求 | 已确认（用户 2026-10-25） |
| D-4 | PARTNER_SHARE 与 PLATFORM_SERVICE_CHARGE 各两条价格：AE001 / USD / CARD_STANDARD（priority 1）与 APPLE_PAY_STANDARD（priority 2），费率 0。SQL 30 已同步追加 APPLE_PAY_STANDARD，离线复演通过（[sql30-apple-pay-rerun.txt](./evidence/rehearsal/sql30-apple-pay-rerun.txt)） | 已确认（用户 2026-10-25） |
| D-5 | 多语言只增不改（第 4 节）；仓库 `i18n/zh-CN/nav.json` 3 条侧栏中文另行修正 | 按建议（用户未提异议，阶段 7 授权时一并确认） |
| D-6 | AC-11 在 mor-test 只验 C1；C2 引用阶段 5 本机证据 | 按建议（用户未提异议，阶段 7 授权时一并确认） |
| D-7 | acquiring REAL 与 commercial 快照开关都只在 AC-11 窗口内打开（第 ⑩—⑪ 步），之后两者恢复 MOCK / false（第 ⑫ 步）；开窗前通知其他会话（R-3：REAL 是全局开关，加上 GAP-PROFILE，其他 Client 在窗口内都无法建单），关窗后再通知 | 已确认（用户 2026-10-25） |

**对 D-7 中 commercial 开关的看法：**
- 建议窗口后关闭。目前没有下游读 `effective_relationship_model_version`：Payment 接收快照在二期，billing 仍按旧字段。所以开着不改变行为，但会让新订单冻结“生效版本 1”。二期接入后，这些订单会被当成按快照计费的订单。
- 关闭后，冻结版本 1 的只有 S-5 的验收订单。
- 应在二期需求就绪时再统一打开。

## 10. 执行记录（阶段 7，2026-10-25，主会话）

备份：`~/Code-MoR-backups/20261025T133812Z-relsnap-mor-test/`（六个库 13:38Z，`SHA256SUMS`；改写时刻 `rewrite-times.txt`；替换前环境变量键名 `keys-before/`）。

| 步 | 时间（UTC） | 结果 |
|---|---|---|
| ① | 13:38 | 7 个运行镜像 = 回滚列；备份完成；precheck 全部等于盘点值（0 处不符）。无其他活跃会话需要通知 |
| ② | 13:38—13:40 | 10 DDL；compliance → `ca158545…` healthy、rc=0、0 ERROR、键名不变；11 回填：37 条 APPROVED / NOT_REQUIRED、37 条 BACKFILL 状态变更 |
| ③ | 13:40 | 20 / 21 / 22 / 23 执行；postcheck `cm.*` 全部等于期望（`payfac_partner_party_eq_ae001=1`、PLATFORM 来源 0、AE001 根版本 2、卖方披露 1、Affiliation 7、签约方资料定稿 2、DEMO-CL-001 定稿 1） |
| ④ | 13:41 | commercial → `e5c1ee6b…`、client-master → `eeed4e63…`，均 healthy、0 ERROR；commercial 只多快照开关键 |
| ⑤ | 13:42 | 40 → 41（改写时刻 13:42:35.205486）→ acquiring `e0a1f618…`（MOCK）healthy、0 ERROR；postcheck `acq.*` 0 / 46 / 0 / 0 / 0；S-0 PASS（LP 107） |
| ⑥ | 13:43 | 50（改写时刻 13:43:40.143889）→ risk-decision `288b0ff6…` healthy、0 ERROR；postcheck `risk.*` 0 / 46 / 2；S-0 再走一次 PASS |
| ⑦ | 13:44 | ops-bff `feefe73f…`、ops-web `4f44725e…` healthy、0 ERROR；入口 200，返回的 index.html 与候选镜像逐字节相同 |
| ⑧ | 13:45—13:48 | 60 注册（休眠）：postcheck 6/6/6/11/38/17/66，Gateway 574 条、无 `CONTROL_PLANE_UNAVAILABLE`。语言包：`rs_bundles.py --apply` 首次在只读核对阶段因 `mysql -B` 转义导致 JSON 解析失败（未写入），修正后重跑 73/73 通过，发布 #8 READY（8813 key、46 个语言包），侧栏 3 条中文不变。61 激活：Gateway 585 条、无错误，角色版本 49 → 50 |
| ⑨ | 13:48—14:09 | 浏览器验收第一轮：28 PASS、2 FAIL（Q4，脚本预期错误：个人 Client 槽位只应有 Individual KYC，已修正），中文 P3 / Z1 前脚本退出、未执行收尾；验收 Client 236087245667409921 资格有效期已手工还原（validFrom 2026-10-14T11:26:38.909478Z，来源变为 MANUAL）。AE001 / PayFac 资格经页面正式登记。PayFac 合同 `payfac_agreement.py --apply`：合同 240113831739723776 ACTIVE（14:06:38Z），PARTNER_SHARE 240113834742845440 / PLATFORM_SERVICE_CHARGE 240113839838924800 ACTIVE（14:07:25Z），两条 0 费率价格；临时 checker 账号 240113837200719872 已 DISABLED、角色 240113836181504000 权限已 REVOKED（首次运行把 204 判为失败，修正后续跑完成清理）。preflight PASS |
| ⑩ | 14:09—14:10 | acquiring REAL（`-f 候选 -f 窗口`）healthy、0 ERROR。S-1 PASS（C1 八个角色、THROUGH_CUSTODIAN、PayFac 两版本、PLATFORM = 880000000701）；S-2 PASS（订单 ord_3LBpeD5DZJUdNEnotmlfVWgrNqKDir，绑定 8 行、生效版本 0、快照版本 1、卖方披露）；S-3 PASS（无 Affiliation Client 与撤销后 DEMO-CL-001 均 422 CHECKOUT_NOT_ELIGIBLE、不落单，资格已按原有效期重登）。S-4（APPLE_PAY 实付）未执行（可选） |
| ⑪ | 14:11 | commercial 快照开关 true；S-5 PASS（订单 ord_3LBpluoyPiLqo4n8Xtq0d54Jwgc3ET 生效版本 1） |
| ⑫ | 14:12 | commercial false、acquiring MOCK，均 healthy、0 ERROR；S-6 PASS；AC-13 PASS |
| ⑬ | 14:13—14:35 | 浏览器验收第二轮（Q4 预期已修正）：31 PASS、1 FAIL——Z1 只因验收 Client 的业务名称“Accept Tester Client”是英文（数据，不是界面文案），无原始 key；中文 P3 PASS；收尾正常，最终状态：验收 Client APPROVED / ELIGIBLE、validFrom 原值，AE001 PAYFAC，PayFac 只有 OE_PROVIDER。7 个服务终态：候选镜像、healthy、acquiring MOCK、快照开关 false。无其他活跃会话需要通知 |

**遗留：**
- S-4（APPLE_PAY 实付后 billing 按 PARTNER 计费）未在 mor-test 执行（可选项，需渠道实付）。
- GAP-PROFILE 另立 client-master 需求；在此之前 mor-test 保持 MOCK。
- 验收 Client 236087245667409921 的 INDIVIDUAL_KYC 资格来源由 REVIEWER 变为 MANUAL（还原有效期只能重新登记）。
- 主 compose 的 compliance 仍指 `:development` 旧镜像，重建须带 `compose-candidate.yaml`。
- 仓库 `operations-console-web/i18n/zh-CN/nav.json` 的 3 条 `nav.sidebar.*` 仍为英文（D-5，另行修正）。


## 11. 需要通知的会话

近期发布过 commercial / client-master / acquiring / ops-bff / ops-web 的会话：
- code-mor-db（多语言）；
- code-mor-78（billing / acquiring 基线）；
- 文件上传入口会话（ops-bff / ops-web 基线）；
- 以及 REAL 窗口期间在 mor-test 建单的会话（R-3）。

具体会话名以阶段 7 时 `ListAgents` 为准。

## 12. 阶段 7 授权清单（草案）

1. **替换 mor-test 镜像 7 个**，顺序与 ID 见第 1、6 节。每个服务约 1 分钟不可用；commercial 与 acquiring 在 AC-11 窗口内各多重启两次。
2. **mor-test 写 SQL：** 先备份，再执行第 2 节 10、11、20、21、22、23、40、41、50、60、61；必要时执行回滚 62、69、80—89。
3. **经运营接口写数据：** PayFac Partner 合同与两个方案、两条资格登记、多语言命名空间与导入、发布 #8；验收中的资格撤销 / 重登、收单资质与角色往返。
4. **验收写入：** 4—5 笔验收订单（保留）；可能的临时 checker 账号（用后禁用）。
5. **通知**第 11 节所列会话。
6. **不包括：** 提交、推送、改 `:development`、改 Gateway / Nginx / 主 compose、REAL 常开、快照开关常开。

