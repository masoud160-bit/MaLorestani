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
            try (FileOutputStream output = new FileOutputStream(file)) {
                XlsxWriter.write(output, Arrays.asList(s1, s2, s3, s4));
            }
            List<XlsxReader.SheetInfo> sheets = XlsxReader.inspect(file);
            equal(4, sheets.size());
            equal("مغایرت نام", sheets.get(0).name);
            equal("مطابق", sheets.get(3).name);
            List<List<String>> rows = XlsxReader.readRows(file, sheets.get(0));
            equal(3, rows.size());
            equal("00000123", rows.get(1).get(0));
            equal("میز اداری & <رنگی>", rows.get(1).get(2));
            equal("چراغ \"دیواری\"", rows.get(2).get(1));
            equal("001", XlsxReader.readRows(file, sheets.get(1)).get(1).get(0));
            equal("999", XlsxReader.readRows(file, sheets.get(2)).get(1).get(0));
            equal("00000008", XlsxReader.readRows(file, sheets.get(3)).get(1).get(0));
            equal("میز مدیریت", XlsxReader.readRows(file, sheets.get(3)).get(1).get(1));
            try (ZipFile archive = new ZipFile(file)) {
                String styles = new String(archive.getInputStream(archive.getEntry("xl/styles.xml")).readAllBytes(), StandardCharsets.UTF_8);
                String report = new String(archive.getInputStream(archive.getEntry("xl/worksheets/sheet4.xml")).readAllBytes(), StandardCharsets.UTF_8);
                if (!styles.contains("FF1C2A43") || !styles.contains("FFF0F5FB")
                        || !styles.contains("B Nazanin")
                        || !styles.contains("numFmtId=\"49\"")) {
                    throw new AssertionError("Expected styled report header and alternating row fill");
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
        System.out.println("PASS: normalization, four-sheet styled XLSX, Persian text, shared strings, padded numbers");
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
