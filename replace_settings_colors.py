import re
import sys

def replace_colors(file_path):
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    replacements = {
        r'"#121629"': '"@color/theme_background"',
        r'"#1A1F36"': '"@color/theme_card"',
        r'"#22273B"': '"@color/theme_divider"',
        r'"#FFFFFF"': '"@color/theme_text_primary"',
        r'"#A0A0A0"': '"@color/theme_text_secondary"',
    }

    # Custom hardcoded fixes:
    # 62: app:cardBackgroundColor="#2945FF" -> keep (primary button/accent bg)
    # 154: app:tint="#3B82F6" -> keep (blue icon tint)
    # 178: app:tint="#A855F7" -> keep (purple icon tint)
    # 202: app:tint="#F97316" -> keep (orange icon tint)
    # 413: app:tint="#EF4444" -> keep (red icon tint)
    # 420: android:textColor="#EF4444" -> keep (red text)

    for old, new in replacements.items():
        content = content.replace(old, new)
        
    with open(file_path, "w", encoding="utf-8") as f:
        f.write(content)

if __name__ == "__main__":
    replace_colors(r"d:\Nghiaaaa\7-Mobile\app\src\main\res\layout\layout_project_settings_panel.xml")
