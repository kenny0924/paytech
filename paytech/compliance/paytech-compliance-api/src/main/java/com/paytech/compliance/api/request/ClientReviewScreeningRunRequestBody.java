package com.paytech.compliance.api.request;

import jakarta.validation.constraints.Pattern;

import lombok.Getter;
import lombok.Setter;

/** 重新发起案件全部筛查；新执行成功后取代同主体同类型的旧命中。 */
@Getter
@Setter
public class ClientReviewScreeningRunRequestBody {

    /**
     * 代操作的责任 PayFac（Local Partner ID）
     * <li>平台运营代 PayFac 操作时填写；compliance 记 operated_by=PLATFORM 与该值，操作人仍为当前运营账号。</li>
     * <li>可空；ops-bff 负责校验该 Partner 存在且具 OE_PROVIDER 角色。</li>
     */
    @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    private String onBehalfOfPartnerId;
}
