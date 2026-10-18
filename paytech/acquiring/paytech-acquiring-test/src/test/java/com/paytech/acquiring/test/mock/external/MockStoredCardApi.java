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
import com.paytech.card.api.storedcard.StoredCardApi;
import com.paytech.card.api.storedcard.request.InvalidateStoredCardsRequestBody;
import com.paytech.card.api.storedcard.response.InvalidateStoredCardsResponseBody;
import com.paytech.card.api.storedcard.response.StoredCardListResponseBody;

import lombok.AllArgsConstructor;

/** 在独立 Mock 服务以 card-service 继承正式已存卡接口（card-api，无 context path），由场景配置 DTO。 */
@RestController
@TestComponent
@AllArgsConstructor
public class MockStoredCardApi implements StoredCardApi {
    private final StoredCardMocker mocker;

    @Override
    public StoredCardListResponseBody listStoredCards(String caller, String clientId, String customerId) {
        requireCaller(caller);
        requestId();
        return mocker.list(clientId, customerId);
    }

    /** 已存卡不存在时按 card 合同返回 404。 */
    @Override
    public ResponseEntity<Void> deleteStoredCard(String caller, String storedCardToken, String clientId,
        String customerId) {
        requireCaller(caller);
        requestId();
        if (!mocker.delete(clientId, customerId, storedCardToken)) {
            throw MockHttpFault.json(HttpStatus.NOT_FOUND.value(), new ApiProblemResponse(ApiProblemProfile.TYPE,
                "Not Found", "已存卡不存在", "NOT_FOUND", "card-test", "2026-09-08T00:00:00.000000Z", null));
        }
        return ResponseEntity.noContent().build();
    }

    @Override
    public InvalidateStoredCardsResponseBody invalidateStoredCards(String caller, String customerId,
        InvalidateStoredCardsRequestBody body) {
        requireCaller(caller);
        requestId();
        var response = new InvalidateStoredCardsResponseBody();
        response.setInvalidatedCount(mocker.invalidate(body.getClientId(), customerId));
        return response;
    }

    /** card 只接受登记的调用方（design/05 §4）：本服务必须以 acquiring-service 身份调用。 */
    static void requireCaller(String caller) {
        if (!"acquiring-service".equals(caller)) {
            throw new AssertionError("已存卡调用方不符: " + caller);
        }
    }

    /** 与故障 Problem 中的 requestId 一致，供公共解码核对。 */
    static void requestId() {
        var attributes = (ServletRequestAttributes)RequestContextHolder.currentRequestAttributes();
        attributes.getResponse().setHeader("X-Request-Id", "card-test");
    }
}
