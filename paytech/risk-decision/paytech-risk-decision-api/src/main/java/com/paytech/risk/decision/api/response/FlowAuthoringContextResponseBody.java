package com.paytech.risk.api.decision.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.paytech.risk.api.decision.DecisionFlowModels.Field;
import com.paytech.risk.api.decision.DecisionFlowModels.Limits;
import com.paytech.risk.api.decision.DecisionFlowModels.NodeOutput;
import com.paytech.risk.api.decision.DecisionFlowModels.OutputDeclaration;
import com.paytech.risk.api.decision.DecisionFlowModels.SupportedPoint;
import com.paytech.risk.api.decision.RiskVariableModels;

/** 本期配置能力目录，不表示执行器已就绪。 */
public class FlowAuthoringContextResponseBody {

    /** Risk 支持的配置决策点。 */
    private List<SupportedPoint> supportedPoints;

    /** 决策点编码。 */
    private String pointCode;

    /** 准确输入合同编码。 */
    private String inputContractCode;

    /** 准确输入合同版本号。 */
    private int inputContractVersion;

    /** 图结构格式版本，当前为 1。 */
    private int graphSchemaVersion;

    /** 当前点系统输入变量的兼容投影。 */
    private List<Field> fields;

    /** 节点输出变量的兼容投影。 */
    private List<NodeOutput> nodeOutputs;

    /** 图与条件的配置容量上限。 */
    private Limits limits;

    /** 配置能力目录编码。 */
    private String descriptorCode;

    /** 条件格式版本，当前为 2。 */
    private int conditionSchemaVersion;

    /** 变量查询所需的决策点与用途。 */
    private RiskVariableModels.VariableQueryContext variableQueryContext;

    /** 当前决策点声明、可由放行结束节点配置的附加输出；没有声明时不输出该键。 */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<OutputDeclaration> outputs;

    /** 供 JSON 属性绑定使用。 */
    public FlowAuthoringContextResponseBody() { }

    /** 根据完整查询事实构造响应。 */
    public FlowAuthoringContextResponseBody(List<SupportedPoint> supportedPoints, String pointCode, String inputContractCode, int inputContractVersion, int graphSchemaVersion, List<Field> fields, List<NodeOutput> nodeOutputs, Limits limits, String descriptorCode, int conditionSchemaVersion, RiskVariableModels.VariableQueryContext variableQueryContext) {
        this.supportedPoints = supportedPoints;
        this.pointCode = pointCode;
        this.inputContractCode = inputContractCode;
        this.inputContractVersion = inputContractVersion;
        this.graphSchemaVersion = graphSchemaVersion;
        this.fields = fields;
        this.nodeOutputs = nodeOutputs;
        this.limits = limits;
        this.descriptorCode = descriptorCode;
        this.conditionSchemaVersion = conditionSchemaVersion;
        this.variableQueryContext = variableQueryContext;
    }

    /** 返回Risk 支持的配置决策点。 */
    public List<SupportedPoint> getSupportedPoints() { return supportedPoints; }

    /** 设置Risk 支持的配置决策点。 */
    public void setSupportedPoints(List<SupportedPoint> supportedPoints) { this.supportedPoints = supportedPoints; }

    /** 返回决策点编码。 */
    public String getPointCode() { return pointCode; }

    /** 设置决策点编码。 */
    public void setPointCode(String pointCode) { this.pointCode = pointCode; }

    /** 返回准确输入合同编码。 */
    public String getInputContractCode() { return inputContractCode; }

    /** 设置准确输入合同编码。 */
    public void setInputContractCode(String inputContractCode) { this.inputContractCode = inputContractCode; }

    /** 返回准确输入合同版本号。 */
    public int getInputContractVersion() { return inputContractVersion; }

    /** 设置准确输入合同版本号。 */
    public void setInputContractVersion(int inputContractVersion) { this.inputContractVersion = inputContractVersion; }

    /** 返回图结构格式版本，当前为 1。 */
    public int getGraphSchemaVersion() { return graphSchemaVersion; }

    /** 设置图结构格式版本，当前为 1。 */
    public void setGraphSchemaVersion(int graphSchemaVersion) { this.graphSchemaVersion = graphSchemaVersion; }

    /** 返回当前点系统输入变量的兼容投影。 */
    public List<Field> getFields() { return fields; }

    /** 设置当前点系统输入变量的兼容投影。 */
    public void setFields(List<Field> fields) { this.fields = fields; }

    /** 返回节点输出变量的兼容投影。 */
    public List<NodeOutput> getNodeOutputs() { return nodeOutputs; }

    /** 设置节点输出变量的兼容投影。 */
    public void setNodeOutputs(List<NodeOutput> nodeOutputs) { this.nodeOutputs = nodeOutputs; }

    /** 返回图与条件的配置容量上限。 */
    public Limits getLimits() { return limits; }

    /** 设置图与条件的配置容量上限。 */
    public void setLimits(Limits limits) { this.limits = limits; }

    /** 返回配置能力目录编码。 */
    public String getDescriptorCode() { return descriptorCode; }

    /** 设置配置能力目录编码。 */
    public void setDescriptorCode(String descriptorCode) { this.descriptorCode = descriptorCode; }

    /** 返回条件格式版本，当前为 2。 */
    public int getConditionSchemaVersion() { return conditionSchemaVersion; }

    /** 设置条件格式版本，当前为 2。 */
    public void setConditionSchemaVersion(int conditionSchemaVersion) { this.conditionSchemaVersion = conditionSchemaVersion; }

    /** 返回变量查询所需的决策点与用途。 */
    public RiskVariableModels.VariableQueryContext getVariableQueryContext() { return variableQueryContext; }

    /** 设置变量查询所需的决策点与用途。 */
    public void setVariableQueryContext(RiskVariableModels.VariableQueryContext variableQueryContext) { this.variableQueryContext = variableQueryContext; }

    /** 返回附加输出声明。 */
    public List<OutputDeclaration> getOutputs() { return outputs; }

    /** 设置附加输出声明。 */
    public void setOutputs(List<OutputDeclaration> outputs) { this.outputs = outputs; }
}
