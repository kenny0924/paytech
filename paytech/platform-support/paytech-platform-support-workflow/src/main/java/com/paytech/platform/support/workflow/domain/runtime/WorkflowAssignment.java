package com.paytech.platform.support.workflow.domain.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 实例发起时冻结的人员分配（t_workflow_instance.assignment_json）：版本默认值加发起时的修改。
 *
 * <p>运行中人员只经转交变化；转交改的是具体动作运行或审批行，新一轮沿用上一轮的实际人员，见运行引擎。</p>
 */
public final class WorkflowAssignment {

    private final Map<String, List<Long>> approvers;

    private final Map<String, Long> executors;

    /**
     * 以节点审批人与动作执行人构造。
     *
     * @param approvers 节点 key 到审批人，保持配置顺序
     * @param executors 动作 key 到执行人
     */
    public WorkflowAssignment(Map<String, List<Long>> approvers, Map<String, Long> executors) {
        Map<String, List<Long>> copy = new LinkedHashMap<>();
        approvers.forEach((key, value) -> copy.put(key, List.copyOf(value)));
        this.approvers = Collections.unmodifiableMap(copy);
        this.executors = Collections.unmodifiableMap(new LinkedHashMap<>(executors));
    }

    /** 节点 key 到审批人。 */
    public Map<String, List<Long>> approvers() {
        return approvers;
    }

    /** 动作 key 到执行人。 */
    public Map<String, Long> executors() {
        return executors;
    }

    /**
     * 节点的审批人。
     *
     * @param nodeKey 节点 key
     * @return 审批人；没有时为空列表
     */
    public List<Long> approversOf(String nodeKey) {
        return approvers.getOrDefault(nodeKey, List.of());
    }

    /**
     * 动作的执行人。
     *
     * @param actionKey 动作 key
     * @return 执行人；没有时为 null
     */
    public Long executorOf(String actionKey) {
        return executors.get(actionKey);
    }

    /**
     * 分配中出现的全部人员，供发起前核对 ACTIVE。
     *
     * @return 账号 ID
     */
    public Set<Long> accountIds() {
        Set<Long> ids = new LinkedHashSet<>();
        approvers.values().forEach(ids::addAll);
        ids.addAll(executors.values());
        return ids;
    }
}
