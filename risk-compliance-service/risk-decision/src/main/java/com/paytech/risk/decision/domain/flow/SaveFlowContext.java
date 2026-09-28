package com.paytech.risk.decision.domain.flow;

import java.util.List;

import com.paytech.risk.decision.core.dal.dataobject.DecisionFlowEdgeRow;
import com.paytech.risk.decision.core.dal.dataobject.DecisionFlowNodeRow;
import com.paytech.risk.decision.core.dal.dataobject.DecisionFlowRow;
import com.paytech.risk.decision.core.dal.dataobject.DecisionFlowVersionRow;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一次完整保存的本地事实，根对象在条件 SQL 成功后原位同步。 */
@Getter
@AllArgsConstructor
public class SaveFlowContext {
    /** 已锁定的管理根；新建时为本次分配的初始根。 */
    private final DecisionFlowRow root;
    /** 编辑前的准确版本；首次创建为空。 */
    private final DecisionFlowVersionRow previousVersion;
    /** 本次不可变版本头。 */
    private final DecisionFlowVersionRow version;
    /** 本次版本的全部节点。 */
    private final List<DecisionFlowNodeRow> nodes;
    /** 本次版本的全部连线。 */
    private final List<DecisionFlowEdgeRow> edges;

    /** 缺少旧版本仅表示首次创建永久流程身份。 */
    public boolean isNewFlow() { return previousVersion == null; }

    /** 从本次事实构造结果，不重新查询或复制当前根。 */
    public DecisionFlow.Saved result() {
        return new DecisionFlow.Saved(root.flowId(), version.flowVersionId(), version.versionNo(), root.version(),
            version.createdBy(), version.createTime());
    }
}
