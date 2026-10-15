package com.paytech.finance.accounting.service.processor.response;

import lombok.Getter;
import lombok.Setter;

/** 创建类命令的结果。 */
@Getter
@Setter
public class CreatedResourceResponse {
    /** 新资源主键 */
    private Long id;
}
