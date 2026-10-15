package com.paytech.commerce.service.processor.customer;

import org.springframework.stereotype.Component;

import com.paytech.commerce.core.dal.dataobject.customer.Customer;
import com.paytech.commerce.core.dal.dataobject.order.CommerceOrder;
import com.paytech.commerce.domain.enums.CustomerStatus;
import com.paytech.commerce.domainservice.repository.customer.ClientStoredCardSettingRepository;
import com.paytech.commerce.domainservice.repository.customer.CustomerRepository;

import lombok.AllArgsConstructor;

/**
 * 结账查询与支付提交共用的存卡资格（Customer 与存卡支付技术方案 §2.2、§2.5）：只读本库，不访问外部服务。
 * <li>订单关联了有效 Customer，且 Client 已开通存卡，才能保存卡或使用已存卡。</li>
 * <li>开关实时读取，不随订单冻结；关闭后已存卡保留但不展示、不可使用。</li>
 */
@Component
@AllArgsConstructor
public class CardSavingEligibility {
    private final CustomerRepository customerRepository;
    private final ClientStoredCardSettingRepository settingRepository;

    /**
     * 订单可用于存卡的 Customer
     *
     * @return 有效 Customer；访客订单、Customer 已删除或 Client 未开通时为 null
     */
    public Customer eligibleCustomer(CommerceOrder order) {
        if (order.getCustomerId() == null || !settingRepository.isEnabled(order.getClientId())) {
            return null;
        }
        Customer customer = customerRepository.findById(order.getCustomerId());
        if (customer == null || customer.getCustomerStatus() != CustomerStatus.ACTIVE
            || customer.getClientId() != order.getClientId()) {
            return null;
        }
        return customer;
    }
}
