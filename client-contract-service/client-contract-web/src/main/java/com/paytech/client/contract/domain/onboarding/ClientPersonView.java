package com.paytech.client.contract.domain.onboarding;
import com.paytech.client.contract.core.dal.dataobject.*;
public record ClientPersonView(ClientPersonIdentity identity,ClientDraftFields fields) {
    @Override public String toString() { return "ClientPersonView[redacted]"; }
}
