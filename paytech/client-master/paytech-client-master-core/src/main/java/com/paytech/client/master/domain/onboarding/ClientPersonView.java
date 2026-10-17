package com.paytech.client.master.domain.onboarding;
import com.paytech.client.master.core.dal.dataobject.*;
public record ClientPersonView(ClientPersonIdentity identity,ClientDraftFields fields) {
    @Override public String toString() { return "ClientPersonView[redacted]"; }
}
