package cn.chyuan.ai.observability.domain.evaluate.promptopt.service;

import cn.chyuan.ai.observability.domain.evaluate.promptopt.model.EvalRecordVO;
import cn.chyuan.ai.observability.domain.evaluate.promptopt.model.FewShotSetVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 少样本挖掘单测（工单 0324 AO2）：阈值过滤/相似去重/K 上限/边界。
 */
class FewShotMinerTest {

    @Test
    void 阈值过滤与得分降序选取() {
        FewShotMiner miner = new FewShotMiner(80, 0.8);
        FewShotSetVO set = miner.mine(List.of(
                EvalRecordVO.builder().input("低分输入").output("x").score(50).build(),
                EvalRecordVO.builder().input("中分输入").output("y").score(80).build(),
                EvalRecordVO.builder().input("高分输入").output("z").score(95).build()), 5);
        assertEquals(2, set.getSamples().size(), "低分被阈值过滤（不计入 dropped）");
        assertEquals(95, set.getSamples().get(0).getScore(), "得分降序");
        assertEquals(80, set.getSamples().get(1).getScore());
        assertTrue(set.getRendered().contains("高分输入 → 输出 z"));
        assertEquals(0, set.getDropped(), "k=5 未触发上限，无去重淘汰");
    }

    @Test
    void 相似输入去重保留高分者() {
        FewShotMiner miner = new FewShotMiner(0, 0.3);
        FewShotSetVO set = miner.mine(List.of(
                EvalRecordVO.builder().input("请解释RAG检索增强生成原理").output("A").score(60).build(),
                EvalRecordVO.builder().input("请解释RAG检索增强生成机制").output("B").score(90).build()), 5);
        // 两条输入三元组高度重叠 → 只留高分者
        assertEquals(1, set.getSamples().size());
        assertEquals(90, set.getSamples().get(0).getScore());
        assertEquals(1, set.getDropped());
    }

    @Test
    void K上限与空记录边界() {
        FewShotMiner miner = new FewShotMiner(0, 1.0);
        FewShotSetVO set = miner.mine(List.of(
                EvalRecordVO.builder().input("a").output("1").score(10).build(),
                EvalRecordVO.builder().input("b").output("2").score(20).build(),
                EvalRecordVO.builder().input("c").output("3").score(30).build()), 2);
        assertEquals(2, set.getSamples().size(), "K 上限截断");
        assertEquals(1, set.getDropped());
        FewShotSetVO empty = miner.mine(List.of(), 3);
        assertTrue(empty.getSamples().isEmpty());
        assertEquals("", empty.getRendered());
        FewShotSetVO nullSafe = miner.mine(null, 3);
        assertTrue(nullSafe.getSamples().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> miner.mine(List.of(), 0));
        assertThrows(IllegalArgumentException.class, () -> new FewShotMiner(-1, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new FewShotMiner(0, 2));
    }

    @Test
    void 相似度内核边界() {
        // 完全相同归一 → Jaccard 1.0；完全不同 → 0.0
        assertEquals(1.0, FewShotMiner.jaccard(
                FewShotMiner.trigrams(FewShotMiner.normalize("ABC")),
                FewShotMiner.trigrams(FewShotMiner.normalize("abc"))));
        assertEquals(0.0, FewShotMiner.jaccard(
                FewShotMiner.trigrams("aaa"), FewShotMiner.trigrams("bbb")));
    }
}
