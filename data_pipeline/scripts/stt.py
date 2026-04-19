#!/usr/bin/env python3

import argparse
import sys
import os
import whisper


def transcribe(input_path: str, output_path: str, model_name: str) -> None:
    if not os.path.exists(input_path):
        print(f"[ERROR] Input file not found: {input_path}", file=sys.stderr)
        sys.exit(1)

    print(f"[STT] Loading Whisper model '{model_name}'...")
    model = whisper.load_model(model_name)

    print(f"[STT] Transcribing: {input_path}")
    result = model.transcribe(input_path, verbose=False)

    transcript_lines = []
    for segment in result["segments"]:
        start = format_timestamp(segment["start"])
        text  = segment["text"].strip()
        transcript_lines.append(f"[{start}] {text}")

    transcript = "\n".join(transcript_lines)

    os.makedirs(os.path.dirname(output_path) or ".", exist_ok=True)
    with open(output_path, "w", encoding="utf-8") as f:
        f.write(transcript)

    print(f"[STT] Transcript written to: {output_path}")
    print(f"[STT] Segments: {len(result['segments'])} | Language detected: {result['language']}")


def format_timestamp(seconds: float) -> str:
    h = int(seconds // 3600)
    m = int((seconds % 3600) // 60)
    s = int(seconds % 60)
    return f"{h:02}:{m:02}:{s:02}"


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Transcribe audio/video to text using Whisper.")
    parser.add_argument("--input",  required=True, help="Path to the input audio or video file.")
    parser.add_argument("--output", required=True, help="Path to write the output transcript (.txt).")
    parser.add_argument("--model",  default="base",
                        choices=["tiny", "base", "small", "medium", "large"],
                        help="Whisper model size (default: base).")
    args = parser.parse_args()

    transcribe(args.input, args.output, args.model)
