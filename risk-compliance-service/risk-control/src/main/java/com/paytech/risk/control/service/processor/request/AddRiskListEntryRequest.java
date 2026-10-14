package com.paytech.risk.control.service.processor.request;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 添加名单成员的 Owner 输入。 */
@Getter
@AllArgsConstructor
public class AddRiskListEntryRequest {
    /** 操作人账号 ID。 */
    private final long actorAccountId;

    /** 名单编码。 */
    @NotBlank
    private final String listCode;

    /**
     * 主体原值
     * <li>CONSUMER 为 clientId:consumerReference；IP 不做 DNS 解析。</li>
     */
    @NotBlank
    @Size(max = 256)
    private final String subjectValue;

    /** 生效起点，缺省为当前时间。 */
    private final Instant effectiveFrom;

    /** 生效终点，不含；缺省为长期有效。 */
    private final Instant effectiveTo;

    /** 加入原因。 */
    @NotBlank
    @Size(max = 500)
    private final String reason;
}
