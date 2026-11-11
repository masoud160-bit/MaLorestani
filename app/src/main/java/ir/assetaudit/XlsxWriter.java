package ir.assetaudit;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Standards-compliant XLSX writer with polished, filterable, RTL report sheets. */
public final class XlsxWriter {
    private XlsxWriter() { }

    public static final class Sheet {
        public final String name;
        public final List<List<String>> rows = new ArrayList<>();

        public Sheet(String name, String... headers) {
            this.name = name;
            add(headers);
        }

        public void add(String... values) {
            List<String> row = new ArrayList<>();
            for (String value : values) row.add(value == null ? "" : value);
            rows.add(row);
        }
    }

    public static void write(OutputStream output, List<Sheet> sheets) throws IOException {
        if (sheets == null || sheets.isEmpty()) throw new IOException("هیچ گزارشی برای ذخیره وجود ندارد.");
        ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8);
        writeEntry(zip, "[Content_Types].xml", contentTypes(sheets.size()));
        writeEntry(zip, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                + "</Relationships>");
        writeEntry(zip, "xl/workbook.xml", workbook(sheets));
        writeEntry(zip, "xl/_rels/workbook.xml.rels", workbookRels(sheets.size()));
        writeEntry(zip, "xl/styles.xml", styles());
        for (int i = 0; i < sheets.size(); i++) writeSheet(zip, i + 1, sheets.get(i));
        zip.finish();
        zip.flush();
    }

    private static String contentTypes(int count) {
        StringBuilder s = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
        for (int i = 1; i <= count; i++) {
            s.append("<Override PartName=\"/xl/worksheets/sheet").append(i)
                    .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
        }
        return s.append("</Types>").toString();
    }

    private static String workbook(List<Sheet> sheets) {
        StringBuilder s = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\""
                + " xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                + "<bookViews><workbookView activeTab=\"0\" firstSheet=\"0\"/></bookViews><sheets>");
        for (int i = 0; i < sheets.size(); i++) {
            s.append("<sheet name=\"").append(escape(sheets.get(i).name)).append("\" sheetId=\"")
                    .append(i + 1).append("\" r:id=\"rId").append(i + 1).append("\"/>");
        }
        return s.append("</sheets></workbook>").toString();
    }

    private static String workbookRels(int count) {
        StringBuilder s = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        for (int i = 1; i <= count; i++) {
            s.append("<Relationship Id=\"rId").append(i)
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet")
                    .append(i).append(".xml\"/>");
        }
        s.append("<Relationship Id=\"rId").append(count + 1)
                .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>");
        return s.append("</Relationships>").toString();
    }

    private static String styles() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<fonts count=\"2\">"
                + "<font><name val=\"B Nazanin\"/><charset val=\"178\"/><family val=\"2\"/><sz val=\"12\"/><color rgb=\"FF26364E\"/></font>"
                + "<font><name val=\"B Nazanin\"/><charset val=\"178\"/><family val=\"2\"/><b/><sz val=\"13\"/><color rgb=\"FFFFFFFF\"/></font>"
                + "</fonts>"
                + "<fills count=\"4\">"
                + "<fill><patternFill patternType=\"none\"/></fill>"
                + "<fill><patternFill patternType=\"gray125\"/></fill>"
                + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF1C2A43\"/>"
                + "<bgColor indexed=\"64\"/></patternFill></fill>"
                + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFE5EFFA\"/>"
                + "<bgColor indexed=\"64\"/></patternFill></fill>"
                + "</fills>"
                + "<borders count=\"2\">"
                + "<border><left/><right/><top/><bottom/><diagonal/></border>"
                + "<border><left/><right/><top/><bottom style=\"thin\">"
                + "<color rgb=\"FFB8C9DE\"/></bottom><diagonal/></border>"
                + "</borders>"
                + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" applyFont=\"1\"/></cellStyleXfs>"
                + "<cellXfs count=\"5\">"
                + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyAlignment=\"1\">"
                + "<alignment horizontal=\"right\" vertical=\"center\" readingOrder=\"2\" wrapText=\"1\"/></xf>"
                + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"1\" xfId=\"0\""
                + " applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyAlignment=\"1\">"
                + "<alignment horizontal=\"center\" vertical=\"center\" readingOrder=\"2\"/></xf>"
                + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"3\" borderId=\"0\" xfId=\"0\""
                + " applyFont=\"1\" applyFill=\"1\" applyAlignment=\"1\">"
                + "<alignment horizontal=\"right\" vertical=\"center\" readingOrder=\"2\" wrapText=\"1\"/></xf>"
                + "<xf numFmtId=\"49\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\""
                + " applyNumberFormat=\"1\" applyFont=\"1\" applyAlignment=\"1\">"
                + "<alignment horizontal=\"right\" vertical=\"center\" readingOrder=\"2\"/></xf>"
                + "<xf numFmtId=\"49\" fontId=\"0\" fillId=\"3\" borderId=\"0\" xfId=\"0\""
                + " applyNumberFormat=\"1\" applyFont=\"1\" applyFill=\"1\" applyAlignment=\"1\">"
                + "<alignment horizontal=\"right\" vertical=\"center\" readingOrder=\"2\"/></xf>"
                + "</cellXfs>"
                + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
                + "</styleSheet>";
    }

    private static void writeSheet(ZipOutputStream zip, int number, Sheet sheet) throws IOException {
        zip.putNextEntry(new ZipEntry("xl/worksheets/sheet" + number + ".xml"));
        Writer w = new OutputStreamWriter(zip, StandardCharsets.UTF_8);
        w.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<sheetPr><tabColor rgb=\"" + tabColor(sheet.name) + "\"/></sheetPr>"
                + "<sheetViews><sheetView rightToLeft=\"1\" showGridLines=\"0\" topLeftCell=\"A1\""
                + " zoomScale=\"100\" workbookViewId=\"0\">"
                + "<selection activeCell=\"A1\" sqref=\"A1\"/>"
                + "</sheetView></sheetViews><sheetFormatPr defaultRowHeight=\"29\"/>"
                + "<cols>");
        List<String> headers = sheet.rows.get(0);
        for (int c = 0; c < headers.size(); c++) {
            String header = headers.get(c);
            // Deliberately bounded widths: the previous 44-character columns were too wide.
            int width = columnWidth(header);
            w.write("<col min=\"" + (c + 1) + "\" max=\"" + (c + 1)
                    + "\" width=\"" + width + "\" customWidth=\"1\"/>");
        }
        w.write("</cols><sheetData>");
        for (int r = 0; r < sheet.rows.size(); r++) {
            List<String> row = sheet.rows.get(r);
            w.write("<row r=\"" + (r + 1) + "\""
                    + (r == 0 ? " ht=\"32\" customHeight=\"1\"" : " ht=\"29\" customHeight=\"1\"") + ">");
            for (int c = 0; c < row.size(); c++) {
                int style = r == 0 ? 1 : c == 0 ? (r % 2 == 0 ? 4 : 3) : (r % 2 == 0 ? 2 : 0);
                // An explicit Persian rich-text run avoids inconsistent first-render font
                // substitution seen in some desktop Excel installations.
                w.write("<c r=\"" + columnName(c) + (r + 1) + "\" t=\"inlineStr\" s=\""
                        + style + "\"><is><r><rPr><rFont val=\"B Nazanin\"/>"
                        + "<charset val=\"178\"/><family val=\"2\"/>"
                        + (r == 0 ? "<b/><sz val=\"13\"/><color rgb=\"FFFFFFFF\"/>"
                                : "<sz val=\"12\"/><color rgb=\"FF26364E\"/>")
                        + "</rPr><t xml:space=\"preserve\">");
                w.write(escape(row.get(c)));
                w.write("</t></r></is></c>");
            }
            w.write("</row>");
        }
        w.write("</sheetData>");
        if (sheet.rows.size() > 1 && !sheet.rows.get(0).isEmpty()) {
            w.write("<autoFilter ref=\"A1:" + columnName(sheet.rows.get(0).size() - 1)
                    + sheet.rows.size() + "\"/>");
        }
        w.write("</worksheet>");
        w.flush(); // The writer must not close the shared ZipOutputStream.
        zip.closeEntry();
    }

    private static int columnWidth(String header) {
        if (header.contains("پلاک")) return 12;
        // This is an approximate display width, not a restriction on entered digits.
        if (header.contains("آمایش")) return 14;
        if (header.contains("زمان") || header.contains("تاریخ")) return 20;
        if (header.contains("وضعیت")) return 17;
        if (header.contains("نام")) return 25;
        return 19;
    }

    private static String tabColor(String name) {
        if ("بررسی فرسودگی".equals(name)) return "FFF5A623";
        if ("مطابق".equals(name)) return "FF20845A";
        if ("مغایرت نام".equals(name)) return "FFD39A35";
        if ("مشاهده‌نشده".equals(name)) return "FF8A9BAF";
        if ("خارج از فهرست".equals(name)) return "FFB94E4E";
        return "FF265CA1";
    }

    private static String columnName(int number) {
        StringBuilder value = new StringBuilder();
        do { value.insert(0, (char) ('A' + (number % 26))); number = number / 26 - 1; }
        while (number >= 0);
        return value.toString();
    }

    private static String escape(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&': out.append("&amp;"); break;
                case '<': out.append("&lt;"); break;
                case '>': out.append("&gt;"); break;
                case '"': out.append("&quot;"); break;
                case '\'': out.append("&apos;"); break;
                default:
                    if (c == '\t' || c == '\n' || c == '\r' || c >= 0x20) out.append(c);
            }
        }
        return out.toString();
    }

    private static void writeEntry(ZipOutputStream zip, String path, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
