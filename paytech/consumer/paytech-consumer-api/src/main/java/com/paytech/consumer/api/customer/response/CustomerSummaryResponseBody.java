package com.paytech.consumer.api.customer.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Customer 摘要；不含消费者引用原值。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CustomerSummaryResponseBody {
    /** Customer 主键。 */
    private String customerId;

    /** 公开 Customer 编号。 */
    private String customerNo;

    /** 所属 Client。 */
    private String clientId;

    /** Customer 状态，取值见 {@link com.paytech.consumer.api.enums.CustomerStatus}。 */
    private String status;
}
