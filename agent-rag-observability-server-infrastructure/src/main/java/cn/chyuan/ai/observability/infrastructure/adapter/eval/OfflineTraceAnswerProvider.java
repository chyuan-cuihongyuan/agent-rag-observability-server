package cn.chyuan.ai.observability.infrastructure.adapter.eval;

import cn.chyuan.ai.observability.domain.evaluate.adapter.port.IAnswerSourceProvider;
import cn.chyuan.ai.observability.domain.evaluate.model.valobj.AnswerSample;
import cn.chyuan.ai.observability.domain.observe.adapter.repository.IChatResultRepository;
import cn.chyuan.ai.observability.domain.observe.adapter.repository.IRagRetrievalRepository;
import cn.chyuan.ai.observability.domain.observe.model.entity.ChatResultEntity;
import cn.chyuan.ai.observability.domain.observe.model.entity.RagRetrievalEntity;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 离线复用答案来源 Provider — 从已采集的 Trace 数据中提取答案和检索内容。
 * 用于评测已运行过的历史版本，无需重新调用 LLM。
 */
@Slf4j
@Component("offlineTraceAnswerProvider")
public class OfflineTraceAnswerProvider implements IAnswerSourceProvider {

    @Resource
    private IChatResultRepository chatResultRepository;

    @Resource
    private IRagRetrievalRepository ragRetrievalRepository;

    @Value("${observability.eval.provider.offline.enabled:false}")
    private boolean enabled;

    /**
     * 从历史 Trace 中查找最匹配的答案。
     * 策略：使用 ES match query 查找 question 字段相似的记录，取最新的一条。
     */
    @Override
    public AnswerSample fetch(String query, String agentId) {
        if (!enabled) {
            log.debug("离线复用未启用");
            return null;
        }

        try {
            List<ChatResultEntity> matches = chatResultRepository.queryByQuestion(query, 1);
            if (matches.isEmpty()) {
                log.debug("未找到匹配的历史答案, query={}", query);
                return null;
            }

            ChatResultEntity chat = matches.get(0);
            List<String> chunks = retrieveChunks(chat.getTraceId());

            log.debug("离线复用命中, query={}, traceId={}", query, chat.getTraceId());
            return AnswerSample.builder()
                    .traceId(chat.getTraceId())
                    .actualAnswer(chat.getAnswer())
                    .retrievedChunks(chunks)
                    .build();
        } catch (Exception e) {
            log.warn("离线复用失败, query={}, err={}", query, e.getMessage());
            return null;
        }
    }

    /**
     * 从 RAG 检索记录中提取检索到的 chunk 内容。
     */
    private List<String> retrieveChunks(String traceId) {
        RagRetrievalEntity retrieval = ragRetrievalRepository.queryByTraceId(traceId);
        if (retrieval == null) {
            return List.of();
        }
        return parseSourceDocs(retrieval.getSourceDocs());
    }

    /**
     * 解析 sourceDocs JSON 字符串为 chunk 列表。
     */
    private List<String> parseSourceDocs(String sourceDocsJson) {
        if (sourceDocsJson == null || sourceDocsJson.isEmpty()) {
            return List.of();
        }
        try {
            com.alibaba.fastjson.JSONArray arr = com.alibaba.fastjson.JSONArray.parseArray(sourceDocsJson);
            List<String> chunks = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) {
                com.alibaba.fastjson.JSONObject obj = arr.getJSONObject(i);
                String content = obj.getString("content");
                if (content != null && !content.isEmpty()) {
                    chunks.add(content);
                }
            }
            return chunks;
        } catch (Exception e) {
            log.warn("解析 sourceDocs 失败, err={}", e.getMessage());
            return List.of();
        }
    }
}
