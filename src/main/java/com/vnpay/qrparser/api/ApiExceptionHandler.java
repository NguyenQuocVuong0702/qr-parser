package com.vnpay.qrparser.api;

import com.vnpay.qrparser.api.dto.QrAnalyzeResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<QrAnalyzeResponse> invalidJson(HttpMessageNotReadableException exception) {
        // Không trả exception.getMessage(): Jackson có thể đưa nội dung request vào đó.
        return ResponseEntity.badRequest().body(QrAnalyzeResponse.failure("INVALID_REQUEST",
                "INVALID_JSON", "Request body không phải JSON hợp lệ."));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<QrAnalyzeResponse> unsupportedMediaType(HttpMediaTypeNotSupportedException exception) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(QrAnalyzeResponse.failure(
                "INVALID_REQUEST", "UNSUPPORTED_MEDIA_TYPE", "Content-Type phải là application/json."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<QrAnalyzeResponse> handleException(Exception exception) {
        // Giữ HTTP status của lỗi framework, ví dụ 405 khi dùng sai HTTP method.
        if (exception instanceof ErrorResponse error) {
            return ResponseEntity.status(error.getStatusCode()).headers(error.getHeaders())
                    .body(QrAnalyzeResponse.failure("INVALID_REQUEST", "HTTP_REQUEST_ERROR",
                            "HTTP request không được hỗ trợ."));
        }
        // Chỉ ghi mã và loại lỗi; không ghi message, cause, QR hoặc stack trace.
        LOGGER.error("QR_ANALYZE_FAILED code=INTERNAL_ERROR exceptionType={}",
                exception.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(QrAnalyzeResponse.failure(
                "INTERNAL_ERROR", "INTERNAL_ERROR", "Không thể xử lý yêu cầu lúc này."));
    }
}
