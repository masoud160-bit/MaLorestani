package ir.assetaudit;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/** Deterministic detection of the actual banking asset-report column headings. */
public final class AssetImportMapping {
    private AssetImportMapping() { }

    public static final class Mapping {
        public final int headerRow;
        public final int plateColumn;
        public final int nameColumn;

        Mapping(int headerRow, int plateColumn, int nameColumn) {
            this.headerRow = headerRow;
            this.plateColumn = plateColumn;
            this.nameColumn = nameColumn;
        }
    }

    public static Mapping detect(List<List<String>> rows) {
        for (int r = 0; r < Math.min(6, rows.size()); r++) {
            List<String> header = rows.get(r);
            int plate = find(header, "پلاک اموال");
            int name = find(header, "عنوان کالا");
            if (plate >= 0 && name >= 0 && plate != name) return new Mapping(r, plate, name);
        }
        return null;
    }

    private static int find(List<String> values, String expected) {
        String target = normalized(expected);
        for (int i = 0; i < values.size(); i++) {
            if (target.equals(normalized(values.get(i)))) return i;
        }
        return -1;
    }

    private static String normalized(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replace('ي', 'ی').replace('ك', 'ک')
                .replace('\u200c', ' ').replace('\u00a0', ' ')
                .trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
