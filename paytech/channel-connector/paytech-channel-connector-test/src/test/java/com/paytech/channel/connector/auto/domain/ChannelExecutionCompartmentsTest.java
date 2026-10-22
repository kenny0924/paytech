package com.paytech.channel.connector.auto.domain;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import com.paytech.channel.connector.api.enums.ChannelType;
import com.paytech.channel.connector.protocol.core.configuration.ExternalApiExecutionProperties;
import com.paytech.channel.connector.protocol.ext.outbound.ChannelExecutionCompartments;

/**
 * 两个执行舱的许可互相独立：服务渠道执行舱占满时立即拿不到许可（不排队），支付渠道执行舱不受影响；许可只归还一次。
 *
 * @see ChannelExecutionCompartments#tryEnter(ChannelType)
 */
class ChannelExecutionCompartmentsTest {

    /** 服务执行舱上限 1：第二个许可为空，支付执行舱仍可进入；重复关闭不会多归还。 */
    @Test
    void test_compartmentsIndependent() {
        ExternalApiExecutionProperties properties = new ExternalApiExecutionProperties();
        properties.setPaymentMaxConcurrency(2);
        properties.setServiceMaxConcurrency(1);
        ChannelExecutionCompartments compartments = new ChannelExecutionCompartments(properties);

        var service = compartments.tryEnter(ChannelType.SERVICE);
        var busy = compartments.tryEnter(ChannelType.SERVICE);
        var payment = compartments.tryEnter(ChannelType.PAYMENT);

        assertAll(() -> assertNotNull(service), () -> assertNull(busy), () -> assertNotNull(payment),
            () -> assertEquals(0, compartments.available(ChannelType.SERVICE)),
            () -> assertEquals(1, compartments.available(ChannelType.PAYMENT)));
        service.close();
        service.close();
        payment.close();
        assertAll(() -> assertEquals(1, compartments.available(ChannelType.SERVICE)),
            () -> assertEquals(2, compartments.available(ChannelType.PAYMENT)));
    }
}
