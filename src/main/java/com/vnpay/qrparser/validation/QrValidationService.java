package com.vnpay.qrparser.validation;

import com.vnpay.qrparser.crc.EmvCrcValidator;
import com.vnpay.qrparser.interpreter.EmvQrInterpreter;
import com.vnpay.qrparser.model.CrcValidationResult;
import com.vnpay.qrparser.model.CrcValidationStatus;
import com.vnpay.qrparser.model.QrType;
import com.vnpay.qrparser.model.QrValidationError;
import com.vnpay.qrparser.model.QrValidationError.Code;
import com.vnpay.qrparser.model.QrValidationResult;
import com.vnpay.qrparser.model.QrValidationStatus;
import com.vnpay.qrparser.model.TlvNode;
import com.vnpay.qrparser.parser.NestedTlvParser;
import com.vnpay.qrparser.parser.TlvParseException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Điều phối parsing, CRC, quy tắc theo sheet và interpreter; không thay đổi payload. */
@Service
public class QrValidationService {
    private final NestedTlvParser parser = new NestedTlvParser();
    private final EmvCrcValidator crcValidator = new EmvCrcValidator();
    private final EmvQrInterpreter interpreter = new EmvQrInterpreter();
    private final CommonQrRules commonRules = new CommonQrRules();
    private final Map<QrType, TypeSpecificRules> typeRules = Map.of(
            QrType.TYPE_01, new OfflineQrRules(), QrType.TYPE_02, new BillingQrRules(),
            QrType.TYPE_03, new ReferenceQrRules(), QrType.TYPE_04, new ReferenceQrRules());

    public QrValidationResult validate(String qr) {
        return validate(qr, QrType.TYPE_01);
    }

    public QrValidationResult validate(String qr, QrType type) {
        Objects.requireNonNull(type, "QrType không được null; dùng validate(qr) cho Type 01 mặc định.");
        List<TlvNode> nodes;
        try {
            nodes = parser.parse(qr, type);
        } catch (TlvParseException exception) {
            QrValidationError error = exception.error();
            QrValidationStatus status = error.code() == Code.EMPTY_INPUT
                    ? QrValidationStatus.INVALID_INPUT : QrValidationStatus.INVALID_STRUCTURE;
            return result(status, error.message(), List.of(error), null, List.of(), type);
        }

        List<QrValidationError> errors = new ArrayList<>();
        ValidationContext context = new ValidationContext(type, nodes, errors);
        for (var tag : context.tags()) {
            if (!tag.node().tag().matches("[0-9]{2}")) {
                context.error(Code.INVALID_TAG_ID, tag, "ID tag phải gồm đúng hai chữ số ASCII từ 00 đến 99.");
            }
        }
        if (!errors.isEmpty()) {
            return result(QrValidationStatus.INVALID_STRUCTURE, "QR có ID tag không hợp lệ.", errors, null, nodes, type);
        }

        CrcValidationResult crc = crcValidator.validate(qr);
        if (crc.status() == CrcValidationStatus.STRUCTURE_ERROR) {
            var crcTag = context.find("63");
            errors.add(crcTag == null
                    ? new QrValidationError(crc.errorCode(), "63", null, null, crc.message())
                    : crcTag.error(crc.errorCode(), crc.message()));
            return result(QrValidationStatus.INVALID_STRUCTURE, crc.message(), errors, crc, nodes, type);
        }

        commonRules.validate(context);
        typeRules.get(type).validate(context);
        if (crc.status() == CrcValidationStatus.MISMATCH) {
            errors.addFirst(context.find("63").error(Code.CRC_MISMATCH, crc.message()));
            return result(QrValidationStatus.CRC_MISMATCH, crc.message(), errors, crc, nodes, type);
        }
        if (!errors.isEmpty()) {
            return result(QrValidationStatus.SPEC_VIOLATION,
                    "QR vi phạm quy tắc của Excel " + type.sheetName() + ".", errors, crc, nodes, type);
        }
        return result(QrValidationStatus.VALID,
                "QR hợp lệ trong phạm vi kiểm tra Excel " + type.sheetName() + ".", errors, crc, nodes, type);
    }

    private QrValidationResult result(QrValidationStatus status, String message, List<QrValidationError> errors,
                                      CrcValidationResult crc, List<TlvNode> nodes, QrType type) {
        return new QrValidationResult(status, message, errors, crc, interpreter.interpret(nodes, type));
    }
}
