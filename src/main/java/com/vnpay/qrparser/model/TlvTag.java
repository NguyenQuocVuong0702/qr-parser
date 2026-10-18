package com.vnpay.qrparser.model;

/** Lưu tag TLV cấp 1; length là số Unicode code point trong value. */
public record TlvTag(String tag, int length, String value) {
}
