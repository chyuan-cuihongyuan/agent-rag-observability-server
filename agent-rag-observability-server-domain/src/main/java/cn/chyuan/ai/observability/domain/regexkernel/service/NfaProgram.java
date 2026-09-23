package cn.chyuan.ai.observability.domain.regexkernel.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Thompson NFA 程序（工单 0634 BX1，Thompson 构造思想）。
 * AST→NFA 状态片段拼接（ε 边 Split/Jmp + 码点类边 + Save 捕获 + 锚点断言）。
 * 贪婪与懒惰由 Split 目标优先序表达（先走贪婪支=贪婪；先走退出支=懒惰）。
 */
public final class NfaProgram {

    /** 指令 */
    public sealed interface Instr permits IClass, ISplit, IJmp, ISave, IBol, IEol, IMatch {
    }

    /** 码点类边（区间集合可否定） */
    public record IClass(List<CharClasses.Range> ranges, boolean negated) implements Instr {
    }

    /** 分裂：优先走 x */
    public record ISplit(int x, int y) implements Instr {
    }

    /** 跳转 */
    public record IJmp(int x) implements Instr {
    }

    /** 捕获槽保存 */
    public record ISave(int slot) implements Instr {
    }

    /** 行首/串首断言（非多行：串首） */
    public record IBol() implements Instr {
    }

    /** 串尾断言 */
    public record IEol() implements Instr {
    }

    /** 匹配成功 */
    public record IMatch() implements Instr {
    }

    private final List<Instr> instrs = new ArrayList<>();
    private final int groupCount;

    private NfaProgram(int groupCount) {
        this.groupCount = groupCount;
    }

    /** 从 AST 编译（外层包 Save(0)/Save(1) 全匹配捕获） */
    public static NfaProgram compile(RegexParser.Ast ast, int groupCount) {
        NfaProgram prog = new NfaProgram(groupCount);
        prog.emit(new ISave(0));
        prog.emitNode(ast);
        prog.emit(new ISave(1));
        prog.emit(new IMatch());
        RegexSafety.checkProgramSize(prog.instrs.size());
        return prog;
    }

    public int size() {
        return instrs.size();
    }

    public Instr at(int pc) {
        return instrs.get(pc);
    }

    public int groupCount() {
        return groupCount;
    }

    private int emit(Instr instr) {
        instrs.add(instr);
        return instrs.size() - 1;
    }

    private void emitNode(RegexParser.Ast node) {
        if (node instanceof RegexParser.CharNode ch) {
            emit(new IClass(List.of(new CharClasses.Range(ch.codePoint(), ch.codePoint())), false));
        } else if (node instanceof RegexParser.ClassNode cls) {
            emit(new IClass(cls.ranges(), cls.negated()));
        } else if (node instanceof RegexParser.Group group) {
            if (group.index() == 0) {
                emitNode(group.body());
            } else {
                emit(new ISave(2 * group.index()));
                emitNode(group.body());
                emit(new ISave(2 * group.index() + 1));
            }
        } else if (node instanceof RegexParser.Cat cat) {
            for (RegexParser.Ast part : cat.parts()) {
                emitNode(part);
            }
        } else if (node instanceof RegexParser.Alt alt) {
            List<RegexParser.Ast> branches = alt.branches();
            emitAlt(branches, 0);
        } else if (node instanceof RegexParser.Anchor anchor) {
            emit(anchor.start() ? new IBol() : new IEol());
        } else if (node instanceof RegexParser.Empty) {
            // 空段无指令
        } else if (node instanceof RegexParser.Rep rep) {
            emitRep(rep);
        }
    }

    private void emitAlt(List<RegexParser.Ast> branches, int index) {
        if (index == branches.size() - 1) {
            emitNode(branches.get(index));
            return;
        }
        int splitAt = emit(new ISplit(-1, -1));
        instrs.set(splitAt, new ISplit(splitAt + 1, -1));
        emitNode(branches.get(index));
        int jmpAt = emit(new IJmp(-1));
        int nextAlt = instrs.size();
        emitAlt(branches, index + 1);
        int end = instrs.size();
        instrs.set(splitAt, new ISplit(splitAt + 1, nextAlt));
        instrs.set(jmpAt, new IJmp(end));
    }

    /** 重复编译：min 份强制 +（有界差值嵌套可选 / 无界星） */
    private void emitRep(RegexParser.Rep rep) {
        for (int i = 0; i < rep.min(); i++) {
            emitNode(rep.node());
        }
        if (rep.max() == -1) {
            int splitAt = emit(new ISplit(-1, -1));
            emitNode(rep.node());
            emit(new IJmp(splitAt));
            int after = instrs.size();
            instrs.set(splitAt, new ISplit(rep.lazy() ? after : splitAt + 1,
                    rep.lazy() ? splitAt + 1 : after));
        } else {
            int optional = rep.max() - rep.min();
            int[] splits = new int[optional];
            for (int i = 0; i < optional; i++) {
                splits[i] = emit(new ISplit(-1, -1));
                emitNode(rep.node());
            }
            int after = instrs.size();
            for (int splitAt : splits) {
                instrs.set(splitAt, new ISplit(rep.lazy() ? after : splitAt + 1,
                        rep.lazy() ? splitAt + 1 : after));
            }
        }
    }
}
