package com.paytech.platform.support.api.filestorage.response;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 检查策略的一个修订。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ScanPolicyRevision {

    /** 修订号，从 1 开始。 */
    private int revisionNo;

    /**
     * 修订状态
     * <li>DRAFT、ACTIVE、RETIRED；同一代码最多一个 ACTIVE、一个 DRAFT。</li>
     */
    private String status;

    /** 允许的规范 MIME。 */
    private List<String> allowedMimeTypes;

    /** 最大字节数，十进制字符串。 */
    private String maxSizeBytes;

    /** 检查要求；本期只有格式检查，只读。 */
    private List<ScanPolicyRequirement> requirements;

    /** 乐观并发版本。 */
    private int version;

    /** 创建时间（UTC）。 */
    private Instant createTime;

    /** 最后更新时间（UTC）。 */
    private Instant updateTime;
}
