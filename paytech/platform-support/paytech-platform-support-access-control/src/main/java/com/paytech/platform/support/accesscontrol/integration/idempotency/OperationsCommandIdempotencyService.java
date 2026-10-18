package com.paytech.platform.support.accesscontrol.integration.idempotency;

import com.paytech.platform.support.accesscontrol.domain.operations.CommandIdempotencyContext;

/** Operations 管理命令共享的调用方隔离、载荷哈希、回放与完成能力。 */
public interface OperationsCommandIdempotencyService {

    /** 在业务事务外准备调用方隔离与 HMAC，返回供原事务查询和保存结果的窄端口。 */
    CommandIdempotencyContext prepare(String actorId, String operation, String key, String requestHashNamespace,
        Object canonicalRequest, String opaqueReferenceNamespace);
}
