# Physical CHECK and FOREIGN KEY Removal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: use superpowers:executing-plans or superpowers:subagent-driven-development; remove no physical constraint before equivalent application protection and tests exist.

**Goal:** 将现有 DDL/数据库中的业务 CHECK 与 FOREIGN KEY 安全迁移为应用校验、事务并发保护和自动测试，同时保留主键、唯一索引、普通索引、NOT NULL、模型逻辑关系和 Sidecar 契约。

**Architecture:** PDManer/Sidecar 表达逻辑关系与业务不变量；Owner 应用在写入口验证并用本地事务、CAS/条件更新、唯一索引或必要局部锁保证并发正确性（六个核心系统为 Java，Vault 为 Go）；审核后的手工 SQL 最后移除物理 CHECK/FK。

**Tech Stack:** PDManer 4.9.3、MySQL 8.4、Java 25/MyBatis/JUnit 5、Go 标准 `database/sql` 测试、仓库自有 `local-environment`、手工 SQL。

**Spec:** [`agent-docs/DATABASE_DESIGN_GUIDE.md#11-索引约束与关系`](../../../agent-docs/DATABASE_DESIGN_GUIDE.md#11-索引约束与关系)

## Global Constraints

- 不删除主键、唯一索引、普通索引或 NOT NULL。
- 不因删除 FK 允许跨库访问、孤儿引用或任意级联删除。
- 不直接修改共享/生产数据库；每库单独审核执行顺序、预检查和回滚。
- Vault 当前实际约束也遵守本流程，不隐式设为永久例外。

## Task 1: 逐库库存

从当前 Schema SQL 和 `information_schema` 生成 CHECK/FK 清单，关联到模型 Sidecar、Owner 写入口和反例。区分“只在历史文档提到”和“当前 DDL/物理库真实存在”。

## Task 2: 先补应用保护

对枚举/组合、同租户归属、关联存在性、状态迁移和删除/失效引用分别实现入口校验；并发不变量使用条件更新、唯一索引和必要局部事务锁。未知/不一致数据失败关闭，影响行数异常有明确错误。

## Task 3: RED/GREEN 测试

先编写绕过顺序、并发竞态、错误归属、非法状态和删除引用测试并确认失败；完成应用保护后，在真实 `local-environment` MySQL 上通过。测试不得依赖数据库 CHECK/FK 报错作为唯一断言。

## Task 4: 数据预检与手工 SQL

执行只读孤儿/非法组合扫描；非零结果必须先制定数据修复审批。为每库编写显式约束名的删除 SQL、执行顺序、锁/停机影响、验证查询和回滚方案，由操作员手工执行。

## Task 5: 文档与证明

将物理决策中的 CHECK/FK 改称逻辑契约，保留 Sidecar 名称供模型和测试追踪。运行 `SHOW CREATE TABLE`/`information_schema`、模型门禁和应用测试，再分别更新实现、验证和部署状态。
