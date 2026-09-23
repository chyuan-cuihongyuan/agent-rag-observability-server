package cn.chyuan.ai.observability.domain.regexkernel.service;

import static cn.chyuan.ai.observability.domain.regexkernel.service.NfaProgram.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 惰性 DFA（工单 0637 BX4，ripgrep lazy DFA 思想）。
 * 子集构造按需确定化/NFA 状态集→DFA 状态/转移缓存（状态×输入码点）/
 * 缓存上限可配置超限弃用回退 Pike VM/匹配结果与 Pike VM 一致断言口径。
 */
public final class LazyDfa {

    /** DFA 状态：码点集合（排序后作键） */
    private static final class DfaState {
        final int[] pcs;
        final boolean accept;

        DfaState(int[] pcs, boolean accept) {
            this.pcs = pcs;
            this.accept = accept;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof DfaState other && Arrays.equals(pcs, other.pcs);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(pcs);
        }
    }

    private final NfaProgram prog;
    private final int cacheLimit;
    private final Map<DfaState, Map<Integer, DfaState>> cache = new HashMap<>();

    public LazyDfa(NfaProgram prog, int cacheLimit) {
        if (cacheLimit <= 0) {
            throw new IllegalArgumentException("缓存上限须为正");
        }
        this.prog = prog;
        this.cacheLimit = cacheLimit;
    }

    public int cachedStates() {
        return cache.size();
    }

    /** 布尔匹配（起位自左向右，找到即真）；缓存超限抛 Fallback 信号 */
    public boolean matches(String input) {
        int[] cps = new int[input.codePointCount(0, input.length())];
        int idx = 0;
        for (int i = 0; i < input.length(); ) {
            int cp = input.codePointAt(i);
            cps[idx++] = cp;
            i += Character.charCount(cp);
        }
        for (int start = 0; start <= cps.length; start++) {
            if (runFrom(cps, start)) {
                return true;
            }
        }
        return false;
    }

    private boolean runFrom(int[] cps, int start) {
        DfaState state = closure(new int[]{0}, start, cps.length);
        for (int pos = start; pos <= cps.length; pos++) {
            if (state.accept) {
                return true;
            }
            if (pos == cps.length) {
                return false;
            }
            int cp = cps[pos];
            DfaState next = transition(state, cp, pos, cps.length);
            if (next == null) {
                throw new FallbackSignal();
            }
            state = next;
        }
        return state.accept;
    }

    private DfaState transition(DfaState state, int cp, int pos, int total) {
        Map<Integer, DfaState> row = cache.computeIfAbsent(state, k -> new HashMap<>());
        DfaState hit = row.get(cp);
        if (hit != null) {
            return hit;
        }
        List<Integer> targets = new ArrayList<>();
        for (int pc : state.pcs) {
            Instr instr = prog.at(pc);
            if (instr instanceof IClass cls && CharClasses.matches(cls.ranges(), cls.negated(), cp)) {
                appendAll(targets, pc + 1);
            }
        }
        if (cache.size() >= cacheLimit) {
            return null;
        }
        DfaState next = closure(toArray(targets), pos + 1, total);
        row.put(cp, next);
        return next;
    }

    private void appendAll(List<Integer> targets, int pc) {
        targets.add(pc);
    }

    /** ε 闭包（Jmp/Split/Save/Bol/Eol 按位条件展开） */
    private DfaState closure(int[] pcs, int pos, int total) {
        List<Integer> out = new ArrayList<>();
        boolean[] seen = new boolean[prog.size()];
        for (int pc : pcs) {
            expand(pc, out, seen, pos, total);
        }
        int[] sorted = toArray(out);
        Arrays.sort(sorted);
        boolean accept = false;
        for (int pc : sorted) {
            if (prog.at(pc) instanceof IMatch) {
                accept = true;
                break;
            }
        }
        return new DfaState(sorted, accept);
    }

    private void expand(int pc, List<Integer> out, boolean[] seen, int pos, int total) {
        if (seen[pc]) {
            return;
        }
        seen[pc] = true;
        switch (prog.at(pc)) {
            case IJmp jmp -> expand(jmp.x(), out, seen, pos, total);
            case ISplit split -> {
                expand(split.x(), out, seen, pos, total);
                expand(split.y(), out, seen, pos, total);
            }
            case ISave save -> expand(pc + 1, out, seen, pos, total);
            case IBol ignored -> {
                if (pos == 0) {
                    expand(pc + 1, out, seen, pos, total);
                }
            }
            case IEol ignored -> {
                if (pos == total) {
                    expand(pc + 1, out, seen, pos, total);
                }
            }
            default -> out.add(pc);
        }
    }

    private static int[] toArray(List<Integer> list) {
        int[] out = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            out[i] = list.get(i);
        }
        return out;
    }

    /** 缓存超限回退信号（调用方回退 Pike VM） */
    public static final class FallbackSignal extends RuntimeException {
    }
}
