package com.paytech.client.master.auto.ext.http.entityadmin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.paytech.client.master.api.entityadmin.ClientMasterEntityAdminApi;
import com.paytech.client.master.api.enums.EntityAdminErrorCode;
import com.paytech.client.master.api.enums.PartnerRoleCode;
import com.paytech.client.master.domain.enums.BusinessChangeRecordRecordType;
import com.paytech.client.master.domain.enums.EntitySourceRelationStatus;
import com.paytech.client.master.domain.enums.LocalPartnerStatus;
import com.paytech.client.master.domain.enums.PartnerRoleStatus;
import com.paytech.client.master.domain.enums.PartyRoleCode;
import com.paytech.client.master.domain.enums.PartyRoleStatus;
import com.paytech.client.master.test.base.entityadmin.AbstractEntityAdminApiTest;
import com.paytech.foundation.common.error.CommonErrorCode;

/**
 * Local Partner 列表、详情与角色整体替换：真实 HTTP 进入，t_partner_role / t_party_role、根 CAS 与快照真实落库。
 *
 * @see ClientMasterEntityAdminApi#listLocalPartners(String, String, String, String, Integer, Integer)
 * @see ClientMasterEntityAdminApi#getLocalPartner(String, String, String)
 * @see ClientMasterEntityAdminApi#replacePartnerRoles(String, String, String,
 *      com.paytech.client.master.api.entityadmin.request.ReplacePartnerRolesRequestBody)
 */
class LocalPartnerAdminHttpTest extends AbstractEntityAdminApiTest {
    private static final BusinessChangeRecordRecordType LP = BusinessChangeRecordRecordType.LOCAL_PARTNER;
    private static final String OE_PROVIDER = PartnerRoleCode.OE_PROVIDER.getCode();
    private static final String REFERRER = PartnerRoleCode.REFERRER.getCode();

    /** 列表按 ID 升序，带角色、提供数与根修订号；角色与状态筛选生效，非法角色 400。 */
    @Test
    void test_listAndFilter() {
        adminGet("/local-partners").assertOk().assertText("/total", "3")
            .assertText("/items/0/localPartnerId", Long.toString(PARTNER_ONE)).assertTexts("/items/0/roles", OE_PROVIDER)
            .assertText("/items/0/providedEntityCount", "1").assertText("/items/0/changeVersion", "1")
            .assertText("/items/0/legalName", "Synthetic Entity One LLC")
            .assertTexts("/items/1/roles", OE_PROVIDER, REFERRER)
            .assertTexts("/items/2/roles", REFERRER).assertText("/items/2/providedEntityCount", "0");
        adminGet("/local-partners?role=" + REFERRER).assertOk().assertText("/total", "2")
            .assertText("/items/0/localPartnerId", Long.toString(PARTNER_TWO))
            .assertText("/items/1/localPartnerId", Long.toString(REFERRER_ONLY));
        adminGet("/local-partners?status=" + LocalPartnerStatus.ACTIVE.getCode() + "&role=" + OE_PROVIDER).assertOk()
            .assertText("/total", "2");
        adminGet("/local-partners?role=SPLIT_PAYEE").assertProblem(400, CommonErrorCode.INVALID_REQUEST.getCode());
    }

    /** 详情含角色与提供的经营主体；不存在或 ID 非法 404。 */
    @Test
    void test_detail() {
        adminGet("/local-partners/" + PARTNER_TWO).assertOk().assertText("/partnerNo", "CCTQUERY-LP-250")
            .assertText("/legalName", "Synthetic Entity Two LLC").assertTexts("/roles", OE_PROVIDER, REFERRER)
            .assertText("/providedEntities/0/entityNo", "AE992")
            .assertText("/providedEntities/0/sourceStatus", EntitySourceRelationStatus.ACTIVE.getCode());
        adminGet("/local-partners/910000000000999").assertProblem(404, EntityAdminErrorCode.LOCAL_PARTNER_NOT_FOUND.getCode());
        adminGet("/local-partners/01").assertProblem(404, EntityAdminErrorCode.LOCAL_PARTNER_NOT_FOUND.getCode());
    }

    /** 增加 REFERRER：新增 ACTIVE 角色与统一角色索引，根 1→2 并写快照。 */
    @Test
    void test_addRole() {
        adminPut("/local-partners/" + PARTNER_ONE + "/roles", roles(1, OE_PROVIDER, REFERRER)).assertOk()
            .assertTexts("/roles", OE_PROVIDER, REFERRER).assertText("/changeVersion", "2");

        assertEquals(List.of(role(OE_PROVIDER, PartnerRoleStatus.ACTIVE, 1L), role(REFERRER, PartnerRoleStatus.ACTIVE, 1L)),
            data.partnerRoles(PARTNER_ONE));
        var index = data.partyRoles(PartyRoleCode.REFERRER, Long.toString(PARTNER_ONE));
        assertEquals(1, index.size());
        assertEquals(910000000000102L, ((Number) index.getFirst().get("party_id")).longValue());
        assertEquals(PartyRoleStatus.ACTIVE.getCode(), index.getFirst().get("status"));
        assertEquals(2L, data.rootVersion(LP, Long.toString(PARTNER_ONE)));
        assertEquals(1, data.snapshots(LP, Long.toString(PARTNER_ONE)).size());
    }

    /** 仍以 ACTIVE 来源关系提供经营主体时不能去掉 OE_PROVIDER（422），不写库。 */
    @Test
    void test_removeProviderWhileProviding() {
        adminPut("/local-partners/" + PARTNER_ONE + "/roles", roles(1, REFERRER))
            .assertProblem(422, EntityAdminErrorCode.ENTITY_ATTRIBUTE_INVALID.getCode());

        assertEquals(List.of(role(OE_PROVIDER, PartnerRoleStatus.ACTIVE, 1L)), data.partnerRoles(PARTNER_ONE));
        assertEquals(1L, data.rootVersion(LP, Long.toString(PARTNER_ONE)));
    }

    /** 来源关系结束后可以去掉 OE_PROVIDER：角色行与统一角色索引一起结束。 */
    @Test
    void test_removeProviderAfterSourcesEnded() {
        data.endPartnerSources(PARTNER_TWO);

        adminPut("/local-partners/" + PARTNER_TWO + "/roles", roles(1, REFERRER)).assertOk().assertTexts("/roles", REFERRER)
            .assertText("/providedEntities/0/sourceStatus", EntitySourceRelationStatus.ENDED.getCode());

        assertEquals(List.of(role(OE_PROVIDER, PartnerRoleStatus.ENDED, null), role(REFERRER, PartnerRoleStatus.ACTIVE, 1L)),
            data.partnerRoles(PARTNER_TWO));
        var index = data.partyRoles(PartyRoleCode.OE_PROVIDER, Long.toString(PARTNER_TWO));
        assertEquals(PartyRoleStatus.ENDED.getCode(), index.getFirst().get("status"));
        assertNull(index.getFirst().get("active_slot"));
        assertNotNull(index.getFirst().get("effective_to"));
    }

    /** 角色为空、重复或不可识别 422；版本过期 409；Partner 不存在 404；均不写库。 */
    @Test
    void test_invalidParameterAndConflict() {
        String path = "/local-partners/" + PARTNER_TWO + "/roles";
        adminPut(path, roles(1)).assertProblem(422, EntityAdminErrorCode.ENTITY_ATTRIBUTE_INVALID.getCode());
        adminPut(path, roles(1, OE_PROVIDER, OE_PROVIDER)).assertProblem(422, EntityAdminErrorCode.ENTITY_ATTRIBUTE_INVALID.getCode());
        adminPut(path, roles(1, PartyRoleCode.SPLIT_PAYEE.getCode()))
            .assertProblem(422, EntityAdminErrorCode.ENTITY_ATTRIBUTE_INVALID.getCode());
        adminPut(path, roles(5, OE_PROVIDER)).assertProblem(409, EntityAdminErrorCode.ENTITY_CHANGE_VERSION_CONFLICT.getCode());
        adminPut("/local-partners/910000000000999/roles", roles(1, OE_PROVIDER))
            .assertProblem(404, EntityAdminErrorCode.LOCAL_PARTNER_NOT_FOUND.getCode());

        assertEquals(List.of(role(OE_PROVIDER, PartnerRoleStatus.ACTIVE, 1L), role(REFERRER, PartnerRoleStatus.ACTIVE, 1L)),
            data.partnerRoles(PARTNER_TWO));
        assertEquals(1L, data.rootVersion(LP, Long.toString(PARTNER_TWO)));
    }

    /** 没有根的 Partner 按版本 1 修改，同一事务补建 V1 根并推进到 2。 */
    @Test
    void test_missingRootCreated() {
        assertNull(data.rootVersion(LP, Long.toString(REFERRER_ONLY)));

        adminPut("/local-partners/" + REFERRER_ONLY + "/roles", roles(1, OE_PROVIDER, REFERRER)).assertOk()
            .assertText("/changeVersion", "2");

        assertEquals(2L, data.rootVersion(LP, Long.toString(REFERRER_ONLY)));
        assertEquals(1, data.partyRoles(PartyRoleCode.OE_PROVIDER, Long.toString(REFERRER_ONLY)).size());
    }

    /** 同一 Partner 的资源锁被占用时返回 409 CONCURRENT_REQUEST，不写库。 */
    @Test
    void test_lockContention() {
        var results = holding("client-master:local-partner:" + PARTNER_ONE,
            () -> List.of(adminPut("/local-partners/" + PARTNER_ONE + "/roles", roles(1, OE_PROVIDER, REFERRER))));

        results.getFirst().assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST.getCode());
        assertEquals(List.of(role(OE_PROVIDER, PartnerRoleStatus.ACTIVE, 1L)), data.partnerRoles(PARTNER_ONE));
    }

    /** 期望的角色行。 */
    private static Map<String, Object> role(String code, PartnerRoleStatus status, Long slot) {
        var row = new java.util.LinkedHashMap<String, Object>();
        row.put("role_code", code);
        row.put("status", status.getCode());
        row.put("active_slot", slot);
        return row;
    }
}
