package com.paytech.client.master.auto.ext.http.onboarding;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.client.master.domain.enums.ClientType;
import com.paytech.client.master.domain.enums.PartyRoleCode;
import com.paytech.client.master.domain.enums.PartyRoleStatus;
import com.paytech.client.master.test.base.ClientMasterMockTestBase;
import com.paytech.client.master.test.dal.checkout.CheckoutCandidateTestMapper;

import tools.jackson.databind.json.JsonMapper;

/**
 * 新建 Client（C01 首次开始申请）时与 Client 同一事务写入统一角色索引的 CLIENT 行；提交后照常交 Platform 开通工作区。
 *
 * @see com.paytech.client.contract.api.onboarding.ClientDraftApi#create
 */
class ClientCreationPartyRoleTest extends ClientMasterMockTestBase {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private CheckoutCandidateTestMapper data;

    /** 新 Client 有且只有一条 ACTIVE 的 CLIENT 角色索引，Party 与 Client 一致。 */
    @Test
    void test_clientRoleWrittenWithClient() {
        owners.mockProvisionAccepted();

        var created = http.post().uri("/client-onboarding/v1/applications").headers(headers -> {
            headers.set("X-Caller-Service", "client-console-bff");
            headers.set("X-Gateway-Actor-Subject", Long.toString(ACCOUNT_ID));
            headers.set("X-Gateway-Session-Id", Long.toString(SESSION_ID));
            headers.set("X-Request-Id", "cct-party-role-test");
        }).header("Content-Type", "application/json").body("{\"clientType\":\"" + ClientType.ORGANIZATION.getCode() + "\"}")
            .exchange().expectBody(byte[].class).returnResult();

        assertEquals(201, created.getStatus().value(), new String(created.getResponseBody()));
        long clientId = Long.parseLong(JSON.readTree(created.getResponseBody()).path("clientId").asString());
        var roles = data.partyRoles(PartyRoleCode.CLIENT, Long.toString(clientId));
        assertEquals(1, roles.size());
        assertEquals(data.clientPartyId(clientId), ((Number) roles.getFirst().get("party_id")).longValue());
        assertEquals(PartyRoleStatus.ACTIVE.getCode(), roles.getFirst().get("status"));
        waitUntil(() -> owners.provisionRequests().size() == 1, Duration.ofSeconds(10));
    }
}
