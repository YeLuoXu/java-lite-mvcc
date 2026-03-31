package com.levi;

import com.levi.core.Row;
import com.levi.core.TransactionManager;
import com.levi.engine.MiniEngine;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * 可视化测试：验证 Undo Log 版本链的构建与回溯
 */
public class UndoLogTest {

    /**
     * 测试版本链的生长与打印结构
     */
    @Test
    public void testUndoLogChain() {
        MiniEngine engine = new MiniEngine();

        // 1. 初始化数据：id=1, name=Jack, age=18
        Map<String, Object> initData = new HashMap<>();
        initData.put("name", "Jack");
        initData.put("age", 18);
        engine.insert(1L, initData);

        // 2. 事务 A (trx_id=1) 修改 age=20
        TransactionManager.begin();
        Map<String, Object> update1 = new HashMap<>();
        update1.put("age", 20);
        engine.update(1L, update1);
        TransactionManager.commit();

        // 3. 事务 B (trx_id=2) 修改 name=Rose
        TransactionManager.begin();
        Map<String, Object> update2 = new HashMap<>();
        update2.put("name", "Rose");
        engine.update(1L, update2);
        TransactionManager.commit();

        // 4. 打印链表结构
        Row currentRow = engine.getRow(1L);
        printVersionChain(currentRow);

        // 5. 断言链表深度与内容
        Assert.assertNotNull(currentRow);
        Assert.assertEquals("Rose", currentRow.getColumnData().get("name"));
        Assert.assertEquals(20, currentRow.getColumnData().get("age"));
        Assert.assertNotNull(currentRow.getRollPointer());
        Assert.assertEquals("Jack", currentRow.getRollPointer().getColumnData().get("name"));
        Assert.assertEquals(18, currentRow.getRollPointer().getRollPointer().getColumnData().get("age"));
    }

    /**
     * 打印版本链
     *
     * @param row 当前行
     */
    private void printVersionChain(Row row) {
        StringBuilder sb = new StringBuilder();
        sb.append("======= Undo Log Version Chain (ASCII) =======\n");
        if (row == null) {
            sb.append("null\n");
            System.out.print(sb.toString());
            return;
        }
        java.util.List<String[]> nodes = new java.util.ArrayList<>();
        Row cursor = row;
        int depth = 0;
        while (cursor != null) {
            String header = String.format("V%d | Trx: %d", depth++, cursor.getTrxId());
            String data = String.format("Data: %s", cursor.getColumnData());
            nodes.add(new String[]{header, data});
            cursor = cursor.getRollPointer();
        }
        int width = 0;
        for (String[] lines : nodes) {
            width = Math.max(width, lines[0].length());
            width = Math.max(width, lines[1].length());
        }
        String topBottom = "+" + repeat('-', width + 2) + "+\n";
        for (int i = 0; i < nodes.size(); i++) {
            sb.append(topBottom);
            sb.append("| ").append(pad(nodes.get(i)[0], width)).append(" |\n");
            sb.append("| ").append(pad(nodes.get(i)[1], width)).append(" |\n");
            sb.append(topBottom);
            if (i < nodes.size() - 1) {
                sb.append("    |\n");
                sb.append("    v\n");
            } else {
                sb.append("    |\n");
                sb.append("    v\n");
                sb.append("   null\n");
            }
        }
        System.out.print(sb.toString());
    }

    private String repeat(char ch, int count) {
        StringBuilder r = new StringBuilder();
        for (int i = 0; i < count; i++) {
            r.append(ch);
        }
        return r.toString();
    }

    private String pad(String s, int width) {
        if (s.length() >= width) return s;
        StringBuilder p = new StringBuilder(s);
        while (p.length() < width) {
            p.append(' ');
        }
        return p.toString();
    }
}