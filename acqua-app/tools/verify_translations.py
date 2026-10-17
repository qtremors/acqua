"""Check shipped translation keys, format placeholders, plural forms and fallback."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1] / "core" / "data" / "src" / "main" / "res"
BASE = {node.attrib["name"]: node for node in ET.parse(ROOT / "values" / "strings.xml").getroot()}
PLACEHOLDER = re.compile(r"%(?:\d+\$)?[dsf]")
catalogs = {}
for language in ("es", "hi", "ar"):
    elements = list(ET.parse(ROOT / f"values-{language}" / "strings.xml").getroot())
    names = [node.attrib["name"] for node in elements]
    assert len(names) == len(set(names)), language
    for node in elements:
        name = node.attrib["name"]
        assert name in BASE and node.tag == BASE[name].tag, (language, name)
        assert BASE[name].get("translatable") != "false", name
        expected = sorted(PLACEHOLDER.findall("".join(BASE[name].itertext())))
        if node.tag == "plurals":
            quantities = {row.attrib["quantity"] for row in node}
            required = {"zero", "one", "two", "few", "many", "other"} if language == "ar" else {"one", "other"}
            assert quantities == required, (language, name, quantities)
            expected = sorted(PLACEHOLDER.findall(BASE[name].find("item[@quantity='other']").text))
            texts = [row.text for row in node]
        else:
            texts = ["".join(node.itertext())]
        for text in texts:
            assert text and sorted(PLACEHOLDER.findall(text)) == expected, (language, name)
            assert chr(0x2014) not in text
    assert len(names) >= 40
    assert "download_diagnostics" not in names and "download_diagnostics" in BASE
    catalogs[language] = set(names)
assert catalogs["es"] == catalogs["hi"] == catalogs["ar"]
print(f"Verified {len(catalogs['es'])} resource entries in each of three starter catalogs; English fallback retained.")
