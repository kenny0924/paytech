package com.paytech.client.master.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.paytech.client.master.core.dal.dataobject.CheckoutRecords.Client;
import com.paytech.client.master.core.dal.dataobject.ClientProjectionHistory;
import com.paytech.client.master.domain.enums.BusinessChangeOperation;
import com.paytech.client.master.domain.enums.BusinessChangeSnapshotFormat;
import com.paytech.client.master.domain.enums.ClientStatus;
import com.paytech.client.master.domain.onboarding.ClientSuspensionContext;
import com.paytech.client.master.domainservice.ClientProjectionSnapshot;
import com.paytech.client.master.domainservice.repository.ClientProjectionRepository;
import com.paytech.client.master.domainservice.repository.ClientRepository;
import com.paytech.client.master.domainservice.service.ClientLifecycleService;
import com.paytech.client.master.service.processor.request.ApplyQualificationChangeRequest;
import com.paytech.client.master.service.processor.response.ApplyQualificationChangeResponse;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * 落地 compliance QualificationChanged（Q15）：CLIENT 角色资格 REVOKED 时 ACTIVE Client 自动 SUSPENDED
 * <li>同一事务挂起、追加 CLIENT_SUSPENDED 生命周期历史并登记 CLIENT_TENANT_PROJECTION，Platform 工作区随之暂停。</li>
 * <li>其他角色、其他状态或 Client 已非 ACTIVE 时忽略；恢复 ACTIVE 需新资格批准后运营确认，不在此自动处理。</li>
 * <li>不加 Redis 锁：Client 状态与历史根版本由条件更新保护，冲突整体回滚后由消息重投处理。</li>
 */
@AllArgsConstructor
public class ApplyQualificationChangeProcessor
    extends AbstractProcessor<ApplyQualificationChangeRequest, ApplyQualificationChangeResponse> {
    private static final String ROLE_CLIENT = "CLIENT";
    private static final String REVOKED = "REVOKED";

    private final Clock clock;
    private final IdGenerator ids;
    private final ClientRepository clients;
    private final ClientLifecycleService lifecycle;
    private final ClientProjectionRepository projections;

    /** 非 CLIENT 撤销直接忽略；CLIENT 撤销须为存在且 ACTIVE 的 Client。 */
    @Override
    protected ApplyQualificationChangeResponse processRequest(ApplyQualificationChangeRequest request) {
        if (!ROLE_CLIENT.equals(request.getRoleType()) || !REVOKED.equals(request.getStatus())) {
            return ApplyQualificationChangeResponse.IGNORED;
        }
        if (!request.getRoleId().matches("[1-9][0-9]{0,18}")) {
            throw new BizCheckFailException("资格变化事件的 Client 标识不合法");
        }
        long clientId = Long.parseLong(request.getRoleId());
        Client client = clients.findById(clientId);
        if (client == null) {
            throw new BizCheckFailException("资格撤销对应的 Client 不存在: " + clientId);
        }
        if (client.status() != ClientStatus.ACTIVE) {
            return ApplyQualificationChangeResponse.IGNORED;
        }
        lifecycle.suspend(buildContext(clientId, request));
        return ApplyQualificationChangeResponse.SUSPENDED;
    }

    /**
     * 从最新生命周期历史构造下一版 SUSPENDED 历史
     * <li>事件不携带操作人，历史 Actor 沿用最新一版的 Actor；触发标识为资格决定 ID。</li>
     */
    ClientSuspensionContext buildContext(long clientId, ApplyQualificationChangeRequest request) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        ClientProjectionHistory latest = projections.latest(clientId);
        if (latest == null || !ClientStatus.ACTIVE.getCode().equals(ClientProjectionSnapshot.read(latest).status())) {
            throw new ErrorException("Client 生命周期不是 ACTIVE: " + clientId);
        }
        ClientProjectionHistory next = new ClientProjectionHistory(ids.nextId(), latest.historyId(), latest.recordId(),
            latest.sourceVersion() + 1, BusinessChangeOperation.UPDATE, BusinessChangeSnapshotFormat.ENTITY_NO, null,
            latest.actorId(), now, latest.clientId(), latest.partyId(), latest.clientNo(), latest.applicantId(),
            latest.applicationCount());
        long trigger = request.getDecisionId() == null ? clientId : request.getDecisionId();
        String snapshot = ClientProjectionSnapshot.snapshot(next, ClientStatus.SUSPENDED, Long.toString(trigger), "CLIENT_SUSPENDED");
        return new ClientSuspensionContext(clientId, next, snapshot, now);
    }
}
