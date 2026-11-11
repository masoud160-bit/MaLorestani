"""Regression: v2 -> v3 preserves flags and adds unrestricted text survey number."""
from pathlib import Path
import sqlite3
source = Path('app/src/main/java/ir/assetaudit/AuditDb.java').read_text(encoding='utf-8')
activity = Path('app/src/main/java/ir/assetaudit/MainActivity.java').read_text(encoding='utf-8')
assert 'super(context, "asset_audit.db", null, 3)' in source
assert 'oldVersion >= 2 && oldVersion < 3' in source
assert 'ALTER TABLE asset_flags ADD COLUMN survey_number TEXT NOT NULL DEFAULT' in source
assert 'return Arrays.asList(mismatch, missing, extra, matched, flagged)' in source
assert 'values.put("survey_number", surveyNumber == null ? "" : surveyNumber.trim())' in source
assert 'survey.setInputType(InputType.TYPE_CLASS_NUMBER)' in activity
assert 'setMaxLength' not in activity and 'LengthFilter' not in activity
assert 'getString(4), c.getString(1), stateLabel' in source
con=sqlite3.connect(':memory:')
con.execute('CREATE TABLE inventory (plate TEXT PRIMARY KEY NOT NULL,name TEXT NOT NULL)')
con.execute('CREATE TABLE observations (plate TEXT PRIMARY KEY NOT NULL,observed_name TEXT NOT NULL,status INTEGER NOT NULL,updated_at INTEGER NOT NULL)')
con.execute("CREATE TABLE asset_flags (plate TEXT PRIMARY KEY NOT NULL,note TEXT NOT NULL DEFAULT '',updated_at INTEGER NOT NULL)")
con.execute('INSERT INTO inventory VALUES (?,?)', ('0007','میز مدیریت'))
con.execute('INSERT INTO observations VALUES (?,?,?,?)', ('0007','میز مدیریت',1,123))
con.execute('INSERT INTO asset_flags VALUES (?,?,?)', ('0007','پرونده قدیمی',456))
con.execute("ALTER TABLE asset_flags ADD COLUMN survey_number TEXT NOT NULL DEFAULT ''")
assert con.execute('SELECT survey_number FROM asset_flags').fetchone()[0] == ''
long_id='0000123456789012345678901234567'
con.execute('UPDATE asset_flags SET survey_number=? WHERE plate=?', (long_id,'0007'))
assert con.execute('SELECT survey_number FROM asset_flags').fetchone()[0] == long_id
assert con.execute('SELECT status FROM observations WHERE plate=?', ('0007',)).fetchone()[0] == 1
assert con.execute('SELECT note FROM asset_flags').fetchone()[0] == 'پرونده قدیمی'
print('PASS: v2->v3 preserves existing flags; survey number can exceed 8 digits and retain zeros')
