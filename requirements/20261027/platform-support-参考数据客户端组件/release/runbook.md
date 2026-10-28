# 参考数据客户端组件 · mor-test 发布运行卡

> 状态：**阶段 6 评审稿（rel-dev，2026-10-28）**。只排步骤，不授权任何动作；所有改变环境的步骤由主会话执行，并须用户对阶段 7 明确授权。
> 依据：technique.md 第 5、14 节；现网基线 [baseline.md](./baseline.md)；构建 [rel-build-report.md](./rel-build-report.md)。本需求**无 SQL、无注册 DML、无语言包**。
> 镜像钉 sha256，不加 `--remove-orphans`，一次只换一个服务。

## 0. 前提与重建

**协调结论（主会话 2026-10-27）**：card-BIN 已于 20:19Z 在 mor-test 发布 `binlist-20261027a`（Platform `186347bc`、card `380064fe`、ops-bff `25f6a869`、ops-web `aec066e9`；Gateway 块 140，613 条路由；ops 语言包 #18）。**它的提交落到 main 后，我们在该提交上 rebase 重建全部 6 个服务**；国家包法务修订在我们之后发布，不影响本次。本目录现有候选（基于 `5e1f16ab`）只是演练：ledger、billing、client-master 的候选本身可用，Platform、card、ops-bff 的候选会回退 card-BIN 的线上工作，**不得使用**。

| # | 事项 | 状态 |
|---|---|---|
| P-1 | 拿到 card-BIN 的提交号 `<CB>`（main 上） | 等通知 |
| P-2 | 按下方"重建"整段执行：新基线 = `<CB>`，新发布 = `<CB>` + 本需求补丁；6 个镜像 tag `refclient-20261028b`；回填 `compose-candidate.<服务>.yaml` 的 `image:` | 待 P-1 |
| P-3 | 基线核对（可重跑）：`scripts/baseline_check.sh <新基线工作树> <输出目录>`。重建后 Platform、card、ops-bff 的"已知差异"应为 0（现网 = card-BIN 提交）；任一服务"未知差异" > 0 即现网又被别人改过，停下判断 | 待 P-2；演练结果见 baseline.md |
| P-4 | 代码冻结：`Code-MoR-refclient` 指纹仍为 `a2ea2734e76893e9`（rel-build-report 第 1 节命令）；变了就重新导出补丁 | 执行当天 |
| P-5 | 回滚目标**不写死**：第 2 节 ⓪ 用 `scripts/gen_rollback.sh` 按部署前实际运行的镜像生成 6 个回滚文件，再跑 `scripts/env_check.sh`（12 行全部 missing / extra / valuediff 为空） | 执行当天 |

**重建**（整段可复用）：

```bash
W=/Users/kenny/Desktop/Workspace; REL=$W/Code-MoR/requirements/20261027/platform-support-参考数据客户端组件/release; CB=<card-BIN 提交号>
git -C $W/Code-MoR worktree add --detach $W/Code-MoR-refclient-base2 $CB
git -C $W/Code-MoR worktree add --detach $W/Code-MoR-refclient-rel2  $CB
$REL/scripts/export_patch.sh $W/Code-MoR-refclient 5e1f16ab $REL/refclient-on-5e1f16ab.patch   # 先重新导出（防代码有变）
(cd $W/Code-MoR-refclient-rel2 && git apply --3way $REL/refclient-on-5e1f16ab.patch)            # 冲突即停，交回研发
for t in base2 rel2; do mkdir -p $W/Code-MoR-refclient-$t/paytech/.mvn; cp $W/Code-MoR-refclient/paytech/.mvn/maven.config $W/Code-MoR-refclient-$t/paytech/.mvn/; done
$REL/scripts/build_apps.sh $W/Code-MoR-refclient-base2 && $REL/scripts/build_apps.sh $W/Code-MoR-refclient-rel2
ALLOW=/dev/null $REL/scripts/baseline_check.sh $W/Code-MoR-refclient-base2 ~/Code-MoR-backups/refclient-baseline-$CB
#   期望：Platform、card、ops-bff、client-master 未知差异 0；ledger / billing 若仍是 10-23 镜像，公共库差异会作为"未知"列出——
#   逐条确认是 <CB> 中更新的公共库（git log），再登记进 scripts/expected-diffs.tsv 后重跑（不带 ALLOW）
# 镜像：rel-build-report 第 2 节命令（上下文 = rel2 的 6 个 JAR + health-probe），tag refclient-20261028b；
#   docker create + docker cp 核对镜像内 JAR 与构建产物逐字节相同；把 sha256 写回 compose-candidate.<服务>.yaml；scripts/env_check.sh
```

补丁 `refclient-on-5e1f16ab.patch`：201 个文件，应用到干净 `5e1f16ab` 后与发布快照逐文件相同（rel-build-report 第 1 节）。

## 1. 约定

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
REL=/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261027/platform-support-参考数据客户端组件/release
up()  { ./test-environment/compose.sh -f "$REL/compose-candidate.$1.yaml" up -d --no-deps --no-build --pull never --wait --wait-timeout 180 "$1"; }
upr() { ./test-environment/compose.sh -f "$REL/compose-rollback.$1.yaml"  up -d --no-deps --no-build --pull never --wait --wait-timeout 180 "$1"; }
img() { docker inspect "mor-test-$1-1" --format '{{.Image}} {{.State.Health.Status}}'; }
ACC="python3 -I $REL/acceptance/refclient_mortest.py"
T0=$(date -u +%Y-%m-%dT%H:%M:%SZ)
```

健康检查：容器健康检查探 readiness；新版本 readiness 组含 `referenceData`，**`up --wait` 成功 = 已开启的种类全部至少成功加载一次**（未加载为 OUT_OF_SERVICE，`--wait` 180 秒超时失败）。

## 2. 发布顺序

原则：消费方之间互不依赖；**全部消费方上线并验收后，最后发布删除旧接口的 Platform**。现网 Platform（binlist）已提供 `/reference-data/*`，消费方先发没有前置。**我们的 card 必须先于我们的 Platform**：现网 binlist card 仍调用 `/country-regions`，Platform 先发它的 BIN 导入就会失败。

| 步 | 内容 | 命令 | 通过条件 | 预计 |
|---|---|---|---|---|
| ⓪ | 回滚列与基线 | `$REL/scripts/gen_rollback.sh` → `$REL/scripts/env_check.sh` → P-3 | 回滚文件 = 此刻现网（追加到 `rollback-targets.tsv`；按现状期望 Platform `186347bc…`、card `380064fe…`、ops-bff `25f6a869…`、ledger `3819ff28…`、billing `53450948…`、client-master `80257737…`；不一致说明有人又发布过，以生成结果为准并先查清）；env 12 / 12 相同；未知差异 0 | 3 分钟 |
| ① | 发布前证据 | `$ACC capture-finance ~/Code-MoR-backups/refclient-finance-before`；`$ACC probe --since $T0 --expect-old-endpoints` | 财务下拉 4 种 200、非法 status 400；旧接口 200 | 2 分钟 |
| ② | ledger | `up ledger-service` | 候选镜像 healthy（启动约 10 秒 + 首次加载） | 1 分钟 |
| ③ | billing | `up billing-service` | healthy | 1 分钟 |
| ④ | card（**先于 Platform**） | `up card-service` | healthy；Platform 必须健康（新增启动依赖：首次加载国家前不就绪） | 1 分钟 |
| ⑤ | client-master | `up client-master-service` | healthy（同上依赖 Platform） | 1 分钟 |
| ⑥ | ops-bff | `up operations-console-bff` → `$ACC compare-finance ~/Code-MoR-backups/refclient-finance-before` | healthy；财务下拉 4 种查询逐字节相同、非法 status 仍 400 | 2 分钟 |
| ⑦ | 消费方验收 | 第 3 节 3.1—3.5（`probe --expect-old-endpoints`） | 全部 PASS | 15 分钟 |
| ⑧ | **Platform（最后）** | 确认 ②—⑥ 全部是候选镜像（`img`）、⑦ 通过、**没有消费方回滚过**，再 `up platform-support-service` | healthy（约 15 秒启动）；运营登录 200——若 503 `No servers available for service: vault-service`，`docker restart mor-test-vault-service-1` 后重试；Gateway 日志 `Gateway Snapshot 刷新成功 … routeCount=613`（本需求不改路由） | 2 分钟 |
| ⑨ | 下线验收 | `$ACC probe --since $T0`；`$ACC compare-finance ~/Code-MoR-backups/refclient-finance-before` | 旧接口 404；四个消费方仍 UP、无刷新失败 WARN、无 ERROR；财务下拉仍逐字节相同 | 3 分钟 |

Platform 重启期间（⑧ 约 1 分钟），已就绪的消费方保留快照继续服务（阶段 5 AC-03）；ledger / billing 下一次刷新在 1 小时后。

## 3. 验收（补阶段 5 遗留）

### 3.1 各服务 referenceData 条数 / 健康明细（不改配置）

现网 `show-details: never`：`/actuator/health/referenceData` 等组件路径 404、整体健康只有状态（baseline 第 4 节实测）；客户端成功加载不写日志、无指标。用 `$ACC probe` 的组合证据（全部只读）：

1. readiness（只读 sidecar：目标镜像 JRE + `--network container:<目标>`）UP——组内含 `referenceData`，等价于"已开启的每一类都成功装入过"；
2. 自 `$T0` 起各服务日志 `Reference data refresh failed` = 0、ERROR = 0；
3. Platform 日志 `X-Caller-Service 明文头（DUAL 模式回退）: <调用方>` 出现 ledger-service、billing-service、card-service、client-master-service（实际访问过 Platform；card / client-master 经服务发现到达）；
4. Nacos：`platform-support-service`（mor-test / DEFAULT_GROUP）有健康实例（服务发现可解析）；
5. Platform 私网 `/reference-data/*` 条数（快照来源，含全部状态）：期望 CURRENCY 4、COUNTRY 249、SUBDIVISION 47、INDUSTRY 42、MCC 298（以当天为准）；
6. 3.2—3.5 的功能用例。

要直接看到条数、`loadedAt`、失败次数只能改配置（单独授权）：一次性覆盖加 `MANAGEMENT_ENDPOINT_HEALTH_SHOWDETAILS: always`（card 另加 `MANAGEMENT_ENDPOINT_HEALTH_SHOWCOMPONENTS: always`）重建该容器，读完用候选覆盖重建恢复（阶段 5 本机用法）。不作常规步骤。

### 3.2 ledger：余额读取 + 试算

`$ACC ledger`：经运营接口列 AE001 账簿 → 找到"账簿 × OPEN 期间"读科目余额（`LedgerQueryServiceImpl` 用客户端币种精度换算）→ **执行一次试算**（`TrialBalanceServiceImpl` 读币种精度；只写一条试算运行记录，不改分录与余额，运营日常可重复）。

不做：
- **真实过账 / 追平**：追平只处理已消费序号之后的待处理过账，现网已追平时为 0 批、不触及币种读取；阶段 5 的追平请求体也未验证（e2e-dev 说明）。造一笔真实过账要发布计费事实，会留下不可删除的分录。
- **"查不到币种 → 可重投"**：账簿只能为快照内的币种创建；要触发，必须向线上过账流注入一条币种不在快照里的 AccountPosted，会进入重投 / 死信并打乱流序号——mor-test 没有安全做法。证据沿用 auto 测试 `CurrencyRulePostingTest`（快照缺币种 → 可重试系统异常 → 消息重投），以及阶段 5 本机"JPY 建账簿 → 400 CURRENCY_NOT_ACTIVE"（同一快照读取）。

### 3.3 billing：精度与未登记币种

`$ACC billing`：与阶段 5 相同的 billing 私网请求（`verification/local-e2e/stage5-requests.json`，经接入 mor-test-backend 的一次性探针容器；Header `X-Gateway-Actor-Subject` = mor-test 运营管理员 `221546852372267008`，可用 `REFCLIENT_ACTOR_ID` 覆盖）：
- USD `1.005` → 400 `FACT_LINE_INVALID`；
- Platform 未登记的币种（JPY；脚本先读 `/reference-data/currencies`，JPY 已登记时顺延 CHF / GBP / CNY / KWD）→ 400 `BUSINESS_CHECK_FAILED`（与迁移前一致，不是 422）；
- USD `1.25` → 201 DRAFT → 立即作废 → 200 VOIDED（不审批、不发布、不产生分录）。
OE 与 Client 用合成编码（`ZZ9<两位>`、`9<run>`），不触及 AE001；留下一条 VOIDED 计费事实。

### 3.4 card：不做 BIN 上传

BIN 上传会留下无法删除的 INVALID 版本与上传文件，mor-test 不做（主会话 2026-10-27 决定）。card 的证据：
1. readiness UP（组含 `referenceData`，COUNTRY 已装入）——3.1 第 1 项；
2. Platform 日志出现调用方 `card-service`——3.1 第 3 项；
3. Nacos 可解析 `platform-support-service`——3.1 第 4 项（card 不配 base-url，只走服务发现）；
4. 阶段 5 本机导入证据：2 行 CSV（AE 合法、XX 非法）→ 版本 INVALID、`errorRows=1`、`issuer_country` `COUNTRY_REGION_MISSING`「发卡国 XX 未登记区域」（`verification/local-e2e/evidence/card-bin-check.txt`）。

### 3.5 client-master：提交与分类的非法编码

`$ACC client-master`（调用参考数据需求的 `refdata_mortest.py --only F,A,K`，已去掉全部改库步骤）：币种 / 行政区 / 国家不存在、行政区与国家不一致 → `FIELD_VALUE_NOT_IN_CATALOG`；带不存在国家正式提交 → 422 `SUBMISSION_INCOMPLETE` 且仍 DRAFT；分类只选大类 / 未知行业 / 未知 MCC / 第 2 行大类 → 422 `CLASSIFICATION_INVALID`；合法提交进入审核，细类 + 建议 MCC 保存成功。写入：注册验收 Client、建申请并提交、保存一个案件分类（与前两次 mor-test 验收同类）；结果文件写在参考数据需求的 `verification/`。停用编码用例不做（需改 Platform 库，且客户端缓存 1 小时）。

### 3.6 旧接口 404 与 ops-bff 财务下拉

- ①、⑦ `probe --expect-old-endpoints`：旧接口 200；⑨ `probe`：`/platform/v1/currency-specs`、`/platform/v1/country-regions` → 404，`/reference-data/*` 200。
- 财务下拉：① capture → ⑥、⑨ compare，4 种查询逐字节相同。

## 4. 回滚触发条件

| 步 | 触发 | 动作 |
|---|---|---|
| ②—⑥ | `up --wait` 超时（readiness 一直 OUT_OF_SERVICE）；`Reference data refresh failed` 持续；ERROR；对应 3.x 验收 FAIL；ops-bff 财务下拉字节不同 | `upr <该服务>`（此时 Platform 尚未发布，旧接口还在，旧镜像可直接恢复） |
| ⑧ | Platform 180 秒内不 healthy；重启 Vault 后登录仍失败；Gateway 持续 `CONTROL_PLANE_UNAVAILABLE`；任一消费方出现刷新失败或 ERROR | `upr platform-support-service`（恢复旧接口） |
| ⑨ | 旧接口仍 200（镜像不对）；任一消费方异常 | 同上 |

## 5. 回滚顺序（重点）

- **Platform 发布之后，任何消费方要回滚到旧镜像，必须先回滚 Platform**（`upr platform-support-service`，恢复 `/currency-specs`、`/country-regions`），再 `upr <消费方>`。旧 ledger / billing 启动时读旧接口（Platform 已下线则就绪探针 DOWN）、旧（binlist）card 的 BIN 导入调 `/country-regions`、旧 ops-bff 财务下拉读旧接口。
- 反过来，回滚 Platform 不需要先回滚消费方：新消费方只读 `/reference-data/*`，旧 Platform 同样提供。
- 全量回滚：`upr platform-support-service` → `upr operations-console-bff` → `upr client-master-service` → `upr card-service` → `upr billing-service` → `upr ledger-service`。回滚文件由 ⓪ 生成，指向部署前实际运行的镜像（`rollback-targets.tsv` 有记录）。
- 无数据回滚：本需求不改库。验收留下：billing 一条 VOIDED 合成事实、ledger 一条试算记录、client-master 验收 Client / 申请 / 案件分类。
