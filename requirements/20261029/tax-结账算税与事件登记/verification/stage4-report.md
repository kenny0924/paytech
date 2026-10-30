# 阶段 4 报告：tax 结账算税与事件登记

- 日期：2026-10-29
- worktree：`/Users/kenny/Desktop/Workspace/Code-MoR-tax`（基于 `e3e0c12f`，detached），**未提交、未推送、未部署**
- 本机 SQL（已授权）：`mor_tax` 在 `mor-local-mysql-1:13306` 上 root 重建为 16 张表并写入初始化数据（回滚验证一次后重新应用）；`mor_acquiring` 执行前滚迁移（回滚在空结构上验证一次后重新应用）。其他本机库结构未改。
- 分项报告：[W1 tax](./stage4/W1-report.md)、[W2 billing / account / ledger](./stage4/W2-report.md)、[W3 acquiring](./stage4/W3-report.md)、[W4 ops-bff / web](./stage4/W4-report.md)、[tax-api 交接](./stage4/tax-api-ready.md)、[任务卡](./task-card-stage4.md)

## 1. 验证结果

| 范围 | 命令 / 方式 | 结果 |
|---|---|---|
| 全 reactor | 主会话 `./mvnw -o test-compile`（不 install、不跑测试） | 通过，0 错误 |
| tax | 模块测试（`package`，本机 MySQL / RabbitMQ） | 62 / 62 |
| acquiring | 模块测试（`package`，本机 MySQL） | 916 / 916 |
| billing / account / ledger | 模块测试 | 142 / 142、68 / 68、332 / 332 |
| operations-console-bff | 模块测试 | 2472 / 2472 |
| operations-console-web | `npm test`、build、i18n 导出 | 1614 / 1614，构建与导出通过（`tax` 命名空间 211 键） |
| arch-rules | `TableOwnershipAutoTest` | 7 / 7 |

## 2. 验收标准覆盖

| AC | 覆盖 | 说明 |
|---|---|---|
| AC-01 | tax、acquiring | SA 15%：100 → 15 / 115，按最终报价付款 |
| AC-02 | tax 接口 | 含税反算 86.96 / 13.04；AE 5%、EG 14% |
| AC-03 | acquiring | 建单暂估、资料提交重算、只有最新 FINAL + READY 可付，暂估或过期被拒 |
| AC-04 | tax、acquiring、billing、ops | PSP 与 CLIENT 来源 → NOT_MANAGED；无事实、无 SELLER_TAX |
| AC-05 | tax、acquiring | BLOCKED 含原因码，订单不可付；未知税类不再 400 |
| AC-06 | tax、acquiring | 多行含折扣，逐行尾差 |
| AC-07 | tax | 正式事实与底稿唯一，重复投递幂等 |
| AC-08 | tax | 事件税额与冻结计算不一致 → 死信 |
| AC-09 | billing、account、ledger | 逐组件 SELLER_TAX、本金减税、合并一笔 TAX_PAYABLE |
| AC-10 | 结构保证，**待阶段 5 端到端** | billing 不调用 tax；tax 停机时 billing 照常入账、tax 恢复后经消息重试补登记，需本机联调实测 |
| AC-11 | tax | 税票分号唯一、并发不撞号、失败重试不换号、不含个人资料 |
| AC-12 | acquiring、billing、tax | 存量订单读取与支付回调正常，不导入正式事实 |
| AC-13 | ops-bff / web | 原型 16—21 六个状态 |
| AC-14 | ops-bff、acquiring | 运营下载经授权；Customer API 返回重算金额与阻断原因 |

## 3. 阶段内裁决与对方案的偏离（均已记录在 technique.md 或分项报告）

| 编号 | 内容 | 处理 |
|---|---|---|
| X-1 | acquiring 计算表增加 `expires_at`，门禁不回调 tax | 批准，已写入 technique 8.2 |
| X-2 | 事件载荷定义在 acquiring core 领域类（api 模块不发布载荷类） | 批准，已写入 technique 11.1 |
| X-3 | CLIENT 来源 OE 无商业关系快照 → `taxSubjectPartyId` 为空 → NOT_MANAGED（`TAX_RESPONSIBLE_UNRESOLVED`），可正常付款；有快照却缺 TAX_RESPONSIBLE 才 502 | 主会话裁决（不做回归），已写入 technique 9.1 |
| X-4 | 运营下载接口挂在订单下：`POST /tax/v1/orders/{orderNo}/documents/{documentId}/download`，Gateway 对外 `/api/operations/tax/v1/...` | 批准，已写入 technique 10.3 |
| X-5 | tax-api 补充：行 `description`（取订单行 `item_name`）、订单查询 `managedBy`、`notManagedReason`、阻断路径行号从 1 起 | 为运营页面名称展示补充 |
| X-6 | 税类改为自由文本，未知税类 → BLOCKED（`UNKNOWN_TAX_CATEGORY`），不再 400 | 按阶段 2 S-13 纠正 |
| X-7 | 运营页名称聚合：client-master 管理端详情接口只校验会话、不校验查看权限，BFF 先用操作人会话向 Platform 确认 Client / OE 详情页权限再查名称，否则显示 ID | 主会话裁决（BFF 不得借聚合扩大访问） |
| X-8 | `t_tax_registration.registered_name` 作为税票卖方名称来源；规则包增加 `reporting` 区块（截止日规则） | 接受 |
| X-9 | billing 税行税基 = 税额、税率为空（事件不带组件税基）；`taxType` 写入证据 | 接受；税基可从 tax 计算追溯 |
| X-10 | SaveConsumer 对 CLIENT 来源订单回读一次 `GET /tax/v1/calculations/{id}` 取冻结参与方 | 接受（同一流程本就调用 tax，无新增失败点） |
| X-11 | billing 新增只改注释的变更文件 `2026-10-29-billing-fact-ref-tax-calculation.sql` | 保留，阶段 6 一并执行 |
| X-12 | tax 从 Finance PDManer 生成器移除（`scripts/pdmaner`） | 按 S-11 |

## 4. 遗留项

需要用户决定：

- **D-AR 阿拉伯语税票。** tax 自写最简 PDF，只支持 ASCII，沙特 / 埃及税票的阿拉伯语无法渲染。方案 A（推荐）本需求保持英文版并标注待补；方案 B 引入支持复杂文字的 PDF 库与阿拉伯语字体（新增依赖需技术栈登记）。

阶段 5（本机联调）：

- AC-10 端到端（tax 停机时付款、恢复后补登记）。
- 真实浏览器检查：en / zh-CN × 1440 / 390，重点是进度条窄屏、两栏折叠、下载失败提示条。
- 本机 e2e 演示数据与脚本中的税类 `STANDARD` 会被判为未知税类而阻断，需改为 `SAAS_AUTOMATED` 或在演示规则包登记别名（S-13）。

阶段 6（mor-test 发布准备）：

- `mor_tax` 在 mor-test 由旧 24 表经重建脚本切换；`tax-initial-data.sql` 的 AE001 Party 需替换为 mor-test 实际值（本机演示为 `880000000702`，与 acquiring MOCK 配置一致，需核对）。
- Platform 注册：分配注册块号（`platform-registration.sql` 中为占位、未分配无法执行）；`tax` 语言包命名空间；文件写入路由（确认默认私有策略允许 PDF）；platform-support 镜像包含新的页面能力清单；Gateway 快照先离线编译。
- 部署配置：ops-bff `OPERATIONS_TAX_BASE_URL`；tax-service 的 RabbitMQ、XXL 执行器、Platform 地址、`internal-auth` 受众白名单；Nacos 可发现。
- 发布顺序：Platform 注册 → tax → billing / account → acquiring（先迁移再上镜像）→ ops-bff → ops-web。

范围外发现（建议另立需求）：

- client-master 管理端 Client 详情与 OE 详情接口只校验会话、不校验查看权限，持有任意运营会话即可读取完整资料。
- CreateCheckout 的 `preparationStatus` 仍写死 `READY`，与 OpenAPI 的 `PENDING` 不一致（原有问题，本需求只纠正 `amountStatus`）。
- `scripts/pdmaner` 中 payout、client-master 模型漂移与 `acquiring-scenario-doc.test.mjs` 表清单过期为既有失败。

过程记录：

- W2 首次查本机库权限时误选 `mor-test-mysql-1` 容器，root 登录被拒、未执行语句；任务卡已加容器约束。
