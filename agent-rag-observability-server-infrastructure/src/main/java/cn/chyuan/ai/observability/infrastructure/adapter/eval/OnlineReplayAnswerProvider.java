package cn.chyuan.ai.observability.infrastructure.adapter.eval;

import cn.chyuan.ai.observability.domain.evaluate.adapter.port.IAnswerSourceProvider;
import cn.chyuan.ai.observability.domain.evaluate.model.valobj.AnswerSample;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 在线回放答案来源 Provider — 直接调用上游 Agent 的 /api/v1/chat 接口获取答案。
 * 用于评测时实时生成答案，适合测试最新模型版本或 RAG 策略。
 */
@Slf4j
@Component("onlineReplayAnswerProvider")
public class OnlineReplayAnswerProvider implements IAnswerSourceProvider {

    private final OkHttpClient httpClient;

    @Value("${observability.eval.provider.online.endpoint:http://127.0.0.1:8091/api/v1/chat}")
    private String endpoint;

    @Value("${observability.eval.provider.online.timeout:30}")
    private int timeoutSeconds;

    @Value("${observability.eval.provider.online.agent-id:200002}")
    private String defaultAgentId;

    public OnlineReplayAnswerProvider() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(600, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public AnswerSample fetch(String query, String agentId) {
        String targetAgentId = agentId != null && !agentId.isEmpty() ? agentId : defaultAgentId;
        String traceId = UUID.randomUUID().toString().replace("-", "");

        try {
            // 构建请求 JSON
            JSONObject requestJson = new JSONObject();
            requestJson.put("agentId", targetAgentId);
            requestJson.put("sessionId", "eval-" + traceId);
            requestJson.put("message", query);
            requestJson.put("stream", false);

            Request request = new Request.Builder()
                    .url(endpoint)
                    .post(RequestBody.create(requestJson.toJSONString(), MediaType.parse("application/json")))
                    .addHeader("Content-Type", "application/json")
                    .build();

            long start = System.currentTimeMillis();
            Response response = httpClient.newCall(request).execute();
            long costMs = System.currentTimeMillis() - start;

            if (!response.isSuccessful()) {
                log.warn("在线回放失败, query={}, code={}", query, response.code());
                return null;
            }

            String responseBody = response.body() != null ? response.body().string() : "";
            // 使用 fastjson 解析 Response<T> 格式
            String actualAnswer = parseAnswer(responseBody);
            List<String> chunks = parseChunks(responseBody);

            log.debug("在线回放成功, query={}, costMs={}", query, costMs);
            return AnswerSample.builder()
                    .traceId(traceId)
                    .actualAnswer(actualAnswer)
                    .retrievedChunks(chunks)
                    .build();
        } catch (IOException e) {
            log.warn("在线回放异常, query={}, err={}", query, e.getMessage());
            return null;
        }
    }

    /**
     * 解析答案文本 - 使用 fastjson 健壮解析
     */
    private String parseAnswer(String responseBody) {
        try {
            JSONObject json = JSON.parseObject(responseBody);
            // 检查是否为标准信封格式 { code: "0000", data: {...} }
            if (json.containsKey("code")) {
                JSONObject data = json.getJSONObject("data");
                if (data != null) {
                    // 优先 answerText，降级 content（ChatResponseDTO 实际字段为 content）
                    String answer = data.getString("answerText");
                    if (answer == null || answer.isEmpty()) {
                        answer = data.getString("content");
                    }
                    if (answer != null) return answer;
                }
            }
            // 直接格式
            String answer = json.getString("answerText");
            if (answer == null || answer.isEmpty()) {
                answer = json.getString("content");
            }
            return answer == null ? "" : answer;
        } catch (Exception e) {
            log.warn("解析答案失败, err={}", e.getMessage());
            return "";
        }
    }

    /**
     * 解析检索 chunks - 使用 fastjson 健壮解析
     */
    private List<String> parseChunks(String responseBody) {
        try {
            JSONObject json = JSON.parseObject(responseBody);
            JSONObject data;
            if (json.containsKey("code")) {
                data = json.getJSONObject("data");
            } else {
                data = json;
            }

            if (data == null || !data.containsKey("sourceDocs")) {
                return new ArrayList<>();
            }

            Object sourceDocsObj = data.get("sourceDocs");
            if (sourceDocsObj instanceof String) {
                // sourceDocs 是 JSON 字符串
                return parseSourceDocsString((String) sourceDocsObj);
            } else if (sourceDocsObj instanceof List) {
                // sourceDocs 是已解析的数组
                List<String> chunks = new ArrayList<>();
                for (Object item : (List<?>) sourceDocsObj) {
                    if (item instanceof JSONObject) {
                        JSONObject chunk = (JSONObject) item;
                        String content = chunk.getString("snippet");
                        if (content == null || content.isEmpty()) {
                            content = chunk.getString("content");
                        }
                        if (content != null && !content.isEmpty()) {
                            chunks.add(content);
                        }
                    }
                }
                return chunks;
            }
            return new ArrayList<>();
        } catch (Exception e) {
            log.warn("解析 sourceDocs 失败, err={}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 解析 sourceDocs JSON 字符串
     */
    private List<String> parseSourceDocsString(String sourceDocsJson) {
        try {
            com.alibaba.fastjson.JSONArray arr = JSON.parseArray(sourceDocsJson);
            List<String> chunks = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String content = obj.getString("snippet");
                if (content == null || content.isEmpty()) {
                    content = obj.getString("content");
                }
                if (content != null && !content.isEmpty()) {
                    chunks.add(content);
                }
            }
            return chunks;
        } catch (Exception e) {
            log.warn("解析 sourceDocs 字符串失败, err={}", e.getMessage());
            return new ArrayList<>();
        }
    }
}
