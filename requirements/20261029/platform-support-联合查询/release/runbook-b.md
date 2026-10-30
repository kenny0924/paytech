# 联合查询发布 b（technique 8.7 已加载对象跳过）· mor-test 运行卡

> 2026-10-30 准备（只读核对 + 本机构建）；执行由主会话。**无 SQL、无注册块、无 XXL、无演示数据登记变更**（D-b1 为可选的运营 API 状态切换）。镜像钉 sha256，tag `jq-20261030b` 仅供辨认。

## 1. 镜像

| 服务 | 现网（发布 a = 回滚目标） | 候选 b |
|---|---|---|
| platform-support-service | `sha256:9444b483a6353960ad8d6f5e3f69c14667b5b1570616ea015ea356c4a4111298`（JAR `96d24bed`） | `sha256:c82b6f3404b5e1d920d7f4fc0d776e0f705079732443856a37dbf32a9b794a23`（JAR `a37497ae`） |
| operations-console-bff | `sha256:34ce01c1d775276287cf3c12c6133d4f08b6a5fa4737d0f00c76ddfbe74983a8`（JAR `ac050134`） | `sha256:2452aaa79b35a73d707d11934eb42b308d94758b5d4d3269c5178ed6b39ef6d3`（JAR `a8447c75`） |
| operations-console-web | `sha256:da81d4b06dd20b3b53bd77abd0fc68bdd6d8686b3e022634fad99efcd8f94656` | `sha256:d236f1d53e219418515926dbe872d92e3f33278b187ae356c4a62b4d72de2dd4`（jq-web，dist 273） |

候选 b 相对 a：Platform 只有 J3 `loadedSubjects` / `alreadyLoaded` 相关 22 个条目（+4 / ~18），ops-bff 8 个（+4 / ~4）（`evidence/jar-candidate-b-vs-a.txt`）；镜像内 JAR = 构建产物，health-probe 与元数据 = 现网（`evidence/image-verify-b.txt`）；6 个 b 覆盖文件渲染 env 与现网逐键逐值相同（`evidence/env-check-b-20261030.txt`）。

## 2. 新旧兼容（决定顺序）

| 组合 | 结果 | 依据 |
|---|---|---|
| 旧 ops-bff（a）+ 新 Platform（b） | **可用**，与发布 a 行为相同：旧 BFF 不发 `loadedSubjects`，Platform 不跳过；响应多出的 `alreadyLoaded` 被旧 BFF 的 HTTP 客户端忽略 | 数据查询代理未开严格 JSON（`strictJsonResponseBytes=0`），默认 `JacksonJsonHttpMessageConverter` 不拒未知字段；用现网 a BFF JAR 的类实测反序列化含 `alreadyLoaded` 的响应成功 |
| 新 ops-bff（b）+ 旧 Platform（a） | **可用**，与发布 a 行为相同：BFF 转发的 `loadedSubjects`（或 null）被旧 Platform 忽略，响应无 `alreadyLoaded`（BFF 模型为 null；新页面按 `?? []` 处理） | Platform 数据查询请求体不走严格解析器（只有配置管理类型严格）；在 platform-test 用真实 MVC 发送带未知属性的 J3 请求得到 200 |
| **新 ops-web + 旧 ops-bff（a）** | **不可用**：新页面在 depth ≥ 1 发送 `loadedSubjects`，旧 BFF 的数据查询严格请求解析拒绝未知属性 → 400，联合查询第 2 层起失败 | BFF `DataQueryRequestConfiguration` 启用 `FAIL_ON_UNKNOWN_PROPERTIES` |
| 新 ops-web + 新 ops-bff + 旧 Platform | 可用，不跳过（同发布 a） | 同第 2 行 |

**推荐顺序：ops-bff → Platform → ops-web**（ops-web 必须最后；Platform 与 ops-bff 先后都安全，先换 ops-bff 让 Platform 的 60 秒重启落在已就绪的 BFF 之后）。中间状态都等同发布 a，没有功能中断，只有各自重启窗口（ops-bff 约 30 秒、Platform 约 60 秒）。**回滚必须先回 ops-web**，否则新页面 + 旧 BFF 会 400。

## 3. 步骤

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
R=/Users/kenny/Desktop/Workspace/Code-MoR-jq-rel/.jq-notes/release
up()  { ./test-environment/compose.sh -f "$R/compose-candidate-b.$1.yaml" up -d --no-deps --no-build --pull never --wait --wait-timeout 180 "$1"; }
upr() { ./test-environment/compose.sh -f "$R/compose-rollback-b.$1.yaml"  up -d --no-deps --no-build --pull never --wait --wait-timeout 180 "$1"; }
img() { docker inspect "mor-test-$1-1" --format '{{.Image}}'; }
```

| 步 | 内容 | 预期 / 失败即停 |
|---|---|---|
| ① | 通知并行会话（多语言发布变更记录、税务等）：ops-bff 约 30 秒、Platform 约 60 秒、ops-web 数秒；operations-console #26 | — |
| ② | `for s in platform-support-service operations-console-bff operations-console-web; do echo "$s $(img $s)"; done` | 三个 = 第 1 节“现网”列；不符即停（有人替换过，需重做基线） |
| ③ | `$R/scripts/source_drift_check_b.sh`；`SERVICES="platform-support-service operations-console-bff operations-console-web" $R/scripts/env_check_b.sh` | `all 241 files unchanged (b); main = 6eaf1bd3`；6 行 `missing=[] extra=[] valuediff=[]` |
| ④ | `up operations-console-bff` | `2452aaa7…` healthy；日志无 ERROR；联合查询页面（旧页面）仍可逐层展开 |
| ⑤ | `T=$(date -u +%FT%TZ); up platform-support-service` | `c82b6f34…` healthy；日志无 ERROR；`docker logs --since $T mor-test-api-gateway-1 2>&1 \| grep -iE 'snapshot\|CONTROL_PLANE\|routeCount' \| tail -3` 快照刷新恢复、routeCount 641；运营控制台可登录 |
| ⑥ | `up operations-console-web` | `d236f1d5…` healthy；文件数 273（`docker exec mor-test-operations-console-web-1 sh -c 'find /usr/share/nginx/operations-console -type f \| wc -l'`） |
| ⑦ | 语言包 #26：计划 `python3 -I $R/jq_bundles_b.py`，发布同一命令加 `--apply` | 计划：当前 **#25**、新增 7（只在 data-query）、其余 0、zh-CN 导入 7、不新建命名空间。当前号不是 #25 或出现 data-query 以外的变化即停。发布后 #26 READY 且为当前；Gateway 504 时先只读查 `t_i18n_release` |
| ⑧ | （可选，D-b1）经运营 API 把 `JQ_BILLING_FACT_BY_ATTEMPT` 改为 INACTIVE | 否则 AC-13 的“计费事实只出现一次”不成立（见第 4 节） |
| ⑨ | 验收：`E2E_TARGET=test python3 -I $R/acceptance/jq_api_b.py`（先跑发布 a 的 `jq_api.py`；若已做 ⑧，加 `--skip-a`，因为 a 的预期含该模板） | 全部 PASS；浏览器走查由 jq-web 执行 |
| ⑩ | 更新 delivery.md、CURRENT_IMPLEMENTATION；通知 ① 的会话 | — |

## 4. 验收预期（mor-test 只读模拟，`precheck/demo-chains-mortest-b-20261030.out`）

| 入口 | 执行数 a → b | 跳过（alreadyLoaded） |
|---|---|---|
| 交易订单号 `ord_3LOyZii4…` | 24 → **16**（⑧ 后 15） | 订单、收银台会话、计费事实、记账批次、会计凭证的“按自身 ID 查”，以及账户分录、凭证分录、渠道对账单明细（见 D-b2） |
| 支付意图 ID `239094951181373440` | 16 → 13 | 支付尝试、支付动作、渠道交易引用按 ID |
| 退款单号 `rfd_3L5B25y5…` | 9 → 7 | 退款申请按 ID、收银台会话按 ID |
| 渠道交易号 `T2026102218153708006502` | 19 → 17 | 渠道交易引用按 ID、支付动作按 ID |
| 会计凭证 ID `241801561934753792`（上游） | 23 → 21 | 渠道对账单明细、收银台会话按 ID；记账批次 / 计费事实 / 订单按 ID 照常执行 |

AC-13 在订单号入口：订单、会话、记账批次、会计凭证各只在一个节点；**计费事实出现两次**（按订单、按支付尝试——后者不是“按自身 ID 查”，按规则不跳过；触发值来自计费事实里合成的支付尝试引用）。⑧ 后为一次；支付意图 / 支付尝试在这条链路上没有数据（计费引用是合成 ID）。

## 5. 回滚（逆序）

| 顺序 | 动作 |
|---|---|
| R1 | `upr operations-console-web`（回 `da81d4b0`，**必须先于 R2**） |
| R2 | `upr platform-support-service`（回 `9444b483`） |
| R3 | `upr operations-console-bff`（回 `34ce01c1`） |
| R4 | 语言包不回退（旧页面不引用 7 个新 key）；⑧ 若已做，可经运营 API 改回 ACTIVE |

R2、R3 先后不限（第 2 节）；只回 ops-web 也可行（页面回到发布 a，服务端照常）。
