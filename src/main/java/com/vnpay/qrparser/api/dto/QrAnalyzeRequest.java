package com.vnpay.qrparser.api.dto;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

/** Giữ kiểu JSON gốc để không tự chuyển số hoặc boolean thành chuỗi QR. */
@JsonDeserialize(using = QrAnalyzeRequest.Deserializer.class)
public record QrAnalyzeRequest(JsonNode body) {

    public JsonNode qr() {
        return body.get("qr");
    }

    public JsonNode type() {
        return body.get("type");
    }

    public static class Deserializer extends ValueDeserializer<QrAnalyzeRequest> {

        @Override
        public QrAnalyzeRequest deserialize(JsonParser parser, DeserializationContext context) {
            return new QrAnalyzeRequest(context.readTree(parser));
        }

        @Override
        public QrAnalyzeRequest getNullValue(DeserializationContext context) {
            // JSON null có DTO chứa NullNode; body thiếu không chạy bộ đọc và DTO là null.
            return new QrAnalyzeRequest(context.getNodeFactory().nullNode());
        }
    }
}
