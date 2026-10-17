package com.paytech.compliance.test.builder;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.paytech.compliance.api.enums.MaterialQualificationType;

/**
 * 通用材料程序测试数据 Builder
 * <li>approved() 给出已批准、已生效、要求完整的合法程序；用例只改与场景相关的字段。</li>
 * <li>要求按非字典序登记，用于验证接口按 requirementCode 排序输出。</li>
 *
 * @author Kenny
 */
public final class MaterialProgramBuilder {
    /** 合法的 64 位小写十六进制内容摘要。 */
    public static final String VALID_CONTENT_HASH = "0123456789abcdef".repeat(4);

    /** 一条材料要求。 */
    public record Requirement(String requirementCode, String documentType, int requiredFlag) {}

    private final MaterialQualificationType qualificationType;

    private String programStatus = "ACTIVE";

    private String versionStatus = "ACTIVE";

    private int versionNo = 1;

    private Integer activeSlot = 1;

    private String contentHash = VALID_CONTENT_HASH;

    private Long approvedBy = 10001L;

    private Instant approvedTime = Instant.now().minus(Duration.ofHours(1));

    private List<Requirement> requirements = new ArrayList<>(List.of(
        new Requirement("PROOF_OF_ADDRESS", "UTILITY_BILL", 0),
        new Requirement("IDENTITY_DOCUMENT", "PASSPORT", 1)));

    private MaterialProgramBuilder(MaterialQualificationType qualificationType) {
        this.qualificationType = qualificationType;
    }

    /** 指定资格类型下已批准且已生效的合法程序。 */
    public static MaterialProgramBuilder approved(MaterialQualificationType qualificationType) {
        return new MaterialProgramBuilder(qualificationType);
    }

    /** 设置程序状态。 */
    public MaterialProgramBuilder programStatus(String value) {
        this.programStatus = value;
        return this;
    }

    /** 设置修订状态。 */
    public MaterialProgramBuilder versionStatus(String value) {
        this.versionStatus = value;
        return this;
    }

    /** 设置修订序号。 */
    public MaterialProgramBuilder versionNo(int value) {
        this.versionNo = value;
        return this;
    }

    /** 设置生效占位，null 表示未占用。 */
    public MaterialProgramBuilder activeSlot(Integer value) {
        this.activeSlot = value;
        return this;
    }

    /** 设置内容摘要。 */
    public MaterialProgramBuilder contentHash(String value) {
        this.contentHash = value;
        return this;
    }

    /** 设置批准人，null 表示未批准。 */
    public MaterialProgramBuilder approvedBy(Long value) {
        this.approvedBy = value;
        return this;
    }

    /** 设置批准时间，null 表示未批准。 */
    public MaterialProgramBuilder approvedTime(Instant value) {
        this.approvedTime = value;
        return this;
    }

    /** 替换全部材料要求。 */
    public MaterialProgramBuilder requirements(List<Requirement> values) {
        this.requirements = new ArrayList<>(values);
        return this;
    }

    /** 追加一条材料要求。 */
    public MaterialProgramBuilder addRequirement(String requirementCode, String documentType, int requiredFlag) {
        this.requirements.add(new Requirement(requirementCode, documentType, requiredFlag));
        return this;
    }

    public MaterialQualificationType qualificationType() {
        return qualificationType;
    }

    public String programStatus() {
        return programStatus;
    }

    public String versionStatus() {
        return versionStatus;
    }

    public int versionNo() {
        return versionNo;
    }

    public Integer activeSlot() {
        return activeSlot;
    }

    public String contentHash() {
        return contentHash;
    }

    public Long approvedBy() {
        return approvedBy;
    }

    public Instant approvedTime() {
        return approvedTime;
    }

    public List<Requirement> requirements() {
        return List.copyOf(requirements);
    }
}
