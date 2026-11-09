import ir.assetaudit.AssetImportMapping;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class AutoMappingTest {
    public static void main(String[] args) {
        List<List<String>> rows = Collections.singletonList(Arrays.asList(
                "عنوان محل نگهداری", "عنوان کالا", "پلاك اموال", "سایر"));
        AssetImportMapping.Mapping m = AssetImportMapping.detect(rows);
        if (m == null || m.headerRow != 0 || m.plateColumn != 2 || m.nameColumn != 1) {
            throw new AssertionError("Failed to prefer exact heading over similarly named location column");
        }
        System.out.println("PASS: exact report column mapping");
    }
}
