package com.paytech.platform.support.filedocument.domain.storage;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 检查策略详情：引用的写入路由与全部修订（修订表、检查要求表与路由表的组合）。 */
@Getter
@AllArgsConstructor
public class FileScanPolicyDetailView {

    /** 策略代码。 */
    private final String policyCode;

    /** 引用该代码的写入路由。 */
    private final List<RouteRef> routes;

    /** 全部修订，按修订号倒序。 */
    private final List<Revision> revisions;

    /** 引用策略的一条写入路由。 */
    @Getter
    @AllArgsConstructor
    public static class RouteRef {

        /** 来源系统或 *。 */
        private final String sourceSystem;

        /** 访问级别 Code。 */
        private final String accessLevel;

        /** 目标实例代码；目标已不存在时为空。 */
        private final String instanceCode;

        /** 目标存储桶名称；目标已不存在时为空。 */
        private final String bucketName;
    }

    /** 一个修订。 */
    @Getter
    @AllArgsConstructor
    public static class Revision {

        /** 修订号。 */
        private final int revisionNo;

        /** 修订状态。 */
        private final String status;

        /** 允许的媒体类型。 */
        private final List<String> allowedMimeTypes;

        /** 最大字节数。 */
        private final long maxSizeBytes;

        /** 检查要求。 */
        private final List<Requirement> requirements;

        /** 乐观并发版本。 */
        private final int version;

        /** 创建时间。 */
        private final Instant createTime;

        /** 最后更新时间。 */
        private final Instant updateTime;
    }

    /** 修订的一条检查要求。 */
    @Getter
    @AllArgsConstructor
    public static class Requirement {

        /** 检查器代码。 */
        private final String scannerCode;

        /** 检查类型 Code。 */
        private final String scanType;

        /** 最低检查器版本。 */
        private final String minimumScannerVersion;

        /** 视为通过的结果代码。 */
        private final List<String> acceptedResultCodes;
    }
}
