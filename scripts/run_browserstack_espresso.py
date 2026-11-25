#!/usr/bin/env python3
"""Submit a real Espresso build; submission never marks B cases as PASS.

Credentials come only from BROWSERSTACK_USERNAME/BROWSERSTACK_ACCESS_KEY.
Device identifiers must be selected from the account's actual device catalog.
"""
import argparse
import base64
import hashlib
import json
import os
import urllib.error
import urllib.request
import uuid
from pathlib import Path

# The release-only tests and the ADB-preconditioned permission checks run in
# run_release_runtime_audit.sh. The device suite exercises the actual prompt.
DEVICE_TEST_CLASSES = [
    'com.example.NativeApiFixtureTest',
    'com.example.NativeLegalViewportTest',
    'com.example.NativeLoginNavigationTest',
    'com.example.NativeNotificationPermissionTest',
    'com.example.NativeScreenJourneyTest',
    'com.example.WorldMapStrictModeTest',
]

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl): return None

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--app',type=Path)
    parser.add_argument('--test-suite',type=Path)
    parser.add_argument('--devices-file',type=Path)
    parser.add_argument('--package-evidence',type=Path,help='CI package-evidence.json for exactly these APKs')
    parser.add_argument('--preflight',action='store_true')
    args=parser.parse_args()
    username,key=os.environ.get('BROWSERSTACK_USERNAME'),os.environ.get('BROWSERSTACK_ACCESS_KEY')
    if not username or not key:
        parser.exit(2,'NOT RUN: BrowserStack account credentials are not configured in this environment.\n')
    if args.preflight:
        print('Credential variables are present; connectivity and execution have not been verified.')
        return
    for path in (args.app,args.test_suite,args.devices_file,args.package_evidence):
        if not path or not path.is_file(): parser.error('Provide existing app, test-suite, selected devices and CI package evidence files')
    package_evidence=json.loads(args.package_evidence.read_text(encoding='utf8'))
    commit=package_evidence.get('commit','')
    if len(commit)!=40 or any(c not in '0123456789abcdef' for c in commit):
        parser.error('Package evidence must identify its actual 40-character CI commit')
    app_hash=hashlib.sha256(args.app.read_bytes()).hexdigest()
    suite_hash=hashlib.sha256(args.test_suite.read_bytes()).hexdigest()
    for kind,digest in (('debug',app_hash),('androidTest',suite_hash)):
        matches=[p for p in package_evidence.get('packages',[]) if p.get('kind')==kind]
        if len(matches)!=1 or matches[0].get('sha256')!=digest or matches[0].get('signatureVerified') is not True:
            parser.error('APK hashes and verified signatures must match the supplied CI package evidence')
    devices=json.loads(args.devices_file.read_text(encoding='utf8'))
    if not isinstance(devices,list) or not devices or not all(isinstance(d,str) and d for d in devices):
        parser.error('devices-file must contain a nonempty JSON list from the actual device catalog')
    opener=urllib.request.build_opener(NoRedirect())
    auth='Basic '+base64.b64encode((username+':'+key).encode()).decode()
    endpoint='https://api-cloud.browserstack.com/app-automate/espresso/v2/'
    def request(path,body,content_type):
        req=urllib.request.Request(endpoint+path,data=body,headers={'Authorization':auth,'Content-Type':content_type},method='POST')
        try:
            with opener.open(req,timeout=180) as response: return json.load(response)
        except urllib.error.HTTPError as error:
            raise SystemExit(f'BrowserStack request failed with HTTP {error.code}; no test success recorded.') from None
    def upload(path,api_path):
        boundary='nextdns-'+uuid.uuid4().hex
        body=(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{path.name}"\r\nContent-Type: application/octet-stream\r\n\r\n').encode()+path.read_bytes()+f'\r\n--{boundary}--\r\n'.encode()
        return request(api_path,body,'multipart/form-data; boundary='+boundary)
    app=upload(args.app,'app'); suite=upload(args.test_suite,'test-suite')
    # Native fixture tests bind a TLS MockWebServer on the device itself.
    # BrowserStack otherwise routes localhost through its proxy (CONNECT 503).
    # Permission changes can kill a running instrumented process. Reset the
    # disposable installation between methods so grant/denial tests are independent.
    payload={'app':app['app_url'],'testSuite':suite['test_suite_url'],'devices':devices,
             'class':DEVICE_TEST_CLASSES, 'project':'NextDNS Android',
             'allowDeviceMockServer':True,'clearPackageData':True}
    build=request('build',json.dumps(payload).encode(),'application/json')
    root=Path(__file__).resolve().parents[1]
    evidence={'status':'SUBMITTED','commit':commit,'appSha256':app_hash,
        'testSuiteSha256':suite_hash,'devices':devices,'testClasses':DEVICE_TEST_CLASSES,
        'appUrl':app['app_url'],'testSuiteUrl':suite['test_suite_url'],'buildId':build['build_id']}
    directory=root/'build/browserstack'; directory.mkdir(parents=True,exist_ok=True)
    (directory/'submission.json').write_text(json.dumps(evidence,indent=2)+'\n',encoding='utf8')
    print('BrowserStack build submitted:',build['build_id'],'; results must be retrieved and reviewed separately.')

if __name__=='__main__': main()
