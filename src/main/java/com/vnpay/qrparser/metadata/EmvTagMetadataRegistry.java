package com.vnpay.qrparser.metadata;

import com.vnpay.qrparser.model.TagMetadata;
import com.vnpay.qrparser.model.QrType;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Metadata được khóa theo cả sheet và đường dẫn tag. Không đọc Excel tại runtime. */
public class EmvTagMetadataRegistry {

    private static final TagMetadata UNKNOWN_TAG = new TagMetadata(
            "Unknown Tag", "Chưa có metadata cho tag này trong sheet Type 01.", Map.of());

    private static final Map<QrType, Map<String, TagMetadata>> METADATA_BY_TYPE = createProfiles();

    public TagMetadata lookup(String path) {
        return lookup(QrType.TYPE_01, path);
    }

    public TagMetadata lookup(QrType type, String path) {
        // Tra cứu đúng đường dẫn; không lấy metadata cấp ngoài cho một tag con.
        return METADATA_BY_TYPE.get(type).getOrDefault(path, type == QrType.TYPE_01 ? UNKNOWN_TAG
                : new TagMetadata("Unknown Tag", "Chưa có metadata cho tag này trong sheet " + type.sheetName() + ".", Map.of()));
    }

    private static Map<QrType, Map<String, TagMetadata>> createProfiles() {
        Map<QrType, Map<String, TagMetadata>> profiles = new java.util.EnumMap<>(QrType.class);
        Map<String, TagMetadata> type01 = createMetadata();
        profiles.put(QrType.TYPE_01, type01);
        for (QrType type : new QrType[]{QrType.TYPE_02, QrType.TYPE_03, QrType.TYPE_04}) {
            Map<String, TagMetadata> metadata = new HashMap<>(type01);
            // Các đường dẫn riêng không được kế thừa từ Type 01.
            metadata.keySet().removeIf(path -> path.startsWith("62/"));
            if (type == QrType.TYPE_02) {
                metadata.remove("54"); // Không có tag 54 trong sheet Type 02.
                metadata.put("01", new TagMetadata("Point of Initiation Method",
                        "11: không có kỳ cước; 12: có kỳ cước tại template 80.", Map.of("11", "QR tĩnh", "12", "QR động")));
                addTag(metadata, "62/03", "Store Label", "Tên terminal, tên dịch vụ.");
                addTag(metadata, "62/06", "Consumer Label", "Mã khách hàng, số điện thoại khách hàng hoặc số hóa đơn.");
                addTag(metadata, "62/07", "Terminal Label", "Mã terminal, mã dịch vụ.");
                addTag(metadata, "62/08", "Purpose of Transaction", "Tên dịch vụ.");
                addTag(metadata, "80", "Term", "Template chứa thông tin kỳ cước cần thanh toán.");
                addTag(metadata, "80/00", "Globally Unique Identifier", "AID bao gồm RID theo ISO/IEC 7816-5.");
                addTag(metadata, "80/01", "Term", "Thông tin kỳ cước cần thanh toán.");
            } else {
                boolean product = type == QrType.TYPE_03;
                metadata.put("01", new TagMetadata("Point of Initiation Method",
                        product ? "Type 03 mô tả QR tĩnh (11)." : "Type 04 mô tả QR động (12).",
                        product ? Map.of("11", "QR tĩnh") : Map.of("12", "QR động")));
                addTag(metadata, "54", "Transaction Amount", product ? "Số tiền của sản phẩm."
                        : "Số tiền của đơn hàng do Cổng thanh toán VNPAY QR cung cấp.");
                addTag(metadata, "62/03", "Store Label", product ? "Tên cửa hàng." : "Tên terminal, tên website.");
                addTag(metadata, "62/05", "Reference Label", (product ? "02 + thời hạn thanh toán (yymmddhhmm) + mã sản phẩm."
                        : "01 + thời hạn thanh toán (yymmddhhmm) + mã đơn hàng."));
                addTag(metadata, "62/07", "Terminal Label", product ? "Mã cửa hàng, mã chi nhánh." : "Mã terminal, mã website.");
                addTag(metadata, "62/08", "Purpose of Transaction", product ? "Tên sản phẩm." : "Mô tả giao dịch thanh toán.");
                metadata.put("62/09", type01.get("62/09"));
            }
            profiles.put(type, Map.copyOf(metadata));
        }
        return Map.copyOf(profiles);
    }

    private static Map<String, TagMetadata> createMetadata() {
        Map<String, TagMetadata> metadata = new HashMap<>();

        metadata.put("00", new TagMetadata("Payload Format Indicator",
                "Phiên bản dữ liệu có giá trị 01; các giá trị khác được dự phòng.",
                Map.of("01", "Phiên bản dữ liệu 01")));
        metadata.put("01", new TagMetadata("Point of Initiation Method",
                "Phương thức khởi tạo: 11 là QR tĩnh, 12 là QR động.",
                Map.of("11", "QR tĩnh", "12", "QR động")));

        // Các nhóm ID cấp ngoài được dành riêng cho tổ chức thanh toán.
        addReservedRange(metadata, 2, 3, "Dành riêng cho Visa.");
        addReservedRange(metadata, 4, 5, "Dành riêng cho Mastercard.");
        addReservedRange(metadata, 6, 8, "Dành riêng cho EMVCo.");
        addReservedRange(metadata, 9, 10, "Dành riêng cho Discover.");
        addReservedRange(metadata, 11, 12, "Dành riêng cho Amex.");
        addReservedRange(metadata, 13, 14, "Dành riêng cho JCB.");
        addReservedRange(metadata, 15, 16, "Dành riêng cho UnionPay.");
        addReservedRange(metadata, 17, 25, "Dành riêng cho EMVCo.");
        addTag(metadata, "26", "Merchant Account Information", "Thông tin tài khoản Merchant.");
        addReservedRange(metadata, 27, 51, "Template dành cho các mạng thanh toán khác.");

        addTag(metadata, "52", "Merchant Category Code", "Mã danh mục Merchant theo ISO 18245.");
        metadata.put("53", new TagMetadata("Transaction Currency Code",
                "Mã tiền tệ theo ISO 4217; mã 704 là VND.", Map.of("704", "VND")));
        addTag(metadata, "54", "Transaction Amount", "Số tiền hóa đơn tại điểm thanh toán offline.");
        addTag(metadata, "58", "Country Code", "Mã quốc gia theo ISO 3166-1 alpha-2.");
        addTag(metadata, "59", "Merchant Name", "Tên giao dịch của Merchant.");
        addTag(metadata, "60", "Merchant City", "Thành phố mà Merchant hoạt động.");
        addTag(metadata, "61", "Postal Code", "Mã bưu điện nơi Merchant hoạt động.");
        addTag(metadata, "62", "Additional Data Field", "Template chứa thông tin bổ sung.");
        addTag(metadata, "63", "CRC", "Trường checksum của QR Code; kết quả kiểm tra được cung cấp bởi CRC validator.");

        // Cùng ID nhưng khác đường dẫn sẽ có metadata khác nhau.
        addTag(metadata, "26/00", "Globally Unique Identifier", "Định danh duy nhất toàn cầu (GUID).");
        addTag(metadata, "26/01", "Merchant ID", "Mã số thuế hoặc mã số doanh nghiệp của Merchant.");
        addTag(metadata, "62/01", "Bill Number", "Số biên lai do Merchant cung cấp.");
        addTag(metadata, "62/03", "Store Label", "Tên Terminal hoặc tên cửa hàng.");
        addTag(metadata, "62/07", "Terminal Label", "Mã Terminal, mã cửa hàng hoặc mã POS.");
        addTag(metadata, "62/08", "Purpose of Transaction", "Ghi chú cho Terminal.");
        addTag(metadata, "62/09", "Additional Consumer Data Request",
                "Yêu cầu dữ liệu khách hàng: A = địa chỉ, M = số điện thoại, E = email; có thể kết hợp nhiều ký tự.");

        // Chỉ bảng tạm ở trên có thể sửa; registry giữ bản không thể sửa đổi.
        return Map.copyOf(metadata);
    }

    private static void addTag(Map<String, TagMetadata> metadata, String path,
                               String name, String description) {
        metadata.put(path, new TagMetadata(name, description, Map.of()));
    }

    private static void addReservedRange(Map<String, TagMetadata> metadata,
                                         int firstId, int lastId, String description) {
        for (int id = firstId; id <= lastId; id++) {
            String path = String.format(Locale.ROOT, "%02d", id);
            addTag(metadata, path, "Merchant Account Information", description);
        }
    }
}
