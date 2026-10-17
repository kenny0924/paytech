package com.paytech.consumer.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.consumer.api.enums.CardSavingIneligibleReason;
import com.paytech.consumer.api.enums.CustomerStatus;
import com.paytech.consumer.core.dal.dataobject.Customer;
import com.paytech.consumer.domainservice.repository.CustomerRepository;
import com.paytech.consumer.integration.clientmaster.ClientStoredCardSettingClient;
import com.paytech.consumer.service.processor.request.CardSavingEligibilityRequest;
import com.paytech.consumer.service.processor.response.CardSavingEligibilityResponse;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 存卡资格（原 Commerce CardSavingEligibility，design/03 §3、D42、S6）：组合本库 Customer 与 client-master 的 Client 已存卡开关，全程不开事务。
 * <li>Customer 必须存在、属于请求 Client 且有效，Client 必须已开通存卡；开关实时读取，不随订单冻结。</li>
 * <li>失败关闭：client-master 不可用、拒绝或响应无效时返回 SETTING_UNAVAILABLE，不当作已开通。</li>
 * <li>Customer 不可用时不再读取开关；跨 Client 时不返回对方 Customer 编号。</li>
 */
@Slf4j
@Component
@AllArgsConstructor
public class GetCardSavingEligibilityProcessor
    extends AbstractProcessor<CardSavingEligibilityRequest, CardSavingEligibilityResponse> {
    private final CustomerRepository customerRepository;
    private final ClientStoredCardSettingClient settings;

    @Override
    protected CardSavingEligibilityResponse processRequest(CardSavingEligibilityRequest request) {
        Customer customer = customerRepository.findById(request.customerId());
        if (customer == null) {
            return new CardSavingEligibilityResponse(CardSavingIneligibleReason.CUSTOMER_NOT_FOUND, null);
        }
        if (customer.getClientId() != request.clientId()) {
            return new CardSavingEligibilityResponse(CardSavingIneligibleReason.CLIENT_MISMATCH, null);
        }
        if (customer.getCustomerStatus() != CustomerStatus.ACTIVE) {
            return new CardSavingEligibilityResponse(CardSavingIneligibleReason.CUSTOMER_INACTIVE,
                customer.getCustomerNo());
        }
        return settingDecision(request.clientId(), customer.getCustomerNo());
    }

    /** 事务外读取 Client 开关；任何读取失败都关闭存卡。 */
    CardSavingEligibilityResponse settingDecision(long clientId, String customerNo) {
        boolean enabled;
        try {
            enabled = settings.storedCardEnabled(clientId);
        } catch (RuntimeException unavailable) {
            log.warn("Client 存卡开关读取失败，按未开通处理, clientId={}, error={}", clientId,
                unavailable.getClass().getSimpleName());
            return new CardSavingEligibilityResponse(CardSavingIneligibleReason.SETTING_UNAVAILABLE, customerNo);
        }
        return enabled ? CardSavingEligibilityResponse.eligible(customerNo)
            : new CardSavingEligibilityResponse(CardSavingIneligibleReason.SETTING_DISABLED, customerNo);
    }
}
