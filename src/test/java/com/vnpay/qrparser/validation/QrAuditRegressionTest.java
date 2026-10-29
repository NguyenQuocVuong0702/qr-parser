package com.vnpay.qrparser.validation;

import com.vnpay.qrparser.model.CrcValidationStatus;
import com.vnpay.qrparser.model.QrType;
import com.vnpay.qrparser.model.QrValidationError;
import com.vnpay.qrparser.model.QrValidationError.Code;
import com.vnpay.qrparser.model.QrValidationResult;
import com.vnpay.qrparser.model.QrValidationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static com.vnpay.qrparser.support.TestQrBuilder.additional;
import static com.vnpay.qrparser.support.TestQrBuilder.base;
import static com.vnpay.qrparser.support.TestQrBuilder.crc;
import static com.vnpay.qrparser.support.TestQrBuilder.full;
import static com.vnpay.qrparser.support.TestQrBuilder.remove;
import static com.vnpay.qrparser.support.TestQrBuilder.replace;
import static com.vnpay.qrparser.support.TestQrBuilder.tlv;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QrAuditRegressionTest {
    private final QrValidationService service = new QrValidationService();

    @ParameterizedTest
    @EnumSource(QrType.class)
    void changedMerchantWithOriginalCrcReportsMismatchAtCrcTag(QrType type) {
        String original = replace(full(type), "59", "AUDIT A");
        String changed = replace(full(type), "59", "AUDIT B");
        String oldCrcSuffix = crc(original).substring(original.length());

        var result = service.validate(changed + oldCrcSuffix, type);

        assertEquals(QrValidationStatus.CRC_MISMATCH, result.status());
        assertEquals(CrcValidationStatus.MISMATCH, result.crcResult().status());
        assertNotEquals(result.crcResult().providedCrc(), result.crcResult().computedCrc());
        var error = onlyError(result, Code.CRC_MISMATCH, "63");
        assertEquals(changed.length(), error.absoluteOffset());
        assertNull(error.parentValueOffset());
    }

    @ParameterizedTest
    @EnumSource(QrType.class)
    void changedMerchantWithRecomputedCrcRemainsValidWithinImplementedRules(QrType type) {
        String original = replace(full(type), "59", "AUDIT A");
        String changed = replace(full(type), "59", "AUDIT B");

        var result = service.validate(crc(changed), type);

        assertEquals(QrValidationStatus.VALID, result.status());
        assertTrue(result.errors().isEmpty());
        assertEquals(CrcValidationStatus.VALID, result.crcResult().status());
        assertEquals(result.crcResult().providedCrc(), result.crcResult().computedCrc());
        assertNotEquals(crc(original), crc(changed));
        assertEquals("AUDIT B", result.tags().stream().filter(tag -> tag.tag().equals("59"))
                .findFirst().orElseThrow().value());
    }

    @Test
    void changedLengthThatAbsorbsPostalTagIsDetectedWhenOriginalCrcIsKept() {
        String prefix = full(QrType.TYPE_01);
        String postal = tlv("61", "XYZ");
        String original = prefix + tlv("99", "AB") + postal;
        String changed = prefix + tlv("99", "AB" + postal);

        var result = service.validate(changed + crc(original).substring(original.length()), QrType.TYPE_01);

        assertEquals(original.length(), changed.length());
        assertEquals(QrValidationStatus.CRC_MISMATCH, result.status());
        assertEquals(CrcValidationStatus.MISMATCH, result.crcResult().status());
        assertEquals(changed.length(), onlyError(result, Code.CRC_MISMATCH, "63").absoluteOffset());
        assertFalse(result.tags().stream().anyMatch(tag -> tag.tag().equals("61")));
    }

    @Test
    void changedLengthThatAbsorbsPostalTagCanPassAfterCrcIsRecomputed() {
        String postal = tlv("61", "XYZ");
        String changed = full(QrType.TYPE_01) + tlv("99", "AB" + postal);

        var result = service.validate(crc(changed), QrType.TYPE_01);

        assertEquals(QrValidationStatus.VALID, result.status());
        assertTrue(result.errors().isEmpty());
        assertEquals(CrcValidationStatus.VALID, result.crcResult().status());
        assertFalse(result.tags().stream().anyMatch(tag -> tag.tag().equals("61")));
        var absorbed = result.tags().stream().filter(tag -> tag.tag().equals("99")).findFirst().orElseThrow();
        assertEquals(9, absorbed.length());
        assertEquals("AB" + postal, absorbed.value());
    }

    static Stream<Arguments> invalidConsumerRequests() {
        return Stream.of(QrType.TYPE_03, QrType.TYPE_04).flatMap(type ->
                Stream.of("X", "a", "M1", "", " ", "\uFF21").map(value -> Arguments.of(type, value)));
    }

    @ParameterizedTest(name = "{0}: invalid 62/09 request [{1}]")
    @MethodSource("invalidConsumerRequests")
    void invalidConsumerDataRequestReportsFieldPathAndUtf16Offsets(QrType type, String value) {
        String prefix = base(type);
        String precedingChildren = remove(additional(type), "09");
        String children = precedingChildren + tlv("09", value);

        var result = service.validate(crc(prefix + tlv("62", children)), type);

        assertEquals(QrValidationStatus.SPEC_VIOLATION, result.status());
        assertEquals(CrcValidationStatus.VALID, result.crcResult().status());
        var error = onlyError(result, Code.INVALID_FIELD_VALUE, "62/09");
        assertEquals(precedingChildren.length(), error.parentValueOffset());
        assertEquals(prefix.length() + 4 + precedingChildren.length(), error.absoluteOffset());
    }

    static Stream<Arguments> invalidOrderReferences() {
        String deadline = "2612311230";
        String identifier = "ORDER";
        return Stream.of(
                Arguments.of("product prefix", "02" + deadline + identifier),
                Arguments.of("short deadline", "01" + deadline.substring(0, 9) + identifier),
                Arguments.of("letters in deadline", "01" + "26AA311230" + identifier),
                Arguments.of("zero month", "01" + "2600311230" + identifier),
                Arguments.of("month above range", "01" + "2613311230" + identifier),
                Arguments.of("zero day", "01" + "2612001230" + identifier),
                Arguments.of("hour above range", "01" + "2612312430" + identifier),
                Arguments.of("minute above range", "01" + "2612311260" + identifier),
                Arguments.of("non ASCII digit", "01" + "261231123\uFF10" + identifier),
                Arguments.of("missing identifier", "01" + deadline),
                Arguments.of("only prefix", "01"));
    }

    @ParameterizedTest(name = "Type 04 rejects reference with {0}")
    @MethodSource("invalidOrderReferences")
    void invalidOrderReferenceReportsReferenceFieldAndOffsetsWithValidCrc(String scenario, String reference) {
        String prefix = base(QrType.TYPE_04);
        String children = tlv("05", reference) + tlv("09", "M");

        var result = service.validate(crc(prefix + tlv("62", children)), QrType.TYPE_04);

        assertEquals(QrValidationStatus.SPEC_VIOLATION, result.status(), scenario);
        assertEquals(CrcValidationStatus.VALID, result.crcResult().status());
        var error = onlyError(result, Code.INVALID_FIELD_VALUE, "62/05");
        assertEquals(0, error.parentValueOffset());
        assertEquals(prefix.length() + 4, error.absoluteOffset());
        assertEquals(reference, result.tags().stream().filter(tag -> tag.tag().equals("62"))
                .findFirst().orElseThrow().children().getFirst().value());
    }

    private QrValidationError onlyError(QrValidationResult result, Code code, String path) {
        assertEquals(1, result.errors().size(), result.errors().toString());
        var error = result.errors().getFirst();
        assertEquals(code, error.code());
        assertEquals(path, error.tagPath());
        return error;
    }
}
