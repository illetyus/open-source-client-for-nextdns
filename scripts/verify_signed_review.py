"""Bind locally signed review artifacts to successful main CI payload evidence."""
import argparse
import hashlib
import json
import re
import subprocess
import zipfile
from pathlib import Path
from package_payload import digest, entries
from release_dex_audit import audit_dex
from release_runtime_evidence import validate_runtime_summary

parser = argparse.ArgumentParser()
parser.add_argument('--evidence', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--tools', type=Path, required=True)
parser.add_argument('--run-id', required=True)
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
evidence = json.loads((args.evidence/'build/compliance/package-evidence.json').read_text(encoding='utf8'))
run = json.loads(subprocess.check_output(['gh','run','view',args.run_id,'--repo','illetyus/open-source-client-for-nextdns','--json','headSha,status,conclusion,event,headBranch'],text=True))
assert run['status']=='completed' and run['conclusion']=='success' and run['event']=='push' and run['headBranch']=='main', run
assert run['headSha']==evidence['commit'] and re.fullmatch('[a-f0-9]{40}', evidence['commit'])
for package in evidence['packages']:
    path = args.evidence/package['file']
    assert hashlib.sha256(path.read_bytes()).hexdigest()==package['sha256'], package['kind']
unsigned_apk = args.evidence/next(x['file'] for x in evidence['packages'] if x['kind']=='release')
unsigned_aab = args.evidence/next(x['file'] for x in evidence['packages'] if x['kind']=='releaseBundle')
apk = args.output/'OpenSourceClientForNextDNS-1.0-review.apk'
aab = args.output/'OpenSourceClientForNextDNS-1.0-review.aab'
assert entries(unsigned_apk)==entries(apk), 'APK payload modified during signing'
assert entries(unsigned_aab)==entries(aab), 'AAB payload modified during signing'
signature = subprocess.check_output([str(args.tools/'apksigner.bat'),'verify','--verbose','--print-certs',str(apk)],text=True)
assert 'Verified using v2 scheme (APK Signature Scheme v2): true' in signature
match = re.search(r'certificate SHA-256 digest: ([a-f0-9]{64})',signature)
assert match, 'Missing verified signing fingerprint'
fingerprint = match.group(1)
bundle_check = subprocess.check_output(['java',str(root/'scripts/VerifyBundleSigner.java'),str(aab),fingerprint],text=True)
subprocess.run([str(args.tools/'zipalign.exe'),'-c','-P','16','4',str(apk)],check=True,capture_output=True)
manifest = subprocess.check_output([str(args.tools/'aapt2.exe'),'dump','xmltree',str(apk),'--file','AndroidManifest.xml'],text=True)
assert 'com.aistudio.nextdns.mgrqvt' in manifest and ':usesCleartextTraffic(0x010104ec)=false' in manifest
assert not re.search(r':debuggable\([^)]*\)=(true|0xffffffff)',manifest,re.I)
with zipfile.ZipFile(apk) as z:
    assert z.read('assets/licenses/THIRD_PARTY_NOTICES.txt') == (root/'app/src/main/assets/licenses/THIRD_PARTY_NOTICES.txt').read_bytes()
    for name in z.namelist():
        if re.fullmatch(r'classes\d*\.dex',name):
            audit_dex(z.read(name))
runtime = json.loads((args.evidence/'build/compliance/runtime/release-runtime-evidence.json').read_text())
validate_runtime_summary(runtime)
assert runtime['commit']==evidence['commit']
assert runtime['payloadSha256']==digest(apk) and runtime['canaryAbsentFromLogcat'] and runtime['payloadIdenticalExceptSignature']
snyk = json.loads((args.evidence/'build/compliance/snyk-app-release.json').read_text())
assert snyk['ok'] is True and snyk['dependencyCount']>20 and not snyk['vulnerabilities']
report = {'status':'SIGNED_REVIEW_CANDIDATE','commit':evidence['commit'],
          'ciUrl':f'https://github.com/illetyus/open-source-client-for-nextdns/actions/runs/{args.run_id}',
          'applicationId':'com.aistudio.nextdns.mgrqvt','versionName':'1.0','versionCode':1,
          'certificateSha256':fingerprint,'payloadIdenticalToMainCi':True,
          'packages':[{'file':x.name,'sha256':hashlib.sha256(x.read_bytes()).hexdigest(),'bytes':x.stat().st_size,'payloadSha256':digest(x)} for x in (apk,aab)],
          'runtime':runtime,'licensesBundled':True,'releaseDebuggable':False,'cleartextTraffic':False,
          'snyk':{'severityThreshold':'high','dependencyCount':snyk['dependencyCount'],'reportedVulnerabilities':0},
          'legalStatus':'DRAFT','productionSigningApproved':False,'publicReleaseApproved':False,
          'browserstack':'NOT_RUN; separate required stage',
          'scope':'Signed candidate for user review. CI runtime uses a disposable signer; all non-signature ZIP entries match the locally signed APK. No assertion of Play app-signing enrollment or live-account end-to-end audit.'}
(args.output/'signed-review-evidence.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
(args.output/'apk-signature.txt').write_text(signature,encoding='utf8')
(args.output/'aab-signature.txt').write_text(bundle_check,encoding='utf8')
(args.output/'release-manifest.txt').write_text(manifest,encoding='utf8')
(args.output/'SHA256SUMS.txt').write_text(''.join(f"{item['sha256']}  {item['file']}\n" for item in report['packages']),encoding='utf8')
print('PASS: signed APK/AAB, certificate, 16 KB alignment, manifest, notices, test-code exclusion, main CI and release runtime payload equivalence')
