package com.paytech.commercial.api.term.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** 版本状态推进命令（提交、批准、驳回、激活）的公共输入。 */
@Getter
@Setter
public class CommercialTermVersionCommandRequestBody {
    /** 方案 BCR 根当前修订版本（CAS 令牌）；不等于当前值时 409。 */
    @NotBlank(message = "expectedChangeVersion 必填")
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "expectedChangeVersion 格式不正确")
    private String expectedChangeVersion;
}
