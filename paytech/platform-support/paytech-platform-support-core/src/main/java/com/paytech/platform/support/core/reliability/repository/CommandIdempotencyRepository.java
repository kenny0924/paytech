package com.paytech.platform.support.core.reliability.repository;

import java.time.Instant;

/** Platform Support 服务级命令幂等事实访问接口。 */
public interface CommandIdempotencyRepository {

    String previousCommand(String scope, String operation, String key);

    void completeCommand(long id, String scope, String operation, String key, String hash, String responseRef,
        Instant now);
}
