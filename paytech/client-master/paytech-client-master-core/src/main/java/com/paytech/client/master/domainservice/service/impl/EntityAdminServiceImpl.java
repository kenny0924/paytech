package com.paytech.client.master.domainservice.service.impl;

import static com.paytech.client.master.api.enums.EntityAdminErrorCode.ENTITY_CHANGE_VERSION_CONFLICT;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.client.master.core.dal.dataobject.PartnerRole;
import com.paytech.client.master.domain.entityadmin.LocalPartnerRoleChangeContext;
import com.paytech.client.master.domain.entityadmin.OperatingEntityChangeContext;
import com.paytech.client.master.domain.entityadmin.RootRevision;
import com.paytech.client.master.domain.enums.PartyRoleCode;
import com.paytech.client.master.domainservice.repository.BusinessChangeRecordRepository;
import com.paytech.client.master.domainservice.repository.BusinessChangeVersionRepository;
import com.paytech.client.master.domainservice.repository.OperatingEntityRepository;
import com.paytech.client.master.domainservice.repository.PartnerRoleRepository;
import com.paytech.client.master.domainservice.repository.PartyRoleRepository;
import com.paytech.client.master.domainservice.service.EntityAdminService;
import com.paytech.foundation.common.error.FailException;

import lombok.AllArgsConstructor;

/** 运营主档维护的本地原子保存：根推进、快照与主表 / 子表写入同一事务提交。 */
@AllArgsConstructor
public class EntityAdminServiceImpl implements EntityAdminService {
    private final TransactionTemplate writes;
    private final PartyRoleRepository partyRoleRepository;
    private final PartnerRoleRepository partnerRoleRepository;
    private final OperatingEntityRepository operatingEntityRepository;
    private final BusinessChangeRecordRepository businessChangeRecordRepository;
    private final BusinessChangeVersionRepository businessChangeVersionRepository;

    /** 根推进后写入资质。 */
    @Override
    public void changeAcquiringQualification(OperatingEntityChangeContext context) {
        RootRevision revision = context.getRevision();
        execute(() -> {
            revise(revision);
            operatingEntityRepository.updateAcquiringQualification(context.getEntity().entityNo(),
                context.getAcquiringQualification(), revision.getNow());
        });
    }

    /** 先结束再插入，避免同角色 ACTIVE 槽位冲突。 */
    @Override
    public void replacePartnerRoles(LocalPartnerRoleChangeContext context) {
        RootRevision revision = context.getRevision();
        String subjectId = Long.toString(context.getPartner().localPartnerId());
        execute(() -> {
            revise(revision);
            for (PartnerRole ended : context.getEndedRoles()) {
                partnerRoleRepository.end(ended, revision.getNow());
                partyRoleRepository.endActive(PartyRoleCode.of(ended.getRoleCode()), subjectId, revision.getNow());
            }
            context.getAddedRoles().forEach(partnerRoleRepository::insert);
            context.getAddedPartyRoles().forEach(partyRoleRepository::insert);
        });
    }

    /** 缺根时补建 V1，再 CAS 推进并追加 UPDATE 快照。 */
    private void revise(RootRevision revision) {
        if (revision.isRootMissing()) {
            businessChangeRecordRepository.insertRoot(revision.getHistoryId(), revision.getRootType(), revision.getRootId(),
                revision.getNow());
        }
        businessChangeRecordRepository.advanceRoot(revision.getHistoryId(), revision.getExpectedVersion(), revision.getNow());
        businessChangeVersionRepository.insertUpdate(revision.getChangeVersionId(), revision.getHistoryId(),
            revision.nextVersion(), revision.getSnapshotJson(), revision.getActorId(), revision.getNow());
    }

    /** 并发补建根、重复快照或角色唯一键冲突转为版本冲突，整体已回滚。 */
    private void execute(Runnable work) {
        try {
            writes.executeWithoutResult(status -> work.run());
        } catch (DuplicateKeyException conflict) {
            throw new FailException(ENTITY_CHANGE_VERSION_CONFLICT, "主档已被并发修改，请刷新后重试", conflict);
        }
    }
}
