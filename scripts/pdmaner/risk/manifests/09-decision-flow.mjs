// RCO-17: 已批准的流程配置表；完整字段和注释与需求 DDL 同源。
import { defineTable, field, relation, uniqueIndex, accessIndex } from "../schema-dsl.mjs";

export const TABLES = Object.freeze([
  defineTable({
    key: "decision_flow", name: "决策流", comment: "保存决策流独立身份、当前配置入口及整体删除状态；不表达运行发布状态", audit: "custom",
    fields: [
      field("flow_id", "BIGINT", "NN", "决策流标识", "Risk 服务侧 Snowflake 生成的流程管理身份；创建后不变。", {"primaryKey": true}),
      field("decision_point_id", "BIGINT", "NN", "决策点标识", "本库决策点身份；与流程编码共同确定不可改变的业务身份。", {}),
      field("flow_code", "VARCHAR(64)", "NN", "流程编码", "决策点内永久唯一的大写流程编码；删除后不回收。", {"source": "Risk 流程配置管理", "characterSet": "ascii", "collation": "ascii_bin"}),
      field("current_flow_version_id", "BIGINT", "NN", "当前配置版本标识", "本流程当前最高业务版本；与新版本在同一事务切换，删除后保留。", {}),
      field("status", "VARCHAR(64)", "NN", "管理状态", "与运行发布无关的整体管理状态。", {"values": ["CONFIGURABLE", "DELETED"]}),
      field("version", "BIGINT", "NN", "管理并发版本", "从 1 开始；每次保存新配置或首次删除加 1；幂等回放不增加。", {"defaultValue": "1"}),
      field("deleted_by", "BIGINT", "N", "删除人", "来源 Platform Account 的永久账号标识，只保存跨系统身份引用；CONFIGURABLE 时为空，DELETED 时必填且不改写。", {}),
      field("deleted_time", "DATETIME(6)", "N", "删除时间", "整体删除的 UTC 微秒时间；CONFIGURABLE 时为空，DELETED 时必填且不改写。", {}),
      field("create_time", "DATETIME(6)", "NN", "创建时间", "本行在 Risk 数据库中创建的 UTC 微秒时间。", {}),
      field("update_time", "DATETIME(6)", "NN", "更新时间", "当前版本指针或删除状态最后改变的 UTC 微秒时间；成功回放不改变。", {}),
    ],
    indexes: [
      { ...uniqueIndex("uk_decision_flow_code", "decision_point_id", "flow_code"), comment: "保证业务唯一性：decision_point_id、flow_code" },
      { ...accessIndex("idx_decision_flow_list", "decision_point_id", "status", "update_time", "flow_id"), comment: "支持访问路径：decision_point_id、status、update_time、flow_id" },
    ],
    relations: [
      relation("decision_point_id", "decision_point", "decision_point_id", "n:1"),
      relation("current_flow_version_id", "decision_flow_version", "flow_version_id", "n:1"),
    ],
  }),
  defineTable({
    key: "decision_flow_version", name: "决策流配置版本", comment: "每次完整保存生成的不可变配置版本头；历史只读，不是运行发布版本", audit: "custom",
    fields: [
      field("flow_version_id", "BIGINT", "NN", "流程版本标识", "Risk 服务侧 Snowflake 生成的不可变配置版本身份。", {"primaryKey": true}),
      field("flow_id", "BIGINT", "NN", "决策流标识", "所属本库决策流管理身份；创建后不变。", {}),
      field("version_no", "INT", "NN", "业务版本号", "同一流程内从 1 开始连续递增；只在成功保存新配置时增加。", {}),
      field("flow_name", "VARCHAR(100)", "NN", "流程名称", "本次保存的流程名称快照，1 至 100 个 Unicode 码点；以后改名不回写。", {}),
      field("input_contract_code", "VARCHAR(64)", "NN", "输入合同编码", "保存时选定的 Risk 决策点输入合同。", {"source": "Risk 输入合同目录"}),
      field("input_contract_version", "INT", "NN", "输入合同版本", "保存时输入合同的准确正整数版本；规则集引用必须与其一致。", {}),
      field("created_by", "BIGINT", "NN", "保存人", "来源 Platform Account 的永久账号标识，只保存可信操作者身份，不复制账号资料。", {}),
      field("change_reason", "VARCHAR(500)", "NN", "变更原因", "本次完整保存的人工说明，1 至 500 个 Unicode 码点；不包含个人原值或凭据。", {}),
      field("create_time", "DATETIME(6)", "NN", "创建时间", "本行在 Risk 数据库中创建的 UTC 微秒时间。", {}),
      field("update_time", "DATETIME(6)", "NN", "更新时间", "不可变配置明细，始终等于 create_time。", {}),
    ],
    indexes: [
      { ...uniqueIndex("uk_flow_version_no", "flow_id", "version_no"), comment: "保证业务唯一性：flow_id、version_no" },
    ],
    relations: [
      relation("flow_id", "decision_flow", "flow_id", "n:1"),
    ],
  }),
  defineTable({
    key: "decision_flow_node", name: "决策流配置节点", comment: "保存每个配置版本的开始、规则集和结束节点；规则版本与原因直接可查，不保存运行结果", audit: "custom",
    fields: [
      field("node_id", "BIGINT", "NN", "节点标识", "Risk 服务侧 Snowflake 生成的版本内节点行身份；每次另存生成新行。", {"primaryKey": true}),
      field("flow_version_id", "BIGINT", "NN", "流程版本标识", "所属不可变流程版本，所有节点及连线必须处于同一版本。", {}),
      field("node_key", "VARCHAR(64)", "NN", "画布节点键", "版本内大小写敏感的节点键；同一编辑节点跨版本保留，删除再添加使用新键。", {"characterSet": "ascii", "collation": "ascii_bin"}),
      field("node_type", "VARCHAR(64)", "NN", "节点类型", "节点业务类型。", {"values": ["START", "RULE_SET", "END"]}),
      field("node_name", "VARCHAR(100)", "NN", "节点名称", "保存时的节点名称快照，1 至 100 个 Unicode 码点。", {}),
      field("rule_set_version_id", "BIGINT", "N", "规则集版本标识", "准确引用本库规则集版本；仅 RULE_SET 必填，其他类型为空；不追随最新版本。", {}),
      field("decision_code", "VARCHAR(64)", "N", "结束决定", "仅 END 必填，其他类型为空。", {"values": ["ALLOW", "REVIEW", "DENY"]}),
      field("reason_code", "VARCHAR(64)", "N", "拒绝原因编码", "仅 DENY 结束节点必填，其他情况为空。", {"source": "本库拒绝原因目录"}),
      field("reason_message", "VARCHAR(500)", "N", "拒绝说明快照", "仅 DENY 结束节点必填，保存时的内部拒绝说明；目录修改不回写，其他情况为空。", {}),
      field("position_x", "INT", "NN", "水平坐标", "编辑画布中的整数像素坐标，范围负 100000 至正 100000；HTTP 使用十进制字符串。", {}),
      field("position_y", "INT", "NN", "垂直坐标", "编辑画布中的整数像素坐标，范围负 100000 至正 100000；HTTP 使用十进制字符串。", {}),
      field("create_time", "DATETIME(6)", "NN", "创建时间", "本行在 Risk 数据库中创建的 UTC 微秒时间。", {}),
      field("update_time", "DATETIME(6)", "NN", "更新时间", "不可变配置明细，始终等于 create_time。", {}),
    ],
    indexes: [
      { ...uniqueIndex("uk_flow_node_key", "flow_version_id", "node_key"), comment: "保证业务唯一性：flow_version_id、node_key" },
      { ...accessIndex("idx_flow_node_reason", "reason_code", "flow_version_id", "node_id"), comment: "支持访问路径：reason_code、flow_version_id、node_id" },
    ],
    relations: [
      relation("flow_version_id", "decision_flow_version", "flow_version_id", "n:1"),
      relation("rule_set_version_id", "rule_set_version", "rule_set_version_id", "n:1"),
      relation("reason_code", "rule_reject_reason", "reason_code", "n:1"),
    ],
  }),
  defineTable({
    key: "decision_flow_edge", name: "决策流配置连线", comment: "保存版本内节点连线、显式出口顺序及受控条件树；一条连线一个条件，不保存求值结果", audit: "custom",
    fields: [
      field("edge_id", "BIGINT", "NN", "连线标识", "Risk 服务侧 Snowflake 生成的版本内连线行身份。", {"primaryKey": true}),
      field("flow_version_id", "BIGINT", "NN", "流程版本标识", "所属不可变流程版本；来源和目标节点必须同属此版本。", {}),
      field("edge_key", "VARCHAR(64)", "NN", "画布连线键", "版本内大小写敏感的稳定连线键；不以创建时间或坐标决定顺序。", {"characterSet": "ascii", "collation": "ascii_bin"}),
      field("source_node_id", "BIGINT", "NN", "来源节点标识", "本库同一流程版本的来源节点；只允许 START 或 RULE_SET。", {}),
      field("target_node_id", "BIGINT", "NN", "目标节点标识", "本库同一流程版本的目标节点；不能指向 START 或自身。", {}),
      field("edge_type", "VARCHAR(64)", "NN", "连线类型", "出口业务含义。", {"values": ["UNCONDITIONAL", "CONDITIONAL", "ELSE"]}),
      field("priority_no", "INT", "NN", "条件判断顺序", "CONDITIONAL 从 1 连续排列且同源唯一；ELSE 与 UNCONDITIONAL 固定为 0，两者不能同源共存。", {}),
      field("edge_name", "VARCHAR(100)", "NN", "连线名称", "运营填写的连线名称快照，1 至 100 个 Unicode 码点。", {}),
      field("condition_json", "VARCHAR(8192)", "N", "条件树", "CONDITIONAL 连线的受控条件树；schemaVersion=1 保留 source/values 历史，schemaVersion=2 使用统一操作数，UTF-8 最多8192字节；其他连线为空。", {}),
      field("create_time", "DATETIME(6)", "NN", "创建时间", "本行在 Risk 数据库中创建的 UTC 微秒时间。", {}),
      field("update_time", "DATETIME(6)", "NN", "更新时间", "不可变配置明细，始终等于 create_time。", {}),
    ],
    indexes: [
      { ...uniqueIndex("uk_flow_edge_key", "flow_version_id", "edge_key"), comment: "保证业务唯一性：flow_version_id、edge_key" },
      { ...uniqueIndex("uk_flow_edge_order", "flow_version_id", "source_node_id", "priority_no"), comment: "保证业务唯一性：flow_version_id、source_node_id、priority_no" },
    ],
    relations: [
      relation("flow_version_id", "decision_flow_version", "flow_version_id", "n:1"),
      relation("source_node_id", "decision_flow_node", "node_id", "n:1"),
      relation("target_node_id", "decision_flow_node", "node_id", "n:1"),
    ],
  }),
]);
