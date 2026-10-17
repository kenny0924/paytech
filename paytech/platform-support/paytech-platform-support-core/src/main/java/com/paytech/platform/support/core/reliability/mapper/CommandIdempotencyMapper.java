package com.paytech.platform.support.core.reliability.mapper;

import java.time.Instant;

import org.apache.ibatis.annotations.Param;

/** Platform Support 服务级命令幂等事实的唯一 Mapper。 */
public interface CommandIdempotencyMapper {

    String previousCommand(@Param("scope") String scope, @Param("operation") String operation,
        @Param("key") String key);

    void completeCommand(@Param("id") long id, @Param("scope") String scope,
        @Param("operation") String operation, @Param("key") String key, @Param("hash") String hash,
        @Param("responseRef") String responseRef, @Param("now") Instant now);
}
