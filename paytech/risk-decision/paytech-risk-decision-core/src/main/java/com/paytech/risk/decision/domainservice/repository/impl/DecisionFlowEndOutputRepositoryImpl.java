package com.paytech.risk.decision.domainservice.repository.impl;

import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.decision.core.dal.dataobject.DecisionFlowEndOutput;
import com.paytech.risk.decision.core.dal.mapper.DecisionFlowEndOutputMapper;
import com.paytech.risk.decision.domainservice.repository.DecisionFlowEndOutputRepository;

import lombok.AllArgsConstructor;

/** 结束节点附加输出持久化；唯一键是资源锁失效后的最终防线。 */
@Repository
@AllArgsConstructor
public class DecisionFlowEndOutputRepositoryImpl implements DecisionFlowEndOutputRepository {
    private final DecisionFlowEndOutputMapper mapper;

    /** 插入一项输出；唯一键冲突视为并发保存。 */
    @Override
    public void insert(DecisionFlowEndOutput row) {
        try {
            if (mapper.insert(row) != 1) {
                throw new ErrorException("流程输出写入行数不一致");
            }
        } catch (DuplicateKeyException failure) {
            throw new ConcurrentRequestException("流程配置被并发更新，请刷新后重试", failure);
        }
    }

    /** 读取准确流程版本的全部输出。 */
    @Override
    public List<DecisionFlowEndOutput> findByVersion(long flowVersionId) {
        return List.copyOf(mapper.selectByVersion(flowVersionId));
    }
}
