package com.vnpay.qrparser.api;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.vnpay.qrparser.model.CrcValidationResult;
import com.vnpay.qrparser.model.CrcValidationStatus;
import com.vnpay.qrparser.model.InterpretedTlvNode;
import com.vnpay.qrparser.model.QrValidationError;
import com.vnpay.qrparser.model.QrValidationResult;
import com.vnpay.qrparser.model.QrValidationStatus;
import com.vnpay.qrparser.validation.QrValidationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(QrAnalyzeController.class)
class QrAnalyzeControllerTest {

    private static final String URL = "/api/qr/analyze";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private QrValidationService validationService;

    @ParameterizedTest
    @CsvSource({"VALID,200", "INVALID_INPUT,400", "INVALID_STRUCTURE,422",
            "CRC_MISMATCH,422", "SPEC_VIOLATION,422"})
    void mapsEveryBusinessStatusToHttpStatus(QrValidationStatus businessStatus, int httpStatus) throws Exception {
        when(validationService.validate("qr-input")).thenReturn(result(businessStatus));

        send("{\"qr\":\"qr-input\"}")
                .andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.status").value(businessStatus.name()))
                .andExpect(jsonPath("$.message").value("Kết quả từ service."));

        verify(validationService).validate("qr-input");
    }

    @Test
    void rejectsMissingBodyWithoutContentType() throws Exception {
        mvc.perform(post(URL))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.errors[0].code").value("REQUEST_BODY_REQUIRED"));
        verifyNoInteractions(validationService);
    }

    @Test
    void rejectsEmptyBodyWithJsonContentType() throws Exception {
        assertRequestError("", "REQUEST_BODY_REQUIRED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", " \n null \t"})
    void distinguishesJsonNullBodyFromMissingBody(String body) throws Exception {
        assertRequestError(body, "REQUEST_BODY_NULL");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"other\":\"sensitive\"}"})
    void distinguishesMissingQrFromNullQr(String body) throws Exception {
        assertRequestError(body, "QR_REQUIRED");
    }

    @Test
    void passesExplicitNullQrToBusinessService() throws Exception {
        when(validationService.validate(isNull())).thenReturn(result(QrValidationStatus.INVALID_INPUT));

        send("{\"qr\":null}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("INVALID_INPUT"));

        verify(validationService).validate(null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "1.5", "true", "false", "{}", "[]", "[\"secret\"]"})
    void rejectsWrongQrTypesInsteadOfCoercingThem(String value) throws Exception {
        assertRequestError("{\"qr\":" + value + "}", "QR_MUST_BE_STRING");
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "\"secret\"", "123", "true"})
    void rejectsNonObjectBodies(String body) throws Exception {
        assertRequestError(body, "REQUEST_BODY_MUST_BE_OBJECT");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"qr\":\"sensitive\"", "{\"qr\":}", "not-json",
            " \n \t", "{\"qr\":\"sensitive\"} {}"})
    void returnsSafeUniformErrorsForMalformedJson(String body) throws Exception {
        String response = assertRequestError(body, "INVALID_JSON");
        assertThat(response).doesNotContain("sensitive", "not-json", "Exception", "stackTrace", "trace");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\n\t", "  ĐỒNG QUÊ \n\t ", "0002010102115303704"})
    void passesStringsToServiceWithoutTrimmingOrNormalizing(String qr) throws Exception {
        when(validationService.validate(qr)).thenReturn(result(QrValidationStatus.INVALID_INPUT));

        send(jsonMapper.writeValueAsString(Map.of("qr", qr))).andExpect(status().isBadRequest());

        verify(validationService).validate(qr);
    }

    @Test
    void keepsCrcErrorsOffsetsAndChildrenInResponse() throws Exception {
        InterpretedTlvNode child = new InterpretedTlvNode("00", 3, " A ",
                "GUID", "Mô tả", " A ", List.of());
        InterpretedTlvNode parent = new InterpretedTlvNode("26", 7, "0003 A ",
                "Merchant Account Information", "Template", "0003 A ", List.of(child));
        QrValidationError error = new QrValidationError(QrValidationError.Code.INVALID_FIELD_VALUE,
                "26/00", 0, 16, "Thông báo nghiệp vụ.");
        CrcValidationResult crc = new CrcValidationResult(CrcValidationStatus.VALID, "5E14", "5E14", "CRC hợp lệ.");
        when(validationService.validate("qr-input")).thenReturn(new QrValidationResult(
                QrValidationStatus.SPEC_VIOLATION, "Vi phạm specification.", List.of(error), crc, List.of(parent)));

        String response = send("{\"qr\":\"qr-input\"}")
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0].tagPath").value("26/00"))
                .andExpect(jsonPath("$.errors[0].parentValueOffset").value(0))
                .andExpect(jsonPath("$.errors[0].absoluteOffset").value(16))
                .andExpect(jsonPath("$.crcResult.status").value("VALID"))
                .andExpect(jsonPath("$.crcResult.providedCrc").value("5E14"))
                .andExpect(jsonPath("$.crcResult.computedCrc").value("5E14"))
                .andExpect(jsonPath("$.crcResult.errorCode").value(nullValue()))
                .andExpect(jsonPath("$.tags[0].value").value("0003 A "))
                .andExpect(jsonPath("$.tags[0].children[0].value").value(" A "))
                .andReturn().getResponse().getContentAsString();

        JsonNode body = jsonMapper.readTree(response);
        assertThat(body.size()).isEqualTo(5);
        assertThat(body.get("crcResult").size()).isEqualTo(5);
        assertThat(body.get("crcResult").get("valid")).isNull();
    }

    @Test
    void doesNotReflectInvalidLengthTextInErrorMessage() throws Exception {
        QrValidationError error = new QrValidationError(QrValidationError.Code.NON_NUMERIC_LENGTH,
                "26/01", 6, 22, "Nội dung đầu vào nhạy cảm: secret.");
        when(validationService.validate("qr-input")).thenReturn(new QrValidationResult(
                QrValidationStatus.INVALID_STRUCTURE, error.message(), List.of(error), null, List.of()));

        String response = send("{\"qr\":\"qr-input\"}")
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0].code").value("NON_NUMERIC_LENGTH"))
                .andExpect(jsonPath("$.errors[0].absoluteOffset").value(22))
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain("secret", "nhạy cảm");
    }

    @Test
    void returnsSafeErrorWhenServiceThrowsUnexpectedException() throws Exception {
        when(validationService.validate("sensitive-qr"))
                .thenThrow(new IllegalStateException("sensitive-qr internal details"));

        String response = send("{\"qr\":\"sensitive-qr\"}")
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.errors[0].code").value("INTERNAL_ERROR"))
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain("sensitive-qr", "IllegalStateException", "internal details", "stackTrace");
    }

    @Test
    void returnsJsonForUnsupportedContentType() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.TEXT_PLAIN).content("sensitive-qr"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].code").value("UNSUPPORTED_MEDIA_TYPE"));
        verifyNoInteractions(validationService);
    }

    @Test
    void keepsFrameworkStatusForUnsupportedHttpMethod() throws Exception {
        mvc.perform(get(URL))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.errors[0].code").value("HTTP_REQUEST_ERROR"));
        verifyNoInteractions(validationService);
    }

    @Test
    void logsOnlySafeDiagnosticInformationForUnexpectedExceptions() throws Exception {
        String sensitive = "sensitive-qr merchant-account secret";
        when(validationService.validate("qr-input")).thenThrow(new IllegalStateException(
                sensitive, new IllegalArgumentException("nested-cause secret")));
        Logger logger = (Logger) LoggerFactory.getLogger(ApiExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            send("{\"qr\":\"qr-input\"}").andExpect(status().isInternalServerError());

            assertThat(appender.list).hasSize(1);
            ILoggingEvent event = appender.list.getFirst();
            assertThat(event.getFormattedMessage()).contains("QR_ANALYZE_FAILED", "INTERNAL_ERROR", "IllegalStateException")
                    .doesNotContain(sensitive, "merchant-account", "secret", "nested-cause", "qr-input");
            assertThat(event.getThrowableProxy()).isNull();
            assertThat(event.getArgumentArray()).containsExactly("IllegalStateException");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private QrValidationResult result(QrValidationStatus status) {
        return new QrValidationResult(status, "Kết quả từ service.", List.of(), null, List.of());
    }

    private ResultActions send(String body) throws Exception {
        return mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    private String assertRequestError(String body, String code) throws Exception {
        String response = send(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].code").value(code))
                .andExpect(jsonPath("$.crcResult").value(nullValue()))
                .andExpect(jsonPath("$.tags").isEmpty())
                .andReturn().getResponse().getContentAsString();
        verifyNoInteractions(validationService);
        JsonNode json = jsonMapper.readTree(response);
        assertThat(json.size()).isEqualTo(5);
        assertThat(json.get("errors").get(0).size()).isEqualTo(5);
        assertThat(json.get("errors").get(0).get("absoluteOffset").isNull()).isTrue();
        return response;
    }
}
