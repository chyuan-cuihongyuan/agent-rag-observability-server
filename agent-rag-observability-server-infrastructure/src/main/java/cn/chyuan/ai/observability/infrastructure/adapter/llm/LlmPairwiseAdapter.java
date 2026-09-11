package cn.chyuan.ai.observability.infrastructure.adapter.llm;

import cn.chyuan.ai.observability.domain.evaluate.adapter.port.ILlmPairwisePort;
import cn.chyuan.ai.observability.domain.evaluate.model.valobj.PairwiseOutcome;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * LLM pairwise 对比判定适配器（工单 0170 X1）— 复用 LLM judge 的 OpenAI 兼容端点配置；
 * 挂点在 pairwise 服务内，异常/无法解析时返回 null 回退规则兜底（由调用方处理）。
 * 开关：eval.pairwise-provider=llm 时装配并 @Primary 覆盖规则占位实现。
 */
@Slf4j
@Component
@Primary
@ConditionalOnProperty(name = "eval.pairwise-provider", havingValue = "llm")
public class LlmPairwiseAdapter implements ILlmPairwisePort {

    private final OkHttpClient httpClient;

    @Value("${llm.judge.base-url:}")
    private String baseUrl;

    @Value("${llm.judge.api-key:}")
    private String apiKey;

    @Value("${llm.judge.model:}")
    private String model;

    public LlmPairwiseAdapter() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public PairwiseOutcome judge(String query, String answerA, String answerB) {
        try {
            String prompt = "你是严格的评审。对同一问题的两个回答做对比，只输出一个词：A_WIN、B_WIN 或 TIE。\n"
                    + "问题：" + query + "\n回答A：" + answerA + "\n回答B：" + answerB;
            JSONObject req = new JSONObject();
            req.put("model", model);
            req.put("messages", java.util.List.of(JSONObject.parseObject("{\"role\":\"user\",\"content\":" + JSON.toJSONString(prompt) + "}")));
            req.put("temperature", 0);

            Request request = new Request.Builder()
                    .url(baseUrl + "/chat/completions")
                    .post(RequestBody.create(req.toJSONString(), MediaType.parse("application/json")))
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.warn("LLM pairwise 调用失败: code={}", response.code());
                    return null;
                }
                String text = JSON.parseObject(response.body().string())
                        .getJSONArray("choices").getJSONObject(0)
                        .getJSONObject("message").getString("content");
                return PairwiseOutcome.fromCode(text == null ? "" : text.trim().toUpperCase());
            }
        } catch (Exception e) {
            log.warn("LLM pairwise 异常（返回 null，由服务层规则兜底）: {}", e.getMessage());
            return null;
        }
    }
}
