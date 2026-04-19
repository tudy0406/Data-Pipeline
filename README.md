# Data Pipeline

A Java-based video processing pipeline that ingests an MP4 file and produces a structured output bundle containing transcoded video, thumbnails, a transcript, a Romanian dub, and a manifest.

## Requirements

- Java 17
- Maven 3.8+
- FFmpeg and ffprobe (available on PATH)
- Python 3.9+ with the following packages (only needed for audio/text tasks):
  - `openai-whisper`
  - `deepl`
  - `gtts`

## Build

```bash
cd data_pipeline
mvn package -q
```

This produces `target/data_pipeline-1.0-SNAPSHOT.jar`.

## Usage

```bash
java -jar target/data_pipeline-1.0-SNAPSHOT.jar <input.mp4> <output_dir> [scripts_dir]
```

- `input.mp4` — path to the source video file
- `output_dir` — directory where the output bundle will be written (created if it does not exist)
- `scripts_dir` — optional path to the Python scripts folder (defaults to `scripts/`)

Example:

```bash
java -jar target/data_pipeline-1.0-SNAPSHOT.jar movie.mp4 output/ scripts/
```

## Output Structure

```
output/
  manifest.json
  video/
    h264/   4k_h264.mp4, 1080p_h264.mp4, 720p_h264.mp4
    vp9/    4k_vp9.webm, 1080p_vp9.webm, 720p_vp9.webm
    hevc/   4k_hevc.mkv, 1080p_hevc.mkv, 720p_hevc.mkv
  images/
    sprite_map.jpg
    thumbnails/
  text/
    source_transcript.txt
    ro_translation.txt
  audio/
    ro_dub_synthetic.aac
  metadata/
    scene_analysis.json
```

## Pipeline Phases

The pipeline runs through the following phases in order:

1. **Ingest** — validates the file format and checks MP4 integrity
2. **Analysis** — detects intro/outro boundaries, credit sequences, and classifies scenes
3. **Visuals / Audio-Text** — run in parallel: transcodes video at three resolutions and three codecs; generates thumbnails and sprite map; produces transcript, Romanian translation, and synthetic dub
4. **Compliance** — safety scan and regional branding tagging
5. **Packaging** — applies DRM stub and writes the final `manifest.json`

## Environment Variables

The translation task requires a DeepL API key:

```bash
set DEEPL_API_KEY=your_key_here   # Windows
export DEEPL_API_KEY=your_key_here  # Linux / macOS
```

## Notes

- The compliance and DRM tasks are stubs intended to be replaced with real implementations.
- Analysis tasks (scene indexing, intro/outro detection, credit detection) are pure Java and do not require Python.
- For short test runs, use a clip of 30-60 seconds to avoid long transcode times.
