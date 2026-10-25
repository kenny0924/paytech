package com.paytech.client.master.auto.ext.http.entityadmin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.client.master.api.entityadmin.ClientMasterEntityAdminApi;
import com.paytech.client.master.api.enums.AcquiringQualification;
import com.paytech.client.master.api.enums.EntityAdminErrorCode;
import com.paytech.client.master.api.enums.PartnerRoleCode;
import com.paytech.client.master.domain.enums.BusinessChangeOperation;
import com.paytech.client.master.domain.enums.BusinessChangeRecordRecordType;
import com.paytech.client.master.domain.enums.BusinessChangeSnapshotFormat;
import com.paytech.client.master.domain.enums.EntitySourceRelationSourceType;
import com.paytech.client.master.domain.enums.OperatingEntityStatus;
import com.paytech.client.master.test.base.entityadmin.AbstractEntityAdminApiTest;
import com.paytech.client.master.test.checker.checkout.CheckoutCandidateResponseChecker;
import com.paytech.foundation.common.error.CommonErrorCode;

/**
 * 运营主体（OE）列表、详情与收单资质修改：真实 HTTP 进入，根 CAS、快照与资质真实落库，资源锁走本机 Redis。
 *
 * @see ClientMasterEntityAdminApi#listOperatingEntities(String, String, String, String, String, Integer, Integer)
 * @see ClientMasterEntityAdminApi#getOperatingEntity(String, String, String)
 * @see ClientMasterEntityAdminApi#changeAcquiringQualification(String, String, String,
 *      com.paytech.client.master.api.entityadmin.request.ChangeAcquiringQualificationRequestBody)
 */
class OperatingEntityAdminHttpTest extends AbstractEntityAdminApiTest {
    private static final BusinessChangeRecordRecordType OE = BusinessChangeRecordRecordType.OPERATING_ENTITY;

    /** 列表按 entityNo 升序，带法定名称、来源、资质、提供方与根修订号；筛选与分页参数生效，非法值 400。 */
    @Test
    void test_listAndFilter() {
        adminGet("/operating-entities").assertOk().assertText("/total", "2").assertText("/items/0/entityNo", "AE991")
            .assertText("/items/0/legalName", "Synthetic Entity One LLC").assertText("/items/0/operatingCountry", "AE")
            .assertText("/items/0/status", OperatingEntityStatus.ACTIVE.getCode())
            .assertText("/items/0/sourceType", EntitySourceRelationSourceType.PARTNER.getCode())
            .assertText("/items/0/acquiringQualification", AcquiringQualification.PAYFAC.getCode())
            .assertText("/items/0/provider/localPartnerId", Long.toString(PARTNER_ONE))
            .assertText("/items/0/provider/legalName", "Synthetic Entity One LLC").assertText("/items/0/changeVersion", "1")
            .assertText("/items/1/acquiringQualification", AcquiringQualification.MERCHANT_RESALE_APPROVED.getCode());
        adminGet("/operating-entities?sourceType=" + EntitySourceRelationSourceType.CLIENT.getCode()).assertOk()
            .assertText("/total", "0");
        adminGet("/operating-entities?country=AE&status=" + OperatingEntityStatus.ACTIVE.getCode() + "&page=2&size=20").assertOk()
            .assertText("/total", "2").assertText("/page", "2").assertText("/size", "20");
        adminGet("/operating-entities?status=UNKNOWN").assertProblem(400, CommonErrorCode.INVALID_REQUEST.getCode());
        adminGet("/operating-entities?size=200").assertProblem(400, CommonErrorCode.INVALID_REQUEST.getCode());
    }

    /** 详情含当前来源、提供方角色、准入范围与卖方披露；不存在或编号非法 404。 */
    @Test
    void test_detail() {
        adminGet("/operating-entities/AE992").assertOk().assertText("/partyId", "910000000000202")
            .assertText("/sourceRelation/sourceRelationId", "910000000000203")
            .assertText("/sourceRelation/sourcePartnerId", Long.toString(PARTNER_TWO))
            .assertText("/provider/partnerNo", "CCTQUERY-LP-250")
            .assertTexts("/provider/roles", PartnerRoleCode.OE_PROVIDER.getCode(), PartnerRoleCode.REFERRER.getCode())
            .assertText("/scopes/0/mccCode", "5734").assertText("/sellerDisclosures/0/locale", "en-AE")
            .assertText("/changeVersion", "1");
        adminGet("/operating-entities/AE999").assertProblem(404, EntityAdminErrorCode.OPERATING_ENTITY_NOT_FOUND.getCode());
        adminGet("/operating-entities/ae1").assertProblem(404, EntityAdminErrorCode.OPERATING_ENTITY_NOT_FOUND.getCode());
    }

    /** 修改资质：根 CAS 1→2，写 UPDATE 快照（操作者、原因），资质落库，响应即修改后详情。 */
    @Test
    void test_changeQualification() {
        adminPut("/operating-entities/AE991/acquiring-qualification", qualification(AcquiringQualification.MERCHANT.getCode(), 1))
            .assertOk().assertText("/acquiringQualification", AcquiringQualification.MERCHANT.getCode())
            .assertText("/changeVersion", "2");

        assertEquals(AcquiringQualification.MERCHANT.getCode(), data.acquiringQualification("AE991"));
        assertEquals(2L, data.rootVersion(OE, "AE991"));
        var snapshots = data.snapshots(OE, "AE991");
        assertEquals(1, snapshots.size());
        assertEquals(2L, ((Number) snapshots.getFirst().get("version_no")).longValue());
        assertEquals(BusinessChangeOperation.UPDATE.getCode(), snapshots.getFirst().get("operation_type"));
        assertEquals(BusinessChangeSnapshotFormat.ENTITY_NO.getCode(), snapshots.getFirst().get("snapshot_format"));
        assertEquals(OPERATOR_ID, ((Number) snapshots.getFirst().get("create_by")).longValue());
        String snapshot = snapshots.getFirst().get("snapshot_json").toString();
        assertTrue(snapshot.contains("Acquirer approved programme") && snapshot.contains("integrityHash"), snapshot);
        // 候选查询原样返回新资质，是否承接由 commercial 判断。
        post(CANDIDATES, request(), headers()).assertOk().assertCandidates("AE991", "AE992")
            .assertText("/candidates/0/acquiringQualification", AcquiringQualification.MERCHANT.getCode());
    }

    /** 版本过期 409、资质取值非法 422、缺原因 422 字段错误、OE 不存在 404；均不写库。 */
    @Test
    void test_conflictAndInvalidParameter() {
        String path = "/operating-entities/AE991/acquiring-qualification";
        adminPut(path, qualification(AcquiringQualification.MERCHANT.getCode(), 2))
            .assertProblem(409, EntityAdminErrorCode.ENTITY_CHANGE_VERSION_CONFLICT.getCode());
        adminPut(path, qualification("SUPER_ACQUIRER", 1)).assertProblem(422, EntityAdminErrorCode.ENTITY_ATTRIBUTE_INVALID.getCode());
        var missingReason = qualification(AcquiringQualification.MERCHANT.getCode(), 1);
        missingReason.remove("reason");
        adminPut(path, missingReason).assertProblem(422, CommonErrorCode.VALIDATION_ERROR.getCode()).assertFieldError("reason");
        adminPut("/operating-entities/AE999/acquiring-qualification", qualification(AcquiringQualification.MERCHANT.getCode(), 1))
            .assertProblem(404, EntityAdminErrorCode.OPERATING_ENTITY_NOT_FOUND.getCode());

        assertEquals(AcquiringQualification.PAYFAC.getCode(), data.acquiringQualification("AE991"));
        assertEquals(1L, data.rootVersion(OE, "AE991"));
        assertEquals(List.of(), data.snapshots(OE, "AE991"));
    }

    /** 历史手工 OE 没有根：详情按版本 1 返回，修改时同一事务补建 V1 根并推进到 2。 */
    @Test
    void test_missingRootCreated() {
        data.deleteRoot(OE, "AE992");
        adminGet("/operating-entities/AE992").assertOk().assertText("/changeVersion", "1");

        adminPut("/operating-entities/AE992/acquiring-qualification", qualification(AcquiringQualification.PAYFAC.getCode(), 1))
            .assertOk().assertText("/changeVersion", "2");

        assertEquals(2L, data.rootVersion(OE, "AE992"));
        assertEquals(1, data.snapshots(OE, "AE992").size());
    }

    /** 同一 OE 的资源锁被占用时返回 409 CONCURRENT_REQUEST，不写库。 */
    @Test
    void test_lockContention() {
        var results = holding("client-master:operating-entity:AE991", () -> List.of(adminPut(
            "/operating-entities/AE991/acquiring-qualification", qualification(AcquiringQualification.MERCHANT.getCode(), 1))));

        results.getFirst().assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST.getCode());
        assertEquals(AcquiringQualification.PAYFAC.getCode(), data.acquiringQualification("AE991"));
        assertEquals(1L, data.rootVersion(OE, "AE991"));
    }

    /** 缺少 Gateway 会话时在读取前拒绝。 */
    @Test
    void test_sessionRequired() {
        var headers = adminHeaders();
        headers.remove("X-Gateway-Session-Id");
        new CheckoutCandidateResponseChecker(invoker.get(ADMIN + "/operating-entities", headers), json, ADMIN_REQUEST_ID)
            .assertProblem(400, CommonErrorCode.INVALID_REQUEST.getCode());
    }
}
