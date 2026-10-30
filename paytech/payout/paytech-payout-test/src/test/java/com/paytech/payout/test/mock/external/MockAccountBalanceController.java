package com.paytech.payout.test.mock.external;

import java.util.List;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.account.api.BalanceQueryApi;
import com.paytech.account.api.response.ClientBalanceResponseBody;
import com.paytech.account.api.response.PartnerBalanceResponseBody;
import com.paytech.payout.test.mocker.external.AccountBalanceMocker;

import lombok.AllArgsConstructor;

/** 仅存在于独立测试 HTTP 服务的 account 余额接口（提供方 BalanceQueryApi）实现，委托 AccountBalanceMocker；payout 不调用单查询。 */
@RestController
@TestComponent
@AllArgsConstructor
public class MockAccountBalanceController implements BalanceQueryApi {
    private final AccountBalanceMocker account;

    /** 单查询不在 payout 调用范围，记为意外调用。 */
    @Override
    public ClientBalanceResponseBody queryClientBalance(String clientId, String entityNo, String currency) {
        throw account.unexpected("clients:" + clientId + ":" + entityNo + ":" + currency);
    }

    /** 单查询不在 payout 调用范围，记为意外调用。 */
    @Override
    public PartnerBalanceResponseBody queryPartnerBalance(String partnerId, String entityNo, String currency) {
        throw account.unexpected("partners:" + partnerId + ":" + entityNo + ":" + currency);
    }

    /** Client 余额列表。 */
    @Override
    public List<ClientBalanceResponseBody> listClientPositions(String clientId) {
        return account.clientPositions(clientId);
    }

    /** Partner 余额列表。 */
    @Override
    public List<PartnerBalanceResponseBody> listPartnerPositions(String partnerId) {
        return account.partnerPositions(partnerId);
    }
}
