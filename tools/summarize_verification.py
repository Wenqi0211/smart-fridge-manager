#!/usr/bin/env python3
"""Summarize actual JUnit XML and JaCoCo XML; never insert target/estimated coverage."""
import argparse
import copy
import datetime
import hashlib
import json
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET


def counters(element):
    result = {}
    for counter in element.findall('counter'):
        missed, covered = int(counter.get('missed')), int(counter.get('covered'))
        result[counter.get('type').lower()] = {
            'covered': covered, 'missed': missed, 'total': missed + covered,
            'percent': round(100 * covered / (missed + covered), 2) if missed + covered else None,
        }
    return result


def summarize(root):
    report_path = root / 'build/reports/jacoco/test/jacocoTestReport.xml'
    suites = sorted((root / 'build/test-results/test').glob('TEST-*.xml'))
    if not suites or not report_path.is_file():
        raise SystemExit('Run ./gradlew clean test jacocoTestReport before summarizing.')
    production_inputs = [p for p in list((root / 'src/main').rglob('*'))
                         + list((root / 'src/test').rglob('*')) + [root / 'build.gradle'] if p.is_file()]
    if report_path.stat().st_mtime < max(p.stat().st_mtime for p in production_inputs):
        raise SystemExit('Coverage report predates current source/tests. Re-run clean test jacocoTestReport.')
    tests = {'tests': 0, 'failures': 0, 'errors': 0, 'skipped': 0, 'suites': []}
    for path in suites:
        suite = ET.parse(path).getroot()
        entry = {'name': suite.get('name'), **{k: int(suite.get(k, 0)) for k in ['tests', 'failures', 'errors', 'skipped']}}
        tests['suites'].append(entry)
        for key in ['tests', 'failures', 'errors', 'skipped']:
            tests[key] += entry[key]
    required = {'API.security.TestAuthenticatedWorkflow': 8, 'API.security.TestRequestBoundaries': 11,
                'service.TestRecommendationBoundaries': 16}
    by_name = {suite['name']: suite for suite in tests['suites']}
    for name, minimum_tests in required.items():
        suite = by_name.get(name)
        if suite is None or suite['tests'] < minimum_tests or suite['failures'] or suite['errors'] or suite['skipped']:
            raise SystemExit(f'Missing/pending verification suite: {name}. Run the full current test suite.')
    coverage_xml = ET.parse(report_path).getroot()
    classes = [c.get('name') for p in coverage_xml.findall('package') for c in p.findall('class')]
    if not {'api/SmartFridgeApiApplication', 'ui/Main'}.issubset(classes):
        raise SystemExit('Coverage report excludes main entrypoints; regenerate the all-production report.')
    # Include every main-source class, including legacy ui and entrypoints. No report exclusions.
    layers = {}
    for package in coverage_xml.findall('package'):
        name = package.get('name').split('/')[0]
        layers.setdefault(name, {'line': {'covered': 0, 'missed': 0}, 'branch': {'covered': 0, 'missed': 0}})
        for kind, metric in counters(package).items():
            if kind in ['line', 'branch']:
                for field in ['covered', 'missed']:
                    layers[name][kind][field] += metric[field]
    for layer in layers.values():
        for metric in layer.values():
            metric['total'] = metric['covered'] + metric['missed']
            metric['percent'] = round(metric['covered'] / metric['total'] * 100, 2) if metric['total'] else None
    digest = hashlib.sha256()
    for path in sorted(list((root / 'src/main').rglob('*')) + list((root / 'src/test').rglob('*')) + [root / 'build.gradle']):
        if path.is_file():
            digest.update(str(path.relative_to(root)).encode())
            digest.update(path.read_bytes())
    data = {
        'generatedAtUtc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
        'baseGitCommit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip(),
        'sourceAndTestSha256': digest.hexdigest(),
        'scope': 'All compiled Java main-source classes; no class exclusions; Flutter is separate.',
        'testResults': tests, 'coverage': counters(coverage_xml), 'layers': layers,
        'instrumentedClasses': len(classes), 'classes': sorted(classes),
        'artifacts': {'junit': 'build/test-results/test', 'jacocoXml': str(report_path.relative_to(root)),
                      'jacocoHtml': 'build/reports/jacoco/test/html/index.html'},
        'limits': [
            'Local functional verification; not production traffic or a cloud deployment.',
            'No recipe quality/accuracy improvement or business impact was measured.',
            'Accounts, inventory, preferences, recommendations and groceries are process-local memory.',
            'Grocery quantities retain the original simplified unit model; no cross-unit conversion or inventory consumption.',
        ],
    }
    http_path = root / 'reports/local-verification/http-smoke.json'
    if http_path.is_file():
        smoke = json.loads(http_path.read_text())
        if smoke.get('sourceAndTestSha256') != data['sourceAndTestSha256']:
            raise SystemExit('Real HTTP smoke report does not match current source/tests. Re-run the HTTP checks.')
        if smoke.get('failed') or not smoke.get('passed'):
            raise SystemExit('Real HTTP smoke report has failures or no successful checks.')
        data['httpSmoke'] = {key: smoke[key] for key in ['generatedAtUtc', 'transport', 'passed', 'failed']}
        data['httpSmoke']['report'] = str(http_path.relative_to(root))
    return data


def compact_coverage(source_path, output_path):
    """Retain raw JaCoCo counters per class without the much larger line-by-line HTML report."""
    source = ET.parse(source_path).getroot()
    result = ET.Element('jacoco-summary', {'source': 'build/reports/jacoco/test/jacocoTestReport.xml',
                                        'scope': 'all compiled production classes; no exclusions'})
    for counter in source.findall('counter'):
        result.append(copy.deepcopy(counter))
    for package in source.findall('package'):
        target = ET.SubElement(result, 'package', {'name': package.get('name')})
        for cls in package.findall('class'):
            entry = ET.SubElement(target, 'class', {'name': cls.get('name')})
            for counter in cls.findall('counter'):
                entry.append(copy.deepcopy(counter))
    ET.indent(result, space='  ')
    ET.ElementTree(result).write(output_path, encoding='utf-8', xml_declaration=True)


def markdown(data):
    tests, coverage = data['testResults'], data['coverage']
    lines = ['# SmartFridge local verification', '', f"Generated UTC: {data['generatedAtUtc']}", '',
             data['scope'], '',
             f"JUnit: {tests['tests']} tests; {tests['failures']} failures; {tests['errors']} errors; {tests['skipped']} skipped.", '',
             '| Scope | Line coverage | Branch coverage |', '| --- | --- | --- |']
    def metric(m):
        return f"{m['covered']}/{m['total']} ({m['percent']}%)" if m['total'] else '0/0 (not applicable)'
    lines.append(f"| All production Java | {metric(coverage['line'])} | {metric(coverage['branch'])} |")
    for name, layer in sorted(data['layers'].items()):
        lines.append(f"| {name} | {metric(layer['line'])} | {metric(layer['branch'])} |")
    lines.extend(['', 'The denominator includes main entrypoints and legacy CLI classes. Coverage is not a correctness guarantee.', '',
                  '## Verified behaviors', '',
                  '- BCrypt registration/login and HS256 bearer-token signature, issuer, audience, type, subject and expiry checks.',
                  '- Two users retain separate inventory, preferences, recommendation state and grocery lists; foreign IDs return 404.',
                  '- One user checkout does not clear another user; missing/forged/expired/invalid tokens are rejected.',
                  '- Twelve recipe-add requests on four worker threads merge into three lines with no lost quantity updates.',
                  '- Malformed JSON, fractional/null numeric inputs, negative quantities and invalid prices return structured errors without mutation.',
                  '- Quantity overflow returns 409 without resetting stock or partially applying a recipe plan; negative deltas still floor at zero.',
                  '- Concurrent increments/toggles retain exact quantity and parity; competing increments at the limit yield one success and one conflict.',
                  '- Concurrent recipe planning and checkout retain either an empty list or complete ingredient quantities for whole recipe plans.',
                  '- Recipe scoring checks exact 60/20/15/5-point components, time boundaries and all three health goals.',
                  '- Expired/depleted inventory is excluded; usable batches nearest expiry determine urgency; optional ingredients do not reduce coverage.',
                  '', '## Limits', ''] + ['- ' + limit for limit in data['limits']] + ['', '## Reproduce', '',
                  '```sh', './gradlew clean test jacocoTestReport', 'python3 tools/summarize_verification.py', '```', '',
                  f"Base commit: `{data['baseGitCommit']}`", f"Source/test SHA256: `{data['sourceAndTestSha256']}`", ''])
    if 'httpSmoke' in data:
        smoke = data['httpSmoke']
        lines.extend(['## Real HTTP smoke checks', '',
                      f"{smoke['passed']} checks passed; {smoke['failed']} failed against a running local Spring Boot server.",
                      f"Evidence: `{smoke['report']}`. This is functional verification, not a throughput/latency benchmark.", ''])
    return '\n'.join(lines)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', default='reports/local-verification')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    data = summarize(root)
    if data['testResults']['failures'] or data['testResults']['errors']:
        raise SystemExit('Tests contain failures/errors; refusing to publish a successful verification report.')
    out = root / args.output
    out.mkdir(parents=True, exist_ok=True)
    (out / 'summary.json').write_text(json.dumps(data, indent=2) + '\n')
    (out / 'summary.md').write_text(markdown(data))
    compact_coverage(root / data['artifacts']['jacocoXml'], out / 'jacoco-class-summary.xml')
    print(json.dumps({'tests': {k: v for k, v in data['testResults'].items() if k != 'suites'},
                      'coverage': data['coverage'], 'report': str(out / 'summary.md')}, indent=2))
