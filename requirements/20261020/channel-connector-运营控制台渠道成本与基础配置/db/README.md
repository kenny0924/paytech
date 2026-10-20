# 数据库与注册变更

| 文件 | 目标 | 性质 | 阶段 |
|---|---|---|---|
| `channel-connector/ddl.sql` | `mor_channel_connector` | 执行文件：新增 `t_channel_config_change`（由 CC-A 产出，与 `paytech/channel-connector/docs/database/sql/changes/2026-10-20-channel-config-change.sql` 同文） | 4 产出，本机阶段 4/5 执行，mor-test 阶段 7 执行 |
| `vault/dml.sql` | `mor_vault` | 执行文件：channel-connector-service PROTECT 规则 + 策略变更记录；本机 local 改 `local-environment/vault/policy.local.json` | 阶段 7（mor-test） |
| `platform-support-service/registration-dml.sql` | `mor_platform` | 执行文件：注册块 92（权限 7、授权 7、资源 9、路由 30、Header、Query、限流），默认休眠；BW 线合入时同文追加到 `platform-support-initial-data.sql` 末尾 | 阶段 5 本机、阶段 7 mor-test |
| `platform-support-service/activation-*.sql` | `mor_platform` | 激活（菜单 / 按钮 / 路由 ENABLED、管理员授权 GRANTED），按环境分别评审 | 阶段 5 / 6 产出 |

不自动扫描执行；每次执行记录在 delivery.md。
