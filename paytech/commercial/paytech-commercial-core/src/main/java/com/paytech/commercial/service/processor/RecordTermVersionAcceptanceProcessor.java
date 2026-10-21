package com.paytech.commercial.service.processor;

import java.util.List;
import java.util.Objects;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import com.paytech.commercial.api.enums.CommercialTermOwnerType;
import com.paytech.commercial.core.dal.dataobject.AgreementParty;
import com.paytech.commercial.core.dal.dataobject.CommercialTermVersion;
import com.paytech.commercial.core.dal.dataobject.TermVersionAcceptance;
import com.paytech.commercial.domain.CommercialTermVersionContext;
import com.paytech.commercial.domain.CommercialTermVersionView;
import com.paytech.commercial.domain.enums.AgreementPartyPartyRole;
import com.paytech.commercial.domain.enums.CommercialTermVersionStatus;
import com.paytech.commercial.domainservice.repository.AgreementPartyRepository;
import com.paytech.commercial.domainservice.repository.TermVersionAcceptanceRepository;
import com.paytech.commercial.domainservice.service.CommercialTermService;
import com.paytech.commercial.service.processor.request.CommercialTermVersionCommandRequest;
import com.paytech.commercial.service.processor.request.RecordTermVersionAcceptanceRequest;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

import lombok.AllArgsConstructor;

/**
 * 登记客户签署（POST /commercial-terms/v1/versions/{id}/acceptance）：APPROVED → CLIENT_SIGNED
 * <li>原请求：签署编号已登记时，同版本同内容按重放返回当前结果，否则 409。</li>
 * <li>新请求：版本 APPROVED、方案不是模板、签署 Party 是版本合同中 CLIENT（Client 方案）或 LOCAL_PARTNER（Partner 方案）角色、签署时刻不晚于
 * 登记时刻。根 CAS、签署记录、状态迁移与版本 Child UPDATE 快照同事务写入。</li>
 */
@Component
@AllArgsConstructor
public class RecordTermVersionAcceptanceProcessor
    extends AbstractLockProcessor<RecordTermVersionAcceptanceRequest, CommercialTermVersionView> {
    private final CommercialTermLoader loader;
    private final CommercialTermService commercialTermService;
    private final AgreementPartyRepository agreementPartyRepository;
    private final TermVersionAcceptanceRepository termVersionAcceptanceRepository;

    /** 方案资源锁，持续等待。 */
    @Override
    protected LockCommand lockCommand(RecordTermVersionAcceptanceRequest request) {
        return LockCommand.lock(CommercialLocks.termPlan(loader.planIdOf(request.getVersionId())));
    }

    @Override
    protected CommercialTermVersionView processWithinLock(RecordTermVersionAcceptanceRequest request) {
        // 原请求：同编号同内容直接返回
        CommercialTermVersion version = loader.version(request.getVersionId());
        TermVersionAcceptance existing = termVersionAcceptanceRepository.findByReference(request.getAcceptanceReference());
        if (existing != null) {
            return replay(existing, request, version);
        }

        // 新请求：状态、方案种类与签署 Party
        CommercialTermVersionContext base = loader.commandContext(command(request), version).build();
        checkAcceptable(base, request);
        CommercialTermVersionContext context = loader.commandContext(command(request), version)
            .acceptance(newAcceptance(request, base)).build();

        // 保存
        try {
            commercialTermService.recordAcceptance(context);
        } catch (DuplicateKeyException concurrent) {
            throw new ConcurrentRequestException("签署编号或版本签署已被并发登记: " + request.getAcceptanceReference(), concurrent);
        }
        return context.toVersionView();
    }

    /** 同编号重放：须为同一版本、同一 Party 与同一附件。 */
    CommercialTermVersionView replay(TermVersionAcceptance existing, RecordTermVersionAcceptanceRequest request,
        CommercialTermVersion version) {
        boolean same = existing.getTermVersionId() == request.getVersionId() && existing.getPartyId() == request.getPartyId()
            && existing.getAcceptanceMethod() == request.getAcceptanceMethod() && existing.getFileId() == request.getFileId()
            && Objects.equals(existing.getContentHash(), request.getContentHash())
            && Objects.equals(existing.getAcceptedAt(), request.getAcceptedAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        if (!same) {
            throw new ConcurrentRequestException("签署编号已被不同内容使用: " + request.getAcceptanceReference());
        }
        return new CommercialTermVersionView(loader.plan(version.getTermPlanId()), version,
            loader.planRoot(loader.plan(version.getTermPlanId())).getCurrentVersion());
    }

    /** 版本 APPROVED、非模板、签署时刻不晚于登记时刻，Party 为合同中对应角色。 */
    void checkAcceptable(CommercialTermVersionContext context, RecordTermVersionAcceptanceRequest request) {
        if (context.template()) {
            throw new BizCheckFailException("标准价目模板版本无需客户签署: " + request.getVersionId());
        }
        if (context.getVersion().getStatus() != CommercialTermVersionStatus.APPROVED) {
            throw new BizCheckFailException("只有 APPROVED 版本可以登记客户签署: " + request.getVersionId() + ", 状态 "
                + context.getVersion().getStatus().getCode());
        }
        if (request.getAcceptedAt().isAfter(context.getNow())) {
            throw new BizCheckFailException("签署时刻不能晚于登记时刻: " + request.getAcceptedAt());
        }
        AgreementPartyPartyRole role = context.getPlan().getOwnerType() == CommercialTermOwnerType.CLIENT
            ? AgreementPartyPartyRole.CLIENT : AgreementPartyPartyRole.LOCAL_PARTNER;
        List<AgreementParty> parties = agreementPartyRepository.findByAgreementId(context.getAgreement().getAgreementId());
        boolean party = parties.stream().anyMatch(item -> item.getPartyId() == request.getPartyId() && item.getPartyRole() == role);
        if (!party) {
            throw new BizCheckFailException("签署方不是合同 " + role.getCode() + " 角色 Party: " + request.getPartyId() + ", 合同 "
                + context.getAgreement().getAgreementId());
        }
    }

    /** 签署记录新行。 */
    TermVersionAcceptance newAcceptance(RecordTermVersionAcceptanceRequest request, CommercialTermVersionContext context) {
        TermVersionAcceptance acceptance = new TermVersionAcceptance();
        acceptance.setVersionAcceptanceId(loader.nextId());
        acceptance.setTermVersionId(request.getVersionId());
        acceptance.setPartyId(request.getPartyId());
        acceptance.setAcceptanceMethod(request.getAcceptanceMethod());
        acceptance.setAcceptedAt(request.getAcceptedAt());
        acceptance.setAcceptanceReference(request.getAcceptanceReference());
        acceptance.setFileId(request.getFileId());
        acceptance.setContentHash(request.getContentHash());
        acceptance.setRecordedBy(Long.toString(request.getOperatorAccountId()));
        acceptance.setCreateTime(context.getNow());
        acceptance.setUpdateTime(context.getNow());
        return acceptance;
    }

    /** 版本命令公共输入。 */
    static CommercialTermVersionCommandRequest command(RecordTermVersionAcceptanceRequest request) {
        return CommercialTermVersionCommandRequest.operator(request.getVersionId(), request.getExpectedChangeVersion(),
            request.getOperatorAccountId());
    }
}
