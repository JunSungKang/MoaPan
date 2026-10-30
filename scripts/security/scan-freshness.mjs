import assert from 'node:assert/strict';
export function verifyScanFreshness(report,db,now=Date.now()){
  const recent=(value,label)=>{const t=Date.parse(value);assert(Number.isFinite(t)&&now-t>=-300000&&now-t<86400000,`${label} must be a valid timestamp within 24 hours`);return t;};
  assert.equal(db?.Version,2,'Unsupported vulnerability database schema');
  const updated=recent(db.UpdatedAt,'DB publication');
  recent(db.DownloadedAt,'DB synchronization');
  const next=Date.parse(db.NextUpdate);assert(Number.isFinite(next)&&next>updated&&next>now,'Vulnerability DB update deadline passed');
  const scanned=recent(report.CreatedAt,'Scan creation');
  assert(scanned>=updated-300000,'Report predates current vulnerability database');
}
