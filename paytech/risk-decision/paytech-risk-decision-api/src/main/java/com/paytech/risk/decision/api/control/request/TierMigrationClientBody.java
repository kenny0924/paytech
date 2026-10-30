package com.paytech.risk.decision.api.control.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 一个待迁移 Client 及其展示字段（来自 client-master 只读导出）。 */
@Getter
@Setter
public class TierMigrationClientBody {

    /** Client ID。 */
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}")
    private String clientId;

    /** Client 法定名称或个人姓名，最长 255 字符。 */
    @NotBlank
    @Size(max = 255)
    private String clientDisplayName;

    /** Client 编号，最长 64 字符。 */
    @NotBlank
    @Size(max = 64)
    private String clientNo;

    /** Client 类型：INDIVIDUAL 或 ORGANIZATION。 */
    @NotBlank
    @Pattern(regexp = "INDIVIDUAL|ORGANIZATION")
    private String clientType;

    /** 入驻辖区代码，最长 16 字符。 */
    @NotBlank
    @Size(max = 16)
    @Pattern(regexp = "[A-Z0-9_-]{1,16}")
    private String jurisdictionCode;
}
