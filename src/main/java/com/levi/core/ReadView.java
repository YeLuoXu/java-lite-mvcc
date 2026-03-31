package com.levi.core;

import lombok.Data;

import java.util.Set;

/**
 * 读视图（MVCC的核心）
 * 功能与作用：
 * - 捕获创建瞬间的活跃事务集合（m_ids）、最小活跃事务 ID（min_trx_id）、全局下一事务 ID（max_trx_id）
 * - 复刻 MySQL read_view_sees_trx_id 可见性判断逻辑，用于快照读选择可见版本
 */
@Data
public class ReadView {
    /**
     * 当前事务id
     * 创建该视图的事务id
     */
    private Long curTrxId;

    /**
     * 最小事务id
     * 活跃事务中的最小事务id
     */
    private Long minTrxId;

    /**
     * 最大事务id
     * 事务管理器预分配的下一个事务id
     */
    private Long maxTrxId;

    /**
     * 活跃事务列表
     * 创建读视图时的活跃事务集合（这些事务尚未提交）
     */
    private Set<Long> activeTrxIdSet;

    /**
     * 构造方法 - 生成读视图
     * @param trxId 生成该读视图的事务id
     */
    public ReadView(Long trxId) {
        // 生成读视图的时候要加锁，暂停这一刻 （"快照"）
        // todo(重要笔记) 和写操作（修改活跃事务列表）用的是同一把锁（读写互斥）
        synchronized (TransactionManager.ACTIVE_TRX_ID_SET) {
            this.curTrxId = trxId;
            this.activeTrxIdSet = TransactionManager.getActiveTrxIdSet();
            this.maxTrxId = TransactionManager.getMaxTrxId();
            this.minTrxId = this.activeTrxIdSet.stream().min(Long::compareTo).orElse(this.maxTrxId);
        }
        /**
         * this.activeTrxIdSet = TransactionManager.getActiveTrxIdSet();
         * this.maxTrxId = TransactionManager.getMaxTrxId();
         * 主要是以上两行代码会有线程安全问题
         *
         * todo(重要笔记) 如果不加锁会有带来什么问题呢？
         * 假设在 T1.0时刻 活跃的事务id列表为[7,8]
         * 线程A开启了一个事务（事务id为9） 并在 T1.0时刻 获取到了活跃事务id列表[7,8,9]，但还未设置最大事务id
         * 线程B 在 T1.1时刻 开启了一个事务（事务id为10） 并且修改了行数据（此时并未commit）
         * 线程A 在 T1.2时刻 设置了最大事务id为11
         * 经过以上步骤，线程A 获取的活跃事务id列表[7,8,9]，最大事务id为11
         * 那么 线程A 在根据读视图去undolog版本链中读取数据的时候就会读取到 事务id为10 的版本数据，
         * 但实际上 事务id为10 的版本数据对 线程A 是不可见的，因为这条数据 线程B 还未提交
         */
    }

    /**
     * 判断是否可见
     * 根据 当前undolog版本链上的数据的事务id 来判断 当前版本的数据是否对当前读视图可见
     * @param undologChainTrxId undolog版本链上的事务id
     */
    public Boolean isVisible(Long undologChainTrxId) {
        // 小于最小事务id，说明当前undolog版本链上的数据是在当前事务创建之前就已经提交了，所以可见
        if (undologChainTrxId < minTrxId) {
            return true;
        }

        // 大于等于最大事务id，说明当前undolog版本链上的数据是在当前事务创建之后修改的，所以不可见
        if (undologChainTrxId >= maxTrxId) {
            return false;
        }

        // 等于当前事务id，说明当前undolog版本链上的数据是当前事务创建的（修改的），所以可见
        if (undologChainTrxId.equals(curTrxId)) {
            return true;
        }

        // 在活跃事务列表中，说明当前undolog版本链上的数据在当前事务创建时还未提交，所以不可见
        return !activeTrxIdSet.contains(undologChainTrxId);
    }
}
