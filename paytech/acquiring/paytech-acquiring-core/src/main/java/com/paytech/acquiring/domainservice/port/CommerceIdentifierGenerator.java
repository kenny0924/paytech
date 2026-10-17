package com.paytech.commerce.domainservice.port;

/** Commerce's narrow boundary for application-assigned database identifiers. */
public interface CommerceIdentifierGenerator {

    long nextId();
}
