package com.vnpay.qrparser.crc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Crc16CalculatorTest {

    private final Crc16Calculator calculator = new Crc16Calculator();

    @Test
    void matchesStandardCcittFalseVector() {
        assertEquals(0x29B1, calculator.calculate("123456789"));
        assertEquals("29B1", calculator.calculateHex("123456789"));
    }

    @Test
    void returnsInitialValueForEmptyData() {
        assertEquals(0xFFFF, calculator.calculate(""));
        assertEquals("FFFF", calculator.calculateHex(""));
    }

    @Test
    void rejectsNullDataWithClearMessage() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> calculator.calculate(null));

        assertEquals("Dữ liệu tính CRC không được null.", exception.getMessage());
    }

    @ParameterizedTest
    @CsvSource({"é, 7ACB", "ĐỒNG QUÊ, 6250"})
    void calculatesOverUtf8Bytes(String input, String expectedCrc) {
        // Các giá trị tham chiếu được đối chiếu bằng Python binascii.crc_hqx.
        assertEquals(expectedCrc, calculator.calculateHex(input));
    }

    @Test
    void padsHexResultWithLeadingZero() {
        assertEquals(0x041E, calculator.calculate(","));
        assertEquals("041E", calculator.calculateHex(","));
    }

    @Test
    void keepsSpacesInData() {
        assertEquals("FD4D", calculator.calculateHex("5903 A 6304"));
    }

    @Test
    void calculatesSupplementaryCharactersOverUtf8Bytes() {
        // Tham chiếu độc lập bằng Python binascii.crc_hqx trên byte UTF-8.
        assertEquals("4F4F", calculator.calculateHex("\uD83D\uDE00"));
        assertEquals("73CD", calculator.calculateHex(" A\uD83D\uDE00 "));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\uD800", "\uDC00", "A\uD800X", "\uDC00\uD800"})
    void refusesToReplaceMalformedUnicodeDuringEncoding(String input) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> calculator.calculateHex(input));

        assertEquals("Dữ liệu tính CRC chứa surrogate đơn không hợp lệ.", exception.getMessage());
    }
}
