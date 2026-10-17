package com.paytech.risk.test.builder.compliance;

import static com.paytech.risk.api.enums.MaterialQualificationType.INDIVIDUAL_KYC;
import static com.paytech.risk.api.enums.MaterialQualificationType.ORGANIZATION_KYB;
import static com.paytech.risk.api.enums.MaterialQualificationType.RELATED_PERSON_KYC;
import static com.paytech.risk.test.builder.compliance.ClientReviewRegistrationRequestBuilder.IDENTITY_DOCUMENT;
import static com.paytech.risk.test.builder.compliance.ClientReviewRegistrationRequestBuilder.PROOF_OF_ADDRESS;
import static com.paytech.risk.test.builder.compliance.ClientReviewRegistrationRequestBuilder.REGISTRATION_CERTIFICATE;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.paytech.risk.api.enums.MaterialQualificationType;
import com.paytech.risk.compliance.domain.ClientReviewAction;
import com.paytech.risk.compliance.service.processor.RegisterClientReviewProcessor;

/**
 * Client 入驻审核任务的本地前置事实 Builder（交给 ClientReviewMocker 直接插入，不经过登记接口）
 * <li>organization()：组织主任务 SUBMITTED，两名关联人员子任务（甲 UBO_KYC + DIRECTOR_KYC，乙 SIGNATORY_KYC）；
 * 每个任务的基础必需材料各有一份 RECEIVED 证据，非必需材料 PENDING 且没有证据，尚未接收分类。</li>
 * <li>individual()：个人主任务，没有子任务。</li>
 * <li>readyForApproval() 把全部证据与对应要求置为 ACCEPTED 并接收一个主要分类，即满足通过门禁。</li>
 *
 * @author Kenny
 */
public final class ClientReviewCaseBuilder {
    /** 案件、要求与证据的状态代码；生产未定义枚举，只在测试侧集中声明。 */
    public static final String SUBMITTED = "SUBMITTED";

    public static final String PENDING = "PENDING";

    public static final String RECEIVED = "RECEIVED";

    public static final String ACCEPTED = "ACCEPTED";

    public static final String REJECTED = "REJECTED";

    /** 拒绝时随主任务撤销的子任务状态。 */
    public static final String WITHDRAWN = ClientReviewAction.REJECT.getChildStatus();

    /** 固定的合成提交时间，带微秒。 */
    public static final Instant SUBMITTED_TIME = Instant.parse("2026-10-13T08:00:00.123456Z");

    /** 一个材料要求：状态及其证据状态（按证据 ID 升序）。 */
    public record RequirementSpec(String status, List<String> evidence) {}

    /** 一个任务：资格类型、状态、子任务的依赖类型及按要求代码索引的材料要求。 */
    public static final class CaseSpec {
        private final MaterialQualificationType qualificationType;

        private final List<String> dependencyTypes;

        private final Map<String, RequirementSpec> requirements = new LinkedHashMap<>();

        private String status = SUBMITTED;

        private boolean active = true;

        CaseSpec(MaterialQualificationType qualificationType, List<String> dependencyTypes) {
            this.qualificationType = qualificationType;
            this.dependencyTypes = List.copyOf(dependencyTypes);
        }

        public MaterialQualificationType qualificationType() {
            return qualificationType;
        }

        public List<String> dependencyTypes() {
            return dependencyTypes;
        }

        public Map<String, RequirementSpec> requirements() {
            return requirements;
        }

        public String status() {
            return status;
        }

        public boolean active() {
            return active;
        }
    }

    /** 已接收的一条分类。 */
    public record Classification(String industryCode, String mccCode, boolean primary) {}

    private final CaseSpec main;

    private final List<CaseSpec> children = new ArrayList<>();

    private final List<Classification> classifications = new ArrayList<>();

    private String sourceNamespace = RegisterClientReviewProcessor.NAMESPACE;

    private Instant submittedTime = SUBMITTED_TIME;

    private long version = 1;

    private Instant decisionTime;

    private String publicNote;

    private String resolutionNote;

    private String returnSection;

    private long confirmedBy = 930000001L;

    private Instant confirmedAt = Instant.parse("2026-10-13T09:00:00.123456Z");

    private ClientReviewCaseBuilder(CaseSpec main) {
        this.main = main;
    }

    /** 组织主任务及两名关联人员子任务。 */
    public static ClientReviewCaseBuilder organization() {
        CaseSpec main = new CaseSpec(ORGANIZATION_KYB, List.of());
        main.requirements.put(REGISTRATION_CERTIFICATE, new RequirementSpec(RECEIVED, List.of(RECEIVED)));
        main.requirements.put(PROOF_OF_ADDRESS, new RequirementSpec(PENDING, List.of()));
        ClientReviewCaseBuilder builder = new ClientReviewCaseBuilder(main);
        builder.children.add(person(List.of("UBO_KYC", "DIRECTOR_KYC")));
        builder.children.add(person(List.of("SIGNATORY_KYC")));
        return builder;
    }

    /** 个人主任务，没有子任务。 */
    public static ClientReviewCaseBuilder individual() {
        CaseSpec main = new CaseSpec(INDIVIDUAL_KYC, List.of());
        main.requirements.put(IDENTITY_DOCUMENT, new RequirementSpec(RECEIVED, List.of(RECEIVED)));
        main.requirements.put(PROOF_OF_ADDRESS, new RequirementSpec(PENDING, List.of()));
        return new ClientReviewCaseBuilder(main);
    }

    /** 关联人员子任务：身份材料一份 RECEIVED，地址证明 PENDING。 */
    private static CaseSpec person(List<String> dependencyTypes) {
        CaseSpec child = new CaseSpec(RELATED_PERSON_KYC, dependencyTypes);
        child.requirements.put(IDENTITY_DOCUMENT, new RequirementSpec(RECEIVED, List.of(RECEIVED)));
        child.requirements.put(PROOF_OF_ADDRESS, new RequirementSpec(PENDING, List.of()));
        return child;
    }

    /** 全部证据及其要求已接受，并已接收一个主要分类：满足通过门禁。 */
    public ClientReviewCaseBuilder readyForApproval() {
        for (CaseSpec spec : allCases()) {
            spec.requirements.replaceAll((code, requirement) -> requirement.evidence().isEmpty() ? requirement
                : new RequirementSpec(ACCEPTED, requirement.evidence().stream().map(status -> ACCEPTED).toList()));
        }
        return classifications.isEmpty() ? classification("RETAIL", "5411", true) : this;
    }

    /** 替换主任务某要求的状态与证据。 */
    public ClientReviewCaseBuilder mainRequirement(String requirementCode, String status, String... evidence) {
        main.requirements.put(requirementCode, new RequirementSpec(status, List.of(evidence)));
        return this;
    }

    /** 替换第 index 个子任务某要求的状态与证据。 */
    public ClientReviewCaseBuilder childRequirement(int index, String requirementCode, String status,
        String... evidence) {
        children.get(index).requirements.put(requirementCode, new RequirementSpec(status, List.of(evidence)));
        return this;
    }

    /** 第 index 个子任务已单独结束为给定状态。 */
    public ClientReviewCaseBuilder childClosed(int index, String status) {
        children.get(index).status = status;
        children.get(index).active = false;
        return this;
    }

    /** 追加一条已接收分类。 */
    public ClientReviewCaseBuilder classification(String industryCode, String mccCode, boolean primary) {
        classifications.add(new Classification(industryCode, mccCode, primary));
        return this;
    }

    /** 清空已接收分类。 */
    public ClientReviewCaseBuilder withoutClassifications() {
        classifications.clear();
        return this;
    }

    /** 分类的确认人与确认时间。 */
    public ClientReviewCaseBuilder confirmed(long confirmedBy, Instant confirmedAt) {
        this.confirmedBy = confirmedBy;
        this.confirmedAt = confirmedAt;
        return this;
    }

    /** 主任务及仍在进行的子任务已按给定结论结束；说明与分区按结论填写。 */
    public ClientReviewCaseBuilder closed(ClientReviewAction action) {
        main.status = action.getCaseStatus();
        main.active = false;
        for (CaseSpec child : children) {
            if (child.active) {
                child.status = action.getChildStatus();
                child.active = false;
            }
        }
        version = 2;
        decisionTime = Instant.parse("2026-10-13T10:00:00.123456Z");
        publicNote = action == ClientReviewAction.RETURN ? "Please upload a clearer certificate." : null;
        resolutionNote = action == ClientReviewAction.RETURN ? null : "Synthetic earlier decision";
        returnSection = action == ClientReviewAction.RETURN ? "MATERIALS" : null;
        return this;
    }

    /** 来源命名空间，用于构造不属于 Client 入驻的案件。 */
    public ClientReviewCaseBuilder sourceNamespace(String value) {
        this.sourceNamespace = value;
        return this;
    }

    /** 主任务提交时间。 */
    public ClientReviewCaseBuilder submittedTime(Instant value) {
        this.submittedTime = value;
        return this;
    }

    /** 主任务当前版本。 */
    public ClientReviewCaseBuilder version(long value) {
        this.version = value;
        return this;
    }

    public CaseSpec main() {
        return main;
    }

    public List<CaseSpec> children() {
        return List.copyOf(children);
    }

    /** 主任务在前，子任务按登记顺序。 */
    public List<CaseSpec> allCases() {
        List<CaseSpec> all = new ArrayList<>();
        all.add(main);
        all.addAll(children);
        return all;
    }

    public List<Classification> classifications() {
        return List.copyOf(classifications);
    }

    public String sourceNamespace() {
        return sourceNamespace;
    }

    public Instant submittedTime() {
        return submittedTime;
    }

    public long version() {
        return version;
    }

    public Instant decisionTime() {
        return decisionTime;
    }

    public String publicNote() {
        return publicNote;
    }

    public String resolutionNote() {
        return resolutionNote;
    }

    public String returnSection() {
        return returnSection;
    }

    public long confirmedBy() {
        return confirmedBy;
    }

    public Instant confirmedAt() {
        return confirmedAt;
    }
}
