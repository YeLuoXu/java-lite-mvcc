# Java-Lite-MVCC：手写数据库多版本并发控制引擎

一个纯 Java 的内存数据库引擎，演示 Undo Log 版本链与 MVCC 快照读，支持 RC 与 RR 两种隔离级别。

## 特性
- 行对象包含隐藏列：最近修改事务 ID、回滚指针（链表构建）
- 写入自动生成 Undo Log 版本链，读时顺链回溯选择可见版本
- 策略模式切换隔离级别：读已提交（RC）、可重复读（RR）
- 简化的事务管理器：发号器、活跃事务集合、ThreadLocal 上下文

## 代码结构
- `com.mini.core`：Row、TransactionManager、ReadView
- `com.mini.mvcc.strategy`：隔离级别策略接口与实现（RC、RR）
- `com.mini.mvcc.engine`：MiniEngine（INSERT/SELECT/UPDATE/DELETE）
- `src/test`：Undo Log 可视化与隔离级别单元测试

## 架构图
查看 Mermaid 图： docs/diagrams.md