package com.paytech.acquiring.test.mock.external;

import java.util.Collections;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.paytech.acquiring.test.mocker.external.CheckoutCommercialMocker;
import com.paytech.commercial.api.checkout.CheckoutContextApi;
import com.paytech.commercial.api.checkout.request.ResolveCheckoutContextRequestBody;
import com.paytech.commercial.api.checkout.response.ResolveCheckoutContextResponseBody;

import lombok.AllArgsConstructor;

/** 独立 Mock 服务直接实现 commercial 结账上下文解析合同；commercial 没有 Context Path，不加路径前缀。 */
@RestController
@TestComponent
@AllArgsConstructor
public class MockCheckoutContextApi implements CheckoutContextApi {
    private final CheckoutCommercialMocker commercial;

    /** 记录全部 X-Caller-Service 值后委托 Mocker；按配置延迟，故障时写出与 Problem Body 一致的 Request ID。 */
    @Override
    public ResolveCheckoutContextResponseBody resolve(String callerService, ResolveCheckoutContextRequestBody request) {
        var attributes = (ServletRequestAttributes)RequestContextHolder.currentRequestAttributes();
        var callerServices = Collections.list(attributes.getRequest().getHeaders("X-Caller-Service"));
        CheckoutCommercialMocker.Reply reply = commercial.next(request, callerServices);
        commercial.notifyObserver(request);
        delay(reply);
        if (reply.isFault()) {
            attributes.getResponse().setHeader("X-Request-Id", CheckoutCommercialMocker.ERROR_REQUEST_ID);
            throw reply.fault();
        }
        return reply.body();
    }

    /** 模拟 commercial 处理耗时，用于超过 acquiring 的读取超时。 */
    static void delay(CheckoutCommercialMocker.Reply reply) {
        if (reply.delay().isZero()) {
            return;
        }
        try {
            Thread.sleep(reply.delay());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
