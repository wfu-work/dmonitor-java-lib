"""Collect report evidence with real SDK calls in isolated child JVMs.

Usage: python run_benchmarks.py --data /path/to/gnss --license /path/to/license.lic
Run Maven test-compile and dependency:build-classpath first (see README.md).
"""
import argparse
import hashlib
import json
import os
import re
from pathlib import Path
import subprocess
import time
from rtcm_audit import audit, dechunk

ROOT = Path(__file__).resolve().parents[2]
WORK = ROOT/'target/report-work'
OUT = ROOT/'docs/test-evidence/2026-09-10'


def main():
    p = argparse.ArgumentParser(); p.add_argument('--data', type=Path, required=True)
    p.add_argument('--license', type=Path, required=True)
    p.add_argument('--only', nargs='*', help='Optional run names to repeat without rerunning other cases')
    a = p.parse_args()
    WORK.mkdir(parents=True, exist_ok=True); OUT.mkdir(parents=True, exist_ok=True)
    cp = str(ROOT/'target/classes') + ':' + (WORK/'classpath.txt').read_text().strip()
    subprocess.run(['javac', '-proc:none', '-cp', cp, '-d', str(WORK),
                    str(ROOT/'scripts/report-tests/GnssReportProbe.java')], check=True)
    cp = str(WORK)+':'+cp
    runs = json.loads((OUT/'run-manifest.json').read_text()) if a.only and (OUT/'run-manifest.json').exists() else []

    def run(name, configs, concurrency=1, rounds=1, serialize=False, timeout=240):
        if a.only and name not in a.only: return
        config_path = WORK/(name+'-config.json'); config_path.write_text(json.dumps(configs))
        output = OUT/(name+'.json'); log = OUT/(name+'.log')
        if output.exists(): output.unlink()
        start = time.perf_counter(); peak_rss = 0
        with log.open('w') as stream:
            process = subprocess.Popen(['java', '-Xmx2g', '-XX:ErrorFile='+str(WORK/'hs_err_pid%p.log'),
                '-cp', cp, 'GnssReportProbe', str(config_path), str(a.license), str(output),
                str(concurrency), str(rounds), str(serialize).lower()], cwd=WORK,
                stdout=stream, stderr=stream)
            timed_out = False
            while process.poll() is None:
                try:
                    rss = subprocess.check_output(['ps','-o','rss=','-p',str(process.pid)], text=True)
                    peak_rss = max(peak_rss, int(rss.strip())*1024)
                except (subprocess.CalledProcessError,ValueError): pass
                if time.perf_counter()-start > timeout:
                    timed_out = True; process.kill(); process.wait(); break
                time.sleep(.2)
        record = dict(name=name, concurrency=concurrency, rounds=rounds, serialize=serialize,
            exit_code=process.returncode, timeout=timed_out, process_wall_s=time.perf_counter()-start,
            peak_rss_bytes=peak_rss, rss_sample_interval_s=.2, result_file=output.name,
            log_file=log.name)
        log.write_text(re.sub(r'[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}',
                              '[UUID REDACTED]',log.read_text()))
        runs[:] = [r for r in runs if r['name'] != name]
        runs.append(record); (OUT/'run-manifest.json').write_text(json.dumps(runs,indent=2))
        print(json.dumps(record), flush=True)
        return record

    def config(id, hour=13, start=None, end=None, mode=3, out_mode=1):
        return dict(id=id, rover=str(a.data/f'raw/2026/249/{hour:02}/PSYCMM0010.2026249binRTCM3'),
            base=str(a.data/f'raw/2026/249/{hour:02}/PSYCMM0009.2026249binRTCM3'),
            nav=str(a.data/'nav/2026/BRDM2490.rnx'),
            start=start or f'2026/09/06 {hour:02}:00:00',
            end=end or f'2026/09/06 {hour+1:02}:00:00', mode=mode, outMode=out_mode)

    run('repeatability', [config('repeat-13')], rounds=6)
    tasks = [config(f'hour-{h:02}',h) for h in range(8,16)]
    for n in (1,2,4,8):
        run(f'concurrent-{n}', tasks, n, 3)
    run('concurrent-8-serialized',tasks,8,2,True)
    windows = [config(f'window-{m}min',end=f'2026/09/06 13:{m:02}:00') for m in (1,5,15,30)]
    windows.append(config('window-60min'))
    run('window-response', windows, rounds=1)
    epochs = config('epoch-series', mode=2, out_mode=0)
    epochs['outFile'] = str(OUT/'epoch-series.pos')
    run('epoch-series', [epochs])

    # Generate chronological, same-station pure RTCM streams; keep complete frames.
    fixtures = {}; fixture_meta = []
    nav = WORK/'BRDM249-250.rnx'
    nav_text = (a.data/'nav/2026/BRDM2490.rnx').read_text()
    extra = (a.data/'nav/2026/BRDM2500.rnx').read_text().splitlines(keepends=True)
    offset = next(i for i,line in enumerate(extra) if 'END OF HEADER' in line)+1
    nav.write_text(nav_text+''.join(extra[offset:]))
    for station in ('PSYCMM0010','PSYCMM0009'):
        sources = []; payload = bytearray()
        for day in (249,250):
            for f in sorted((a.data/f'raw/2026/{day}').glob(f'*/{station}.*')):
                raw = f.read_bytes(); data = dechunk(raw); sources.append(dict(
                    path=str(f.relative_to(a.data)), bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest()))
                payload.extend(data)
                if len(payload) >= 5_000_000: break
            if len(payload) >= 5_000_000: break
        _, ends = audit(payload)
        for target in (2_500_000,5_000_000):
            end = max(e for e in ends if e <= target)
            data = bytes(payload[:end]); path = WORK/f'{station}-{target}.rtcm3';path.write_bytes(data)
            stat, _ = audit(data);stat.update(station=station, target_bytes=target,
                path=str(path), sources=sources);fixture_meta.append(stat)
            fixtures[(station,target)] = str(path)
    (OUT/'fixture-manifest.json').write_text(json.dumps(fixture_meta,indent=2))
    for target in (2_500_000,5_000_000):
        c=config(f'payload-{target}')
        c.update(rover=fixtures[('PSYCMM0010',target)],base=fixtures[('PSYCMM0009',target)],nav=str(nav),
                 start='2026/09/06 00:00:00',
                 end='2026/09/06 12:00:00' if target==2_500_000 else '2026/09/07 01:00:00')
        run(f'payload-{target}',[c],rounds=4,timeout=420)


if __name__ == '__main__': main()
