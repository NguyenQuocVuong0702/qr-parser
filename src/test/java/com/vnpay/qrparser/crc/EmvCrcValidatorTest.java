package com.vnpay.qrparser.crc;

import com.vnpay.qrparser.model.CrcValidationResult;
import com.vnpay.qrparser.model.CrcValidationStatus;
import com.vnpay.qrparser.model.QrValidationError.Code;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmvCrcValidatorTest {

    private static final String ORIGINAL_QR =
            "00020101021126300010A00000077501120682000002105204549953037045802VN5911DONG QUE 796007LAMDONG62270311DONG QUE 790708DONGQUE163045E14";

    private final EmvCrcValidator validator = new EmvCrcValidator();

    @Test
    void validatesOriginalQr() {
        CrcValidationResult result = validator.validate(ORIGINAL_QR);

        assertEquals(CrcValidationStatus.VALID, result.status());
        assertTrue(result.isValid());
        assertEquals("5E14", result.providedCrc());
        assertEquals("5E14", result.computedCrc());
    }

    @Test
    void detectsOneChangedValueCharacterWithUnchangedCrc() {
        // Chỉ sửa lần xuất hiện đầu tiên, thuộc value của tag 59.
        String changedQr = ORIGINAL_QR.replaceFirst("DONG QUE 79", "DONG QUE 78");

        CrcValidationResult result = validator.validate(changedQr);

        assertEquals(CrcValidationStatus.MISMATCH, result.status());
        assertFalse(result.isValid());
        assertEquals("5E14", result.providedCrc());
        assertEquals("93F6", result.computedCrc());
    }

    @Test
    void reportsWellFormedButIncorrectCrcAsMismatch() {
        CrcValidationResult result = validator.validate("00020163040000");

        assertEquals(CrcValidationStatus.MISMATCH, result.status());
        assertFalse(result.isValid());
        assertEquals("0000", result.providedCrc());
        assertEquals("AAE6", result.computedCrc());
        assertTrue(result.message().contains("CRC không khớp"));
    }

    @Test
    void includesCrcHeaderAndExcludesCrcValueFromCalculation() {
        // CRC tham chiếu AAE6 được tính độc lập trên chuỗi 0002016304.
        CrcValidationResult result = validator.validate("0002016304AAE6");

        assertEquals(CrcValidationStatus.VALID, result.status());
        assertEquals("AAE6", result.computedCrc());
    }

    @Test
    void acceptsLowercaseHexAndKeepsProvidedValue() {
        CrcValidationResult result = validator.validate("0002016304aae6");

        assertEquals(CrcValidationStatus.VALID, result.status());
        assertEquals("aae6", result.providedCrc());
        assertEquals("AAE6", result.computedCrc());
    }

    @Test
    void keepsSpacesInOriginalValue() {
        CrcValidationResult result = validator.validate("5903 A 6304FD4D");

        assertEquals(CrcValidationStatus.VALID, result.status());
        assertEquals("FD4D", result.computedCrc());
    }

    @Test
    void computesOverOriginalUtf8BytesWithExistingTlvCharacterLengths() {
        // Value "é A" có 3 Java char và 4 byte UTF-8; TLV parser được giữ nguyên.
        CrcValidationResult result = validator.validate("5903é A630465E4");

        assertEquals(CrcValidationStatus.VALID, result.status());
        assertEquals("65E4", result.computedCrc());
    }

    @Test
    void reportsMissingCrcInOriginalQr() {
        String withoutCrc = ORIGINAL_QR.substring(0, ORIGINAL_QR.length() - 8);

        CrcValidationResult result = validator.validate(withoutCrc);

        assertStructureError(result);
        assertEquals("QR thiếu tag CRC 63 ở cấp ngoài cùng.", result.message());
    }

    @Test
    void doesNotTreatCrcInsideTemplateAsRootCrc() {
        CrcValidationResult result = validator.validate("26086304ABCD");

        assertStructureError(result);
        assertTrue(result.message().contains("thiếu tag CRC 63 ở cấp ngoài cùng"));
    }

    @Test
    void doesNotTreatHeaderTextInsideValueAsRootCrc() {
        CrcValidationResult result = validator.validate("5912ABCD6304ABCD");

        assertStructureError(result);
        assertTrue(result.message().contains("thiếu tag CRC 63"));
    }

    @Test
    void acceptsRootCrcWhenTemplateAlsoContainsTag63() {
        String qr = withCrc("26086304ABCD");

        assertEquals(CrcValidationStatus.VALID, validator.validate(qr).status());
    }

    @Test
    void acceptsRootCrcWhenValueContainsHeaderText() {
        String qr = withCrc("59086304ABCD");

        assertEquals(CrcValidationStatus.VALID, validator.validate(qr).status());
    }

    @Test
    void validatesCrcWithinItsScopeWithoutParsingTemplateChildren() {
        String qr = withCrc("2603XYZ");

        assertEquals(CrcValidationStatus.VALID, validator.validate(qr).status());
    }

    @Test
    void rejectsCrcBeforeLastRootTag() {
        CrcValidationResult result = validator.validate("0002016304AAE65303704");

        assertStructureError(result);
        assertEquals("Tag CRC 63 phải nằm cuối QR.", result.message());
    }

    @Test
    void rejectsRepeatedRootCrcTags() {
        CrcValidationResult result = validator.validate("0002016304AAE66304AAAA");

        assertStructureError(result);
        assertTrue(result.message().contains("đúng một lần"));
        assertTrue(result.message().contains("tìm thấy 2 lần"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0002016303ABC", "0002016305ABCDE", "0002016300"})
    void rejectsCrcWithWrongDeclaredLength(String qr) {
        CrcValidationResult result = validator.validate(qr);

        assertStructureError(result);
        assertTrue(result.message().contains("length 04"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"GGGG", "5E1G", " 123", "+123", "１２３４", "8TTM"})
    void rejectsNonHexCrcValue(String providedCrc) {
        CrcValidationResult result = validator.validate("0002016304" + providedCrc);

        assertStructureError(result);
        assertTrue(result.message().contains("4 ký tự hexadecimal"));
    }

    @Test
    void reportsTruncatedCrcValue() {
        CrcValidationResult result = validator.validate("0002016304AAA");

        assertStructureError(result);
        assertTrue(result.message().contains("Value của tag 63"));
        assertTrue(result.message().contains("bị cắt ngắn"));
    }

    @Test
    void reportsNonNumericCrcLength() {
        CrcValidationResult result = validator.validate("00020163A4AAE6");

        assertStructureError(result);
        assertTrue(result.message().contains("Length của tag 63"));
        assertTrue(result.message().contains("phải gồm 2 chữ số"));
    }

    @Test
    void reportsIncompleteHeaderAfterCrc() {
        CrcValidationResult result = validator.validate("0002016304AAE60");

        assertStructureError(result);
        assertTrue(result.message().contains("Thiếu header"));
    }

    @Test
    void reportsMalformedTlvBeforeCrc() {
        CrcValidationResult result = validator.validate("00A2016304AAE6");

        assertStructureError(result);
        assertTrue(result.message().contains("Length của tag 00"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void handlesNullEmptyAndBlankQrWithoutThrowing(String qr) {
        CrcValidationResult result = validator.validate(qr);

        assertStructureError(result);
        assertTrue(result.message().contains("Dữ liệu TLV không được để trống"));
    }

    @Test
    void distinguishesCrcMismatchFromStructureError() {
        CrcValidationResult mismatch = validator.validate("00020163040000");
        CrcValidationResult structureError = validator.validate("0002016304GGGG");

        assertEquals(CrcValidationStatus.MISMATCH, mismatch.status());
        assertEquals("AAE6", mismatch.computedCrc());
        assertStructureError(structureError);
        assertFalse(mismatch.isValid());
        assertFalse(structureError.isValid());
    }

    @Test
    void acceptsUtf8CrcWithCodePointTlvLength() {
        CrcValidationResult result = validator.validate(withCrc("9901\uD83D\uDE00"));

        assertEquals(CrcValidationStatus.VALID, result.status());
        assertEquals(result.providedCrc(), result.computedCrc());
    }

    @Test
    void returnsStableStructureErrorForUnpairedSurrogate() {
        CrcValidationResult result = validator.validate("9901\uD8006304AAAA");

        assertStructureError(result);
        assertEquals(Code.INVALID_UNICODE, result.errorCode());
    }

    private String withCrc(String payload) {
        String prefix = payload + "6304";
        return prefix + new Crc16Calculator().calculateHex(prefix);
    }

    private void assertStructureError(CrcValidationResult result) {
        assertEquals(CrcValidationStatus.STRUCTURE_ERROR, result.status());
        assertFalse(result.isValid());
        assertEquals("", result.providedCrc());
        assertEquals("", result.computedCrc());
        assertFalse(result.message().isBlank());
    }
}
