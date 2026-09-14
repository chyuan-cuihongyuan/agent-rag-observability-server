package cn.chyuan.ai.observability.domain.evaluate.promptopt.service;

import cn.chyuan.ai.observability.domain.evaluate.promptopt.model.RefineResultVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 自我改进循环单测（工单 0328 AO6）：三路径/错误回喂/校验器异常兜底。
 */
class SelfRefineLoopTest {

    @Test
    void 一次通过与重试后通过与上限失败() {
        // 一次通过
        SelfRefineLoop once = new SelfRefineLoop(3);
        RefineResultVO ok = once.run("输入", (input, feedback) -> "合法输出",
                output -> output != null && output.contains("合法") ? List.of() : List.of(bad("x")));
        assertTrue(ok.isPassed());
        assertEquals(1, ok.getAttempts());
        // 第二次带错误回喂后通过
        SelfRefineLoop retry = new SelfRefineLoop(3);
        List<List<RefineResultVO.ValidationErrorVO>> seenFeedback = new java.util.ArrayList<>();
        RefineResultVO fixed = retry.run("输入", (input, feedback) -> {
            seenFeedback.add(feedback);
            return feedback == null ? "坏输出" : "修复输出";
        }, output -> output.contains("修复") ? List.of() : List.of(bad("$.格式")));
        assertTrue(fixed.isPassed());
        assertEquals(2, fixed.getAttempts());
        assertTrue(seenFeedback.get(0) == null, "首轮无反馈");
        assertEquals("$.格式", seenFeedback.get(1).get(0).getPath());
        // 上限失败带最后错误
        SelfRefineLoop never = new SelfRefineLoop(2);
        RefineResultVO failed = never.run("输入", (input, feedback) -> "总是坏",
                output -> List.of(bad("$.永远")));
        assertFalse(failed.isPassed());
        assertEquals(2, failed.getAttempts());
        assertEquals("$.永远", failed.getLastErrors().get(0).getPath());
    }

    @Test
    void 校验器异常兜底与生成异常() {
        // 校验器异常 → 兜底错误计入，达到上限失败
        SelfRefineLoop loop = new SelfRefineLoop(1);
        RefineResultVO result = loop.run("输入", (input, feedback) -> "输出",
                output -> {
                    throw new IllegalStateException("校验器挂");
                });
        assertFalse(result.isPassed());
        assertEquals(1, result.getAttempts());
        assertTrue(result.getLastErrors().get(0).getMessage().contains("校验器异常"));
        // 生成端口异常 → null 输出由校验器判定
        SelfRefineLoop genFail = new SelfRefineLoop(1);
        RefineResultVO nullOutput = genFail.run("输入", (input, feedback) -> {
            throw new IllegalStateException("生成挂");
        }, output -> output == null ? List.of(bad("$.null")) : List.of());
        assertFalse(nullOutput.isPassed());
        assertEquals(null, nullOutput.getOutput());
    }

    @Test
    void 回喂渲染与非法配置() {
        String rendered = SelfRefineLoop.renderFeedback(List.of(
                bad("$.a"), bad("$.b")));
        assertEquals("$.a: 错误1; $.b: 错误1", rendered);
        assertThrows(IllegalArgumentException.class, () -> new SelfRefineLoop(0));
        assertThrows(IllegalArgumentException.class, () -> new SelfRefineLoop(1).run("x", null, o -> List.of()));
    }

    private RefineResultVO.ValidationErrorVO bad(String path) {
        return RefineResultVO.ValidationErrorVO.builder().path(path).message("错误1").build();
    }
}
