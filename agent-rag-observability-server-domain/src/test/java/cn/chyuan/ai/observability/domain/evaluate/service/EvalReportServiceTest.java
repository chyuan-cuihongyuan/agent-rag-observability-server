package cn.chyuan.ai.observability.domain.evaluate.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 评测报告拼装单元测试（工单 0175 X6）— 小节开关、空数据降级、markdown 转义。
 */
@DisplayName("评测报告拼装测试")
class EvalReportServiceTest {

    private final EvalReportService.Sections all = EvalReportService.Sections.all();

    private EvalReportService service() {
        // build 纯函数不触仓，构造依赖传 null 即可
        return new EvalReportService(null, null, null, null);
    }

    @Test
    @DisplayName("拼装 — 四小节标题与内容就位")
    public void testBuildAllSections() {
        String md = service().build("回归报告", "任务对比表", "PASS", "PLANNING 5（50%）", "轮次 P1 全绿", all);

        assertTrue(md.startsWith("# 回归报告"));
        assertTrue(md.contains("## 1. 任务对比"));
        assertTrue(md.contains("任务对比表"));
        assertTrue(md.contains("## 2. 门禁结论"));
        assertTrue(md.contains("PASS"));
        assertTrue(md.contains("## 3. 归因分布"));
        assertTrue(md.contains("## 4. 巡检状态"));
    }

    @Test
    @DisplayName("空小节降级「暂无数据」；开关关闭不输出小节")
    public void testEmptyAndSwitches() {
        String md = service().build("报告", null, "BLOCK", null, null, all);
        assertTrue(md.contains("暂无数据"));
        assertTrue(md.contains("BLOCK"));

        EvalReportService.Sections taskOnly = new EvalReportService.Sections(true, false, false, false);
        String trimmed = service().build("报告", "仅任务", null, null, null, taskOnly);
        assertTrue(trimmed.contains("仅任务"));
        assertFalse(trimmed.contains("门禁结论"));
    }

    @Test
    @DisplayName("mdCell 转义 — 竖线与换行")
    public void testMdCell() {
        assertEquals("", EvalReportService.mdCell(null));
        assertEquals("a\\|b", EvalReportService.mdCell("a|b"));
        assertEquals("x y", EvalReportService.mdCell("x\ny"));
    }
}
