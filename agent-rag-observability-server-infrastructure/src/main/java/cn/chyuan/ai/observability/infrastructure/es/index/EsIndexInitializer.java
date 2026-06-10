package cn.chyuan.ai.observability.infrastructure.es.index;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ES 索引初始化器
 * 创建 Composable Index Template 匹配月度索引（如 agent_decision_log-2026.06）
 * 月度索引由 Repository 写入时自动创建，Template 保证 mapping 正确
 */
@Slf4j
@Component
public class EsIndexInitializer implements CommandLineRunner {

    @Resource
    private ElasticsearchClient esClient;

    private static final Map<String, String> TEMPLATES = Map.of(
            "agent_decision_log", "es-templates/agent_decision_log.json",
            "rag_retrieval_log", "es-templates/rag_retrieval_log.json",
            "chat_result_log", "es-templates/chat_result_log.json",
            "tool_call_log", "es-templates/tool_call_log.json",
            "memory_recall_log", "es-templates/memory_recall_log.json"
    );

    @Override
    public void run(String... args) {
        TEMPLATES.forEach(this::createIndexTemplate);
    }

    /**
     * 创建 Composable Index Template
     * 模板名 = index_prefix + "-tpl"，匹配 index_prefix + "-*" 模式
     * 这样月度索引（如 agent_decision_log-2026.06）在首次写入时自动获得正确 mapping
     */
    private void createIndexTemplate(String indexPrefix, String templatePath) {
        String templateName = indexPrefix + "-tpl";
        String indexPattern = indexPrefix + "-*";
        try {
            String mapping = readClasspathResource(templatePath);
            if (mapping == null) {
                log.warn("ES template file not found: {}", templatePath);
                return;
            }
            // 构建 Composable Index Template JSON：把原始 settings+mappings 包装到 template 字段中
            String templateJson = "{\"index_patterns\":[\"" + indexPattern + "\"],\"priority\":200,\"template\":" + mapping + "}";
            esClient.indices().putIndexTemplate(r -> r
                    .name(templateName)
                    .withJson(new java.io.StringReader(templateJson)));
            log.info("ES index template {} created (pattern: {})", templateName, indexPattern);
        } catch (Exception e) {
            log.warn("Failed to create ES index template {}: {}", templateName, e.getMessage());
        }
    }

    private String readClasspathResource(String path) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ClassPathResource(path).getInputStream()))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return null;
        }
    }
}
