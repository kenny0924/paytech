# SQL 执行记录

| 时间（+04） | 环境 | 库 | 文件 | 备份 | 结果 |
|---|---|---|---|---|---|
| 2026-10-27 01:19 | local | mor_vault | db/vault/dml.sql（先 dml-check，后 dml-verify） | `Code-MoR-backups/onboard-20261027/local-mor_vault-pre.sql` | 核对五项全 0、执行前版本 24；执行后 LEGAL_IDENTIFIER_V1 1 个类型、2 条规则（0761/0762）、变更记录 0760 版本 25 |

> 本地 e2e 目标库核对（01:35）：Vault 容器读 `mor_vault`（上条有效）；client-master 读 `mor_client_master_v2e2e`、compliance 读 `mor_compliance_v2e2e`、channel-connector 读 `mor_channel_connector_v2e2e`、Platform 读 `mor_platform_v2e2e`（`mor_platform` 为空库）。本需求本地 DDL / DML 与块 138 执行到上述 `_v2e2e` 库（先备份）。

> 构建隔离（01:52）：worktree 新增 `paytech/.mvn/maven.config`（`-Dmaven.repo.local=$HOME/.m3-onboard/repository`），防止 agent 构建写入共享 `~/.m3`；**仅限本 worktree，提交时排除**。共享 `~/.m3` 的 compliance-api 已两次从主工作区恢复（01:18、01:51）。
| 2026-10-27 ~05:50（cmp-dev） | local | mor_compliance（auto 测试库，非 e2e） | compliance 新 schema 重建（29 表） | `~/Code-MoR-backups/20261027-onboarding-local/` | cmp-dev 报告：变更脚本在旧 schema 副本上重放结果与新 schema 一致；131 个 compliance 测试通过 |
| 2026-10-27 06:55 | local | 4 个 `_v2e2e` 库 | 备份 | `Code-MoR-backups/onboard-20261027/local-*_v2e2e-pre.sql` | client_master / compliance / platform / channel_connector |
| 2026-10-27 07:00 | local | mor_client_master_v2e2e | release/sql 20 → 21 → 22 → 23 → 29 | 同上 | 清理删除 0（本地无 PENDING）；国家包 3 辖区 ACTIVE、8 个 Schema、275 字段；29 六项全 0 |
| 2026-10-27 07:03 | local | mor_compliance_v2e2e | 30 → 31（@approved_by=236033080844881920）→ 32 | 同上 | 清理删除 0；核验全 0；资格 CLIENT 2 / OE 1 / LP 1 保留 |
| 2026-10-27 07:04 | local | mor_platform_v2e2e | 01 → 60 → 02 → 63（休眠） | 同上 | 预检全 0，依赖各 1 |
| 2026-10-27 07:06 | local | mor_platform_v2e2e | 61 → 64（激活） | 同上 | Gateway 快照 475 → 497（与离线门禁一致） |

> 本地服务替换（07:02—07:06，`verification/local-e2e/swap.py`，旧 JAR 备份 `*.pre-onboard`）：client-master（adcfc346）、compliance、Platform（onboard-20261027a）、ops-bff；client-bff 新启动（argv 取自 ops-bff 捕获并换 JAR，`CLIENT_ONBOARDING_ENABLED=true`、`CLIENT_CONSOLE_ORIGIN=https://127.0.0.1:18443`）；client-web 由 onboarding worktree 的 vite 在 18302 提供（nginx 18443）。ops-web 18301 为主工作区 vite（他人共用），未替换。本地未登记 XXL 逾期任务（由 auto 测试覆盖）。

> 本地环境修复（07:13）：本地 Vault 容器 `mor-local-vault-service-1` 在 Nacos 的注册已丢失（10-25 之后，与本需求无关），Platform 登录 503 `No servers available for service: vault-service`。`docker restart` 后重新注册（127.0.0.1:18107 healthy）。

## mor-test（阶段 7，主会话执行）

| 时间（+04） | 步 | 内容 | 结果 |
|---|---|---|---|
| 07:47 | ① | 备份 5 库 + 镜像回滚列 + env 键名，目录 `/Users/kenny/Code-MoR-backups/20261027T034703Z-onboarding-slice1-mor-test` | SHA256SUMS 已记；回滚文件 8 个服务镜像与现网一致（Gateway 不换） |
| 07:48 | ② | 只读预检 00 / 01 | Vault 五项 0、版本 20；块 138 全 0、依赖各 1、c03 ACTIVE |
| 07:48 | — | 通知并行会话 code-mor-6b（P-10）；对方确认 relsnap（4b661830）在本次基线内（ea0272f3 祖先，运行 JAR 逐项一致） | — |
| 07:49 | ④ | 10 → 11 Vault | LEGAL_IDENTIFIER_V1 + 2 规则 ACTIVE，策略版本 21 |
| 07:49 | ⑨ | screening-mock 容器 | healthy，172.30.73.43，挂载 onboarding worktree 脚本 |
| 07:50 | ⑩ | channel-connector 同镜像 23679d45 重建 + 两项放行；backend 网络内登记 | 7 项登记 PASS、冒烟 6/6、未授权 403（verification/screening-channel-mortest.json） |
| 07:50 | ⑪ | 60 → 02 → 63（块 138 休眠） | 预检全 0；Gateway 快照 585 不变 |
| 07:51 | ⑪b | Platform → cad6dc14 | healthy，无 ERROR；快照仍 585 |
| 07:54 | ⑤ | 20 → 21 → 22 → 23 → 29 client-master | 与试跑一致：ACTIVE 37、PENDING 0、申请 37；legacy_redacted_keys 37、apps_schema_null 37；29 六项 0 |
| 07:55 | ⑥ | client-master → 595d20bc，07:58 再换最终 997f8bf8（cm-dev 申请级错误挂主体的跟进；之后代码冻结） | healthy，无 ERROR |
| 07:55 | ⑦ | 停 compliance → 补备份 `mor_compliance-pre-ddl.sql` → 30 → 31（@approved_by=221546852372267008）→ 32 | 案件只剩 APPROVED 37；资格 CLIENT 37 / OE 1 / LP 1；核验全 0 |
| 07:56 | ⑧ | compliance → f5a9b13a | healthy，无 ERROR；ONBOARDING_MATERIALS_ENABLED 已去除 |
| 07:57 | ⑧b | XXL job 24 `clientReviewReturnExpiryScan`（group 6，`0 */10 * * * ?`，FIRST，SERIAL_EXECUTION，超时 300，重试 0） | trigger_status=1 |
| 07:58 | ⑫ | client-console-bff → 83386ff5；operations-console-bff → c5ed58df | healthy，无 ERROR；ops-bff 4 个 OPERATIONS_*_BASE_URL 保留 |
| 07:59 | ⑬ | client-console-web → 15557b63；operations-console-web → a6b61d81 | 两端首页 200 |
| 08:00 | 环境修复 | mor-test Vault 容器（10-22 启动）在 Nacos 的注册已丢失，Platform 重启后登录 503 `No servers available for service: vault-service`；`docker restart mor-test-vault-service-1` 后恢复 | 与本需求代码无关；同一问题本机亦出现 |
| 08:00 | ⑬b | 语言包发布：登录通过，但 Platform 拒绝命名空间编码 `onboardingSchema`（规则 `[a-z][a-z0-9-]{0,63}`）→ 前端改名 `onboarding-schema`（后端 labelKey 保持，前端映射），重建两个 Web 后重跑 | 进行中 |
| 08:01 | ⑭⑮ | 61 → 64 激活块 138 | 路由 18 + 5、资源 7、授权 7、c03 停用；Gateway 快照 585 → 607（与离线门禁一致） |
| 08:04 | ⑬ 再换 | client-console-web → 4659a92a；operations-console-web → 4ee66de8（命名空间改名 `onboarding-schema`） | 两端首页 200 |
| 08:05 | ⑬b | 重跑计划（废弃 142 全为 `onboarding.*`）→ `--apply` | 通过 102、失败 0；client-console #2、operations-console #9，命名空间 9 / 24，`onboarding-schema` 中英文匿名可读；记录 `~/Code-MoR-backups/onboarding-slice1-i18n/onboarding-bundles-2026-10-27T040503Z.json` |
| 09:33 | 补丁 | 用户报告运营「入驻配置 · 采集 Schema」显示服务暂时不可用（requestId 57e94cd8…）：BFF 返回 200，ops-web 解码器拒绝 `organizationTypeCode: "*"`。ops-fe 修复 → operations-console-web 4d4c1130（单服务回滚 `release/compose-rollback-patch.operations-console-web.yaml` → 4ee66de8）；client-web 核对无同类问题（新增国家包真实形状测试） | 已替换 |
| 09:34 | 补丁 | operations-console 语言包 #10（新增 `client-review.config.defaultOrganizationType`、废弃未用 `config.allOrganizationTypes`）；发布请求经 Gateway 504，但服务端 05:34Z READY，重跑核对 63 项通过 | 完成 |
| 09:50 | 补丁 | ui-smoke 发现运营案件工作台全部 INVALID_RESPONSE（documents 用 fileAddress、industries 用 labelKey，解码器未跟上）及 Client 详情原始字段码、列表缺辖区筛选与列宽；ops-fe 修复（43 个真实响应回放测试）→ operations-console-web 35b61f12（单服务回滚到 4d4c1130） | 已替换 |
| 09:51 | 补丁 | operations-console 语言包 #11（新增 23 key） | 65 项通过，复核无差异 |
| 10:12 | 补丁 | ui-smoke 第二批：入驻码过期状态、目录值翻译、业务信息网格折行、onboarding-schema 镜像补全（206 key）、历史轮次提示 → operations-console-web 1274fefe（单服务回滚到 35b61f12）；语言包 operations-console #12 | 已替换 |
| 10:16 | 补丁 | ui-smoke 发现客户门户地址 / 人员保存全部 422（前端多送 `address.addressFormatCode`，client-master 可写清单缺该键），且保存失败仍显示「已保存」；client-fe 修复（不再发送该键、串行保存队列 + 429 退避、失败态、按错误码本地化、人员抽屉标签）→ client-console-web 4aaecccb（单服务回滚 `compose-rollback-patch.client-console-web.yaml` → 4659a92a）；语言包 client-console #3 | 已替换 |
| 10:19 | 补丁 | client-master → ba3f136f（草稿接受可选 `address.addressFormatCode`，存储值按地址国家由服务端推导；单服务回滚 `compose-rollback-patch.client-master-service.yaml` → 997f8bf8）；本机同步替换 | 已替换 |
| 10:21 | 补丁 | client-console-web → 9f85e61c（地址国家 ≠ 辖区国家时用 GENERIC 布局；回滚到 4aaecccb） | 已替换 |
| 10:27 | 补丁 | client-console-web → 14690d94（390px 顶栏截断、移动端撤回入口、中文列表分隔、上传检查中禁止继续并后台续接、422 字段不阻塞其它保存；回滚到 9f85e61c）；语言包 client-console #4（+2 key） | 已替换 |
| 10:30 | 补丁 | operations-console-web → 88ab111a（Client 详情显示责任 PayFac 名称、国家按语言显示名称；回滚到 1274fefe）；无新增文案 | 已替换 |
| 10:33 | 补丁 | ui-smoke 发现机构申请无法添加人员（client-master 要求 relation.effectiveFrom，前端未送，且 422 无 errors[]）→ client-console-web 34d3d6e3（新关系送当天日期、抽屉映射字段错误与通用提示；回滚到 14690d94）；语言包 client-console #5（+1） | 已替换 |
| 10:36 | 补丁 | client-master → 0af5b792（关系 effectiveFrom 缺省为当天、人员 / 关系失败一律返回 errors[]；回滚 `compose-rollback-patch.client-master-service.yaml` → ba3f136f）；本机同步替换 | 已替换 |
