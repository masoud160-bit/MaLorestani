import ir.assetaudit.XlsxWriter;
import java.io.FileOutputStream;
import java.util.Arrays;

/** Temporary fixture for testing report interoperability with other XLSX readers. */
public final class GenerateReportFixture {
    public static void main(String[] args) throws Exception {
        XlsxWriter.Sheet a = new XlsxWriter.Sheet("مغایرت نام", "پلاک", "نام ثبت‌شده", "نام مشاهده‌شده", "زمان ثبت");
        a.add("000123", "میز اداری", "صندلی اداری", "2026/11/08 12:00");
        a.add("000126", "ماشین حساب", "ماشین حساب رومیزی", "2026/11/08 12:35");
        a.add("000129", "صندلی انتظار", "میز کوچک", "2026/11/08 14:00");
        XlsxWriter.Sheet b = new XlsxWriter.Sheet("مشاهده‌نشده", "پلاک", "نام ثبت‌شده");
        b.add("000124", "کمد بایگانی");
        b.add("000130", "مانیتور");
        b.add("000132", "میز کارشناس");
        XlsxWriter.Sheet c = new XlsxWriter.Sheet("خارج از فهرست", "پلاک", "نام مشاهده‌شده", "زمان ثبت");
        c.add("000999", "تابلو اعلانات", "2026/11/08 12:10");
        c.add("001999", "صندلی بازدیدکننده", "2026/11/08 13:10");
        c.add("002999", "پرینتر قدیمی", "2026/11/08 14:10");
        XlsxWriter.Sheet d = new XlsxWriter.Sheet("مطابق", "پلاک", "نام ثبت‌شده", "زمان تأیید");
        d.add("000125", "صندلی اداری", "2026/11/08 12:20");
        d.add("000127", "ماشین اسکناس شمار", "2026/11/08 12:30");
        d.add("000128", "میز اداری", "2026/11/08 12:40");
        d.add("000133", "کشو فلزی", "2026/11/08 12:50");
        XlsxWriter.Sheet e = new XlsxWriter.Sheet("بررسی فرسودگی",
                "پلاک", "شماره آمایش", "نام ثبت‌شده", "وضعیت بررسی",
                "نام مشاهده‌شده", "تاریخ علامت‌گذاری");
        e.add("000125", "00001234", "صندلی اداری", "مطابق", "صندلی اداری", "2026/11/12 09:15");
        e.add("000127", "123456789012345678", "ماشین اسکناس شمار", "مطابق", "ماشین اسکناس شمار", "2026/11/12 09:20");
        e.add("000124", "", "کمد بایگانی", "مشاهده‌نشده", "", "2026/11/12 10:00");
        e.add("000123", "547", "میز اداری", "مغایرت نام", "صندلی اداری", "2026/11/12 11:10");
        try (FileOutputStream file = new FileOutputStream(args[0])) {
            XlsxWriter.write(file, Arrays.asList(a, b, c, d, e));
        }
    }
}
