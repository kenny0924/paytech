package com.paytech.risk.api.decision.request;

import com.paytech.risk.api.decision.DecisionFlowModels.Graph;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** 完整保存或只读静态校验的请求，编辑基准与根修订号必须同时提供。 */
public class SaveFlowRequestBody {

    /**
     * 所属决策点编码。
     * <li>本期仅支持 CHECKOUT_PREPARE。</li>
     */
    @NotNull
    private String pointCode;

    /**
     * 流程永久编码。
     * <li>决策点内唯一，删除后不可复用。</li>
     */
    @NotNull
    private String flowCode;

    /**
     * 本次配置名称。
     * <li>1–100 个 Unicode 码点，保存为版本快照。</li>
     */
    @NotNull
    private String flowName;

    /**
     * 编辑基准版本 ID。
     * <li>正 BIGINT 字符串；新建为 null，键始终必填。</li>
     */
    private String baseFlowVersionId;

    /**
     * 调用方读取的根修订号。
     * <li>正 BIGINT 字符串；与基准版本同时为 null 或同时提供。</li>
     */
    private String expectedVersion;

    /**
     * 本次人工变更说明。
     * <li>1–500 个 Unicode 码点，不包含个人原值或凭据。</li>
     */
    @NotNull
    private String changeReason;

    /**
     * 完整配置图。
     * <li>只包含业务节点、连线与布局，不包含图形库临时状态。</li>
     */
    @NotNull @Valid
    private Graph graph;

    /** 返回所属决策点编码。 */
    public String getPointCode() { return pointCode; }

    /** 设置所属决策点编码。 */
    public void setPointCode(String pointCode) { this.pointCode = pointCode; }

    /** 返回流程永久编码。 */
    public String getFlowCode() { return flowCode; }

    /** 设置流程永久编码。 */
    public void setFlowCode(String flowCode) { this.flowCode = flowCode; }

    /** 返回本次配置名称。 */
    public String getFlowName() { return flowName; }

    /** 设置本次配置名称。 */
    public void setFlowName(String flowName) { this.flowName = flowName; }

    /** 返回编辑基准版本 ID。 */
    public String getBaseFlowVersionId() { return baseFlowVersionId; }

    /** 设置编辑基准版本 ID。 */
    public void setBaseFlowVersionId(String baseFlowVersionId) { this.baseFlowVersionId = baseFlowVersionId; }

    /** 返回调用方读取的根修订号。 */
    public String getExpectedVersion() { return expectedVersion; }

    /** 设置调用方读取的根修订号。 */
    public void setExpectedVersion(String expectedVersion) { this.expectedVersion = expectedVersion; }

    /** 返回本次人工变更说明。 */
    public String getChangeReason() { return changeReason; }

    /** 设置本次人工变更说明。 */
    public void setChangeReason(String changeReason) { this.changeReason = changeReason; }

    /** 返回完整配置图。 */
    public Graph getGraph() { return graph; }

    /** 设置完整配置图。 */
    public void setGraph(Graph graph) { this.graph = graph; }
}
