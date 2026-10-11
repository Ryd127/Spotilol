"""Fail fast if the embedded native-UI JavaScript is not valid JavaScript."""
from pathlib import Path
import subprocess
import tempfile

source = Path("app/src/main/java/com/project/lol/ui/components/NativeMobileJs.kt").read_text(encoding="utf-8")
start = 'const val INSTALL = """'
assert source.count(start) == 1, "native UI JS entrypoint missing"
payload = source.split(start, 1)[1].split('"""', 1)[0]
assert len(payload) > 1000, "native UI JS payload unexpectedly short"
assert "splNativeSnapshot" in payload and "splNativeNavigate" in payload
with tempfile.TemporaryDirectory(prefix="spotilol-native-js-") as tmp:
    path = Path(tmp) / "native-ui.js"
    path.write_text(payload, encoding="utf-8")
    subprocess.run(["node", "--check", str(path)], check=True)
print("Native UI JavaScript syntax verified")
