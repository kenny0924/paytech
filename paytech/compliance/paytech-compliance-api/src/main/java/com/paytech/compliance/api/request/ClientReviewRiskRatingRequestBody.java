package com.paytech.compliance.api.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 手工客户风险评级（CRR）：评级与依据因子一并替换当前值。 */
@Getter
@Setter
public class ClientReviewRiskRatingRequestBody {

    /** 评级：LOW、MEDIUM 或 HIGH。 */
    @NotBlank
    @Pattern(regexp = "^(LOW|MEDIUM|HIGH)$")
    private String riskRating;

    /** 依据因子，至少一项、最多 50 项；同一因子代码与取值不重复。 */
    @NotEmpty
    @Size(max = 50)
    @Valid
    private List<ClientReviewRiskFactorBody> factors;

    /** 评级说明，1—1000 字符。 */
    @NotBlank
    @Size(max = 1000)
    private String note;

    /**
     * 代操作的责任 PayFac（Local Partner ID）
     * <li>平台运营代 PayFac 操作时填写；compliance 记 operated_by=PLATFORM 与该值，操作人仍为当前运营账号。</li>
     * <li>可空；ops-bff 负责校验该 Partner 存在且具 OE_PROVIDER 角色。</li>
     */
    @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    private String onBehalfOfPartnerId;
}
