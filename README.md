# EMV QR Parser

Ứng dụng học tập dùng Java 21, Spring Boot 4.1.1 và Maven. API nhận chuỗi QR, đọc TLV, phân tích template 26/62/80 theo ngữ cảnh, giải nghĩa metadata theo Type và đường dẫn, kiểm tra CRC-16 và các quy tắc từ đủ bốn sheet **Type 01–04**.

Ứng dụng xử lý chuỗi payload đã đọc từ QR. Chưa có chức năng đọc ảnh QR, frontend hoặc thực hiện thanh toán.

## Môi trường và thư mục làm việc

- Cài JDK 21 và cấu hình `JAVA_HOME`/`PATH`.
- Dùng Maven Wrapper có sẵn; không cần cài Maven riêng.
- Lần chạy đầu cần truy cập mạng để tải Maven và dependency.
- Chạy lệnh trong thư mục chứa `pom.xml`, `mvnw.cmd` và `src`. Trong workspace hiện tại, đây là thư mục con `qr-parser`.

Kiểm tra Java:

```powershell
java -version
```

## Chạy, test và build

Chạy bằng Maven Wrapper trên Windows PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

Mặc định ứng dụng chạy tại `http://localhost:8080`. Nhấn `Ctrl+C` để dừng.

Chạy toàn bộ JUnit test:

```powershell
.\mvnw.cmd test
```

Build và kiểm thử từ đầu:

```powershell
.\mvnw.cmd clean verify
```

Chạy executable JAR sau khi build:

```powershell
java -jar .\target\qr-parser-0.0.1-SNAPSHOT.jar
```

Nếu cổng 8080 đang được dùng:

```powershell
java -jar .\target\qr-parser-0.0.1-SNAPSHOT.jar --server.port=8081
```

Trên Linux/macOS, dùng `./mvnw` thay cho `.\mvnw.cmd`. Kết quả JUnit nằm trong `target/surefire-reports`.

## Cấu trúc và luồng xử lý

```text
src/main/java/com/vnpay/qrparser/
├── api/          Controller, xử lý exception và DTO HTTP
├── parser/       TlvParser, NestedTlvParser, TlvParseException
├── interpreter/  Giải nghĩa cây TLV theo đường dẫn tag
├── metadata/     Metadata bất biến theo QrType và đường dẫn tag
├── crc/          CRC-16/CCITT-FALSE và kiểm tra tag CRC
├── validation/   Điều phối và các bộ quy tắc chung / offline / billing / reference
├── text/         Kiểm tra Unicode UTF-16 hợp lệ
└── model/        QrType, ReferenceLabel, cây TLV, trạng thái và kết quả
```

`src/test/java` chứa test cho parser, interpreter, CRC, validation và REST API. Đặc tả gốc nằm tại `docs/MasterData_VNP_QRCode_V1.0.xlsx`.

Luồng HTTP:

```text
JSON → QrAnalyzeRequest → QrAnalyzeController → QrValidationService
     → parsing → kiểm tra CRC → kiểm tra theo QrType → giải nghĩa tag theo Type và path
     → QrAnalyzeResponse → HTTP status và JSON
```

Controller kiểm tra hợp đồng HTTP và gọi lại service. Logic TLV, CRC và specification nằm trong các thành phần nghiệp vụ.

## Endpoint

```http
POST /api/qr/analyze
Content-Type: application/json
Accept: application/json
```

Request cũ (giữ mặc định Type 01):

```json
{
  "qr": "00020101021126300010A00000077501120682000002105204549953037045802VN5911DONG QUE 796007LAMDONG62270311DONG QUE 790708DONGQUE163045E14"
}
```

API giữ nguyên chuỗi QR, bao gồm khoảng trắng trong value. Không `trim()`, chuẩn hóa Unicode, tự bổ sung tag hay tính lại CRC để sửa QR.

### Chọn Type và bốn request hợp lệ

`type` là chuỗi `"01"`, `"02"`, `"03"` hoặc `"04"`. Thiếu `type` hoặc `type:null` dùng Type 01 như API cũ. Số, boolean, array, object trả 400 / `TYPE_MUST_BE_STRING`; chuỗi khác (kể cả `"TYPE_01"`, `"auto"`, `"1"`) trả 400 / `INVALID_QR_TYPE`. Không tự trim hoặc tự nhận diện Type từ payload.

Các ví dụ dưới được tạo bằng TLV builder và CRC calculator, được JUnit đọc lại từ README và kiểm tra `VALID`. Chúng minh họa cùng endpoint và trả HTTP 200. Đây là dữ liệu tổng hợp, không dùng để thanh toán.

**Type 01: QR offline tĩnh**

```json
{
  "type": "01",
  "qr": "00020101021126260010A0000007750108MERCHANT5204549953037045802VN5904SHOP6005HANOI62290304SHOP0704TERM0804NOTE0901M63042256"
}
```

**Type 02: QR hóa đơn động có kỳ cước 80**

```json
{
  "type": "02",
  "qr": "00020101021226260010A0000007750108MERCHANT5204549953037045802VN5904SHOP6005HANOI62430304SHOP0704TERM0608CUSTOMER0811ELECTRICITY80240010A000000775010612202663042490"
}
```

**Type 03: QR sản phẩm tĩnh có số tiền và reference 02**

```json
{
  "type": "03",
  "qr": "00020101021126260010A0000007750108MERCHANT5204549953037045802VN5904SHOP6005HANOI540855000.0062610304SHOP0704TERM0519022612311230PRODUCT0811DESCRIPTION0903AME6304A9F4"
}
```

**Type 04: QR đơn hàng động có reference 01**

```json
{
  "type": "04",
  "qr": "00020101021226260010A0000007750108MERCHANT5204549953037045802VN5904SHOP6005HANOI540855000.0062590304SHOP0704TERM0517012612311230ORDER0811DESCRIPTION0903AME6304AB5F"
}
```

Type 03/04 phân tích reference tại `62/05`; `displayValue` hiển thị mã sản phẩm/đơn hàng và thời hạn dạng `yymmddhhmm`, còn `value` giữ nguyên. Không thêm tag con giả hoặc thay đổi 5 trường response hiện có.

### Hợp đồng response

| Thuộc tính | Nội dung |
|---|---|
| `status` | Trạng thái nghiệp vụ hoặc trạng thái lỗi HTTP |
| `message` | Thông báo dễ hiểu |
| `errors` | Danh sách mã lỗi, đường dẫn tag, offset và thông báo |
| `crcResult` | Kết quả CRC riêng; `null` nếu chưa thể kiểm tra |
| `tags` | Cây đã giải nghĩa; rỗng nếu parsing chưa hoàn tất |

Mỗi node có `tag`, `length`, `value`, `name`, `description`, `displayValue`, `children`. `value` luôn là dữ liệu gốc; `displayValue` chứa cách hiển thị đã giải nghĩa hoặc value gốc nếu chưa có ánh xạ. Children giữ đúng thứ tự.

QR gốc ở trên trả **HTTP 422**, `SPEC_VIOLATION`, nhưng CRC vẫn **VALID**, giá trị cung cấp và tính được đều là `5E14`.

Ví dụ response rút gọn dưới đây chỉ hiển thị tag 53. Response thực tế chứa đủ **10 tag cấp ngoài**, gồm children của 26 và 62:

```json
{
  "status": "SPEC_VIOLATION",
  "message": "QR vi phạm quy tắc của Excel Type 01.",
  "errors": [
    {
      "code": "MISSING_REQUIRED_TAG",
      "tagPath": "62/09",
      "parentValueOffset": null,
      "absoluteOffset": null,
      "message": "Excel Type 01 (ô J31=M) yêu cầu 62/09 khi có tag 62; EMVCo Merchant-Presented Mode v1.1 coi trường này là optional."
    }
  ],
  "crcResult": {
    "status": "VALID",
    "providedCrc": "5E14",
    "computedCrc": "5E14",
    "message": "CRC hợp lệ trong phạm vi kiểm tra của validator.",
    "errorCode": null
  },
  "tags": [
    {
      "tag": "53",
      "length": 3,
      "value": "704",
      "name": "Transaction Currency Code",
      "description": "Mã tiền tệ theo ISO 4217; mã 704 là VND.",
      "displayValue": "VND",
      "children": []
    }
  ]
}
```

Lỗi request cũng dùng cùng hợp đồng. Ví dụ `{"qr":123}` trả HTTP 400:

```json
{
  "status": "INVALID_REQUEST",
  "message": "Thuộc tính qr phải là chuỗi JSON hoặc null.",
  "errors": [
    {
      "code": "QR_MUST_BE_STRING",
      "tagPath": null,
      "parentValueOffset": null,
      "absoluteOffset": null,
      "message": "Thuộc tính qr phải là chuỗi JSON hoặc null."
    }
  ],
  "crcResult": null,
  "tags": []
}
```

### HTTP status và lỗi đầu vào

| Trường hợp | HTTP | `status` / mã lỗi |
|---|---:|---|
| QR vượt qua các kiểm tra đang triển khai | 200 | `VALID` |
| QR null, rỗng hoặc chỉ có khoảng trắng | 400 | `INVALID_INPUT` / `EMPTY_INPUT` |
| TLV hoặc cấu trúc CRC sai | 422 | `INVALID_STRUCTURE` |
| Unicode chứa surrogate đơn | 422 | `INVALID_STRUCTURE` / `INVALID_UNICODE` |
| CRC không khớp | 422 | `CRC_MISMATCH` |
| Vi phạm quy tắc Type đã chọn | 422 | `SPEC_VIOLATION` |
| Body thiếu | 400 | `INVALID_REQUEST` / `REQUEST_BODY_REQUIRED` |
| Body JSON `null` | 400 | `INVALID_REQUEST` / `REQUEST_BODY_NULL` |
| Body không phải object JSON | 400 | `INVALID_REQUEST` / `REQUEST_BODY_MUST_BE_OBJECT` |
| Thiếu thuộc tính `qr` | 400 | `INVALID_REQUEST` / `QR_REQUIRED` |
| `qr` là số, boolean, object hoặc array | 400 | `INVALID_REQUEST` / `QR_MUST_BE_STRING` |
| JSON sai cú pháp | 400 | `INVALID_REQUEST` / `INVALID_JSON` |
| Content-Type không được hỗ trợ | 415 | `INVALID_REQUEST` / `UNSUPPORTED_MEDIA_TYPE` |
| HTTP method không được hỗ trợ | 405 | `INVALID_REQUEST` / `HTTP_REQUEST_ERROR` |
| Lỗi server ngoài dự kiến | 500 | `INTERNAL_ERROR` |

`{"qr":null}` có thuộc tính `qr` và được chuyển vào service; khác với `{}` là lỗi hợp đồng request. `{"qr":""}` cũng là lỗi nội dung QR.

Khi client chỉ chấp nhận kiểu khác JSON, ví dụ `Accept: text/plain`, server có thể trả **406 với body rỗng**. Hành vi này được giữ nguyên; nên gửi `Accept: application/json`.

Nếu CRC mismatch và đồng thời có lỗi specification, trạng thái chính là `CRC_MISMATCH`, danh sách errors vẫn chứa lỗi specification. Nếu cấu trúc ngăn kiểm tra CRC, hệ thống không suy đoán kết quả CRC.

## Unicode, Length và offset

- ID và length header chiếm hai ký tự ASCII mỗi trường.
- Length của value đếm **Unicode code point**, không đếm byte UTF-8 hoặc UTF-16 code unit.
- Length của template đếm code point của toàn bộ value, bao gồm header TLV con.
- CRC vẫn tính trên byte **UTF-8** của chuỗi gốc.
- `absoluteOffset` và `parentValueOffset` tính theo **UTF-16**, bắt đầu từ 0, tương thích chỉ số chuỗi Java.
- Offset chỉ đến header liên quan. `parentValueOffset` tính từ đầu value của tag cha; ở cấp ngoài là `null`. Khi vị trí chưa xác định, hệ thống trả `null`.
- Surrogate đơn bị từ chối trước khi mã hóa UTF-8, không thay bằng `?`.

| Value | Code point / Length | UTF-16 units | Byte UTF-8 |
|---|---:|---:|---:|
| `A` | 1 | 1 | 1 |
| `é` | 1 | 1 | 2 |
| `😀` | 1 | 2 | 4 |

Ví dụ `9901😀000201` có hai tag: 99 chứa một emoji, 00 chứa `01`. Header thứ hai ở UTF-16 offset **6**. Code point khác với ký tự hiển thị: `e` cộng dấu kết hợp U+0301 có hai code point; ứng dụng giữ nguyên chuỗi này.

Nếu malformed Unicode được phát hiện ngay khi đọc value của template cấp ngoài, lỗi chỉ rõ tag cha và header của nó. Hệ thống không suy đoán tag con khi parsing con chưa diễn ra.

## Phạm vi specification

Đã đọc toàn bộ bốn sheet, không đọc workbook tại runtime. Bảng đối chiếu `docs/TAG_MATRIX.md` và audit `docs/EXCEL_EXAMPLE_AUDIT.md` là tài liệu cục bộ được sinh từ workbook nội bộ; các file này được `.gitignore` bảo vệ và không có trong bản clone sạch. Có thể tái tạo tại máy có workbook bằng `python tools/audit_spec.py` (Python standard library, không sửa Excel). `docs/SPEC_AMBIGUITIES.md` và `docs/UPGRADE_REPORT.md` cũng được giữ cục bộ. Không đưa các tài liệu này lên GitHub khi chưa được phép chia sẻ.

| Type | Quy tắc riêng |
|---|---|
| 01 — offline | Giữ 62/01, 03, 07, 08, 09; 01=11 không có 54/62/01, 01=12 cần cả hai. |
| 02 — hóa đơn / khách hàng | 62/03, 06, 07, 08; mở 80 và yêu cầu 80/00 (≤32), 80/01 (≤20) trong mỗi instance. 01=11 không có kỳ cước, 01=12 cần kỳ cước. Không yêu cầu 62/09 hoặc áp quy tắc amount/bill Type 01. |
| 03 — sản phẩm | 62/03, 05, 07, 08, 09; nếu có 01 chỉ nhận 11. Reference `02 + yymmddhhmm + mã sản phẩm`, tối đa 25 code point. 54 có thể xuất hiện. |
| 04 — đơn hàng | 62/03, 05, 07, 08, 09; nếu có 01 chỉ nhận 12. Reference `01 + yymmddhhmm + mã đơn hàng`, tối đa 25 code point. Không tự yêu cầu 62/01. |

Các rule chung chỉ được dùng sau khi đối chiếu cả bốn sheet: tag root bắt buộc, GUID trong mỗi 26, N, fixed/max length, phiên bản 00=01 và country dạng hai chữ Latin viết hoa. 62/09 bắt buộc khi có 62 ở Type 01/03/04 theo J31=M; Type 02 không có trường này trong sheet. Tag không được sheet định nghĩa vẫn được giữ, có metadata `Unknown Tag`, không áp quy tắc của Type khác.

`CommonQrRules` xử lý các ràng buộc chung; `OfflineQrRules`, `BillingQrRules`, `ReferenceQrRules` xử lý khác biệt từng Type. `ValidationContext` giữ vị trí UTF-16. Các overload `validate(qr)`, `parse(qr)`, `interpret(nodes)`, `lookup(path)` tiếp tục mặc định Type 01. Overload có `QrType` phục vụ caller chọn loại rõ ràng. `QrAnalyzeRequest.type()` giữ kiểu JSON gốc để không ép kiểu số thành chuỗi.

### Giới hạn đã biết và điểm cần review

Chi tiết từng ô và quyết định nằm trong `docs/SPEC_AMBIGUITIES.md` cục bộ. Các giới hạn của phần triển khai:

- Type 02!C30 ghi N cho 80 nhưng các con ghi ans; triển khai TLV theo schema con, không ép value cha chỉ gồm số. Kỳ cước không bị ép thành MMYYYY.
- 54 được đánh C ở Type 03/04 nhưng không có điều kiện cụ thể; chỉ kiểm tra khi xuất hiện. Tag 01 và 62/05 vẫn optional theo sheet.
- Thời hạn reference giữ nguyên `yymmddhhmm`; kiểm tra ngày theo năm/tháng/ngày và giờ/phút. Với `yy` khác `00`, 29/02 chỉ hợp lệ khi `yy` chia hết cho 4. Với `yy=00`, thế kỷ chưa được quy định nên giữ chấp nhận 29/02 như trước để chờ xác nhận; không tự quy đổi thế kỷ, timestamp hoặc xét hết hạn.
- Không có đủ bằng chứng để tự nhận diện Type. Payload tối thiểu có thể hợp lệ cho nhiều Type. Thiếu type vẫn là Type 01, kể cả khi payload có prefix reference 01/02 hoặc tag 80.
- Giữ Unicode, length 00, thứ tự root và tag trùng như trước (ngoại trừ CRC). Không cấm ký tự A/M/E lặp. Không áp bảng ký tự ans, schema mạng thanh toán khác hoặc quy tắc ISO không có trong workbook.
- Country, currency, MCC, GUID/AID chưa đối chiếu danh mục đầy đủ. Format số tiền ans chưa có regex/điều kiện dương hoặc số lẻ; không tự áp quy tắc tài chính ngoài tài liệu.

`VALID` nghĩa là vượt qua phạm vi các kiểm tra đã triển khai, không phải chứng nhận đầy đủ EMVCo/ISO hoặc khả năng thanh toán. Không sửa ví dụ Excel bị sai length/CRC để biến chúng thành bằng chứng dương.

## CRC và dữ liệu nhạy cảm

CRC-16/CCITT-FALSE dùng polynomial `0x1021`, initial `0xFFFF`, không reflection, XOR output `0x0000`. Vector `123456789` có CRC `29B1`.

Tag 63 phải có đúng một lần ở cấp ngoài, nằm cuối, length 04 và bốn ký tự hexadecimal. CRC được tính đến hết header `6304`, không gồm bốn ký tự CRC. Kiểm tra chấp nhận chữ hex hoa hoặc thường.

CRC phát hiện một số lỗi dữ liệu, **không phải xác thực mật mã hoặc cơ chế chống giả mạo**. Người sửa payload có thể tính lại CRC. CRC khớp không chứng minh merchant đáng tin cậy, nguồn QR xác thực hoặc không có va chạm.

Handler lỗi ngoài dự kiến chỉ ghi mã lỗi và loại exception để chẩn đoán; không ghi QR, exception message, cause hoặc stack trace. HTTP response lỗi không chứa exception/stack trace. Response phân tích vẫn chứa value theo chức năng API, vì vậy cần bảo vệ response như dữ liệu QR gốc. Không bật DEBUG/TRACE HTTP hoặc tự ghi request/response vào log khi dùng dữ liệu thật.

## Kiểm thử bằng Postman và PowerShell

Trong Postman:

1. Khởi động ứng dụng, tạo request **POST** đến `http://localhost:8080/api/qr/analyze`.
2. Chọn **Body → raw → JSON**; gửi `Content-Type: application/json` và `Accept: application/json`.
3. Dán request QR mẫu: mong đợi HTTP 422, `SPEC_VIOLATION`, CRC `VALID=5E14`, 10 tag.
4. Thử `{}`, `null`, `{"qr":null}`, `{"qr":123}` để phân biệt các dạng input.
5. Thử JSON thiếu dấu đóng: mong đợi 400 / `INVALID_JSON`.
6. Thử `{"qr":"000"}`: mong đợi 422 / `INCOMPLETE_HEADER`.
7. Thử `{"qr":"9901\ud800"}`: mong đợi 422 / `INVALID_UNICODE`.
8. Trong QR mẫu, đổi **lần xuất hiện đầu tiên** của `DONG QUE 79` thành `DONG QUE 78`, giữ CRC: mong đợi 422 / `CRC_MISMATCH`, computed CRC `93F6`.

JUnit có fixture cho cả bốn Type được tạo riêng bằng TLV builder và CRC calculator; QR mẫu gốc không bị sửa để làm fixture hợp lệ.

PowerShell:

```powershell
$qrText = '00020101021126300010A00000077501120682000002105204549953037045802VN5911DONG QUE 796007LAMDONG62270311DONG QUE 790708DONGQUE163045E14'
$requestJson = @{ qr = $qrText } | ConvertTo-Json

try {
    $response = Invoke-RestMethod `
        -Uri 'http://localhost:8080/api/qr/analyze' `
        -Method Post `
        -ContentType 'application/json; charset=utf-8' `
        -Headers @{ Accept = 'application/json' } `
        -Body ([System.Text.Encoding]::UTF8.GetBytes($requestJson))

    $response | ConvertTo-Json -Depth 20
}
catch {
    $_.ErrorDetails.Message
}
```

QR mẫu trả 422 nên PowerShell đi vào `catch`; đây là kết quả nghiệp vụ đã dự kiến.
