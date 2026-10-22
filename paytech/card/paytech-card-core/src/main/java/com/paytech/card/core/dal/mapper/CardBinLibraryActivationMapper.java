package com.paytech.card.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.card.core.dal.dataobject.CardBinLibraryActivation;
import com.paytech.card.domain.binlibrary.BinLibraryActivationView;

/** BIN 库激活记录 SQL 映射 */
@Mapper
public interface CardBinLibraryActivationMapper {
    /** 追加一条激活记录。 */
    int insertActivation(CardBinLibraryActivation activation);

    /** 激活记录总数。 */
    long countActivations();

    /** 激活历史分页，按激活时间倒序，关联本次与被替换版本的版本号。 */
    List<BinLibraryActivationView> listActivations(@Param("offset") int offset, @Param("limit") int limit);
}
