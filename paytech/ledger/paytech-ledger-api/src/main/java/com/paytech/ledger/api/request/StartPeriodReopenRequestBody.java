package com.paytech.ledger.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 发起期间重开请求；编制人取自 X-Gateway-Actor-Subject。 */
@Getter
@Setter
public class StartPeriodReopenRequestBody {
    /**
     * 重开原因代码
     * <li>ACCOUNTING_CORRECTION：会计更正；LATE_TRANSACTION：晚到交易补记；AUDIT_ADJUSTMENT：审计调整。</li>
     * <li>合法性由服务端校验，不在目录内时 400。</li>
     */
    @NotBlank
    private String reasonCode;

    /**
     * 批准本次重开的 Platform 工单或操作申请稳定引用
     * <li>最长 128 字符。</li>
     */
    @NotBlank
    @Size(max = 128)
    private String requestRef;
}
