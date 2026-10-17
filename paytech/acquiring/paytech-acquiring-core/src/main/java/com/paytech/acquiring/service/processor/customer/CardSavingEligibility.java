package com.paytech.acquiring.service.processor.customer;

import org.springframework.stereotype.Component;

import com.paytech.acquiring.core.dal.dataobject.order.CommerceOrder;
import com.paytech.acquiring.integration.consumer.ConsumerCustomerClient;
import com.paytech.consumer.api.enums.CardSavingIneligibleReason;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 结账查询与支付提交共用的存卡资格（Customer 与存卡支付技术方案 §2.2、§2.5；design/03 §3、S6）：在数据库事务外调用 consumer。
 * <li>订单关联了有效 Customer，且 Client 已开通存卡，才能保存卡或使用已存卡；判断由 consumer 组合 Customer 状态与 client-master 开关。</li>
 * <li>开关实时读取，不随订单冻结；关闭后已存卡保留但不展示、不可使用。</li>
 * <li>失败关闭：consumer 不可用、响应无效或开关不可读时一律降级为不可存卡并记 WARN，不影响结账。</li>
 */
@Slf4j
@Component
@AllArgsConstructor
public class CardSavingEligibility {
    private final ConsumerCustomerClient consumer;

    /**
     * 订单可用于存卡的 Customer
     *
     * @return 有资格的 Customer；访客订单、Customer 已删除、Client 未开通或资格读取失败时为 null
     */
    public EligibleCustomer eligibleCustomer(CommerceOrder order) {
        if (order.getCustomerId() == null) {
            return null;
        }
        ConsumerCustomerClient.CardSavingDecision decision;
        try {
            decision = consumer.cardSavingEligibility(order.getClientId(), order.getCustomerId());
        } catch (RuntimeException failure) {
            log.warn("存卡资格读取失败，降级为不可存卡, orderId={}, customerId={}, error={}", order.getOrderId(),
                order.getCustomerId(), failure.getClass().getSimpleName());
            return null;
        }
        if (!decision.eligible()) {
            if (CardSavingIneligibleReason.SETTING_UNAVAILABLE.getCode().equals(decision.reason())) {
                log.warn("Client 存卡开关不可读，降级为不可存卡, orderId={}, clientId={}", order.getOrderId(),
                    order.getClientId());
            }
            return null;
        }
        return new EligibleCustomer(order.getClientId(), order.getCustomerId(), decision.customerNo());
    }

    /**
     * 有存卡资格的 Customer
     *
     * @param clientId 所属 Client（与订单一致）
     * @param customerId Customer 主键
     * @param customerNo 公开 Customer 编号
     */
    public record EligibleCustomer(long clientId, long customerId, String customerNo) {}
}
