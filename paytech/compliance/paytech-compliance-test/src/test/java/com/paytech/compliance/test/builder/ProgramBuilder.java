package com.paytech.compliance.test.builder;

import java.util.ArrayList;
import java.util.List;

/**
 * 测试程序定义：辖区、主体类型、角色、Owner、四眼、复审间隔与材料 / 检查 / 筛查模板
 * <li>{@link #zzOrganization()} 等虚构国家 ZZ 的模板只存在于测试夹具，不进入任何初始化 DML（technique S-09）。</li>
 */
public class ProgramBuilder {
    String code;
    String qualificationType;
    String jurisdictionCode;
    String partyType;
    String roleType;
    String tier = "STANDARD";
    Long ownerPartnerId;
    boolean fourEyes = true;
    Integer reviewIntervalMonths = 36;
    String status = "ACTIVE";
    boolean approved = true;
    final List<Material> materials = new ArrayList<>();
    final List<Check> checks = new ArrayList<>();
    final List<String> screenings = new ArrayList<>();

    /**
     * 材料槽
     *
     * @param code 要求代码
     * @param types 允许类型
     * @param required 是否必需
     * @param maxAgeDays 最长时效
     * @param waivable 是否可豁免
     */
    public record Material(String code, List<String> types, boolean required, Integer maxAgeDays, boolean waivable) {
    }

    /**
     * 人工检查
     *
     * @param code 检查代码
     * @param blocking 是否阻断
     * @param validityDays 有效天数
     */
    public record Check(String code, boolean blocking, Integer validityDays) {
    }

    /** 虚构国家 ZZ 的机构 Client 程序：自定义材料类型 ZZ_BUSINESS_PERMIT 与人工检查 ZZ_REGISTRY_CHECK。 */
    public static ProgramBuilder zzOrganization() {
        return new ProgramBuilder().code("ZZ_CLIENT_ORGANIZATION_STANDARD").jurisdiction("ZZ").scope("ORGANIZATION", "CLIENT", "ORGANIZATION_KYB")
            .material(new Material("REGISTRATION", List.of("ZZ_BUSINESS_PERMIT"), true, null, false))
            .material(new Material("BANK", List.of("BANK_PROOF"), false, 180, true))
            .check(new Check("ZZ_REGISTRY_CHECK", true, 90)).screening("SANCTIONS");
    }

    /** 虚构国家 ZZ 的关联人员程序。 */
    public static ProgramBuilder zzRelatedPerson() {
        return new ProgramBuilder().code("ZZ_RELATED_PERSON_STANDARD").jurisdiction("ZZ").scope("INDIVIDUAL", "RELATED_PERSON",
            "RELATED_PERSON_KYC").material(new Material("IDENTITY", List.of("ZZ_RESIDENT_CARD"), true, null, false)).screening("SANCTIONS");
    }

    /** 全局（辖区为空）机构程序。 */
    public static ProgramBuilder globalOrganization() {
        return new ProgramBuilder().code("GLOBAL_CLIENT_ORGANIZATION").jurisdiction(null).scope("ORGANIZATION", "CLIENT", "ORGANIZATION_KYB")
            .material(new Material("REGISTRATION", List.of("COMMERCIAL_REGISTRATION"), true, null, false));
    }

    /** 程序编码。 */
    public ProgramBuilder code(String value) {
        this.code = value;
        return this;
    }

    /** 辖区；null 为全局。 */
    public ProgramBuilder jurisdiction(String value) {
        this.jurisdictionCode = value;
        return this;
    }

    /** 主体类型、角色与资格类型。 */
    public ProgramBuilder scope(String party, String role, String qualification) {
        this.partyType = party;
        this.roleType = role;
        this.qualificationType = qualification;
        return this;
    }

    /** Owner PayFac。 */
    public ProgramBuilder owner(Long partnerId) {
        this.ownerPartnerId = partnerId;
        return this;
    }

    /** 是否四眼。 */
    public ProgramBuilder fourEyes(boolean value) {
        this.fourEyes = value;
        return this;
    }

    /** 复审间隔。 */
    public ProgramBuilder reviewIntervalMonths(Integer value) {
        this.reviewIntervalMonths = value;
        return this;
    }

    /** 版本未批准（approved_by 为空）。 */
    public ProgramBuilder unapproved() {
        this.approved = false;
        return this;
    }

    /** 追加材料槽。 */
    public ProgramBuilder material(Material value) {
        materials.add(value);
        return this;
    }

    /** 追加人工检查。 */
    public ProgramBuilder check(Check value) {
        checks.add(value);
        return this;
    }

    /** 追加筛查类型（PRIMARY，必需）。 */
    public ProgramBuilder screening(String type) {
        screenings.add(type);
        return this;
    }

    /** 程序编码。 */
    public String getCode() {
        return code;
    }

    /** 辖区。 */
    public String getJurisdictionCode() {
        return jurisdictionCode;
    }

    /** 主体类型。 */
    public String getPartyType() {
        return partyType;
    }

    /** 角色。 */
    public String getRoleType() {
        return roleType;
    }

    /** 资格类型。 */
    public String getQualificationType() {
        return qualificationType;
    }

    /** 档次。 */
    public String getTier() {
        return tier;
    }

    /** Owner。 */
    public Long getOwnerPartnerId() {
        return ownerPartnerId;
    }

    /** 四眼。 */
    public boolean isFourEyes() {
        return fourEyes;
    }

    /** 复审间隔。 */
    public Integer getReviewIntervalMonths() {
        return reviewIntervalMonths;
    }

    /** 状态。 */
    public String getStatus() {
        return status;
    }

    /** 是否已批准。 */
    public boolean isApproved() {
        return approved;
    }

    /** 材料槽。 */
    public List<Material> getMaterials() {
        return materials;
    }

    /** 检查。 */
    public List<Check> getChecks() {
        return checks;
    }

    /** 筛查类型。 */
    public List<String> getScreenings() {
        return screenings;
    }
}
