package cn.chyuan.ai.observability.domain.regexkernel.service;

import static cn.chyuan.ai.observability.domain.regexkernel.service.NfaProgram.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Pike VM（工单 0635 BX2 + 0636 BX3，RE2/ripgrep Pike VM 思想）。
 * NFA 线程列表按输入位置逐步推进/线程优先级顺序保持最左优先
 * （leftmost-first，与回溯语义等价）/相同状态线程剪枝（去重，
 * 零宽循环免疫）/捕获槽随线程携带（内部码点位，出口换算字符位）/
 * 步数计数线性保证（超限熔断）。
 */
public final class PikeVm {

    /** 匹配结果（-1=未参与；字符位口径） */
    public record Match(int start, int end, int[] slots) {

        public boolean present() {
            return start >= 0;
        }

        public int groupStart(int group) {
            return slots.length > 2 * group ? slots[2 * group] : -1;
        }

        public int groupEnd(int group) {
            return slots.length > 2 * group + 1 ? slots[2 * group + 1] : -1;
        }

        public String group(int group, String input) {
            int s = groupStart(group);
            int e = groupEnd(group);
            return s < 0 || e < 0 || s > e ? null : input.substring(s, e);
        }
    }

    public static final Match NO_MATCH = new Match(-1, -1, new int[]{-1, -1});

    /** 线程：pc + 捕获槽（码点位） */
    private static final class Thread {
        final int pc;
        final int[] slots;

        Thread(int pc, int[] slots) {
            this.pc = pc;
            this.slots = slots;
        }
    }

    private final NfaProgram prog;

    public PikeVm(NfaProgram prog) {
        this.prog = prog;
    }

    /** 无锚搜索：最左优先（起位自左向右，锚定运行首个成功即返回） */
    public Match search(String input, int charFrom) {
        this.codePointTotal = input.codePointCount(0, input.length());
        try {
            int[] cps = codePoints(input);
            int[] offsets = offsets(input);
            int startCp = charIndex(charFrom, offsets);
            long steps = 0L;
            for (int start = startCp; start <= cps.length; start++) {
                Match match = runAnchored(cps, offsets, start, steps);
                if (match != null) {
                    return match;
                }
            }
            return NO_MATCH;
        } finally {
            this.codePointTotal = -1;
        }
    }

    /** 锚定运行：首个（优先序）匹配记录后截断低优先线程，高优先线程继续贪进；步数熔断 */
    private Match runAnchored(int[] cps, int[] offsets, int startCp, long steps) {
        Match best = null;
        List<Thread> current = new ArrayList<>();
        addThread(current, new boolean[prog.size()], 0, startCp, newSlots(startCp));
        int pos = startCp;
        while (!current.isEmpty()) {
            List<Thread> next = new ArrayList<>();
            boolean[] nextOn = new boolean[prog.size()];
            int cp = pos < cps.length ? cps[pos] : -1;
            for (Thread thread : current) {
                if (++steps > RegexSafety.MAX_STEPS) {
                    throw RegexSafety.stepLimitExceeded(steps);
                }
                Instr instr = prog.at(thread.pc);
                if (instr instanceof IMatch) {
                    // 命中：覆盖 best（截断低优先线程后，后续命中必来自更高优先世系）
                    best = toCharMatch(thread.slots, offsets);
                    break;
                } else if (instr instanceof IClass cls && cp >= 0
                        && CharClasses.matches(cls.ranges(), cls.negated(), cp)) {
                    addThread(next, nextOn, thread.pc + 1, pos + 1, thread.slots);
                }
            }
            if (cp < 0) {
                break;
            }
            current = next;
            pos++;
        }
        return best;
    }

    /** ε 闭包展开（优先序添加；同 pc 剪枝防零宽循环） */
    private void addThread(List<Thread> list, boolean[] onList, int pc, int pos, int[] slots) {
        if (onList[pc]) {
            return;
        }
        onList[pc] = true;
        Instr instr = prog.at(pc);
        switch (instr) {
            case IJmp jmp -> addThread(list, onList, jmp.x(), pos, slots);
            case ISplit split -> {
                addThread(list, onList, split.x(), pos, slots);
                addThread(list, onList, split.y(), pos, slots);
            }
            case ISave save -> {
                int[] saved = slots.clone();
                saved[save.slot()] = pos;
                addThread(list, onList, pc + 1, pos, saved);
            }
            case IBol ignored -> {
                if (pos == 0) {
                    addThread(list, onList, pc + 1, pos, slots);
                }
            }
            case IEol ignored -> {
                if (pos == codePointTotal) {
                    addThread(list, onList, pc + 1, pos, slots);
                }
            }
            default -> list.add(new Thread(pc, slots));
        }
    }

    private int codePointTotal;

    /** 码点位槽 → 字符位结果 */
    private Match toCharMatch(int[] slots, int[] offsets) {
        int[] out = new int[slots.length];
        for (int i = 0; i < slots.length; i++) {
            out[i] = slots[i] < 0 ? -1 : charPos(slots[i], offsets);
        }
        return new Match(out[0], out[1], out);
    }

    private int[] newSlots(int startCp) {
        int[] slots = new int[2 * (prog.groupCount() + 1)];
        java.util.Arrays.fill(slots, -1);
        return slots;
    }

    private static int[] codePoints(String input) {
        int[] out = new int[input.codePointCount(0, input.length())];
        int idx = 0;
        for (int i = 0; i < input.length(); ) {
            int cp = input.codePointAt(i);
            out[idx++] = cp;
            i += Character.charCount(cp);
        }
        return out;
    }

    private static int[] offsets(String input) {
        int count = input.codePointCount(0, input.length());
        int[] out = new int[count + 1];
        int idx = 0;
        for (int i = 0; i < input.length(); i += Character.charCount(input.codePointAt(i))) {
            out[idx++] = i;
        }
        out[count] = input.length();
        return out;
    }

    private static int charIndex(int charFrom, int[] offsets) {
        for (int i = 0; i < offsets.length; i++) {
            if (offsets[i] >= charFrom) {
                return i;
            }
        }
        return offsets.length - 1;
    }

    private static int charPos(int cpIndex, int[] offsets) {
        return cpIndex < offsets.length ? offsets[cpIndex] : offsets[offsets.length - 1];
    }
}
