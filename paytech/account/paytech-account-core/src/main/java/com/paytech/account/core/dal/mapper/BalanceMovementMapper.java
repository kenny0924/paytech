package com.paytech.account.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.account.api.enums.MovementSourceType;
import com.paytech.account.core.dal.dataobject.BalanceMovement;

/** t_balance_movement：余额桶移动命令记录。 */
@Mapper
public interface BalanceMovementMapper {

    /** 插入一行。 */
    int insert(BalanceMovement record);

    /** 按来源读取，不存在返回空。 */
    BalanceMovement selectBySource(@Param("sourceType") MovementSourceType sourceType,
        @Param("sourceRef") String sourceRef);

    /** 按主键读取一组移动记录。 */
    List<BalanceMovement> selectByIds(@Param("ids") List<Long> ids);
}
