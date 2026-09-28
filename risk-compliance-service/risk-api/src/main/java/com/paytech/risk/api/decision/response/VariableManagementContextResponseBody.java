package com.paytech.risk.api.decision.response;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonInclude;

import com.paytech.risk.api.decision.RiskVariableModels.*;

/** 工程合同只用于验证映射能力，变量候选必须查询变量库。 */
public class VariableManagementContextResponseBody {

    /** 已支持的决策点。 */
    private List<SupportedPoint> supportedPoints;

    /** 来源类型及运营创建能力。 */
    private List<SourceCapability> sources;

    /** 支持的标量类型。 */
    private List<String> valueTypes;

    /** 通用格式能力。 */
    private List<FormatProfile> formatProfiles;

    /** 支持的数值量纲。 */
    private List<String> numericUnits;

    /** 接口容量约束。 */
    private Limits limits;

    /** 准确来源合同，不替代变量库候选。 */
    private List<SourceContract> contracts;

    /** JSON 反序列化构造。 */
    public VariableManagementContextResponseBody() { }

    /** 按完整合同构造。 */
    public VariableManagementContextResponseBody(List<SupportedPoint> supportedPoints, List<SourceCapability> sources, List<String> valueTypes, List<FormatProfile> formatProfiles, List<String> numericUnits, Limits limits, List<SourceContract> contracts) {
        this.supportedPoints = supportedPoints;
        this.sources = sources;
        this.valueTypes = valueTypes;
        this.formatProfiles = formatProfiles;
        this.numericUnits = numericUnits;
        this.limits = limits;
        this.contracts = contracts;
    }

    /** 读取已支持的决策点。 */
    public List<SupportedPoint> getSupportedPoints() { return supportedPoints; }

    /** 设置已支持的决策点。 */
    public void setSupportedPoints(List<SupportedPoint> supportedPoints) { this.supportedPoints = supportedPoints; }

    /** 读取来源类型及运营创建能力。 */
    public List<SourceCapability> getSources() { return sources; }

    /** 设置来源类型及运营创建能力。 */
    public void setSources(List<SourceCapability> sources) { this.sources = sources; }

    /** 读取支持的标量类型。 */
    public List<String> getValueTypes() { return valueTypes; }

    /** 设置支持的标量类型。 */
    public void setValueTypes(List<String> valueTypes) { this.valueTypes = valueTypes; }

    /** 读取通用格式能力。 */
    public List<FormatProfile> getFormatProfiles() { return formatProfiles; }

    /** 设置通用格式能力。 */
    public void setFormatProfiles(List<FormatProfile> formatProfiles) { this.formatProfiles = formatProfiles; }

    /** 读取支持的数值量纲。 */
    public List<String> getNumericUnits() { return numericUnits; }

    /** 设置支持的数值量纲。 */
    public void setNumericUnits(List<String> numericUnits) { this.numericUnits = numericUnits; }

    /** 读取接口容量约束。 */
    public Limits getLimits() { return limits; }

    /** 设置接口容量约束。 */
    public void setLimits(Limits limits) { this.limits = limits; }

    /** 读取准确来源合同，不替代变量库候选。 */
    public List<SourceContract> getContracts() { return contracts; }

    /** 设置准确来源合同，不替代变量库候选。 */
    public void setContracts(List<SourceContract> contracts) { this.contracts = contracts; }
}
