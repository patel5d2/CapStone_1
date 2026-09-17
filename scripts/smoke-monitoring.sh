#!/usr/bin/env bash
# Called by smoke-container.sh with disposable fixture credentials in the environment.
set -euo pipefail
python3 - <<'PY'
import base64
import json
import os
import time
import urllib.error
import urllib.parse
import urllib.request

app = os.environ.get('APP_URL', 'http://127.0.0.1:18080')
prometheus = os.environ.get('PROMETHEUS_URL', 'http://127.0.0.1:19090')
grafana = os.environ.get('GRAFANA_URL', 'http://127.0.0.1:13000')
metrics_password = os.environ['METRICS_PASSWORD']
grafana_user = os.environ.get('GF_SECURITY_ADMIN_USER', 'admin')
grafana_password = os.environ['GF_SECURITY_ADMIN_PASSWORD']

def request(url, credentials=None):
    headers = {}
    if credentials:
        encoded = base64.b64encode((':'.join(credentials)).encode()).decode()
        headers['Authorization'] = 'Basic ' + encoded
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=5) as response:
            return response.status, response.read().decode()
    except urllib.error.HTTPError as error:
        return error.code, error.read().decode()

def assert_status(path, expected, credentials=None):
    status, body = request(app + path, credentials)
    assert status == expected, f'{path}: expected {expected}, received {status}'
    return body

def wait_for(description, check):
    deadline = time.monotonic() + 90
    while time.monotonic() < deadline:
        try:
            if check():
                print(description + ': passed', flush=True)
                return
        except (OSError, ValueError, KeyError):
            pass
        time.sleep(2)
    raise SystemExit(description + ': did not become ready within 90 seconds')

def query(expression):
    status, body = request(prometheus + '/api/v1/query?' + urllib.parse.urlencode({'query': expression}))
    if status != 200:
        return []
    payload = json.loads(body)
    return payload['data']['result'] if payload['status'] == 'success' else []

health = json.loads(assert_status('/actuator/health', 200))
assert health['status'] == 'UP' and 'components' not in health and 'details' not in health
assert_status('/actuator/prometheus', 401)
assert_status('/actuator/prometheus', 401, ('prometheus', 'incorrect'))
for path in ('/api/schools', '/student', '/actuator/metrics', '/actuator/info'):
    assert_status(path, 401, ('prometheus', metrics_password))
metrics = assert_status('/actuator/prometheus', 200, ('prometheus', metrics_password))
assert 'http_server_requests_seconds_bucket' in metrics, 'HTTP histogram buckets are missing'
assert 'jvm_memory_used_bytes' in metrics, 'JVM metrics are missing'
print('Metrics authentication, API isolation, and histogram export: passed', flush=True)

def target_ready():
    result = query('up{job="spring-boot-app"}')
    return len(result) == 1 and result[0]['value'][1] == '1'

wait_for('Authenticated Prometheus scrape', target_ready)
wait_for('Prometheus histogram ingestion', lambda: bool(query('http_server_requests_seconds_bucket{job="spring-boot-app"}')))

def dashboard_ready():
    status, body = request(grafana + '/api/dashboards/uid/campusbridge-overview',
                           (grafana_user, grafana_password))
    if status != 200:
        return False
    dashboard = json.loads(body)['dashboard']
    return len(dashboard['panels']) >= 6 and dashboard['uid'] == 'campusbridge-overview'

wait_for('Provisioned Grafana dashboard', dashboard_ready)
status, body = request(grafana + '/api/datasources/uid/campusbridge-prometheus/health',
                       (grafana_user, grafana_password))
assert status == 200 and json.loads(body)['status'] == 'OK', 'Grafana cannot query Prometheus'
print('Grafana datasource connection: passed', flush=True)
PY
