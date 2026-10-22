package com.paytech.card.core.dal.dataobject;

import java.time.Instant;

import com.paytech.card.api.enums.BinLibraryVersionStatus;
import com.paytech.platform.support.api.files.FileAddress;

import lombok.Getter;
import lombok.Setter;

/** t_card_bin_library_version 的完整持久化映射；不通过 HTTP 或其他服务暴露。 */
@Getter
@Setter
public class CardBinLibraryVersion {
    /** 版本标识。 */
    private Long versionId;

    /** 对运营展示的版本号，从 1 起递增。 */
    private Integer versionNo;

    /** 版本状态。 */
    private BinLibraryVersionStatus status;

    /**
     * 生效占位
     * <li>ACTIVE 时为 1，其余状态为 null；唯一索引保证至多一个 ACTIVE 版本。</li>
     */
    private Integer activeSlot;

    /** Platform 文件地址 /files/v1/{fileId}；种子版本为 null。 */
    private String fileAddress;

    /** 创建时冻结的文件 SHA-256 小写十六进制；种子版本为 null。 */
    private String fileContentHash;

    /** 文件展示名；种子版本为 null。 */
    private String fileName;

    /** 数据行数（不含表头）；校验完成前为 null。 */
    private Integer totalRows;

    /** 已写入号段的数据行数。 */
    private Integer processedRows;

    /** 校验失败的数据行数。 */
    private Integer errorRows;

    /** 文件级失败原因，不含卡号。 */
    private String failureReason;

    /** 导入人 Operations Account ID；种子版本为 null。 */
    private Long createdBy;

    /** 乐观并发版本。 */
    private Integer version;

    /** 创建时间。 */
    private Instant createTime;

    /** 更新时间。 */
    private Instant updateTime;

    /** 由文件地址经 Platform {@link FileAddress} 解析文件 ID；种子版本没有文件地址时为 null。 */
    public Long fileId() {
        return fileAddress == null ? null : FileAddress.parse(fileAddress).fileId();
    }

    /** 是否为当前生效版本。 */
    public boolean isActive() {
        return status == BinLibraryVersionStatus.ACTIVE;
    }

    @Override
    public String toString() {
        return "CardBinLibraryVersion[" + versionId + "/v" + versionNo + "]";
    }
}
