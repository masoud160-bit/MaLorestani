import ir.assetaudit.XlsxReader;
import java.io.File;
import java.util.List;

/** Proves that the sample workbook is accepted by the Android import parser. */
public final class TemplateCompatibilityTest {
    public static void main(String[] args) throws Exception {
        File file = new File(args[0]);
        List<XlsxReader.SheetInfo> sheets = XlsxReader.inspect(file);
        List<List<String>> rows = XlsxReader.readRows(file, sheets.get(0));
        if (rows.size() != 4 || !"000123".equals(rows.get(1).get(0))
                || !"میز اداری".equals(rows.get(1).get(1))) {
            throw new AssertionError("Template import or formatted plate parsing failed: " + rows);
        }
        System.out.println("PASS: template imported; formatted plate retains 000123");
    }
}
