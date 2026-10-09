import runpy, json, sys, os, hashlib, subprocess
from pathlib import Path
root=Path(sys.argv[1]); os.chdir(root)
scripts=json.loads(sys.argv[2]); logdir=Path(sys.argv[3]); logdir.mkdir(exist_ok=True)
original=runpy.run_path; stack=[]; calls=[]
def traced(path,*args,**kwargs):
    p=Path(path).resolve(); rel=str(p.relative_to(root))
    entry={"path":rel,"parent":stack[-1] if stack else None,"depth":len(stack)}
    calls.append(entry); stack.append(rel)
    try:
        result=original(str(p),*args,**kwargs); entry["status"]="ok"; return result
    except BaseException as exc:
        entry["status"]="failed"; entry["error"]=str(exc); raise
    finally: stack.pop()
runpy.run_path=traced
for name in scripts:
    print("TOP",name,flush=True)
    try: traced(root/"android-apk"/name,run_name="__main__")
    except BaseException:
        (logdir/"invocations.json").write_text(json.dumps(calls,indent=2)); raise
(logdir/"invocations.json").write_text(json.dumps(calls,indent=2))
all_sources=(root/'android-apk').rglob('*')
hashes={str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in all_sources if p.is_file() and p.suffix in {'.java','.kt','.html','.py','.gradle'}}
(logdir/"effective-hashes.json").write_text(json.dumps(hashes,sort_keys=True,indent=2))
print("PATCH_CHAIN_COMPLETE",len(scripts),"top-level",len(calls),"runpy invocations",flush=True)
