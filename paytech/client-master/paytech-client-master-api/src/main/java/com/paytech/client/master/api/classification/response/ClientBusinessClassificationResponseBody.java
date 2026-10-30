package com.paytech.client.master.api.classification.response;

import lombok.Getter;
import lombok.Setter;

/** Client 已确认的主经营分类。 */
@Getter
@Setter
public class ClientBusinessClassificationResponseBody {
    /** Client ID，十进制字符串。 */
    private String clientId;

    /** 经营资料的分类已由运营确认（确认时间非空）；没有经营资料为 false。 */
    private boolean confirmed;

    /**
     * 主分类（primary_slot=1）的行业代码
     * <li>未确认或没有主分类时为空。</li>
     */
    private String industryCode;

    /**
     * 主分类的四位 MCC
     * <li>未确认或没有主分类时为空。</li>
     */
    private String mccCode;
}
