package com.vnpay.qrparser.validation;

import com.vnpay.qrparser.model.CrcValidationStatus;
import com.vnpay.qrparser.model.QrType;
import com.vnpay.qrparser.model.QrValidationError;
import com.vnpay.qrparser.model.QrValidationStatus;
import com.vnpay.qrparser.model.ReferenceLabel;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Locale;
import java.util.stream.Stream;

import static com.vnpay.qrparser.support.TestQrBuilder.base;
import static com.vnpay.qrparser.support.TestQrBuilder.crc;
import static com.vnpay.qrparser.support.TestQrBuilder.tlv;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReferenceLabelDateTest {
    private final QrValidationService service = new QrValidationService();

    static Stream<Arguments> calendarDates() {
        return Stream.of(QrType.TYPE_03, QrType.TYPE_04).flatMap(type -> Stream.of(
                Arguments.of(type, 23, 2, 29, false),
                Arguments.of(type, 24, 2, 29, true),
                Arguments.of(type, 24, 4, 31, false),
                Arguments.of(type, 23, 3, 15, true),
                // Excel chưa quy định thế kỷ của yy=00: khóa hành vi cũ, không chọn 1900/2000.
                Arguments.of(type, 0, 2, 29, true),
                Arguments.of(type, 0, 4, 31, false)));
    }

    @ParameterizedTest(name = "{0}: yy={1}, month={2}, day={3}, valid={4}")
    @MethodSource("calendarDates")
    void validatesCalendarDateInParserAndQrService(QrType type, int year, int month, int day, boolean valid) {
        String prefix = type == QrType.TYPE_03 ? "02" : "01";
        String deadline = String.format(Locale.ROOT, "%02d%02d%02d%02d%02d", year, month, day, 12, 30);
        String identifier = "ITEM";
        String reference = prefix + deadline + identifier;

        var parsed = ReferenceLabel.parse(reference, type);
        assertEquals(valid, parsed.isPresent());
        if (valid) {
            var label = parsed.orElseThrow();
            assertEquals(prefix, label.prefix());
            assertEquals(deadline, label.deadlineText());
            assertEquals(identifier, label.identifier());
        }

        String payload = base(type) + tlv("62", tlv("05", reference) + tlv("09", "M"));
        var result = service.validate(crc(payload), type);
        assertEquals(CrcValidationStatus.VALID, result.crcResult().status());
        assertEquals(valid ? QrValidationStatus.VALID : QrValidationStatus.SPEC_VIOLATION, result.status());
        if (valid) {
            assertTrue(result.errors().isEmpty());
        } else {
            assertEquals(1, result.errors().size());
            assertEquals(QrValidationError.Code.INVALID_FIELD_VALUE, result.errors().getFirst().code());
            assertEquals("62/05", result.errors().getFirst().tagPath());
        }
        var rawReference = result.tags().stream().filter(tag -> tag.tag().equals("62")).findFirst().orElseThrow()
                .children().stream().filter(tag -> tag.tag().equals("05")).findFirst().orElseThrow();
        assertEquals(reference, rawReference.value());
    }
}
