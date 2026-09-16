# CCT 草稿写链的实施依赖核对

这是阶段 4 对[批准实施计划](./implementation-plan.md)的代码与实际数据库差异记录，不改变个人/组织页面和审核规则，不代表新增能力已经实现。当前状态与证据统一见[实现台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#client-console-onboarding-20260914)。

## 1. 已有只读能力不能直接承担创建申请

当前 CCT 有 Checkout 查询 API；[配置](../../../client-contract-service/client-contract-web/src/main/java/com/paytech/client/contract/configuration/CheckoutQueryConfiguration.java)只装配只读连接与 Mapper，[依赖门禁](../../../client-contract-service/pom.xml)仍禁止 Redis、补偿执行器和 XXL-Job。新增入驻命令必须按已批准的资源锁、事务与恢复规范调整实际消费者依赖；不能放宽所有禁止项或让现有 Checkout 开关顺带打开写接口。

[十表执行候选](./db/implementation/cct-local-execution.md)解决单份资料的物理结构和已有 Checkout 读取回归前置；它不解决下面的任务执行器缺口。

## 2. 创建成功必须同时留下开通恢复依据

[数据设计](./data-design.md#2-草稿身份与字段映射)要求创建申请、必要业务对象及投影任务在同一 CCT 事务提交。不能先返回成功，再尝试在内存或另一个事务创建任务；否则中途崩溃会留下无法自动恢复上传工作区的申请。没有处理器的任务登记也不是可运行恢复能力。

已冻结的旧二十列源保留 `execution_type`、`source_type`、`source_id`、`source_version`、`schema_version`、`payload_hash`，缺少 `sub_event_type`，不能承载现行 Foundation。目标必须符合[现行补偿指南](../../../agent-docs/COMPENSATION_EVENT_GUIDE.md#3-compensation_event-最小模型)的十五列合同；各环境切换情况按执行回执判断，不能通过填假版本或另写旧执行器绕过。

必须在开启 C04 之前同步补齐：

- CCT 自己的 PDManer、生成器、完整 Schema、模型测试和独立人工迁移候选，采用现行十五字段的本地补偿合同。保留真实 Claim 的 `lock_version`，不重新引入表单资料版本。
- 创建 Processor 在资源锁内开启短事务，原子写申请及现行任务身份；失败整体回滚。外部 Platform 查询/操作放在本地事务之外。
- 唯一 Handler 使用已落库的申请/Client/账号和生命周期事实恢复 Platform 投影；正确处理重复、延迟、终态及未知结果，不把可恢复事实或敏感资料复制进 Payload。
- 同事务回滚、丢失 afterCommit 唤醒、重复执行、租约接管、失败保留、Platform 暂时不可用和终态迟到执行的真实基础设施测试。

本机独立 SQL 执行和检查结果见[补偿单表记录](./db/implementation/cct-compensation-local-execution.md)及[实现台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#client-console-onboarding-20260914)。隔离 Test 模块可以承载真实 Foundation 的 Schema 合同探针；它不是业务 Handler，也不使部署包获得恢复能力。生产模块的依赖和开关仍须随真实写入口与唯一 Handler 一并接入。现有十表 SQL 和阶段 3 冻结字段投影保留原范围，不顺带迁移 Foundation 命令幂等 JAR、其他系统数据库或外部调度配置。

## 3. 接口实现的顺序与边界

先验证目标资料模型下的现有 Checkout 读取，再补齐上述写入依赖，完成 C04 创建/恢复、C05 Owner 资料查询、C06 部分保存。随后按批准计划连接人员/关系、材料、Risk 审核及 BFF 页面组合。

创建需要重新核验 CUSTOMER/CLIENT 账号归属；同账号跨个人/组织只占一个申请名额。保存区分遗漏与显式 NULL，仅接收批准字段；Industry/MCC、状态、申请人及版本等客户端伪造字段必须拒绝。Profile 首次批准后不可编辑，RETURNED 才可修正。Owner 查询只返回本系统事实；当前审核任务、上传就绪与页面动作组合仍由 BFF 查询各 Owner 后组装。

以上均是现行设计的落地条件，不因为已经有静态页面、Schema 文件或四项 HTTP 入站测试就登记为草稿创建/保存通过。

## 4. Platform 投影写端与恢复身份

[统一人类认证设计](../../../docs/superpowers/specs/2026-08-30-platform-unified-human-authentication-design.md)的 PLT-I17 定义了 `tenant_type + owner_ref_id + requested_by_account_id + source_version + payload_hash`，Platform 侧具体写端及来源查询合同见 [PLT-I17 实施合同](./platform-tenant-projection-contract.md)。已有认证 context 和 Tenant 管理查询不能冒充该能力；CCT 内部客户端与 Handler 须消费专用写端，以 loopback 协议 Simulator 验证暂时失败/未知结果，并分别测试两个 Owner 的本地事务。各侧实现与验证状态只见当前台账。

源版本属于 CCT Client Root 的业务历史和 Platform 投影合同，不能重新塞入补偿表，也不能用每次资料保存的 Application Root 版本替代。首次 Provision 与后续状态投影需按原始来源身份恢复；Status 先到时可重试，不能创建缺申请账号的 Tenant。同版本不同 Hash 冲突，旧版本不得回退状态，跳版本按既有合同补齐缺失事件。只从最新状态拼一个新请求会丢失中间身份，不能作为恢复方案。

PENDING Tenant 不签发工作区 Token；初始 Onboarding 角色和审批后 Owner 角色的真实授予由 Platform 完成。未接通该写端时，不把本地任务登记或模拟器返回成功描述为客户工作区已开通。

## 5. CCT 恢复实施细节

生命周期来源、CLIENT 安全审计目录、Hash 输入、处理顺序及默认关闭配置见[恢复合同](./cct-workspace-recovery-contract.md)。普通草稿保存不写 BCR 表单快照；C04/C06 已确认返回固定操作回执，成功后查询当前资料，见[响应决定](./draft-command-response-proposal.md)。实现和验证结果只从当前台账读取。
