package com.levi.strategy;

import com.levi.core.ReadView;

/**
 * 事务隔离级别策略接口
 */
public interface IsolationStrategy {
    /**
     * 获取读视图
     * @param trxId 当前事务id
     * @return 读视图
     */
    ReadView getReadView(Long trxId);
}
