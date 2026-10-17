package com.paytech.platform.support.test.util;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Primary;

import com.paytech.platform.support.test.builder.CredentialFixture;

/** 仅测试装配使用的 UTC 固定时钟，使数据库微秒边界可重复验证。 */
@Primary
@TestComponent
public class TestClock extends Clock {

    /** 保持生产时间合同的 UTC 时区。 */
    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    /** 切换展示时区不改变本例认证时点。 */
    @Override
    public Clock withZone(ZoneId zone) {
        return Clock.fixed(instant(), zone);
    }

    /** 所有用例均显式围绕同一时点准备独立数据库事实。 */
    @Override
    public Instant instant() {
        return CredentialFixture.NOW;
    }
}
