package com.vnpay.qrparser.api;

import com.vnpay.qrparser.api.dto.QrAnalyzeRequest;
import com.vnpay.qrparser.api.dto.QrAnalyzeResponse;
import com.vnpay.qrparser.model.QrValidationResult;
import com.vnpay.qrparser.validation.QrValidationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
public class QrAnalyzeController {

    private final QrValidationService validationService;

    public QrAnalyzeController(QrValidationService validationService) {
        this.validationService = validationService;
    }

    @PostMapping(value = "/api/qr/analyze", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<QrAnalyzeResponse> analyze(@RequestBody(required = false) QrAnalyzeRequest request) {
        if (request == null) {
            return invalidRequest("REQUEST_BODY_REQUIRED", "Request body không được để trống.");
        }
        if (request.body().isNull()) {
            return invalidRequest("REQUEST_BODY_NULL", "Request body phải là object JSON, không được là null.");
        }
        if (!request.body().isObject()) {
            return invalidRequest("REQUEST_BODY_MUST_BE_OBJECT", "Request body phải là object JSON chứa thuộc tính qr.");
        }

        JsonNode qr = request.qr();
        if (qr == null) {
            return invalidRequest("QR_REQUIRED", "Request body thiếu thuộc tính qr.");
        }
        if (!qr.isNull() && !qr.isString()) {
            return invalidRequest("QR_MUST_BE_STRING", "Thuộc tính qr phải là chuỗi JSON hoặc null.");
        }

        // Null hoặc chuỗi được chuyển nguyên vẹn vào service, không trim hay sửa nội dung.
        QrValidationResult result = validationService.validate(qr.isNull() ? null : qr.asString());
        HttpStatus httpStatus = switch (result.status()) {
            case VALID -> HttpStatus.OK;
            case INVALID_INPUT -> HttpStatus.BAD_REQUEST;
            case INVALID_STRUCTURE, CRC_MISMATCH, SPEC_VIOLATION -> HttpStatus.UNPROCESSABLE_CONTENT;
        };
        return ResponseEntity.status(httpStatus).body(QrAnalyzeResponse.from(result));
    }

    private ResponseEntity<QrAnalyzeResponse> invalidRequest(String code, String message) {
        return ResponseEntity.badRequest().body(QrAnalyzeResponse.failure("INVALID_REQUEST", code, message));
    }
}
