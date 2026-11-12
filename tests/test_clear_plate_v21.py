"""Regression checks for working clear-plate action and restrained header uplift."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
MAIN = (ROOT/'app/src/main/java/ir/assetaudit/MainActivity.java').read_text(encoding='utf-8')
GRADLE = (ROOT/'app/build.gradle').read_text(encoding='utf-8')


def block(start, end):
    return MAIN[MAIN.index(start): MAIN.index(end)]


def test_clear_is_real_operation_with_safe_reset():
    screen = block('private void buildScreen()', 'private void buildMenuScreen()')
    assert 'clearPlateButton.setImageResource(R.drawable.ic_clear_plate)' in screen
    assert 'clearPlateButton.setOnClickListener(v -> clearPlateEntry())' in screen
    assert 'clearPlateButton.setContentDescription("پاک کردن شماره پلاک و ورود شماره جدید")' in screen
    assert 'lookupButton' not in MAIN
    assert 'R.drawable.ic_search_plate' not in MAIN
    clear = block('private void clearPlateEntry()', 'private void finishCurrentPlate()')
    assert 'if (busy) return;' in clear
    assert 'finishCurrentPlate();' in clear
    assert 'database.record(' not in clear
    finish = block('private void finishCurrentPlate()', 'private void submitObservation(')
    for needed in ['selectedPlate = null', 'selectedItem = null', 'resultPanel.setVisibility(View.GONE)',
                   'actionButtons.setVisibility(View.GONE)', 'mismatchArea.setVisibility(View.GONE)',
                   'flagArea.setVisibility(View.GONE)', 'focusNextPlate();']:
        assert needed in finish
    focus = block('private void focusNextPlate()', 'private void hideKeyboard')
    for needed in ['plateInput.setText("")', 'plateInput.requestFocus()', 'showSoftInput(plateInput']:
        assert needed in focus


def test_search_remains_available_from_numeric_keyboard():
    assert 'plateInput.setInputType(InputType.TYPE_CLASS_NUMBER)' in MAIN
    assert 'plateInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH)' in MAIN
    on_action = block('plateInput.setOnEditorActionListener(', 'resultPanel = vertical()')
    for v in ['EditorInfo.IME_ACTION_SEARCH', 'EditorInfo.IME_ACTION_DONE',
              'EditorInfo.IME_ACTION_GO', 'KeyEvent.KEYCODE_ENTER', 'searchPlate();']:
        assert v in on_action


def test_header_visibly_distinguished_but_keeps_original_palette():
    assert 'Color.rgb(25, 30, 36)' in MAIN
    assert 'Color.rgb(80, 155, 213)' in MAIN
    assert 'Color.rgb(38, 56, 72)' in MAIN
    assert 'header.setBackground(rounded(NAVY, dp(7), HEADER_BORDER))' in MAIN
    assert 'Color.rgb(67, 94, 114)' in MAIN
    assert 'header.addView(menu' in MAIN
    assert 'header.addView(heading' in MAIN
    assert 'header.addView(info' in MAIN


def test_clear_vector_android_resource_and_version():
    icon = ROOT/'app/src/main/res/drawable/ic_clear_plate.xml'
    root = ET.parse(icon).getroot()
    assert root.tag.endswith('vector')
    assert len(root) >= 2
    assert "versionName '2.2.0'" in GRADLE
    assert 'versionCode 13' in GRADLE
    assert 'setContentDescription("باز کردن منو")' in MAIN
