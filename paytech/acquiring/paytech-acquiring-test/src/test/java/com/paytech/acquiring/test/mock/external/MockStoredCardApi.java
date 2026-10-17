package com.paytech.acquiring.test.mock.external;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.paytech.acquiring.test.mocker.external.StoredCardMocker;
import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import com.paytech.foundation.httpproblem.api.ApiProblemResponse;
import com.paytech.foundation.testsupport.api.MockHttpFault;
import com.paytech.payment.api.card.StoredCardApi;
import com.paytech.payment.api.card.request.InvalidateStoredCardsRequestBody;
import com.paytech.payment.api.card.response.InvalidateStoredCardsResponseBody;
import com.paytech.payment.api.card.response.StoredCardListResponseBody;

import lombok.AllArgsConstructor;

/** 在独立 Mock 服务继承正式已存卡接口，由场景配置 DTO。 */
@RestController
@TestComponent
@AllArgsConstructor
public class MockStoredCardApi implements StoredCardApi {
    private final StoredCardMocker mocker;

    @Override
    public StoredCardListResponseBody listStoredCards(String clientId, String customerId) {
        requestId();
        return mocker.list(clientId, customerId);
    }

    /** 已存卡不存在时按 Payment 合同返回 404。 */
    @Override
    public ResponseEntity<Void> deleteStoredCard(String storedCardToken, String clientId, String customerId) {
        requestId();
        if (!mocker.delete(clientId, customerId, storedCardToken)) {
            throw MockHttpFault.json(HttpStatus.NOT_FOUND.value(), new ApiProblemResponse(ApiProblemProfile.TYPE,
                "Not Found", "已存卡不存在", "NOT_FOUND", "payment-test", "2026-09-08T00:00:00.000000Z", null));
        }
        return ResponseEntity.noContent().build();
    }

    @Override
    public InvalidateStoredCardsResponseBody invalidateStoredCards(String customerId,
        InvalidateStoredCardsRequestBody body) {
        requestId();
        var response = new InvalidateStoredCardsResponseBody();
        response.setInvalidatedCount(mocker.invalidate(body.getClientId(), customerId));
        return response;
    }

    /** 与故障 Problem 中的 requestId 一致，供公共解码核对。 */
    static void requestId() {
        var attributes = (ServletRequestAttributes)RequestContextHolder.currentRequestAttributes();
        attributes.getResponse().setHeader("X-Request-Id", "payment-test");
    }
}
