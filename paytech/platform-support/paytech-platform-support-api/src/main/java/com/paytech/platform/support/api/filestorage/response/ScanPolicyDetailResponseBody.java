package com.paytech.platform.support.api.filestorage.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 检查策略详情：引用它的写入路由与全部修订。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ScanPolicyDetailResponseBody {

    /** 策略代码。 */
    private String policyCode;

    /** 引用该策略代码的写入路由。 */
    private List<ScanPolicyRouteRef> routes;

    /** 全部修订，按修订号倒序。 */
    private List<ScanPolicyRevision> revisions;
}
