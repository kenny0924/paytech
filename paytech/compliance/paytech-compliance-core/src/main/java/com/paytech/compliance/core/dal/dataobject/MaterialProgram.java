package com.paytech.risk.compliance.core.dal.dataobject;

import java.time.Instant;

import lombok.Data;

/** 材料程序及其当前 ACTIVE 修订的只读投影，来自 t_dd_program 与 t_dd_program_version。 */
@Data
public class MaterialProgram {
    /** 程序 ID。 */
    private long programId;

    /** 生效修订 ID。 */
    private long programVersionId;

    /** 修订序号，从 1 开始。 */
    private int versionNo;

    /**
     * 生效占位
     * <li>唯一生效修订必须为 1，其他为空。</li>
     */
    private Integer activeSlot;

    /** 经批准材料闭包的小写十六进制 SHA-256。 */
    private String contentHash;

    /** 批准人账号 ID；未批准为空。 */
    private Long approvedBy;

    /** 批准时间（UTC）；未批准为空。 */
    private Instant approvedTime;

    /** 判断该修订在指定时刻是否为完整、已批准且已生效的材料权威。 */
    public boolean isApprovedAuthorityAt(Instant now) {
        return programId > 0 && programVersionId > 0 && versionNo >= 1 && Integer.valueOf(1).equals(activeSlot)
            && approvedBy != null && approvedBy > 0 && approvedTime != null && !approvedTime.isAfter(now)
            && contentHash != null && contentHash.matches("[0-9a-f]{64}");
    }
}
