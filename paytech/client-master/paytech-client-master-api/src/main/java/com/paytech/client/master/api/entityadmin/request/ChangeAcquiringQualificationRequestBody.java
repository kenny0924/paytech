package com.paytech.client.master.api.entityadmin.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 修改 Operating Entity 收单资质。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChangeAcquiringQualificationRequestBody {
    /**
     * 目标收单资质 Code
     * <li>MERCHANT、MERCHANT_RESALE_APPROVED 或 PAYFAC；其他取值返回 422 ENTITY_ATTRIBUTE_INVALID。</li>
     */
    @NotBlank(message = "acquiringQualification 必填")
    private String acquiringQualification;

    /**
     * 读取详情时得到的 changeVersion
     * <li>与当前版本不一致返回 409 ENTITY_CHANGE_VERSION_CONFLICT。</li>
     */
    @NotNull(message = "expectedChangeVersion 必填")
    @Positive(message = "expectedChangeVersion 必须为正整数")
    private Long expectedChangeVersion;

    /**
     * 修改原因
     * <li>1—500 字符，写入审计快照。</li>
     */
    @NotBlank(message = "reason 必填")
    @Size(max = 500, message = "reason 最多 500 字符")
    private String reason;
}
