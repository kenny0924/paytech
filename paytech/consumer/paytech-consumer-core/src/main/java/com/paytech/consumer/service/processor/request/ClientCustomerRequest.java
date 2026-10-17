package com.paytech.commerce.service.processor.customer.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Client Customer 操作的内部输入；各操作只填所需字段，HTTP 字段约束由入口校验。 */
@Getter
@AllArgsConstructor
public class ClientCustomerRequest {
    /** 可信接入层确定的 Client 主键。 */
    private final long clientId;

    /** 公开 Customer 编号；按引用操作时为空。 */
    private final String customerNo;

    /** 消费者引用；按编号操作时为空。 */
    private final String consumerReference;

    /** 已存卡 Token；只在删除已存卡时填写。 */
    private final String storedCardToken;

    @Override
    public String toString() {
        return "ClientCustomerRequest[REDACTED]";
    }
}
