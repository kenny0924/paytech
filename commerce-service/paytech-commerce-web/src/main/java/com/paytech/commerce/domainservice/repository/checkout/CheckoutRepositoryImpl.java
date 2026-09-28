package com.paytech.commerce.domainservice.repository.checkout;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.paytech.commerce.core.dal.dataobject.checkout.CheckoutCreationView;
import com.paytech.commerce.core.dal.dataobject.order.OrderItem;
import com.paytech.commerce.core.dal.mapper.checkout.BasicCheckoutMapper;

import lombok.AllArgsConstructor;

/** MyBatis 实现的 Order 与 Hosted Checkout 首片仓储。 */
@Repository
@AllArgsConstructor
public class CheckoutRepositoryImpl implements CheckoutRepository {

    private final BasicCheckoutMapper basicCheckoutMapper;

    /** 按可信 Client 和冻结身份读取 Order/Session，按调用方要求加锁。 */
    @Override
    public Optional<CheckoutCreationView> findBasic(long clientId, long orderId, String orderNo, boolean lock) {
        requirePositive(clientId, "clientId");
        requirePositive(orderId, "orderId");
        return Optional
            .ofNullable(basicCheckoutMapper.select(clientId, orderId, requireText(orderNo, "orderNo"), lock));
    }

    /** 读取原商品事实，供创建回执完整性校验。 */
    @Override
    public List<OrderItem> findBasicItems(long clientId, long orderId) {
        return basicCheckoutMapper.selectItems(clientId, orderId);
    }

    /** 拒绝无效本地查询身份。 */
    private void requirePositive(long value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " 必须为正数");
        }
    }

    /** 拒绝空查询条件。 */
    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value;
    }
}
