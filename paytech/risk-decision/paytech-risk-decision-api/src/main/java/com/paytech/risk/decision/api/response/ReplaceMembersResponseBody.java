package com.paytech.risk.decision.api.response;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonInclude;

import com.paytech.risk.decision.api.RiskVariableModels.*;

/** 整批替换成功结果；失败不保留部分成员。 */
public class ReplaceMembersResponseBody {

    /** 永久唯一变量编码。 */
    private String variableCode;

    /** 根 CAS 版本，以正整数字符串表示。 */
    private String version;

    /** 集合成员数量；非集合为空。 */
    private int memberCount;

    /** UTC 微秒更新时间。 */
    private String updateTime;

    /** JSON 反序列化构造。 */
    public ReplaceMembersResponseBody() { }

    /** 按完整合同构造。 */
    public ReplaceMembersResponseBody(String variableCode, String version, int memberCount, String updateTime) {
        this.variableCode = variableCode;
        this.version = version;
        this.memberCount = memberCount;
        this.updateTime = updateTime;
    }

    /** 读取永久唯一变量编码。 */
    public String getVariableCode() { return variableCode; }

    /** 设置永久唯一变量编码。 */
    public void setVariableCode(String variableCode) { this.variableCode = variableCode; }

    /** 读取根 CAS 版本，以正整数字符串表示。 */
    public String getVersion() { return version; }

    /** 设置根 CAS 版本，以正整数字符串表示。 */
    public void setVersion(String version) { this.version = version; }

    /** 读取集合成员数量。 */
    public int getMemberCount() { return memberCount; }

    /** 设置集合成员数量。 */
    public void setMemberCount(int memberCount) { this.memberCount = memberCount; }

    /** 读取UTC 微秒更新时间。 */
    public String getUpdateTime() { return updateTime; }

    /** 设置UTC 微秒更新时间。 */
    public void setUpdateTime(String updateTime) { this.updateTime = updateTime; }
}
