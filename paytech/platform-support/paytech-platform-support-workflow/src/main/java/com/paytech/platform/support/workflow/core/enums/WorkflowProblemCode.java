package com.paytech.platform.support.workflow.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 流程发布校验与动作配置校验的问题码。
 *
 * <p>保存草稿时返回问题列表但不阻止保存；发布时有任何问题即拒绝发布。</p>
 */
@Getter
@RequiredArgsConstructor
public enum WorkflowProblemCode implements CodeEnum {

    /** graph_json 超过 48 KiB。 */
    GRAPH_TOO_LARGE("GRAPH_TOO_LARGE"),

    /** start_form_json 超过 12 KiB。 */
    START_FORM_TOO_LARGE("START_FORM_TOO_LARGE"),

    /** 节点超过 100 个。 */
    TOO_MANY_NODES("TOO_MANY_NODES"),

    /** 单个节点的动作超过 20 个。 */
    TOO_MANY_ACTIONS("TOO_MANY_ACTIONS"),

    /** 节点 key 为空或格式不合法。 */
    NODE_KEY_INVALID("NODE_KEY_INVALID"),

    /** 节点 key 重复。 */
    NODE_KEY_DUPLICATE("NODE_KEY_DUPLICATE"),

    /** 任务节点或排他网关缺少名称。 */
    NODE_NAME_MISSING("NODE_NAME_MISSING"),

    /** 节点带有其类型不允许的配置。 */
    NODE_CONFIG_NOT_ALLOWED("NODE_CONFIG_NOT_ALLOWED"),

    /** 连线 key 为空或格式不合法。 */
    EDGE_KEY_INVALID("EDGE_KEY_INVALID"),

    /** 连线 key 重复。 */
    EDGE_KEY_DUPLICATE("EDGE_KEY_DUPLICATE"),

    /** 连线的起点或终点不是已存在的节点。 */
    EDGE_ENDPOINT_UNKNOWN("EDGE_ENDPOINT_UNKNOWN"),

    /** 两条连线的起点与终点相同。 */
    EDGE_DUPLICATE("EDGE_DUPLICATE"),

    /** 开始节点不是恰好一个。 */
    START_COUNT("START_COUNT"),

    /** 没有结束节点。 */
    END_MISSING("END_MISSING"),

    /** 开始节点有入边。 */
    START_HAS_INCOMING("START_HAS_INCOMING"),

    /** 开始节点不是恰好一条出边。 */
    START_OUTGOING("START_OUTGOING"),

    /** 结束节点有出边。 */
    END_HAS_OUTGOING("END_HAS_OUTGOING"),

    /** 节点从开始节点不可达。 */
    NODE_UNREACHABLE("NODE_UNREACHABLE"),

    /** 节点不能到达任何结束节点。 */
    NODE_CANNOT_REACH_END("NODE_CANNOT_REACH_END"),

    /** 流程图存在回环。 */
    GRAPH_CYCLE("GRAPH_CYCLE"),

    /** 任务节点不是恰好一条出边。 */
    TASK_OUTGOING("TASK_OUTGOING"),

    /** 排他网关少于两条出边。 */
    EXCLUSIVE_OUTGOING("EXCLUSIVE_OUTGOING"),

    /** 排他网关没有默认出边。 */
    EXCLUSIVE_DEFAULT_MISSING("EXCLUSIVE_DEFAULT_MISSING"),

    /** 排他网关的默认出边不是它的出边。 */
    EXCLUSIVE_DEFAULT_INVALID("EXCLUSIVE_DEFAULT_INVALID"),

    /** 排他网关的出边既无条件也不是默认边。 */
    EXCLUSIVE_EDGE_UNUSED("EXCLUSIVE_EDGE_UNUSED"),

    /** 条件引用的连线不是本网关的出边。 */
    CONDITION_EDGE_INVALID("CONDITION_EDGE_INVALID"),

    /** 同一出边配置了多组条件，或默认边配置了条件。 */
    CONDITION_EDGE_DUPLICATE("CONDITION_EDGE_DUPLICATE"),

    /** 条件组没有任何子句。 */
    CONDITION_EMPTY("CONDITION_EMPTY"),

    /** 并行分叉少于两条出边。 */
    PARALLEL_SPLIT_OUTGOING("PARALLEL_SPLIT_OUTGOING"),

    /** 并行汇合少于两条入边。 */
    PARALLEL_JOIN_INCOMING("PARALLEL_JOIN_INCOMING"),

    /** 并行汇合不是恰好一条出边。 */
    PARALLEL_JOIN_OUTGOING("PARALLEL_JOIN_OUTGOING"),

    /** 并行分叉与汇合没有一一成对。 */
    PARALLEL_UNPAIRED("PARALLEL_UNPAIRED"),

    /** 并行分支中没有任何节点。 */
    PARALLEL_BRANCH_EMPTY("PARALLEL_BRANCH_EMPTY"),

    /** 并行分支之间或与块外存在连线。 */
    PARALLEL_BRANCH_CROSS("PARALLEL_BRANCH_CROSS"),

    /** 一个并行分支以多条连线进入汇合。 */
    PARALLEL_BRANCH_JOIN_ENTRY("PARALLEL_BRANCH_JOIN_ENTRY"),

    /** 并行块内嵌套了并行分叉。 */
    PARALLEL_NESTED("PARALLEL_NESTED"),

    /** 结束节点位于并行块内部。 */
    PARALLEL_END_INSIDE("PARALLEL_END_INSIDE"),

    /** 任务节点既没有审批人也没有动作。 */
    TASK_EMPTY("TASK_EMPTY"),

    /** 配置了审批但缺少审批方式。 */
    APPROVAL_MODE_MISSING("APPROVAL_MODE_MISSING"),

    /** 配置了审批但没有审批人。 */
    APPROVER_MISSING("APPROVER_MISSING"),

    /** 同一节点的审批人重复。 */
    APPROVER_DUPLICATE("APPROVER_DUPLICATE"),

    /** 审批人不是 ACTIVE 的 Operations 账号。 */
    APPROVER_INACTIVE("APPROVER_INACTIVE"),

    /** 动作 key 为空或格式不合法。 */
    ACTION_KEY_INVALID("ACTION_KEY_INVALID"),

    /** 动作 key 使用了保留前缀 start。 */
    ACTION_KEY_RESERVED("ACTION_KEY_RESERVED"),

    /** 动作 key 在流程版本内重复。 */
    ACTION_KEY_DUPLICATE("ACTION_KEY_DUPLICATE"),

    /** 动作引用的模板不存在。 */
    TEMPLATE_NOT_FOUND("TEMPLATE_NOT_FOUND"),

    /** 动作引用的模板已停用。 */
    TEMPLATE_INACTIVE("TEMPLATE_INACTIVE"),

    /** 动作引用了只预留未开放的 DATA_QUERY 模板。 */
    TEMPLATE_RESERVED_TYPE("TEMPLATE_RESERVED_TYPE"),

    /** 动作没有默认执行人。 */
    EXECUTOR_MISSING("EXECUTOR_MISSING"),

    /** 动作执行人不是 ACTIVE 的 Operations 账号。 */
    EXECUTOR_INACTIVE("EXECUTOR_INACTIVE"),

    /** 输入映射的参数不在模板声明中。 */
    INPUT_KEY_UNDECLARED("INPUT_KEY_UNDECLARED"),

    /** 输入映射缺少变量名或常量值。 */
    INPUT_VALUE_INVALID("INPUT_VALUE_INVALID"),

    /** 引用的变量在该节点不可用。 */
    VARIABLE_UNAVAILABLE("VARIABLE_UNAVAILABLE"),

    /** 比较符不能用于该类型的变量。 */
    OPERATOR_TYPE_MISMATCH("OPERATOR_TYPE_MISMATCH"),

    /** 比较值缺失、多余或格式与变量类型不符。 */
    CONDITION_VALUE_INVALID("CONDITION_VALUE_INVALID"),

    /** 通知规则只能配置在任务节点或流程设置上。 */
    NOTIFICATION_NOT_ALLOWED("NOTIFICATION_NOT_ALLOWED"),

    /** 触发事件与规则所在位置或节点配置不符。 */
    NOTIFICATION_TRIGGER_INVALID("NOTIFICATION_TRIGGER_INVALID"),

    /** 规则字段与规则类型不符。 */
    NOTIFICATION_RULE_INVALID("NOTIFICATION_RULE_INVALID"),

    /** 通知规则没有通知对象。 */
    NOTIFICATION_RECIPIENT_MISSING("NOTIFICATION_RECIPIENT_MISSING"),

    /** 通知对象在规则所在位置没有可通知的人。 */
    NOTIFICATION_RECIPIENT_INVALID("NOTIFICATION_RECIPIENT_INVALID"),

    /** 指定的通知用户不是 ACTIVE 的 Operations 账号。 */
    NOTIFICATION_RECIPIENT_INACTIVE("NOTIFICATION_RECIPIENT_INACTIVE"),

    /** 通知规则没有通知渠道。 */
    NOTIFICATION_CHANNEL_MISSING("NOTIFICATION_CHANNEL_MISSING"),

    /** 回调编码为空或格式不合法。 */
    CALLBACK_CODE_INVALID("CALLBACK_CODE_INVALID"),

    /** 表单字段超过 50 个。 */
    TOO_MANY_FIELDS("TOO_MANY_FIELDS"),

    /** 字段 key 为空或格式不合法。 */
    FIELD_KEY_INVALID("FIELD_KEY_INVALID"),

    /** 字段 key 重复。 */
    FIELD_KEY_DUPLICATE("FIELD_KEY_DUPLICATE"),

    /** 字段缺少显示名。 */
    FIELD_LABEL_MISSING("FIELD_LABEL_MISSING"),

    /** 字段规则与字段类型不符或取值越界。 */
    FIELD_RULE_INVALID("FIELD_RULE_INVALID"),

    /** 动作模板配置与其类型的要求不符。 */
    ACTION_CONFIG_INVALID("ACTION_CONFIG_INVALID");

    private final String code;
}
