package com.paytech.consumer.service.processor.request;

/**
 * 存卡资格查询的内部输入
 *
 * @param clientId 订单所属 Client
 * @param customerId 订单关联的 Customer
 */
public record CardSavingEligibilityRequest(long clientId, long customerId) {}
