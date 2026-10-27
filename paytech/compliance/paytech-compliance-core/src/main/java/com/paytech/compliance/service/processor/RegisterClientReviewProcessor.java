package com.paytech.compliance.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import com.paytech.compliance.core.dal.dataobject.CaseDependency;
import com.paytech.compliance.core.dal.dataobject.DueDiligenceCase;
import com.paytech.compliance.core.dal.dataobject.EvidenceRef;
import com.paytech.compliance.core.enums.DueDiligenceCaseStatus;
import com.paytech.compliance.core.enums.ReviewCodes;
import com.paytech.compliance.domain.ClientReviewCaseBundle;
import com.paytech.compliance.domain.ClientReviewContext;
import com.paytech.compliance.domain.ClientReviewFamily;
import com.paytech.compliance.domain.ResolvedProgram;
import com.paytech.compliance.domainservice.repository.DueDiligenceCaseRepository;
import com.paytech.compliance.domainservice.repository.EvidenceRefRepository;
import com.paytech.compliance.domainservice.service.ClientReviewService;
import com.paytech.compliance.domainservice.service.DueDiligenceProgramService;
import com.paytech.compliance.service.processor.request.RegisterClientReviewRequest;
import com.paytech.compliance.service.processor.request.RegisterClientReviewRequest.Relation;
import com.paytech.compliance.service.processor.request.RegisterClientReviewRequest.Subject;
import com.paytech.compliance.service.processor.response.RegisterClientReviewResponse;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * I01 v2：按来源提交编号登记 Client 入驻审核案件
 * <li>主案件 role=CLIENT；每名关联人员一个 RELATED_PERSON_KYC 子案件，通过依赖关联主案件。</li>
 * <li>程序按（辖区，主体类型，角色，STANDARD，责任 PayFac）解析并冻结版本；无可用程序 503，不回退。</li>
 * <li>退回后重提（S-01）：新提交编号 + previousCaseId，新建案件、轮次加 1，复用上一轮已接受材料与有效 PASS 检查；
 * previousCaseId 必须是该申请最近一轮、仍为 RETURNED、未过补件期限且未被重提的主案件，责任方与辖区不得改变。</li>
 * <li>同一来源提交已登记时比较原案件与本次内容：一致返回原主案件，不一致 409；唯一键 uk_dd_case_source 是最终保护，不加 Redis 锁。</li>
 * <li>保存同事务登记 CASE_SCREENING 补偿任务，提交后执行名单筛查。资料快照本期不做，不写 t_dd_party_snapshot，也不调用 Vault。</li>
 */
@Component
@AllArgsConstructor
public class RegisterClientReviewProcessor extends AbstractProcessor<RegisterClientReviewRequest, RegisterClientReviewResponse> {
    private final Clock clock;
    private final ClientReviewService reviews;
    private final EvidenceRefRepository evidence;
    private final DueDiligenceCaseRepository cases;
    private final ClientReviewFamilyLoader families;
    private final DueDiligenceProgramService programs;
    private final ClientReviewRegistrationBuilder builder;

    /** 只依赖输入的结构关系：主体角色与资格、关系与材料的主体归属。 */
    @Override
    protected void validate(RegisterClientReviewRequest request) {
        boolean organization = ReviewCodes.PARTY_ORGANIZATION.equals(request.getClientType());
        if (!organization && !ReviewCodes.PARTY_INDIVIDUAL.equals(request.getClientType())) {
            throw new InvalidParameterException("客户类型无效");
        }
        List<Subject> subjects = request.getSubjects();
        Subject primary = subjects.getFirst();
        if (!ReviewCodes.SUBJECT_PRIMARY.equals(primary.getSubjectRole())
            || !(organization ? "ORGANIZATION_KYB" : "INDIVIDUAL_KYC").equals(primary.getQualificationType())) {
            throw new InvalidParameterException("主主体角色或资格类型无效");
        }
        Set<Long> parties = new HashSet<>();
        for (Subject subject : subjects) {
            boolean related = subject != primary;
            if (!parties.add(subject.getPartyId()) || (related && (!organization
                || !ReviewCodes.ROLE_RELATED_PERSON.equals(subject.getSubjectRole())
                || !"RELATED_PERSON_KYC".equals(subject.getQualificationType())))) {
                throw new InvalidParameterException("受审主体重复或关联人员无效");
            }
        }
        checkRelations(request, parties);
        if (request.getDocuments().stream().anyMatch(document -> !parties.contains(document.getSubjectPartyId()))) {
            throw new InvalidParameterException("材料所属主体不在受审范围");
        }
    }

    /** 原提交重放直接返回；新提交校验上一轮、解析程序、构建并保存全部案件。 */
    @Override
    protected RegisterClientReviewResponse processRequest(RegisterClientReviewRequest request) {
        // 查询原提交
        List<DueDiligenceCase> existing = submissionCases(request);
        if (!existing.isEmpty()) {
            return replay(request, existing);
        }

        // 上一轮案件与上下文
        Instant now = ClientReviewRegistrationBuilder.micros(clock.instant());
        ClientReviewFamily previous = previousFamily(request, now);
        ClientReviewContext context = buildContext(request, previous, now);

        // 保存上下文（同事务登记筛查任务）
        try {
            reviews.register(context);
        } catch (DuplicateKeyException collision) {
            return collision(request, collision);
        }

        // 响应处理
        return new RegisterClientReviewResponse(request.getSourceSubmissionId(), context.getMainCase().getReviewCase().getCaseId());
    }

    /** 每名关联人员至少一条关系，关系只能指向关联人员。 */
    void checkRelations(RegisterClientReviewRequest request, Set<Long> parties) {
        long primaryParty = request.getSubjects().getFirst().getPartyId();
        Set<Long> related = new HashSet<>();
        for (Relation relation : request.getRelations()) {
            if (relation.getRelatedPartyId() == primaryParty || !parties.contains(relation.getRelatedPartyId())) {
                throw new InvalidParameterException("关系指向的人员无效");
            }
            related.add(relation.getRelatedPartyId());
        }
        if (related.size() != parties.size() - 1) {
            throw new InvalidParameterException("关联人员缺少关系");
        }
    }

    /**
     * 校验并读取上一轮案件全貌
     * <li>首次提交：该申请不得已有案件。重提：必须指向最近一轮仍为 RETURNED、未过期、未被重提的主案件，且责任方、辖区一致。</li>
     *
     * @param request I01 输入
     * @param now 当前时间
     * @return 上一轮全貌；首次提交为空
     */
    ClientReviewFamily previousFamily(RegisterClientReviewRequest request, Instant now) {
        DueDiligenceCase latest = cases.findLatestClientCase(request.getApplicationId());
        if (request.getPreviousCaseId() == null) {
            if (latest != null) {
                throw new BizCheckFailException("申请已有审核案件，重提必须带上一轮案件 ID: " + request.getApplicationId());
            }
            return null;
        }
        if (latest == null || latest.getCaseId() != request.getPreviousCaseId()
            || !DueDiligenceCaseStatus.RETURNED.getCode().equals(latest.getStatus()) || cases.hasSuccessor(latest.getCaseId())) {
            throw new BizCheckFailException("上一轮案件不是该申请当前被退回的案件: " + request.getPreviousCaseId());
        }
        if (latest.getReviewDueTime() != null && !latest.getReviewDueTime().isAfter(now)) {
            throw new BizCheckFailException("上一轮案件已超过补件期限: " + request.getPreviousCaseId());
        }
        if (!Objects.equals(latest.getResponsiblePartnerId(), request.getResponsiblePartnerId())
            || !latest.getJurisdictionCode().equals(request.getJurisdictionCode())) {
            throw new BizCheckFailException("重提不能改变责任方或辖区: " + request.getPreviousCaseId());
        }
        return families.load(latest.getCaseId());
    }

    /** 解析程序、实例化各案件并一次构造完整上下文。 */
    ClientReviewContext buildContext(RegisterClientReviewRequest request, ClientReviewFamily previous, Instant now) {
        List<Subject> subjects = request.getSubjects();
        ResolvedProgram mainProgram = programs.resolve(request.getClientType(), ReviewCodes.ROLE_CLIENT, ReviewCodes.TIER_STANDARD,
            request.getJurisdictionCode(), request.getResponsiblePartnerId(), now);
        ClientReviewCaseBundle main = builder.bundle(request, subjects.getFirst(), request.getClientId(), mainProgram,
            previous == null ? null : previous.getMainCase(), previous, now);
        List<ClientReviewCaseBundle> children = new ArrayList<>();
        List<CaseDependency> dependencies = new ArrayList<>();
        if (subjects.size() > 1) {
            ResolvedProgram relatedProgram = programs.resolve(ReviewCodes.PARTY_INDIVIDUAL, ReviewCodes.ROLE_RELATED_PERSON,
                ReviewCodes.TIER_STANDARD, request.getJurisdictionCode(), request.getResponsiblePartnerId(), now);
            for (Subject subject : subjects.subList(1, subjects.size())) {
                DueDiligenceCase previousChild = ClientReviewRegistrationBuilder.previousChild(previous, subject);
                ClientReviewCaseBundle child = builder.bundle(request, subject, subject.getPartyId(), relatedProgram, previousChild,
                    previous, now);
                child.getReviewCase().setRoundNo(main.getReviewCase().getRoundNo());
                children.add(child);
                dependencies.addAll(builder.dependencies(request, main, child, now));
            }
        }
        return new ClientReviewContext(main, children, dependencies);
    }

    /** 同一来源提交的已登记案件。 */
    List<DueDiligenceCase> submissionCases(RegisterClientReviewRequest request) {
        return cases.findBySubmission(ReviewCodes.NAMESPACE_CLIENT_APPLICATION, request.getApplicationId(),
            request.getSourceSubmissionId());
    }

    /** 已登记：主体集合一致且本次材料都已登记（复用复制的证据除外）则返回原主案件，否则 409。 */
    RegisterClientReviewResponse replay(RegisterClientReviewRequest request, List<DueDiligenceCase> existing) {
        Set<String> expectedCases = new HashSet<>();
        expectedCases.add(ReviewCodes.ROLE_CLIENT + ":" + request.getClientId() + ":" + request.getSubjects().getFirst().getQualificationType());
        request.getSubjects().subList(1, request.getSubjects().size()).forEach(subject -> expectedCases
            .add(ReviewCodes.ROLE_RELATED_PERSON + ":" + subject.getPartyId() + ":" + subject.getQualificationType()));
        Set<String> actualCases = new HashSet<>();
        existing.forEach(row -> actualCases.add(row.getRoleType() + ":" + row.getRoleId() + ":" + row.getQualificationType()));
        Set<String> expectedEvidence = new HashSet<>();
        request.getDocuments().forEach(document -> expectedEvidence.add(document.getFileAddress() + ":" + document.getContentHash()));
        Set<String> fresh = new HashSet<>();
        Set<String> all = new HashSet<>();
        for (EvidenceRef row : evidence.findByCaseIds(existing.stream().map(DueDiligenceCase::getCaseId).toList())) {
            String key = row.getFileAddress() + ":" + row.getContentHash();
            all.add(key);
            if (row.getReusedFromCaseId() == null) {
                fresh.add(key);
            }
        }
        if (!expectedCases.equals(actualCases) || !expectedEvidence.containsAll(fresh) || !all.containsAll(expectedEvidence)) {
            throw new ConcurrentRequestException("同一来源提交编号的内容不一致");
        }
        return existing.stream().filter(row -> ReviewCodes.ROLE_CLIENT.equals(row.getRoleType())).findFirst()
            .map(row -> new RegisterClientReviewResponse(request.getSourceSubmissionId(), row.getCaseId()))
            .orElseThrow(() -> new ConcurrentRequestException("同一来源提交编号的内容不一致"));
    }

    /** 并发首次登记由唯一键回滚后按原提交处理；主体已有其他进行中案件明确冲突。 */
    RegisterClientReviewResponse collision(RegisterClientReviewRequest request, DuplicateKeyException collision) {
        String reason = collision.getMostSpecificCause().getMessage();
        if (reason != null && reason.contains("uk_dd_case_source")) {
            List<DueDiligenceCase> existing = submissionCases(request);
            if (!existing.isEmpty()) {
                return replay(request, existing);
            }
        }
        if (reason != null && reason.contains("uk_dd_case_active")) {
            throw new ConcurrentRequestException("受审主体已有进行中的审核案件");
        }
        throw collision;
    }
}
