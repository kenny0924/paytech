package com.paytech.platform.support.api.filestorage.response;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 一个检查策略代码的摘要；内容取 ACTIVE 修订，没有 ACTIVE 时取 DRAFT 修订。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ScanPolicySummary {

    /** 策略代码。 */
    private String policyCode;

    /** 当前 ACTIVE 修订号；没有为空。 */
    private Integer activeRevisionNo;

    /** DRAFT 修订号；没有为空。 */
    private Integer draftRevisionNo;

    /**
     * 允许的规范 MIME
     * <li>可能含格式检查器无法识别的存量类型（不在 supportedMimeTypes 中），这类类型无法检查通过。</li>
     */
    private List<String> allowedMimeTypes;

    /** 最大字节数，十进制字符串。 */
    private String maxSizeBytes;

    /** 引用该策略代码的写入路由数；大于 0 时不能停用。 */
    private int routeCount;

    /**
     * 策略状态
     * <li>ACTIVE：有生效修订；DRAFT：只有草稿；RETIRED：全部修订已停用。</li>
     */
    private String status;

    /** 该代码下修订的最近更新时间（UTC）。 */
    private Instant updateTime;
}
