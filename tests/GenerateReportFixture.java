import ir.assetaudit.XlsxWriter;
import java.io.FileOutputStream;
import java.util.Arrays;

/** Temporary fixture for testing report interoperability with other XLSX readers. */
public final class GenerateReportFixture {
    public static void main(String[] args) throws Exception {
        XlsxWriter.Sheet a = new XlsxWriter.Sheet("مغایرت نام", "پلاک", "نام ثبت‌شده", "نام مشاهده‌شده", "زمان ثبت");
        a.add("000123", "میز", "صندلی", "2026/11/08 12:00");
        XlsxWriter.Sheet b = new XlsxWriter.Sheet("مشاهده‌نشده", "پلاک", "نام ثبت‌شده");
        b.add("000124", "کمد");
        XlsxWriter.Sheet c = new XlsxWriter.Sheet("خارج از فهرست", "پلاک", "نام مشاهده‌شده", "زمان ثبت");
        c.add("000999", "تابلو", "2026/11/08 12:10");
        XlsxWriter.Sheet d = new XlsxWriter.Sheet("مطابق", "پلاک", "نام ثبت‌شده", "زمان تأیید");
        d.add("000125", "صندلی", "2026/11/08 12:20");
        try (FileOutputStream file = new FileOutputStream(args[0])) {
            XlsxWriter.write(file, Arrays.asList(a, b, c, d));
        }
    }
}
