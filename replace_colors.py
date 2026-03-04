import re
import sys

def replace_colors(file_path):
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    replacements = {
        r"@color/background_light": "@color/theme_background",
        r"@color/slate_900": "@color/theme_text_primary",
        r"@color/slate_800": "@color/theme_text_primary",
        r"@color/slate_700": "@color/theme_text_secondary",
        r"@color/slate_600": "@color/theme_text_secondary",
        r"@color/slate_500": "@color/theme_text_hint",
        r"@color/slate_300": "@color/theme_border",
        r"@color/slate_100": "@color/theme_surface_variant",
        r"@color/slate_50": "@color/theme_surface",
    }

    for old, new in replacements.items():
        content = content.replace(old, new)
        
    with open(file_path, "w", encoding="utf-8") as f:
        f.write(content)

if __name__ == "__main__":
    replace_colors(r"d:\Nghiaaaa\7-Mobile\app\src\main\res\layout\activity_timeline.xml")
