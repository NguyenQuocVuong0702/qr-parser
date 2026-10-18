package com.vnpay.qrparser.model;

/** Offset là vị trí header liên quan, tính theo UTF-16 của Java; null nếu chưa xác định. */
public record QrValidationError(
        Code code,
        String tagPath,
        Integer parentValueOffset,
        Integer absoluteOffset,
        String message) {

    public enum Code {
        EMPTY_INPUT,
        EMPTY_TEMPLATE,
        INCOMPLETE_HEADER,
        NON_NUMERIC_LENGTH,
        TRUNCATED_VALUE,
        INVALID_UNICODE,
        INVALID_TAG_ID,
        CRC_STRUCTURE_ERROR,
        CRC_MISSING,
        CRC_DUPLICATED,
        CRC_INVALID_POSITION,
        CRC_INVALID_LENGTH,
        CRC_INVALID_FORMAT,
        CRC_MISMATCH,
        MISSING_REQUIRED_TAG,
        INVALID_FIELD_LENGTH,
        INVALID_FIELD_VALUE,
        INVALID_INITIATION_RULE
    }
}
