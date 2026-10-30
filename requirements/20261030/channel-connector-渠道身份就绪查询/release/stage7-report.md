# REQ-02 · 阶段 7 mor-test 发布与验收报告（2026-10-30）

> 用户授权："继续"（接受 2 条预期暂停、`GOLIVE_MID_READY` 同批登记、进入阶段 7）。执行者：主会话。协调：明细账运维（design-discuss-cb）确认不再发 ops-web；REQ-06（design-discuss-5f）确认部署期间不碰 mor-test。

## 1. 发布（18:45—19:01Z，按 [runbook](./runbook.md)）

| 步 | 结果 |
|---|---|
| 0 复核 | NO DRIFT；env 8 个覆盖逐键相同；`plan` 10 / 10（4 个现网镜像 = 发布前，新接口 404） |
| 1 巡检 | 与阶段 6 一致：2 × WILL_SUSPEND、2 × STAYS_ENABLED_WITH_REMINDER、8 × 不变（`precheck/…-stage7.out`） |
| 2 client-master | `df13cc47` healthy，日志 0 ERROR |
| 3 channel-connector | `f330c4ae` healthy，日志 0 ERROR |
| 4 commercial | `2be844e7` healthy；就绪查询接口与经营分类接口 200 |
| 5 ops-web + 语言包 | `bbc03d33` healthy；运营语言包 **#31**（commercial 新增 17、基准变更 3、废弃 2，zh 导入 20；82 项核对通过） |
| 6 全量重查 | XXL 任务 26（`scope=ALL`）触发 200；18:49:11Z DEMO-CL-001 APPLE_PAY、DEMO2-CL-101 CARD → `SUSPENDED_BY_PREREQUISITE`；DEMO-CL-001 / DEMO2-CL-103 CARD 仍 ENABLED（AED 提醒）；其余 8 条不变——与预测完全一致 |
| 8 data-query | `GOLIVE_MID_READY` 登记 ACTIVE（版本 1，数据源 `MOR_CHANNEL_CONNECTOR_REPLICA`），登记前经 data-query 真实执行路径试跑 8 组全符合 |

## 2. 验收

| 项 | 结果 |
|---|---|
| `api` | **57 / 0**（门禁结论、"还差什么"原因与参数、partial 场景、就绪查询、经营分类、GOLIVE 模板 6 组正式执行） |
| `check` | **15 / 0**（2 条预期暂停恰好为预测集合） |
| 浏览器 | **44 / 0**：zh-CN / en × 1440 / 390，管理员与无 MID 菜单账号；"还差什么"渠道身份（partial 绿勾 + 提醒、APPLE_PAY 红叉 + 原因）、去配置 MID 跳转并按 AE001 预填、无 MID 菜单不显示链接、明细账运维 / MID / 合同页回归；控制台 0、HTTP 错误 0、原始键 0、溢出 0 |
| 收尾 | 合成运营账号 `241996993830928384` 已停用；本机状态文件已删 |

## 3. 发布中发现并修正

1. **`GOLIVE_MID_READY` 在 data-query 真实执行时 500**：data-query 以预编译参数绑定执行，MySQL `JSON_TABLE` 不接受绑定参数（阶段 3 / 5 / 6 均以字面量替换直连库试跑，未覆盖绑定路径）。改为 `WITH RECURSIVE` 按逗号拆分列表，逻辑不变；已同步 `db/data-query-templates.sql` 与登记材料 `sql/03-…`，plan 14 / 14、apply 试跑 8 / 8 后登记。
2. 验收脚本三处脚本缺陷（非产品问题）：浏览器断言把"不适用"行也要求含 OE / MCC；`req02_data_query.py` 的 `body()` 只取 `SELECT` 起的正文、COALESCE 处数写死 3、试跑行按对象解析（实际为列数组）；`req02_acceptance.py` GOLIVE 段同样的行解析。均已修正后重跑。

## 4. 现网版本

client-master `sha256:df13cc47…`（req02-20261030a）、channel-connector `sha256:f330c4ae…`（a）、commercial `sha256:2be844e7…`（b）、ops-web `sha256:bbc03d33…`（b，= plops 发布树 + 本需求）；运营语言包 #31；data-query 模板 `GOLIVE_MID_READY` v1。回滚：倒序 `compose-rollback.*.yaml`，模板置 INACTIVE。

## 5. 遗留

- AC-07（MID 关闭 → 暂停 → 恢复）、AC-09（Client 无 MCC）、AC-10（channel-connector 不可用）只由 auto 测试覆盖，未在 mor-test 演练（会改渠道配置或造故障）。
- DEMO2-CL-101 CARD、DEMO-CL-001 APPLE_PAY 暂停属预期（运行期本就选不到 MID）；补 MID 能力后自动恢复。
