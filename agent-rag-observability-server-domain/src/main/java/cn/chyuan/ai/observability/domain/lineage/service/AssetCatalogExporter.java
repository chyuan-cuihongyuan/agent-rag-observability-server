package cn.chyuan.ai.observability.domain.lineage.service;

import cn.chyuan.ai.observability.domain.lineage.service.AssetRegistry.AssetEntity;
import cn.chyuan.ai.observability.domain.lineage.service.LineageGraphOps.LineageEdge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 资产目录导出（工单 0292 AK8，借鉴 dbt docs 思想）—
 * 全量资产+血缘边+健康分+schema 时间线摘要 → catalog.json（确定性键序）+ 简版 HTML（零依赖字符串模板）。
 */
public final class AssetCatalogExporter {

    /** 导出产物 */
    public record Catalog(String json, String html, int assets, int edges) {
    }

    private AssetCatalogExporter() {
    }

    /** 组装 catalog JSON（确定性键序：资产按 URN 排序） */
    public static String buildJson(List<AssetEntity> assets, List<LineageEdge> edges,
            Map<String, AssetHealthCalculator.AssetHealth> health) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"assets\": [\n");
        List<AssetEntity> sortedAssets = new ArrayList<>(assets);
        sortedAssets.sort((a, b) -> a.urn().compareTo(b.urn()));
        for (int i = 0; i < sortedAssets.size(); i++) {
            AssetEntity asset = sortedAssets.get(i);
            AssetHealthCalculator.AssetHealth assetHealth = health.get(asset.urn());
            sb.append("    {\"urn\":\"").append(asset.urn()).append('"')
                    .append(",\"displayName\":\"").append(escape(asset.displayName())).append('"')
                    .append(",\"owner\":\"").append(escape(asset.owner())).append('"');
            if (assetHealth != null) {
                sb.append(",\"health\":{").append("\"score\":").append(assetHealth.totalScore())
                        .append(",\"grade\":\"").append(assetHealth.grade()).append("\"}");
            }
            sb.append("}");
            sb.append(i < sortedAssets.size() - 1 ? ",\n" : "\n");
        }
        sb.append("  ],\n  \"edges\": [\n");
        List<LineageEdge> sortedEdges = new ArrayList<>(edges);
        sortedEdges.sort((a, b) -> (a.fromUrn() + a.toUrn()).compareTo(b.fromUrn() + b.toUrn()));
        for (int i = 0; i < sortedEdges.size(); i++) {
            LineageEdge edge = sortedEdges.get(i);
            sb.append("    {\"from\":\"").append(edge.fromUrn()).append("\",\"to\":\"")
                    .append(edge.toUrn()).append("\",\"source\":\"").append(edge.source()).append("\"}");
            sb.append(i < sortedEdges.size() - 1 ? ",\n" : "\n");
        }
        sb.append("  ]\n}\n");
        return sb.toString();
    }

    /** 简版 HTML（零依赖字符串模板；健康分级着色） */
    public static String buildHtml(List<AssetEntity> assets, Map<String, AssetHealthCalculator.AssetHealth> health) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html><html><head><meta charset=\"utf-8\"><title>资产目录</title></head><body>\n");
        sb.append("<h1>数据资产目录</h1>\n<table border=\"1\" cellpadding=\"4\">\n");
        sb.append("<tr><th>URN</th><th>名称</th><th>负责人</th><th>健康</th></tr>\n");
        List<AssetEntity> sortedAssets = new ArrayList<>(assets);
        sortedAssets.sort((a, b) -> a.urn().compareTo(b.urn()));
        for (AssetEntity asset : sortedAssets) {
            AssetHealthCalculator.AssetHealth assetHealth = health.get(asset.urn());
            sb.append("<tr><td>").append(escape(asset.urn())).append("</td><td>")
                    .append(escape(asset.displayName())).append("</td><td>")
                    .append(escape(asset.owner())).append("</td><td>")
                    .append(assetHealth == null ? "-" : assetHealth.grade() + " (" + assetHealth.totalScore() + ")")
                    .append("</td></tr>\n");
        }
        sb.append("</table>\n</body></html>\n");
        return sb.toString();
    }

    /** 组装完整 catalog */
    public static Catalog export(List<AssetEntity> assets, List<LineageEdge> edges,
            Map<String, AssetHealthCalculator.AssetHealth> health) {
        String json = buildJson(assets, edges, health);
        String html = buildHtml(assets, health);
        return new Catalog(json, html, assets.size(), edges.size());
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
