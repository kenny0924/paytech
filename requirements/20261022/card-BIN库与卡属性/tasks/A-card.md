# 卡 BIN-A：card 服务、acquiring / counterparty 透出、commercial 规范值

- 工作目录：`/Users/kenny/Desktop/Workspace/Code-MoR/Code-MoR-bin`（嵌套在主工作区内）
- 公共规则：`/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261022/payment-3DS全链路/tasks/00-common.md`（必须先读）
- 设计权威：[requirement.md](../requirement.md)、[technique.md](../technique.md) 第 2、3 节、`db/card/ddl.sql`、`db/card/dml.sql`
- 报告：`tasks/A-card-report.md`

## 范围（technique.md §3.8 顺序 2 的 acquiring / counterparty、顺序 4 的 card、3.5.8 commercial）

1. card：4 张新表（PDManer 经 `scripts/pdmaner/generate-card-model.mjs` + additions 生成、`card-schema.sql`、test init DDL、`table-ownership.yaml`、新建 `card-initial-data.sql` 种子）；7 个运营接口（只允许 `operations-console-bff`，操作人取 `X-Gateway-Actor-Subject`）；`CARD_BIN_LIBRARY_IMPORT` 补偿 Handler（两遍：校验 + 哈希、分批写入、进度、重入）；激活 / 回切（锁 + `active_slot` + `expectedActiveVersionNo` 409 + 激活记录）；查询服务（8→7→6 位，一条 SQL，不缓存）；交易工具响应 `cardAttributes` 快照；已存卡列表 `cardBrand`；Platform 内部文件客户端（`getFile`、下载地址）与国家区域查询客户端（接口合同按 technique.md 3.5.3，Platform 实现由卡 BIN-B 完成，本卡测试用 DTO Mock 端点）。
2. acquiring、counterparty：Card DTO 新字段，结账已存卡与 Client API 已存卡列表透出 `cardBrand`（对外 API 文档同步）。
3. commercial：`CommercialCodes.CardBrand` 补 MADA、DISCOVER、JCB、DINERS。

## 本机 SQL（已授权）

本机 card 测试库执行 `db/card/ddl.sql`（种子 DML 视测试需要）；按公共规则先备份。

## 测试

technique.md §3.8 第 4 行 card 的全部场景 + 第 2 行 acquiring、counterparty。

## 不在本卡

payment、risk-decision（另有卡）、platform-support（卡 BIN-B）、ops-bff / ops-web（卡 BIN-C）。
