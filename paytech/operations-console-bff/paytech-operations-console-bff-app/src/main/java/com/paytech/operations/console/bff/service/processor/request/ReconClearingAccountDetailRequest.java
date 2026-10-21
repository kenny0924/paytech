package com.paytech.operations.console.bff.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 对账清算账户详情页的内部请求。 */
@Getter
@AllArgsConstructor
public class ReconClearingAccountDetailRequest {

    /**
     * 清算账户主键
     * <li>入口已按正十进制格式校验。</li>
     */
    @NotBlank(message = "清算账户主键不能为空")
    private final String clearingAccountId;
}
