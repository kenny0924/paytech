# CCT Commercial Term 合并实施计划

> **历史实施提示：** Commercial Term 四表结果仍有效；本文中的 V3 Wire 与当时整体统计已由 [实时资格与 Retention 延期实施计划](./2026-08-23-cct-realtime-eligibility-retention-deferral-implementation-plan.md)取代，当前使用 V4 且不含 Eligibility 证明。

> 设计依据：[CCT Commercial Term 合并与 Hash 精简设计](./2026-08-23-cct-commercial-term-consolidation-design.md)

## 目标

把 CCT Client Pricing / Partner Share 的 8 张物理表收敛为 4 张，在不合并业务语义的前提下删除内部派生 Hash，并同步 Commerce、Finance、需求目录和全局时序契约。

## 任务

1. 先添加会对当前 48 表模型失败的 Commercial Term 合并契约测试，确认 RED。
2. 更新 CCT 权威 Catalog、字段中文元数据、生成器索引/关系/图组和模型测试清单。
3. 运行生成器产生新的 `client-contract.pdma.json`，确认模板文件字节级未变，刷新实际统计、Structural Projection Digest 和 Model SHA。
4. 更新 CCT 物理决策、数据库导航、项目上下文和当前表数说明；历史实施计划只增加“已部分取代”提示，不改写当时结果。
5. 更新 Commerce 的结构化 Commercial Term 快照、Finance 的结构化来源规则字段，并删除两端四个 Pricing/Share 中间 Hash。
6. 更新 Requirements、全局时序和站点测试，使 CCT-I13/COM-I04/Finance 均使用 V3 原子 Hash与完整结构化条款。
7. 运行 PDManer 生成检查、全部模型测试、Requirements 测试/构建/Lint、JSON 解析和 `git diff --check`；PDManer 4.9.3 应用内检查仍保留为人工门禁。

## 全局约束

- 仅修改当前文档、生成器、测试和授权的 CCT PDManer 设计产物。
- 不创建 Java 服务、DDL、Migration、数据库、环境或部署资源。
- 不删除与本次 Commercial Term 无关的 Hash。
- 不提交 Git、不创建分支、不覆盖用户已有改动。
