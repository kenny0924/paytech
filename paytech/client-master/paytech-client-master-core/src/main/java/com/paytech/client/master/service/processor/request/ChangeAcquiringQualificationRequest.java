package com.paytech.client.master.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 修改 OE 收单资质的内部请求。 */
@Getter
@AllArgsConstructor
public class ChangeAcquiringQualificationRequest {
    /** OE 编号；入口已按格式校验。 */
    @NotBlank
    @Pattern(regexp = "[A-Z]{2}[0-9]{3}")
    private final String entityNo;
    /** 目标收单资质 Code，取值由 validate 校验。 */
    @NotBlank
    private final String acquiringQualification;
    /** 调用方读取时的根修订号。 */
    @Positive
    private final long expectedChangeVersion;
    /** 修改原因，1—500 字符。 */
    @NotBlank
    @Size(max = 500)
    private final String reason;
    /** 操作者 Account ID。 */
    @Positive
    private final long actorId;
}
