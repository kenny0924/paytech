package com.paytech.client.master.domainservice.repository;
import java.time.Instant;
import java.util.List;
import com.paytech.client.master.core.dal.dataobject.ClientApplicationDocument;
/** CCT 材料引用持久化边界。 */
public interface ClientDocumentRepository {
    /** 当前申请材料引用。 */
    List<ClientApplicationDocument> documents(long applicationId);
    /** 精确原引用。 */
    ClientApplicationDocument document(long applicationId,long id);
    /** 新增经验证引用。 */
    void insert(long applicationId,ClientApplicationDocument document,Instant now);
    /** 删除准确原引用。 */
    int remove(long applicationId,long id);
}
