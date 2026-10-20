package com.paytech.counterparty.ext.job;

import org.springframework.stereotype.Component;

import com.paytech.counterparty.core.dal.dataobject.Customer;
import com.paytech.counterparty.api.enums.CustomerStatus;
import com.paytech.counterparty.domainservice.repository.CustomerRepository;
import com.paytech.counterparty.integration.card.StoredCardClient;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationEventHandler;
import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;

import lombok.AllArgsConstructor;

/**
 * Customer 删除后失效其 Payment 已存卡（Customer 与存卡支付技术方案 §2.1）。
 *
 * <p>
 * 删除事务内登记，提交后由 Foundation 唤醒；只读本地 Customer 后在事务外调用 Payment，调用可重复。Payment 暂不可用时保留任务重试。Commerce 在列出与使用已存卡前都检查 Customer
 * 有效，本任务只负责让 Payment 侧状态最终一致。
 */
@Component
@AllArgsConstructor
public class StoredCardInvalidationHandler implements CompensationEventHandler {

    /** 失效已存卡的任务类型。 */
    public static final String MAIN_TYPE = "STORED_CARD_INVALIDATION";

    private final CustomerRepository customers;
    private final StoredCardClient storedCards;

    /** 仅构造登记请求；任务凭 Customer 主键恢复，无业务子类型或 Payload。 */
    public static CompensationEventRequest buildEvent(long customerId) {
        return new CompensationEventRequest(Long.toString(customerId), MAIN_TYPE, "-");
    }

    /** 返回创建端登记的稳定事件类型。 */
    @Override
    public String eventType() {
        return MAIN_TYPE;
    }

    /** 读取已删除的 Customer，事务外调用 Payment 失效其全部已存卡。 */
    @Override
    public CompensationHandlerResult handle(CompensationEvent event) {
        if (!eventType().equals(event.eventType()) || event.payloadJson() != null
            || !event.eventKey().matches("[1-9][0-9]{0,18}")) {
            return CompensationHandlerResult.terminalFailure("INVALID_STORED_CARD_TASK", "已存卡失效任务元数据不符合约定");
        }
        Customer customer = customers.findById(Long.parseLong(event.eventKey()));
        if (customer == null || customer.getCustomerStatus() != CustomerStatus.DELETED) {
            return CompensationHandlerResult.terminalFailure("CUSTOMER_NOT_DELETED", "Customer 不存在或未删除");
        }
        try {
            storedCards.invalidate(customer.getClientId(), customer.getCustomerId());
            return CompensationHandlerResult.success();
        } catch (ServiceUnavailableException unavailable) {
            return CompensationHandlerResult.retryableFailure("PAYMENT_UNAVAILABLE", "Payment 暂不可用");
        }
    }
}
