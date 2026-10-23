package com.paytech.platform.support.api.filestorage.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 检查策略列表：每个策略代码一行，附格式检查器能识别的类型。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ScanPolicyListResponseBody {

    /** 全部策略代码，按代码升序。 */
    private List<ScanPolicySummary> items;

    /**
     * 格式检查器能识别的规范 MIME
     * <li>修订的允许类型只能从中选择；当前为 application/pdf、image/png、image/jpeg。</li>
     */
    private List<String> supportedMimeTypes;
}
