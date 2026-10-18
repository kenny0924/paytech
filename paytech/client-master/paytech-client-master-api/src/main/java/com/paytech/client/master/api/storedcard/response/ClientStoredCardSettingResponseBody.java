package com.paytech.client.master.api.storedcard.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Client 级已存卡开关。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ClientStoredCardSettingResponseBody {
    /** Client ID。 */
    private String clientId;

    /** 是否开通存卡与已存卡支付；没有记录为 false。 */
    private boolean enabled;
}
