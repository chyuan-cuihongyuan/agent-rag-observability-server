package cn.chyuan.ai.observability.domain.regexkernel.service;

import java.util.List;

/**
 * 字符类语义（工单 0639 BX6，ripgrep 字节面向思想）。
 * 区间/否定/转义成员/预定义类（\d \w \s 与否定大写）/
 * 点号语义（默认除换行）/Unicode 码点与 UTF-8 字节序列匹配
 * （输入按码点解码，中文多字节正确匹配）。
 */
public final class CharClasses {

    /** 码点区间（闭区间） */
    public record Range(int lo, int hi) {

        public Range {
            if (hi < lo) {
                throw new IllegalArgumentException("区间逆序：" + lo + "-" + hi);
            }
        }

        public boolean contains(int cp) {
            return cp >= lo && cp <= hi;
        }
    }

    /** Unicode 码点上界 */
    public static final int MAX_CP = 0x10FFFF;

    private CharClasses() {
    }

    public static List<Range> digitRanges() {
        return List.of(new Range('0', '9'));
    }

    public static List<Range> wordRanges() {
        return List.of(
                new Range('0', '9'),
                new Range('A', 'Z'),
                new Range('_', '_'),
                new Range('a', 'z'));
    }

    public static List<Range> spaceRanges() {
        return List.of(
                new Range('\t', '\r'),
                new Range(' ', ' '));
    }

    /** 区间集合匹配（否定时对全码点域取补，点号除 \n 由调用方处理） */
    public static boolean matches(List<Range> ranges, boolean negated, int cp) {
        boolean hit = false;
        for (Range range : ranges) {
            if (range.contains(cp)) {
                hit = true;
                break;
            }
        }
        if (negated) {
            hit = !hit;
        }
        return hit;
    }
}
