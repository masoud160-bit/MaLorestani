"""Static UI and fast-entry regressions for the approved v2.0 portrait design."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
MAIN = (ROOT / 'app/src/main/java/ir/assetaudit/MainActivity.java').read_text(encoding='utf-8')
GRADLE = (ROOT / 'app/build.gradle').read_text(encoding='utf-8')


def between(start, end):
    return MAIN[MAIN.index(start):MAIN.index(end)]


def test_portrait_positions_and_style():
    screen = between('private void buildScreen()', 'private void buildMenuScreen()')
    assert 'Color.rgb(25, 30, 36)' in MAIN  # #191E24 provided background
    assert 'Color.rgb(80, 155, 213)' in MAIN  # #509BD5 provided blue
    assert 'header.addView(menu' in screen
    assert 'root.addView(plateCard' in screen
    assert 'root.addView(resultPanel' in screen
    assert 'root.addView(actionRow' in screen
    assert 'root.addView(flagArea' in screen
    assert 'root.addView(status' in screen
    assert screen.index('root.addView(plateCard') < screen.index('root.addView(resultPanel') < screen.index('root.addView(actionRow')
    assert screen.index('actionRow.addView(matchedButton') < screen.index('actionRow.addView(mismatchChooseButton') < screen.index('actionRow.addView(flagButton')
    assert 'weightedAction()' in screen
    assert 'outlinedButton("!\\nثبت مغایرت", BLUE)' in screen
    assert 'outlinedButton("⚑\\nعلامت‌گذاری", ORANGE)' in screen
    assert 'matchedButton = button("✓\\nتأیید مشاهده", GREEN)' in screen
    assert 'setVisibility(View.GONE)' in screen
    assert 'importButton' not in screen and 'exportButton' not in screen


def test_menu_contains_import_export_and_navigation():
    menu = between('private void buildMenuScreen()', 'private void addMenuItem(')
    assert '"ورود فایل اکسل"' in menu and 'openExcelPicker();' in menu
    assert '"دریافت گزارش"' in menu and 'confirmExport();' in menu
    for title in ['"صفحه اصلی"', '"کنترل و تطبیق اثاثیه"', '"وضعیت بررسی"', '"راهنما"']:
        assert title in menu
    assert 'showHomeScreen();' in menu
    assert 'if (isMenuVisible) showHomeScreen();' in MAIN


def test_fast_keyboard_and_validated_record_flows():
    finish = between('private void finishCurrentPlate()', 'private void submitObservation(')
    assert 'focusNextPlate();' in finish
    assert 'resultPanel.setVisibility(View.GONE)' in finish
    assert 'actionButtons.setVisibility(View.GONE)' in finish
    assert 'surveyInput.setText("")' in finish
    record = between('private void submitObservation(', 'private void confirmRemove()')
    assert record.index('database.record(') < record.index('finishCurrentPlate()')
    assert record.index('actualInput.requestFocus()') < record.index('database.record(')
    focus = between('private void focusNextPlate()', 'private void hideKeyboard')
    assert 'plateInput.setText("")' in focus
    assert 'plateInput.requestFocus()' in focus
    assert 'keyboard.showSoftInput(plateInput, InputMethodManager.SHOW_IMPLICIT)' in focus
    assert 'plateInput.setInputType(InputType.TYPE_CLASS_NUMBER)' in focus
    assert 'surveyInput.setInputType(InputType.TYPE_CLASS_NUMBER)' in MAIN
    assert 'setMaxLength' not in MAIN and 'LengthFilter' not in MAIN
    assert MAIN.count('private void focusNextPlate()') == 1


def test_flagging_independent_and_report_retained():
    assert 'database.markForReview(plate, note, surveyInput.getText().toString())' in MAIN
    assert 'database.unmarkForReview(plate)' in MAIN
    mark = between('private void saveFlagFromInput()', 'private void removeFlagFromInput()')
    assert 'database.record(' not in mark
    assert 'finishCurrentPlate();' in mark
    db = (ROOT / 'app/src/main/java/ir/assetaudit/AuditDb.java').read_text(encoding='utf-8')
    assert 'super(context, "asset_audit.db", null, 3)' in db
    assert 'return Arrays.asList(mismatch, missing, extra, matched, flagged)' in db
    assert "versionName '2.0.0'" in GRADLE
    assert 'versionCode 11' in GRADLE


def test_accessible_vector_resources():
    for name in ('ic_asset_desk.xml', 'ic_search_plate.xml', 'ic_status_bell.xml'):
        path = ROOT / 'app/src/main/res/drawable' / name
        root = ET.parse(path).getroot()
        assert root.tag.endswith('vector')
        assert len(root) >= 1
    assert 'setContentDescription("جست‌وجوی پلاک")' in MAIN
    assert 'setContentDescription("باز کردن منو")' in MAIN
