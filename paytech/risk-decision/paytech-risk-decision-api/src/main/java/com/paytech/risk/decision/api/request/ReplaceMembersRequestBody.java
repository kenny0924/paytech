package com.paytech.risk.decision.api.request;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonInclude;

import com.paytech.risk.decision.api.RiskVariableModels.*;

/** 原子替换集合；空数组清空，null 或重复规范值使整批失败。 */
public class ReplaceMembersRequestBody {

    /** 调用者确认的正整数根版本。 */
    @NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}")
    private String expectedVersion;

    /** 完整成员批次；空数组清空，最多 1000 项。 */
    @NotNull @Size(max = 1000)
    private List<@NotNull @Valid MemberRequest> members;

    /** JSON 反序列化构造。 */
    public ReplaceMembersRequestBody() { }

    /** 按完整合同构造。 */
    public ReplaceMembersRequestBody(String expectedVersion, List<MemberRequest> members) {
        this.expectedVersion = expectedVersion;
        this.members = members;
    }

    /** 读取调用者确认的正整数根版本。 */
    public String getExpectedVersion() { return expectedVersion; }

    /** 设置调用者确认的正整数根版本。 */
    public void setExpectedVersion(String expectedVersion) { this.expectedVersion = expectedVersion; }

    /** 读取完整成员批次。 */
    public List<MemberRequest> getMembers() { return members; }

    /** 设置完整成员批次。 */
    public void setMembers(List<MemberRequest> members) { this.members = members; }
}
