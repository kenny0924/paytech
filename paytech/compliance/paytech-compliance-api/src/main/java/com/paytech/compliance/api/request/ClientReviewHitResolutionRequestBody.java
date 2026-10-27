package com.paytech.compliance.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 处置一条筛查命中；每次处置追加一条不可变记录。 */
@Getter
@Setter
public class ClientReviewHitResolutionRequestBody {

    /**
     * 处置结论
     * <li>NO_MATCH、FALSE_POSITIVE、TRUE_POSITIVE_ACCEPT、TRUE_POSITIVE_REJECT 关闭命中（RESOLVED）。</li>
     * <li>POTENTIAL_MATCH、INSUFFICIENT_EVIDENCE 只记录，命中仍为 OPEN。</li>
     */
    @NotBlank
    @Pattern(regexp = "^(NO_MATCH|FALSE_POSITIVE|POTENTIAL_MATCH|TRUE_POSITIVE_ACCEPT|TRUE_POSITIVE_REJECT|INSUFFICIENT_EVIDENCE)$")
    private String resolution;

    /** 处置原因码（受控目录）。 */
    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,63}$")
    private String reasonCode;

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
