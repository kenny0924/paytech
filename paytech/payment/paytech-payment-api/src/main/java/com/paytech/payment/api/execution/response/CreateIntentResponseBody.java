package com.paytech.payment.api.execution.response;

import lombok.Getter;
import lombok.Setter;

/** 创建支付意图响应 */
@Getter
@Setter
public class CreateIntentResponseBody {
    /** 支付意图 ID */
    private String intentId;

    /**
     * 支付意图状态
     * <li>OPEN、SUCCEEDED、CANCELLED 或 EXPIRED；重复创建返回原记录当前状态。</li>
     */
    private String status;
}
