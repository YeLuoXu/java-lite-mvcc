package com.levi.engine;

import com.levi.core.ReadView;
import com.levi.core.Row;
import com.levi.core.TransactionManager;
import com.levi.strategy.IsolationStrategy;
import com.levi.strategy.impl.RRIsolationStrategyImpl;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简易数据库引擎（内存版）
 * 功能与作用：
 * - 提供基础的 INSERT/SELECT/UPDATE/DELETE 能力
 * - 写入操作自动构建 Undo Log 版本链（rollPointer）以支持 MVCC 快照读
 * - 通过策略模式切换 RC 与 RR 的读视图生成策略
 */
public class MiniEngine {
    /**
     * 默认采用RR隔离级别
     */
    private IsolationStrategy isolationStrategy = new RRIsolationStrategyImpl();

    /**
     * 模拟mysql表
     * 主键id -> Row对象
     */
    private final Map<Long, Row> table = new ConcurrentHashMap<>();

    /**
     * todo(笔记) 动态设置隔离级别（采用的是策略模式）
     */
    public void setIsolationStrategy(IsolationStrategy isolationStrategy) {
        if (isolationStrategy == null) {
            throw new RuntimeException("isolation strategy can not be null");
        }
        this.isolationStrategy = isolationStrategy;
    }

    /**
     * 插入行数据（INSERT）
     * @param id 主键id
     * @param columnData 业务数据
     */
    public void insert(Long id, Map<String, Object> columnData) {
        if (id == null || columnData == null) {
            throw new RuntimeException("id and columnData can not be null");
        }
        if (table.get(id) != null) {
            throw new RuntimeException("id already exists");
        }
        Row row = new Row(id);

        try {
            row.getLock().lock();  // insert操作时加行锁
            row.setColumnData(new HashMap<>(columnData));  // todo(笔记) 深拷贝以免外部修改影响存储
            row.setTrxId(TransactionManager.getCurTrxId());
            table.put(id, row);  // 新增行数据到表中
        } finally {
            row.getLock().unlock();  // 解除行锁
        }
    }

    /**
     * 查询数据 (SELECT)
     * @param id 主键id
     * @return 可见版本的业务数据（深拷贝）
     * 核心读逻辑（快照读）
     */
    public Map<String, Object> select(Long id) {
        Row curRow = table.get(id);
        if (curRow == null) {
            return null;
        }
        Long curTrxId = TransactionManager.getCurTrxId();
        // 获取读视图
        ReadView readView = isolationStrategy.getReadView(curTrxId);

        // 根据读视图去行数据版本链（undolog版本链）中获取对应的数据
        Row cur = curRow;  // todo(笔记) 注意：这里必须得新建一个指针指向当前行数据，而不能直接拿当前行数据作为指针，不然下次就找不到当前行数据了
        while (cur != null) {
            if (readView.isVisible(cur.getTrxId())) {  // 找到了对应版本的数据
                if (cur.isDeleted()) {  // 判断是否被删除
                    return null;
                }
                return new HashMap<>(cur.getColumnData());  // todo(笔记) 这里返回的也是业务数据的副本，防止其他地方将业务数据给修改了，影响到这里的业务数据
            }
            cur = cur.getRollPointer();  // 没找到，继续判断下一个版本
        }

        return null;
    }

    /**
     * 更新行数据（UPDATE）
     * @param id 主键id
     * @param columnData 新的业务数据（部分更新）
     * 核心写逻辑
     * 构建 undolog版本链
     */
    public void update(Long id, Map<String, Object> columnData) {
        if (id == null || columnData == null) {
            throw new RuntimeException("id and columnData can not be null");
        }
        Row curRow = table.get(id);
        if (curRow == null) {
            throw new RuntimeException("data not exists");
        }

        try {
            curRow.getLock().lock();  // update操作时加上行锁
            // 构建旧版本数据(节点)
            Row oldRow = new Row(id);
            oldRow.setColumnData(curRow.getColumnData());
            oldRow.setTrxId(curRow.getTrxId());
            oldRow.setRollPointer(curRow.getRollPointer());
            oldRow.setDeleted(curRow.isDeleted());

            Map<String, Object> curColumData = new HashMap<>(curRow.getColumnData());  // 旧的业务数据
            curColumData.putAll(columnData);  // 更新业务数据
            curRow.setColumnData(curColumData);  // 将最新的业务数据赋给当前行（得到最新的行数据）
            curRow.setTrxId(TransactionManager.getCurTrxId());  // 设置当前行数据对应的事务id

            // 将当前最新版本数据链上旧版本数据
            curRow.setRollPointer(oldRow);

//        table.put(id, curRow);  // 当前行数据已经是最新的数据，不需要再put
        } finally {
            curRow.getLock().unlock();  // 解除行锁
        }
    }

    /**
     * 删除行数据（DELETE）
     * 逻辑删除，生成一个 "删除版本" 作为新表头
     */
    public void delete(Long id) {
        Row curRow = table.get(id);
        if (curRow == null) {
            throw new RuntimeException("data not exists");
        }

        try {
            curRow.getLock().lock();  // delete操作时加上行锁
            // 记录旧版本数据
            Row oldRow = new Row(curRow.getId());
            oldRow.setTrxId(curRow.getTrxId());
            oldRow.setColumnData(curRow.getColumnData());
            oldRow.setRollPointer(curRow.getRollPointer());
            oldRow.setDeleted(curRow.isDeleted());

            curRow.setTrxId(TransactionManager.getCurTrxId());
            curRow.setColumnData(new HashMap<>(curRow.getColumnData()));
            curRow.setDeleted(true);
            curRow.setRollPointer(oldRow);  // 将最新的"删除版本"数据，链上旧版本数据
        } finally {
            curRow.getLock().unlock();  // 解除行锁
        }
    }

    /**
     * 获取行对象（用于调试或测试打印版本链）
     * @param id 主键id
     * @return 行对象
     */
    public Row getRow(Long id) {
        return table.get(id);
    }
}
