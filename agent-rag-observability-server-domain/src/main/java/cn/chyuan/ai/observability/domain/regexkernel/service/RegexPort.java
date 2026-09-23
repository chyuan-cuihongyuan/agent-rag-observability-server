package cn.chyuan.ai.observability.domain.regexkernel.service;

import java.util.ArrayList;
import java.util.List;

/**
 * 正则端口+组合管线（工单 0641 BX8）。
 * RegexPort（模式编译→匹配/组提取/替换/findAll）组合管线：
 * 安全校验→解析→NFA→Pike VM·惰性 DFA 双引擎一致→捕获替换；
 * 与 logkernel 只读联动（LogQL regexp 管道阶段可切换匹配器可选形态，
 * 泛型入参不 import logkernel，不改任何类）/
 * regex-kernel.enabled 默认关（开启才改变行为）。
 */
public interface RegexPort {

    /** 编译产物：双引擎 + 命名索引 */
    final class Compiled {
        private final NfaProgram prog;
        private final PikeVm vm;
        private final LazyDfa dfa;
        private final CaptureGroups groups;

        Compiled(NfaProgram prog, PikeVm vm, LazyDfa dfa, CaptureGroups groups) {
            this.prog = prog;
            this.vm = vm;
            this.dfa = dfa;
            this.groups = groups;
        }

        /** 整串匹配（锚定起点，全长校验） */
        public boolean matches(String input) {
            if (input == null) {
                throw new IllegalArgumentException("输入不得为 null");
            }
            PikeVm.Match match = vm.search(input, 0);
            return match.present() && match.end() == input.length();
        }

        /** 包含匹配（惰性 DFA 快路径，超限回退 VM；双引擎一致口径） */
        public boolean contains(String input) {
            try {
                boolean byDfa = dfa.matches(input);
                boolean byVm = vm.search(input, 0).present();
                if (byDfa != byVm) {
                    throw new IllegalStateException("双引擎结果不一致（DFA=" + byDfa + "）");
                }
                return byVm;
            } catch (LazyDfa.FallbackSignal e) {
                return vm.search(input, 0).present();
            }
        }

        /** 首个匹配（最左优先） */
        public PikeVm.Match find(String input) {
            return vm.search(input, 0);
        }

        /** 全部非重叠匹配 */
        public List<PikeVm.Match> findAll(String input) {
            List<PikeVm.Match> out = new ArrayList<>();
            int from = 0;
            while (from <= input.length()) {
                PikeVm.Match match = vm.search(input, from);
                if (!match.present()) {
                    break;
                }
                out.add(match);
                int next = match.end() == match.start() ? match.end() + 1 : match.end();
                if (next > input.length()) {
                    break;
                }
                from = next;
            }
            return out;
        }

        /** 替换全部（模板 $1·${name}；非重叠遍历，空匹配跳一字符） */
        public String replaceAll(String input, String template) {
            StringBuilder out = new StringBuilder();
            int from = 0;
            while (from <= input.length()) {
                PikeVm.Match match = vm.search(input, from);
                if (!match.present()) {
                    break;
                }
                out.append(input, from, match.start());
                out.append(groups.expand(template, input, match));
                if (match.end() == match.start()) {
                    if (match.end() < input.length()) {
                        out.append(input.charAt(match.end()));
                    }
                    from = match.end() + 1;
                } else {
                    from = match.end();
                    if (from >= input.length()) {
                        break;
                    }
                }
            }
            out.append(input, Math.min(from, input.length()), input.length());
            return out.toString();
        }

        public CaptureGroups groups() {
            return groups;
        }

        public int programSize() {
            return prog.size();
        }

        public int dfaCachedStates() {
            return dfa.cachedStates();
        }

    }

    /** 编译（安全上限校验） */
    Compiled compile(String pattern);

    /** 与 logkernel 只读联动：LogQL regexp 阶段行匹配形态 */
    boolean lineMatches(String pattern, String line);

    /** 内存假实现：解析→NFA→双引擎全链 */
    class InMemoryRegex implements RegexPort {

        @Override
        public synchronized Compiled compile(String pattern) {
            RegexParser.Ast ast = RegexParser.parse(pattern);
            int groupCount = countGroups(pattern);
            NfaProgram prog = NfaProgram.compile(ast, groupCount);
            return new Compiled(prog, new PikeVm(prog), new LazyDfa(prog, 4096),
                    CaptureGroups.fromPattern(pattern));
        }

        @Override
        public synchronized boolean lineMatches(String pattern, String line) {
            if (pattern == null || line == null) {
                throw new IllegalArgumentException("模式与行不得为 null");
            }
            return compile(pattern).find(line).present();
        }

        private int countGroups(String pattern) {
            int count = 0;
            for (int i = 0; i < pattern.length(); i++) {
                char c = pattern.charAt(i);
                if (c == '\\') {
                    i++;
                } else if (c == '[') {
                    i = skipClass(pattern, i);
                } else if (c == '(') {
                    if (pattern.startsWith("?:", i + 1)) {
                        i++;
                    } else {
                        count++;
                    }
                }
            }
            return count;
        }

        private int skipClass(String pattern, int open) {
            int i = open + 1;
            if (i < pattern.length() && pattern.charAt(i) == '^') {
                i++;
            }
            if (i < pattern.length() && pattern.charAt(i) == ']') {
                i++;
            }
            while (i < pattern.length() && pattern.charAt(i) != ']') {
                if (pattern.charAt(i) == '\\') {
                    i++;
                }
                i++;
            }
            return Math.min(i, pattern.length() - 1);
        }
    }
}
