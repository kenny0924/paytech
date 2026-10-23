# operations：运营控制台多语言迁移 · mor-test 发布包

阶段 6（2026-10-23 19:20—19:55Z）。本阶段只做了：只读核对（`docker inspect`、临时容器从**镜像**取出 JAR / dist、`Config.Env` 只比较不输出值、`mor_platform` / `mor_commercial` 只读 SELECT、匿名只读 GET manifest 与公开桶、无头浏览器只读 fetch）；在新建的干净 worktree 构建 2 个镜像；离线用 Platform 的 ICU 校验器校验全部文案；写发布包与脚本并干跑。**没有停 / 起 / 替换 mor-test 任何容器，没有执行写 SQL，没有登录、没有调用写接口，没有提交，没有 `mvn install`，没有改 `:development` tag，没有 `docker cp` 进运行容器。** 阶段 7 须用户授权，由主会话按第 6 节执行，结果写第 10 节。当前实现状态以 [CURRENT_IMPLEMENTATION.md](../../../../agent-docs/CURRENT_IMPLEMENTATION.md) 为准。

**本次内容：**
- **换镜像（2 个）：** operations-console-bff、operations-console-web。其他服务（含 platform-support-service）不动。
- **数据（经真实运营接口，不写 SQL）：** `operations-console` 默认语言 `zh-CN` → `en`；新建 19 个命名空间；导入 8523 个英文 key 与 8523 条 zh-CN 翻译；发布第 3 版（42 个语言包）。
- **数据库 DDL / 注册 DML：** 无。**环境变量：** 不新增、不删除（第 3.3 节）。
- **不动：** Platform、Gateway、Nginx、MinIO、Nacos、主 compose 与其余容器。

## 1. 源码与镜像

### 1.1 构建源

| 项 | 内容 |
|---|---|
| 本需求源码 | 集成目录 `/Users/kenny/Desktop/Workspace/Code-MoR-opsi18n`（detached `9f210b5c`，index 已 `add -A`，工作区 = index，无未跟踪文件，只读）。`git diff --cached --binary 9f210b5c`：434 个文件（新增 51、修改 382、删除 1），补丁 SHA-256 `064f85856d35a4b1…`（[清单](./evidence/source-diff-9f210b5c.txt)）。范围：ops-web 397 个 `src/` 文件 + `i18n/` 22 + `scripts/`、`docs/`、`index.html`（`lang=en`、英文 description）；ops-bff 10（7 个生产类 + 3 个测试）；文档 2（决定、开发约定，不进镜像） |
| 构建 worktree | `/Users/kenny/Desktop/Workspace/Code-MoR-opsi18n-rel`（`git worktree add --detach … 9f210b5c`）。先在纯 `9f210b5c` 上构建 BFF 基线，再 `git apply --index --binary` 上述补丁；与集成目录 `diff -rq`（排除 `node_modules`、`dist`、`i18n-out`、`target`）ops-web 与 ops-bff 两个目录**完全相同** |
| ops-web 基线 | `/Users/kenny/Desktop/Workspace/Code-MoR-opsi18n-base`（`9f210b5c`，干净）`npm run build` |
| Java | JDK 25；`./mvnw -B -o -q -DskipTests -Dmaven.repo.local=<scratchpad>/m2 -Dmaven.repo.local.tail=$HOME/.m3/repository -pl operations-console-bff/paytech-operations-console-bff-app -am clean package`，没有 install。镜像用 `test-environment/java/Dockerfile`（与主工作区逐字节相同），`health-probe/` 从运行镜像经临时容器取出（与候选镜像内逐字节相同） |
| Web | `npm ci` → `tsc --noEmit` → `npm test`（**1481 / 1481**）→ `npm run build`（dist 214 个文件）→ `operations-console-web/Dockerfile`；`deploy/nginx.conf` 本需求未改，与现网逐字节相同。`npm run i18n:export-source` 输出复制到 [i18n-bundles/](./i18n-bundles/)（第 8 节） |
| 镜像核对 | BFF 镜像内 `/app/application.jar` = 构建产物（`3c7e6f64…`）、`health-probe/` = 运行镜像；ops-web 镜像内 `dist/` = 构建产物、`nginx.conf` = 现网；两个镜像 Entrypoint / Cmd / User / WorkingDir / Env / ExposedPorts / Healthcheck / StopSignal 与对应运行镜像相同（[precheck_readonly.py](./acceptance/precheck_readonly.py) 复核） |

### 1.2 基线证明（不加本需求，与运行容器比较）

比较方法同前置需求（[evidence/jarcmp.py](./evidence/jarcmp.py)）：`BOOT-INF/classes` 与嵌套 `paytech-*` JAR 逐条目 CRC，第三方 JAR 整包 SHA-256；排除 MANIFEST、`META-INF/maven`、build-info、classpath / layers 索引与目录项。运行 JAR 从运行镜像经临时容器取出，SHA-256 与 `docker exec … sha256sum /app/application.jar` 一致（`d52e541b…`）。Web 比较 `dist/` 与 `nginx.conf` 逐字节。

| 容器 | 运行镜像（起始时间） | `9f210b5c` 基线构建 | 结论 |
|---|---|---|---|
| operations-console-bff | `e4d913e0948b…`（i18n-20261023a，08:34:52Z 起，JAR `d52e541baf7a163b`） | 2,597 项：**现网有、`9f210b5c` 没有 = 0**；只差 `paytech-ledger-api` 2 项（`LedgerSetupQueryApi`、`LedgerSetupErrorCode`，[明细](./evidence/jar-diff-live-vs-9f210b5c-operations-console-bff.txt)），来自已提交的 `98210e0b`（billing 支付事实自动登记，现网 BFF 构建于其之前） | 现网 = `bfb17bc9` + 多语言管理（已提交 `ed361c26`）；`9f210b5c` 只多已提交的 ledger-api 只增改动 |
| operations-console-web | `9fb809dfef26…`（billb-f1-20261023，09:01:24Z 起） | `dist/` **215 个文件逐字节一致**，`nginx.conf` 一致 | 现网 = `9f210b5c`（`37173818` 计费事实抽屉、`ed361c26` 多语言管理、`9f210b5c` 数据查询体验均已在 main），**没有未提交的现网改动需要带上** |

候选 BFF 会顺带上线的已提交改动（只报告，不取舍）：`paytech-ledger-api` 新增 `getFinanceEntityByParty`（BFF 不调用）与错误码 `FINANCE_ENTITY_NOT_FOUND`（`FinanceClientConfiguration` 按枚举整体登记，多登记一个 404 编码，现有 400 路径不变）。

### 1.3 候选镜像（tag `opsi18n-20261023a`）与差异

| 服务 | 候选镜像 | JAR / dist | 相对基线（= 现网 + 已提交改动） |
|---|---|---|---|
| operations-console-bff | `sha256:3751638e5f34273844582b53ad0b5b2401a057fe8001bfc63d15241a8f8d487f` | JAR `3c7e6f647261115c` | **+2、~12，全部在本 BFF `BOOT-INF/classes`，库与第三方 0 改动**（[明细](./evidence/jar-diff-candidate-operations-console-bff.txt)），恰为三处：① 权限树 `code`——`RolePermissionTree`、`RoleModels$PermissionNode / PermissionResource / GrantablePermission`、`PlatformOperationsClientImpl`（透传 `resourceCode`）；② 审计去中文标签——`AuditModels$AuditChange / AuditProperty`、`AuditEventDetailsProcessor`（含 `$ChangeValues`）；③ 试算说明结构化——`CommercialPricePreviewModels`（`$ComponentIllustration`，新增 `$IllustrationNote`、`$IllustrationNoteCode`）、`CommercialFeeIllustrator`。`application.yml` 未改 |
| operations-console-web | `sha256:d228e3c883fdc1524ddb63c2d10f291100fc72a8bf25c191f1e03074c7a33433` | dist 214 个文件 | 全量多语言迁移：21 个命名空间 8523 key（`src/i18n/en/*.json` 内置英文）、根部 Gate 与本地缓存、页头 / 登录页语言按钮、antd / dayjs 跟随、菜单面包屑按 `nav.resource.<CODE>`、全 `src` 禁中文守卫；`index.html` `lang=en`。依赖不变（`package.json` / lock 未改） |

**对其他消费方：** BFF 三处只改 BFF ↔ ops-web 内部合同，没有其他调用方。

## 2. 兼容性与执行顺序

| 组合 / 时点 | 结果 |
|---|---|
| 第 ② 步语言包数据 + **现网旧 ops-web** | **无影响。** 旧 ops-web 只在多语言三页启动运行时、只加载 `i18n-admin`、`enum`（`src/i18n/index.ts` 固定默认 `zh-CN`，manifest 的 `defaultLocale` 只在所选语言未发布时作回退）。线上这两个命名空间的 409 个 key 的英文基准与中文翻译**与本次导入文件逐条相同**（`precheck_readonly.py` 用只读 SELECT 逐条比较，0 处不同）：F9 的 174 处英文用词统一全部在新命名空间，未改动 `i18n-admin`、`enum`，所以新发布中旧页面会读到的文案 0 变化（只是语言包地址换成第 3 版）。默认语言改为 `en` 对旧前端不生效 |
| 第 ② 步之后 key 集合 | `operations-console` 由 409 key 变为 8523 key；“翻译”页会列出全部 key（运营数据，不影响旧页面） |
| 新 ops-bff + 旧 ops-web（第 ③—④ 步之间） | 三处内部合同不兼容：旧前端**审计详情**要求 `label`，打开详情会报 INVALID_RESPONSE；**角色权限树**“其他权限”分组显示编码 `OTHER_PERMISSIONS`；**价格试算**示例费用不显示说明（旧字段 `note` 缺省为空）。其余页面正常。第 ③、④ 步连续执行，窗口约 1 分钟 |
| 新 ops-web + 旧 ops-bff | 角色权限树缺 `code` 判为 INVALID_RESPONSE；审计字段名显示原 key；试算说明缺失。**所以 BFF 先换、Web 后换；回滚反之（先 Web 后 BFF）** |
| 新 ops-web + 只有第 2 版发布（第 ② 步未完成） | 19 个新命名空间在 manifest 中不存在，选中文的用户这些页面回退内置英文。**所以第 ② 步必须在第 ④ 步之前 READY**（technique 3.7） |
| 新 ops-web + 第 3 版发布 | 默认英文；选中文后全部页面中文（与迁移前逐字一致，第 9 节 AC-01） |
| Platform 默认语言 `en` | 新 ops-web 本身固定默认 `en`（不读浏览器语言）；manifest `defaultLocale` 实时取应用行，改为 `en` 使 Platform 数据与需求一致 |

**顺序：** ① 通知 → ② 语言包数据（窗口外即可，对现网无影响）→ ③ ops-bff → ④ ops-web → ⑤ 验收 → ⑥ 通知。

**不可用窗口：** ops-bff 重启约 45 秒（运营控制台全部接口）；ops-web 约 10 秒；③—④ 之间约 1 分钟内上表三处旧页面受影响。另有**语言感知变化**：默认语言改为英文后，没有本地选择的用户首次打开为英文（需求本意，需在通知中说明，选择一次后记在本机）。

## 3. 只读核对（阶段 6 已做）

### 3.1 一键预检

[`acceptance/precheck_readonly.py --browser`](./acceptance/precheck_readonly.py) 最近一次于 19:44Z 执行：**PASS 34，FAIL 0**（[输出](./acceptance/precheck-readonly-20261023T1944Z.out)）。

| 组 | 结果 |
|---|---|
| 容器 | 2 个 `.Image` = [compose-rollback.yaml](./compose-rollback.yaml)、healthy、重启 0 |
| 候选 | 2 个镜像存在、= 候选覆盖文件、配置与运行镜像相同、BFF JAR SHA-256 正确 |
| 环境 | 候选 / 回滚覆盖文件渲染后与运行容器逐键、逐值相同（第 3.3 节） |
| Platform（只读 SELECT） | `operations-console`（`900000000000135011`）ENABLED；**默认语言 `zh-CN`（版本 1，需改为 `en`）**；必须语言 `["zh-CN"]`、覆盖率阈值 100；`en`、`zh-CN` ENABLED；命名空间只有 `enum`、`i18n-admin`（19 个新编码未占用）；发布 #1、#2 均 READY、409 key，**当前 #2**，无进行中的发布；线上 409 个 key 的英文与中文 = 本包输入 |
| manifest | `?locale=en` / `zh-CN` 匿名 200、#2、命名空间 `enum`、`i18n-admin`、`Cache-Control: no-store` |
| 公开桶 | 当前语言包对 `http://localhost:28080` 200 + CORS 允许；其他 Origin 无允许头；TLS 只信任 test CA |
| 浏览器 | 无头 Chromium 严格证书从运营控制台 fetch 公开桶语言包 200 |
| 输入 | `i18n-bundles` 两个文件 SHA256SUMS 一致 |

第 ② 步之后用 `--after-bundles` 复跑（期望默认 `en`、21 个命名空间、当前发布 8523 key）。

### 3.2 文案离线校验

用 Platform 候选源码的 `IcuMessageValidator`（icu4j 79.1）逐条校验两个输入文件：8523 个英文基准 `validateSource`、8523 条中文 `validateTranslation`（语法、`other` 分支、参数名集合与基准一致，即 Platform 导入时的同一校验）——**0 问题**（[evidence/icu-validate.txt](./evidence/icu-validate.txt)）。另由 `ops_bundles.py --dry-run` 核对 key 编码正则、所在命名空间前缀、文案长度、命名空间编码、zh-CN 覆盖 100% 与 2 MiB 上限。

### 3.3 环境变量

- 主 compose（`test-environment/compose.applications.yaml`）已含历次覆盖文件中这 2 个服务的全部键；候选 / 回滚文件只钉镜像。
- 扫描 91 个 `requirements/**/release/**/compose-*.yaml`：ops-bff 历史上出现过的键只有 `OPERATIONS_ACCOUNT_BASE_URL`、`OPERATIONS_BILLING_BASE_URL`、`OPERATIONS_LEDGER_BASE_URL`、`OPERATIONS_RECONCILIATION_BASE_URL`，渲染结果全部包含；ops-web 无。
- `compose.sh -f <文件> config --format json` 渲染后与运行容器 `Config.Env` 逐键、逐值比较（只输出键名与是否一致）：ops-bff 14 / 20、ops-web 0 / 7（compose 键 / 运行键），**候选与回滚都 0 缺、0 多、0 不同**；两者都没有挂载。
- 本需求不新增变量。

## 4. 发布物

| 文件 | 用途 |
|---|---|
| [compose-candidate.yaml](./compose-candidate.yaml) / [compose-rollback.yaml](./compose-rollback.yaml) | 钉 sha256 的候选 / 回滚覆盖文件（2 个服务） |
| [i18n-bundles/](./i18n-bundles/) | `operations-console.en.json`（646.9 KiB）、`operations-console.zh-CN.json`（600.6 KiB）与 `SHA256SUMS`；发布后的 manifest 快照写 `i18n-bundles/published/` |
| [acceptance/opsi18n_common.py](./acceptance/opsi18n_common.py) | 公共件：import 前例 `platform-support-多语言管理/release/acceptance/i18n7_common.py`（登录、Gateway 请求、上传、公开桶读取、只读 SQL），改指本包目录 |
| [acceptance/precheck_readonly.py](./acceptance/precheck_readonly.py) | 只读一键预检（第 3.1 节） |
| [acceptance/ops_bundles.py](./acceptance/ops_bundles.py) | 第 ② 步语言包数据（写数据） |
| [acceptance/stage7_api.py](./acceptance/stage7_api.py) | 接口验收（只读：GET + 只读 POST 查询“价格试算”） |
| [acceptance/stage7_browser.py](./acceptance/stage7_browser.py) | 浏览器验收（`released` 只读；`ac07` 写数据） |
| [acceptance/stage7_zh_compare.py](./acceptance/stage7_zh_compare.py) | AC-01 中文零变化复核（基线 Vite vs 发布后现网） |
| [evidence/](./evidence/) | JAR 差异明细（现网对基线、基线对候选）、源码差异清单、ICU 校验、比较脚本 |

干跑（阶段 6，未调用任何接口）：`ops_bundles.py --dry-run` 35/35；`stage7_api.py --dry-run` 3/3（试算候选 12 个，来自只读 SQL）；`stage7_browser.py --dry-run` 14/14（两个 phase）；`stage7_zh_compare.py --dry-run` 通过（70 个菜单页）。全部脚本 `py_compile` 通过；运行时不写 `__pycache__`。

## 5. 执行前提

1. 用户授权阶段 7（镜像替换；经接口写入语言包数据；AC-07 的临时发布与切回）。
2. 第 12 节各项已裁决。
3. 构建 worktree `/Users/kenny/Desktop/Workspace/Code-MoR-opsi18n-rel` 与 2 个候选镜像保留到发布完成（镜像只按 sha256 引用）。集成目录若在阶段 6 之后又有改动，本包作废，按第 1 节重建。

## 6. 执行步骤（阶段 7，主会话执行）

在主工作区根目录：

```
R="$PWD/requirements/20261023/operations-控制台多语言迁移/release"; A="$R/acceptance"
O="$R/compose-candidate.yaml"; RB="$R/compose-rollback.yaml"
up() { ./test-environment/compose.sh -f "$O" up -d --no-deps --no-build --pull never --wait --wait-timeout 180 "$@"; }
chk() { for s in "$@"; do docker inspect mor-test-$s-1 --format "$s {{.Image}} {{.State.Health.Status}} rc={{.RestartCount}}"; done; }
```

任一步不符合预期就停，按第 7 节回滚。

1. **① 协调与复核：** 通知会替换 ops-bff / ops-web 的会话（计费、工作流、数据查询、卡 BIN、文件存储等）本窗口；期间有人替换了这两个服务，本包作废并重做第 1 节。`python3 -I "$A/precheck_readonly.py" --browser` → `FAIL 0`。
2. **② 语言包数据（窗口外即可）：** 先 `python3 -I "$A/ops_bundles.py" --dry-run`（35/35），再 `python3 -I "$A/ops_bundles.py"`。预期：
   - ②a 默认语言 `zh-CN → en`（必须语言、阈值不变；原值写运行记录 `applicationBefore`）；
   - ②b 新建 19 个命名空间，命名空间 21 个齐全；
   - ②c key 导入预览：新增 8114、变更 0、**废弃 0**、不变 409 → 确认 → 再预览为全部不变；
   - ②d zh-CN 按命名空间 21 个文件导入：写入 8114、不变 409、空 0；
   - ②e 预检可发布、缺口 0（相对 #2 新增 8114 key）→ 发布 **#3** → READY 且为当前，语言包 42/42，全部在 `MINIO/mor-platform-public`；
   - ②f 匿名 manifest `en` / `zh-CN`：#3、默认 `en`、21 个命名空间；42 个语言包匿名 200、摘要 = hash、内容 = 输入文件、CORS 允许运营 Origin。
   任何 502 / 503 / 504 脚本都会等待并重新核对（重新预览 / 查发布记录），不会盲目重复写；可直接重跑。完成后 `python3 -I "$A/precheck_readonly.py" --after-bundles` → `FAIL 0`（此时“线上 i18n-admin / enum = 输入”仍应通过）。
   **对现网的核对：** 用现网旧 ops-web 打开“多语言管理 → 翻译”，中文界面不变（第 2 节）。
3. **③ ops-bff：** 记下开始时间 `T3=$(date -u +%FT%TZ)`；`up operations-console-bff`；`chk operations-console-bff` 为 `sha256:3751638e… healthy rc=0`；`docker logs --since "$T3" mor-test-operations-console-bff-1 2>&1 | grep -c ' ERROR '` 为 0；`docker exec mor-test-operations-console-bff-1 printenv | cut -d= -f1 | sort` 与替换前键名一致（20 个）。
4. **④ ops-web（紧接第 ③ 步）：** `up operations-console-web`；`chk operations-console-web` 为 `sha256:d228e3c8… healthy rc=0`；`curl -s http://localhost:28080/ | grep -o '<html lang="en">'` 有输出。提醒用户刷新已打开的运营控制台标签页。
5. **⑤ 验收：**
   - `python3 -I "$A/stage7_api.py" --since "$T3"`（第 9 节 A—C）；
   - `python3 -I "$A/stage7_browser.py"`（`released`，只读，B1—B8；截图 `acceptance/screens/`）；
   - `python3 -I "$A/stage7_zh_compare.py" capture --target base`（脚本自行在基线目录起停 Vite 18312）→ `capture --target live` → `diff`，人工判读 `zhcmp-stage7-diff.json`（只应剩数据 / 时间类差异与登录页语言按钮）；
   - AC-07（写数据）：`python3 -I "$A/stage7_browser.py" --phase ac07`：改 `auth.login.title` 中文 → 发布 #4 → 刷新可见 → 切回 #3 → 刷新恢复 → 译文改回原文 → 预检相对 #3 无差异。
6. **⑥ 记录与通知：** 填第 10 节；`delivery.md` 补环境操作记录；通知第 ① 步各会话新镜像 ID 与源码基线：今后 ops-bff / ops-web 候选须包含本需求（直到提交），新文案按开发约定第 10 节先加 en key 与 zh-CN 种子，再经多语言管理导入发布；告知用户默认语言已为英文、可在页头切换。

## 7. 回滚

`upr() { ./test-environment/compose.sh -f "$RB" up -d --no-deps --no-build --pull never --wait --wait-timeout 180 "$@"; }`

| 范围 | 做法 |
|---|---|
| ops-web | `upr operations-console-web`（先于 BFF）。旧前端恢复硬编码中文；在新前端选过语言的用户，本机 `i18n.locale` 会被旧前端的多语言三页沿用（选 English 的人这三页显示英文，可用 `?lang=zh-CN` 或清除本机记录） |
| ops-bff | ops-web 回滚后 `upr operations-console-bff`（旧 BFF 与旧前端配套；与新前端不兼容，见第 2 节） |
| 语言包发布 | **一般不回滚**：第 3 版对旧前端读到的 `i18n-admin`、`enum` 文案 0 变化。确需恢复时（且 ops-web 已回滚，否则新前端 19 个命名空间会回退英文）：`POST /api/operations/localization-admin/v1/applications/operations-console/current-release`，正文 `{"releaseNo": 2, "reason": "运营控制台多语言迁移回滚"}`（同前例 `stage7_acceptance.py` 的切回调用；或在“多语言管理 → 发布记录”选 #2 “切回此版本”） |
| 默认语言 | 在“多语言管理 → 语言与应用”把 `operations-console` 默认语言改回简体中文，或 `PUT …/applications/operations-console`（`expectedVersion` 取当前、`applicationName` / `requiredLocales` / `coverageThresholdPercent` 原样带回、`defaultLocale: "zh-CN"`）。原值见 `ops-bundles-*.json` 的 `applicationBefore` |
| 命名空间与 key | **建议保留**：Platform 没有“删除 key”，只有导入时废弃；19 个命名空间里有 key 时也不能删除。保留对旧前端无影响（旧前端不读），下次重新上线可直接复用，不需要重新导入。若一定要撤销，需要导入只含 `i18n-admin`、`enum` 的源文件把 8114 个 key 标为废弃，再删除空命名空间——不建议 |
| AC-07 中途失败 | 按 `I18N7_STATE/ac07-*.json`：若当前不是 `baseReleaseNo`，先切回；再把 `auth.login.title` 中文改回 `original`（翻译页逐条编辑或 `translations/batch`） |

## 8. 语言包输入

| 命名空间 | key | | 命名空间 | key | | 命名空间 | key |
|---|---|---|---|---|---|---|---|
| access | 422 | | data-query | 372 | | nav | 188 |
| auth | 42 | | enum（已有） | 8 | | notification | 278 |
| card | 141 | | file | 403 | | recon | 541 |
| channel | 1157 | | finance | 834 | | risk | 1132 |
| client-review | 163 | | gateway | 247 | | vault | 194 |
| commercial | 785 | | i18n-admin（已有） | 401 | | webhook | 147 |
| common | 42 | | | | | workflow | 989 |
| currency | 37 | | | | | **合计** | **8523** |

- 输入来自候选 worktree 的 `npm run i18n:export-source`：`operations-console.en.json` `6c26694ff242879e…`、`operations-console.zh-CN.json` `af160022a81ba10b…`。**比任务卡的 8513 多 10 个**：阶段 5 新增的 `access.enum.tenantAccessReason.*`（租户访问准入条件按编码翻译）。集成目录 `i18n-out/` 是 18:14Z 的旧导出（8513），已核对源码相同、只差这 10 个 key，以本包为准。
- 翻译导入按命名空间切成 21 个文件（每个 ≤ 85 KiB）：Platform 翻译导入逐行写入，8114 行一次导入可能超过 BFF → Platform 9 秒读取超时（Gateway 返回 504 而 Platform 仍会提交）；按命名空间导入每次 ≤ 1157 行。key 导入必须是全集（Platform 把文件视为应用完整 key 集合，缺的会被废弃），所以 key 导入是一个文件（批量插入）。
- 导入文件在 Platform 私有导入桶留下 22 个文件记录（1 个 KEY_IMPORT + 21 个 TRANSLATION_IMPORT），与前例做法相同，保留。

## 9. mor-test 验收

**接口（[`stage7_api.py`](./acceptance/stage7_api.py)，只读）。**

| 组 | 检查 |
|---|---|
| A 运行 | 2 个容器为候选镜像、healthy、重启 0；BFF 自第 ③ 步起 ERROR 0 |
| B manifest | `en` / `zh-CN` 两种请求：默认 `en`、语言 en / zh-CN、各 21 个命名空间；42 个语言包匿名 200、摘要 = hash、key 集合与内容 = 输入文件、CORS；记录语言包 `Cache-Control` 实际值 |
| C1 权限树 | `role-permission-options` 每个节点有非空 `code`；资源节点 code 多数能在 `nav.resource.*` 找到；“其他权限”分组（有未挂资源的权限时才有）`code = displayName = OTHER_PERMISSIONS`、无中文 |
| C2 审计 | 第一页前 10 条详情的 `changes` / `attributes` 只有 `key` 与值、无 `label` |
| C3 试算 | `POST /commercial-admin/v1/commercial-prices/simulate`（BFF 注明只读查询）：候选取自 `mor_commercial` 只读 SELECT 的 ACTIVE 条款价格（有上下限 / 阶梯 / 依赖其他费用的组件，金额 1.00 与 100000.00）；组件无 `note`；`notes[{code, params}]` 编码属于 9 个、params 为字符串；每个出现的编码在 `commercial.simulator.note.*` 有 en / zh-CN |

**浏览器（[`stage7_browser.py`](./acceptance/stage7_browser.py)，严格证书，只读阶段拦截一切非 GET 写请求）。** B1 新浏览器默认英文、语言按钮出现；B2 切到简体中文后整页刷新为中文、本机记录、语言包从公开桶直接拉取全部 200、刷新保持；B3 中文登录、页头按钮“简体中文”、菜单中文；B4 角色编辑权限树一级节点 = 资源中文名 / “其他权限” / 权限编码；B5 审计详情（页面实际收到的响应无 `label`）字段名中文；B6 价格试算说明中文；B7 页头切到 English 后 B4—B6 英文复核，再切回中文；B8 拦截 manifest 与公开桶后刷新仍为中文（本地缓存）。判定：控制台错误 0、4xx / 5xx 0、无裸 key、未拦截到写请求。**AC-07**（`--phase ac07`，写数据）见第 6 节第 5 步。

**中文零变化（AC-01，[`stage7_zh_compare.py`](./acceptance/stage7_zh_compare.py)）：** 同阶段 5 方法——基线 `9f210b5c` Vite（只读中继 mor-test）与发布后现网（真实 manifest、本机选择 zh-CN）各自登录后逐个菜单页（70 个）与首行详情抓可见文字，逐行比较；只读（非 GET 除登录 / 刷新外全部拦截）。

**本机已覆盖、mor-test 不重复：** 1481 个前端测试（含全 `src` 禁中文守卫、key 一致性、`nav` 与 `pageCapabilities` 一致）、2345 个 BFF 测试（F9）；阶段 5 的 218 张英文截图与逐词断行检查（AC-11、AC-12）。

## 10. 执行记录（阶段 7 填写）

执行人：主会话 code-mor-db；用户授权：2026-10-23“按现在的做法，放到 mor-test 验收，继续吧”与“继续”（阶段 6—7 合并；第 12 节全部按建议）。运行记录目录 `~/Code-MoR-backups/opsi18n-stage7/`。

| 时间（UTC） | 步骤 | 结果 |
|---|---|---|
| 19:53 | ① 协调 | 通知 code-mor-6b / d6 / 78 / bf / 42；bf、78、42 回复不受影响、窗口内不替换这两个服务；78 要求确认包含 `37173818`（见下） |
| 19:53 | ① 预检 | `precheck_readonly.py --browser` 34/34；`ops_bundles.py --dry-run` 35/35 |
| 19:54 | ② 首跑 | ②a 默认语言 `zh-CN → en`（原值见 `ops-bundles-2026-10-23T195446Z.json` 的 `applicationBefore`）、②b 新建 19 个命名空间；②c 确认导入 6 次均 503：Platform 抛 `ID_GENERATION_ERROR`——`SnowflakeIdGenerator` 同一毫秒序列（12 位，4096）用尽即抛异常、不等下一毫秒，一次确认需生成 8114 个 key ID。预览始终为新增 8114，未写入任何 key |
| 20:01 | ② 修正后重跑 | `ops_bundles.py` 改为**累积分批**导入：每批包含此前全部 key 与若干新命名空间（含已在线 key 的命名空间排最前），每批新增 ≤ 3000，最后一批即全集，任何一批废弃为 0（3198 / 6061 / 8523 key，新增 2789 / 2863 / 2462）。中途一次因批次未编码为字节在上传前失败（未写数据）。完成：全集再预览 8523 不变；zh-CN 写入 8114、不变 409；发布 **#3** READY，42 个语言包在 `MINIO/mor-platform-public`，匿名可读、摘要与内容一致、CORS 正确；通过 145 / 失败 0 |
| 20:03 | ② 后核对 | `precheck_readonly.py --after-bundles` 33/33 |
| 20:04:16—20:04:52 | ③④ 换镜像 | ops-bff `sha256:3751638e…` healthy rc=0、ERROR 0、环境键名与替换前一致（22 个）；ops-web `sha256:d228e3c8…` healthy rc=0；`/` 为 `<html lang="en">`；两步间隔约 20 秒 |
| 20:05 | ⑤ 接口验收 | `stage7_api.py` 65/65（首跑 64/1：C2 抽到的 10 条审计都是无字段的登录事件，检查落空；改为翻页查找带字段事件后通过，5 个字段无 `label`） |
| 20:07—20:20 | ⑤ 浏览器验收 | `stage7_browser.py` 28 通过、1 项预期：真实 manifest 下默认英文、切中文刷新保持、语言包从公开桶直取；中英文权限树（资源名 / 其他权限 / 编码，15 个一级节点）、审计字段名（5 个）、价格试算说明均正确；断网刷新保持中文；控制台错误 0。预期项：价格试算页整页跳转时取消的 `GET auth/v1/context`。脚本修正 2 处：antd 两字按钮自动加空格（“试 算”）、审计按动作编码筛出带字段事件 |
| 20:25—20:36 | ⑤ 中文零变化 | `stage7_zh_compare.py`：基线（`9f210b5c` Vite）对现网新版 70 页 + 详情：6 页差异均为数据 / 时间（审计、工作台、通知）与多语言三页基线回退英文、现网正确中文；无迁移造成的中文变化 |
| 20:38 | ⑤ AC-07 | `--phase ac07` 11/11：改 `auth.login.title` 中文 → 发布 #4 → 刷新可见 → 切回 #3 → 恢复 → 译文改回 → 草稿与 #3 无差异（保留非当前 #4） |
| 20:45 | 78 复核 | 新 ops-web 打开 acquiring 来源计费事实 `239295097391407104` 详情抽屉：200、正常显示、无 INVALID_RESPONSE（`acceptance/f1_check.py`，截图 `screens/f1-billing-fact-drawer.png`） |

**最终镜像：** operations-console-bff `sha256:3751638e5f34273844582b53ad0b5b2401a057fe8001bfc63d15241a8f8d487f`；operations-console-web `sha256:d228e3c883fdc1524ddb63c2d10f291100fc72a8bf25c191f1e03074c7a33433`（tag `opsi18n-20261023a`）。Platform：`operations-console` 当前发布 #3、默认语言 `en`、21 个命名空间 8523 key。回滚文件不变（现网前镜像）。

**遗留：** Foundation `SnowflakeIdGenerator` 序列用尽即抛异常（不等下一毫秒），单次生成超过 4096 个 ID 的写操作都会失败——建议另立需求修正公共库（或在批量写入处分批），本次以累积分批导入规避。

## 11. 风险与窗口影响

1. **默认语言变化：** 默认英文，没有本地选择的用户首次打开为英文（需求本意）；通知中说明页头 / 登录页右上角的语言按钮，选择后记在本机。
2. **③—④ 之间约 1 分钟：** 旧前端的审计详情报 INVALID_RESPONSE、权限树“其他权限”显示编码、试算说明缺失（第 2 节）。
3. **导入 / 发布超时：** key 导入确认（8114 行批量插入）与发布（42 个语言包在 Platform 锁内生成并上传）若超过 9 秒，浏览器 / 脚本收到 504 而 Platform 照常完成；脚本以重新预览与发布记录为准。锁租约 120 秒。
4. **Gateway 限流：** 运营路由每用户并发 2；脚本全部串行。浏览器验收共 5 次整页刷新（切换语言与断网），间隔较长；若出现 `GATEWAY_RATE_LIMITED` 429，按网络记录判读后重跑该段。
5. **其他机器的浏览器：** 不信任 test CA 时拉不到语言包，新前端回退内置英文且隐藏语言按钮（同前例第 11 节第 3 项）。
6. **基线漂移：** 阶段 7 之前若有人替换 ops-bff 或 ops-web，第 ① 步预检会 FAIL，本包需重建；本需求研发期间 ops-web 冻结（Q6）。
7. **AC-07 残留：** 留下一个非当前的发布 #4（登录标题带“（AC-07 验收）”），当前发布仍为 #3，草稿与 #3 一致。

## 12. 需要主会话裁决

1. **③、④ 的衔接。** 建议按第 6 节连续两条命令（BFF 健康后立即换 Web），接受约 1 分钟的旧前端三处不兼容（第 2 节）。备选：一条 `up operations-console-bff operations-console-web` 同时换——Web 先就绪时会出现“新前端 + 旧 / 重启中 BFF”，权限树报错，不建议。
2. **翻译导入切分。** 建议按命名空间 21 个文件导入（多 20 个私有导入文件记录）；备选一次导入全量文件（可能 504，脚本会重试核对）。
3. **AC-07 的做法。** 用真实 key `auth.login.title` 临时改中文并发布 #4、切回 #3、改回原文（专门的测试 key 需要改 en 源文件重新导入，不建议）。是否接受留下非当前的 #4。
4. **默认语言修改的必要性（只报告）。** 新前端固定默认英文，Platform 默认语言只影响 manifest `defaultLocale`（所选语言未发布时的回退）与“语言与应用”页显示；按任务卡仍改为 `en` 保持数据一致。
5. **随 BFF 上线的已提交改动（只报告）：** `98210e0b` 的 `paytech-ledger-api` 只增改动（第 1.2 节）。
6. **语言包缓存头（沿用前例裁决 4）：** 语言包对象没有长期缓存头；新前端用 `localStorage` 缓存不可变语言包，影响更小。验收只记录实际值。
7. **集成目录导出过期（只报告）：** `Code-MoR-opsi18n/operations-console-web/i18n-out/` 是阶段 5 修正前的导出（8513 key），提交前请重新导出或不纳入提交；本包以候选 worktree 的新导出（8523）为准。
