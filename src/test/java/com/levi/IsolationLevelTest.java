package com.levi;

import com.levi.core.TransactionManager;
import com.levi.engine.MiniEngine;
import com.levi.strategy.impl.RCIsolationStrategyImpl;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * 隔离级别测试：验证 RC 与 RR 的可见性差异。
 */
public class IsolationLevelTest {

    /**
     * RR：重复读同一事务内的快照不受其他事务提交影响
     */
    @Test
    public void testRepeatableRead() throws InterruptedException {
        MiniEngine engine = new MiniEngine(); // 默认 RR
        Map<String, Object> init = new HashMap<>();
        init.put("name", "Jack");
        init.put("age", 18);
        engine.insert(1L, init);

        // 通过 CountDownLatch 来控制线程的执行顺序
        CountDownLatch latchReady = new CountDownLatch(1);
        CountDownLatch latchDone = new CountDownLatch(1);

        // 事务 A（当前线程）
        TransactionManager.begin();
        Map<String, Object> firstRead = engine.select(1L);
        Assert.assertEquals("Jack", firstRead.get("name"));
        Assert.assertEquals(18, firstRead.get("age"));

        // 事务 B（另一个线程）更新并提交
        Thread t = new Thread(() -> {
            TransactionManager.begin();
            Map<String, Object> u = new HashMap<>();
            u.put("name", "Rose");
            u.put("age", 20);
            engine.update(1L, u);
            TransactionManager.commit();
            latchReady.countDown();
            try {
                latchDone.await();  // 需要等待 事务A 执行完latchDone.countDown(); 才能继续往下执行
            } catch (InterruptedException ignored) {
            }
        });
        t.start();

        // 等待事务 B 完成提交
        latchReady.await();  // 需要等待 事务B 执行完latchReady.countDown(); 才能继续往下执行

        // 事务 A 再次读取，应复用第一次生成的快照
        Map<String, Object> secondRead = engine.select(1L);
        Assert.assertEquals("Jack", secondRead.get("name"));
        Assert.assertEquals(18, secondRead.get("age"));

        TransactionManager.commit();
        latchDone.countDown();
    }

    /**
     * RC：同一事务内每次 SELECT 都生成最新快照，可读到他人已提交修改
     */
    @Test
    public void testReadCommitted() throws InterruptedException {
        MiniEngine engine = new MiniEngine();
        engine.setIsolationStrategy(new RCIsolationStrategyImpl());  // 设置事务隔离级别为RC
        Map<String, Object> init = new HashMap<>();
        init.put("name", "Jack");
        init.put("age", 18);
        engine.insert(1L, init);

        CountDownLatch latchReady = new CountDownLatch(1);
        CountDownLatch latchDone = new CountDownLatch(1);

        // 事务 A（当前线程）
        TransactionManager.begin();
        Map<String, Object> firstRead = engine.select(1L);
        Assert.assertEquals("Jack", firstRead.get("name"));
        Assert.assertEquals(18, firstRead.get("age"));

        // 事务 B（另一个线程）更新并提交
        Thread t = new Thread(() -> {
            TransactionManager.begin();
            Map<String, Object> u = new HashMap<>();
            u.put("name", "Rose");
            u.put("age", 20);
            engine.update(1L, u);
            TransactionManager.commit();
            latchReady.countDown();
            try {
                latchDone.await();
            } catch (InterruptedException ignored) {
            }
        });
        t.start();

        // 等待事务 B 完成提交
        latchReady.await();

        // RC：再次读取，应读到事务 B 的提交结果
        Map<String, Object> secondRead = engine.select(1L);
        Assert.assertEquals("Rose", secondRead.get("name"));
        Assert.assertEquals(20, secondRead.get("age"));

        TransactionManager.commit();
        latchDone.countDown();
    }

    /**
     * 测试RR隔离级别下的删除可见性
     */
    @Test
    public void testDeleteRepeatableRead() throws InterruptedException {
        MiniEngine engine = new MiniEngine();
        Map<String, Object> init = new HashMap<>();
        init.put("name", "Jack");
        init.put("age", 18);
        engine.insert(1L, init);

        CountDownLatch latchReady = new CountDownLatch(1);
        CountDownLatch latchDone = new CountDownLatch(1);

        TransactionManager.begin();
        Map<String, Object> firstRead = engine.select(1L);
        Assert.assertNotNull(firstRead);

        Thread t = new Thread(() -> {
            TransactionManager.begin();
            engine.delete(1L);
            TransactionManager.commit();
            latchReady.countDown();
            try {
                latchDone.await();
            } catch (InterruptedException ignored) {
            }
        });
        t.start();

        latchReady.await();

        Map<String, Object> rrSecondRead = engine.select(1L);
        Assert.assertNotNull(rrSecondRead);
        TransactionManager.commit();

        latchDone.countDown();
    }

    /**
     * 测试RC隔离级别下的删除可见性（逻辑删除）
     */
    @Test
    public void testDeleteReadCommitted() throws InterruptedException {
        MiniEngine engine = new MiniEngine();
        engine.setIsolationStrategy(new RCIsolationStrategyImpl());
        Map<String, Object> init = new HashMap<>();
        init.put("name", "Jack");
        init.put("age", 18);
        engine.insert(1L, init);

        TransactionManager.begin();
        Map<String, Object> firstRead = engine.select(1L);
        Assert.assertNotNull(firstRead);

        Thread t = new Thread(() -> {
            TransactionManager.begin();
            engine.delete(1L);
            TransactionManager.commit();
        });
        t.start();
        t.join();  // 会阻塞主线程（事务A），直到子线程 t（事务B）执行完毕

        Map<String, Object> secondRead = engine.select(1L);
        Assert.assertNull(secondRead);
        TransactionManager.commit();
    }

    /**
     * 测试RR隔离级别下的可见性（复杂场景：更新 + 删除）
     */
    @Test
    public void testDeleteRepeatableReadComplexSequence() throws InterruptedException {
        MiniEngine engine = new MiniEngine();
        Map<String, Object> init = new HashMap<>();
        init.put("name", "Jack");
        init.put("age", 18);
        engine.insert(1L, init);

        CountDownLatch latchU = new CountDownLatch(1);
        CountDownLatch latchD = new CountDownLatch(1);

        TransactionManager.begin();
        Map<String, Object> aRead1 = engine.select(1L);
        Assert.assertNotNull(aRead1);
        Assert.assertEquals(18, aRead1.get("age"));

        Thread tUpdate = new Thread(() -> {
            TransactionManager.begin();
            Map<String, Object> u = new HashMap<>();
            u.put("age", 20);
            engine.update(1L, u);
            TransactionManager.commit();
            latchU.countDown();
        });
        tUpdate.start();

        latchU.await();

        Thread tDelete = new Thread(() -> {
            TransactionManager.begin();
            engine.delete(1L);
            TransactionManager.commit();
            latchD.countDown();
        });
        tDelete.start();

        latchD.await();

        Map<String, Object> aRead2 = engine.select(1L);
        Assert.assertNotNull(aRead2);
        Assert.assertEquals(18, aRead2.get("age"));
        TransactionManager.commit();

        // 重新开启一个事务
        TransactionManager.begin();
        Map<String, Object> newRead = engine.select(1L);
        Assert.assertNull(newRead);
        TransactionManager.commit();
    }

    /**
     * 测试RC隔离级别下的可见性（复杂场景：更新 + 删除）
     */
    @Test
    public void testDeleteReadCommittedComplexSequence() throws InterruptedException {
        MiniEngine engine = new MiniEngine();
        engine.setIsolationStrategy(new RCIsolationStrategyImpl());
        Map<String, Object> init = new HashMap<>();
        init.put("name", "Jack");
        init.put("age", 18);
        engine.insert(1L, init);

        CountDownLatch latchU = new CountDownLatch(1);
        CountDownLatch latchD = new CountDownLatch(1);

        TransactionManager.begin();
        Map<String, Object> aRead1 = engine.select(1L);
        Assert.assertNotNull(aRead1);
        Assert.assertEquals(18, aRead1.get("age"));

        Thread tUpdate = new Thread(() -> {
            TransactionManager.begin();
            Map<String, Object> u = new HashMap<>();
            u.put("age", 20);
            engine.update(1L, u);
            TransactionManager.commit();
            latchU.countDown();
        });
        tUpdate.start();

        latchU.await();

        Map<String, Object> aRead2 = engine.select(1L);
        Assert.assertEquals(20, aRead2.get("age"));

        Thread tDelete = new Thread(() -> {
            TransactionManager.begin();
            engine.delete(1L);
            TransactionManager.commit();
            latchD.countDown();
        });
        tDelete.start();

        latchD.await();

        Map<String, Object> aRead3 = engine.select(1L);
        Assert.assertNull(aRead3);
        TransactionManager.commit();
    }

    /**
     * 测试RR隔离级别下的可见性（复杂场景：更新 + 更新 + 更新 + 删除）
     */
    @Test
    public void testDeleteRepeatableReadScenario3() throws InterruptedException {
        MiniEngine engine = new MiniEngine();
        Map<String, Object> init = new HashMap<>();
        init.put("name", "Jack");
        init.put("age", 18);
        engine.insert(1L, init);

        CountDownLatch l1 = new CountDownLatch(1);
        CountDownLatch l2 = new CountDownLatch(1);
        CountDownLatch l3 = new CountDownLatch(1);

        TransactionManager.begin();
        Map<String, Object> first = engine.select(1L);
        Assert.assertNotNull(first);
        Assert.assertEquals(18, first.get("age"));
        Assert.assertEquals("Jack", first.get("name"));

        Thread t1 = new Thread(() -> {
            TransactionManager.begin();
            Map<String, Object> u = new HashMap<>();
            u.put("age", 19);
            engine.update(1L, u);
            TransactionManager.commit();
            l1.countDown();
        });
        t1.start();

        Thread t2 = new Thread(() -> {
            TransactionManager.begin();
            Map<String, Object> u = new HashMap<>();
            u.put("age", 20);
            engine.update(1L, u);
            TransactionManager.commit();
            l2.countDown();
        });
        t2.start();

        // 验证自己更新的数据是否可见
        Thread t3 = new Thread(() -> {
            TransactionManager.begin();
            Map<String, Object> u = new HashMap<>();
            u.put("name", "Rose");
            engine.update(1L, u);
            Map<String, Object> curSelect = engine.select(1L);
            Assert.assertNotNull(curSelect);
            Assert.assertEquals("Rose", curSelect.get("name"));
            TransactionManager.commit();
            l3.countDown();
        });
        t3.start();

        l1.await();
        l2.await();
        l3.await();

        CountDownLatch lDel = new CountDownLatch(1);

        Map<String, Object> mainSelect = engine.select(1L);
        Assert.assertNotNull(mainSelect);
        Assert.assertEquals("Jack", mainSelect.get("name"));

        Thread td = new Thread(() -> {
            TransactionManager.begin();
            engine.delete(1L);
            TransactionManager.commit();
            lDel.countDown();
        });
        td.start();

        lDel.await();

        Map<String, Object> second = engine.select(1L);
        Assert.assertNotNull(second);
        Assert.assertEquals(18, second.get("age"));
        Assert.assertEquals("Jack", second.get("name"));
        TransactionManager.commit();

        // 重写开启一个事务，看是否能看到最新的数据
        TransactionManager.begin();
        Map<String, Object> after = engine.select(1L);
        Assert.assertNull(after);
        TransactionManager.commit();
    }
}