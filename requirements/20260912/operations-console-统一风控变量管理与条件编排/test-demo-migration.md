# test 示例统一变量迁移

本文保留阶段 3 确认的迁移方案；实际执行、保存身份与验证状态统一见[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#rco19-variables-20260912)及其发布证据。模板不可直接执行或重跑。目标环境与 SQL 前置见 [db/README.md](./db/README.md)。

## 精确目标

| 对象 | 管理身份 | 前次读取的版本 | 改动 |
|---|---|---|---|
| DEMO_FLOW_AMOUNT_CHECK | 224578384506753024 | 224578384481587200 / 第1版 | INPUT_NET_AMOUNT > DEMO_AMOUNT_THRESHOLD；动作仍为null |
| DEMO_CHECKOUT_BRANCH_FLOW | 224578384930377728 | 224578384930377729 / 第1版 | matched引用、币种变量比较，并引用本次保存的准确规则版本 |

这些是已有读取证据，执行前必须再读取当前根、版本、Hash/CAS与规则/图内容。若出现用户编辑，只比较并展示差异，不覆盖新修改。原创建证据见 [user-demo.json](../operations-console-决策流配置/assets/stage7/user-demo.json)。

## 待执行的具体操作

1. 确认本期3应用和Schema/变量注册已部署，9新增Route/菜单/权限正确启用；通过真实会话授权读取，不在脚本记录凭据。
2. 查询 INPUT_NET_AMOUNT、INPUT_CURRENCY、NODE_MATCHED、DEMO_AMOUNT_THRESHOLD、DEMO_STANDARD_CURRENCY 的定义及币种关联。阈值应为MONEY/INPUT_CURRENCY、初始8000.00000000；标准币种初始USD。若已被用户维护成其他值，先列出差异，不无条件执行初始化DML覆盖。
3. 从[保存模板](./contracts/test-demo-template.json)绑定本次读取的 CURRENT_RULE_VERSION_ID/CURRENT_RULE_CONTENT_HASH，以新的逻辑幂等键保存规则新版本。只有目标条件改成变量；规则identity/优先级/启用值/空动作保持。读回准确新版本并检查原版仍可读。
4. 重新读取当前flow根；用本次新规则版本ID替换金额检查节点的准确引用，把两条条件改成schemaVersion2：NODE_MATCHED = literal true，INPUT_CURRENCY != DEMO_STANDARD_CURRENCY。原5节点/4连线、优先级1/2/ELSE、START/END类型及位置保持。
5. 更新名称/连线/文案为“配置阈值/标准币种”，原 reasonCode=DEMO_AMOUNT_LIMIT 保留，不把原因字典同步升级为文案模板。先静态validate，再用本次读取的flow基准/CAS和新逻辑幂等键保存；失败不重复创建规则版本，可复用已保存规则ID重试同一flow命令。
6. 读回规则、流程、两者历史/使用关系；记录新版本ID/内容Hash/根CAS。原“大额拒绝；其余非标准币种人工审核；其他放行”的初始含义保持。这是配置读写/静态校验，不声称已执行。
7. 对这两个专用DEMO常量分别以CAS临时改为10000.00000000/EUR；不保存规则/flow，再读当前投影应显示新值、原版本ID/内容Hash不变，原literal仍不变。
8. 以最新CAS恢复8000.00000000/USD并核对。恢复失败必须明确记录现值/冲突交给用户，不能声称完成迁移或偷偷以最新CAS覆盖他人期间变更。

模板包含 `${...}` 占位符，明确不可直接提交；日期、原示例ID不当作可反复重用的幂等键。新增规则集/流程版本号由Risk分配，不硬填“第2版”。不会删除原历史或全库改写CONTEXT/LITERAL。

金额规则仍空动作，所以它的NODE_DECISION按新方案为null；示例的拒绝分支继续使用matched=true。本次另加的“处理结果=REJECT继续规则集”用独立配置验收样例验证，不能为了演示该功能把原金额规则改成DENY动作。

## 记录与恢复

仅在当前台账登记实际执行的环境、版本、保存回执与恢复结果；本文件保留操作方案。若flow保存前失败，已新建规则版本可保留为未引用配置，记录ID后继续原迁移，禁止直接删历史。若功能入口失败，关闭新写入口并前滚修复Reader/页面，不将新格式数据交给旧程序读取。恢复原业务条件需要通过管理API保存明确的新版本，不UPDATE既有不可变行。
