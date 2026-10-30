package com.paytech.operations.console.bff.service.model.risklimittier;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 上线复核清单的一个系统项。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LimitTierCheckItemBody {
    /** 系统项代码：QUALIFICATION、UNDERWRITING、AGREEMENT、CAPABILITIES。 */
    private String code;

    /** 核对结果：OK、NOT_OK、UNAVAILABLE。 */
    private String status;

    /** 来源系统：compliance、risk、commercial。 */
    private String source;
}
