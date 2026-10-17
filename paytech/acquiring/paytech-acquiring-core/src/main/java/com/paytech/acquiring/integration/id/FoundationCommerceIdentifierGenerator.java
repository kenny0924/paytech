package com.paytech.acquiring.integration.id;

import com.paytech.acquiring.domainservice.port.CommerceIdentifierGenerator;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.id.IdGenerator;
import lombok.AllArgsConstructor;

/** Keeps the Foundation Snowflake implementation outside Commerce's domain port. */
@AllArgsConstructor
public final class FoundationCommerceIdentifierGenerator implements CommerceIdentifierGenerator {

    private final IdGenerator delegate;

    @Override
    public long nextId() {
        long id = delegate.nextId();
        if (id <= 0) {
            throw new ErrorException("Foundation ID 生成器返回了非正值");
        }
        return id;
    }
}
