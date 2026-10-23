package com.vnpay.qrparser.support;

import com.vnpay.qrparser.crc.Crc16Calculator;
import com.vnpay.qrparser.model.QrType;
import com.vnpay.qrparser.parser.TlvParser;

import java.util.Locale;

/** Fixtures luôn khai báo length theo code point và tính CRC trên payload thực tế. */
public final class TestQrBuilder {
    private TestQrBuilder() { }

    public static String tlv(String id, String value) {
        int length = value.codePointCount(0, value.length());
        if (!id.matches("[0-9]{2}") || length > 99) {
            throw new IllegalArgumentException("Fixture không thể biểu diễn bằng TLV hai chữ số.");
        }
        return id + String.format(Locale.ROOT, "%02d", length) + value;
    }

    public static String crc(String payload) {
        String prefix = payload + "6304";
        return prefix + new Crc16Calculator().calculateHex(prefix);
    }

    public static String base(QrType type) {
        return tlv("00", "01") + tlv("01", type == QrType.TYPE_04 ? "12" : "11")
                + tlv("26", tlv("00", "A000000775") + tlv("01", "MERCHANT"))
                + tlv("52", "5499") + tlv("53", "704") + tlv("58", "VN")
                + tlv("59", " ĐỒNG QUÊ 😀 ") + tlv("60", "HANOI");
    }

    public static String additional(QrType type) {
        String common = tlv("03", "SHOP") + tlv("07", "TERM") + tlv("08", "DESCRIPTION");
        return switch (type) {
            case TYPE_01 -> common + tlv("09", "M");
            case TYPE_02 -> common + tlv("06", "CUSTOMER");
            case TYPE_03 -> common + tlv("05", "022612311230PRODUCT") + tlv("09", "AME");
            case TYPE_04 -> common + tlv("05", "012612311230ORDER") + tlv("09", "AME");
        };
    }

    public static String full(QrType type) {
        return base(type) + tlv("62", additional(type));
    }

    public static String replace(String payload, String id, String value) {
        return remove(payload, id) + tlv(id, value);
    }

    public static String remove(String payload, String id) {
        StringBuilder result = new StringBuilder();
        for (var tag : new TlvParser().parse(payload)) {
            if (!tag.tag().equals(id)) { result.append(tlv(tag.tag(), tag.value())); }
        }
        return result.toString();
    }
}
