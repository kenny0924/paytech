package com.paytech.commercial.api.pricing.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 定价画像列表（运营选择用；画像目录规模有限，不分页）。 */
@Getter
@Setter
public class PricingProfileListResponseBody {
    /** 按画像 ID 升序 */
    private List<PricingProfileResponseBody> profiles;
}
