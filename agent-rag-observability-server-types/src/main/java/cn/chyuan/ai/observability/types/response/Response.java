package cn.chyuan.ai.observability.types.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Response<T> {
    private String code;
    private String info;
    private T data;

    public static <T> Response<T> success(T data) {
        return Response.<T>builder().code("0000").info("成功").data(data).build();
    }

    public static <T> Response<T> success() {
        return Response.<T>builder().code("0000").info("成功").build();
    }

    public static <T> Response<T> fail(String code, String info) {
        return Response.<T>builder().code(code).info(info).build();
    }
}
