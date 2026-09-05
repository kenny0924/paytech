# Vault V1 Consumer Model Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: use superpowers:executing-plans or superpowers:subagent-driven-development; do not change a model before its consumer contract and migration assertions are written.

**Goal:** 将六系统对历史 Vault 能力的依赖逐个收敛到当前 Protect/Reveal V1，或为确有消费者的最小能力提出单独 V2 设计。

**Architecture:** Vault V1 保持 Go Server + Java Client、三表和 Protect/Reveal。每个敏感字段由业务 Owner 证明 Reveal、等值查询、展示、完整性/版本或跨接收方交付消费者；V1 能满足的只保存 opaque Token，不能满足的进入按消费者分组的 V2 设计。

**Tech Stack:** Go 1.27.1、Java 25 `@HttpExchange` Client、MySQL 8.4、PDManer 4.9.3、手工 SQL。

**Spec:** [`agent-docs/decisions/2026-09-05-document-governance-and-conflict-resolution.md#d2-vault-v1-的正式实现是-go-server--java-client`](../../../agent-docs/decisions/2026-09-05-document-governance-and-conflict-resolution.md#d2-vault-v1-的正式实现是-go-server--java-client)

## Global Constraints

- 不新增默认 Token 套餐，不用普通 SHA 代替低熵敏感值等值查询。
- 不在业务能力未替代前直接删除旧字段。
- API、策略、模型、应用事务、数据迁移和安全反例必须同一设计闭环。
- 不把 local/test AES、Caller Header 或合成 smoke 宣称为生产 KMS/强身份/业务接入。

## Task 1: 建立消费者矩阵

逐字段列出 Owner、原值分类、当前字段、写入者、Reveal、等值查询、脱敏展示、版本/完整性、跨系统交付、Retention 和删除消费者。至少覆盖 CCT 联系方式、Commerce Consumer、Finance Tax/Bank、Risk 调查资料与 Platform 登录/通知标识。

## Task 2: 分流 V1 与 V2

对只需 Protect/Reveal 的字段设计 V1 Token-only 迁移；对等值查询、展示、版本或跨接收方交付分别证明不能由业务 Owner 的批准派生事实、Stable Token 或受控同步接口承担后，再提出最小 V2 能力。不同消费者不得被捆成一个“大而全 Vault V2”。

## Task 3: 先写门禁，再改模型

为每个系统增加会因当前旧字段/旧 API 假设而失败的 Manifest/Policy/文档测试；冻结期望字段和禁止字段。随后同步系统文档、场景、PDManer、手工 SQL、DataObject/Mapper/Repository 和集成测试。

## Task 4: 迁移与兼容

说明旧 Token/派生值如何读取、回填、双读/切换和回滚；完整 Token 不进入日志、MQ、BCR 或补偿 Payload。生产切换前完成 KMS、强服务身份、最小权限、审计、备份/恢复和合规评审。

## Task 5: 验证和状态更新

运行六系统模型门禁、Vault `verify.sh`、受影响服务测试和 PDManer 4.9.3 人工检查。分别更新实现、验证和部署状态，不用一次本地测试关闭生产项。
