package com.paytech.foundation.id;

/**
 * Generates identifiers for locally owned business records.
 *
 * @author Codex
 */
@FunctionalInterface
public interface IdGenerator {

    /**
     * Generates the next unique identifier.
     *
     * @return next identifier
     */
    long nextId();
}
