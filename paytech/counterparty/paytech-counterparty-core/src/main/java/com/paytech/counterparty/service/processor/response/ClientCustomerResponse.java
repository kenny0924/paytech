package com.paytech.counterparty.service.processor.response;

import com.paytech.counterparty.core.dal.dataobject.Customer;

/**
 * Customer 操作结果
 *
 * @param customer 本次读取或保存的 Customer
 * @param created 是否本次新建
 */
public record ClientCustomerResponse(Customer customer, boolean created) {}
