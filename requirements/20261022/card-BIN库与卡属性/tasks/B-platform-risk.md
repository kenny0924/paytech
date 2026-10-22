# 卡 BIN-B：platform-support（CSV、国家区域、块 132）与 risk-decision

- 工作目录：`/Users/kenny/Desktop/Workspace/Code-MoR/Code-MoR-bin-plat`（嵌套在主工作区内）
- 公共规则：`/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261022/payment-3DS全链路/tasks/00-common.md`（必须先读）
- 设计权威：[technique.md](../technique.md) 第 3 节（3.5.3 Platform、3.5.5 Risk、3.5.7 BFF 路由清单、3.6 数据）、`db/platform-support/*`、`db/risk-decision/*`
- 报告：`tasks/B-platform-risk-report.md`

## 范围

1. platform-support：`StoredContentType` / `CompletedUploadVerifier` / `FormatFileScanner` 识别 `text/csv`（无 PDF/PNG/JPEG 文件头、合法 UTF-8、无 NUL）；`t_country_region` 表（PDManer、Schema、initial-data 种子 249 行）与 `GET /platform/v1/country-regions` 查询接口；CSV 扫描策略 `CARD_BIN_CSV` DML；`platform-support-initial-data.sql` 追加块 132（权限、Console Resource、9 条 BFF 路由；默认停用，激活 / 回滚语句写注释），同步 `console-resource-capabilities.json`。注意：文件存储第二段由另一会话在 `Code-MoR-fs2-platform` 开发，本卡只改上述 CSV 识别相关类，避免触碰该会话的文件（`FileAddress`、`FileErrorCode`、下载签发等）。
2. risk-decision：`PaymentAttemptRiskInput` 合同 v1 原地扩展 `issuerCountry`、`cardBrand`、`fundingType`；校验、冻结输入、`t_transaction_risk_assessment` 3 列（PDManer / Schema / DDL）、重放映射；变量 DML 900000000000093014—016（initial-data 与增量）。

## 本机 SQL（已授权）

本机 platform 测试库（`mor_platform` 自动测试库）执行国家区域 DDL；本机 risk 测试库执行 3 列 DDL 与变量 DML（以 test 配置确认库名）；按公共规则先备份。

## 测试

technique.md §3.8 第 1 行（risk）与第 3 行（platform）全部场景；块 132 编入后 Gateway Snapshot 编译相关测试通过。

## 不在本卡

card、payment、ops-bff / ops-web。
