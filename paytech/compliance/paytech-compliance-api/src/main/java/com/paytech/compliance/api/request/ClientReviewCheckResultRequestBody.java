package com.paytech.compliance.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 录入一项人工检查的结论。 */
@Getter
@Setter
public class ClientReviewCheckResultRequestBody {

    /** 结论：PASS 或 FAIL。 */
    @NotBlank
    @Pattern(regexp = "^(PASS|FAIL)$")
    private String outcome;

    /** 结论原因码（受控目录），例如 NO_HIT、REFUND_POLICY_MISSING。 */
    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,63}$")
    private String reasonCode;

    /**
     * 查询或名单版本引用
     * <li>人工名单筛查录名单版本、终止商户查询录查询编号等；可空，最多 128 字符。</li>
     */
    @Size(max = 128)
    private String evidenceRef;

    /** 内部备注，可空，最多 1000 字符。 */
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
