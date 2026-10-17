package com.paytech.platform.support.accesscontrol.domain.operations;

import java.time.Instant;

/** 业务命令可使用的窄幂等上下文；持久化、哈希和冲突转换由 Web 可靠性实现。 */
public interface CommandIdempotencyContext {

    String previous();

    void complete(String responseReference, Instant now);

    String opaqueReference();
}
