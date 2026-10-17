package com.paytech.platform.support.core.reliability.repository.impl;

import java.time.Instant;

import org.springframework.stereotype.Repository;

import com.paytech.platform.support.core.reliability.mapper.CommandIdempotencyMapper;
import com.paytech.platform.support.core.reliability.repository.CommandIdempotencyRepository;

import lombok.AllArgsConstructor;

/** Platform Support 服务级命令幂等事实访问实现。 */
@Repository
@AllArgsConstructor
public class CommandIdempotencyRepositoryImpl implements CommandIdempotencyRepository {
    private final CommandIdempotencyMapper mapper;

    @Override
    public String previousCommand(String scope, String operation, String key) {
        return mapper.previousCommand(scope, operation, key);
    }

    @Override
    public void completeCommand(long id, String scope, String operation, String key, String hash, String responseRef,
        Instant now) {
        mapper.completeCommand(id, scope, operation, key, hash, responseRef, now);
    }
}
