package com.paytech.platform.support.api.notificationmanagement.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 配置写入结果。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationMutationResponseBody {

    /** 对象编码：模板编码、语言或账号编码。 */
    private String code;

    /** 写入后的版本。 */
    private int version;
}
