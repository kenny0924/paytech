package com.paytech.card.api.binlibrary.response;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** BIN 库版本详情 */
@Getter
@Setter
public class BinLibraryVersionResponseBody {
    /** 版本号，从 1 起按创建顺序递增 */
    private int versionNo;

    /** 版本状态，{@code BinLibraryVersionStatus} Code：VALIDATING、VALID、INVALID、ACTIVE、RETIRED */
    private String status;

    /** 是否为当前生效版本 */
    private boolean active;

    /** Platform 文件地址 /files/v1/{fileId}；初始化种子版本为 null */
    private String fileAddress;

    /** 导入文件展示名；初始化种子版本为 null */
    private String fileName;

    /** 创建时冻结的文件 SHA-256 小写十六进制；初始化种子版本为 null */
    private String fileContentHash;

    /** 数据行数（不含表头）；校验完成前为 null */
    private Integer totalRows;

    /** 已写入号段的数据行数，导入进度 = processedRows / totalRows */
    private int processedRows;

    /** 校验失败的数据行数；大于 0 时状态为 INVALID，错误明细最多记录前 200 条 */
    private int errorRows;

    /** 文件级失败原因（扫描未通过、表头不符、哈希不一致等）；不是 INVALID 或只有行级错误时为 null */
    private String failureReason;

    /** 导入人 Operations Account ID，十进制字符串；初始化种子版本为 null */
    private String createdBy;

    /** 创建时间（UTC） */
    private Instant createTime;

    /** 最近更新时间（UTC） */
    private Instant updateTime;
}
