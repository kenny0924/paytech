package com.paytech.ledger.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 在账簿下创建统一账户请求。 */
@Getter
@Setter
public class CreateAccountRequestBody {
    /**
     * 科目代码
     * <li>必须属于账簿采用的科目表且可直接过账</li>
     */
    @NotBlank
    @Size(max = 64)
    private String accountCode;

    /**
     * 核算对象类型
     * <li>OPERATING_ENTITY、FINANCE_ENTITY、CLIENT、LOCAL_PARTNER、CHANNEL_ACCOUNT、CASH_ACCOUNT、TAX_AUTHORITY</li>
     */
    @NotBlank
    @Pattern(regexp = "OPERATING_ENTITY|FINANCE_ENTITY|CLIENT|LOCAL_PARTNER|CHANNEL_ACCOUNT|CASH_ACCOUNT|TAX_AUTHORITY", message = "核算对象类型非法")
    private String ownerType;

    /**
     * 核算对象标识
     * <li>OPERATING_ENTITY 为 entityNo，其余为正十进制字符串</li>
     */
    @NotBlank
    @Size(max = 64)
    private String ownerId;

    /**
     * 余额桶
     * <li>CLIENT_PAYABLE、PARTNER_PAYABLE 使用 PENDING、AVAILABLE、LOCKED；其他科目使用 NONE</li>
     */
    @NotBlank
    @Pattern(regexp = "PENDING|AVAILABLE|LOCKED|NONE", message = "余额桶非法")
    private String balanceBucket;
}
