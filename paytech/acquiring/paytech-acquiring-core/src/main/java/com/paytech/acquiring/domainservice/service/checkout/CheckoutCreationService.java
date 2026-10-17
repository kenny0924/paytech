package com.paytech.acquiring.domainservice.service.checkout;

import com.paytech.acquiring.domain.checkout.CreateCheckoutContext;

/** 基础 Checkout 的本地原子保存。 */
public interface CheckoutCreationService {
    /** REQUIRED 加入当前事务或新建本地事务，一次保存五类基础事实。 */
    void saveCreateCheckoutContext(CreateCheckoutContext context);
}
