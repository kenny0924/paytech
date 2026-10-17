package com.paytech.platform.support.accesscontrol.domain.clientdeveloper;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ClientApiCredential;

/** 凭证查询结果与推导状态所用的同一时刻。 */
public record ClientApiCredentialQueryResult(List<ClientApiCredential> credentials, Instant asOf) {}
