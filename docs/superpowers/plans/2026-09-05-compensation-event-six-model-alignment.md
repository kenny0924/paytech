# Compensation Event Six-Model Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: use superpowers:executing-plans or superpowers:subagent-driven-development and migrate all six model families as one contract change.

**Goal:** 将六库旧 `compensation_event` 统一迁移为 Foundation 当前最小模型，避免逐服务产生第三种结构。

**Architecture:** 每服务本地 `t_compensation_event` 只用 `event_key + event_type + sub_event_type` 定位一个 Handler，默认从宿主权威表重建上下文；`payload_json` 仅为可选最小补充。MQ 业务信封的领域版本/Schema/Hash 是独立契约，不复制回本地可靠执行表。

**Tech Stack:** Java 25、MyBatis、MySQL 8.4、PDManer 4.9.3、Node.js 模型生成/Oracle、JUnit 5、手工 SQL。

**Spec:** [`agent-docs/COMPENSATION_EVENT_GUIDE.md`](../../../agent-docs/COMPENSATION_EVENT_GUIDE.md)

## Global Constraints

- 一次定义六库相同字段、类型、默认值、索引、注释和状态矩阵。
- 删除 `execution_type/source_type/source_id/source_version/schema_version/payload_hash` 前，逐事件证明可从不可变业务事实重建。
- 不新增通用 Inbox/Outbox、中央表或 Canonical Envelope。
- 不自动执行 Schema；数据迁移和回滚使用审核后的手工 SQL。

## Task 1: 锁定共享 Oracle

先在根模型测试中定义精确字段、`uk_comp_event_identity(event_key,event_type,sub_event_type)`、4 KiB Payload、无 CHECK/FK 和禁止旧字段断言，并确认当前六模型全部按预期失败。

## Task 2: 建立事件重建清单

逐服务列出每个 `event_type/sub_event_type`、`event_key` 指向的权威表、业务 MQ 信封来源、永久幂等位置和删除本地记录后的追溯证据。不能确定性重建的事件先补领域事实，不把完整上下文塞回 Payload。

## Task 3: 同步六套资产

更新六套 Manifest、Policy、场景和专项数据库文档，确定性再生成 PDManer；同步手工 DDL/数据迁移、DataObject、Mapper/Repository 和 Handler。保留各模型原有 ID 与无关结构。

## Task 4: 数据迁移

为现存行定义旧身份到新三元组的确定性映射、冲突检测、Payload 缩减、回滚和停机/兼容窗口。同一新身份映射出不同业务事实时必须中止迁移并报告，不能任选一行。

## Task 5: 验证

运行六模型 Oracle、确定性生成、Foundation/服务测试、真实本地 MySQL 并发与恢复用例，并完成人工 PDManer 检查。最后更新 `CURRENT_IMPLEMENTATION.md` 的 COMP-01。
