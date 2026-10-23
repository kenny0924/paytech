package com.paytech.platform.support.api.localization.response;

import lombok.Getter;
import lombok.Setter;

/** 发布语言包的文件检查进度；读取详情时按文件服务当前状态计算。 */
@Getter
@Setter
public class BundleCheckProgress {

    /** 语言包总数。 */
    private int total;

    /** 检查通过且可用的数量。 */
    private int available;

    /** 仍在检查的数量。 */
    private int pending;

    /** 检查拒绝、隔离、对象不可用或文件不存在的数量。 */
    private int rejected;
}
