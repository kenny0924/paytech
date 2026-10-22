package com.paytech.card.domainservice.repository;

import java.util.List;

import com.paytech.card.core.dal.dataobject.CardBinLibraryActivation;
import com.paytech.card.domain.binlibrary.BinLibraryActivationView;

/** BIN 库激活记录数据访问 */
public interface CardBinLibraryActivationRepository {
    /** 追加一条激活记录。 */
    void insertActivation(CardBinLibraryActivation activation);

    /** 激活记录总数。 */
    long countActivations();

    /** 激活历史分页，按激活时间倒序。 */
    List<BinLibraryActivationView> listActivations(int offset, int limit);
}
