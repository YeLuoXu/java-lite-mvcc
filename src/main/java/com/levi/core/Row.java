package com.levi.core;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 行数据类
 * 模拟 MySQL InnoDB 中的一行数据结构
 * 功能与作用：
 * - 封装业务列数据与 MVCC 相关的隐藏列（最近修改事务id、回滚指针）
 * - 通过 rollPointer 形成 Undo Log 版本链，用于快照读时的版本回溯
 * - 提供行级锁以模拟写操作互斥（简化 MySQL 行锁行为）
 */
@Data
public class Row {
    /**
     * 主键
     */
    private Long id;

    /**
     * 业务数据列集合 {"name":"zhangsan", "age":23 ...}
     * 不包含主键列
     */
    private Map<String, Object> columnData;

    /**
     * 最近修改这行数据的事务id
     */
    private Long trxId;

    /**
     * 回滚指针
     * 指向这行数据的 "上一个版本"
     */
    private Row rollPointer;

    /**
     * 行锁
     * Mysql写操作需要互斥执行
     * 我们用 ReentrantLock 模拟行锁
     */
    private final Lock lock = new ReentrantLock();

    /**
     * 逻辑删除标记
     * todo(笔记) 为什么不能将将boolean类型的变量命名为 isDeleted
     * 1.boolean 类型的 getter 方法：
     *  当字段名为 isDeleted 时，@Data 注解生成的 getter 方法仍然是 isDeleted()
     *  这会导致方法名与字段名重复，造成语义混乱
     * 2.boolean 类型的 setter 方法：
     *  当字段名为 isDeleted 时，@Data 注解生成的 setter 方法是 setDeleted()
     *  这样会产生不一致的命名规范
     * 扩展：Boolean 类型的 getter 方法
     *  当字段名为 isDeleted 且类型为 Boolean（包装类型）时
     *  生成的 getter 方法为 getIsDeleted()
     *  生成的 setter 方法为 setIsDeleted()
     *  可见Boolean 类型不会有任何影响
     */
    private boolean deleted;

    public Row(Long id) {
        this.id = id;
        this.columnData = new HashMap<>();
        this.trxId = 0L;  // 0表示由系统初始化，无事务
        this.rollPointer = null;
        this.deleted = false;
    }
}
