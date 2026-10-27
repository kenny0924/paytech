package com.paytech.compliance.service.processor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.paytech.compliance.core.dal.dataobject.RiskFactor;
import com.paytech.compliance.core.enums.ReviewCodes;
import com.paytech.compliance.domain.ClientReviewFamily;
import com.paytech.compliance.domain.ReviewCommandContext;
import com.paytech.compliance.domain.RiskRatingContext;
import com.paytech.compliance.domainservice.service.ClientReviewService;
import com.paytech.compliance.service.processor.request.RateClientReviewRiskRequest;
import com.paytech.compliance.service.processor.response.ClientReviewCommandResponse;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * 手工客户风险评级（design 7.5，S1 手工 CRR）：评级、说明与审核员因子一并替换当前值
 * <li>同一因子代码与取值不得重复；评级变化会清空推荐，四眼时需重新推荐。</li>
 */
@Component
@AllArgsConstructor
public class RateClientReviewRiskProcessor extends AbstractProcessor<RateClientReviewRiskRequest, ClientReviewCommandResponse> {
    /** 允许的因子代码（design 10.2 t_dd_risk_factor）。 */
    static final Set<String> FACTOR_CODES = Set.of("COUNTRY_RISK", "NATIONALITY_RISK", "PEP", "INDUSTRY_RISK", "VOLUME",
        "ENTITY_STRUCTURE", "ADVERSE_MEDIA", "PRIOR_REJECTION", "MANUAL");

    private final ClientReviewService reviews;
    private final ClientReviewCommandSupport support;
    private final ClientReviewFamilyLoader families;

    /** 因子代码在目录内且（代码，取值）不重复。 */
    @Override
    protected void validate(RateClientReviewRiskRequest request) {
        Set<String> seen = new HashSet<>();
        for (RateClientReviewRiskRequest.Factor factor : request.getFactors()) {
            if (!FACTOR_CODES.contains(factor.getFactorCode()) || !seen.add(factor.getFactorCode() + ":" + factor.getFactorValue())) {
                throw new InvalidParameterException("风险因子代码无效或重复");
            }
        }
    }

    /** 案件进行中时保存评级与因子。 */
    @Override
    protected ClientReviewCommandResponse processRequest(RateClientReviewRiskRequest request) {
        ClientReviewFamily family = families.load(request.getCaseId());
        ReviewCommandContext command = support.begin(family, request.getActorId(), request.getOnBehalfOfPartnerId());
        List<RiskFactor> factors = request.getFactors().stream().map(factor -> factor(family, factor, command)).toList();
        reviews.rateRisk(new RiskRatingContext(command, request.getRiskRating(), request.getNote(), factors));
        return ReviewClientEvidenceProcessor.response(family);
    }

    /** 一项审核员因子。 */
    RiskFactor factor(ClientReviewFamily family, RateClientReviewRiskRequest.Factor factor, ReviewCommandContext command) {
        RiskFactor row = new RiskFactor();
        row.setFactorId(support.nextId());
        row.setCaseId(family.getMainCase().getCaseId());
        row.setFactorCode(factor.getFactorCode());
        row.setFactorValue(factor.getFactorValue());
        row.setSource(ReviewCodes.FACTOR_SOURCE_REVIEWER);
        row.setRecordedBy(command.getActorId());
        row.setRecordedTime(command.getNow());
        row.setCreateTime(command.getNow());
        return row;
    }
}
