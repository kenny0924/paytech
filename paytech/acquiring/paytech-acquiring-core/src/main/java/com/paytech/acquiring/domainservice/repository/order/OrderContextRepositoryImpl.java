package com.paytech.commerce.domainservice.repository.order;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.commerce.core.dal.mapper.customer.CustomerCheckoutMapper;
import com.paytech.commerce.core.dal.dataobject.customer.FrozenCheckoutRecord;
import org.springframework.stereotype.Repository;

import com.paytech.commerce.core.dal.mapper.order.OrderContextMapper;
import com.paytech.commerce.domain.customer.CheckoutLegalView;

import lombok.AllArgsConstructor;

/** 冻结上下文及其法律展示的 MyBatis 查询。 */
@Repository
@AllArgsConstructor
public class OrderContextRepositoryImpl implements OrderContextRepository {
    private final CustomerCheckoutMapper preparationMapper;
    private final OrderContextMapper orderContextMapper;

    /** 插入本表事实，唯一约束失败由外层事务整体回滚。 */
    @Override
    public void insert(FrozenCheckoutRecord row) {
        if (preparationMapper.insertContext(row) != 1) {
            throw new ErrorException("准备事实未成功保存");
        }
    }

    /** 仅组合当前展示所需的三张表，不读取价格、税快照或 Risk 引用。 */
    @Override
    public CheckoutLegalView findCheckoutLegal(long orderId) {
        return orderContextMapper.findCheckoutLegal(orderId);
    }
    /** 读取原订单的三表冻结事实。 */
    @Override
    public FrozenCheckoutRecord findFrozen(long orderId) {
        return preparationMapper.frozen(orderId);
    }
}
