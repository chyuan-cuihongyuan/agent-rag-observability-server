package cn.chyuan.ai.observability.domain.tskernel.service;

import java.util.ArrayList;
import java.util.List;

/**
 * 时间戳与值压缩（工单 0480 BF1，prometheus Gorilla 思想）。
 * delta-of-delta 时间戳编码 + Gorilla XOR 浮点值编码/位流往返等价/压缩率统计。
 * 位流以 long[] 承载（纯内存无真实字节序依赖）。
 */
public class DeltaEncoder {

    /** 位流（顺序写入/顺序读取，写读游标独立） */
    public static final class BitStream {
        private final List<Long> words = new ArrayList<>();
        private int bitPos;
        private int readPos;
        private static final int WORD_BITS = 64;

        public void writeBits(long value, int count) {
            if (count <= 0 || count > 64) {
                throw new IllegalArgumentException("写入位数须 1..64: " + count);
            }
            for (int i = count - 1; i >= 0; i--) {
                int wordIndex = bitPos / WORD_BITS;
                while (words.size() <= wordIndex) {
                    words.add(0L);
                }
                long bit = (value >>> i) & 1L;
                if (bit != 0) {
                    int offset = bitPos % WORD_BITS;
                    words.set(wordIndex, words.get(wordIndex) | (1L << (63 - offset)));
                }
                bitPos++;
            }
        }

        public long readBits(int count) {
            if (count <= 0 || count > 64) {
                throw new IllegalArgumentException("读取位数须 1..64: " + count);
            }
            long value = 0;
            for (int i = 0; i < count; i++) {
                int wordIndex = readPos / WORD_BITS;
                int offset = readPos % WORD_BITS;
                long word = wordIndex < words.size() ? words.get(wordIndex) : 0L;
                long bit = (word >>> (63 - offset)) & 1L;
                value |= bit << (count - 1 - i);
                readPos++;
            }
            return value;
        }

        public int bitCount() {
            return bitPos;
        }
    }

    /** 压缩结果：位流 + 统计 */
    public record Encoded(BlockBitStream stream, int sampleCount, double rawBitsPerSample, double compressionRatio) {
    }

    /** 编码承载：时间戳流 + 值流共用一条 */
    public static final class BlockBitStream {
        final BitStream timestamps = new BitStream();
        final BitStream values = new BitStream();
        long firstTimestamp;
        long firstValueBits;
        long previousTimestamp;
        long previousDelta;
        long previousValueBits;
    }

    /** 编码时间戳序列（须升序）与浮点值序列 */
    public Encoded encode(List<Long> timestamps, List<Double> values) {
        if (timestamps.size() != values.size() || timestamps.isEmpty()) {
            throw new IllegalArgumentException("时间戳与值等长且非空");
        }
        BlockBitStream block = new BlockBitStream();
        block.firstTimestamp = timestamps.get(0);
        block.firstValueBits = Double.doubleToLongBits(values.get(0));
        block.previousTimestamp = block.firstTimestamp;
        block.previousValueBits = block.firstValueBits;
        // 时间戳头（首样本 64 位）
        block.timestamps.writeBits(block.firstTimestamp, 64);
        // 值头（首样本 64 位）
        block.values.writeBits(block.firstValueBits, 64);
        for (int i = 1; i < timestamps.size(); i++) {
            long ts = timestamps.get(i);
            if (ts <= block.previousTimestamp) {
                throw new IllegalArgumentException("时间戳须严格升序: " + ts);
            }
            long delta = ts - block.previousTimestamp;
            long dod = i == 1 ? delta : delta - block.previousDelta;
            if (i == 1) {
                block.timestamps.writeBits(delta, 40);
            } else if (dod == 0) {
                block.timestamps.writeBits(0, 1);
            } else if (dod >= -63 && dod <= 64) {
                block.timestamps.writeBits(0b10, 2);
                block.timestamps.writeBits(dod + 63, 7);
            } else {
                block.timestamps.writeBits(0b11, 2);
                block.timestamps.writeBits(dod, 64);
            }
            block.previousDelta = delta;
            block.previousTimestamp = ts;
            // Gorilla XOR 值编码
            long bits = Double.doubleToLongBits(values.get(i));
            long xor = bits ^ block.previousValueBits;
            if (xor == 0) {
                block.values.writeBits(0, 1);
            } else {
                block.values.writeBits(1, 1);
                block.values.writeBits(xor, 64);
            }
            block.previousValueBits = bits;
        }
        int samples = timestamps.size();
        double rawBits = samples * 128.0;
        double usedBits = block.timestamps.bitCount() + block.values.bitCount();
        return new Encoded(block, samples, usedBits / samples, rawBits / usedBits);
    }

    /** 解码往返等价 */
    public DecodeResult decode(Encoded encoded) {
        BlockBitStream block = encoded.stream();
        long firstTs = block.timestamps.readBits(64);
        double firstValue = Double.longBitsToDouble(block.values.readBits(64));
        List<Long> timestamps = new ArrayList<>(List.of(firstTs));
        List<Double> values = new ArrayList<>(List.of(firstValue));
        long previousTs = firstTs;
        long previousDelta = 0;
        long previousBits = Double.doubleToLongBits(firstValue);
        for (int i = 1; i < encoded.sampleCount(); i++) {
            long delta;
            if (i == 1) {
                delta = block.timestamps.readBits(40);
            } else {
                int flag = (int) block.timestamps.readBits(1);
                if (flag == 0) {
                    delta = previousDelta;
                } else {
                    int twoBit = (int) block.timestamps.readBits(1);
                    if (twoBit == 0) {
                        delta = previousDelta + block.timestamps.readBits(7) - 63;
                    } else {
                        delta = previousDelta + block.timestamps.readBits(64);
                    }
                }
            }
            previousDelta = delta;
            previousTs += delta;
            timestamps.add(previousTs);
            long hasXor = block.values.readBits(1);
            long bits = hasXor == 0 ? previousBits : previousBits ^ block.values.readBits(64);
            previousBits = bits;
            values.add(Double.longBitsToDouble(bits));
        }
        return new DecodeResult(timestamps, values);
    }

    /** 解码结果 */
    public record DecodeResult(List<Long> timestamps, List<Double> values) {
    }
}
