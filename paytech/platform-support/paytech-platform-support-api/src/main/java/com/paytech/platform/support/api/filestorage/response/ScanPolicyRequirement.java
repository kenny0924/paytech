package com.paytech.platform.support.api.filestorage.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 检查策略修订的一条检查要求。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ScanPolicyRequirement {

    /** 检查器代码，例如 PLATFORM_FORMAT。 */
    private String scannerCode;

    /** 检查类型，例如 FORMAT。 */
    private String scanType;

    /** 要求的最低检查器版本。 */
    private String minimumScannerVersion;

    /** 视为通过的结果代码。 */
    private List<String> acceptedResultCodes;
}
