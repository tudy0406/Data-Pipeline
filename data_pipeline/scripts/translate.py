#!/usr/bin/env python3

import argparse
import sys
import os
import deepl


def translate(input_path: str, target_lang: str, output_path: str) -> None:
    api_key = os.environ.get("DEEPL_API_KEY")
    if not api_key:
        print("[ERROR] DEEPL_API_KEY environment variable is not set.", file=sys.stderr)
        sys.exit(1)

    if not os.path.exists(input_path):
        print(f"[ERROR] Input file not found: {input_path}", file=sys.stderr)
        sys.exit(1)

    with open(input_path, "r", encoding="utf-8") as f:
        source_text = f.read()

    if not source_text.strip():
        print("[ERROR] Input file is empty.", file=sys.stderr)
        sys.exit(1)

    print(f"[TRANSLATE] Connecting to DeepL API...")
    translator = deepl.Translator(api_key)

    print(f"[TRANSLATE] Translating to '{target_lang}'...")
    result = translator.translate_text(source_text, target_lang=target_lang)

    translated_text = str(result)

    os.makedirs(os.path.dirname(output_path) or ".", exist_ok=True)
    with open(output_path, "w", encoding="utf-8") as f:
        f.write(translated_text)

    print(f"[TRANSLATE] Translation written to: {output_path}")
    print(f"[TRANSLATE] Characters translated: {len(source_text)}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Translate a text file using DeepL.")
    parser.add_argument("--input",       required=True, help="Path to the source transcript (.txt).")
    parser.add_argument("--target-lang", required=True, help="Target language code (e.g. RO, DE, FR).")
    parser.add_argument("--output",      required=True, help="Path to write the translated file (.txt).")
    args = parser.parse_args()

    translate(args.input, args.target_lang, args.output)
