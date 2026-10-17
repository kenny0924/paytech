package com.paytech.compliance.service.processor;

import static com.paytech.compliance.core.enums.ClientReviewErrorCode.*;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.compliance.core.dal.dataobject.CaseClassification;
import com.paytech.compliance.core.dal.dataobject.DueDiligenceCase;
import com.paytech.compliance.domainservice.port.ComplianceIdentifierGenerator;
import com.paytech.compliance.domainservice.repository.CaseClassificationRepository;
import com.paytech.compliance.domainservice.repository.DueDiligenceCaseRepository;
import com.paytech.compliance.domainservice.service.ClientReviewService;
import com.paytech.compliance.service.processor.request.ReceiveClientReviewClassificationsRequest;
import com.paytech.compliance.service.processor.response.ClientReviewCommandResponse;

import lombok.AllArgsConstructor;

/**
 * I02 接收 CCT 已确认的同一任务分类；确认后不可修改
 * <li>申请、提交编号必须与主任务一致，否则按不存在处理，不暴露其他任务。</li>
 * <li>已接收时只接受完全相同的集合、确认人与确认时间重放；并发首次接收由唯一键回滚后按已接收集合判断。</li>
 */
@Component
@AllArgsConstructor
public class ReceiveClientReviewClassificationsProcessor
    extends AbstractProcessor<ReceiveClientReviewClassificationsRequest, ClientReviewCommandResponse> {
    private final Clock clock;
    private final ClientReviewService reviews;
    private final ComplianceIdentifierGenerator ids;
    private final DueDiligenceCaseRepository cases;
    private final CaseClassificationRepository classifications;

    /** 恰好一个主要分类，行业与 MCC 组合不重复。 */
    @Override
    protected void validate(ReceiveClientReviewClassificationsRequest request) {
        if (request.getClassifications().stream().filter(ReceiveClientReviewClassificationsRequest.Item::isPrimary).count() != 1) {
            throw new BizCheckFailException("分类必须恰好有一个主要分类");
        }
        // 与唯一键 (case, industry, mcc) 一致：主次不同的同一组合也算重复
        long pairs = request.getClassifications().stream().map(item -> item.getIndustryCode() + ":" + item.getMccCode()).distinct().count();
        if (pairs != request.getClassifications().size()) {
            throw new BizCheckFailException("分类中的行业与 MCC 组合重复");
        }
    }

    @Override
    protected ClientReviewCommandResponse processRequest(ReceiveClientReviewClassificationsRequest request) {
        // 归属与当前状态
        DueDiligenceCase main = cases.findById(request.getCaseId());
        if (main == null || !RegisterClientReviewProcessor.NAMESPACE.equals(main.getSourceNamespace()) || !"CLIENT".equals(main.getRoleType())
            || !Long.valueOf(request.getApplicationId()).equals(main.getSourceApplicationId())
            || !request.getSubmissionId().equals(main.getSourceSubmissionId())) {
            throw new FailException(CLIENT_REVIEW_NOT_FOUND);
        }
        List<CaseClassification> existing = classifications.findByCase(main.getCaseId());
        if (!existing.isEmpty()) {
            return replay(request, main, existing);
        }
        if (!Integer.valueOf(1).equals(main.getActiveSlot())) {
            throw new FailException(CLIENT_REVIEW_CLOSED);
        }

        // 保存分类
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        try {
            reviews.saveClassifications(main, rows(request, now), now);
        } catch (DuplicateKeyException collision) {
            return replay(request, main, classifications.findByCase(main.getCaseId()));
        }
        return new ClientReviewCommandResponse(main.getCaseId(), main.getStatus());
    }

    /** 构造分类行。 */
    List<CaseClassification> rows(ReceiveClientReviewClassificationsRequest request, Instant now) {
        return request.getClassifications().stream().map(item -> {
            CaseClassification row = new CaseClassification();
            row.setCaseClassificationId(ids.nextIdentifier());
            row.setCaseId(request.getCaseId());
            row.setIndustryCode(item.getIndustryCode());
            row.setMccCode(item.getMccCode());
            row.setPrimarySlot(item.isPrimary() ? 1 : null);
            row.setConfirmedBy(request.getConfirmedBy());
            row.setConfirmedTime(request.getConfirmedAt().truncatedTo(ChronoUnit.MICROS));
            row.setCreateTime(now);
            return row;
        }).toList();
    }

    /** 已接收：集合、确认人与确认时间完全一致才视为重放。 */
    ClientReviewCommandResponse replay(ReceiveClientReviewClassificationsRequest request, DueDiligenceCase main,
        List<CaseClassification> existing) {
        Set<String> actual = new HashSet<>();
        existing.forEach(row -> actual.add(row.getIndustryCode() + ":" + row.getMccCode() + ":" + Integer.valueOf(1).equals(row.getPrimarySlot())));
        Instant confirmedAt = request.getConfirmedAt().truncatedTo(ChronoUnit.MICROS);
        boolean same = actual.equals(fingerprint(request)) && existing.stream()
            .allMatch(row -> row.getConfirmedBy() == request.getConfirmedBy() && confirmedAt.equals(row.getConfirmedTime()));
        if (!same) {
            throw new FailException(CLIENT_REVIEW_CLASSIFICATION_CONFLICT);
        }
        return new ClientReviewCommandResponse(main.getCaseId(), main.getStatus());
    }

    /** 请求分类集合的比较键。 */
    static Set<String> fingerprint(ReceiveClientReviewClassificationsRequest request) {
        Set<String> keys = new HashSet<>();
        request.getClassifications().forEach(item -> keys.add(item.getIndustryCode() + ":" + item.getMccCode() + ":" + item.isPrimary()));
        return keys;
    }
}
