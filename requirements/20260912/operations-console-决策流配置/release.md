# RCO-17 test 发布包

文档性质：保留阶段 6 获批发布包；下文“当前/待部署/尚未”均指发布前检查点。用户已授权阶段 7，实际执行和辅助校验修正见[部署记录](./deployment.md)，当前状态由[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#rco17-test-20260912)维护。阶段 6 manifest 保留批准时的文件摘要，本页此说明为部署后追加。

一句话：替换 Risk、Operations BFF、Web 3 个应用；Risk 新增 4 表/44 字段/11 索引，另改 3 张旧表（增 4 列、删 2 列及 2 索引），回填 462 行；Platform 新增 118 行注册，开放 10 条新路由，不新增 Target。

## 环境、制品与依赖

- 本机 Docker Desktop `desktop-linux`、Compose project `mor-test`；入口 `http://localhost:28080`。
- 数据库为 test MySQL `mor-test-mysql-1`，`127.0.0.1:23306/mor_risk` 与 `mor_platform`；不是开发库 13306，也不涉及生产。
- 现有 11 个容器健康。服务/镜像完整 ID、数据库 UUID、结构、配置及版本见 [只读清单](./assets/stage6/preflight.json)。
- 本次不增加环境配置项；复用现有 Nacos、Redis 和服务发现。BFF 健康探针继续访问独立管理端口 19201。

| 应用 | 当前镜像 ID 前缀 | 待部署镜像 ID 前缀 |
|---|---|---|
| risk-service | c6a4f44a8cba | 89bd500057a2 |
| operations-console-bff | 79b697a757a9 | bfc92deaee5e |
| operations-console-web | 8832199077ef | e6585089b4e4 |

候选 tag 统一为 `mor-test/<应用>:rco17-decision-flow-20260912`。完整 ID、JAR/静态文件 SHA-256、源码摘要与仓库基线在 [images.json](./assets/stage6/images.json)。Risk/BFF 直接封装阶段 5 验证过的 JAR，Web 封装阶段 5 构建结果，没有重新解析 SNAPSHOT 依赖。Foundation 修复已嵌入 BFF，不需要单独启动服务或发布 Nexus。源码仍是未提交工作区，HEAD 不能代替文件摘要。

Risk 与 BFF 的 `risk-api` 包有 9 个 class 的调试行号差异；93 个条目逐项核对，差异 class 的完整 `javap -p -v` 在排除源行号、文件路径和校验和后完全一致，方法指令、字段、注解、常量池一致。不是把不同合同当作相同包。

通过未启动的临时容器核对镜像内 JAR 和 Web 全部 22 个静态文件，临时容器均已删除。[镜像检查](./assets/stage6/image-inspection.json)记录 Web index SHA-256 为 `3494b5ed6c8080be3264b2cb0049f1c7780c8c51521362dd23a13fa134c34c81`。test 的原容器 ID、镜像和启动时间均未改变。

现有 Platform 和 Gateway 的实际部署 JAR 已离线编译当前、维护、禁用注册和最终开放快照，路由数分别为 **51→27→27→61**，见 [兼容性结果](./assets/stage6/compatibility.json)。因此不替换 Platform/Gateway，不运行全量 `deploy.sh`，不变更 Vault、Nginx 或基础设施。

## SQL 与历史兼容门槛

| 顺序 | 唯一来源/环境执行文件 | 内容 |
|---|---|---|
| 1 | [Platform 维护入口](./db/platform-support-service/test-release/01-maintenance.sql) | 关闭 24 条旧风控 Route、17 个 Resource、13 个原有效角色绑定，推进 2 个角色版本 |
| 2 | [Risk 四表 DDL](./db/risk-compliance-service/ddl.sql) | 新增流程根、版本、节点、边；旧 74 表不因此删除 |
| 3 | [Risk 扩展 DDL](./db/risk-compliance-service/simplification/ddl.sql) | 在 3 张旧表新增 4 个暂时可空列 |
| 4 | [Risk 回填](./db/risk-compliance-service/simplification/dml.sql)及[后置核对](./db/risk-compliance-service/simplification/postcheck.sql) | 同一会话/事务回填并核对；预检当前为 33+418+11 行，异常为 0 后显式 COMMIT |
| 5 | 新 Reader 历史检查 | 入口仍关闭；使用准确候选 JAR 读取迁移后数据，核对与旧 Reader 的基线一致 |
| 6 | [Risk 最终 DDL](./db/risk-compliance-service/simplification/finalize-ddl.sql) | 2 个合同列收紧 NOT NULL，删除旧 2 个物理引用列及 2 个索引 |
| 7 | [Platform 禁用注册](./db/platform-support-service/test-release/02-registration.sql) | 原 [dml.sql](./db/platform-support-service/dml.sql) 的 118 行原值，角色更新改为 test 精确 CAS；不开放入口 |
| 8 | 更新并启动 3 个应用 | Risk→BFF→Web，分别等待真实 readiness；刷新浏览器资源 |
| 9 | [Platform 开放](./db/platform-support-service/test-release/03-enable.sql) | 恢复旧入口并启用新入口；Gateway 最终应为 61 条路由 |

Platform 环境文件是 canonical 注册 SQL 的明确执行投影，不是第二份初始化数据权威。其 CAS 前提及摘要见 [manifest](./db/platform-support-service/test-release/manifest.json)：初始管理员版本 12、只读角色版本 6；维护后 13/7，注册后 14/7，开放后 15/8。事务文件刻意不写 COMMIT，每个 `expected_one` 必须为 1；任一不符在同一连接 ROLLBACK，不能断开后另起连接提交。禁用注册 118 行的结果须在该事务内逐值核对来源。新增权限仅授予既有管理员，原只读角色保留只读。

只读 [precheck.sql](./db/risk-compliance-service/simplification/precheck.sql) 已通过：33 个规则版本、418 个条件 Context、11 个动作 Context；3 项关系异常均为 0。33 个旧协议版本、304 个旧输入字段、33 个旧绑定和 10 个 Release 全部保留。注册 ID/编码无冲突。

[历史基线](./assets/stage6/old-history.json)使用 test 旧 Risk JAR 的实际历史 Reader，在 MySQL READ ONLY 事务中读取全部 33 个规则版本，规则和协议 Hash 均与存储值一致；10 个 Release 的实际详情校验及创建回放读取通过。23 个已删除管理根的历史通过旧回放内部 Reader 读取，未修改其管理可见性。这是只读历史证据，不是完整 HTTP 幂等写请求回归。

下一阶段停写后重新捕获该基线；回填提交后、删列前执行：

```sh
python3 requirements/20260912/operations-console-决策流配置/assets/stage6/read_history.py --candidate
```

此命令使用新 JAR 的 Reader，比较全部规则/协议 Hash、旧输入字段身份摘要、Release Hash/详情/创建回放；凭据只通过进程标准输入传入。当前仅已完成旧 Reader 执行及新 Reader 编译，**尚未在 test 运行回填后的新 Reader**。结果不一致就不能执行最终删列。旧命令回执当前为保存 33、创建 Candidate 10、删除 23、冻结 14、校验 Candidate 7；迁移不得改写这些已有回执。

## 阶段 7 执行边界

1. 取得本包授权后，持有既有 `risk_operations_rollout_lock`；重新比对环境身份、3 个候选镜像、运行清单、源文件/SQL 摘要及 CAS。环境发生变化时先核对，不用旧版本号强行执行。
2. 只备份本次受影响数据：Risk 三张旧表的结构和数据、Risk 全库结构；Platform 涉及注册/入口/角色的 9 张配置表。存放 `~/Code-MoR-backups/rco17-test-<UTC>/`，目录 0700、文件 0600，校验命令退出码、文件非空及摘要；凭据与备份不进仓库。保存当前 3 个镜像 ID，恢复时使用准确 ID。
3. 按表中顺序关闭入口，等待 Gateway 27 条路由；停止 Risk/BFF/Web、核对无 Risk 写入连接，重新读取历史基线和只读预检，再执行迁移。维护期间操作台 Web 会短时不可用，浏览器未保存编辑应提前完成。
4. 完成新 Reader 比对、最终结构核对及注册后，只给 3 个已列应用更新 `:development`，使用 `compose.sh up -d --no-deps <服务>` 分别启动。不要触发其他服务重建。
5. readiness、Nacos 注册和 API 合同对齐后开放入口，确认同一 Gateway 消费新快照；已登录用户可能需重新登录以取得新角色版本。执行下述简短验收后交用户做完整功能验收。

## 恢复范围与触发条件

- 备份失败、历史 Hash 异常、回填数量不符、CAS 不符、候选镜像漂移或 readiness 失败：停在当前门槛，不开放入口。
- DML 未提交可同连接 ROLLBACK；DDL 隐式提交，不能靠事务撤销。尚未删旧列且没有新写入时，旧应用仍可读取旧结构；按实际已完成的 ALTER 核对，不能重复整份执行。
- 最终删列后、开放前，如需恢复旧应用：按备份恢复原三表结构/数据并验证旧 Reader，再恢复旧镜像。4 张新表为空时可以保留禁用，不为了回退额外 DROP。完成禁用注册后但尚未开放的旧入口恢复文件为 [05](./db/platform-support-service/test-release/05-restore-old-before-enable.sql)，只适用该明确阶段；其他部分执行状态先重新核对版本再准备对应恢复。
- 已开放后故障可用 [04](./db/platform-support-service/test-release/04-emergency-close-after-enable.sql)再次关闭全部风控入口并推进角色版本；只适用于 03 完整提交后的状态。若已产生新规则，旧应用无法理解缺少旧编排的记录，此时保留新数据、关闭入口并修复新版，不自动覆盖数据或补造旧 ID。

## 简短部署验收与用户验收

- 检查三个应用健康、Gateway 最终 61 条路由、公开 HTML/静态文件摘要属于本包，并在真实页面确认决策流菜单和编辑器。
- 复用现有 test 管理员/只读身份；不新增账户或扩大其他角色权限。检查只读可查看、不能保存/删除，管理员可保存/删除。
- 允许通过公开 API 创建 **1 个带 `RCO17_TEST_` 前缀的合成规则集、1 个合成流程、最多 2 个流程版本**，验证保存、历史、引用及非法图 422 保留编辑；最多各 1 次同键重放和旧版本冲突。使用既有字典，不新增字典、不创建或激活真实执行。
- 验收后通过已有删除 API 删除本次流程、再删除本次规则集；业务定义的历史和命令回执保留，不物理清库。只清理本次浏览器会话凭据。所有样本 ID 记录在阶段 7 结果，用户完整测试另行进行。

SQL 生成与 CAS 路径已在内存 SQLite 验证（开放 61、紧急关闭 27、恢复旧入口 51、过期 CAS 为 0），它不代表已在 test 执行 MySQL 写入。阶段 5 的业务/浏览器证据见 [acceptance.md](./acceptance.md)，本包不重复宣称一次新的全量应用回归。

原阶段 6 确认项已获用户回复“对”，授权记录见 [delivery.md](./delivery.md)。代码提交和共享制品发布不在此授权内。
