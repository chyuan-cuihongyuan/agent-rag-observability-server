package cn.chyuan.ai.observability.domain.evaluate.promptopt.adapter.repository;

import cn.chyuan.ai.observability.domain.evaluate.promptopt.model.OptimExperimentVO;

import java.util.List;
import java.util.Optional;

/**
 * 优化实验仓储端口（AO7：infrastructure 内存/数据库适配）。
 */
public interface OptimExperimentRepository {

    void save(OptimExperimentVO experiment);

    Optional<OptimExperimentVO> findById(String experimentId);

    List<OptimExperimentVO> listAll();

    /** 两实验得分曲线对比（缺失实验返回空 Optional） */
    Optional<CurveCompareVO> compare(String leftId, String rightId);

    /** 曲线对比值对象 */
    record CurveCompareVO(String leftId, String rightId,
                          String leftCurveJson, String rightCurveJson,
                          double leftBest, double rightBest, double lift) {
    }
}
