package com.paytech.platform.support.api.clientapicredential.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 本工作区凭证列表。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ClientApiCredentialsResponseBody {

    /** 按创建时间倒序的凭证摘要。 */
    private List<ClientApiCredentialResponseBody> items;
}
