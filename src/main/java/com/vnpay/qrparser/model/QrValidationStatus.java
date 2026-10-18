package com.vnpay.qrparser.model;

public enum QrValidationStatus {
    VALID,
    INVALID_INPUT,
    INVALID_STRUCTURE,
    CRC_MISMATCH,
    SPEC_VIOLATION
}
