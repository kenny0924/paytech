# commercial 运营控制台完善 交付记录

## 适用阶段

| 阶段 | 安排 |
|---|---|
| 1. 需求评审与范围确认 | 已确认（2026-10-20，"可以，都按建议来，登记需求开始做"）：F1–F9，后置合作伙伴合同等 |
| 2–3. 分析与设计 | 评审稿已交付（technique.md）；按用户"开始做"直接进入阶段 4，设计偏离在阶段 4 报告中说明 |
| 4. 研发 | H（commercial 只读接口 + F9）→ I（BFF、Web、注册），Opus 5.5 agent |
| 5. 本机验证 | 完成（2026-10-20）：各线 auto 测试与构建；本机冒烟 `verification/stage5-smoke.md`（1 个 Web 缺陷 D1 已修） |
| 6–7. mor-test | 完成（2026-10-20 05:34–05:53Z）：`release/release.md` §6；验收 `verification/stage7-acceptance.md` 101/102（唯一失败为验收脚本预期错误，复测 409 通过）、页面回归 46/46 |
| 8. 提交 | 完成（2026-10-20）：代码随 `commercial/second-slice` 快进合入 main（19ccd78a）；需求目录、台账、索引由主会话私有索引提交（见下方） |

## 确认记录

| 日期 | 阶段 | 用户原意 | 获准的下一步 |
|---|---|---|---|
| 2026-10-20 | 1 | "可以，都按建议来，登记需求开始做" | 登记需求，交付阶段 2 / 3 评审稿并开始阶段 4 |

## 当前阶段与下一确认点

阶段 1–8 完成（2026-10-20）。研发与发布过程记录见「研发与集成记录」；用户待办与遗留见文末。

**mor-test 现状：** commercial `sha256:59bb000d…`、ops-bff `sha256:e294a4ae…`、ops-web `sha256:f00801f5…`（tag `commercial-20261020b`，基线 19ccd78a = main 4543bfc1 + H + I1 + F9 + I2 + D1 修复）；`mor_platform` 块 93（103 行）与块 95 I1 续写（37 行）已插入并启用（11 路由、5 菜单 ACTIVE，PLATFORM_ADMIN 版本 34，菜单排序 95303→30、95304→20）；`mor_commercial` 无 SQL，验收合成数据已清理，`CL-F1105E` 未动；备份 `~/Code-MoR-backups/20261020T053437Z-commercial-ops2-test/`；回滚按 release.md §5。Platform 镜像未发（capability JSON 由 code-mor-9c 下一批 Platform 发布带上）。

**待用户处理：** (1) `test-environment/compose.applications.yaml` 的 commercial-service 段仍未回写（沿用首切片待办）；(2) 推送 / PR 未做；(3) 本机 `mor_commercial_lineb` 留有两轮冒烟数据（v4–v7、方案 SMK-SLICE2-PLAN-02），是否清理由你定。

**遗留（下期）：** commercial 版本驳回命令增加原因字段；版本 / 方案列表行补 `agreementNo`；BCR 快照带 `previousVersionNo` 或按 recordType 读取（现 BFF 回溯上限 1000 条，超出标 NOT_COMPARED）；合同 / 版本状态枚举发布到 `commercial-api`；`fee-events?termType=非法值` 应 400；commercial 日志加 requestId；client-master 提供运营可用的 Client 摘要 / 搜索与 OE 列表接口（Client 选择器现为 ID 手输 + 最近使用 + 审核通过 Client）；Platform 的 `console-resource-capabilities.json` 商务键待 Platform 发版。

## 研发与集成记录（2026-10-20）




- H 线完成：`commercial/line-h` 285c15d4（四个只读 API + Controller/Converter/Repository 扩展，README 增「运营控制台只读接口」节）。
- I1 线完成：`commercial/line-i1` 95c9834b（导航 / 仪表盘壳 / 费率预览 / 价格试算 / F9 Owner detail 透传 / 块 95 续用至 95962）；调研结论并入 technique.md §2.1–2.2。
- 集成：`commercial/second-slice` = main e73764d1 + H（6f21d37f）+ I1（fcd83ea4）+ F9 快进（c5f2c3f2）。合并 agent 验证（slice2 worktree，JDK 25）：commercial 168/168、arch-rules 61/61、ops-bff 1605/1605（F9 后同数）、ops-web 1117/1117 + build、platform-support 878/878、`generate-oe-client-schemas.mjs --check` 12/12；I1 与 9c 重叠的 4 个文件自动合并、块 95 与块 91 无 ID 重叠。main 其后又前进到 5b825160（counterparty 改名），最终发布前再 rebase。
- 既有失败（非本需求引入，两侧父提交同名失败）：`scripts/pdmaner` 下 platform-support-schema 4/11、platform-support-model 3/31、platform-support-scenario-doc 1/12 失败；另行报告。
- I2 线完成：`commercial/line-i2` 41e3cf1c（已 rebase 到 c5f2c3f2；60 文件）。BFF 新增 8 端点（合同列表、方案 / 版本列表、版本对比 diff、产品目录三个、变更历史含 changedFields / previousData，工作台改 5 张实数据卡 + 待审批队列）；Web 新增合同列表、价目总览、版本对比、产品目录、变更历史页，表单下拉改目录接口，Client 选择器（ID 手输 + 最近使用 + 审核通过 Client）；Platform 块 93 共 103 行（934xx 8 / 936xx 16 / 937xx 31 / 939xx 48，全部休眠，不新增权限）。BFF 1632/1632，web 1125/1125 + build，initial-data 全量导入临时库通过。与任务书差异：路径沿用 `/commercial-terms/plans|versions` 命名空间；5 张卡而非 4 张。遗留见 `notes/I2-gaps.md`（驳回接口无原因字段、版本行无合同号、BCR 回溯上限 1000 条、状态枚举未发布到 api；Client 名称搜索 / Party / OE 下拉需 client-master 接口）。
- 集成分支快进到 4d8f4ec3（41e3cf1c + I2 测试补提交，去掉新用例对 Owner detail 的断言），阶段 5 本机冒烟进行中（commercial-smoke agent，结果 `verification/stage5-smoke.md`）。
- F9 线完成：`commercial/line-f9` c5f2c3f2（Foundation `HttpProblemDecoder.DecodedProblem.detail`、`HttpServiceClientOptions.withOwnerProblemDetail()` 默认关闭、拦截器对 BUSINESS_CHECK_FAILED / CONCURRENT_REQUEST / NOT_FOUND / 已登记合同码用远端 detail，空白、>512 码点或含 ISO 控制字符回退；BFF 删除 `CommercialOwnerProblemDetail`，两个 commercial 工厂开启开关）。http-client-support 130/130，ops-bff 1605/1605，reactor test-compile 通过。规格见 technique.md §2.1。
- 设计稿：`operations-console-web/docs/operations-console.pen` 新增画板「商务 · 01 工作台」至「09 价格试算」（Pencil 内需手动保存）。

**跨会话约束：** code-mor-9c 的科目分级已提交 main e73764d1，发布 ops-bff / ops-web 前仍需通知它；code-mor-32 已把 consumer-service 切为 counterparty（03:24Z），本需求不重建 acquiring / card。

下一确认点：阶段 5 冒烟通过后 rebase 到最新 main、通知 9c，再按既有授权发 mor-test（只发 ops-bff、ops-web、commercial 与 mor_platform 块 93 DML）。

### 暂停点（04:1xZ）

- 代码全部已提交在分支上：`commercial/second-slice` 头 4d8f4ec3（= main e73764d1 + H 285c15d4 + I1 95c9834b + F9 c5f2c3f2 + I2 41e3cf1c/4d8f4ec3），各 worktree 干净。main 未合入、未 push、mor-test 未动。
- 主工作树未提交：本需求目录（requirement / technique / delivery / notes / db）、`requirements/README.md` 索引行、`operations-console-web/docs/operations-console.pen` 商务 01–09 画板（Pencil 内需手动保存）。
- 阶段 5 本机冒烟被强制停止（04:1xZ）：agent 未写出 `verification/stage5-smoke.md`，它启动的 commercial / ops-bff 本机进程已由主会话 kill；本机 `mor_platform_v2e2e` 是否已插入 / 激活块 93 未核实，恢复时先查 `t_gateway_route` 93 段计数再决定是否重跑 DML。冒烟需从头重做。mor-test 发布材料已备好（slice2 worktree `release/`：release.md、test-dml.sql 140 INSERT + 2 菜单排序 UPDATE、test-activate / deactivate 预期 3/8/5/1、compose-candidate 占位、acceptance.md；一次性库演练通过；mor-test 只读核对无 ID / 路由冲突）。发布前置：通知 code-mor-9c；channel-connector 运营台（块 92）也要替换 ops-bff / ops-web，后发布者须基于含对方改动的 main 构建；Platform 镜像不发，其 capability JSON 落后属已知。
- 恢复顺序：看两份 agent 产物 → 补完冒烟 → second-slice rebase 到最新 main（≥ 5b825160）→ 通知 code-mor-9c → 构建 `commercial-20261020b` → mor-test 发布 / 验收 → 阶段 8 私有索引提交。

### 恢复（04:3xZ）

- `commercial/second-slice` rebase 到 main 4543bfc1（含 counterparty 改名 c661e9fc / 5b825160 / 4543bfc1），头 886edda7，`--rebase-merges` 保留 H、I1 合并提交；无冲突，`PlatformOperationsSimulator.java` 与 `platform-support-initial-data.sql` 自动合并。合并 agent 正在 rebase 后复核（三个测试模块、web、schema check、test-dml.sql 与 initial-data 逐行比对）。
- 本机 `mor_platform_v2e2e` 核实：块 93 路由 8/8 已插入并 ACTIVE，I1 块 95 续写（资源 5、路由 3）已 ACTIVE，角色 510 版本 26。冒烟重跑跳过 DML / 激活步骤。
- rebase 后复核（合并 agent，886edda7）：commercial 168/168、ops-bff 1632/1632、platform-support 878/878（第一遍因共用 mor_platform 有他人测试残留而失败，重跑通过）、web 1125/1125 + build、schema check 12/12；`release/test-dml.sql` 与 initial-data 末尾 I1 / I2 块逐行一致，main 在 e73764d1 之后未改该 SQL，全文件无重复 ID / route_code / 路径。
- 阶段 5 冒烟完成（commercial-smoke2，886edda7，`verification/stage5-smoke.md` + transcript + 截图 01–11）：导航 8 菜单；工作台 5 卡与待审数随提交 / 审批变化；合同列表各筛选与 size=7 / 201、status=FOO → 400；方案 / 版本列表；新建 v6 提交、自批 400（Owner detail）、合成审批人 B 批准 200；v7 改价后 diff added 1 / removed 1 / changed 1，跨方案 400，缺失 404；目录 7 / 11 / 21 与库一致；变更历史 11 条含 changedFields / previousData，非法参数 400；F9 透传 400 / 409 / 422 / 404 均带 Owner 原因且 requestId 为 BFF 自己，非商务客户端仍是公共文案；只读端点未写库；日志 0 ERROR。
- 冒烟缺陷：**D1** Web `menu.ts` 的 `OPERATIONS_COMMERCIAL_TERMS` 路径表缺版本对比页路由，页面显示「暂无访问权限」（I2 agent 修复中，连带历史页「首个快照」重复标签、priceCount 导致的 changedFields 噪音）；**D2 / D3** 验收稿 page=0 与 versionDifferences 预期错误（同步改）；D4 查询参数白名单只在冒烟替身里验证，阶段 7 在真实 Gateway 复验。次要观察：fee-events termType=FOO 返回 200 空、commercial 日志无 requestId（记遗留）。
- 阶段 7 启动：commercial-release agent 执行复核与备份、test-dml；构建等 D1 修复提交后给基线。
- code-mor-9c 确认：无需带上它的改动、未在 mor-test 验收，可直接发；发完后要我合入 main 并告知 commit，它再 rebase 重建 ops-bff / ops-web / Platform（带 capability JSON）。
- D1 修复提交 19ccd78a（menu.ts 补对比页路径 + 路由测试、历史页标签去重、BFF `CommercialBusinessChangeProcessor` 对版本快照未记录的 priceCount / makerAccountId / checkerAccountId 不算变化 + auto 用例；web 1126/1126，BFF 1633/1633）。验收稿 acceptance.md 按实现修订（page=1、size=10、F3-4 预期）。
- 阶段 7 执行中（commercial-release）：第 1 步复核 05:34Z 通过；第 2 步备份 `~/Code-MoR-backups/20261020T053437Z-commercial-ops2-test/`；第 4 步 test-dml 05:34:52Z 提交 5/3/8/3/18/8/16/31/48（休眠，菜单排序 95303→30、95304→20，角色 510 版本仍 33）；基线 19ccd78a 已下发，构建 → 替换 → 启用 → 验收进行中。
- 阶段 7 完成（05:39–05:53Z，commercial-release）：构建基线 19ccd78a，镜像 commercial `59bb000d…`（JAR 437a46e8…）、ops-bff `e294a4ae…`（JAR 54c9bf0c…，F9 开关仅在 `CommercialClientConfiguration`）、ops-web `f00801f5…`（入口 `assets/index-D5UHJXEd.js`）；替换顺序 commercial 05:39:47 → ops-bff 05:40:17 → ops-web 05:40:34，均 healthy、0 重启、0 ERROR；test-activate 05:40:43 提交 3/8/5/1，PLATFORM_ADMIN 33→34；验收 101/102（F9-3 脚本以 expected=当前-1 触发了重放语义返回 200，改为 5 后 409 带 Owner detail；acceptance.md 已更正），真实 Gateway 查询参数白名单 400 生效，PRICING_READ-only 账号对工作台 / 目录 403，diff / 试算 / 413 大 body 均符合预期；页面回归 42 页 + 4 深链 46/46（含 D1 对比页）；五服务日志 0 ERROR；XXL 任务 16 3/3、17 26/27（唯一 500 落在 commercial 重启窗口）；清理一次事务删除合成数据（BCR 版本 42 / 记录 10 / 组件 27 / 价格 9 / 版本 2 / 方案 1 / 限额 3 / 开通项 2 / 产品 1 / 文件 1 / 参与方 2 / 条件 3 / 画像 3），行数回到验收前基线，合成账号停用、角色清空。
- 阶段 8：main 快进到 19ccd78a；需求目录（含 release / verification / assets）、台账与索引由主会话私有索引提交。

