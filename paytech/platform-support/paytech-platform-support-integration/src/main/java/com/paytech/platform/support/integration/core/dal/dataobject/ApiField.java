package com.paytech.platform.support.integration.core.dal.dataobject;

import com.paytech.platform.support.api.enums.ApiFieldDataType;
import com.paytech.platform.support.integration.core.enums.ApiFieldDirection;
import com.paytech.platform.support.api.enums.ApiFieldLocation;
import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 标准 API 与渠道原子 API 共用的请求、响应字段定义，随所属 API 整组替换。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ApiField {

    /** 字段定义内部技术标识。 */
    private Long apiFieldId;

    /** 所属 API 标识：方向为 STANDARD_API_* 时为标准 API，为 INTEGRATION_API_* 时为渠道原子 API。 */
    private Long apiId;

    /** 字段所属 API 类型与请求或响应方向。 */
    private ApiFieldDirection direction;

    /** 字段在 HTTP 报文中的位置。 */
    private ApiFieldLocation fieldLocation;

    /** BODY 为完整点分路径，数组元素用 []；其他位置为参数名。 */
    private String fieldPath;

    /** 页面显示名称。 */
    private String fieldName;

    /** 字段值数据类型。 */
    private ApiFieldDataType dataType;

    /** 是否必填；子字段在父对象存在时校验，响应字段在 SUCCESS 时校验。 */
    private boolean requiredFlag;

    /** 同一 API、同一方向内的显示顺序。 */
    private int sortNo;

    /** 业务说明，未填写时为空。 */
    private String description;

    /** UTC 微秒创建时间。 */
    private Instant createTime;

    /** UTC 微秒更新时间。 */
    private Instant updateTime;
}
