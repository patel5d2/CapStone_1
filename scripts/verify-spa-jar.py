"""Fail release packaging if index.html or any compiled asset is absent from the JAR."""
from html.parser import HTMLParser
from pathlib import Path
from zipfile import ZipFile


class Assets(HTMLParser):
    def __init__(self):
        super().__init__()
        self.root = False
        self.paths = []

    def handle_starttag(self, tag, attributes):
        attrs = dict(attributes)
        self.root |= attrs.get("id") == "root"
        path = attrs.get("src") if tag == "script" else attrs.get("href")
        if path and path.startswith("/assets/"):
            self.paths.append(path)


jars = list(Path("target").glob("*.jar"))
assert len(jars) == 1, "Expected exactly one application JAR in target/"
with ZipFile(jars[0]) as jar:
    page = Assets()
    page.feed(jar.read("BOOT-INF/classes/static/index.html").decode())
    assert page.root, "The packaged index.html has no React root"
    assert any(path.endswith(".js") for path in page.paths), "Missing compiled JavaScript"
    assert any(path.endswith(".css") for path in page.paths), "Missing compiled stylesheet"
    for path in page.paths:
        assert jar.getinfo("BOOT-INF/classes/static" + path).file_size > 0, f"Empty asset: {path}"
print(f"Verified packaged SPA and {len(page.paths)} assets in {jars[0].name}.")
