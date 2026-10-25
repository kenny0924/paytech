package com.paytech.client.master.service.processor;

import static com.paytech.client.master.api.enums.EntityAdminErrorCode.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.paytech.client.master.api.enums.AcquiringQualification;
import com.paytech.client.master.core.dal.dataobject.CheckoutRecords.BusinessChangeRecord;
import com.paytech.client.master.core.dal.dataobject.CheckoutRecords.OperatingEntity;
import com.paytech.client.master.domain.entityadmin.OperatingEntityAdminDetail;
import com.paytech.client.master.domain.entityadmin.OperatingEntityChangeContext;
import com.paytech.client.master.domain.entityadmin.RootRevision;
import com.paytech.client.master.domain.enums.BusinessChangeRecordRecordType;
import com.paytech.client.master.domainservice.EntityChangeSnapshot;
import com.paytech.client.master.domainservice.repository.BusinessChangeRecordRepository;
import com.paytech.client.master.domainservice.repository.OperatingEntityRepository;
import com.paytech.client.master.domainservice.service.EntityAdminQueryService;
import com.paytech.client.master.domainservice.service.EntityAdminService;
import com.paytech.client.master.service.processor.request.ChangeAcquiringQualificationRequest;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

/**
 * 运营修改经营主体收单资质（client-master Admin API 3）
 * <li>TRY_LOCK client-master:operating-entity:{entityNo}，等待与租约沿用 resource-lock 配置。</li>
 * <li>锁内一个事务：缺根补建 V1、OPERATING_ENTITY 根 CAS（expectedChangeVersion → +1）、UPDATE 快照、写入资质；
 * 根版本推进使 commercial 的候选复核感知变化。本轮不发事件（无消费者）。</li>
 * <li>没有根的历史手工 OE 视为版本 1。响应为修改后的详情。</li>
 */
public class ChangeAcquiringQualificationProcessor
    extends AbstractLockProcessor<ChangeAcquiringQualificationRequest, OperatingEntityAdminDetail> {
    private final Clock clock;
    private final IdGenerator ids;
    private final Duration lockWait;
    private final Duration lockLease;
    private final EntityAdminService entityAdminService;
    private final EntityAdminQueryService entityAdminQueryService;
    private final OperatingEntityRepository operatingEntityRepository;
    private final BusinessChangeRecordRepository businessChangeRecordRepository;

    /** 锁等待与租约沿用 client-master 资源锁配置。 */
    public ChangeAcquiringQualificationProcessor(Clock clock, IdGenerator ids, Duration lockWait, Duration lockLease,
        EntityAdminService entityAdminService, EntityAdminQueryService entityAdminQueryService,
        OperatingEntityRepository operatingEntityRepository, BusinessChangeRecordRepository businessChangeRecordRepository) {
        this.clock = clock;
        this.ids = ids;
        this.lockWait = lockWait;
        this.lockLease = lockLease;
        this.entityAdminService = entityAdminService;
        this.entityAdminQueryService = entityAdminQueryService;
        this.operatingEntityRepository = operatingEntityRepository;
        this.businessChangeRecordRepository = businessChangeRecordRepository;
    }

    /** 资质必须是三档之一。 */
    @Override
    protected void validate(ChangeAcquiringQualificationRequest request) {
        if (AcquiringQualification.fromCode(request.getAcquiringQualification()) == null) {
            throw new FailException(ENTITY_ATTRIBUTE_INVALID, "收单资质取值非法: " + request.getAcquiringQualification());
        }
    }

    /** 同一 OE 的主档维护共用一把锁。 */
    @Override
    protected LockCommand lockCommand(ChangeAcquiringQualificationRequest request) {
        return LockCommand.tryLocks(List.of("client-master:operating-entity:" + request.getEntityNo()), lockWait, lockLease);
    }

    @Override
    protected OperatingEntityAdminDetail processWithinLock(ChangeAcquiringQualificationRequest request) {
        // 查询经营主体与根
        OperatingEntity entity = operatingEntityRepository.findByEntityNo(request.getEntityNo());
        if (entity == null) {
            throw new FailException(OPERATING_ENTITY_NOT_FOUND);
        }
        BusinessChangeRecord root =
            businessChangeRecordRepository.findRoot(BusinessChangeRecordRecordType.OPERATING_ENTITY, entity.entityNo());

        // 构建上下文
        OperatingEntityChangeContext context = buildContext(entity, root, request);

        // 保存上下文
        entityAdminService.changeAcquiringQualification(context);

        // 响应处理
        return entityAdminQueryService.operatingEntity(entity.entityNo());
    }

    /** 核对调用方版本后一次构造根推进与快照。 */
    OperatingEntityChangeContext buildContext(OperatingEntity entity, BusinessChangeRecord root,
        ChangeAcquiringQualificationRequest request) {
        long current = root == null ? 1 : root.currentVersion();
        if (current != request.getExpectedChangeVersion()) {
            throw new FailException(ENTITY_CHANGE_VERSION_CONFLICT,
                "经营主体已被修改: " + entity.entityNo() + ", 当前版本 " + current + ", 请求版本 " + request.getExpectedChangeVersion());
        }
        AcquiringQualification qualification = AcquiringQualification.fromCode(request.getAcquiringQualification());
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        long historyId = root == null ? ids.nextId() : root.historyId();
        long changeVersionId = ids.nextId();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("entityNo", entity.entityNo());
        data.put("partyId", Long.toString(entity.partyId()));
        data.put("status", entity.status().getCode());
        data.put("acquiringQualification", qualification.getCode());
        data.put("previousAcquiringQualification", entity.acquiringQualification().getCode());
        String snapshot = EntityChangeSnapshot.update(BusinessChangeRecordRecordType.OPERATING_ENTITY, entity.entityNo(),
            historyId, current + 1, request.getActorId(), now, changeVersionId, "ACQUIRING_QUALIFICATION_CHANGED",
            request.getReason(), data);
        RootRevision revision = new RootRevision(BusinessChangeRecordRecordType.OPERATING_ENTITY, entity.entityNo(), historyId,
            root == null, current, changeVersionId, snapshot, request.getActorId(), now);
        return new OperatingEntityChangeContext(entity, qualification, revision);
    }
}
