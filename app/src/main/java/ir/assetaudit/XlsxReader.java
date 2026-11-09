package ir.assetaudit;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Streaming XLSX parser using only Android/Java platform APIs. No network or POI. */
public final class XlsxReader {
    private static final long MAX_XML_BYTES = 64L * 1024 * 1024;
    public static final int MAX_ROWS = 50000;
    public static final int MAX_COLUMNS = 128;
    private static final int MAX_CELL_LENGTH = 10000;

    private XlsxReader() { }

    public static final class SheetInfo {
        public final String name;
        public final String path;

        SheetInfo(String name, String path) {
            this.name = name;
            this.path = path;
        }
    }

    public static List<SheetInfo> inspect(File file) throws IOException {
        try (ZipFile zip = new ZipFile(file)) {
            WorkbookHandler wb = new WorkbookHandler();
            parse(zip, "xl/workbook.xml", wb);
            RelHandler rel = new RelHandler();
            parse(zip, "xl/_rels/workbook.xml.rels", rel);
            List<SheetInfo> result = new ArrayList<>();
            for (String[] pair : wb.sheets) {
                String target = rel.targets.get(pair[1]);
                if (target == null) continue;
                String path = normalizePath(target);
                if (zip.getEntry(path) != null) result.add(new SheetInfo(pair[0], path));
            }
            if (result.isEmpty()) throw new IOException("هیچ برگهٔ قابل خواندنی در اکسل پیدا نشد.");
            return result;
        }
    }

    public static List<List<String>> readRows(File file, SheetInfo sheet) throws IOException {
        try (ZipFile zip = new ZipFile(file)) {
            List<String> shared = new ArrayList<>();
            if (zip.getEntry("xl/sharedStrings.xml") != null) {
                parse(zip, "xl/sharedStrings.xml", new SharedStringsHandler(shared));
            }
            StylesHandler styles = new StylesHandler();
            if (zip.getEntry("xl/styles.xml") != null) {
                parse(zip, "xl/styles.xml", styles);
            }
            SheetHandler handler = new SheetHandler(shared, styles);
            parse(zip, sheet.path, handler);
            if (handler.rows.isEmpty()) throw new IOException("برگهٔ انتخاب‌شده خالی است.");
            return handler.rows;
        }
    }

    private static String normalizePath(String target) throws IOException {
        String raw = target.replace('\\', '/');
        String[] parts = (raw.startsWith("/") ? raw.substring(1)
                : raw.startsWith("xl/") ? raw : "xl/" + raw).split("/");
        List<String> stack = new ArrayList<>();
        for (String part : parts) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) {
                if (stack.isEmpty()) throw new IOException("مسیر نامعتبر در فایل اکسل.");
                stack.remove(stack.size() - 1);
            } else stack.add(part);
        }
        String path = String.join("/", stack);
        if (!path.startsWith("xl/")) throw new IOException("مسیر نامعتبر در فایل اکسل.");
        return path;
    }

    private static void parse(ZipFile zip, String path, DefaultHandler handler) throws IOException {
        ZipEntry entry = zip.getEntry(path);
        if (entry == null) throw new IOException("بخش موردنیاز در اکسل پیدا نشد: " + path);
        if (entry.getSize() > MAX_XML_BYTES) throw new IOException("حجم برگهٔ اکسل بیش از حد مجاز است.");
        try (InputStream in = new LimitedInputStream(zip.getInputStream(entry), MAX_XML_BYTES)) {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            XMLReader parser = factory.newSAXParser().getXMLReader();
            // Never load DTDs or entities from an untrusted Excel file.
            parser.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
            try { parser.setFeature("http://xml.org/sax/features/external-general-entities", false); }
            catch (SAXException ignored) { }
            try { parser.setFeature("http://xml.org/sax/features/external-parameter-entities", false); }
            catch (SAXException ignored) { }
            try { parser.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); }
            catch (SAXException ignored) { }
            parser.setContentHandler(handler);
            parser.parse(new InputSource(in));
        } catch (ParserConfigurationException | SAXException ex) {
            throw new IOException("ساختار فایل اکسل قابل خواندن نیست: " + ex.getMessage(), ex);
        }
    }

    private static String tag(String localName, String qName) {
        if (localName != null && !localName.isEmpty()) return localName;
        int colon = qName.indexOf(':');
        return colon < 0 ? qName : qName.substring(colon + 1);
    }

    private static String attr(Attributes at, String localName) {
        for (int i = 0; i < at.getLength(); i++) {
            if (localName.equals(at.getLocalName(i)) || localName.equals(at.getQName(i))) {
                return at.getValue(i);
            }
        }
        return null;
    }

    private static final class WorkbookHandler extends DefaultHandler {
        final List<String[]> sheets = new ArrayList<>();

        @Override public void startElement(String uri, String local, String q, Attributes at) {
            if (!tag(local, q).equals("sheet")) return;
            String name = attr(at, "name");
            String id = null;
            for (int i = 0; i < at.getLength(); i++) {
                if ("id".equals(at.getLocalName(i)) || "r:id".equals(at.getQName(i))) {
                    id = at.getValue(i);
                }
            }
            if (name != null && id != null) sheets.add(new String[]{name, id});
        }
    }

    private static final class RelHandler extends DefaultHandler {
        final Map<String, String> targets = new HashMap<>();

        @Override public void startElement(String uri, String local, String q, Attributes at) {
            if (!tag(local, q).equals("Relationship")) return;
            String type = attr(at, "Type");
            if (type != null && type.endsWith("/worksheet") && !"External".equals(attr(at, "TargetMode"))) {
                targets.put(attr(at, "Id"), attr(at, "Target"));
            }
        }
    }

    private static final class SharedStringsHandler extends DefaultHandler {
        final List<String> values;
        final StringBuilder word = new StringBuilder();
        boolean inSi;
        boolean inT;
        int phonetics;

        SharedStringsHandler(List<String> values) { this.values = values; }

        @Override public void startElement(String uri, String local, String q, Attributes at) {
            String name = tag(local, q);
            if ("si".equals(name)) { inSi = true; word.setLength(0); phonetics = 0; }
            else if ("rPh".equals(name)) phonetics++;
            else if (inSi && "t".equals(name) && phonetics == 0) inT = true;
        }

        @Override public void characters(char[] ch, int start, int len) throws SAXException {
            if (inT) {
                if (word.length() + len > MAX_CELL_LENGTH) throw new SAXException("متن یک خانه بیش از حد طولانی است.");
                word.append(ch, start, len);
            }
        }

        @Override public void endElement(String uri, String local, String q) {
            String name = tag(local, q);
            if ("t".equals(name)) inT = false;
            else if ("rPh".equals(name)) phonetics = Math.max(0, phonetics - 1);
            else if ("si".equals(name)) { values.add(word.toString()); inSi = false; }
        }
    }

    private static final class StylesHandler extends DefaultHandler {
        final Map<Integer, Integer> customZeroWidths = new HashMap<>();
        final List<Integer> zeroWidths = new ArrayList<>();
        boolean inXfs;

        @Override public void startElement(String uri, String local, String q, Attributes at) {
            String name = tag(local, q);
            if ("numFmt".equals(name)) {
                try {
                    int id = Integer.parseInt(attr(at, "numFmtId"));
                    String fmt = attr(at, "formatCode");
                    if (fmt != null && fmt.length() <= 30 && fmt.matches("0{2,}")) {
                        customZeroWidths.put(id, fmt.length());
                    }
                } catch (NumberFormatException ignored) { }
            } else if ("cellXfs".equals(name)) inXfs = true;
            else if ("xf".equals(name) && inXfs) {
                int width = 0;
                try { width = customZeroWidths.getOrDefault(Integer.parseInt(attr(at, "numFmtId")), 0); }
                catch (NumberFormatException ignored) { }
                zeroWidths.add(width);
            }
        }

        @Override public void endElement(String uri, String local, String q) {
            if ("cellXfs".equals(tag(local, q))) inXfs = false;
        }

        int width(int styleIndex) {
            return styleIndex >= 0 && styleIndex < zeroWidths.size() ? zeroWidths.get(styleIndex) : 0;
        }
    }

    private static final class SheetHandler extends DefaultHandler {
        final List<String> shared;
        final StylesHandler styles;
        final List<List<String>> rows = new ArrayList<>();
        List<String> currentRow;
        String type;
        int style;
        int column;
        int sequentialColumn;
        StringBuilder cell = new StringBuilder();
        boolean capture;
        boolean inCell;

        SheetHandler(List<String> shared, StylesHandler styles) {
            this.shared = shared;
            this.styles = styles;
        }

        @Override public void startElement(String uri, String local, String q, Attributes at) throws SAXException {
            String name = tag(local, q);
            if ("row".equals(name)) {
                currentRow = new ArrayList<>();
                sequentialColumn = 0;
            } else if ("c".equals(name) && currentRow != null) {
                inCell = true;
                type = attr(at, "t");
                style = -1;
                try { style = Integer.parseInt(attr(at, "s")); }
                catch (NumberFormatException ignored) { }
                String ref = attr(at, "r");
                column = ref == null ? sequentialColumn : columnOf(ref);
                if (column >= MAX_COLUMNS) throw new SAXException("ستون‌های فایل بیش از حد مجاز است.");
                sequentialColumn = column + 1;
                cell.setLength(0);
            } else if (inCell && ("v".equals(name) || ("t".equals(name) && "inlineStr".equals(type)))) {
                capture = true;
            }
        }

        @Override public void characters(char[] ch, int start, int len) throws SAXException {
            if (capture) {
                if (cell.length() + len > MAX_CELL_LENGTH) throw new SAXException("متن یک خانه بیش از حد طولانی است.");
                cell.append(ch, start, len);
            }
        }

        @Override public void endElement(String uri, String local, String q) throws SAXException {
            String name = tag(local, q);
            if ("v".equals(name) || "t".equals(name)) capture = false;
            else if ("c".equals(name) && inCell) {
                String value = decode(cell.toString(), type, styles.width(style), shared);
                while (currentRow.size() <= column) currentRow.add("");
                currentRow.set(column, value);
                inCell = false;
            } else if ("row".equals(name) && currentRow != null) {
                boolean hasText = false;
                for (String v : currentRow) if (!v.trim().isEmpty()) { hasText = true; break; }
                if (hasText) rows.add(currentRow);
                currentRow = null;
                if (rows.size() > MAX_ROWS) throw new SAXException("تعداد ردیف‌ها بیش از حد مجاز است.");
            }
        }

        private static int columnOf(String address) {
            int result = 0;
            for (int i = 0; i < address.length(); i++) {
                char ch = Character.toUpperCase(address.charAt(i));
                if (ch < 'A' || ch > 'Z') break;
                result = result * 26 + (ch - 'A' + 1);
            }
            return result == 0 ? 0 : result - 1;
        }
    }

    private static String decode(String raw, String type, int zeroWidth, List<String> shared) {
        if ("s".equals(type)) {
            try {
                int i = Integer.parseInt(raw.trim());
                return i >= 0 && i < shared.size() ? shared.get(i) : "";
            } catch (NumberFormatException ignored) { return ""; }
        }
        if ("inlineStr".equals(type) || "str".equals(type)) return raw;
        if ("e".equals(type)) return "";
        if ("b".equals(type)) return "1".equals(raw) ? "TRUE" : "FALSE";
        if (raw.isEmpty()) return "";
        try {
            BigDecimal number = new BigDecimal(raw).stripTrailingZeros();
            String plain = number.toPlainString();
            if (zeroWidth > 0 && number.signum() >= 0 && number.scale() <= 0 && plain.length() < zeroWidth) {
                return String.join("", Collections.nCopies(zeroWidth - plain.length(), "0")) + plain;
            }
            return plain;
        } catch (NumberFormatException ignored) { return raw; }
    }

    private static final class LimitedInputStream extends FilterInputStream {
        long remaining;
        LimitedInputStream(InputStream input, long limit) { super(input); remaining = limit; }
        @Override public int read() throws IOException {
            if (remaining == 0) throw new IOException("حجم داده‌های اکسل بیش از حد مجاز است.");
            int c = super.read();
            if (c >= 0) remaining--;
            return c;
        }
        @Override public int read(byte[] b, int off, int len) throws IOException {
            if (remaining == 0) throw new IOException("حجم داده‌های اکسل بیش از حد مجاز است.");
            int count = super.read(b, off, (int) Math.min(len, remaining));
            if (count > 0) remaining -= count;
            return count;
        }
    }
}
