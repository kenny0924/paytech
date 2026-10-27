package com.paytech.compliance.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** O05 材料核验结果；拒绝必须带原因码，客户可见说明在退回时另行提交。 */
@Getter
@Setter
public class ClientReviewEvidenceReviewRequestBody {

    /**
     * 核验结果
     * <li>ACCEPTED 或 REJECTED。</li>
     */
    @NotBlank
    @Pattern(regexp = "^(ACCEPTED|REJECTED)$")
    private String outcome;

    /**
     * 拒绝原因码
     * <li>REJECTED 必填：DOCUMENT_EXPIRED、DOCUMENT_TOO_OLD、DOCUMENT_UNREADABLE、DOCUMENT_MISMATCH_NAME、
     * DOCUMENT_MISMATCH_ADDRESS、DOCUMENT_TYPE_INVALID、DOCUMENT_FRAUDULENT、OTHER；ACCEPTED 必须为空。</li>
     */
    @Pattern(regexp = "^(DOCUMENT_EXPIRED|DOCUMENT_TOO_OLD|DOCUMENT_UNREADABLE|DOCUMENT_MISMATCH_NAME|DOCUMENT_MISMATCH_ADDRESS|DOCUMENT_TYPE_INVALID|DOCUMENT_FRAUDULENT|OTHER)$")
    private String rejectReasonCode;

    /** 内部核验备注，可空，最多 1000 字符。 */
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
