import xml.etree.ElementTree as ET

try:
    with open("Diagrama DER.drawio.xml", "r", encoding="utf-8") as f:
        content = f.read()
    print("Length of content:", len(content))
    print("First 1000 characters:")
    print(content[:1000])
except Exception as e:
    print("Error:", e)
