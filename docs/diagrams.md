# Java-Lite-MVCC 关系图与架构图

## ER 关系图（数据视角）

```mermaid
erDiagram
    TRANSACTION {
        long trx_id PK
        string status
    }
    READVIEW {
        long creatorTrxId FK
        long minTrxId
        long maxTrxId
        set  mIds  "活跃事务ID集合快照"
    }
    ROW {
        int  id PK
        map  columnData
        long trxId  "最近修改事务ID"
        int  rollPointer FK "指向旧版本的行ID(内存自引用)"
    }
    LITEENGINE {
        map table "Map<id, Row> 当前行作为链表表头"
    }

    TRANSACTION ||--o{ READVIEW : "创建快照(视图)"
    READVIEW }o--o{ TRANSACTION : "引用活跃事务ID"
    TRANSACTION ||--o{ ROW : "修改行(生成新版本)"
    READVIEW ||--o{ ROW : "可见性筛选(快照读)"
```

说明
- READVIEW.mIds 是“生成时刻”的活跃事务ID集合快照，用于可见性判断
- ROW.rollPointer 构成 Undo Log 链；LITEENGINE.table 的每个 id 对应“链表表头”（最新版本）

## 架构关系（类与依赖）

```mermaid
flowchart LR
    subgraph Core
        TM[TransactionManager]
        RV[ReadView]
        RowClass[Row]
    end

    subgraph Engine
        ME[LiteEngine]
    end

    subgraph Strategy
        IS[IsolationStrategy]
        RC[ReadCommittedStrategy]
        RR[RepeatableReadStrategy]
    end

    TM --> RV
    TM -->|"发号器/活跃集"| TRX[(trx_id)]
    ME --> IS
    IS --> RC
    IS --> RR
    ME -->|"select使用ReadView回溯"| RowClass
    RowClass -->|"rollPointer自引用"| RowClass
```

要点
- LiteEngine 通过 IsolationStrategy 获取 ReadView（RC 每次新建，RR 复用首次）
- ReadView 使用 TransactionManager 的活跃集与发号器边界进行可见性判断
- Row 自引用形成版本链；select 顺链回溯到可见版本

## 版本链（Undo Log 可视化）

```mermaid
flowchart LR
    V0[(Row V0<br/>trxId=2)]
    V1[(Row V1<br/>trxId=1)]
    V2[(Row V2<br/>trxId=0)]
    NULL[null]

    V0 --> V1 --> V2 --> NULL
```

关联代码
- 事务管理与活跃集：TransactionManager.java
- 读视图与可见性：ReadView.java
- 行对象与隐藏列：Row.java
- 引擎读写与链回溯：LiteEngine.java
