# Client Console 已交付功能收尾 — 交付记录

## 适用阶段

合并安排（2026-10-17 随阶段 1 确认）：1 → 2+3 → 4+5 → 6+7 → 8，每个合并点停下等待确认。

## 确认记录

| 阶段 | 日期 | 成果 | 用户原意 | 获准的下一步 |
|---|---|---|---|---|
| 1. 需求评审与范围确认 | 2026-10-17 | [requirement.md](./requirement.md) | “可以”：范围 F-01—F-03、AC-01—07 确认；Q2 不清理旧 PENDING Client、Q3 回填取最早 APPROVED 申请更新时间、Q4 合并阶段；Q1 收件地址未提供，F-01 待用户提供已验证邮箱后再做 | 阶段 2+3（F-02、F-03 的差异与设计） |
| 2+3. 差异与方案 | 2026-10-17 | [technique.md](./technique.md) | “继续”：确认方案；D1 只写 Client 主体资料、D2 test 不发事件、D3 只替换 test CCT | 阶段 4+5 研发与本机验证（授权本机 `mor_cct` 回填、替换本机 CCT） |
| 4+5. 研发与本机验证 | 2026-10-17 | 见下文“阶段 4+5” | “可以”：接受结果，并授权阶段 6+7 | 阶段 6+7 test 发布与验收 |

## 动作授权

| 动作 | 状态 |
|---|---|
| 本机 `mor_cct` 执行回填 DML、替换本机 CCT | 2026-10-17 用户“继续”授权（阶段 4+5） |
| 本地提交（只含本需求文件，不推送）；test `mor_cct` 备份 `t_party_profile` 后执行回填 DML；只替换 test CCT；test 跑 `developer_browser.mjs`、`workspace_flow.py`、`team_flow.py` 并写入验收数据 | 2026-10-17 用户“可以”授权（阶段 6+7） |
| 提交、推送 | 未授权 |

## 当前阶段与下一确认点

阶段 6+7 进行中；F-01 等待用户提供 SES 已验证的收件地址。

## 实现 / 验证 / 部署状态

### 阶段 4+5 研发与本机验证（2026-10-17，未提交）

| 项 | 结果 |
|---|---|
| F-03 代码 | `PartyProfileMapper.markApproved` / `PartyProfileRepository.markApproved`（只填空值，行数必须为 1）；`ClientReviewOutcomeServiceImpl.conclude` 在激活分支同一事务写入 Client 主体资料批准时间；工作区摘要 `approvedAt` = `COALESCE(f.approved_time, 最早 APPROVED 申请 update_time)`；`ClientReviewConfiguration` 装配 |
| F-03 测试 | `ClientReviewResultListenerTest` 补断言（批准后等于结论时间、重复投递不变、退回 / 拒绝为空）；`ClientSummariesTest` 新增 `test_edgeCase`（批准后申请再更新日期不变；存量空值退回申请时间）。CCT 全量 **53/53** |
| F-03 回填 | [db/dml.sql](./db/dml.sql)；本机 `mor_cct` 执行两次：执行前 28 个已批准 Client 为空，执行后 0；第二次无变化；受影响 profile_id 已导出到会话 scratchpad 备回滚 |
| 本机全链路 | 替换本机 CCT（scratchpad 制品）；`workspace_flow.py` **53/53**，新批准 Client 的 `approved_time` 与申请结论时间、首页 `approvedAt` 三者一致（`2026-10-17T10:34:29.211895Z`）；`team_flow.py` **123/123**；CCT 日志 ERROR 0 |
| F-02 | `developer_browser.mjs` 增加 `E2E_TARGET=test`（28443 入口、`example.com` 端点新建后停用、三项事件步骤记为 SKIP 且不计入通过数）；本机回归 **12/12**，行为不变 |
| 未覆盖 | F-02 test 实跑与 F-03 test 部署在阶段 6+7；F-01 待邮箱 |
