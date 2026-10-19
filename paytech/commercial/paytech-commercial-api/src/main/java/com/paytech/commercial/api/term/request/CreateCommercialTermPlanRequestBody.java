package com.paytech.commercial.api.term.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** 创建商业条款方案（DRAFT）；方案编号在条款类型内唯一，同号同内容重放返回原方案。 */
@Getter
@Setter
public class CreateCommercialTermPlanRequestBody {
    /**
     * 方案编号
     * <li>同一条款类型内唯一，创建后不可修改。</li>
     */
    @NotBlank(message = "planCode 必填")
    @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]{0,63}", message = "planCode 格式不正确")
    private String planCode;

    /**
     * 条款类型
     * <li>必须在计费事件目录（t_product_fee_event.term_type）登记，例如 CLIENT_PRICING、PARTNER_SHARE。</li>
     */
    @NotBlank(message = "termType 必填")
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "termType 格式不正确")
    private String termType;

    /**
     * 所属合同 ID
     * <li>CLIENT_PRICING 等 Client 条款引用 CLIENT 合同；Partner 条款引用同一 Local Partner 的 PARTNER 合同。</li>
     */
    @NotBlank(message = "agreementId 必填")
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "agreementId 格式不正确")
    private String agreementId;

    /**
     * Client Owner
     * <li>Client 条款必填且等于合同 Client；Partner 条款必须为空（与 localPartnerId 二选一）。</li>
     */
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "clientId 格式不正确")
    private String clientId;

    /**
     * Local Partner Owner
     * <li>PARTNER_SHARE、PLATFORM_SERVICE_CHARGE、PARTNER_BUY_RATE 必填且等于合同 Local Partner；Client 条款必须为空。</li>
     */
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "localPartnerId 格式不正确")
    private String localPartnerId;

    /** 操作人账号 ID，记入审计快照。 */
    @NotBlank(message = "operatorAccountId 必填")
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "operatorAccountId 格式不正确")
    private String operatorAccountId;
}
