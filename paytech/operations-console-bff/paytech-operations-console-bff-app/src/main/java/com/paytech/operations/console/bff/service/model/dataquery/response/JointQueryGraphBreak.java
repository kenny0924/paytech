package com.paytech.operations.console.bff.service.model.dataquery.response;

import lombok.Getter;
import lombok.Setter;

/** 链路断点：可以到达但没有任何参与模板以它为触发键的键类型。 */
@Getter
@Setter
public class JointQueryGraphBreak {

    /** 键类型编码。 */
    private String keyType;

    /** 中文原因。 */
    private String reason;
}
