# 英文用词裁决（主会话）

各模块报告中"拿不准的英文用词"的裁决；F9 按此统一，并回写 glossary.md。

| 来源 | 中文 | 裁决 | 说明 |
|---|---|---|---|
| M3 | 驳回 / 已驳回 | Reject / Rejected | 术语表原写 Returned，与"退回修改 Return for Changes"冲突；全局改为 Rejected（术语表同步） |
| M3 | 执行人 | Assignee | 与处理人同词 |
| M3 | 条件运算符 | 小写短语 equals / is one of / is at least / is at most … | 接受 |
| M3 | 排他网关 / 并行拆分 / 并行汇聚 | Exclusive Gateway / Parallel Split / Parallel Join；新条件节点默认名 Condition | 接受 |
| M3 | 待办页签 / 填写 / 会签 | To Do / Handled；Fill In；All must approve | 接受 |
| M3 | 进入待审批 / 转交；节点处理人 / 节点审批人 | Approval Opened / Reassigned；Node Assignees / Node Approvers | 接受 |
| M3 | 你（句中） | you（句中小写，句首 You） | 例："Waiting on you, 陈雪"；F9 修改 |
| M3 | 规则行 | When [trigger] then [type] | 接受 |
| M6 | 正常 | Active | 接受 |
| M6 | 已暂停（租户安全状态） | Suspended | 接受：安全处置语境；排期类"已暂停"仍为 Paused |
| M6 | 来源业务状态 / 操作记录 | Source Business Status / Activity | 接受 |
| M6 | 号段 / 回切 / 借贷记 | BIN Ranges / Roll Back / Funding Type | 接受 |
| M6 | 幂等 NONE / REQUIRED | Off / Required | 接受 |
| M6 | 关联员工数 / 审核任务 / authEpoch | Users / Review Case / Auth Epoch | 接受 |
| M4 | 驳回 / 已驳回（成本版本审批） | Reject / Rejected | 按 M3 全局裁决统一；F9 把 channel 中的 Return / Returned 改为 Reject / Rejected |
| M4 | 放行 / 拒绝（保留记录）（调用授权） | Allowed / Denied (record kept) | 接受：授权语境用 Allowed / Denied；风控决策仍为 Allow / Reject |
| M4 | 技术账号 / 协议配置 | Channel Account / Protocol | 接受 |
| M4 | 沙箱验证 / 需重新验证 | Sandbox Test / Needs Re-test | 接受 |
| M4 | 出口 / 必经节点结果 / 受控句柄 / 初始化项 | Exit / Required Node Results / Protected Handle / Initializer | 接受 |
| M4 | 配置写入 / 改绑账号 | Configuration Write / Account Rebind | 接受 |
| M4 | 调用方显示名 | Counterparty / Hosted Checkout / Payment Orchestration / Acquiring / Client Master Data | 接受 |
| M4 | 内容摘要 / 经营主体编号 | Content Hash / Operating Entity No. | 接受 |
| M2 | 待激活 / 校验未通过 / 影子运行 / 终态 | Ready to Activate / Checks Failed / Shadow Running / Terminal | 接受 |
| M2 | 人工复核 / 审核；拦截 / 放行（名单用途） | Manual Review / Review；Block / Allow | 接受 |
| M2 | 实时累计 / 名单命中；字段 / 常量 / 集合变量；去重主体 | Real-Time Aggregate / List Match；Field / Constant / Set Variable；Distinct Subject | 接受 |
| M2 | 内容摘要 | Content Hash | 与 M4 统一（M2 原为 Content Digest）；F9 修改 |
| M2 | 已退役 / 已退休 | Retired | 接受（key 分开） |
| M2 | 结账准备 / 支付尝试 | Checkout Preparation / Payment Attempt | 接受 |
| M2 | 编辑器面包屑"规则配置" | Rules | 与菜单名一致；F9 修改 |
| M2 | 风控点 / 决策点 | Risk Checkpoint / Decision Point | 照原文两个词分别译，不合并 |
| M2 | 列表分隔符"、" | ", " | 接受 |
| M5 | 开通项 / 可开通项 | Enablement / Capability | 接受 |
| M5 | 驳回（价目版本） | Reject | 按全局裁决；F9 把 commercial 的 Return / Returned / return 改为 Reject / Rejected / reject |
| M5 | SUSPENDED（合同） | Suspended | 接受 |
| M5 | 破标 / 无标准可比 | Below Standard / No Standard to Compare | 接受 |
| M5 | 口径 / 含税口径 | Basis / Tax Treatment | 接受 |
| M5 | 线下签署 / 历史免签 | Offline Signature / Exempt (Legacy) | 接受 |
| M5 | 换汇币对 / Partner 底价 | FX Currency Pair / Partner Buy Rate | 接受 |
| M5 | 保证金 / 滚动保证金 | Reserve / Rolling Reserve | 接受：此处为滚动保证金（比例 + 天数），不是 Client Security Deposit |
| M5 | 商业时点 | Commercial As-Of Time | 接受 |
| M1 | 驳回 / 已驳回（复核） | Reject / Rejected | 按全局裁决；F9 改 finance / recon 约 18 个 key；拒绝仍为 Reject |
| M1 | 科目 / 汇总 / 末级 / 余额方向 | GL Account / Summary / Leaf / Normal Balance | 接受 |
| M1 | 余额卡片"PENDING 待到期应付"等 | PENDING Maturing Payable 等 | 接受（代码 + 名称的原格式） |
| M1 | 期间名 | FY2026 P3 | 接受 |
| M1 | 已对平 / 挂账 / 待到账 / 在途 / 尾号 | Reconciled / Suspense / Awaiting Cash / In-Transit / ending 1234 | 接受 |
| M1 | 详情页"{对象}不存在" | No matching {object} found. | 避免句首小写；F9 修改 |
| M7 | 令牌模式 / 数据分级 / 原因码 CUSTOMER_VERIFICATION / 整个策略 | New Token Each Time / Same Value, Same Token；Standard Sensitive；Client Information Check；Entire Policy | 接受 |
| M7 | webhook DEAD / 密钥编号 | Abandoned / Signing Secret · No. N | 接受 |
| M7 | 发件地址 / 密钥（通知）/ 路由角色 | From Address / Credentials / Primary、Fallback | 路由角色作为标签用首字母大写；F9 修改 |
| M7 | 写入规则 / 静态密钥（Vault）/ 更换密钥 | Write Rule / Static Keys (Vault) / Replace Keys | 接受：存储访问密钥用 Keys，通知发送凭据用 Credentials，语境不同 |
| M7 | 已作废（上传）/ 修订 / 生效 | Voided / Revision / Active | 接受 |
| M7 | 片段 / 试运行 / 展开校验 / 脱敏选项 | Fragment / Trial Run / Expanded Validation / Hidden、Fully Masked、Keep Edges | 作为标签首字母大写；F9 修改 |
