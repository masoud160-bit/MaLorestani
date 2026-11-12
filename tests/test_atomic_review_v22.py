"""Regression checks for single-action matching/mismatch and review-flag storage."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent.parent
UI = (ROOT/'app/src/main/java/ir/assetaudit/MainActivity.java').read_text(encoding='utf-8')
DB = (ROOT/'app/src/main/java/ir/assetaudit/AuditDb.java').read_text(encoding='utf-8')
GRADLE = (ROOT/'app/build.gradle').read_text(encoding='utf-8')


def section(text, begin, end):
    return text[text.index(begin):text.index(end)]


def test_no_separate_flag_save_control():
    screen = section(UI, 'private void buildScreen()', 'private void buildMenuScreen()')
    assert 'saveFlagButton' not in UI
    assert 'saveFlagFromInput' not in UI
    assert 'ثبت / ذخیره علامت' not in screen
    assert 'clearFlagButton = outlinedButton(' in screen
    assert 'flagButton.setOnClickListener(v -> showFlagDialog())' in screen


def test_selecting_review_is_pending_until_an_observation_is_submitted():
    flag = section(UI, 'private void showFlagDialog()', 'private void removeFlagFromInput()')
    assert 'reviewSelected = true;' in flag
    assert 'database.markForReview(' not in flag
    assert 'database.record(' not in flag
    submit = section(UI, 'private void submitObservation(', 'private void confirmRemove()')
    assert 'status != AuditDb.MATCH && observed.isEmpty()' in submit
    assert 'actualInput.requestFocus()' in submit
    assert submit.index('actualInput.requestFocus()') < submit.index('database.recordWithReview(')
    assert 'reviewSelected && selectedItem != null' in submit
    assert 'surveyInput.getText().toString()' in submit
    assert 'database.recordWithReview(plate, value, status, flagged, survey);' in submit
    assert submit.index('database.recordWithReview(') < submit.index('finishCurrentPlate();')
    assert 'matchedButton.setOnClickListener(v -> submitObservation(AuditDb.MATCH));' in UI
    assert 'mismatchButton.setOnClickListener(v -> submitObservation(AuditDb.MISMATCH));' in UI


def test_atomic_transaction_keeps_state_and_review_in_sync():
    body = section(DB, 'public void recordWithReview(', 'public void removeObservation(')
    for part in ['db.beginTransaction();', '"observations"', '"asset_flags"',
                 'flag.put("survey_number", surveyNumber == null ? "" : surveyNumber.trim());',
                 'db.setTransactionSuccessful();', 'db.endTransaction();']:
        assert part in body
    assert body.index('"observations"') < body.index('"asset_flags"')
    assert 'if (markForReview)' in body
    assert 'db.delete("asset_flags"' not in body
    assert 'super(context, "asset_audit.db", null, 3)' in DB  # no destructive migration


def test_two_report_sheets_reflect_same_flagged_record():
    reports = section(DB, 'public List<XlsxWriter.Sheet> reports()', 'private static String time(')
    assert '"مغایرت نام"' in reports and 'WHERE o.status=2' in reports
    assert '"مطابق"' in reports and 'WHERE o.status=1' in reports
    assert '"بررسی فرسودگی"' in reports and '"شماره آمایش"' in reports
    assert 'FROM asset_flags f JOIN inventory i' in reports
    assert 'LEFT JOIN observations o ON o.plate=i.plate' in reports
    assert 'state == MATCH ? "مطابق"' in reports
    assert 'state == MISMATCH ? "مغایرت نام"' in reports
    assert 'getString(4), c.getString(1), stateLabel' in reports


def test_pending_flag_cancel_and_durable_flag_removal():
    removal = section(UI, 'private void removeFlagFromInput()', '/** Clear is a real action:')
    assert 'if (old == null)' in removal
    assert 'reviewSelected = false;' in removal
    assert 'database.unmarkForReview(plate)' in removal
    assert 'finishCurrentPlate();' not in removal
    assert "versionName '2.2.0'" in GRADLE
    assert 'versionCode 13' in GRADLE
