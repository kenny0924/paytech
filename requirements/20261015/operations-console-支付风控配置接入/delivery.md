# 交付记录：Operations 接入支付风控配置与执行发布

| 日期 | 阶段 | 内容 | 状态 |
|---|---|---|---|
| 2026-10-15 | 1 需求评审 | [requirement.md](./requirement.md) 评审稿：范围、不做项、Q1～Q4、AC-1～12、UI-1～4 | 待确认 |
| 2026-10-15 | 1 需求评审 | 用户授权直接通知 Risk；已向 Risk 会话发送 R-Q1（执行发布列表接口）、R-Q2（附加输出声明时间） | 待 Risk 回复 |
| 2026-10-15 | 1 需求评审 | 用户回复“可以”：需求、范围、验收标准及 Q2～Q4 按建议确认，进入阶段 2 | **已确认** |
| 2026-10-15 | 1→2 | Risk 回复 R-Q1/R-Q2 原则接受并给出字段（列表 pointCode 必填；回滚＝用旧 flowVersionId 新建发布，不支持重新激活；上下文 outputs 声明），待 Risk 用户确认 | 记录 |
| 2026-10-15 | 2 现状差异与影响 | [technique.md](./technique.md) 评审稿：差异表 16 项、逐系统影响 8 项、风险 K1～K7、工作量粗估；新发现 R-Q3（变量参数不回显）、R-Q4（Risk 名单日志）、R-Q5（严格校验导致的部署顺序）已发送 Risk | 待确认 |
| 2026-10-15 | 2 现状差异与影响 | Risk 回复：R-Q4 关闭；R-Q5 同意先 BFF 后 Risk；更正上下文 `outputs` 位于顶层且仅支付点输出（结账不受影响），K1 已修订；R-Q3 有初步方案待 Risk 用户确认 | 记录 |
| 2026-10-15 | 2 现状差异与影响 | 用户回复“没问题”：差异判断、影响范围与风险处理确认，进入阶段 3 | **已确认** |
| 2026-10-15 | 3 方案与设计 | [technique.md](./technique.md) 第 7～14 节、[ui.md](./ui.md)（新增 2 页、修改 3 页）、[注册 DML](./db/platform-support-service/registration-dml.sql)（122 行）与[本机激活](./db/platform-support-service/activation-local.sql)；D1～D5 待确认 | 待确认 |
| 2026-10-15 | 3 方案与设计 | 用户要求 UI 改动用 Pencil 作图：`design.pen` 新增 13 张“支付风控 ·”画板并导出到 [assets/stage3](./assets/stage3/)，ui.md 以画板为准。Risk 更新：R-Q1/R-Q2 已提交 `b26e857` 并部署 mor-test（先于 BFF，附兼容证据）；R-Q3 不做，Velocity 选项写死、详情不回显参数；方案相应更新 | 待确认 |
| 2026-10-15 | 3 方案与设计 | 用户回复“我看了下没什么问题，继续吧”：方案、接口、权限（D1～D4）、注册 DML 与 13 张画板确认，进入阶段 4 研发（不含 SQL 执行与部署） | **已确认** |
| 2026-10-15 | 4 研发 | BFF：risk-api 升级到 `b26e857`；决策流 `outputs`（模型、转换、请求/响应严格校验接受可选键）；变量 `velocity`/`list` 参数与新来源；名单 5 个、执行发布 6 个转发接口（Controller → Client → Impl/Converter/ResponsePolicy），`/risk-lists` 元数据日志；测试新增名单 5、执行发布 5、决策流输出 1、变量新来源 1，并修正模拟服务回显新键、路由清单与依赖边界守护。Web：规则集风控点切换与支付点统一变量目录、拒绝动作泛化；决策流放行结束节点认证要求与画布 3DS 标签；变量实时累计/名单命中表单；新增“风控名单”“执行发布”两页（影子轮询、激活确认醒目差异与强制勾选、以此版本新建回滚）；页面能力登记 2 项。验证：BFF `./mvnw -o verify` 1298/1298；Web `npm test` 1012/1012、`npm run build` 通过。偏差：校验/激活 POST 需带 `Content-Type: application/json`（入站拦截要求）；变量创建的新键允许省略以兼容旧请求。未做：本机联调与浏览器核对（阶段 5） | 已确认（用户：“可以”） |
| 2026-10-15 | 5 综合验证 | 用户确认阶段 4 并授权本机 SQL/启动/验收。[报告](./verification/stage5-local-acceptance.md)：本机全链路接口 44/44（[证据](./verification/stage5-api-evidence.md)）、浏览器 26/26（[截图](./assets/stage5/)，对照 13 张画板）。执行 `mor_platform_e2e`：注册 122 行、本机激活 11/5/4/1、前置能力开放 28/13/8/1（用户授权）、请求头增量 28 行；`mor_risk` 调试后按基线恢复再做最终验收。修复 5 个缺陷：D1 BFF 规则集目录终态动作写死结账点（支付点 502）；D2 注册 DML 写路由漏 content-type/idempotency-key 头（415，完整 DML 改为 150 行）；D3 Web 名单/执行发布写命令缺 Idempotency-Key；D4 风控点下拉只有结账点；D5 决策流详情过时文案。恢复被其他会话还原的 5 个共享文件改动。验证：BFF `verify` 1299/1299，Web 1014/1014 与构建通过。待决定：F1 Nginx 访问日志记录名单原值查询参数；F2 操作人显示 ID；F3 SHADOWING 文案 | 已确认（用户：“可以”） |
| 2026-10-15 | 5 综合验证 | 确认后清理：停本机 Risk，按基线备份恢复 `mor_risk`（恢复前核对差异全部来自本次验收），84 张表行数逐表一致，Risk 重新启动。`mor_platform_e2e` 的注册与开放保留。F2、F3 按建议保持现状 | 记录 |
| 2026-10-15 | 6 test 发布准备 | [发布清单](./release/release.md)：只替换 Operations BFF/Web；mor-test 只读核对（ID 无冲突、前置能力已开放、PLATFORM_ADMIN v23、支付点无发布与评估）；候选制品由 `132c982` 加本需求补丁在干净 reactor 构建（jar `d0dd068f…`）；[激活](./release/test-activate.sql)/[停用](./release/test-deactivate.sql)/[合成数据清理](./release/test-acceptance-cleanup.sql) SQL（清理 SQL 在本机回滚试跑通过）；联调基线与阈值 D1、F1 改判 D2（test 的 Nginx 不记访问日志）、只读账号 403 分支 D3。候选镜像构建被权限策略拦截，待用户执行构建脚本 | 已确认（用户：“可以”，D1～D3 按建议） |
| 2026-10-15 | 7 test 部署与环境验收 | [执行记录](./release/release.md#9-执行记录2026-10-15utc-15551605)：候选镜像 BFF `68e939f5…`、Web `5fb6911b…`；备份后 `mor_platform` 注册 150 行、激活 11/5/4/1（PLATFORM_ADMIN v24）；替换 BFF/Web，浏览器拿到的资源与构建一致；经 `http://localhost:28080` 接口 44/44、浏览器 27/27（[截图](./assets/stage7/)）、25 页只读回归、日志 0 命中；合成数据已清理（84 张表行数与备份一致）；联调基线 R-11 `PAY_ATTEMPT_FLOW` 已激活并完成 4 次生效确认（10/10）。待提交清单见发布记录第 10 节 | 已确认（用户：“可以”） |
| 2026-10-15 | 8 代码提交与归档 | 按[发布记录第 10 节](./release/release.md#10-待提交清单阶段-8-授权后执行只做本地提交)本地提交：operations-console-bff、operations-console-web（含 `design.pen`）、本需求目录，以及 `requirements/README.md`、`CURRENT_IMPLEMENTATION.md` 中属于本需求的部分（提交号见 `git log`，提交说明含本需求目录名）。未推送 | 已完成 |

当前实现与部署状态只见 [CURRENT_IMPLEMENTATION.md](../../../agent-docs/CURRENT_IMPLEMENTATION.md)。
