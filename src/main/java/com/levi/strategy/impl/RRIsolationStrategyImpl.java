package com.levi.strategy.impl;

import com.levi.core.ReadView;
import com.levi.strategy.IsolationStrategy;
import lombok.AllArgsConstructor;

/**
 * RR隔离级别策略
 * 特点：
 * - 复用当前事务第一次 SELECT 时生成的 ReadView 快照，后续查询复用该快照
 * - 当事务id 变化（新事务）时，生成并缓存新的快照
 */
public class RRIsolationStrategyImpl implements IsolationStrategy {
    /**
     * 读视图缓存
     * 每个事务对应有自己的ReadView，所以采用ThreadLocal
     */
    private static final ThreadLocal<ReadViewContext> READ_VIEW_CACHE = new ThreadLocal<>();

    /**
     * RR隔离级别下，只有第一次查数据时会生成读视图
     */
    @Override
    public ReadView getReadView(Long trxId) {
        // 去本地缓存中查询，如果有则直接返回
        ReadViewContext readViewContext = READ_VIEW_CACHE.get();
        if (readViewContext != null && readViewContext.trxId.equals(trxId)) {
            return readViewContext.readView;
        }

        // 如果没有，生成读视图
        ReadView readView = new ReadView(trxId);

        // 将读视图缓存到本地缓存中
        readViewContext = new ReadViewContext(trxId, readView);
        READ_VIEW_CACHE.set(readViewContext);

        return readView;
    }

    /**
     * 读视图内部类
     * todo(笔记) 为什么用 static 和 final修饰？
     * 一般这种内部类都可以考虑用静态的，因为静态的话相当于跟外部类没有引用关系
     * 这里的 ReadViewContext 是要放到 ThreadLocal 里面的，故减少和外部类的引用关系可以减少一定程度上的内存泄露概率
     *
     * final就是单纯的不希望这个类被继承+更改，包括字段也是，因为快照这个东西本来就是创建之后不会变的
     */
    @AllArgsConstructor
    private static final class ReadViewContext {
        final Long trxId;
        final ReadView readView;
    }
}
