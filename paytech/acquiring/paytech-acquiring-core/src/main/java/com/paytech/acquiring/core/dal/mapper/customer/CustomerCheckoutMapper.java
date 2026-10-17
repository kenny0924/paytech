package com.paytech.acquiring.core.dal.mapper.customer;

import com.paytech.acquiring.core.dal.dataobject.customer.FrozenCheckoutRecord;

/** 准备事实的三表查询与本地写入；写入由 Service 的短事务组合。 */
public interface CustomerCheckoutMapper {
    /** 同一订单的商业上下文、法律事实和合同。 */
    FrozenCheckoutRecord frozen(long orderId);
    /** 商业上下文。 */
    int insertContext(FrozenCheckoutRecord row);
    /** 卖方和源法律文档。 */
    int insertLegal(FrozenCheckoutRecord row);
    /** 当前订单展示的合同。 */
    int insertContract(FrozenCheckoutRecord row);
}
