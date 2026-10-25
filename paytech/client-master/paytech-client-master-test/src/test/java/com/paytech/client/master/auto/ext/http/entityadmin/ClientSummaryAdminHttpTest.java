package com.paytech.client.master.auto.ext.http.entityadmin;

import org.junit.jupiter.api.Test;

import com.paytech.client.master.api.entityadmin.ClientMasterEntityAdminApi;
import com.paytech.client.master.api.enums.CheckoutCandidateErrorCode;
import com.paytech.client.master.domain.enums.ClientStatus;
import com.paytech.client.master.domain.enums.ClientType;
import com.paytech.client.master.test.base.entityadmin.AbstractEntityAdminApiTest;
import com.paytech.client.master.test.mocker.checkout.CheckoutCandidateFixture;

/**
 * 资格登记前的 Client 主体核对。
 *
 * @see ClientMasterEntityAdminApi#getClientSummary(String, String, String)
 */
class ClientSummaryAdminHttpTest extends AbstractEntityAdminApiTest {
    /** 返回 Client 编号、Party、类型、状态与法定名称；不存在或 ID 非法 404 CLIENT_NOT_FOUND。 */
    @Test
    void test_summary() {
        adminGet("/clients/" + CheckoutCandidateFixture.CLIENT_ID + "/summary").assertOk()
            .assertText("/clientNo", "CCTQUERY-CLIENT").assertText("/partyId", "910000000000002")
            .assertText("/clientType", ClientType.ORGANIZATION.getCode()).assertText("/status", ClientStatus.ACTIVE.getCode())
            .assertText("/legalName", "Synthetic Client LLC");
        adminGet("/clients/910000000000999/summary").assertProblem(404, CheckoutCandidateErrorCode.CLIENT_NOT_FOUND.getCode());
        adminGet("/clients/abc/summary").assertProblem(404, CheckoutCandidateErrorCode.CLIENT_NOT_FOUND.getCode());
    }
}
