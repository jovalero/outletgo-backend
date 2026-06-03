import xml.etree.ElementTree as ET
import re

def parse_drawio(file_path):
    tree = ET.parse(file_path)
    root = tree.getroot()
    
    cells = []
    for cell in root.iter('mxCell'):
        cells.append({
            'id': cell.get('id'),
            'parent': cell.get('parent'),
            'value': cell.get('value', ''),
            'style': cell.get('style', '')
        })
        
    tables = {}
    for c in cells:
        style = c['style'] or ''
        if 'shape=table' in style or 'startSize=' in style:
            name = re.sub('<[^<]+?>', '', c['value']).strip()
            if name:
                tables[c['id']] = {
                    'name': name,
                    'rows': []
                }
                
    rows = {}
    for c in cells:
        style = c['style'] or ''
        parent_id = c['parent']
        if 'shape=tableRow' in style and parent_id in tables:
            rows[c['id']] = {
                'table_id': parent_id,
                'cells': []
            }
            tables[parent_id]['rows'].append(rows[c['id']])
            
    for c in cells:
        parent_id = c['parent']
        if parent_id in rows:
            val = re.sub('<[^<]+?>', '', c['value']).strip()
            rows[parent_id]['cells'].append(val)
            
    for t_id, t in tables.items():
        print(f"\nTable: {t['name']}")
        print("-" * (len(t['name']) + 7))
        for r in t['rows']:
            non_empty_cells = [c for c in r['cells'] if c]
            if len(non_empty_cells) >= 2:
                key_type = non_empty_cells[0]
                col_name = non_empty_cells[1]
                col_type = non_empty_cells[2] if len(non_empty_cells) > 2 else ""
                print(f"  {key_type:<10} | {col_name:<20} {col_type}")
            elif len(non_empty_cells) == 1:
                print(f"             | {non_empty_cells[0]:<20}")

if __name__ == "__main__":
    parse_drawio("Diagrama DER.drawio.xml")
