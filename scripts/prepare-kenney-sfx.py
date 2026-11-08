"""Convert the selected Kenney CC0 OGG effects to Java 8 friendly PCM WAV.

Usage:
    python -m pip install soundfile numpy
    python scripts/prepare-kenney-sfx.py --ui kenney_ui-audio.zip \
        --digital kenney_digital-audio.zip --impact kenney_impact-sounds.zip

The downloaded archives are intentionally not checked in. The generated WAVs are.
"""

import argparse
import hashlib
import io
from pathlib import Path
import zipfile

import numpy as np
import soundfile as sf


ARCHIVES = {
    "ui": "946FC23A63D535D693EB31B2EABB80C8C28D6351E2186B344CEB71B2CB1D5EB6",
    "digital": "24E6CE28B76A6D8C89CFF4D331E0965FF5C3DE8A73C612028E9D363CC64E4F06",
    "impact": "029D734AF1582474EDF3A694D1B0CEBC97C1C152F2F39FA34D4C2BAFC5DE77F8",
}

# Output name: (archive, source filename, gain). All source files are from
# Kenney's original CC0 packs. Gain keeps quick actions behind major feedback.
SOUNDS = {
    "button": ("ui", "click3", 0.80),
    "move": ("ui", "click1", 0.62),
    "rotate": ("ui", "switch12", 0.70),
    "drop": ("impact", "impactGeneric_light_000", 0.85),
    "line-clear": ("digital", "threeTone1", 0.68),
    "attack": ("digital", "phaserDown1", 0.63),
    "hit": ("impact", "impactMetal_heavy_000", 0.80),
    "item-acquire": ("digital", "powerUp5", 0.70),
    "item-use": ("digital", "phaseJump2", 0.67),
    "heal": ("digital", "powerUp2", 0.66),
    "fever": ("digital", "highUp", 0.57),
    "victory": ("digital", "powerUp1", 0.70),
    "defeat": ("digital", "lowThreeTone", 0.60),
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for pack in ARCHIVES:
        parser.add_argument("--" + pack, required=True, type=Path)
    parser.add_argument(
        "--output", type=Path,
        default=Path(__file__).resolve().parent.parent
        / "src/main/resources/audio/kenney",
    )
    args = parser.parse_args()
    sources = {}
    for pack, expected in ARCHIVES.items():
        path = getattr(args, pack)
        digest = hashlib.sha256(path.read_bytes()).hexdigest().upper()
        if digest != expected:
            parser.error("archive checksum mismatch: " + str(path))
        archive = zipfile.ZipFile(path)
        license_text = archive.read("License.txt").decode("utf-8", errors="replace")
        if "Creative Commons Zero, CC0" not in license_text:
            parser.error("CC0 license missing: " + str(path))
        sources[pack] = archive
    args.output.mkdir(parents=True, exist_ok=True)
    try:
        for name, (pack, original, gain) in SOUNDS.items():
            encoded = sources[pack].read("Audio/" + original + ".ogg")
            samples, sample_rate = sf.read(io.BytesIO(encoded), dtype="float32")
            if samples.ndim == 2:
                samples = np.mean(samples, axis=1)
            # The source sounds already contain tails. A tiny extra fade avoids
            # clicks at the exact cut point; keep the original musical timing.
            fade = min(128, len(samples) // 8)
            samples[-fade:] *= np.linspace(1.0, 0.0, fade, dtype=np.float32)
            samples = np.clip(samples * gain, -0.99, 0.99)
            target = args.output / (name + ".wav")
            sf.write(target, samples, sample_rate, subtype="PCM_16", format="WAV")
            print(f"{target.name}: {len(samples)/sample_rate:.3f}s, "
                  f"{hashlib.sha256(target.read_bytes()).hexdigest().upper()}")
    finally:
        for archive in sources.values():
            archive.close()


if __name__ == "__main__":
    main()
