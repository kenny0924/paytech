# RCO-17 test 部署与验收记录

2026-09-12 用户对具体发布包回复“对”，本记录对应阶段 7 的实际执行证据。当前阶段与用户确认由 [delivery.md](./delivery.md) 和[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#rco17-test-20260912)维护。

一句话：更新 Risk、Operations BFF、Web 3 个应用，Risk 新增 4 表/44 字段/11 索引、调整 3 张旧表（增 4 列、删 2 列及 2 索引）、回填 462 行；Platform 插入 118 条注册，新增 10 条公共路由，交付配置管理。

## 环境及执行

- 环境：Docker Desktop `desktop-linux` / `mor-test`；MySQL `127.0.0.1:23306` 的 `mor_risk`、`mor_platform`。
- 入口：[决策流配置](http://localhost:28080/risk/decision-flows)。角色版本已推进，既有会话如要求重新登录，使用原管理员登录即可。
- 执行窗口：2026-09-12 16:45:58–16:49:42 UTC；先关闭风控入口、停三个应用，再按[发布包](./release.md)执行 SQL 和升级，最后开放入口。
- 备份：[本机受限目录](/Users/kenny/Code-MoR-backups/rco17-test-20260912T164558Z)，含 Risk 全库结构、三张迁移表结构及数据、Platform 九张控制面表；大小、摘要与退出成功证据见[执行日志](./assets/stage7/deployment.json)。备份与凭据不进入 Git。
- 仅替换上述三个应用。Platform/Gateway/Vault/Nginx 和四个基础设施容器保持原 ID、镜像和启动时间，没有配置项新增或共享制品发布。

| 应用 | 实际镜像 ID 前缀 | 结果 |
|---|---|---|
| Risk | `89bd500057a2` | healthy |
| Operations BFF | `bfc92deaee5e` | healthy |
| Operations Web | `e6585089b4e4` | healthy |

完整镜像、源码及 JAR 摘要沿用已批准的 [images.json](./assets/stage6/images.json)，实际容器见[最终核对](./assets/stage7/final-checks.json)。公开 HTML 及全部 22 个静态文件与批准制品逐一匹配，见[静态资源证据](./assets/stage7/public-assets.json)。本阶段没有修改业务代码或重建候选制品。

## SQL 与历史结果

1. Platform `01-maintenance.sql` 完成 56 次精确更新；Gateway 由 51 降为 27 条路由。
2. Risk 新增四表、三表扩展后，同连接回填 33 个规则版本、418 个条件 Context、11 个动作 Context；六项异常/未填计数均为 0 后提交。
3. 最终删列前，实际旧/新 Risk JAR 对全部 33 个原规则、协议及输入身份 Hash，以及 10 个 Release 详情/创建回放与迁移前基线逐项一致。见[历史比对](./assets/stage7/migration-history-verification.json)。原三张旧编排表、Release 和已有命令回执保持。
4. 执行最终三表 DDL，再执行 Platform `02-registration.sql`（118 条插入、1 次角色 CAS）；启动三个应用后执行 `03-enable.sql`（71 次更新）。没有执行恢复文件 04/05，没有重复执行已提交 SQL。
5. 最终 Risk 78 表；相关七张表的字段、类型、默认值、可空性、索引均与模型一致，新建表及新增列注释吻合。Gateway 61 条 ACTIVE Operations Route、35 个 ACTIVE Resource，管理员/只读角色版本分别为 15/8。

历史校验首次停在删列前：原辅助程序错误地要求普通配置 Reader 返回已按设计省略的旧 Point 投影。更正仅限阶段 7 校验程序，调用真实旧投影与字段还原，同时保留普通规则 Hash 核对；以旧、新两份 JAR 对同一原始基线通过后才继续。原发布包及摘要保留，修正程序另存 [ReadRiskHistory.java](./assets/stage7/ReadRiskHistory.java)。最终元数据辅助程序另统一了 SQL 字面量与 MySQL 的 BOOLEAN 表示，没有因此改动数据库。

## 限定验收及清理

| 验收项 | 结果与证据 |
|---|---|
| 配置创建、图内容、规则准确引用 | 创建 1 个合成规则集、1 个流程；条件分支和提前 END 配置可保存；[初始夹具](./assets/stage7/acceptance-fixture.json) |
| 同键重放与版本冲突 | 1 次同键重放返回同一版本；1 次过期版本保存返回 409；[HTTP 结果](./assets/stage7/http-acceptance.json) |
| 非法图与错误定位 | 浏览器新增不可达 END 后保存返回 422，标题、原因及节点草稿保留；修正后成功保存第 2 版；[错误状态](./assets/stage7/save-error-status.json)、[截图](./assets/stage7/save-error-preserves-draft.png) |
| 历史与返回 | 恰好 2 个成功流程版本，第 1 版内容未变且只读、无保存按钮；浏览器 Back 按历史→编辑→列表返回；[浏览器记录](./assets/stage7/browser-acceptance.json)、[历史截图](./assets/stage7/history-version-one.png) |
| 未登录访问 | 流程列表返回 401 |
| 只读账号登录验收 | **test 未重跑**：现有只读角色关联的 2 个账号 DISABLED、1 个历史账号 CLOSED；未新增/启用账号，阶段 5 本地只读证据保留；[核对结果](./assets/stage7/readonly-account-check.json) |
| 清理 | 先通过 API 删除流程，再删除规则集；活动列表均不再显示，准确历史和已删除规则引用仍可读取；[清理回执](./assets/stage7/cleanup.json) |

样本前缀 `RCO17_TEST_20260912T165248`；规则集 `224572973699403776`，流程 `224572974676676608`，流程版本 `224572974676676609` / `224573969729163264`。最终两个管理根分别为 DELETED，保留业务历史及回执；未创建真实评估或动作事实（两表仍为 0）。API/浏览器验收会话已退出，临时浏览器页关闭，凭据未落盘。

清理辅助程序按真实 DELETE 零 Body 合同及规则列表 `managementVersion` 字段修正后完成；没有重放创建/保存，也没有物理清库。本阶段是限定环境验收，用户完整功能验收及只读账号 test 验收仍待完成，不替代阶段 5 全量回归。

## 待提交范围

下列为下一阶段的审阅范围，本次未执行 Git 暂存、提交或推送。[工作区文件清单](./assets/stage7/pending-diff-inventory.json)只记录观察结果，不能整体作为暂存清单。

| 仓库 | 本需求提交内容 |
|---|---|
| risk-compliance-service | 决策流 API/领域/持久化/HTTP、规则输入解耦、历史兼容、模型及相关测试 |
| operations-console-bff | 决策流转发、响应校验、规则兼容及 422 字段错误投影、相关测试 |
| operations-console-web | 决策流页面与画布、规则历史引用、校验及导航、Pencil 设计、必要依赖与测试 |
| paytech-foundation | 本轮批准的 HTTP 字段校验异常与 MVC 422 映射、相关测试 |
| platform-support-service | 唯一初始化 SQL 中本轮 118 条注册及角色更新 |
| Code-MoR 根仓库 | 本需求目录、范围决定、Risk 生成源/测试、需求和时序目录、相关导航及当前台账的本需求差异 |

Gateway 当前无差异。根仓库其他需求目录及无关改动不纳入本次提交；逐文件/逐差异复核后才能暂存。用户确认 test 并明确授权提交后进入阶段 8。
