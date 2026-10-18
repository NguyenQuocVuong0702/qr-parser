package com.vnpay.qrparser.model;

/** Kết quả kiểm tra CRC; các giá trị CRC chưa xác định được dùng chuỗi rỗng. */
public record CrcValidationResult(
        CrcValidationStatus status,
        String providedCrc,
        String computedCrc,
        String message,
        QrValidationError.Code errorCode) {

    // Giữ constructor cũ cho code đã dùng kết quả CRC ở các giai đoạn trước.
    public CrcValidationResult(CrcValidationStatus status, String providedCrc,
                                String computedCrc, String message) {
        this(status, providedCrc, computedCrc, message,
                status == CrcValidationStatus.MISMATCH ? QrValidationError.Code.CRC_MISMATCH
                        : status == CrcValidationStatus.STRUCTURE_ERROR
                        ? QrValidationError.Code.CRC_STRUCTURE_ERROR : null);
    }

    public boolean isValid() {
        return status == CrcValidationStatus.VALID;
    }
}
