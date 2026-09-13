package cn.chyuan.ai.observability.domain.lineage.service;

/**
 * 资产 URN（工单 0285 AK1，借鉴 DataHub 实体命名）—
 * urn:agent:&lt;type&gt;:&lt;qualifier&gt;；type 白名单 dataset|job|model。
 * 组装/解析/合法性校验纯函数。
 *
 * @author chyuan
 */
public record AssetUrn(String type, String qualifier) {

    public static final String TYPE_DATASET = "dataset";
    public static final String TYPE_JOB = "job";
    public static final String TYPE_MODEL = "model";
    private static final java.util.Set<String> LEGAL_TYPES = java.util.Set.of(TYPE_DATASET, TYPE_JOB, TYPE_MODEL);
    private static final String PREFIX = "urn:agent:";

    public AssetUrn {
        if (!LEGAL_TYPES.contains(type)) {
            throw new IllegalArgumentException("非法资产类型: " + type);
        }
        if (qualifier == null || qualifier.isBlank() || !qualifier.matches("[a-zA-Z0-9._/-]+")) {
            throw new IllegalArgumentException("非法资产限定符: " + qualifier);
        }
    }

    /** 组装 URN 字符串 */
    public String urn() {
        return PREFIX + type + ":" + qualifier;
    }

    /** 解析 URN（非法抛 IllegalArgumentException） */
    public static AssetUrn parse(String urn) {
        if (urn == null || !urn.startsWith(PREFIX)) {
            throw new IllegalArgumentException("URN 需以 " + PREFIX + " 开头: " + urn);
        }
        String[] parts = urn.substring(PREFIX.length()).split(":", 2);
        if (parts.length != 2 || parts[1].isBlank()) {
            throw new IllegalArgumentException("URN 结构非法: " + urn);
        }
        return new AssetUrn(parts[0], parts[1]);
    }
}
