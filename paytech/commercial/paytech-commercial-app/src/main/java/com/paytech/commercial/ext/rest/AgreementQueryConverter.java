package com.paytech.commercial.ext.rest;

import static com.paytech.commercial.ext.rest.WireValues.code;
import static com.paytech.commercial.ext.rest.WireValues.codes;
import static com.paytech.commercial.ext.rest.WireValues.id;
import static com.paytech.commercial.ext.rest.WireValues.matching;
import static com.paytech.commercial.ext.rest.WireValues.text;

import java.util.List;

import com.paytech.commercial.api.agreement.response.AgreementSummary;
import com.paytech.commercial.domain.AgreementListQuery;
import com.paytech.commercial.domain.AgreementSummaryView;
import com.paytech.commercial.domain.enums.AgreementAgreementType;
import com.paytech.commercial.domain.enums.AgreementStatus;
import com.paytech.foundation.web.paging.PagingRequestBody;

/** 合同列表 Wire 与内部查询 / 结果的协议转换，不访问数据库或执行业务校验。 */
final class AgreementQueryConverter {
    /** 与 CreateAgreementRequestBody.agreementNo 同字符集的前缀。 */
    static final String AGREEMENT_NO_PREFIX = "[A-Za-z0-9._-]{1,64}";

    /** 5 位 OE 编号。 */
    static final String ENTITY_NO = "[A-Z]{2}[0-9]{3}";

    /** 纯静态工具。 */
    private AgreementQueryConverter() {
    }

    /** 列表查询条件。 */
    static AgreementListQuery query(String clientId, String localPartnerId, String agreementType, List<String> status,
        String entityNo, String agreementNo, Integer page, Integer size) {
        return new AgreementListQuery(id("clientId", clientId), id("localPartnerId", localPartnerId),
            code(AgreementAgreementType.class, "agreementType", agreementType),
            codes(AgreementStatus.class, "status", status), matching("entityNo", entityNo, ENTITY_NO),
            matching("agreementNo", agreementNo, AGREEMENT_NO_PREFIX), PagingRequestBody.of(page, size));
    }

    /** 列表一行。 */
    static AgreementSummary summary(AgreementSummaryView view) {
        AgreementSummary summary = new AgreementSummary();
        summary.setAgreementId(Long.toString(view.getAgreementId()));
        summary.setAgreementNo(view.getAgreementNo());
        summary.setAgreementName(view.getAgreementName());
        summary.setAgreementType(text(view.getAgreementType()));
        summary.setStatus(text(view.getStatus()));
        summary.setBusinessMode(text(view.getBusinessMode()));
        summary.setFundMode(text(view.getFundMode()));
        summary.setClientId(text(view.getClientId()));
        summary.setLocalPartnerId(text(view.getLocalPartnerId()));
        summary.setEntityNo(view.getEntityNo());
        summary.setEffectiveFrom(text(view.getEffectiveFrom()));
        summary.setEffectiveTo(text(view.getEffectiveTo()));
        summary.setSignedTime(text(view.getSignedTime()));
        summary.setActivatedTime(text(view.getActivatedTime()));
        summary.setChangeVersion(view.getChangeVersion());
        summary.setUpdateTime(text(view.getUpdateTime()));
        return summary;
    }
}
