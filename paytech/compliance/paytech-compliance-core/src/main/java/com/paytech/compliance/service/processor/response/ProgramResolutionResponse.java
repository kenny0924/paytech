package com.paytech.compliance.service.processor.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

/** CP-1 解析出的程序版本与模板（Owner 结果）。 */
@Data
public class ProgramResolutionResponse {
    /** 程序 ID。 */
    private long programId;
    /** 程序编码。 */
    private String programCode;
    /** 版本 ID。 */
    private long programVersionId;
    /** 版本号。 */
    private int versionNo;
    /** 资格类型。 */
    private String qualificationType;
    /** 程序辖区；全局为空。 */
    private String jurisdictionCode;
    /** 档次。 */
    private String tier;
    /** 程序 Owner；平台为空。 */
    private Long ownerPartnerId;
    /** 复审间隔月数。 */
    private Integer reviewIntervalMonths;
    /** 是否四眼。 */
    private boolean fourEyesRequired;
    /** 主体类型（只用于运营程序目录）。 */
    private String partyType;
    /** 角色类型（只用于运营程序目录）。 */
    private String roleType;
    /** 程序状态（只用于运营程序目录）。 */
    private String programStatus;
    /** 版本状态（只用于运营程序目录）。 */
    private String versionStatus;
    /** 版本批准人（只用于运营程序目录）。 */
    private Long approvedBy;
    /** 版本批准时间（只用于运营程序目录）。 */
    private java.time.Instant approvedTime;
    /** 材料槽。 */
    private List<Material> materials;
    /** 检查项。 */
    private List<Check> checks;
    /** 筛查项。 */
    private List<Screening> screenings;

    /** 一个材料槽。 */
    @Getter
    @AllArgsConstructor
    public static class Material {
        /** 要求代码。 */
        private final String requirementCode;
        /** 主体角色。 */
        private final String subjectRole;
        /** 允许类型。 */
        private final List<String> documentTypes;
        /** 是否必需。 */
        private final boolean required;
        /** 最长时效天数。 */
        private final Integer maxAgeDays;
        /** 是否可豁免。 */
        private final boolean waivable;
    }

    /** 一个检查项。 */
    @Getter
    @AllArgsConstructor
    public static class Check {
        /** 检查代码。 */
        private final String checkCode;
        /** 检查类型。 */
        private final String checkType;
        /** 执行方式。 */
        private final String checkMode;
        /** 主体角色。 */
        private final String subjectRole;
        /** 是否阻断。 */
        private final boolean blocking;
        /** 有效天数。 */
        private final Integer validityDays;
    }

    /** 一个筛查项。 */
    @Getter
    @AllArgsConstructor
    public static class Screening {
        /** 筛查类型。 */
        private final String screeningType;
        /** 主体角色。 */
        private final String subjectRole;
        /** 是否必需。 */
        private final boolean required;
        /** 是否持续监控。 */
        private final boolean monitoringRequired;
    }
}
