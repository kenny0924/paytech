package com.paytech.compliance.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 推荐人对案件给出推荐结论；四眼开启时决定人必须与推荐人不同。 */
@Getter
@Setter
public class ClientReviewRecommendationRequestBody {

    /** 推荐结论：APPROVE 或 REJECT。 */
    @NotBlank
    @Pattern(regexp = "^(APPROVE|REJECT)$")
    private String recommendation;

    /** 推荐理由，1—2000 字符；内部可见。 */
    @NotBlank
    @Size(max = 2000)
    private String note;

    /**
     * 代操作的责任 PayFac（Local Partner ID）
     * <li>平台运营代 PayFac 操作时填写；compliance 记 operated_by=PLATFORM 与该值，操作人仍为当前运营账号。</li>
     * <li>可空；ops-bff 负责校验该 Partner 存在且具 OE_PROVIDER 角色。</li>
     */
    @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    private String onBehalfOfPartnerId;
}
