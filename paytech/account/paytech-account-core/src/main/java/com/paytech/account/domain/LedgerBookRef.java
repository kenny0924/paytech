package com.paytech.account.domain;

/**
 * 经 ledger-api 读取的账簿身份
 *
 * @param ledgerBookId ledger 账簿主键
 * @param identity 账簿主体身份
 */
public record LedgerBookRef(long ledgerBookId, BookIdentity identity) {
}
