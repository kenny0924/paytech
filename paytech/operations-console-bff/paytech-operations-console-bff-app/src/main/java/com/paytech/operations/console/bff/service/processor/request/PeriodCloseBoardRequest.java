package com.paytech.operations.console.bff.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 期间关账看板聚合的内部请求。 */
@Getter
@AllArgsConstructor
public class PeriodCloseBoardRequest {

    /**
     * 财务日历主键
     * <li>入口已按正十进制格式校验。</li>
     */
    @NotBlank(message = "财务日历不能为空")
    private final String fiscalCalendarId;
}
