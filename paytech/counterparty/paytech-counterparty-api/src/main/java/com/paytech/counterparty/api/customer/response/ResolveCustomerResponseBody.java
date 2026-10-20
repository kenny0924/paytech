package com.paytech.counterparty.api.customer.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 建或取 Customer 的结果。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResolveCustomerResponseBody {
    /** Customer 主键。 */
    private String customerId;

    /** 公开 Customer 编号。 */
    private String customerNo;

    /** Customer 状态，取值见 {@link com.paytech.counterparty.api.enums.CustomerStatus}。 */
    private String status;

    /** 是否本次新建。 */
    private boolean created;
}
