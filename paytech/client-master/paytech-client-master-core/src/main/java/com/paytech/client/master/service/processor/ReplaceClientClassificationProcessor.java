package com.paytech.client.master.service.processor;

import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.paytech.client.master.configuration.ClientFormCatalogProperties;
import com.paytech.client.master.core.dal.dataobject.ClientApplicationSubmission;
import com.paytech.client.master.core.dal.dataobject.ClientClassification;
import com.paytech.client.master.core.dal.dataobject.ClientClassificationProfile;
import com.paytech.client.master.domain.enums.ClientApplicationStatus;
import com.paytech.client.master.domain.onboarding.ClientClassificationContext;
import com.paytech.client.master.domainservice.repository.ClientApplicationRepository;
import com.paytech.client.master.domainservice.repository.ClientBusinessClassificationRepository;
import com.paytech.client.master.domainservice.repository.ClientBusinessProfileRepository;
import com.paytech.client.master.domainservice.service.ClientClassificationService;
import com.paytech.client.master.service.processor.request.ClientClassificationRequest;
import com.paytech.client.master.service.processor.response.ClientClassificationResponse;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

/**
 * O03 设置分类：整体替换本任务的分类集合，确认前可反复修改
 * <li>与 O04 共用 client-onboarding:review-case:{caseId} 锁；本任务确认后的修改由条件更新最终拒绝。</li>
 * <li>行业必须在 CCT 行业目录；MCC 只校验四位数字；组合不重复且恰好一个主要分类。</li>
 */
public class ReplaceClientClassificationProcessor
    extends AbstractLockProcessor<ClientClassificationRequest, ClientClassificationResponse> {
    private final Clock clock;
    private final IdGenerator ids;
    private final Duration lockWait;
    private final Duration lockLease;
    private final ClientFormCatalogProperties catalog;
    private final ClientBusinessProfileRepository profiles;
    private final ClientClassificationService classifications;
    private final ClientApplicationRepository applications;
    private final ClientBusinessClassificationRepository rows;

    /** 锁等待与租约沿用 CCT 既有资源锁配置。 */
    public ReplaceClientClassificationProcessor(Clock clock, IdGenerator ids, Duration lockWait, Duration lockLease,
        ClientFormCatalogProperties catalog, ClientBusinessProfileRepository profiles, ClientClassificationService classifications,
        ClientApplicationRepository applications, ClientBusinessClassificationRepository rows) {
        this.clock = clock;
        this.ids = ids;
        this.lockWait = lockWait;
        this.lockLease = lockLease;
        this.catalog = catalog;
        this.profiles = profiles;
        this.classifications = classifications;
        this.applications = applications;
        this.rows = rows;
    }

    /** 目录、组合不重复与恰好一个主要分类只依赖输入。 */
    @Override
    protected void validate(ClientClassificationRequest request) {
        Set<String> industries = new HashSet<>();
        catalog.industries().forEach(industry -> industries.add(industry.code()));
        Set<String> pairs = new HashSet<>();
        long primary = 0;
        for (ClientClassificationRequest.Item item : request.getItems()) {
            if (!industries.contains(item.getIndustryCode()) || !pairs.add(item.getIndustryCode() + ":" + item.getMccCode())) {
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

        // 保存上下文
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        classifications.replace(new ClientClassificationContext(request.getCaseId(), application, profile,
            newRows(profile, request.getItems(), now), request.getActorId(), now));

        // 响应处理
        return GetClientClassificationProcessor.response(request.getCaseId(), application, profile, rows);
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
