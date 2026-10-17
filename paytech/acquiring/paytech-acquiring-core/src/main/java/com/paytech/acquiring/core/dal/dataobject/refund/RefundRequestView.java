package com.paytech.acquiring.core.dal.dataobject.refund;

import lombok.Getter;
import lombok.Setter;

/** 退款查询关联订单号的结果；创建流程直接使用单表 RefundRequest。 */
@Getter
@Setter
public class RefundRequestView extends RefundRequest {
    /** 同 Client 的关联订单公开编号。 */
    private String orderNo;
}
