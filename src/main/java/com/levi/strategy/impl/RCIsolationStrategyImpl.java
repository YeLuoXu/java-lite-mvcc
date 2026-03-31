package com.levi.strategy.impl;

import com.levi.core.ReadView;
import com.levi.strategy.IsolationStrategy;

/**
 * RC隔离级别策略
 */
public class RCIsolationStrategyImpl implements IsolationStrategy {
    /**
     * RC隔离级别下，每次都生成最新的读视图
     * @param trxId 当前事务id
     * @return 新的读视图
     */
    @Override
    public ReadView getReadView(Long trxId) {
        return new ReadView(trxId);
    }
}
