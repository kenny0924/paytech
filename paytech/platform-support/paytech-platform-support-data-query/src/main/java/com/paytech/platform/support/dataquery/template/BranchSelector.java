package com.paytech.platform.support.dataquery.template;

/** 条件块走向的选择钩子；执行时按条件求值，保存校验用它记录与强制分支。 */
interface BranchSelector {

    /** 按条件求值的结果走。 */
    BranchSelector NATURAL = (node, natural) -> natural;

    /**
     * 选择条件块的走向。
     *
     * @param node 条件块
     * @param natural 按条件求值得到的走向：分支下标，或 branches.size() 表示都不成立
     * @return 实际走向
     */
    int select(IfNode node, int natural);
}
