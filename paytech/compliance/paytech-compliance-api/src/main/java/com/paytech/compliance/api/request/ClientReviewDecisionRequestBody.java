package com.paytech.compliance.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** O08/O09 最终决定；批准或拒绝由操作路径确定，不由请求体决定。 */
@Getter
@Setter
public class ClientReviewDecisionRequestBody {

    /** 决定原因代码。 */
    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,63}$")
    private String reasonCode;

    /** 内部审核理由，1—2000 字符；不会展示给客户。 */
    @NotBlank
    @Size(max = 2000)
    private String reason;

    /** 另行确认的客户可见说明，可空，最多 1000 字符。 */
    @Size(max = 1000)
    private String publicNote;

    /**
     * 代操作的责任 PayFac（Local Partner ID）
     * <li>平台运营代 PayFac 操作时填写；compliance 记 operated_by=PLATFORM 与该值，操作人仍为当前运营账号。</li>
     * <li>可空；ops-bff 负责校验该 Partner 存在且具 OE_PROVIDER 角色。</li>
     */
    @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    private String onBehalfOfPartnerId;
}
