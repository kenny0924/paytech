package com.paytech.platform.support.delivery.integration.provider;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 瞬时供应商输入，一条记录只能有一个收件人，禁止默认 toString。 */
@Getter
@RequiredArgsConstructor
public class EmailProviderRequest {

    private final String requestNo;

    private final String email;

    private final String subject;

    private final String body;
}
