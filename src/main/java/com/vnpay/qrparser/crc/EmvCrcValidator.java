package com.vnpay.qrparser.crc;

import com.vnpay.qrparser.model.CrcValidationResult;
import com.vnpay.qrparser.model.CrcValidationStatus;
import com.vnpay.qrparser.model.TlvTag;
import com.vnpay.qrparser.parser.TlvParser;
import com.vnpay.qrparser.parser.TlvParseException;
import com.vnpay.qrparser.model.QrValidationError.Code;

import java.util.List;

public class EmvCrcValidator {

    private final TlvParser tlvParser = new TlvParser();
    private final Crc16Calculator calculator = new Crc16Calculator();

    public CrcValidationResult validate(String qr) {
        List<TlvTag> tags;
        try {
            tags = tlvParser.parse(qr);
        } catch (TlvParseException exception) {
            return structureError(exception.error().code(), "QR không đúng cấu trúc TLV: " + exception.getMessage());
        }

        // Chỉ đếm tag 63 ở cấp ngoài cùng, không tìm chuỗi "63" trong value.
        int crcTagCount = 0;
        TlvTag crcTag = null;
        for (TlvTag tag : tags) {
            if ("63".equals(tag.tag())) {
                crcTagCount++;
                crcTag = tag;
            }
        }

        if (crcTagCount == 0) {
            return structureError(Code.CRC_MISSING, "QR thiếu tag CRC 63 ở cấp ngoài cùng.");
        }
        if (crcTagCount > 1) {
            return structureError(Code.CRC_DUPLICATED, "Tag CRC 63 phải xuất hiện đúng một lần ở cấp ngoài cùng,"
                    + " tìm thấy " + crcTagCount + " lần.");
        }
        if (!"63".equals(tags.get(tags.size() - 1).tag())) {
            return structureError(Code.CRC_INVALID_POSITION, "Tag CRC 63 phải nằm cuối QR.");
        }
        if (crcTag.length() != 4) {
            return structureError(Code.CRC_INVALID_LENGTH, "Tag CRC 63 phải có length 04, nhận được " + crcTag.length() + ".");
        }

        String providedCrc = crcTag.value();
        // Chấp nhận đúng 4 ký tự hexadecimal ASCII, cả chữ hoa và chữ thường.
        if (!providedCrc.matches("[0-9A-Fa-f]{4}")) {
            return structureError(Code.CRC_INVALID_FORMAT, "Value của tag CRC 63 phải gồm đúng 4 ký tự hexadecimal (0-9, A-F, a-f).");
        }

        // Đã xác nhận tag 63 ở cuối và length 04: bỏ đúng 4 ký tự CRC.
        // Dùng chuỗi gốc để giữ nguyên khoảng trắng; header 6304 vẫn được tính.
        String crcInput = qr.substring(0, qr.length() - 4);
        String computedCrc = calculator.calculateHex(crcInput);

        if (providedCrc.equalsIgnoreCase(computedCrc)) {
            return new CrcValidationResult(CrcValidationStatus.VALID, providedCrc, computedCrc,
                    "CRC hợp lệ trong phạm vi kiểm tra của validator.");
        }

        return new CrcValidationResult(CrcValidationStatus.MISMATCH, providedCrc, computedCrc,
                "CRC không khớp: QR chứa " + providedCrc + ", tính được " + computedCrc + ".");
    }

    private CrcValidationResult structureError(Code code, String message) {
        return new CrcValidationResult(CrcValidationStatus.STRUCTURE_ERROR, "", "", message, code);
    }
}
