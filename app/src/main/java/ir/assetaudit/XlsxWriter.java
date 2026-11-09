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

/** Minimal standards-compliant XLSX writer with three readable, RTL report sheets. */
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
                + " xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>");
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
                + "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Arial\"/></font>"
                + "<font><b/><sz val=\"11\"/><name val=\"Arial\"/></font></fonts>"
                + "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill>"
                + "<fill><patternFill patternType=\"gray125\"/></fill></fills>"
                + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
                + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                + "<cellXfs count=\"2\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
                + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/></cellXfs>"
                + "</styleSheet>";
    }

    private static void writeSheet(ZipOutputStream zip, int number, Sheet sheet) throws IOException {
        zip.putNextEntry(new ZipEntry("xl/worksheets/sheet" + number + ".xml"));
        Writer w = new OutputStreamWriter(zip, StandardCharsets.UTF_8);
        w.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<sheetViews><sheetView rightToLeft=\"1\" workbookViewId=\"0\">"
                + "<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>"
                + "</sheetView></sheetViews><sheetFormatPr defaultRowHeight=\"17\"/>"
                + "<cols><col min=\"1\" max=\"1\" width=\"22\" customWidth=\"1\"/>"
                + "<col min=\"2\" max=\"3\" width=\"36\" customWidth=\"1\"/>"
                + "<col min=\"4\" max=\"4\" width=\"22\" customWidth=\"1\"/></cols><sheetData>");
        for (int r = 0; r < sheet.rows.size(); r++) {
            List<String> row = sheet.rows.get(r);
            w.write("<row r=\"" + (r + 1) + "\">");
            for (int c = 0; c < row.size(); c++) {
                w.write("<c r=\"" + columnName(c) + (r + 1) + "\" t=\"inlineStr\"");
                if (r == 0) w.write(" s=\"1\"");
                w.write("><is><t xml:space=\"preserve\">");
                w.write(escape(row.get(c)));
                w.write("</t></is></c>");
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
