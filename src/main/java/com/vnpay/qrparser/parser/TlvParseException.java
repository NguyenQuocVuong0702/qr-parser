package com.vnpay.qrparser.parser;

import com.vnpay.qrparser.model.QrValidationError;

/** Giữ thông báo cũ, đồng thời cung cấp mã lỗi và offset có cấu trúc. */
public class TlvParseException extends IllegalArgumentException {

    private final QrValidationError error;

    public TlvParseException(QrValidationError.Code code, String tagPath,
                             Integer absoluteOffset, String message) {
        this(new QrValidationError(code, tagPath, null, absoluteOffset, message), null);
    }

    public TlvParseException(QrValidationError error, Throwable cause) {
        super(error.message(), cause);
        this.error = error;
    }

    public QrValidationError error() {
        return error;
    }
}
