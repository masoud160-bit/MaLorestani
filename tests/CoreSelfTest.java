import ir.assetaudit.PlateNormalizer;
import ir.assetaudit.XlsxReader;
import ir.assetaudit.XlsxWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import java.nio.charset.StandardCharsets;

/** Plain-Java regression checks for the import/export core. */
public final class CoreSelfTest {
    private static void equal(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("expected=" + expected + "; actual=" + actual);
        }
    }

    public static void main(String[] args) throws Exception {
        equal("001234", PlateNormalizer.normalize("۰۰۱۲۳۴"));
        equal("000123", PlateNormalizer.normalize(" ٠٠٠١٢٣ "));
        equal("AB 003", PlateNormalizer.normalize("AB  ۰۰۳"));
        equal("00001-A", PlateNormalizer.normalize("00001-A"));

        File file = Files.createTempFile("asset_audit_test_", ".xlsx").toFile();
        try {
            XlsxWriter.Sheet s1 = new XlsxWriter.Sheet("مغایرت نام", "پلاک", "نام ثبت‌شده", "نام مشاهده‌شده");
            s1.add("00000123", "میز و صندلی", "میز اداری & <رنگی>");
            s1.add("A-004", "چراغ \"دیواری\"", "دستگاه");
            XlsxWriter.Sheet s2 = new XlsxWriter.Sheet("مشاهده‌نشده", "پلاک", "نام");
            s2.add("001", "کمد");
            XlsxWriter.Sheet s3 = new XlsxWriter.Sheet("خارج از فهرست", "پلاک", "نام");
            s3.add("999", "رایانه");
            XlsxWriter.Sheet s4 = new XlsxWriter.Sheet("مطابق", "پلاک", "نام ثبت‌شده", "زمان تأیید");
            s4.add("00000008", "میز مدیریت", "2026/11/09 14:10");
            XlsxWriter.Sheet s5 = new XlsxWriter.Sheet("بررسی فرسودگی", "پلاک", "شماره آمایش", "نام ثبت‌شده",
                    "وضعیت بررسی", "نام مشاهده‌شده", "تاریخ علامت‌گذاری");
            s5.add("00000008", "00001234", "میز مدیریت", "مطابق", "میز مدیریت", "2026/11/12 10:00");
            s5.add("00000009", "12345678901234567890", "پرینتر", "مشاهده‌نشده", "", "2026/11/12 10:01");
            try (FileOutputStream output = new FileOutputStream(file)) {
                XlsxWriter.write(output, Arrays.asList(s1, s2, s3, s4, s5));
            }
            List<XlsxReader.SheetInfo> sheets = XlsxReader.inspect(file);
            equal(5, sheets.size());
            equal("مغایرت نام", sheets.get(0).name);
            equal("مطابق", sheets.get(3).name);
            equal("بررسی فرسودگی", sheets.get(4).name);
            List<List<String>> rows = XlsxReader.readRows(file, sheets.get(0));
            equal(3, rows.size());
            equal("00000123", rows.get(1).get(0));
            equal("میز اداری & <رنگی>", rows.get(1).get(2));
            equal("چراغ \"دیواری\"", rows.get(2).get(1));
            equal("001", XlsxReader.readRows(file, sheets.get(1)).get(1).get(0));
            equal("999", XlsxReader.readRows(file, sheets.get(2)).get(1).get(0));
            equal("00000008", XlsxReader.readRows(file, sheets.get(3)).get(1).get(0));
            equal("میز مدیریت", XlsxReader.readRows(file, sheets.get(3)).get(1).get(1));
            equal("00000008", XlsxReader.readRows(file, sheets.get(4)).get(1).get(0));
            equal(6, XlsxReader.readRows(file, sheets.get(4)).get(0).size());
            equal("شماره آمایش", XlsxReader.readRows(file, sheets.get(4)).get(0).get(1));
            equal("00001234", XlsxReader.readRows(file, sheets.get(4)).get(1).get(1));
            equal("12345678901234567890", XlsxReader.readRows(file, sheets.get(4)).get(2).get(1));
            equal("2026/11/12 10:00", XlsxReader.readRows(file, sheets.get(4)).get(1).get(5));
            equal("مطابق", XlsxReader.readRows(file, sheets.get(4)).get(1).get(3));
            try (ZipFile archive = new ZipFile(file)) {
                String styles = new String(archive.getInputStream(archive.getEntry("xl/styles.xml")).readAllBytes(), StandardCharsets.UTF_8);
                String report = new String(archive.getInputStream(archive.getEntry("xl/worksheets/sheet4.xml")).readAllBytes(), StandardCharsets.UTF_8);
                String flaggedReport = new String(archive.getInputStream(archive.getEntry("xl/worksheets/sheet5.xml")).readAllBytes(), StandardCharsets.UTF_8);
                if (!styles.contains("FF1C2A43") || !styles.contains("FFE5EFFA")
                        || !styles.contains("B Nazanin")
                        || !styles.contains("numFmtId=\"49\"")) {
                    throw new AssertionError("Expected styled report header and alternating row fill");
                }
                if (!flaggedReport.contains("rightToLeft=\"1\"")
                        || !flaggedReport.contains("<autoFilter")
                        || !flaggedReport.contains("00000008")
                        || flaggedReport.contains("توضیح برای پیگیری")
                        || !flaggedReport.contains("2026/11/12 10:00")
                        || !flaggedReport.contains("00001234")
                        || !flaggedReport.contains("12345678901234567890")
                        || !flaggedReport.contains("width=\"14\"")) {
                    throw new AssertionError("Expected filterable flagged report with six columns and an audit-number field");
                }
                if (!styles.contains("applyFont=\"1\"")
                        || !report.contains("<rFont val=\"B Nazanin\"/>")
                        || !report.contains("<charset val=\"178\"/>")
                        || !report.contains("width=\"12\"")
                        || !report.contains("width=\"25\"")
                        || !report.contains("FFE5EFFA") && !styles.contains("FFE5EFFA")) {
                    throw new AssertionError("Expected explicit Persian font, compact widths, and stripes");
                }
                if (!report.contains("rightToLeft=\"1\"") || !report.contains("showGridLines=\"0\"")
                        || !report.contains("topLeftCell=\"A1\"") || report.contains("<pane")
                        || !report.contains("<selection activeCell=\"A1\"")
                        || !report.contains("<autoFilter") || !report.contains("FF20845A")) {
                    throw new AssertionError("Expected stable RTL initial view and filterable matched report");
                }
            }
        } finally {
            Files.deleteIfExists(file.toPath());
        }
        testSharedStringsAndNumberStyle();
        System.out.println("PASS: normalization, five-sheet styled XLSX, Persian text, flags, shared strings, padded numbers");
    }

    private static void testSharedStringsAndNumberStyle() throws Exception {
        File file = Files.createTempFile("asset_audit_shared_", ".xlsx").toFile();
        try {
            try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                add(zip, "xl/workbook.xml", "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                        + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                        + "<sheets><sheet name=\"فهرست\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
                add(zip, "xl/_rels/workbook.xml.rels", "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                        + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" "
                        + "Target=\"/xl/worksheets/sheet1.xml\"/></Relationships>");
                add(zip, "xl/sharedStrings.xml", "<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                        + "<si><t>پلاک</t></si><si><t>نام اثاثیه</t></si>"
                        + "<si><r><t>میز</t></r><r><t> اداری</t></r></si></sst>");
                add(zip, "xl/styles.xml", "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                        + "<numFmts count=\"1\"><numFmt numFmtId=\"164\" formatCode=\"000000\"/></numFmts>"
                        + "<cellXfs count=\"2\"><xf numFmtId=\"0\"/><xf numFmtId=\"164\"/></cellXfs></styleSheet>");
                add(zip, "xl/worksheets/sheet1.xml", "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>"
                        + "<row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c>"
                        + "<c r=\"B1\" t=\"s\"><v>1</v></c></row>"
                        + "<row r=\"2\"><c r=\"A2\" s=\"1\"><v>123</v></c>"
                        + "<c r=\"B2\" t=\"s\"><v>2</v></c></row></sheetData></worksheet>");
            }
            List<XlsxReader.SheetInfo> sheets = XlsxReader.inspect(file);
            equal("فهرست", sheets.get(0).name);
            List<List<String>> rows = XlsxReader.readRows(file, sheets.get(0));
            equal("پلاک", rows.get(0).get(0));
            equal("000123", rows.get(1).get(0));
            equal("میز اداری", rows.get(1).get(1));
        } finally {
            Files.deleteIfExists(file.toPath());
        }
    }

    private static void add(ZipOutputStream zip, String name, String xml) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(xml.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
