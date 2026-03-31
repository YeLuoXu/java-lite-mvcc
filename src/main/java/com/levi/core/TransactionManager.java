package com.levi.core;

import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 全局事务管理器
 * 功能与作用：
 * - 负责为每次开启的事务生成唯一事务id（模拟 MySQL trx_sys->max_trx_id）
 * - 维护活跃事务集合（已开启但未提交/回滚），用于 ReadView 可见性判断
 * - 使用 ThreadLocal 绑定当前线程持有的事务id，模拟会话维度的事务上下文
 */
@Slf4j
public class TransactionManager {
    /**
     * 事务id生成器（原子类，用来生成全局唯一、自增的事务id）
     * 从 1 开始自增
     */
    public static final AtomicLong TRX_ID_GENERATOR = new AtomicLong(1L);

    /**
     * todo(笔记) 也可以通过（自定义一个最大事务id变量 + 锁）来实现全局唯一、自增的事务id
     * 效果和通过原子类来实现事务id生成器一样
     */
/*    public static Long maxTrxId = 1L;
    public static Long generateTrxId() {
        synchronized (ACTIVE_TRX_ID_SET) {
            return maxTrxId++;
        }
    }*/

    /**
     * 当前线程持有的事务id（模拟当前会话事务上下文）
     */
    public static final ThreadLocal<Long> THREAD_TRX_ID = new ThreadLocal<>();

    /**
     * 活跃事务列表
     * 存放当前所有 "已开始但未提交/回滚" 的事务id
     * 需要满足3个特点：1.查询快 2.并发安全 3.唯一性 => ConcurrentHashMap -> HashSet -> 并发安全HashSet
     */
    public static final Set<Long> ACTIVE_TRX_ID_SET = Collections.synchronizedSet(new HashSet<>());

    /**
     * 开启事务（BEGIN）
     * @return 新分配的事务id
     */
    public static long begin() {
        // 自增生成新的事务id
        long trxId = TRX_ID_GENERATOR.getAndIncrement();
        THREAD_TRX_ID.set(trxId);  // 绑定到当前线程

        // todo(重要笔记) 和读操作（生成ReadView）用的是同一把锁（读写互斥）
        synchronized (ACTIVE_TRX_ID_SET) {
            ACTIVE_TRX_ID_SET.add(trxId);  // 添加到活跃事务列表
        }

        log.info("开启事务：{}", trxId);
        System.out.println("开启事务："+ trxId);

        return trxId;
    }

    /**
     * 提交事务（COMMIT）
     */
    public static void commit() {
        Long trxId = THREAD_TRX_ID.get();
        if (trxId == null) {
            throw new RuntimeException("trxId is null");
        }
        synchronized (ACTIVE_TRX_ID_SET) {
            ACTIVE_TRX_ID_SET.remove(trxId);  // 事务提交后，对应的事务id从活跃事务列表中删除
        }
        THREAD_TRX_ID.remove();  // 清除threadLocal对象，防止内存泄漏

        log.info("提交事务：{}", trxId);
        System.out.println("提交事务："+ trxId);
    }

    /**
     * 回滚事务（ROLLBACK）
     */
    public static void rollback() {
        Long trxId = THREAD_TRX_ID.get();
        if (trxId == null) {
            throw new RuntimeException("trxId is null");
        }
        synchronized (ACTIVE_TRX_ID_SET) {
            ACTIVE_TRX_ID_SET.remove(trxId);  // 事务回滚后，对应的事务id从活跃事务列表中删除
        }
        THREAD_TRX_ID.remove();  // 清除threadLocal对象，防止内存泄漏

        log.info("回滚事务：{}", trxId);
        System.out.println("回滚事务："+ trxId);
    }

    /**
     * 获取活跃事务列表
     * 必须返回副本，防止并发修改
     */
    public static Set<Long> getActiveTrxIdSet() {
        /*
        todo(笔记) 返回对象引用的副本，避免线程安全问题
        因为如果直接返回对象引用，其他线程拿到后，
        如果修改了数据会影响到全局事务管理器的活跃事务列表
         */
        return new HashSet<>(ACTIVE_TRX_ID_SET);
    }

    /**
     * 获取最大事务id（下一个将要分配的事务id）
     * @return 最大事务id
     */
    public static Long getMaxTrxId() {
        return TRX_ID_GENERATOR.get();
    }

    /**
     * 获取当前线程持有的事务id
     * @return 事务id
     */
    public static Long getCurTrxId() {
        Long trxId = THREAD_TRX_ID.get();
        return trxId == null ? 0L : trxId;  // 若未开启事务则返回0
    }
}
