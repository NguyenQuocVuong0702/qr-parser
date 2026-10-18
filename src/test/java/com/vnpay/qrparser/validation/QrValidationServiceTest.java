package com.vnpay.qrparser.validation;

import com.vnpay.qrparser.crc.Crc16Calculator;
import com.vnpay.qrparser.crc.EmvCrcValidator;
import com.vnpay.qrparser.model.CrcValidationResult;
import com.vnpay.qrparser.model.CrcValidationStatus;
import com.vnpay.qrparser.model.QrValidationError;
import com.vnpay.qrparser.model.QrValidationError.Code;
import com.vnpay.qrparser.model.QrValidationResult;
import com.vnpay.qrparser.model.QrValidationStatus;
import com.vnpay.qrparser.model.TlvTag;
import com.vnpay.qrparser.parser.TlvParseException;
import com.vnpay.qrparser.parser.TlvParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QrValidationServiceTest {

    private static final String ORIGINAL_QR =
            "00020101021126300010A00000077501120682000002105204549953037045802VN5911DONG QUE 796007LAMDONG62270311DONG QUE 790708DONGQUE163045E14";

    private final QrValidationService service = new QrValidationService();

    @Test
    void acceptsType01QrWithoutOptionalTemplate62AndMerchantId() {
        QrValidationResult result = service.validate(withCrc(basePayload()));

        assertEquals(QrValidationStatus.VALID, result.status());
        assertTrue(result.isValid());
        assertTrue(result.errors().isEmpty());
        assertEquals(CrcValidationStatus.VALID, result.crcResult().status());
    }

    @Test
    void acceptsType01QrWithAdditionalConsumerDataRequest() {
        QrValidationResult result = service.validate(withCrc(payloadWith62()));

        assertEquals(QrValidationStatus.VALID, result.status());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void separatesOriginalQrValidCrcFromExcelSpecificationViolation() {
        QrValidationResult result = service.validate(ORIGINAL_QR);

        assertEquals(QrValidationStatus.SPEC_VIOLATION, result.status());
        assertFalse(result.isValid());
        assertEquals(CrcValidationStatus.VALID, result.crcResult().status());
        assertEquals("5E14", result.crcResult().providedCrc());
        assertEquals("5E14", result.crcResult().computedCrc());
        assertEquals(1, result.errors().size());
        QrValidationError error = findError(result, Code.MISSING_REQUIRED_TAG, "62/09");
        assertTrue(error.message().contains("Excel Type 01"));
        assertTrue(error.message().contains("J31=M"));
        assertTrue(error.message().contains("EMVCo Merchant-Presented Mode v1.1"));
        assertTrue(error.message().contains("optional"));
        assertFalse(result.message().contains("bị sửa"));
        assertNull(error.parentValueOffset());
        assertNull(error.absoluteOffset());

        // Không tự thêm 09, đổi CRC hoặc làm mất khoảng trắng trong value.
        assertEquals(10, result.tags().size());
        assertEquals("0311DONG QUE 790708DONGQUE1", result.tags().get(8).value());
        assertEquals(List.of("03", "07"), result.tags().get(8).children().stream().map(node -> node.tag()).toList());
        assertEquals("DONG QUE 79", result.tags().get(6).value());
        assertEquals("5E14", result.tags().get(9).value());
    }

    @Test
    void reportsChangedOriginalQrAsCrcMismatchAndRetainsSpecificationError() {
        QrValidationResult result = service.validate(ORIGINAL_QR.replaceFirst("DONG QUE 79", "DONG QUE 78"));

        assertEquals(QrValidationStatus.CRC_MISMATCH, result.status());
        assertEquals(CrcValidationStatus.MISMATCH, result.crcResult().status());
        assertEquals("5E14", result.crcResult().providedCrc());
        assertEquals("93F6", result.crcResult().computedCrc());
        findError(result, Code.CRC_MISMATCH, "63");
        findError(result, Code.MISSING_REQUIRED_TAG, "62/09");
        assertEquals(2, result.errors().size());
    }

    @Test
    void distinguishesWellFormedCrcMismatchFromStructureError() {
        String validQr = withCrc(basePayload());
        char replacement = validQr.endsWith("0") ? '1' : '0';
        QrValidationResult result = service.validate(validQr.substring(0, validQr.length() - 1) + replacement);

        assertEquals(QrValidationStatus.CRC_MISMATCH, result.status());
        assertEquals(CrcValidationStatus.MISMATCH, result.crcResult().status());
        assertNotEquals(result.crcResult().providedCrc(), result.crcResult().computedCrc());
        assertEquals(1, result.errors().size());
        findError(result, Code.CRC_MISMATCH, "63");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void handlesMissingInputWithoutThrowing(String qr) {
        QrValidationResult result = service.validate(qr);

        assertEquals(QrValidationStatus.INVALID_INPUT, result.status());
        QrValidationError error = findError(result, Code.EMPTY_INPUT, "");
        assertNull(error.absoluteOffset());
        assertNull(error.parentValueOffset());
        assertNull(result.crcResult());
        assertTrue(result.tags().isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"000, 0", "0002010, 6"})
    void reportsIncompleteRootHeader(String qr, int offset) {
        QrValidationResult result = service.validate(qr);

        assertEquals(QrValidationStatus.INVALID_STRUCTURE, result.status());
        QrValidationError error = findError(result, Code.INCOMPLETE_HEADER, "");
        assertEquals(offset, error.absoluteOffset());
        assertNull(error.parentValueOffset());
    }

    @Test
    void reportsLength5AUsingStableCode() {
        QrValidationResult result = service.validate("000201595AHELLO");

        assertEquals(QrValidationStatus.INVALID_STRUCTURE, result.status());
        QrValidationError error = findError(result, Code.NON_NUMERIC_LENGTH, "59");
        assertEquals(6, error.absoluteOffset());
        assertNull(error.parentValueOffset());
    }

    @ParameterizedTest
    @CsvSource({"5905AB, 0", "0002015905AB, 6"})
    void reportsTruncatedRootValue(String qr, int offset) {
        QrValidationResult result = service.validate(qr);

        assertEquals(QrValidationStatus.INVALID_STRUCTURE, result.status());
        assertEquals(offset, findError(result, Code.TRUNCATED_VALUE, "59").absoluteOffset());
    }

    @ParameterizedTest
    @ValueSource(strings = {"A1", "-1", "０１"})
    void rejectsNonAsciiNumericRootIds(String id) {
        QrValidationResult result = service.validate(tlv(id, "AB"));

        assertEquals(QrValidationStatus.INVALID_STRUCTURE, result.status());
        assertEquals(0, findError(result, Code.INVALID_TAG_ID, id).absoluteOffset());
    }

    @Test
    void reportsInvalidChildIdWithBothOffsets() {
        QrValidationResult result = service.validate("000201010211" + tlv("26", tlv("A1", "AB")));

        assertEquals(QrValidationStatus.INVALID_STRUCTURE, result.status());
        QrValidationError error = findError(result, Code.INVALID_TAG_ID, "26/A1");
        assertEquals(0, error.parentValueOffset());
        assertEquals(16, error.absoluteOffset());
    }

    @ParameterizedTest
    @ValueSource(strings = {"26", "62"})
    void reportsIncompleteChildHeaderWithoutGuessingChildId(String parent) {
        QrValidationResult result = service.validate("000201010211" + tlv(parent, "0002AB0"));

        assertEquals(QrValidationStatus.INVALID_STRUCTURE, result.status());
        QrValidationError error = findError(result, Code.INCOMPLETE_HEADER, parent);
        assertEquals(6, error.parentValueOffset());
        assertEquals(22, error.absoluteOffset());
    }

    @ParameterizedTest
    @ValueSource(strings = {"26", "62"})
    void reportsNonNumericChildLengthWithBothOffsets(String parent) {
        QrValidationResult result = service.validate("000201010211" + tlv(parent, "0002AB015AC"));

        assertEquals(QrValidationStatus.INVALID_STRUCTURE, result.status());
        QrValidationError error = findError(result, Code.NON_NUMERIC_LENGTH, parent + "/01");
        assertEquals(6, error.parentValueOffset());
        assertEquals(22, error.absoluteOffset());
    }

    @ParameterizedTest
    @ValueSource(strings = {"26", "62"})
    void reportsTruncatedChildValueWithBothOffsets(String parent) {
        QrValidationResult result = service.validate("000201010211" + tlv(parent, "0002AB0102C"));

        assertEquals(QrValidationStatus.INVALID_STRUCTURE, result.status());
        QrValidationError error = findError(result, Code.TRUNCATED_VALUE, parent + "/01");
        assertEquals(6, error.parentValueOffset());
        assertEquals(22, error.absoluteOffset());
        assertNull(result.crcResult());
    }

    @ParameterizedTest
    @ValueSource(strings = {"26", "62"})
    void reportsEmptyTemplateAsStructureErrorWithoutInventingOffsets(String parent) {
        QrValidationResult result = service.validate("000201" + tlv(parent, ""));

        assertEquals(QrValidationStatus.INVALID_STRUCTURE, result.status());
        QrValidationError error = findError(result, Code.EMPTY_TEMPLATE, parent);
        assertNull(error.parentValueOffset());
        assertNull(error.absoluteOffset());
    }

    @Test
    void reportsBlankTemplateAsStructureError() {
        QrValidationResult result = service.validate(tlv("26", "   "));

        assertEquals(QrValidationStatus.INVALID_STRUCTURE, result.status());
        findError(result, Code.EMPTY_TEMPLATE, "26");
    }

    @Test
    void measuresOffsetsAsUtf16RatherThanUtf8Bytes() {
        QrValidationResult result = service.validate(tlv("59", "é A") + tlv("26", "0002AB0102C"));

        QrValidationError error = findError(result, Code.TRUNCATED_VALUE, "26/01");
        assertEquals(6, error.parentValueOffset());
        assertEquals(17, error.absoluteOffset());
    }

    @Test
    void detectsBadChildStructureEvenWhenRootCrcMatches() {
        String qr = withCrc(replaceRoot(basePayload(), "26", "0002AB0102C"));
        assertEquals(CrcValidationStatus.VALID, new EmvCrcValidator().validate(qr).status());

        QrValidationResult result = service.validate(qr);

        assertEquals(QrValidationStatus.INVALID_STRUCTURE, result.status());
        findError(result, Code.TRUNCATED_VALUE, "26/01");
    }

    @Test
    void reportsMissingCrcWithoutInventingItsPosition() {
        QrValidationResult result = service.validate(basePayload());

        assertEquals(QrValidationStatus.INVALID_STRUCTURE, result.status());
        assertEquals(CrcValidationStatus.STRUCTURE_ERROR, result.crcResult().status());
        QrValidationError error = findError(result, Code.CRC_MISSING, "63");
        assertNull(error.absoluteOffset());
        assertNull(error.parentValueOffset());
    }

    @Test
    void reportsWrongCrcLength() {
        String payload = basePayload();
        QrValidationResult result = service.validate(payload + "6303ABC");

        assertEquals(QrValidationStatus.INVALID_STRUCTURE, result.status());
        assertEquals(payload.length(), findError(result, Code.CRC_INVALID_LENGTH, "63").absoluteOffset());
        assertEquals(Code.CRC_INVALID_LENGTH, result.crcResult().errorCode());
    }

    @Test
    void reportsNonHexCrcFormat() {
        QrValidationResult result = service.validate(basePayload() + "63045E1G");

        assertEquals(QrValidationStatus.INVALID_STRUCTURE, result.status());
        findError(result, Code.CRC_INVALID_FORMAT, "63");
    }

    @Test
    void reportsWrongCrcPosition() {
        QrValidationResult result = service.validate(withCrc(basePayload()) + tlv("99", "X"));

        assertEquals(QrValidationStatus.INVALID_STRUCTURE, result.status());
        findError(result, Code.CRC_INVALID_POSITION, "63");
    }

    @Test
    void reportsDuplicateRootCrc() {
        QrValidationResult result = service.validate(withCrc(basePayload()) + "6304AAAA");

        assertEquals(QrValidationStatus.INVALID_STRUCTURE, result.status());
        findError(result, Code.CRC_DUPLICATED, "63");
    }

    @Test
    void reportsTruncatedCrcAsTlvStructureError() {
        QrValidationResult result = service.validate(basePayload() + "6304ABC");

        assertEquals(QrValidationStatus.INVALID_STRUCTURE, result.status());
        findError(result, Code.TRUNCATED_VALUE, "63");
        assertNull(result.crcResult());
    }

    @ParameterizedTest
    @ValueSource(strings = {"00", "26", "52", "53", "58", "59", "60"})
    void reportsEachRequiredRootFieldWithValidCrc(String id) {
        QrValidationResult result = service.validate(withCrc(withoutRoot(basePayload(), id)));

        assertEquals(QrValidationStatus.SPEC_VIOLATION, result.status());
        assertEquals(CrcValidationStatus.VALID, result.crcResult().status());
        QrValidationError error = findError(result, Code.MISSING_REQUIRED_TAG, id);
        assertNull(error.absoluteOffset());
        assertNull(error.parentValueOffset());
    }

    @Test
    void requiresGuidInsideEachTemplate26() {
        String firstTemplateHasGuid = basePayload();
        String secondTemplateHasNoGuid = tlv("26", tlv("01", "MERCHANT"));
        QrValidationResult result = service.validate(withCrc(firstTemplateHasGuid + secondTemplateHasNoGuid));

        assertEquals(QrValidationStatus.SPEC_VIOLATION, result.status());
        findError(result, Code.MISSING_REQUIRED_TAG, "26/00");
    }

    @Test
    void requiresRequestInsideEachTemplate62AccordingToExcel() {
        QrValidationResult result = service.validate(withCrc(payloadWith62() + tlv("62", tlv("03", "OTHER"))));

        assertEquals(QrValidationStatus.SPEC_VIOLATION, result.status());
        findError(result, Code.MISSING_REQUIRED_TAG, "62/09");
    }

    @Test
    void rejectsPayloadFormatValueOtherThan01() {
        QrValidationResult result = service.validate(withCrc(replaceRoot(basePayload(), "00", "02")));

        assertEquals(QrValidationStatus.SPEC_VIOLATION, result.status());
        findError(result, Code.INVALID_FIELD_VALUE, "00");
    }

    @Test
    void rejectsUnknownInitiationMethod() {
        QrValidationResult result = service.validate(withCrc(replaceRoot(basePayload(), "01", "13")));

        assertEquals(QrValidationStatus.SPEC_VIOLATION, result.status());
        findError(result, Code.INVALID_FIELD_VALUE, "01");
    }

    @ParameterizedTest
    @CsvSource({"00, 1", "01, 1", "52, 123", "53, 70", "58, VNM"})
    void enforcesFixedLengthsFromExcel(String id, String value) {
        QrValidationResult result = service.validate(withCrc(replaceRoot(basePayload(), id, value)));

        assertEquals(QrValidationStatus.SPEC_VIOLATION, result.status());
        findError(result, Code.INVALID_FIELD_LENGTH, id);
    }

    @ParameterizedTest
    @CsvSource({"00, A1", "01, 1A", "52, 54A9", "53, 7A4"})
    void enforcesNumericFormatsFromExcel(String id, String value) {
        QrValidationResult result = service.validate(withCrc(replaceRoot(basePayload(), id, value)));

        assertEquals(QrValidationStatus.SPEC_VIOLATION, result.status());
        findError(result, Code.INVALID_FIELD_VALUE, id);
    }

    @ParameterizedTest
    @CsvSource({"54, 13", "59, 25", "60, 15", "61, 10"})
    void enforcesRootMaximumLengths(String id, int maxLength) {
        String payload = withoutRoot(replaceRoot(basePayload(), "01", "12"), "01");
        QrValidationResult result = service.validate(withCrc(withoutRoot(payload, id) + tlv(id, "X".repeat(maxLength + 1))));

        assertEquals(QrValidationStatus.SPEC_VIOLATION, result.status());
        findError(result, Code.INVALID_FIELD_LENGTH, id);
    }

    @ParameterizedTest
    @CsvSource({"26, 00, 32", "26, 01, 13", "62, 01, 20", "62, 03, 20", "62, 07, 15", "62, 08, 19", "62, 09, 3"})
    void enforcesChildMaximumLengths(String parentId, String childId, int maxLength) {
        String payload = withoutRoot(payloadWith62(), "01");
        String existingChildren = new TlvParser().parse(payload).stream()
                .filter(tag -> tag.tag().equals(parentId)).findFirst().orElseThrow().value();
        String newChildren = withoutRoot(existingChildren, childId) + tlv(childId, "A".repeat(maxLength + 1));
        QrValidationResult result = service.validate(withCrc(replaceRoot(payload, parentId, newChildren)));

        assertEquals(QrValidationStatus.SPEC_VIOLATION, result.status());
        findError(result, Code.INVALID_FIELD_LENGTH, parentId + "/" + childId);
    }

    @Test
    void locatesSpecificationErrorInsideTemplate() {
        String prefix = basePayload();
        String children = tlv("03", "SHOP") + tlv("09", "X");
        QrValidationResult result = service.validate(withCrc(prefix + tlv("62", children)));

        QrValidationError error = findError(result, Code.INVALID_FIELD_VALUE, "62/09");
        assertEquals(8, error.parentValueOffset());
        assertEquals(prefix.length() + 4 + 8, error.absoluteOffset());
    }

    @ParameterizedTest
    @ValueSource(strings = {"A", "M", "E", "AME", "ME"})
    void acceptsConsumerDataRequestCharactersDefinedInExcel(String request) {
        String payload = basePayload() + tlv("62", tlv("09", request));

        assertEquals(QrValidationStatus.VALID, service.validate(withCrc(payload)).status());
    }

    @Test
    void acceptsDynamicQrWithAmountBillAndExcelRequiredRequest() {
        String payload = replaceRoot(basePayload(), "01", "12") + tlv("54", "1500.00")
                + tlv("62", tlv("01", "BILL1") + tlv("09", "M"));

        assertEquals(QrValidationStatus.VALID, service.validate(withCrc(payload)).status());
    }

    @Test
    void rejectsDynamicQrMissingBothAmountAndBill() {
        QrValidationResult result = service.validate(withCrc(replaceRoot(basePayload(), "01", "12")));

        assertEquals(QrValidationStatus.SPEC_VIOLATION, result.status());
        assertTrue(findError(result, Code.INVALID_INITIATION_RULE, "01").message().contains("Excel Type 01"));
    }

    @Test
    void rejectsDynamicQrWithOnlyAmount() {
        String payload = replaceRoot(basePayload(), "01", "12") + tlv("54", "1500");

        findError(service.validate(withCrc(payload)), Code.INVALID_INITIATION_RULE, "01");
    }

    @Test
    void rejectsDynamicQrWithOnlyBill() {
        String payload = replaceRoot(basePayload(), "01", "12") + tlv("62", tlv("01", "BILL1") + tlv("09", "M"));

        findError(service.validate(withCrc(payload)), Code.INVALID_INITIATION_RULE, "01");
    }

    @Test
    void rejectsStaticQrWithAmountAccordingToExcel() {
        findError(service.validate(withCrc(basePayload() + tlv("54", "1500"))), Code.INVALID_INITIATION_RULE, "01");
    }

    @Test
    void rejectsStaticQrWithBillAccordingToExcel() {
        String payload = basePayload() + tlv("62", tlv("01", "BILL1") + tlv("09", "M"));

        findError(service.validate(withCrc(payload)), Code.INVALID_INITIATION_RULE, "01");
    }

    @Test
    void doesNotRequireOptionalInitiationMethod() {
        assertEquals(QrValidationStatus.VALID, service.validate(withCrc(withoutRoot(basePayload(), "01"))).status());
    }

    @Test
    void doesNotForceExampleGuidMerchantCurrencyOrCountryValues() {
        String payload = replaceRoot(basePayload(), "26", tlv("00", "OTHER-GUID") + tlv("01", "OTHER-ID"));
        payload = replaceRoot(payload, "53", "840");
        payload = replaceRoot(payload, "58", "US");

        assertEquals(QrValidationStatus.VALID, service.validate(withCrc(payload)).status());
    }

    @Test
    void preservesUnknownTagsAndBmpUnicodeWithMatchingCrc() {
        String payload = basePayload() + tlv("99", " é ");
        QrValidationResult result = service.validate(withCrc(payload));

        assertEquals(QrValidationStatus.VALID, result.status());
        assertEquals("Unknown Tag", result.tags().get(8).name());
        assertEquals(" é ", result.tags().get(8).value());
    }

    @Test
    void acceptsInnerTag63WithoutCountingItAsAnotherRootCrc() {
        String payload = basePayload() + tlv("62", tlv("09", "M") + tlv("63", "ABCD"));

        assertEquals(QrValidationStatus.VALID, service.validate(withCrc(payload)).status());
    }

    @Test
    void collectsMultipleSpecificationErrorsWithValidCrc() {
        String payload = withoutRoot(withoutRoot(basePayload(), "52"), "53");
        QrValidationResult result = service.validate(withCrc(payload));

        assertEquals(QrValidationStatus.SPEC_VIOLATION, result.status());
        assertEquals(CrcValidationStatus.VALID, result.crcResult().status());
        assertEquals(2, result.errors().size());
        findError(result, Code.MISSING_REQUIRED_TAG, "52");
        findError(result, Code.MISSING_REQUIRED_TAG, "53");
    }

    @Test
    void parserExceptionRemainsCompatibleAndCarriesStableCode() {
        TlvParseException exception = assertThrows(TlvParseException.class,
                () -> new TlvParser().parse("595AHELLO"));

        assertTrue(exception instanceof IllegalArgumentException);
        assertEquals(Code.NON_NUMERIC_LENGTH, exception.error().code());
        assertEquals("59", exception.error().tagPath());
        assertEquals(0, exception.error().absoluteOffset());
    }

    @Test
    void retainsFourArgumentCrcResultConstructor() {
        assertNull(new CrcValidationResult(CrcValidationStatus.VALID, "ABCD", "ABCD", "OK").errorCode());
        assertEquals(Code.CRC_MISMATCH,
                new CrcValidationResult(CrcValidationStatus.MISMATCH, "ABCD", "1234", "Mismatch").errorCode());
        assertEquals(Code.CRC_STRUCTURE_ERROR,
                new CrcValidationResult(CrcValidationStatus.STRUCTURE_ERROR, "", "", "Error").errorCode());
    }

    @Test
    void copiesAndProtectsResultLists() {
        QrValidationResult original = service.validate(ORIGINAL_QR);
        List<QrValidationError> errors = new ArrayList<>(original.errors());
        var tags = new ArrayList<>(original.tags());
        QrValidationResult copy = new QrValidationResult(original.status(), original.message(), errors, original.crcResult(), tags);
        errors.clear();
        tags.clear();

        assertEquals(original.errors(), copy.errors());
        assertEquals(original.tags(), copy.tags());
        assertThrows(UnsupportedOperationException.class, () -> copy.errors().clear());
        assertThrows(UnsupportedOperationException.class, () -> copy.tags().clear());
    }

    @ParameterizedTest
    @ValueSource(strings = {"VN", "US", "ZZ"})
    void acceptsUppercaseLatinCountryFormatWithoutCountryRegistry(String country) {
        QrValidationResult result = service.validate(withCrc(replaceRoot(basePayload(), "58", country)));

        assertEquals(QrValidationStatus.VALID, result.status());
    }

    @ParameterizedTest
    @ValueSource(strings = {"12", "vn", "vN", "V1", "V_", "ÁB", "ＶＮ", "VNM"})
    void rejectsCountryValuesThatAreNotTwoUppercaseLatinLetters(String country) {
        QrValidationResult result = service.validate(withCrc(replaceRoot(basePayload(), "58", country)));

        assertEquals(QrValidationStatus.SPEC_VIOLATION, result.status());
        assertEquals(CrcValidationStatus.VALID, result.crcResult().status());
        findError(result, Code.INVALID_FIELD_VALUE, "58");
    }

    @Test
    void preservesSupplementaryUnicodeAndOriginalCrcInput() {
        String value = " A\uD83D\uDE00 ";
        String payload = basePayload() + tlv("99", value);
        QrValidationResult result = service.validate(withCrc(payload));

        assertEquals(QrValidationStatus.VALID, result.status());
        assertEquals(CrcValidationStatus.VALID, result.crcResult().status());
        assertEquals(4, result.tags().get(8).length());
        assertEquals(value, result.tags().get(8).value());
        assertEquals(value, result.tags().get(8).displayValue());
    }

    @ParameterizedTest
    @CsvSource({"25,VALID", "26,SPEC_VIOLATION"})
    void enforcesExistingMaximumLengthInCodePoints(int length, QrValidationStatus expected) {
        String payload = replaceRoot(basePayload(), "59", "\uD83D\uDE00".repeat(length));
        QrValidationResult result = service.validate(withCrc(payload));

        assertEquals(expected, result.status());
        assertEquals(length, result.tags().get(6).length());
        if (expected == QrValidationStatus.SPEC_VIOLATION) {
            findError(result, Code.INVALID_FIELD_LENGTH, "59");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"26", "62"})
    void locatesNestedParseErrorAfterEmojiInUtf16(String parent) {
        String qr = "9901\uD83D\uDE00" + tlv(parent, "0001\uD83D\uDE000102X");
        QrValidationResult result = service.validate(qr);

        assertEquals(QrValidationStatus.INVALID_STRUCTURE, result.status());
        QrValidationError error = findError(result, Code.TRUNCATED_VALUE, parent + "/01");
        assertEquals(6, error.parentValueOffset());
        assertEquals(16, error.absoluteOffset());
    }

    @Test
    void locatesSpecificationErrorAfterEmojiInRootAndChildValues() {
        String prefix = basePayload() + tlv("99", "\uD83D\uDE00");
        String children = tlv("03", "\uD83D\uDE00") + tlv("09", "X");
        QrValidationResult result = service.validate(withCrc(prefix + tlv("62", children)));

        assertEquals(QrValidationStatus.SPEC_VIOLATION, result.status());
        assertEquals(CrcValidationStatus.VALID, result.crcResult().status());
        QrValidationError error = findError(result, Code.INVALID_FIELD_VALUE, "62/09");
        assertEquals(6, error.parentValueOffset());
        assertEquals(prefix.length() + 4 + 6, error.absoluteOffset());
    }

    @Test
    void locatesCrcHeaderAfterEmojiInUtf16() {
        String payload = basePayload() + tlv("99", "\uD83D\uDE00");
        QrValidationResult result = service.validate(payload + "6304ZZZZ");

        assertEquals(payload.length(), findError(result, Code.CRC_INVALID_FORMAT, "63").absoluteOffset());
    }

    @ParameterizedTest
    @ValueSource(strings = {"\uD800", "\uDC00"})
    void returnsStableUnicodeErrorBeforeCrcEncoding(String value) {
        String prefix = basePayload();
        QrValidationResult result = service.validate(prefix + tlv("99", value) + "6304AAAA");

        assertEquals(QrValidationStatus.INVALID_STRUCTURE, result.status());
        assertNull(result.crcResult());
        assertEquals(prefix.length(), findError(result, Code.INVALID_UNICODE, "99").absoluteOffset());
    }

    @ParameterizedTest
    @ValueSource(strings = {"26", "62"})
    void identifiesParentForMalformedUnicodeInsideTemplate(String parent) {
        QrValidationResult result = service.validate("9901\uD83D\uDE00" + tlv(parent, "0301\uD800"));

        QrValidationError error = findError(result, Code.INVALID_UNICODE, parent);
        assertEquals(6, error.absoluteOffset());
        assertNull(error.parentValueOffset());
    }

    @Test
    void keepsExistingPoliciesForDuplicateTagsZeroLengthAndRootOrder() {
        String payload = basePayload().substring(6) + "000201" + tlv("59", "")
                + tlv("62", tlv("09", "AA"));

        assertEquals(QrValidationStatus.VALID, service.validate(withCrc(payload)).status());
    }

    private QrValidationError findError(QrValidationResult result, Code code, String path) {
        return result.errors().stream().filter(error -> error.code() == code && error.tagPath().equals(path))
                .findFirst().orElseThrow(() -> new AssertionError("Không tìm thấy lỗi " + code + " tại " + path));
    }

    private String basePayload() {
        return tlv("00", "01") + tlv("01", "11") + tlv("26", tlv("00", "ABCDEF"))
                + tlv("52", "5499") + tlv("53", "704") + tlv("58", "VN") + tlv("59", "SHOP") + tlv("60", "CITY");
    }

    private String payloadWith62() {
        return basePayload() + tlv("62", tlv("03", "SHOP") + tlv("07", "TERM") + tlv("09", "M"));
    }

    private String tlv(String id, String value) {
        return id + String.format(Locale.ROOT, "%02d", value.codePointCount(0, value.length())) + value;
    }

    private String withCrc(String payload) {
        String prefix = payload + "6304";
        return prefix + new Crc16Calculator().calculateHex(prefix);
    }

    private String withoutRoot(String payload, String id) {
        StringBuilder result = new StringBuilder();
        for (TlvTag tag : new TlvParser().parse(payload)) {
            if (!tag.tag().equals(id)) {
                result.append(tlv(tag.tag(), tag.value()));
            }
        }
        return result.toString();
    }

    private String replaceRoot(String payload, String id, String value) {
        StringBuilder result = new StringBuilder();
        for (TlvTag tag : new TlvParser().parse(payload)) {
            result.append(tlv(tag.tag(), tag.tag().equals(id) ? value : tag.value()));
        }
        return result.toString();
    }
}
