package cn.chyuan.ai.observability.domain.evaluate.service;

import cn.chyuan.ai.observability.domain.evaluate.model.valobj.RetrievalMetrics;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 检索指标计算器单元测试 — 覆盖 recall/precision/f1/top3 以及新增的排序感知指标 mrr/ndcg/map。
 * 用蓝军视角覆盖：全命中、部分命中、无命中、空输入、命中位置影响排序指标 等边界。
 */
class RetrievalMetricCalculatorTest {

    private final RetrievalMetricCalculator calculator = new RetrievalMetricCalculator();

    // ===== 辅助构造：标准 chunk 与实际 chunk 文本完全一致，保证 Jaccard=1 命中 =====

    @Test
    void allHits_returnsFullScores() {
        List<String> gold = List.of("chunkA内容", "chunkB内容");
        // 实际检索顺序与标准一致 → 全命中
        List<String> actual = List.of("chunkA内容", "chunkB内容");

        RetrievalMetrics m = calculator.compute(gold, actual, "ansA", "ansA");

        assertThat(m.getRecall()).isEqualTo(1.0);
        assertThat(m.getPrecision()).isEqualTo(1.0);
        assertThat(m.getF1()).isEqualTo(1.0);
        assertThat(m.getTop3HitRate()).isEqualTo(1.0);
        // 全命中且第一条命中：MRR=1, NDCG=1, MAP=1
        assertThat(m.getMrr()).isEqualTo(1.0);
        assertThat(m.getNdcg()).isEqualTo(1.0);
        assertThat(m.getMap()).isEqualTo(1.0);
    }

    @Test
    void mrr_rewardsHigherRank() {
        List<String> gold = List.of("唯一标准chunk");
        // 命中条排第 2 位（第 1 条是噪声）
        List<String> actual = List.of("完全无关的噪声内容", "唯一标准chunk");

        RetrievalMetrics m = calculator.compute(gold, actual, "ans", "ans");

        // MRR = 1/2 = 0.5
        assertThat(m.getMrr()).isCloseTo(0.5, within(0.001));
        // Top3 仍命中
        assertThat(m.getTop3HitRate()).isEqualTo(1.0);
        // recall=1（标准 chunk 被命中）
        assertThat(m.getRecall()).isEqualTo(1.0);
        // precision=0.5（2 条实际只有 1 条命中）
        assertThat(m.getPrecision()).isCloseTo(0.5, within(0.001));
    }

    @Test
    void mrr_zeroWhenNoHit() {
        List<String> gold = List.of("唯一标准chunk");
        List<String> actual = List.of("噪声A", "噪声B", "噪声C");

        RetrievalMetrics m = calculator.compute(gold, actual, "ans", "ans");

        assertThat(m.getMrr()).isEqualTo(0.0);
        assertThat(m.getNdcg()).isEqualTo(0.0);
        assertThat(m.getMap()).isEqualTo(0.0);
        assertThat(m.getRecall()).isEqualTo(0.0);
        assertThat(m.getPrecision()).isEqualTo(0.0);
        assertThat(m.getTop3HitRate()).isEqualTo(0.0);
    }

    @Test
    void ndcg_degradesWhenRelevantItemsRankLow() {
        List<String> gold = List.of("标准A", "标准B");
        // 2 条命中但都排在噪声后面：[噪声, 命中A, 命中B]
        List<String> actual = List.of("无关噪声内容", "标准A", "标准B");

        RetrievalMetrics m = calculator.compute(gold, actual, "ans", "ans");

        // 相关项全排后 → NDCG < 1
        assertThat(m.getNdcg()).isGreaterThan(0.0).isLessThan(1.0);
        // 但 recall/precision 仍是满分（2 命中 / 2 标准，2 命中 / 2 实际相关）
        assertThat(m.getRecall()).isEqualTo(1.0);
        // precision = 2命中/3实际 = 0.6667
        assertThat(m.getPrecision()).isCloseTo(0.6667, within(0.001));
    }

    @Test
    void ndcg_equalsOneWhenAllRelevantRankedFirst() {
        List<String> gold = List.of("标准A", "标准B");
        // 理想排序：相关项全在最前
        List<String> actual = List.of("标准A", "标准B", "无关噪声");

        RetrievalMetrics m = calculator.compute(gold, actual, "ans", "ans");

        assertThat(m.getNdcg()).isEqualTo(1.0);
    }

    @Test
    void map_accumulatesPrecisionAtHitPositions() {
        List<String> gold = List.of("标准A", "标准B");
        // relevant = [命中, 噪声, 命中] → precision@1=1/1, precision@3=2/3
        // MAP = (1.0 + 0.6667) / 2 = 0.8333
        List<String> actual = List.of("标准A", "无关噪声内容", "标准B");

        RetrievalMetrics m = calculator.compute(gold, actual, "ans", "ans");

        assertThat(m.getMap()).isCloseTo(0.8333, within(0.001));
    }

    @Test
    void emptyInputs_returnsAllZerosWithoutException() {
        RetrievalMetrics m = calculator.compute(null, null, null, null);

        assertThat(m.getRecall()).isEqualTo(0.0);
        assertThat(m.getPrecision()).isEqualTo(0.0);
        assertThat(m.getF1()).isEqualTo(0.0);
        assertThat(m.getTop3HitRate()).isEqualTo(0.0);
        assertThat(m.getMrr()).isEqualTo(0.0);
        assertThat(m.getNdcg()).isEqualTo(0.0);
        assertThat(m.getMap()).isEqualTo(0.0);
        assertThat(m.getAnswerSimilarity()).isEqualTo(0.0);
    }

    @Test
    void emptyGold_returnsZeroRecallButPrecisionStaysZero() {
        // 标准集为空：recall 分母为 0 → 0；precision 分母为实际数但无命中 → 0
        List<String> actual = List.of("实际检索chunkA", "实际检索chunkB");

        RetrievalMetrics m = calculator.compute(List.of(), actual, "std", "act");

        assertThat(m.getRecall()).isEqualTo(0.0);
        assertThat(m.getPrecision()).isEqualTo(0.0);
        assertThat(m.getMrr()).isEqualTo(0.0);
        assertThat(m.getMap()).isEqualTo(0.0);
    }

    @Test
    void f1_isHarmonicMeanOfRecallAndPrecision() {
        // 用足够长且词面无交集的文本，避免中文单字分词 + Jaccard 阈值导致的假命中
        List<String> gold = List.of("alpha configuration settings for database connection pool",
                "beta optimization parameters for query performance tuning");
        // 只命中第 1 条标准 → recall=0.5；2 条实际只有 1 条命中 → precision=0.5
        List<String> actual = List.of("alpha configuration settings for database connection pool",
                "zzzz totally unrelated noise document content here for testing only");

        RetrievalMetrics m = calculator.compute(gold, actual, "ans", "ans");

        assertThat(m.getRecall()).isCloseTo(0.5, within(0.001));
        assertThat(m.getPrecision()).isCloseTo(0.5, within(0.001));
        // F1 = 2*0.5*0.5/(0.5+0.5) = 0.5
        assertThat(m.getF1()).isCloseTo(0.5, within(0.001));
    }
}
