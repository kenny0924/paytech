package com.paytech.client.master.domainservice.repository.impl;
import java.time.Instant;
import java.util.List;
import com.paytech.client.master.core.dal.dataobject.ClientApplicationDocument;
import com.paytech.client.master.core.dal.mapper.ClientDocumentMapper;
import com.paytech.client.master.domainservice.repository.ClientDocumentRepository;
/** 所有 SQL 位于本 Owner Mapper XML，不跨库读取。 */
public final class ClientDocumentRepositoryImpl implements ClientDocumentRepository {
    private final ClientDocumentMapper mapper;
    /** 注入同库 Mapper。 */
    public ClientDocumentRepositoryImpl(ClientDocumentMapper mapper) { this.mapper=mapper; }
    /** 查询当前引用。 */
    @Override public List<ClientApplicationDocument> documents(long applicationId) { return mapper.documents(applicationId); }
    /** 查询准确原引用。 */
    @Override public ClientApplicationDocument document(long applicationId,long id) { return mapper.document(applicationId,id); }
    /** 保留材料读取范围。 */
    @Override public boolean retainedSubject(long applicationId,long partyId) { return mapper.retainedSubject(applicationId,partyId); }
    /** 保存准确文件摘要。 */
    @Override public void insert(long applicationId,ClientApplicationDocument document,Instant now) { mapper.insert(applicationId,document,now); }
    /** 只删除引用。 */
    @Override public int remove(long applicationId,long id) { return mapper.remove(applicationId,id); }
}
