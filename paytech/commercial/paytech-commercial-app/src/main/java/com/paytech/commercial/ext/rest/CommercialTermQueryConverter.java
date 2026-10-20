package com.paytech.commercial.ext.rest;

import static com.paytech.commercial.ext.rest.WireValues.code;
import static com.paytech.commercial.ext.rest.WireValues.codes;
import static com.paytech.commercial.ext.rest.WireValues.id;
import static com.paytech.commercial.ext.rest.WireValues.matching;
import static com.paytech.commercial.ext.rest.WireValues.text;
import static com.paytech.commercial.ext.rest.WireValues.time;

import java.util.List;

import com.paytech.commercial.api.enums.CommercialTermPlanStatus;
import com.paytech.commercial.api.term.response.CommercialTermPlanSummary;
import com.paytech.commercial.api.term.response.CommercialTermVersionSummary;
import com.paytech.commercial.domain.CommercialTermPlanListQuery;
import com.paytech.commercial.domain.CommercialTermPlanSummaryView;
import com.paytech.commercial.domain.CommercialTermVersionListQuery;
import com.paytech.commercial.domain.CommercialTermVersionSummaryView;
import com.paytech.commercial.domain.enums.CommercialTermVersionStatus;
import com.paytech.foundation.web.paging.PagingRequestBody;

/** 商业条款方案 / 版本列表 Wire 与内部查询 / 结果的协议转换，不访问数据库或执行业务校验。 */
final class CommercialTermQueryConverter {
    /** 与 CreateCommercialTermPlanRequestBody.termType 相同的开放代码格式。 */
    static final String TERM_TYPE = "[A-Z][A-Z0-9_]{0,63}";

    /** 纯静态工具。 */
    private CommercialTermQueryConverter() {
    }

    /** 方案列表查询条件。 */
    static CommercialTermPlanListQuery planQuery(String agreementId, String clientId, String localPartnerId,
        String termType, String status, Integer page, Integer size) {
        return new CommercialTermPlanListQuery(id("agreementId", agreementId), id("clientId", clientId),
            id("localPartnerId", localPartnerId), matching("termType", termType, TERM_TYPE),
            code(CommercialTermPlanStatus.class, "status", status), PagingRequestBody.of(page, size));
    }

    /** 版本列表查询条件。 */
    static CommercialTermVersionListQuery versionQuery(String planId, String agreementId, List<String> status,
        String effectiveFrom, String effectiveTo, Integer page, Integer size) {
        return new CommercialTermVersionListQuery(id("planId", planId), id("agreementId", agreementId),
            codes(CommercialTermVersionStatus.class, "status", status), time("effectiveFrom", effectiveFrom),
            time("effectiveTo", effectiveTo), PagingRequestBody.of(page, size));
    }

    /** 方案列表一行。 */
    static CommercialTermPlanSummary planSummary(CommercialTermPlanSummaryView view) {
        CommercialTermPlanSummary summary = new CommercialTermPlanSummary();
        summary.setPlanId(Long.toString(view.getPlanId()));
        summary.setPlanCode(view.getPlanCode());
        summary.setTermType(view.getTermType());
        summary.setAgreementId(text(view.getAgreementId()));
        summary.setEntityNo(view.getEntityNo());
        summary.setClientId(text(view.getClientId()));
        summary.setLocalPartnerId(text(view.getLocalPartnerId()));
        summary.setStatus(text(view.getStatus()));
        summary.setChangeVersion(Long.toString(view.getChangeVersion()));
        if (view.getActiveVersion() != null) {
            summary.setActiveVersionId(Long.toString(view.getActiveVersion().getVersionId()));
            summary.setActiveVersionNo(Long.toString(view.getActiveVersion().getVersionNo()));
            summary.setActiveEffectiveFrom(text(view.getActiveVersion().getEffectiveFrom()));
        }
        if (view.getScheduledVersion() != null) {
            summary.setScheduledVersionId(Long.toString(view.getScheduledVersion().getVersionId()));
            summary.setScheduledVersionNo(Long.toString(view.getScheduledVersion().getVersionNo()));
            summary.setScheduledEffectiveFrom(text(view.getScheduledVersion().getEffectiveFrom()));
        }
        summary.setCreateTime(text(view.getCreateTime()));
        summary.setUpdateTime(text(view.getUpdateTime()));
        return summary;
    }

    /** 版本列表一行。 */
    static CommercialTermVersionSummary versionSummary(CommercialTermVersionSummaryView view) {
        CommercialTermVersionSummary summary = new CommercialTermVersionSummary();
        summary.setVersionId(Long.toString(view.getVersionId()));
        summary.setPlanId(Long.toString(view.getPlanId()));
        summary.setPlanCode(view.getPlanCode());
        summary.setTermType(view.getTermType());
        summary.setAgreementId(text(view.getAgreementId()));
        summary.setEntityNo(view.getEntityNo());
        summary.setClientId(text(view.getClientId()));
        summary.setLocalPartnerId(text(view.getLocalPartnerId()));
        summary.setVersionNo(Long.toString(view.getVersionNo()));
        summary.setStatus(text(view.getStatus()));
        summary.setEffectiveFrom(text(view.getEffectiveFrom()));
        summary.setEffectiveTo(text(view.getEffectiveTo()));
        summary.setRoundingMode(text(view.getRoundingMode()));
        summary.setPricingModel(text(view.getPricingModel()));
        summary.setFeeTaxTreatment(text(view.getFeeTaxTreatment()));
        summary.setMakerAccountId(text(view.getReview().getMakerAccountId()));
        summary.setCheckerAccountId(text(view.getReview().getCheckerAccountId()));
        summary.setSubmittedTime(text(view.getReview().getSubmittedTime()));
        summary.setPriceCount(view.getPriceCount());
        summary.setChangeVersion(Long.toString(view.getChangeVersion()));
        summary.setUpdateTime(text(view.getUpdateTime()));
        return summary;
    }
}
