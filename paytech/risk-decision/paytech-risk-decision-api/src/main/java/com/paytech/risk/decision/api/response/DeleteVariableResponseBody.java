package com.paytech.risk.api.decision.response;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonInclude;

import com.paytech.risk.api.decision.RiskVariableModels.*;

/** 无引用时保留 DELETED 墓碑及永久编码。 */
public class DeleteVariableResponseBody {

    /** 永久唯一变量编码。 */
    private String variableCode;

    /** 当前状态。 */
    private String status;

    /** 根 CAS 版本，以正整数字符串表示。 */
    private String version;

    /** JSON 反序列化构造。 */
    public DeleteVariableResponseBody() { }

    /** 按完整合同构造。 */
    public DeleteVariableResponseBody(String variableCode, String status, String version) {
        this.variableCode = variableCode;
        this.status = status;
        this.version = version;
    }

    /** 读取永久唯一变量编码。 */
    public String getVariableCode() { return variableCode; }

    /** 设置永久唯一变量编码。 */
    public void setVariableCode(String variableCode) { this.variableCode = variableCode; }

    /** 读取当前状态。 */
    public String getStatus() { return status; }

    /** 设置当前状态。 */
    public void setStatus(String status) { this.status = status; }

    /** 读取根 CAS 版本，以正整数字符串表示。 */
    public String getVersion() { return version; }

    /** 设置根 CAS 版本，以正整数字符串表示。 */
    public void setVersion(String version) { this.version = version; }
}
