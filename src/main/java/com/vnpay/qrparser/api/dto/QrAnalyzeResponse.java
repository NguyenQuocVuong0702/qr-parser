package com.vnpay.qrparser.api.dto;

import com.vnpay.qrparser.model.CrcValidationResult;
import com.vnpay.qrparser.model.InterpretedTlvNode;
import com.vnpay.qrparser.model.QrValidationError;
import com.vnpay.qrparser.model.QrValidationResult;

import java.util.List;

/** Hợp đồng JSON của API; không thay đổi các model nghiệp vụ. */
public record QrAnalyzeResponse(
        String status,
        String message,
        List<ApiError> errors,
        CrcDetails crcResult,
        List<InterpretedTlvNode> tags) {

    public QrAnalyzeResponse {
        errors = List.copyOf(errors);
        tags = List.copyOf(tags);
    }

    public static QrAnalyzeResponse from(QrValidationResult result) {
        List<ApiError> errors = result.errors().stream().map(ApiError::from).toList();
        String message = result.message();
        if (!result.errors().isEmpty()
                && result.errors().getFirst().code() == QrValidationError.Code.NON_NUMERIC_LENGTH) {
            // Không phản chiếu phần length sai từ dữ liệu đầu vào vào thông báo HTTP.
            message = safeMessage(result.errors().getFirst());
        }
        return new QrAnalyzeResponse(result.status().name(), message, errors,
                CrcDetails.from(result.crcResult()), result.tags());
    }

    public static QrAnalyzeResponse failure(String status, String code, String message) {
        ApiError error = new ApiError(code, null, null, null, message);
        return new QrAnalyzeResponse(status, message, List.of(error), null, List.of());
    }

    private static String safeMessage(QrValidationError error) {
        return error.code() == QrValidationError.Code.NON_NUMERIC_LENGTH
                ? "Length TLV phải gồm hai chữ số ASCII từ 0 đến 9."
                : error.message();
    }

    public record ApiError(
            String code,
            String tagPath,
            Integer parentValueOffset,
            Integer absoluteOffset,
            String message) {

        private static ApiError from(QrValidationError error) {
            return new ApiError(error.code().name(), error.tagPath(),
                    error.parentValueOffset(), error.absoluteOffset(), safeMessage(error));
        }
    }

    /** Chỉ xuất các trường đã công bố, không thêm thuộc tính từ helper isValid(). */
    public record CrcDetails(
            String status,
            String providedCrc,
            String computedCrc,
            String message,
            String errorCode) {

        private static CrcDetails from(CrcValidationResult result) {
            if (result == null) {
                return null;
            }
            return new CrcDetails(result.status().name(), result.providedCrc(), result.computedCrc(),
                    result.message(), result.errorCode() == null ? null : result.errorCode().name());
        }
    }
}
