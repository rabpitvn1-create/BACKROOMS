"""Fail packaged test/release builds on missing configuration, without printing values."""
import os
import sys
from urllib.parse import urlparse

required = ['GEHIHI_API_KEY', 'GEHIHI_MODEL'] + [f'GEMINI_API_KEY_{i}' for i in range(1, 6)]
missing = [name for name in required if not os.environ.get(name, '').strip()]
if missing:
    sys.exit('Missing provider configuration: ' + ', '.join(missing))
base = os.environ.get('GEHIHI_BASE_URL', '').strip() or 'https://api.vilao.ai/v1'
url = urlparse(base)
if url.scheme != 'https' or not url.hostname or url.username or url.password or url.query or url.fragment:
    sys.exit('GEHIHI_BASE_URL must be an HTTPS API endpoint without credentials/query/fragment')
print('PASS: Gehihi configured; Gemini keys K1-K5 configured (values hidden)')
