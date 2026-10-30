# 联合查询 · 阶段 6 · Operations Web 发布准备（2026-10-30）

> 只做准备：未改 mor-test（只读 `docker inspect`、从容器 `docker cp` **取出**静态文件、`compose.sh … config` 渲染），未停起任何容器，未提交。
> 镜像在本机 Docker 构建；mor-test 与本机同主机，镜像可直接使用。未推送任何仓库，未动 `:development` tag。

## 1. 基线证明（候选不回退现网）

| 项 | 结果 |
|---|---|
| 现网容器 | `mor-test-operations-console-web-1`，镜像 `sha256:748ca7b9c6b417104bbb7e3a09ba4b2630d8c4c513a4de2c43644a769b66a97a`（`tax-20261029a`），healthy。容器于 10-30 05:49Z 随 Docker 重启重建，镜像未变 |
| 现网 dist | `docker cp …:/usr/share/nginx/operations-console/.` 取出，266 个文件 → `evidence/ops-web-dist-live-748ca7b9.sha256` |
| 基线构建 | `git archive 6eaf1bd3 operations-console-web` 加主工作树 `node_modules`（`package-lock.json` 逐字节相同）后 `npm run build`，266 个文件 → `evidence/ops-web-dist-baseline-6eaf1bd3.sha256` |
| 比较 | **266 个文件逐字节相同**（`evidence/ops-web-dist-baseline-compare.txt` 为空） |

结论：现网 ops-web 就是 main 6eaf1bd3（税务 fa21d5ce）的构建，没有未提交内容；候选 = 6eaf1bd3 + 本需求文件，不回退任何现网工作。

补充：team-lead 更新基线之前，我用 `Code-MoR-tax-rel`（30361be7 + 税务未提交改动）构建过一次，也是 266 个文件与现网逐字节相同，与 6eaf1bd3 的结论一致。

## 2. 候选源码与检查

**源码。** `/Users/kenny/Desktop/Workspace/Code-MoR-jq-rel`（由 jq-platform 准备）：main 6eaf1bd3 + 联合查询 ops-web 34 个文件，SHA-256 见 `evidence/ops-web-source-files.sha256`。

**独立核对。** 我在暂存区把 jq-m 的改动三方合并到税务基线上，结果与 jq-rel 的 34 个文件逐字节相同：

- 与税务重叠的 5 个文件：`i18n/zh-CN/nav.json`、`src/i18n/en/nav.json`、`ConsolePage.tsx`、`pageCapabilities.ts` 自动合并。
- `configurationModel.test.ts` 页面数冲突，取 **82** = 79 + 联合查询 2 + 税务 1，与 Platform `console-resource-capabilities.json` 的 82 项一致。

**检查（在 jq-rel 内）。** `node_modules` 为指向主工作树的符号链接，lock 相同，与基线构建用的是同一套依赖。

| 命令 | 结果 |
|---|---|
| `npx tsc --noEmit` | 通过 |
| `npm test` | **1654 / 1654**（`evidence/ops-web-test.log`） |
| `node --experimental-strip-types --test src/i18nSourceGuard.test.ts src/i18nCatalog.test.ts` | 7 / 7 |
| `npm run build` | 通过，dist **273** 个文件（`evidence/ops-web-dist-candidate.sha256`）；与暂存区合并构建逐字节相同 |

**与现网的差异。** 40 个文件相同，1 个改动（`index.html`），新增 232、移除 225（`evidence/ops-web-dist-candidate-vs-live.txt`）。主要是 Vite 按内容命名的公共块：共享代码一变，文件名连带改变，属于预期；不回退的证明见第 1 节。

## 3. 候选镜像（tag `jq-20261030a`，只认 sha256）

| 项 | 值 |
|---|---|
| 构建 | `docker build --pull=false -t mor-test/operations-console-web:jq-20261030a .`（jq-rel/operations-console-web；Dockerfile 与基础镜像 `nginx:1.30.4-alpine@sha256:dc5069ad…` 同现网） |
| 镜像 ID | **`sha256:da81d4b06dd20b3b53bd77abd0fc68bdd6d8686b3e022634fad99efcd8f94656`**（Created 2026-10-30T06:13:43Z） |
| 镜像内 dist | 273 个文件，与构建产物逐字节相同（`evidence/ops-web-dist-image-da81d4b0.sha256`） |
| nginx.conf | 与现网镜像逐字节相同 |
| 元数据 | Entrypoint `["nginx"]`、Cmd、User `nginx`、端口 80 / 8080、StopSignal `SIGQUIT`、Healthcheck（compose 提供）与现网 748ca7b9 相同 |

## 4. compose 覆盖文件

| 文件 | 镜像 |
|---|---|
| `compose-candidate.operations-console-web.yaml` | `sha256:da81d4b0…`（jq-20261030a，dist 273） |
| `compose-rollback.operations-console-web.yaml` | `sha256:748ca7b9…`（tax-20261029a，现网，dist 266） |

- 用法（阶段 7，由主会话执行，须用户授权）：

  ```
  ./test-environment/compose.sh -f "<文件绝对路径>" up -d --no-deps --no-build --pull never --wait --wait-timeout 180 operations-console-web
  ```

- 渲染检查（`compose.sh -f … config`，只读）：两个文件都只换 image；应用 env 为空，healthcheck 来自基础 compose。现网容器 env 只有 nginx 基础镜像自带的 7 个键，基础镜像相同，因此候选与回滚的 env 与现网一致。不新增 env。
- 阶段 7 执行后核对：

  ```
  docker exec mor-test-operations-console-web-1 sh -c 'find /usr/share/nginx/operations-console -type f | wc -l'
  ```

  结果应为 273；容器 healthy。

**发布顺序。** ops-web 放在 Platform / ops-bff / 注册块（含 Gateway 快照）**之后**换，语言包随后发布：新页面调用新接口，提供方先上线。窗口影响：

- 换 ops-web：数秒。
- 换 ops-web 到发布语言包之间：新页面的 zh-CN 文案显示内置英文。

**回滚。** 只回退 ops-web 可行：新页面下线，接口保留。回退 Platform / ops-bff 而保留新 ops-web 不可行：新页面会调用不存在的接口。语言包不必回退：旧页面不引用新 key。

**漂移检查。** 阶段 7 前对 jq-rel ops-web 重新计算 `evidence/ops-web-source-files.sha256`；有变化就重建镜像并改写候选文件。

## 5. 阶段 7 浏览器验收计划（mor-test）

**前提**（由 jq-platform / 主会话提供）：

- Platform、ops-bff、注册块（路由与菜单 联合查询 / 键类型）已上线，Gateway 快照刷新成功。
- operations-console 语言包已发布（data-query 新增 213 个 key + nav 2 个）。
- mor-test 演示模板（R7，`JQ_*`）与数据源已登记。
- 入口值（退款单号 / 支付尝试 ID / 客户订单号）告知我；联合查询页与键类型页的菜单对管理员可见。

**入口与脚本**

- 入口 `http://localhost:28080`（运营控制台，同源）。
- 脚本 `acceptance/jq_browser.mortest.mjs`，由阶段 5 脚本改写：
  - 凭据读 `test-environment/private/access.md`，不打印。
  - 只登录一次，会话写入后续组合的 sessionStorage，避开 REQ-05 遇到的同账号短时间多次登录被 Gateway 拦截。
  - 入口值经 `ENTRY` 传入，模板经 `TEMPLATE` 传入。
- 用法：

  ```
  ENTRY='{"refund":"…","attempt":"…","clientOrder":"…"}' TEMPLATE=JQ_… node jq_browser.mortest.mjs
  ```

**组合与步骤。** zh-CN / en × 1440 / 390：

| 原型 | 步骤 |
|---|---|
| 01 | 空状态分组标签、类型菜单（常用 / 更多、输入提示、Esc 关闭） |
| 08 | 两行输入中有一行格式不对：就地标红与提示；覆盖范围抽屉 |
| 02 | 退款单号：总览、明细与路径；有截断节点时“加载更多”；“定位”；“全部 n 项” |
| 07 | 支付尝试：上游节点在左侧泳道；明细中维度值“以此查询” |
| 03 | 客户订单号（若演示数据有多个客户）：客户筛选；若自然出现失败或未查节点：汇总行、明细与“重试这一项”（重试只读） |
| 06 | 390 宽：分组卡片、明细抽屉、“查看链路图” |
| 04 | 键类型页：分组列表；被引用的键类型值类型锁定；正则现场试（只在前端，不保存） |
| 05 | 模板编辑器“联合查询”页签（390 下标签可能在“…”菜单中，脚本按视口自动选择）；1440 下核对保存往返：截获 PUT 请求体比对 `joint` 后在浏览器内中止 |

**每步检查。** 无未翻译 key（`data-query.*` / `nav.*` / `common.*`）、390 无横向滚动、控制台无新增 error、`/api/` 下无非预期 HTTP ≥ 400。刻意中止的 PUT 单独记录。

**对 mor-test 的写操作：默认 0。**

- 不启用 / 停用任何模板、不拖动键类型（K5）、不新建或修改键类型、不保存模板。
- 模板编辑器的保存请求在浏览器内中止（脚本另用 route 拦截作保险），版本号前后核对不变。
- 以下写操作在 mor-test 默认关闭，只有设 `WRITES=1` 且经用户单独批准才执行：
  - 临时启用 / 停用慢查询模板，演示失败与重试；
  - 键类型同组拖动后拖回。

  建议 mor-test 不做；失败与重试已在阶段 5 本机验证。

**记录。** 截图写 `acceptance/screens/`，结果写 `acceptance/browser-result*.json`，报告写 `stage7-report-browser.md`。

## 6. 本机遗留（仅本机，与 mor-test 无关）

- 本机 Vite 18316 的语言包 CORS：MinIO 19400 未放行 18316，阶段 5 脚本用 Playwright route 补 `Access-Control-Allow-Origin`。mor-test 走同源 28080，不受影响。
- 暂存区：`/Users/kenny/Desktop/Workspace/.jq-web-stage6/`（基线构建、合并核对与现网取出的 dist），可删除。
- `Code-MoR-jq-rel/operations-console-web` 下的 `node_modules`（符号链接）与 `dist/` 都不入库，提交时排除。
