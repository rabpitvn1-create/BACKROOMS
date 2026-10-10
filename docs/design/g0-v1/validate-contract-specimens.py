"""Host-only SQL design specimen checks. Not an Android repository/reducer test."""
from pathlib import Path
import re
import sqlite3

DESIGN = Path(__file__).resolve().parents[1] / 'SAVESTATS_CHARACTER_BRAIN_TECHNICAL_DESIGN_V1.md'
DDL = re.search(r'~~~sql\n(.*?)\n~~~', DESIGN.read_text(), re.S).group(1)

def fixture():
    db = sqlite3.connect(':memory:')
    db.executescript(DDL)
    db.execute("INSERT INTO save_slot VALUES ('s',1,'COMPANION_V1',1,2,'{}','hash',0,0)")
    db.execute("INSERT INTO turn_receipt(slot_id,request_id,turn_id,input_hash,expected_revision,committed_revision,status,batch_manifest_json,final_result_json) VALUES ('s','r','t','hash',0,1,'COMMITTED','{}','{}')")
    db.execute("INSERT INTO world_event(slot_id,event_id,turn_id,committed_revision,ordinal,event_type,verified_payload_json,evidence_refs_json) VALUES ('s','e','t',1,0,'TEST','{}','[]')")
    db.execute("INSERT INTO actor_observation VALUES ('s','o','A','e','t',1,'TOLD','X','UNCERTAIN')")
    db.commit()
    return db

CASES = {
    'memory actor FK': "INSERT INTO actor_memory VALUES ('s','m','B','o','t',1,'summary','NATIVE','ORDINARY','ACTIVE',NULL)",
    'memory slot FK': "INSERT INTO actor_memory VALUES ('other','m','A','o','t',1,'summary','NATIVE','ORDINARY','ACTIVE',NULL)",
    'CERTAIN inference': "INSERT INTO actor_observation VALUES ('s','o2','A','e','t',1,'INFERRED',NULL,'CERTAIN')",
    'event update trigger': "UPDATE world_event SET event_type='FORGED'",
    'event delete trigger': "DELETE FROM world_event",
    'future receipt provenance': "INSERT INTO world_event(slot_id,event_id,turn_id,committed_revision,ordinal,event_type,verified_payload_json,evidence_refs_json) VALUES ('s','e2','t',2,1,'TEST','{}','[]')",
    'COMMITTED without revision': "INSERT INTO turn_receipt(slot_id,request_id,turn_id,input_hash,expected_revision,status,batch_manifest_json,final_result_json) VALUES ('s','r2','t2','hash',1,'COMMITTED','{}','{}')",
}
for name, sql in CASES.items():
    db = fixture()
    try:
        db.execute(sql)
        db.commit()
    except sqlite3.IntegrityError:
        print(name + ': PASS')
    else:
        raise AssertionError('Expected SQL rejection: ' + name)
    finally:
        db.close()
db = fixture()
db.execute("INSERT INTO actor_memory VALUES ('s','m','A','o','t',1,'summary','NATIVE','ORDINARY','ACTIVE',NULL)")
db.commit()
db.close()
print('valid same-actor memory: PASS')
