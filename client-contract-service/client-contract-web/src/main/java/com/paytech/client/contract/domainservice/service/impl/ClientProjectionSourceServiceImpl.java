package com.paytech.client.contract.domainservice.service.impl;
import com.paytech.client.contract.domain.onboarding.ClientProjectionModels.Source;
import com.paytech.client.contract.domainservice.ClientProjectionSnapshot;
import com.paytech.client.contract.domainservice.repository.ClientProjectionRepository;
import com.paytech.client.contract.domainservice.service.ClientProjectionSourceService;
import org.springframework.transaction.support.TransactionTemplate;
/** 读取结束即释放本地事务，调用者之后才可访问 Platform。 */
public final class ClientProjectionSourceServiceImpl implements ClientProjectionSourceService {
    private final ClientProjectionRepository repository;
    private final TransactionTemplate reads;
    public ClientProjectionSourceServiceImpl(ClientProjectionRepository repository, TransactionTemplate reads) {
        this.repository = repository; this.reads = reads;
    }
    @Override public Source source(long id) { return reads.execute(tx -> validate(repository.source(id))); }
    @Override public Source sourceAt(long clientId, int version) {
        return reads.execute(tx -> validate(repository.sourceAt(clientId, version)));
    }
    private Source validate(com.paytech.client.contract.core.dal.dataobject.ClientProjectionHistory row) {
        Source source = ClientProjectionSnapshot.read(row);
        if (source.sourceVersion() > 1) {
            Source first = ClientProjectionSnapshot.read(repository.sourceAt(source.clientId(), 1));
            if (first.applicantId() != source.applicantId()) { throw new IllegalArgumentException("Invalid original Client applicant"); }
        }
        return source;
    }
}
