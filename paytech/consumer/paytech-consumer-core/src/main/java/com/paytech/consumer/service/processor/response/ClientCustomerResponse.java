package com.paytech.commerce.service.processor.customer.response;

import com.paytech.commerce.core.dal.dataobject.customer.Customer;

/**
 * Customer 操作结果
 *
 * @param customer 本次读取或保存的 Customer
 * @param created 是否本次新建
 */
public record ClientCustomerResponse(Customer customer, boolean created) {}
