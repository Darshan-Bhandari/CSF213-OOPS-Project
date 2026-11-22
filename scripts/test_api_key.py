"""
Quick test script to verify that the OPENROUTER_API_KEY and OPENROUTER_MODEL
configured in .env are valid and working.
"""
import json
import urllib.request
import urllib.error
from pathlib import Path

root = Path(__file__).resolve().parent.parent
dotenv = root / '.env'

if not dotenv.exists():
    print("Error: .env file not found.")
    exit(1)

config = {}
for line in dotenv.read_text(encoding='utf-8').splitlines():
    line = line.strip()
    if line and not line.startswith('#') and '=' in line:
        k, v = line.split('=', 1)
        config[k.strip()] = v.strip().strip("'\"")

key = config.get('OPENROUTER_API_KEY')
model = config.get('OPENROUTER_MODEL')

if not key:
    print("[FAIL] OPENROUTER_API_KEY is not set in .env")
    exit(1)

if not model:
    print("[FAIL] OPENROUTER_MODEL is not set in .env")
    exit(1)

masked_key = f"{key[:12]}...{key[-4:]}" if len(key) > 16 else "***"
print(f"Testing OpenRouter API Key: {masked_key}")
print(f"Testing Model:              {model}")
print("-" * 50)

# Step 1: Check Key Authentication
auth_req = urllib.request.Request(
    'https://openrouter.ai/api/v1/auth/key',
    headers={'Authorization': f'Bearer {key}'}
)

try:
    with urllib.request.urlopen(auth_req, timeout=10) as resp:
        data = json.loads(resp.read().decode('utf-8')).get('data', {})
        print("[SUCCESS] API Key is VALID!")
        print(f"  - Key Label: {data.get('label')}")
        print(f"  - Free Tier: {data.get('is_free_tier')}")
        print(f"  - Remaining Limit ($): {data.get('limit_remaining')}")
        free_reqs = data.get('free_model_daily_requests', {}).get('remaining', 'N/A')
        print(f"  - Free Requests Remaining Today: {free_reqs}")
except urllib.error.HTTPError as e:
    print(f"[FAIL] Key verification failed with HTTP {e.code}: {e.read().decode('utf-8')}")
    exit(1)
except Exception as e:
    print(f"[FAIL] Error connecting to OpenRouter: {e}")
    exit(1)

print("-" * 50)

# Step 2: Test Chat Completion with the configured model
print(f"Testing Chat Completion with model '{model}'...")
payload = json.dumps({
    'model': model,
    'messages': [
        {'role': 'user', 'content': 'Hello, are you working? Reply with one short sentence.'}
    ]
}).encode('utf-8')

completion_req = urllib.request.Request(
    'https://openrouter.ai/api/v1/chat/completions',
    data=payload,
    headers={
        'Authorization': f'Bearer {key}',
        'Content-Type': 'application/json'
    }
)

try:
    with urllib.request.urlopen(completion_req, timeout=20) as resp:
        result = json.loads(resp.read().decode('utf-8'))
        content = result['choices'][0]['message']['content'].strip()
        print("[SUCCESS] Model response received:")
        print(f"  \"{content}\"")
        print("\nAll checks passed! Your OpenRouter API configuration is ready.")
except urllib.error.HTTPError as e:
    print(f"[FAIL] Completion request failed with HTTP {e.code}: {e.read().decode('utf-8')}")
    exit(1)
except Exception as e:
    print(f"[FAIL] Error completing chat request: {e}")
    exit(1)
