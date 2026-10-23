package com.paytech.platform.support.filedocument.domain.storage;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 检查策略列表的一行：一个策略代码的生效与草稿修订、展示内容与引用路由数。 */
@Getter
@AllArgsConstructor
public class FileScanPolicyView {

    /** 策略代码。 */
    private final String policyCode;

    /** ACTIVE 修订号；没有为空。 */
    private final Integer activeRevisionNo;

    /** DRAFT 修订号；没有为空。 */
    private final Integer draftRevisionNo;

    /** 展示内容的允许类型：取 ACTIVE，没有 ACTIVE 时取 DRAFT，都没有时取最新修订。 */
    private final List<String> allowedMimeTypes;

    /** 展示内容的最大字节数。 */
    private final long maxSizeBytes;

    /** 引用该代码的写入路由数。 */
    private final int routeCount;

    /** 策略状态：ACTIVE、DRAFT 或 RETIRED。 */
    private final String status;

    /** 该代码下修订的最近更新时间。 */
    private final Instant updateTime;
}
