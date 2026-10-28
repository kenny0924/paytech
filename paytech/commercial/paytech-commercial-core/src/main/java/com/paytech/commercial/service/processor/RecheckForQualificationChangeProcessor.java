package com.paytech.commercial.service.processor;

import static com.paytech.commercial.api.enums.CheckoutErrorCode.OWNER_UNAVAILABLE;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.paytech.commercial.api.enums.RelationshipSubjectType;
import com.paytech.commercial.core.dal.dataobject.Agreement;
import com.paytech.commercial.domain.enums.AgreementAgreementType;
import com.paytech.commercial.domain.enums.AgreementStatus;
import com.paytech.commercial.domainservice.repository.AgreementCapabilityRepository;
import com.paytech.commercial.domainservice.repository.AgreementRepository;
import com.paytech.commercial.service.processor.request.CheckCapabilityPrerequisitesRequest;
import com.paytech.commercial.service.processor.request.RecheckForQualificationChangeRequest;
import com.paytech.commercial.service.processor.response.CapabilityPrerequisiteCheckResponse;
import com.paytech.commercial.service.processor.response.CapabilityPrerequisiteRecheckResponse;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * 资格变化后的开通项前置重查（开通项前置门禁 technique.md §11.2）：按角色定位受影响的 CLIENT 合同，逐个在事务外调用门禁
 * <li>CLIENT → 该 Client 的 CLIENT 合同；OPERATING_ENTITY → 有开通项在该 OE 上的 CLIENT 合同；只取 SIGNED / ACTIVE 合同；
 * LOCAL_PARTNER 与其他角色忽略（S-07）。</li>
 * <li>事件只触发重查，结论以同步资格查询为准；重复投递再查一次。</li>
 * <li>门禁的冲突或系统错误原样抛出；任一合同资格查询不可用时全部处理后抛出 OWNER_UNAVAILABLE（503），由消息重投再查。本身不加锁、不开事务。</li>
 */
@Component
@AllArgsConstructor
public class RecheckForQualificationChangeProcessor
    extends AbstractProcessor<RecheckForQualificationChangeRequest, CapabilityPrerequisiteRecheckResponse> {
    /** 资格变化会影响执行的合同状态。 */
    static final Set<AgreementStatus> GATED_STATUSES = Set.of(AgreementStatus.SIGNED, AgreementStatus.ACTIVE);

    private static final Logger LOG = LoggerFactory.getLogger(RecheckForQualificationChangeProcessor.class);

    private final AgreementRepository agreementRepository;
    private final AgreementCapabilityRepository agreementCapabilityRepository;
    private final CheckCapabilityPrerequisitesProcessor checkCapabilityPrerequisitesProcessor;

    @Override
    protected CapabilityPrerequisiteRecheckResponse processRequest(RecheckForQualificationChangeRequest request) {
        // 定位受影响合同
        List<Agreement> agreements = affectedAgreements(request);

        // 逐个核对
        int changed = 0;
        int unknown = 0;
        for (Agreement agreement : agreements) {
            CapabilityPrerequisiteCheckResponse response = checkCapabilityPrerequisitesProcessor.process(
                new CheckCapabilityPrerequisitesRequest(agreement.getAgreementId()));
            changed += response.isChanged() ? 1 : 0;
            unknown += response.isUnknown() ? 1 : 0;
        }
        if (unknown > 0) {
            throw new ErrorException(OWNER_UNAVAILABLE, "资格查询不可用，开通项前置重查需要重试: " + request.getRoleType() + " "
                + request.getRoleId());
        }
        return new CapabilityPrerequisiteRecheckResponse(agreements.size(), changed, 0, 0);
    }

    /** 按角色定位 SIGNED / ACTIVE 的 CLIENT 合同；不需要处理的角色为空列表。 */
    List<Agreement> affectedAgreements(RecheckForQualificationChangeRequest request) {
        List<Agreement> candidates = new ArrayList<>();
        if (RelationshipSubjectType.CLIENT.getCode().equals(request.getRoleType())) {
            candidates.addAll(agreementRepository.findByClientId(clientId(request.getRoleId())));
        } else if (RelationshipSubjectType.OPERATING_ENTITY.getCode().equals(request.getRoleType())) {
            agreementCapabilityRepository.findAgreementIdsByEntityNo(request.getRoleId())
                .forEach(agreementId -> candidates.add(agreementRepository.findById(agreementId)));
        } else {
            LOG.info("资格变化角色不影响开通项门禁，忽略 roleType={}", request.getRoleType());
        }
        return candidates.stream().filter(agreement -> agreement != null
            && agreement.getAgreementType() == AgreementAgreementType.CLIENT
            && GATED_STATUSES.contains(agreement.getStatus())).toList();
    }

    /** CLIENT 角色标识为十进制 Client ID。 */
    static long clientId(String roleId) {
        try {
            return Long.parseLong(roleId);
        } catch (NumberFormatException malformed) {
            throw new IllegalArgumentException("Client 角色标识不是十进制 ID: " + roleId, malformed);
        }
    }
}
