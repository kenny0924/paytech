package com.paytech.acquiring.domainservice.port;

/** Commerce's narrow boundary for application-assigned database identifiers. */
public interface CommerceIdentifierGenerator {

    long nextId();
}
