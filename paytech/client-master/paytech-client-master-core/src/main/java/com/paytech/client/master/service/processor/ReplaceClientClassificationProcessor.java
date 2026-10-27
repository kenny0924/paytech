package com.paytech.client.master.service.processor;

import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.paytech.client.master.core.dal.dataobject.ClientApplicationSubmission;
import com.paytech.client.master.core.dal.dataobject.ClientClassification;
import com.paytech.client.master.core.dal.dataobject.ClientClassificationProfile;
import com.paytech.client.master.domain.enums.ClientApplicationStatus;
import com.paytech.client.master.domain.onboarding.ClientClassificationContext;
import com.paytech.client.master.domain.onboarding.pack.ReferenceDataItem;
import com.paytech.client.master.domainservice.repository.ClientApplicationRepository;
import com.paytech.client.master.domainservice.repository.ClientBusinessClassificationRepository;
import com.paytech.client.master.domainservice.repository.ClientBusinessProfileRepository;
import com.paytech.client.master.domainservice.service.ClientClassificationService;
import com.paytech.client.master.integration.ReferenceDataClient;
import com.paytech.client.master.service.processor.request.ClientClassificationRequest;
import com.paytech.client.master.service.processor.response.ClientClassificationResponse;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.httpproblem.api.ProblemFieldError;
import com.paytech.foundation.httpproblem.api.RequestBodyValidationException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

/**
 * O03 设置分类：整体替换本任务的分类集合，确认前可反复修改
 * <li>与 O04 共用 client-onboarding:review-case:{caseId} 锁；本任务确认后的修改由条件更新最终拒绝。</li>
 * <li>行业必须是 Platform 参考数据中 ACTIVE 的细类（第 2 级），MCC 必须在 Platform 存在且 ACTIVE；组合不重复且恰好一个主要分类。</li>
 * <li>Platform 在锁内、本地事务（ClientClassificationService.replace）之前按编码批量查询；不可用时 503，不写入。</li>
 */
public class ReplaceClientClassificationProcessor
    extends AbstractLockProcessor<ClientClassificationRequest, ClientClassificationResponse> {
    private final Clock clock;
    private final IdGenerator ids;
    private final Duration lockWait;
    private final Duration lockLease;
    private final ReferenceDataClient referenceData;
    private final ClientBusinessProfileRepository profiles;
    private final ClientClassificationService classifications;
    private final ClientApplicationRepository applications;
    private final ClientBusinessClassificationRepository rows;

    /** 锁等待与租约沿用 CCT 既有资源锁配置。 */
    public ReplaceClientClassificationProcessor(Clock clock, IdGenerator ids, Duration lockWait, Duration lockLease,
        ReferenceDataClient referenceData, ClientBusinessProfileRepository profiles, ClientClassificationService classifications,
        ClientApplicationRepository applications, ClientBusinessClassificationRepository rows) {
        this.clock = clock;
        this.ids = ids;
        this.lockWait = lockWait;
        this.lockLease = lockLease;
        this.referenceData = referenceData;
        this.profiles = profiles;
        this.classifications = classifications;
        this.applications = applications;
        this.rows = rows;
    }

    /** 可分类的行业层级：细类。 */
    static final int DETAIL_INDUSTRY_LEVEL = 2;

    /** 组合不重复与恰好一个主要分类只依赖输入；行业与 MCC 在锁内、事务外向 Platform 校验。 */
    @Override
    protected void validate(ClientClassificationRequest request) {
        Set<String> pairs = new HashSet<>();
        long primary = 0;
        for (ClientClassificationRequest.Item item : request.getItems()) {
            if (!pairs.add(item.getIndustryCode() + ":" + item.getMccCode())) {
                throw new FailException(CLASSIFICATION_INVALID);
            }
            primary += item.isPrimary() ? 1 : 0;
        }
        if (request.getItems().isEmpty() || primary != 1) {
            throw new FailException(CLASSIFICATION_INVALID);
        }
    }

    /** 与 O04 共用的任务锁。 */
    @Override
    protected LockCommand lockCommand(ClientClassificationRequest request) {
        return LockCommand.tryLocks(List.of("client-onboarding:review-case:" + request.getCaseId()), lockWait, lockLease);
    }

    @Override
    protected ClientClassificationResponse processWithinLock(ClientClassificationRequest request) {
        // 当前任务与可修改状态
        ClientApplicationSubmission application = GetClientClassificationProcessor.currentApplication(applications, request.getCaseId());
        if (application.getStatus() != ClientApplicationStatus.UNDER_REVIEW) {
            throw new FailException(APPLICATION_NOT_EDITABLE);
        }
        ClientClassificationProfile profile = GetClientClassificationProcessor.classificationProfile(profiles, application);
        if (profile.confirmedFor(request.getCaseId())) {
            throw new FailException(CLASSIFICATION_CONFIRMED);
        }

        // Platform 参考数据（事务外）
        checkReferenceCodes(request.getItems());

        // 保存上下文
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        classifications.replace(new ClientClassificationContext(request.getCaseId(), application, profile,
            newRows(profile, request.getItems(), now), request.getActorId(), now));

        // 响应处理
        return GetClientClassificationProcessor.response(request.getCaseId(), application, profile, rows);
    }

    /**
     * 行业须为 ACTIVE 细类、MCC 须存在且 ACTIVE；不符合时 422 CLASSIFICATION_INVALID，errors[] 逐行指向 items[i].industryCode
     * 或 items[i].mccCode，消息区分不存在、未启用与只选了大类，并带编码。
     */
    void checkReferenceCodes(List<ClientClassificationRequest.Item> items) {
        Map<String, ReferenceDataItem> industries = referenceData.industries(
            items.stream().map(ClientClassificationRequest.Item::getIndustryCode).distinct().toList());
        Map<String, ReferenceDataItem> mccs = referenceData.mccs(items.stream().map(ClientClassificationRequest.Item::getMccCode).distinct().toList());
        List<ProblemFieldError> errors = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            ClientClassificationRequest.Item item = items.get(index);
            String industryProblem = industryProblem(item.getIndustryCode(), industries.get(item.getIndustryCode()));
            if (industryProblem != null) {
                errors.add(new ProblemFieldError("items[" + index + "].industryCode", industryProblem));
            }
            String mccProblem = mccProblem(item.getMccCode(), mccs.get(item.getMccCode()));
            if (mccProblem != null) {
                errors.add(new ProblemFieldError("items[" + index + "].mccCode", mccProblem));
            }
        }
        if (!errors.isEmpty()) {
            throw new RequestBodyValidationException(CLASSIFICATION_INVALID, errors, null);
        }
    }

    /** 行业问题；可用时为 null。 */
    static String industryProblem(String code, ReferenceDataItem industry) {
        if (industry == null) {
            return "行业不存在: " + code;
        }
        if (!industry.active()) {
            return "行业未启用: " + code;
        }
        return Integer.valueOf(DETAIL_INDUSTRY_LEVEL).equals(industry.level()) ? null : "须选择细类，" + code + " 是大类";
    }

    /** MCC 问题；可用时为 null。 */
    static String mccProblem(String code, ReferenceDataItem mcc) {
        if (mcc == null) {
            return "MCC 不存在: " + code;
        }
        return mcc.active() ? null : "MCC 未启用: " + code;
    }

    /** 新分类行。 */
    List<ClientClassification> newRows(ClientClassificationProfile profile, List<ClientClassificationRequest.Item> items, Instant now) {
        return items.stream().map(item -> {
            ClientClassification row = new ClientClassification();
            row.setClassificationId(ids.nextId());
            row.setBusinessProfileId(profile.getBusinessProfileId());
            row.setIndustryCode(item.getIndustryCode());
            row.setMccCode(item.getMccCode());
            row.setPrimarySlot(item.isPrimary() ? 1 : null);
            row.setCreateTime(now);
            row.setUpdateTime(now);
            return row;
        }).toList();
    }
}
