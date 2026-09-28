package com.paytech.commerce.integration.cct;

/** 创建阶段只询问当前基础可用性，不返回或选择 OE。 */
public interface CctPrecheckClient {
    void verify(long clientId, String consumerCountryCode, String currency);
}
