package com.paytech.consumer.service.processor.request;

/**
 * 按主键读取 Customer 的内部输入
 *
 * @param customerId Customer 主键
 */
public record CustomerIdRequest(long customerId) {}
