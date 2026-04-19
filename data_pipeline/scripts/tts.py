#!/usr/bin/env python3

import argparse
import sys
import os
import tempfile
import subprocess
from gtts import gTTS


def generate_dub(input_path: str, lang: str, output_path: str) -> None:
    if not os.path.exists(input_path):
        print(f"[TTS] ERROR: Input file not found: {input_path}", file=sys.stderr)
        sys.exit(1)

    with open(input_path, "r", encoding="utf-8") as f:
        text = f.read().strip()

    if not text:
        print("[TTS] ERROR: Input file is empty.", file=sys.stderr)
        sys.exit(1)

    print(f"[TTS] Generating speech for language '{lang}'...")
    tts = gTTS(text=text, lang=lang, slow=False)

    # gTTS outputs MP3 — save to a temp file then convert to AAC via ffmpeg
    os.makedirs(os.path.dirname(output_path) or ".", exist_ok=True)

    with tempfile.NamedTemporaryFile(suffix=".mp3", delete=False) as tmp:
        tmp_mp3 = tmp.name

    try:
        tts.save(tmp_mp3)
        print(f"[TTS] Converting MP3 to AAC...")

        result = subprocess.run(
            ["ffmpeg", "-y", "-i", tmp_mp3, "-c:a", "aac", "-b:a", "128k", output_path],
            capture_output=True, text=True
        )

        if result.returncode != 0:
            print(f"[TTS] ffmpeg error:\n{result.stderr}", file=sys.stderr)
            sys.exit(1)

    finally:
        if os.path.exists(tmp_mp3):
            os.remove(tmp_mp3)

    print(f"[TTS] Synthetic dub written to: {output_path}")
    print(f"[TTS] Input length: {len(text)} characters")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Generate a TTS audio dub from a translated text file.")
    parser.add_argument("--input",  required=True, help="Path to the translated text file (.txt).")
    parser.add_argument("--lang",   required=True, help="Language code for TTS (e.g. ro, en, de).")
    parser.add_argument("--output", required=True, help="Path to write the output audio file (.aac).")
    args = parser.parse_args()

    generate_dub(args.input, args.lang, args.output)
