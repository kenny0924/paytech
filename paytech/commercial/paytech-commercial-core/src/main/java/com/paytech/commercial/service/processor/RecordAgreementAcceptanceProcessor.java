package com.paytech.commercial.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.dao.DuplicateKeyException;

import com.paytech.commercial.core.dal.dataobject.Agreement;
import com.paytech.commercial.core.dal.dataobject.AgreementDocumentRef;
import com.paytech.commercial.core.dal.dataobject.AgreementParty;
import com.paytech.commercial.core.dal.dataobject.BusinessChangeRecord;
import com.paytech.commercial.domain.AgreementContext;
import com.paytech.commercial.domain.enums.AgreementDocumentRefDocumentType;
import com.paytech.commercial.domain.enums.AgreementPartyAcceptanceMethod;
import com.paytech.commercial.domain.enums.AgreementStatus;
import com.paytech.commercial.domainservice.repository.AgreementDocumentRefRepository;
import com.paytech.commercial.domainservice.repository.AgreementPartyRepository;
import com.paytech.commercial.domainservice.repository.AgreementRepository;
import com.paytech.commercial.domainservice.repository.BusinessChangeRecordRepository;
import com.paytech.commercial.domainservice.service.AgreementService;
import com.paytech.commercial.service.processor.request.RecordAgreementAcceptanceRequest;
import com.paytech.commercial.service.processor.response.AgreementResponse;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.id.IdGenerator;

/**
 * 签署登记（POST /agreements/v1/{agreementId}/parties/{partyId}/acceptance），锁 {@code agreement:{id}}
 * <li>业务唯一身份是全局唯一的 acceptanceReference：已登记时比较合同、Party、方式、接受时刻与 Actor，一致返回当前合同，否则 409。</li>
 * <li>新请求：期望版本一致、状态 PENDING_SIGNATURE、该 Party 的全部角色都未签。签署方式矩阵：CLICK_ACCEPTANCE 证据为唯一
 * SIGNING_DOCUMENT 且必须有实际接受 Actor；E_SIGNATURE / MANUAL_SIGNATURE 证据为合同唯一 EXECUTED_DOCUMENT（首次登记时新增，
 * 已有时必须是同一文件）。同 Party 全部角色一次原子登记，组内最小主键行占 slot=1；全部 Party 签完时同一事务进入 SIGNED，签署时刻取
 * 各 Party 接受时刻的最大值。不发布事件。</li>
 * <li>acceptanceReference 跨合同并发复用由 uk_acceptance_ref_slot 拦截，唯一键冲突回滚后转换为 409。</li>
 */
public class RecordAgreementAcceptanceProcessor
    extends AbstractAgreementWriteProcessor<RecordAgreementAcceptanceRequest, AgreementResponse> {
    private final IdGenerator idGenerator;
    private final AgreementService agreementService;
    private final AgreementPartyRepository agreementPartyRepository;
    private final AgreementDocumentRefRepository agreementDocumentRefRepository;

    /**
     * 注入依赖
     *
     * @param clock 业务时钟
     * @param idGenerator 主键生成器
     * @param agreementService 合同写入服务
     * @param agreementRepository 合同仓储
     * @param agreementPartyRepository 参与方仓储
     * @param businessChangeRecordRepository BCR 仓储
     * @param agreementDocumentRefRepository 文件引用仓储
     */
    public RecordAgreementAcceptanceProcessor(Clock clock, IdGenerator idGenerator, AgreementService agreementService,
        AgreementRepository agreementRepository, AgreementPartyRepository agreementPartyRepository,
        BusinessChangeRecordRepository businessChangeRecordRepository,
        AgreementDocumentRefRepository agreementDocumentRefRepository) {
        super(clock, agreementRepository, businessChangeRecordRepository);
        this.idGenerator = idGenerator;
        this.agreementService = agreementService;
        this.agreementPartyRepository = agreementPartyRepository;
        this.agreementDocumentRefRepository = agreementDocumentRefRepository;
    }

    /** 请求所指合同。 */
    @Override
    protected long agreementId(RecordAgreementAcceptanceRequest request) {
        return request.getAgreementId();
    }

    /** 签署方式与证据字段的组合只依赖输入。 */
    @Override
    protected void validate(RecordAgreementAcceptanceRequest request) {
        boolean click = request.getAcceptanceMethod() == AgreementPartyAcceptanceMethod.CLICK_ACCEPTANCE;
        boolean executed = request.getExecutedFileId() != null;
        if (executed != (request.getExecutedContentHash() != null)) {
            throw new InvalidParameterException("executedDocument 的 fileId 与 contentHash 必须同时提供");
        }
        if (click && (request.getAcceptedByActorId() == null || executed)) {
            throw new BizCheckFailException("CLICK_ACCEPTANCE 必须提供 acceptedByActorId 且不能提供 executedDocument");
        }
        if (!click && !executed) {
            throw new BizCheckFailException(request.getAcceptanceMethod().getCode() + " 必须提供 executedDocument");
        }
    }

    @Override
    protected AgreementResponse processWithinLock(RecordAgreementAcceptanceRequest request) {
        // 查询合同与原签署
        Agreement agreement = getAgreement(request.getAgreementId());
        BusinessChangeRecord root = getRoot(agreement);
        List<AgreementParty> recorded = agreementPartyRepository.findByAcceptanceReference(request.getAcceptanceReference());
        if (!recorded.isEmpty()) {
            checkSameAcceptance(recorded, request);
            return response(agreement, root);
        }

        // 构建上下文
        AgreementContext context = buildContext(agreement, root, request);

        // 保存上下文
        try {
            agreementService.recordAcceptance(context);
        } catch (DuplicateKeyException duplicate) {
            throw new ConcurrentRequestException("acceptanceReference 已被其他签署占用: " + request.getAcceptanceReference(),
                duplicate);
        }

        // 响应处理
        return response(agreement, root);
    }

    /** 同引用重放必须是同一合同、同一 Party、同一方式、同一接受时刻与 Actor。 */
    static void checkSameAcceptance(List<AgreementParty> recorded, RecordAgreementAcceptanceRequest request) {
        AgreementParty first = recorded.getFirst();
        boolean same = first.getAgreementId().equals(request.getAgreementId())
            && first.getPartyId().equals(request.getPartyId())
            && first.getAcceptanceMethod() == request.getAcceptanceMethod()
            && Objects.equals(first.getAcceptedTime(), micros(request.getAcceptedAt()))
            && Objects.equals(first.getAcceptedByActorId(), request.getAcceptedByActorId());
        if (!same) {
            throw new ConcurrentRequestException("acceptanceReference 已用于其他签署结果: " + request.getAcceptanceReference());
        }
    }

    /** 校验版本、状态、Party 与证据，构造同 Party 全部角色的签署上下文。 */
    AgreementContext buildContext(Agreement agreement, BusinessChangeRecord root, RecordAgreementAcceptanceRequest request) {
        checkVersion(root, request.getExpectedChangeVersion());
        if (agreement.getStatus() != AgreementStatus.PENDING_SIGNATURE) {
            throw new BizCheckFailException("只有 PENDING_SIGNATURE 合同可以登记签署: " + agreement.getAgreementId() + ", 状态: "
                + agreement.getStatus().getCode());
        }
        Instant now = now();
        Instant acceptedAt = micros(request.getAcceptedAt());
        if (acceptedAt.isAfter(now)) {
            throw new BizCheckFailException("接受时刻不能晚于登记时刻: " + request.getAcceptanceReference());
        }
        List<AgreementParty> parties = agreementPartyRepository.findByAgreementId(agreement.getAgreementId());
        List<AgreementParty> roles = pendingRoles(agreement, parties, request.getPartyId());
        AgreementContext context = new AgreementContext(agreement, root, request.getActorId(), now,
            request.getExpectedChangeVersion());
        context.setParties(roles);
        AgreementDocumentRef evidence = findEvidence(agreement, request);
        if (evidence == null) {
            evidence = buildExecutedDocument(agreement, request, now);
            context.setNewDocuments(List.of(evidence));
        }
        context.setAcceptance(buildAcceptance(request, acceptedAt, evidence, now));
        moveToSignedWhenComplete(context, parties, acceptedAt);
        return context;
    }

    /** 该 Party 在合同中的全部角色（按主键升序），必须存在且都未签。 */
    static List<AgreementParty> pendingRoles(Agreement agreement, List<AgreementParty> parties, long partyId) {
        List<AgreementParty> roles = parties.stream().filter(party -> party.getPartyId() == partyId)
            .sorted(Comparator.comparing(AgreementParty::getAgreementPartyId)).toList();
        if (roles.isEmpty()) {
            throw new BizCheckFailException("Party 不是该合同参与方: " + partyId + ", 合同: " + agreement.getAgreementId());
        }
        if (roles.stream().anyMatch(role -> role.getAcceptedTime() != null)) {
            throw new BizCheckFailException("该 Party 已登记签署结果: " + partyId + ", 合同: " + agreement.getAgreementId());
        }
        return roles;
    }

    /** 已有签署证据：CLICK 取唯一 SIGNING_DOCUMENT；其他方式取已有 EXECUTED_DOCUMENT（必须同一文件），尚无时返回 null。 */
    AgreementDocumentRef findEvidence(Agreement agreement, RecordAgreementAcceptanceRequest request) {
        List<AgreementDocumentRef> documents = agreementDocumentRefRepository.findByAgreementId(agreement.getAgreementId());
        if (request.getAcceptanceMethod() == AgreementPartyAcceptanceMethod.CLICK_ACCEPTANCE) {
            return documents.stream()
                .filter(document -> document.getDocumentType() == AgreementDocumentRefDocumentType.SIGNING_DOCUMENT)
                .findFirst().orElseThrow(() -> new BizCheckFailException("合同缺少 SIGNING_DOCUMENT: "
                    + agreement.getAgreementId()));
        }
        AgreementDocumentRef executed = documents.stream()
            .filter(document -> document.getDocumentType() == AgreementDocumentRefDocumentType.EXECUTED_DOCUMENT)
            .findFirst().orElse(null);
        if (executed == null) {
            return null;
        }
        if (!executed.getFileId().equals(request.getExecutedFileId())
            || !executed.getContentHash().equals(request.getExecutedContentHash())) {
            throw new BizCheckFailException("合同已有不同的 EXECUTED_DOCUMENT，每份合同至多一份: " + agreement.getAgreementId());
        }
        return executed;
    }

    /** 新增最终执行文件引用。 */
    AgreementDocumentRef buildExecutedDocument(Agreement agreement, RecordAgreementAcceptanceRequest request, Instant now) {
        AgreementDocumentRef executed = new AgreementDocumentRef();
        executed.setAgreementDocumentId(idGenerator.nextId());
        executed.setAgreementId(agreement.getAgreementId());
        executed.setDocumentType(AgreementDocumentRefDocumentType.EXECUTED_DOCUMENT);
        executed.setFileId(request.getExecutedFileId());
        executed.setContentHash(request.getExecutedContentHash());
        executed.setCreateTime(now);
        executed.setUpdateTime(now);
        return executed;
    }

    /** 签署字段载体：登记 Actor 与实际接受 Actor 分别保存。 */
    static AgreementParty buildAcceptance(RecordAgreementAcceptanceRequest request, Instant acceptedAt,
        AgreementDocumentRef evidence, Instant now) {
        AgreementParty acceptance = new AgreementParty();
        acceptance.setAcceptanceMethod(request.getAcceptanceMethod());
        acceptance.setAcceptedByActorId(request.getAcceptedByActorId());
        acceptance.setAcceptedTime(acceptedAt);
        acceptance.setEvidenceDocumentId(evidence.getAgreementDocumentId());
        acceptance.setAcceptanceReference(request.getAcceptanceReference());
        acceptance.setAcceptanceRecordedBy(request.getActorId());
        acceptance.setAcceptanceRecordedAt(now);
        return acceptance;
    }

    /** 其余 Party 均已签署时进入 SIGNED，签署时刻取全部接受时刻的最大值。 */
    static void moveToSignedWhenComplete(AgreementContext context, List<AgreementParty> parties, Instant acceptedAt) {
        Long partyId = context.getParties().getFirst().getPartyId();
        List<AgreementParty> others = parties.stream().filter(party -> !party.getPartyId().equals(partyId)).toList();
        if (others.stream().anyMatch(party -> party.getAcceptedTime() == null)) {
            return;
        }
        Instant signedTime = others.stream().map(AgreementParty::getAcceptedTime).filter(time -> time.isAfter(acceptedAt))
            .max(Comparator.naturalOrder()).orElse(acceptedAt);
        context.moveTo(AgreementStatus.SIGNED);
        context.setNextSignedTime(signedTime);
    }

    /** 截断到 DATETIME(6) 微秒。 */
    static Instant micros(Instant value) {
        return value.truncatedTo(ChronoUnit.MICROS);
    }
}
