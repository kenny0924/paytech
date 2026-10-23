package com.paytech.platform.support.api.filestorage.response;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 存储实例下的一个存储桶。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StorageBucket {

    /** 存储桶名称，即文件记录上的存储容器。 */
    private String bucketName;

    /**
     * 访问级别
     * <li>PRIVATE 或 PUBLIC；登记后不可修改，写入路由只能指向同级别的桶。</li>
     */
    private String accessLevel;

    /**
     * 对外基础地址
     * <li>PUBLIC 桶的 HTTPS 基础地址（CDN 或桶公开地址，不以 / 结尾）；PRIVATE 桶为空。公开地址 = 基础地址 + / + 对象名。</li>
     */
    private String publicBaseUrl;

    /**
     * 状态
     * <li>{@code FileStorageBucketStatus} Code：DRAFT、ACTIVE、READ_ONLY、DISABLED。</li>
     */
    private String status;

    /** 最近一次启用检查时间（UTC）；从未检查为空。 */
    private Instant lastCheckTime;

    /**
     * 最近一次启用检查结果
     * <li>{@code FileStorageCheckResult} Code；从未检查为空。启用要求 10 分钟内的 OK。</li>
     */
    private String lastCheckResult;

    /** 指向该桶的写入路由数；大于 0 时不能设为只读或停用。 */
    private int routeCount;

    /** 乐观并发版本。 */
    private int version;

    /** 最后更新时间（UTC）。 */
    private Instant updateTime;
}
