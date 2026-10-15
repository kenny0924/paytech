-- 需求：requirements/20261015/risk-评估结果查询与解释（阶段 4）。目标库：Risk（mor_risk）。
-- 性质：增量执行文件，与 PDManer 模型及完整 Schema（risk-compliance-schema.sql，85 表）中的同名表一致；不得重复执行。
-- 前置：库为本需求前的 84 表 Schema，且不存在 t_evaluation_flow_trace。
-- 回滚：DROP TABLE `t_evaluation_flow_trace`;（旧版本应用不读写该表，应用回退后也可保留）

CREATE TABLE `t_evaluation_flow_trace` (
  `trace_id` BIGINT NOT NULL COMMENT '评估流程轨迹的稳定业务标识。',
  `evaluation_id` BIGINT NOT NULL COMMENT '决策评估的稳定业务标识。',
  `step_no` INT NOT NULL COMMENT '从 1 开始的节点经过顺序；开始节点为 1，结束节点为最后一行。',
  `node_key` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '评估所用决策流版本内的节点键；流程版本由评估的发布得出。',
  `node_type` VARCHAR(64) NOT NULL COMMENT '节点业务类型。允许值：START、RULE_SET、END。',
  `rule_set_version_id` BIGINT NULL COMMENT '规则集节点引用的规则集版本；其他节点为空。',
  `matched_flag` BOOLEAN NULL COMMENT '规则集节点是否有规则命中，1 表示是，0 表示否，NULL 表示不适用。',
  `node_decision` VARCHAR(64) NULL COMMENT '规则集节点首个终态命中的决定或结束节点的结论；没有时为空。允许值：ALLOW、REVIEW、DENY。',
  `reused_flag` BOOLEAN NULL COMMENT '规则集节点是否复用同一规则集版本此前的执行结果，1 表示是，0 表示否，NULL 表示不适用。',
  `exit_edge_key` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '离开本节点所走连线在流程版本内的键；结束节点为空。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`trace_id`),
  UNIQUE KEY `uk_eval_flow_trace_step` (`evaluation_id`, `step_no`) COMMENT '保证业务唯一性：evaluation_id、step_no'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='按经过顺序保存一次评估实际经过的决策流节点、规则集节点结果与所走连线';
