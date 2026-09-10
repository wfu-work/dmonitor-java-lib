"""Deterministic calculations for the report; reads saved measurements only."""
import collections
import hashlib
import json
import math
from pathlib import Path
import statistics
import subprocess
import numpy as np

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT/'docs/test-evidence/2026-09-10'


def quantile(values, q):
    """Nearest rank, including for small samples (P95 can equal the maximum)."""
    values = sorted(values)
    return values[max(0, math.ceil(len(values)*q)-1)]


def stats(values):
    return dict(n=len(values), min=min(values), median=statistics.median(values),
                p95=quantile(values,.95), max=max(values), mean=statistics.mean(values))


def read(name): return json.loads((OUT/(name+'.json')).read_text())


def quality(sample):
    r = sample['result']
    return bool(not sample['exception'] and sample['callbackCount']==1 and r
                and not r['errMsg'] and r['fileStatus']==0 and r['navStatus']==0
                and r['fixedRate']>=.75 and r['solStatus']=='Fixed'
                and all(math.isfinite(r[k]) for k in ('E','N','U')))


def main():
    summary={}; repeat=read('repeatability')
    summary['repeatability']=dict(call_ms=stats([s['callMs'] for s in repeat['samples'][1:]]),
        first_call_ms=repeat['samples'][0]['callMs'],
        coordinate_range_m={k:max(s['result'][k] for s in repeat['samples'])-
                            min(s['result'][k] for s in repeat['samples']) for k in ('E','N','U')})
    base=read('concurrent-1'); ref={s['id']:s['result'] for s in base['samples'] if s['round']==0}
    summary['hourly_baseline']=[s for s in base['samples'] if s['round']==0]
    summary['hourly_quality_pass']=sum(quality(s) for s in summary['hourly_baseline'])
    summary['concurrency']=[]; manifests={r['name']:r for r in read('run-manifest')}
    compare_fields=('E','N','U','fixedRate','solStatus','fileStatus','navStatus','errMsg',
                    'gpsTime','roverObsNum','baseObsNum','roverEpochRate','baseEpochRate')
    for name in ['concurrent-1','concurrent-2','concurrent-4','concurrent-8','concurrent-8-serialized']:
        run=read(name); measured=[s for s in run['samples'] if s['round']>0]
        mismatches=[dict(id=s['id'],round=s['round']) for s in run['samples']
                    if not s['result'] or any(s['result'][k]!=ref[s['id']][k] for k in compare_fields)]
        walls=run['roundWallMs'][1:]
        summary['concurrency'].append(dict(name=name,n=run['concurrency'],measured_tasks=len(measured),
            total_tasks=len(run['samples']),batch_ms=stats(walls),
            throughput_tasks_s=len(measured)/(sum(walls)/1000),
            call_ms=stats([s['callMs'] for s in measured]),
            response_ms=stats([s['callMs']+s['queueMs'] for s in measured]),
            callbacks=sum(s['callbackCount'] for s in run['samples']),
            exceptions=sum(s['exception'] is not None for s in run['samples']),
            quality_pass=sum(quality(s) for s in run['samples']),mismatches=mismatches,
            peak_rss_mib=manifests[name]['peak_rss_bytes']/1024**2))
    summary['windows']=read('window-response')['samples']
    rows=[line.split() for line in (OUT/'epoch-series.pos').read_text().splitlines() if line.strip()]
    times=[r[0]+' '+r[1] for r in rows]
    all_values=np.array([[float(v) for v in r[2:5]] for r in rows]); codes=[r[5] for r in rows]
    fixed=all_values[np.array(codes)=='1'];center=fixed.mean(axis=0)
    def scatter(x):
        residual=x-center
        return dict(n=len(x),sample_sd_mm=(x.std(axis=0,ddof=1)*1000).tolist(),
            range_mm=(np.ptp(x,axis=0)*1000).tolist(),
            horizontal_p95_mm=quantile(np.linalg.norm(residual[:,:2],axis=1),.95)*1000,
            vertical_p95_mm=quantile(abs(residual[:,2]),.95)*1000,
            horizontal_max_mm=float(np.linalg.norm(residual[:,:2],axis=1).max()*1000),
            vertical_max_mm=float(abs(residual[:,2]).max()*1000))
    summary['epoch_series']=dict(total=len(rows),codes=dict(collections.Counter(codes)),
        first_epoch=times[0],first_fixed=times[codes.index('1')],last_epoch=times[-1],
        fixed_mean_m=center.tolist(),fixed=scatter(fixed),all=scatter(all_values),
        note='Scatter about the fixed-solution sample mean; not absolute accuracy.')
    summary['payload']=[]
    for size in (2500000,5000000):
        name=f'payload-{size}'
        if not (OUT/(name+'.json')).exists(): continue
        run=read(name); measured=run['samples'][1:];cfg=run['configs'][0]
        total=sum(Path(cfg[k]).stat().st_size for k in ('rover','base'))
        nav_bytes=Path(cfg['nav']).stat().st_size
        lat=stats([s['callMs'] for s in measured])
        summary['payload'].append(dict(name=name,rtcm_bytes=total,nav_bytes=nav_bytes,
            call_ms=lat,first_call_ms=run['samples'][0]['callMs'],read_ms=run['readMs'],
            throughput_mb_s=total/1e6/(lat['median']/1000),
            peak_rss_mib=manifests[name]['peak_rss_bytes']/1024**2,
            quality_pass=sum(quality(s) for s in run['samples']),samples=run['samples']))
    summary['environment']=dict(git=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),
        native_sha256=hashlib.sha256((ROOT/'src/main/resources/darwin-aarch64/libDMonitor.dylib').read_bytes()).hexdigest(),
        os=subprocess.check_output(['sw_vers','-productVersion'],text=True).strip(),
        java='Oracle JDK 21.0.9',cpu='Apple M2',logical_cpus=8,memory_gib=24)
    (OUT/'summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2))
    print(json.dumps({k:v for k,v in summary.items() if k not in ('hourly_baseline','windows','payload')},indent=2))


if __name__=='__main__': main()
