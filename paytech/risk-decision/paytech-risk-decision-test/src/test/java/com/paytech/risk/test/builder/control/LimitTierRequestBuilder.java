package com.paytech.risk.test.builder.control;

import java.util.List;

import com.paytech.risk.control.core.enums.LimitTier;
import com.paytech.risk.control.core.enums.UnderwritingClientType;
import com.paytech.risk.decision.api.control.request.DecideClientTierChangeRequestBody;
import com.paytech.risk.decision.api.control.request.MigrateClientTiersRequestBody;
import com.paytech.risk.decision.api.control.request.RequestClientTierChangeRequestBody;
import com.paytech.risk.decision.api.control.request.TierChangeChecklistBody;
import com.paytech.risk.decision.api.control.request.TierMigrationClientBody;
import com.paytech.risk.decision.api.control.request.WithdrawClientTierChangeRequestBody;

/**
 * 限额档位请求体构造
 * <li>{@link #checklist()} 是全部满足的上线复核清单；负例由用例显式覆盖字段，不修复非法值。</li>
 * <li>只构造对象，不访问数据库或网络。</li>
 */
public final class LimitTierRequestBuilder {
    /** 资格已批准。 */
    public static final String QUALIFICATION_APPROVED = "APPROVED";

    /** 纯构造工具。 */
    private LimitTierRequestBuilder() { }

    /** 变更申请：给定期望版本与目标档位，理由固定，不带清单。 */
    public static RequestClientTierChangeRequestBody request(int expectedTierVersion, LimitTier toTier) {
        RequestClientTierChangeRequestBody body = new RequestClientTierChangeRequestBody();
        body.setExpectedTierVersion(expectedTierVersion);
        body.setToTier(toTier.getCode());
        body.setReason("测试变更理由");
        return body;
    }

    /** 上线复核申请：VERIFICATION → OBSERVATION，带上线说明与全部满足的清单。 */
    public static RequestClientTierChangeRequestBody goLive(int expectedTierVersion) {
        RequestClientTierChangeRequestBody body = request(expectedTierVersion, LimitTier.OBSERVATION);
        body.setGoLiveNote("生产验证完成，渠道报备已回执");
        body.setChecklist(checklist());
        return body;
    }

    /** 全部满足的上线复核清单。 */
    public static TierChangeChecklistBody checklist() {
        TierChangeChecklistBody body = new TierChangeChecklistBody();
        body.setQualificationStatus(QUALIFICATION_APPROVED);
        body.setQualificationValidTo("2027-10-30T00:00:00Z");
        body.setUnderwritingVersionNo(1);
        body.setAgreementId("5100001");
        body.setAgreementStatus("ACTIVE");
        body.setEnabledPaymentCapabilities("CARD,APPLE_PAY");
        body.setPendingPaymentCapabilities("");
        body.setChannelRegistrationConfirmed(Boolean.TRUE);
        body.setIntegrationConfirmed(Boolean.TRUE);
        body.setProductionVerified(Boolean.TRUE);
        body.setOpenIssuesCleared(Boolean.TRUE);
        return body;
    }

    /** 撤回请求。 */
    public static WithdrawClientTierChangeRequestBody withdraw(int expectedVersion) {
        WithdrawClientTierChangeRequestBody body = new WithdrawClientTierChangeRequestBody();
        body.setExpectedVersion(expectedVersion);
        return body;
    }

    /** 审批或驳回请求。 */
    public static DecideClientTierChangeRequestBody decide(int expectedVersion, String comment) {
        DecideClientTierChangeRequestBody body = new DecideClientTierChangeRequestBody();
        body.setExpectedVersion(expectedVersion);
        body.setComment(comment);
        return body;
    }

    /** 迁移请求：执行人与理由固定。 */
    public static MigrateClientTiersRequestBody migrate(long executedBy, TierMigrationClientBody... clients) {
        MigrateClientTiersRequestBody body = new MigrateClientTiersRequestBody();
        body.setExecutedBy(Long.toString(executedBy));
        body.setReason("上线前存量可交易 Client 迁移");
        body.setClients(List.of(clients));
        return body;
    }

    /** 一个待迁移 Client。 */
    public static TierMigrationClientBody client(long clientId) {
        TierMigrationClientBody body = new TierMigrationClientBody();
        body.setClientId(Long.toString(clientId));
        body.setClientDisplayName("Migrated Client " + clientId);
        body.setClientNo("CL-M-" + clientId);
        body.setClientType(UnderwritingClientType.ORGANIZATION.getCode());
        body.setJurisdictionCode("AE");
        return body;
    }
}
