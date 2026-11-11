"""Static regression checks for v1.9 fast keyboard workflow and palette."""
from pathlib import Path
p=Path('app/src/main/java/ir/assetaudit/MainActivity.java')
s=p.read_text(encoding='utf-8')
assert s.count('private void focusNextPlate()') == 1
focus=s[s.index('private void focusNextPlate()'):s.index('private void hideKeyboard')]
assert 'plateInput.setText("");' in focus
assert 'plateInput.requestFocus();' in focus
assert 'screenScroll.scrollTo(0, 0);' in focus
assert 'keyboard.showSoftInput(plateInput, InputMethodManager.SHOW_IMPLICIT);' in focus
assert 'plateInput.setInputType(InputType.TYPE_CLASS_NUMBER)' in focus
sub=s[s.index('private void submitObservation('):s.index('private void confirmRemove()')]
assert 'focusNextPlate();' in sub and sub.index('database.record(')<sub.index('focusNextPlate()')
assert 'actualInput.requestFocus();' in sub and sub.index('actualInput.requestFocus();')<sub.index('database.record(')
assert 'database.markForReview(plate, oldNote, survey.getText().toString())' in s
assert 'focusNextPlate();' in s[s.index('private void showFlagDialog()'):s.index('private void submitObservation(')]
assert 'Color.rgb(59, 105, 165)' in s  # muted cobalt
assert 'Color.rgb(214, 160, 76)' in s  # gold
assert 'Color.rgb(52, 134, 92)' in s   # muted green
assert "versionName '1.9.0'" in Path('app/build.gradle').read_text()
print('PASS: v1.9 fast next-plate keyboard, visual theme, preserved flagging')
