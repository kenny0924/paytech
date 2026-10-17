package com.paytech.platform.support.integration.domain;

import java.util.List;

import com.paytech.platform.support.integration.core.dal.dataobject.ApiField;
import com.paytech.platform.support.integration.core.dal.dataobject.StandardApi;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 新建标准 API 的完整保存内容；标识与初始版本已在插入前准备。 */
@Getter
@AllArgsConstructor
public class CreateStandardApiContext {

    /** 待插入的标准 API。 */
    private final StandardApi standardApi;

    /** 请求与响应字段，方向与顺序已确定。 */
    private final List<ApiField> fields;
}
