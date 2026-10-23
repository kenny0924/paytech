package com.paytech.platform.support.api.filestorage.response;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 引用检查策略的一条写入路由。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ScanPolicyRouteRef {

    /** 来源系统，或 * 表示默认路由。 */
    private String sourceSystem;

    /** 访问级别：PRIVATE 或 PUBLIC。 */
    private String accessLevel;

    /** 目标实例代码；目标已不存在时为空。 */
    private String instanceCode;

    /** 目标存储桶名称；目标已不存在时为空。 */
    private String bucketName;
}
