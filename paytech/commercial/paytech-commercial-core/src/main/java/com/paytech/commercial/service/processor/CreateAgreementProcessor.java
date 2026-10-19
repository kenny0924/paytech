package com.paytech.commercial.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.paytech.commercial.api.enums.BusinessMode;
import com.paytech.commercial.api.enums.FundMode;
import com.paytech.commercial.api.enums.PricingAuthority;
import com.paytech.commercial.core.dal.dataobject.Agreement;
import com.paytech.commercial.core.dal.dataobject.AgreementDocumentRef;
import com.paytech.commercial.core.dal.dataobject.AgreementOperatingEntity;
import com.paytech.commercial.core.dal.dataobject.AgreementParty;
import com.paytech.commercial.domain.AgreementContext;
import com.paytech.commercial.domain.enums.AgreementAgreementFormat;
import com.paytech.commercial.domain.enums.AgreementAgreementType;
import com.paytech.commercial.domain.enums.AgreementDocumentRefDocumentType;
import com.paytech.commercial.domain.enums.AgreementPartyPartyRole;
import com.paytech.commercial.domain.enums.AgreementStatus;
import com.paytech.commercial.domain.enums.BusinessChangeRecordRecordType;
import com.paytech.commercial.domainservice.repository.AgreementDocumentRefRepository;
import com.paytech.commercial.domainservice.repository.AgreementOperatingEntityRepository;
import com.paytech.commercial.domainservice.repository.AgreementPartyRepository;
import com.paytech.commercial.domainservice.repository.AgreementRepository;
import com.paytech.commercial.domainservice.repository.BusinessChangeRecordRepository;
import com.paytech.commercial.domainservice.service.AgreementService;
import com.paytech.commercial.service.processor.request.CreateAgreementRequest;
import com.paytech.commercial.service.processor.request.CreateAgreementRequest.Document;
import com.paytech.commercial.service.processor.request.CreateAgreementRequest.Party;
import com.paytech.commercial.service.processor.response.AgreementResponse;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.id.IdGenerator;

/**
 * 创建 DRAFT 合同（POST /agreements/v1）：锁 {@code agreement:{agreementNo}}，agreementNo 是业务唯一身份
 * <li>原请求：同号合同已存在时比较创建内容，一致返回当前合同，不一致 409；不重新检查新建资格。</li>
 * <li>新请求：合同行、参与方角色、合同文件、PARTNER 覆盖 OE 与 AGREEMENT Root Version 1 由 AgreementService 在一个本地事务写入；
 * 创建不发布事件。</li>
 * <li>Owner XOR、CLIENT / PARTNER 三属性组合与本期 SETTLE_THROUGH_PLATFORM / PLATFORM 限制只依赖输入，放在 validate。</li>
 */
public class CreateAgreementProcessor extends AbstractCommercialWriteProcessor<CreateAgreementRequest, AgreementResponse> {
    private final IdGenerator idGenerator;
    private final AgreementService agreementService;
    private final AgreementRepository agreementRepository;
    private final AgreementPartyRepository agreementPartyRepository;
    private final AgreementDocumentRefRepository agreementDocumentRefRepository;
    private final AgreementOperatingEntityRepository agreementOperatingEntityRepository;

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
     * @param agreementOperatingEntityRepository 覆盖 OE 仓储
     */
    public CreateAgreementProcessor(Clock clock, IdGenerator idGenerator, AgreementService agreementService,
        AgreementRepository agreementRepository, AgreementPartyRepository agreementPartyRepository,
        BusinessChangeRecordRepository businessChangeRecordRepository,
        AgreementDocumentRefRepository agreementDocumentRefRepository,
        AgreementOperatingEntityRepository agreementOperatingEntityRepository) {
        super(clock, businessChangeRecordRepository);
        this.idGenerator = idGenerator;
        this.agreementService = agreementService;
        this.agreementRepository = agreementRepository;
        this.agreementPartyRepository = agreementPartyRepository;
        this.agreementDocumentRefRepository = agreementDocumentRefRepository;
        this.agreementOperatingEntityRepository = agreementOperatingEntityRepository;
    }

    /** 按合同编号串行化创建。 */
    @Override
    protected LockCommand lockCommand(CreateAgreementRequest request) {
        return LockCommand.lock(CommercialLocks.agreementNo(request.getAgreementNo()));
    }

    /** 只依赖输入的组合规则：Owner XOR、合同属性、生效区间、参与方与文件。 */
    @Override
    protected void validate(CreateAgreementRequest request) {
        checkOwner(request);
        checkCommercialAttributes(request);
        if (request.getEffectiveTo() != null && !request.getEffectiveTo().isAfter(request.getEffectiveFrom())) {
            throw new BizCheckFailException("合同生效终点必须晚于起点: " + request.getAgreementNo());
        }
        checkParties(request);
        checkDocuments(request);
    }

    @Override
    protected AgreementResponse processWithinLock(CreateAgreementRequest request) {
        // 原请求：同号合同已存在
        Agreement existing = agreementRepository.findByAgreementNo(request.getAgreementNo());
        if (existing != null) {
            checkSameContent(existing, request);
            return AbstractAgreementWriteProcessor.response(existing, getRoot(BusinessChangeRecordRecordType.AGREEMENT,
                existing.getAgreementId()));
        }

        // 构建上下文
        AgreementContext context = buildContext(request);

        // 保存上下文
        agreementService.createAgreement(context);

        // 响应处理
        return AbstractAgreementWriteProcessor.response(context.getAgreement(), context.getRoot());
    }

    /** CLIENT 合同绑定 Client 与一个 OE，PARTNER 合同绑定 Local Partner 并以覆盖 OE 表达多 OE。 */
    static void checkOwner(CreateAgreementRequest request) {
        boolean client = request.getAgreementType() == AgreementAgreementType.CLIENT;
        boolean clientOwned = request.getClientId() != null && request.getEntityNo() != null
            && request.getLocalPartnerId() == null && request.getOperatingEntityNos().isEmpty();
        boolean partnerOwned = request.getLocalPartnerId() != null && request.getClientId() == null
            && request.getEntityNo() == null;
        if (client && !clientOwned) {
            throw new BizCheckFailException("CLIENT 合同必须且只能指定 clientId 与 entityNo，不能指定 localPartnerId 或覆盖 OE: "
                + request.getAgreementNo());
        }
        if (!client && !partnerOwned) {
            throw new BizCheckFailException("PARTNER 合同必须且只能指定 localPartnerId，不能指定 clientId 或 entityNo: "
                + request.getAgreementNo());
        }
        if (new HashSet<>(request.getOperatingEntityNos()).size() != request.getOperatingEntityNos().size()) {
            throw new InvalidParameterException("operatingEntityNos 不得重复");
        }
    }

    /** CLIENT 三属性非空且本期只接受 SETTLE_THROUGH_PLATFORM 与 PLATFORM；PARTNER 三属性与推荐伙伴为空。 */
    static void checkCommercialAttributes(CreateAgreementRequest request) {
        if (request.getAgreementType() == AgreementAgreementType.PARTNER) {
            if (request.getBusinessMode() != null || request.getFundMode() != null || request.getPricingAuthority() != null
                || request.getReferralPartnerId() != null) {
                throw new BizCheckFailException("PARTNER 合同不能指定 businessMode、fundMode、pricingAuthority 或 referralPartnerId: "
                    + request.getAgreementNo());
            }
            return;
        }
        if (request.getBusinessMode() == null || request.getFundMode() == null || request.getPricingAuthority() == null) {
            throw new BizCheckFailException("CLIENT 合同必须指定 businessMode、fundMode 与 pricingAuthority: "
                + request.getAgreementNo());
        }
        if (request.getBusinessMode() == BusinessMode.MOR && request.getFundMode() != FundMode.SETTLE_THROUGH_PLATFORM) {
            throw new BizCheckFailException("MOR 合同只允许 SETTLE_THROUGH_PLATFORM 资金模式: " + request.getAgreementNo());
        }
        if (request.getFundMode() != FundMode.SETTLE_THROUGH_PLATFORM) {
            throw new BizCheckFailException("本期只接受 SETTLE_THROUGH_PLATFORM 资金模式: " + request.getAgreementNo()
                + ", fundMode: " + request.getFundMode().getCode());
        }
        if (request.getPricingAuthority() == PricingAuthority.PARTNER && request.getReferralPartnerId() == null) {
            throw new BizCheckFailException("PARTNER 定价权必须指定 referralPartnerId: " + request.getAgreementNo());
        }
        if (request.getPricingAuthority() != PricingAuthority.PLATFORM) {
            throw new BizCheckFailException("本期只接受 PLATFORM 定价权: " + request.getAgreementNo() + ", pricingAuthority: "
                + request.getPricingAuthority().getCode());
        }
    }

    /** (partyId, partyRole) 不重复，同 Party 同资料版本，签约 Owner 角色与合同类型匹配。 */
    static void checkParties(CreateAgreementRequest request) {
        Set<String> roles = new HashSet<>();
        Map<Long, Long> profiles = new HashMap<>();
        for (Party party : request.getParties()) {
            if (!roles.add(party.getPartyId() + ":" + party.getPartyRole().getCode())) {
                throw new InvalidParameterException("parties 中同一 Party 的角色不得重复: " + party.getPartyId());
            }
            Long profile = profiles.putIfAbsent(party.getPartyId(), party.getPartyProfileId());
            if (profile != null && !profile.equals(party.getPartyProfileId())) {
                throw new BizCheckFailException("同一 Party 的各角色必须引用同一资料版本: " + party.getPartyId());
            }
        }
        Set<AgreementPartyPartyRole> present = request.getParties().stream().map(Party::getPartyRole)
            .collect(Collectors.toSet());
        boolean client = request.getAgreementType() == AgreementAgreementType.CLIENT;
        AgreementPartyPartyRole owner = client ? AgreementPartyPartyRole.CLIENT : AgreementPartyPartyRole.LOCAL_PARTNER;
        AgreementPartyPartyRole foreign = client ? AgreementPartyPartyRole.LOCAL_PARTNER : AgreementPartyPartyRole.CLIENT;
        if (!present.contains(owner) || present.contains(foreign)) {
            throw new BizCheckFailException(request.getAgreementType().getCode() + " 合同必须包含 " + owner.getCode()
                + " 签约方且不能包含 " + foreign.getCode() + ": " + request.getAgreementNo());
        }
    }

    /** 创建只接受 SIGNING_DOCUMENT（至多一份）与 ANNEX，同用途同文件不重复。 */
    static void checkDocuments(CreateAgreementRequest request) {
        Set<String> files = new HashSet<>();
        long signing = 0;
        for (Document document : request.getDocuments()) {
            AgreementDocumentRefDocumentType type = document.getDocumentType();
            if (type != AgreementDocumentRefDocumentType.SIGNING_DOCUMENT && type != AgreementDocumentRefDocumentType.ANNEX) {
                throw new InvalidParameterException("创建合同只接受 SIGNING_DOCUMENT 与 ANNEX 文件");
            }
            if (!files.add(type.getCode() + ":" + document.getFileId())) {
                throw new InvalidParameterException("documents 中同用途文件不得重复: " + document.getFileId());
            }
            signing += type == AgreementDocumentRefDocumentType.SIGNING_DOCUMENT ? 1 : 0;
        }
        if (signing > 1) {
            throw new BizCheckFailException("每份合同只能有一份 SIGNING_DOCUMENT: " + request.getAgreementNo());
        }
    }

    /** 同号重放必须与原创建内容一致；终止只收窄 effectiveTo，故已终止合同不比较终点。 */
    void checkSameContent(Agreement existing, CreateAgreementRequest request) {
        long id = existing.getAgreementId();
        boolean same = Objects.equals(existing.getAgreementName(), request.getAgreementName())
            && existing.getAgreementType() == request.getAgreementType()
            && existing.getBusinessMode() == request.getBusinessMode() && existing.getFundMode() == request.getFundMode()
            && existing.getPricingAuthority() == request.getPricingAuthority()
            && Objects.equals(existing.getReferralPartnerId(), request.getReferralPartnerId())
            && Objects.equals(existing.getClientId(), request.getClientId())
            && Objects.equals(existing.getLocalPartnerId(), request.getLocalPartnerId())
            && Objects.equals(existing.getEntityNo(), request.getEntityNo())
            && Objects.equals(existing.getEffectiveFrom(), request.getEffectiveFrom())
            && (existing.getStatus() == AgreementStatus.TERMINATED
                || Objects.equals(existing.getEffectiveTo(), request.getEffectiveTo()))
            && partyKeys(agreementPartyRepository.findByAgreementId(id)).equals(requestPartyKeys(request))
            && documentKeys(agreementDocumentRefRepository.findByAgreementId(id)).equals(requestDocumentKeys(request))
            && agreementOperatingEntityRepository.findByAgreementId(id).stream().map(AgreementOperatingEntity::getEntityNo)
                .collect(Collectors.toSet()).equals(new HashSet<>(request.getOperatingEntityNos()));
        if (!same) {
            throw new ConcurrentRequestException("合同编号已存在且内容不同: " + request.getAgreementNo());
        }
    }

    /** 已保存参与方的身份集合。 */
    static Set<String> partyKeys(List<AgreementParty> parties) {
        return parties.stream().map(party -> party.getPartyId() + ":" + party.getPartyProfileId() + ":"
            + party.getPartyRole().getCode()).collect(Collectors.toSet());
    }

    /** 请求参与方的身份集合。 */
    static Set<String> requestPartyKeys(CreateAgreementRequest request) {
        return request.getParties().stream().map(party -> party.getPartyId() + ":" + party.getPartyProfileId() + ":"
            + party.getPartyRole().getCode()).collect(Collectors.toSet());
    }

    /** 已保存的创建期文件集合（签署文件与附件）。 */
    static Set<String> documentKeys(List<AgreementDocumentRef> documents) {
        return documents.stream()
            .filter(document -> document.getDocumentType() == AgreementDocumentRefDocumentType.SIGNING_DOCUMENT
                || document.getDocumentType() == AgreementDocumentRefDocumentType.ANNEX)
            .map(document -> document.getDocumentType().getCode() + ":" + document.getFileId() + ":"
                + document.getContentHash())
            .collect(Collectors.toSet());
    }

    /** 请求文件集合。 */
    static Set<String> requestDocumentKeys(CreateAgreementRequest request) {
        return request.getDocuments().stream().map(document -> document.getDocumentType().getCode() + ":"
            + document.getFileId() + ":" + document.getContentHash()).collect(Collectors.toSet());
    }

    /** 构建合同、参与方、文件与覆盖 OE 的新行。 */
    AgreementContext buildContext(CreateAgreementRequest request) {
        Instant now = now();
        Agreement agreement = buildAgreement(request, now);
        AgreementContext context = new AgreementContext(agreement, null, request.getActorId(), now, null);
        context.setParties(request.getParties().stream().map(party -> buildParty(agreement, party, request, now)).toList());
        context.setNewDocuments(request.getDocuments().stream()
            .map(document -> buildDocument(agreement.getAgreementId(), document.getDocumentType(), document.getFileId(),
                document.getContentHash(), now))
            .toList());
        context.setOperatingEntities(request.getOperatingEntityNos().stream()
            .map(entityNo -> buildOperatingEntity(agreement.getAgreementId(), entityNo, now)).toList());
        return context;
    }

    /** DRAFT 合同行：新写入一律 MERGED_PARTY 共签格式。 */
    Agreement buildAgreement(CreateAgreementRequest request, Instant now) {
        Agreement agreement = new Agreement();
        agreement.setAgreementId(idGenerator.nextId());
        agreement.setAgreementNo(request.getAgreementNo());
        agreement.setAgreementName(request.getAgreementName());
        agreement.setAgreementType(request.getAgreementType());
        agreement.setBusinessMode(request.getBusinessMode());
        agreement.setFundMode(request.getFundMode());
        agreement.setPricingAuthority(request.getPricingAuthority());
        agreement.setReferralPartnerId(request.getReferralPartnerId());
        agreement.setClientId(request.getClientId());
        agreement.setLocalPartnerId(request.getLocalPartnerId());
        agreement.setEntityNo(request.getEntityNo());
        agreement.setAgreementFormat(AgreementAgreementFormat.MERGED_PARTY);
        agreement.setStatus(AgreementStatus.DRAFT);
        agreement.setEffectiveFrom(request.getEffectiveFrom());
        agreement.setEffectiveTo(request.getEffectiveTo());
        agreement.setCreateBy(request.getActorId());
        agreement.setCreateTime(now);
        agreement.setUpdateTime(now);
        return agreement;
    }

    /** 待签参与方角色行。 */
    AgreementParty buildParty(Agreement agreement, Party party, CreateAgreementRequest request, Instant now) {
        AgreementParty row = new AgreementParty();
        row.setAgreementPartyId(idGenerator.nextId());
        row.setAgreementId(agreement.getAgreementId());
        row.setPartyId(party.getPartyId());
        row.setPartyProfileId(party.getPartyProfileId());
        row.setPartyRole(party.getPartyRole());
        row.setCreateBy(request.getActorId());
        row.setCreateTime(now);
        row.setUpdateTime(now);
        return row;
    }

    /** 合同文件引用行（插入后冻结）。 */
    AgreementDocumentRef buildDocument(long agreementId, AgreementDocumentRefDocumentType type, long fileId,
        String contentHash, Instant now) {
        AgreementDocumentRef row = new AgreementDocumentRef();
        row.setAgreementDocumentId(idGenerator.nextId());
        row.setAgreementId(agreementId);
        row.setDocumentType(type);
        row.setFileId(fileId);
        row.setContentHash(contentHash);
        row.setCreateTime(now);
        row.setUpdateTime(now);
        return row;
    }

    /** PARTNER 覆盖 OE 行。 */
    AgreementOperatingEntity buildOperatingEntity(long agreementId, String entityNo, Instant now) {
        AgreementOperatingEntity row = new AgreementOperatingEntity();
        row.setAgreementOperatingEntityId(idGenerator.nextId());
        row.setAgreementId(agreementId);
        row.setEntityNo(entityNo);
        row.setCreateTime(now);
        row.setUpdateTime(now);
        return row;
    }
}
