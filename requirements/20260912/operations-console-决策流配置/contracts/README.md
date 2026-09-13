# 决策流合同样例

状态：阶段 3 设计稿，尚未实现。

- save-flow.schema.json：完整保存的结构；N04 对未完成字段只做类型/形状校验，然后生成定位问题，不能把它当作保存成功。
- save-flow.example.json：6 节点、6 连线、两个条件出口有明确顺序。所有规则版本 ID 与拒绝原因均为合成引用，未插入数据库；真实请求前须替换为同点、同输入合同且符合资格的记录。
- 规则未配置动作时使用现有 CONFIGURATION 格式的 action=null、additionalActions=[]；不新增规则类型或模式字段，不生成运行命中结果。

JSON Schema 不承担无环、可达、出口互斥、字段域、历史引用资格、CAS 或权限校验；完整约束见 [api.md](../api.md) 和 [data.md](../data.md)。
