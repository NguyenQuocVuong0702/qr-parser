package com.vnpay.qrparser.model;

import java.util.List;

/** crcResult là null nếu chưa thể kiểm tra CRC; tags rỗng nếu parsing chưa hoàn tất. */
public record QrValidationResult(
        QrValidationStatus status,
        String message,
        List<QrValidationError> errors,
        CrcValidationResult crcResult,
        List<InterpretedTlvNode> tags) {

    public QrValidationResult {
        errors = List.copyOf(errors);
        tags = List.copyOf(tags);
    }

    public boolean isValid() {
        return status == QrValidationStatus.VALID;
    }
}
