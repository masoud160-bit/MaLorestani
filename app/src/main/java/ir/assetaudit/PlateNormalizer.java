package ir.assetaudit;

import java.text.Normalizer;

/** Stable inventory keys; intentionally keeps leading zeroes and punctuation. */
public final class PlateNormalizer {
    private PlateNormalizer() { }

    public static String normalize(String input) {
        if (input == null) return "";
        String value = Normalizer.normalize(input, Normalizer.Form.NFKC).trim();
        StringBuilder out = new StringBuilder(value.length());
        boolean pendingSpace = false;
        for (int i = 0; i < value.length(); ) {
            int cp = value.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)
                    || cp == 0x200c || cp == 0x200d || cp == 0xfeff) {
                if (out.length() > 0) pendingSpace = true;
                continue;
            }
            if (pendingSpace) {
                out.append(' ');
                pendingSpace = false;
            }
            int digit = Character.digit(cp, 10);
            if (digit >= 0 && digit <= 9) out.append((char) ('0' + digit));
            else out.appendCodePoint(cp);
        }
        return out.toString();
    }
}
