package cn.chyuan.ai.observability.infrastructure.es.index;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class EsIndexInitializer implements CommandLineRunner {

    @Resource
    private ElasticsearchClient esClient;

    private static final Map<String, String> INDICES = Map.of(
            "agent_decision_log", "es-templates/agent_decision_log.json",
            "rag_retrieval_log", "es-templates/rag_retrieval_log.json",
            "chat_result_log", "es-templates/chat_result_log.json"
    );

    @Override
    public void run(String... args) {
        INDICES.forEach(this::createIndexIfAbsent);
    }

    private void createIndexIfAbsent(String indexName, String templatePath) {
        try {
            boolean exists = esClient.indices().exists(ExistsRequest.of(e -> e.index(indexName))).value();
            if (exists) {
                log.debug("ES index {} already exists", indexName);
                return;
            }
            String mapping = readClasspathResource(templatePath);
            if (mapping == null) {
                log.warn("ES template not found: {}", templatePath);
                return;
            }
            esClient.indices().create(CreateIndexRequest.of(c -> c
                    .index(indexName)
                    .withJson(new java.io.StringReader(mapping))
            ));
            log.info("ES index {} created successfully", indexName);
        } catch (ElasticsearchException e) {
            if (e.error().type().equals("resource_already_exists_exception")) {
                log.debug("ES index {} already exists (concurrent creation)", indexName);
            } else {
                log.warn("Failed to create ES index {}: {}", indexName, e.getMessage());
            }
        } catch (Exception e) {
            log.warn("Failed to create ES index {}: {}", indexName, e.getMessage());
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
