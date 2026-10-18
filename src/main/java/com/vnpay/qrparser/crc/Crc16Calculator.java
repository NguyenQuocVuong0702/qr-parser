package com.vnpay.qrparser.crc;

import com.vnpay.qrparser.text.UnicodeText;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Tính CRC-16/CCITT-FALSE, độc lập với cấu trúc QR hoặc TLV. */
public class Crc16Calculator {

    private static final int POLYNOMIAL = 0x1021;
    private static final int INITIAL_VALUE = 0xFFFF;

    public int calculate(String input) {
        if (input == null) {
            throw new IllegalArgumentException("Dữ liệu tính CRC không được null.");
        }
        if (UnicodeText.containsUnpairedSurrogate(input)) {
            throw new IllegalArgumentException("Dữ liệu tính CRC chứa surrogate đơn không hợp lệ.");
        }

        int crc = INITIAL_VALUE;

        // Giữ nguyên dữ liệu và xử lý từng byte UTF-8, không xử lý từng char.
        for (byte dataByte : input.getBytes(StandardCharsets.UTF_8)) {
            int unsignedByte = dataByte & 0xFF;
            crc ^= unsignedByte << 8;

            // Xử lý từ bit cao xuống bit thấp, không phản chiếu bit.
            for (int bit = 0; bit < 8; bit++) {
                if ((crc & 0x8000) != 0) {
                    crc = (crc << 1) ^ POLYNOMIAL;
                } else {
                    crc = crc << 1;
                }

                // Java int có 32 bit; chỉ giữ lại 16 bit của CRC.
                crc &= 0xFFFF;
            }
        }

        // XOR output bằng 0x0000 nên kết quả cuối không thay đổi.
        return crc;
    }

    public String calculateHex(String input) {
        return String.format(Locale.ROOT, "%04X", calculate(input));
    }
}
