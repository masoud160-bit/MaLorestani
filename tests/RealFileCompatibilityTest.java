import ir.assetaudit.AssetImportMapping;
import ir.assetaudit.PlateNormalizer;
import ir.assetaudit.XlsxReader;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Read-only integration check; the actual user's inventory file is NOT bundled. */
public final class RealFileCompatibilityTest {
    public static void main(String[] args) throws Exception {
        File file = new File(args[0]);
        List<XlsxReader.SheetInfo> sheets = XlsxReader.inspect(file);
        if (sheets.size() != 1) throw new AssertionError("Unexpected sheet count: " + sheets.size());
        List<List<String>> rows = XlsxReader.readRows(file, sheets.get(0));
        if (rows.size() != 409) throw new AssertionError("Unexpected rows: " + rows.size());
        if (!"پلاک اموال".equals(rows.get(0).get(11).trim())) throw new AssertionError("Plate column changed");
        if (!"عنوان کالا".equals(rows.get(0).get(14).trim())) throw new AssertionError("Name column changed");
        AssetImportMapping.Mapping mapping = AssetImportMapping.detect(rows);
        if (mapping == null || mapping.headerRow != 0 || mapping.plateColumn != 11 || mapping.nameColumn != 14) {
            throw new AssertionError("Real Excel auto-mapping failed");
        }
        Set<String> keys = new HashSet<>();
        for (int i = 1; i < rows.size(); i++) {
            String plate = PlateNormalizer.normalize(rows.get(i).get(11));
            String name = rows.get(i).get(14).trim();
            if (plate.isEmpty() || name.isEmpty()) throw new AssertionError("Missing value in row " + (i + 1));
            if (!keys.add(plate)) throw new AssertionError("Duplicate plate in row " + (i + 1));
        }
        System.out.println("PASS: real Excel report; 408 unique, nonempty plate/name pairs");
    }
}
