package com.paytech.ledger.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 创建财务主体请求。 */
@Getter
@Setter
public class CreateFinanceEntityRequestBody {
    /**
     * Finance 内部财务主体代码
     * <li>永久唯一，最长 64 位</li>
     */
    @NotBlank
    @Size(max = 64)
    private String entityCode;

    /**
     * CCT 法律 Party 标识
     * <li>正十进制字符串；一个 Party 最多映射一个财务主体</li>
     */
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "必须是正十进制整数字符串")
    private String legalPartyId;

    /**
     * 内部显示名称
     * <li>最长 256 位</li>
     */
    @NotBlank
    @Size(max = 256)
    private String displayName;
}
