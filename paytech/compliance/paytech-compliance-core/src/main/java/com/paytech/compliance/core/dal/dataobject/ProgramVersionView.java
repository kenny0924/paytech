package com.paytech.compliance.core.dal.dataobject;

import java.time.Instant;

import lombok.Data;

/** 程序及其版本的组合读取结果（t_dd_program + t_dd_program_version），用于解析与案件冻结的程序属性。 */
@Data
public class ProgramVersionView {
    /** 程序 ID。 */
    private long programId;
    /** 程序编码。 */
    private String programCode;
    /** 资格类型。 */
    private String qualificationType;
    /** 程序辖区；全局程序为空。 */
    private String jurisdictionCode;
    /** 主体类型。 */
    private String partyType;
    /** 角色类型。 */
    private String roleType;
    /** 尽调档次。 */
    private String tier;
    /** 程序 Owner；平台程序为空。 */
    private Long ownerPartnerId;
    /** 程序状态：DRAFT、ACTIVE、RETIRED。 */
    private String programStatus;
    /** 版本 ID。 */
    private long programVersionId;
    /** 版本号。 */
    private int versionNo;
    /** 复审间隔月数；不复审为空。 */
    private Integer reviewIntervalMonths;
    /** 是否要求四眼：1 或 0。 */
    private int fourEyesRequired;
    /** 版本生效占位。 */
    private Integer activeSlot;
    /** 版本状态：DRAFT、ACTIVE、RETIRED。 */
    private String versionStatus;
    /** 批准人。 */
    private Long approvedBy;
    /** 批准时间。 */
    private Instant approvedTime;

    /**
     * 该版本在指定时刻是否为完整、已批准且已生效的程序权威
     *
     * @param now 当前时间
     * @return 已批准、批准时间不晚于 now 且为当前生效版本时为 true
     */
    public boolean isApprovedAuthorityAt(Instant now) {
        return programId > 0 && programVersionId > 0 && versionNo >= 1 && Integer.valueOf(1).equals(activeSlot) && approvedBy != null
            && approvedBy > 0 && approvedTime != null && !approvedTime.isAfter(now);
    }

    /** 是否要求推荐人与决定人不同。 */
    public boolean requiresFourEyes() {
        return fourEyesRequired == 1;
    }
}
