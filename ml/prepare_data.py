"""Audit SCIN, download one photo per case, remove duplicates, and freeze splits.

Only public dataset images are used. App photos never enter this pipeline.
"""
import argparse
import ast
import collections
import concurrent.futures
import csv
import hashlib
import io
import json
import random
import time
import urllib.request
from pathlib import Path

CLASSES = ["Eczema", "Urticaria", "Folliculitis"]
SEED = 20261006
BASE = "https://storage.googleapis.com/dx-scin-public-data/"


def dominant_label(raw):
    labels = ast.literal_eval(raw or "{}")
    ranked = sorted(labels.items(), key=lambda item: (-item[1], item[0]))
    if not ranked or ranked[0][1] < 0.5:
        return None
    if len(ranked) > 1 and ranked[0][1] - ranked[1][1] < 0.15:
        return None
    return ranked[0][0]


def stable_order(case_id):
    return hashlib.sha256(f"{SEED}:{case_id}".encode()).hexdigest()


def freeze_splits(rows):
    """Stratify by supported class; OOD examples only enter calibration or test."""
    buckets = collections.defaultdict(list)
    for row in rows:
        buckets[row["label"] if row["label"] in CLASSES else "__ood__"].append(row)
    for label, group in buckets.items():
        group.sort(key=lambda row: stable_order(row["case_id"]))
        n = len(group)
        for i, row in enumerate(group):
            if label == "__ood__":
                row["split"] = "ood_calibration" if i < int(n * 0.6) else "ood_test"
            else:
                row["split"] = ("train" if i < int(n * .60) else "tune" if i < int(n * .75)
                                else "calibration" if i < int(n * .85) else "test")
    return sorted(rows, key=lambda row: row["case_id"])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--data-dir", type=Path, required=True)
    parser.add_argument("--ood-cases", type=int, default=240)
    args = parser.parse_args()
    root = args.data_dir
    meta = root / "metadata"
    meta.mkdir(parents=True, exist_ok=True)
    for name in ["scin_cases.csv", "scin_labels.csv"]:
        if not (meta / name).exists():
            urllib.request.urlretrieve(BASE + "dataset/" + name, meta / name)
    cases = list(csv.DictReader((meta / "scin_cases.csv").open()))
    labels = {r["case_id"]: r for r in csv.DictReader((meta / "scin_labels.csv").open())}
    counts = collections.Counter()
    excluded = collections.Counter()
    supported, ood = [], []
    for case in cases:
        annotation = labels.get(case["case_id"], {})
        label = dominant_label(annotation.get("weighted_skin_condition_label", ""))
        if not label:
            excluded["missing_or_ambiguous_label"] += 1
            continue
        if not any(annotation.get(f"dermatologist_gradable_for_skin_condition_{i}", "") in {"True", "true", "DEFAULT_YES_IMAGE_QUALITY_SUFFICIENT"} for i in range(1, 4)):
            excluded["not_gradable"] += 1
            continue
        paths = [case.get(f"image_{i}_path", "") for i in range(1, 4)]
        paths = [p for p in paths if p]
        if not paths:
            excluded["no_photo"] += 1
            continue
        counts[label] += 1
        monk = annotation.get("monk_skin_tone_label_india", "") or annotation.get("monk_skin_tone_label_us", "")
        row = dict(case_id=case["case_id"], label=label, image_path=paths[0], all_image_paths=paths,
                   monk=monk or "unknown", label_weights=annotation["weighted_skin_condition_label"])
        (supported if label in CLASSES else ood).append(row)
    ood.sort(key=lambda r: stable_order(r["case_id"]))
    selected = supported + ood[:args.ood_cases]
    print("Audited class counts:", dict(counts.most_common()), flush=True)
    print(f"Selected {len(supported)} supported + {len(selected)-len(supported)} unsupported cases", flush=True)
    (root / "images").mkdir(exist_ok=True)
    from PIL import Image, ImageOps
    import numpy as np

    def download(row):
        cache = root / "images" / (row["case_id"] + ".json")
        if cache.exists():
            return json.loads(cache.read_text())
        for attempt in range(3):
            try:
                with urllib.request.urlopen(BASE + row["image_path"], timeout=45) as response:
                    raw = response.read()
                image = ImageOps.exif_transpose(Image.open(io.BytesIO(raw))).convert("RGB")
                if min(image.size) < 160:
                    return dict(error="undersized", case_id=row["case_id"])
                pixels = image.resize((64, 64), Image.Resampling.BILINEAR)
                gray = np.asarray(image.resize((9, 8)).convert("L"))
                bits = (gray[:, 1:] > gray[:, :-1]).flatten()
                dhash = sum(int(bit) << i for i, bit in enumerate(bits))
                row.update(source_sha256=hashlib.sha256(raw).hexdigest(),
                           pixel_sha256=hashlib.sha256(pixels.tobytes()).hexdigest(),
                           dhash=f"{dhash:016x}", original_size=list(image.size))
                image.thumbnail((640, 640), Image.Resampling.LANCZOS)
                row["file"] = "images/" + row["case_id"] + ".jpg"
                image.save(root / row["file"], quality=92)
                cache.write_text(json.dumps(row))
                return row
            except Exception as exc:
                if attempt == 2: return dict(error=type(exc).__name__, case_id=row["case_id"])
                time.sleep(attempt + 1)

    results = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as executor:
        for i, row in enumerate(executor.map(download, selected)):
            results.append(row)
            if (i + 1) % 50 == 0: print(f"Downloaded {i+1}/{len(selected)}", flush=True)
    # Remove all members of a duplicate cluster, including conflicting labels.
    valid = [r for r in results if "error" not in r]
    duplicate_ids = set()
    for i, a in enumerate(valid):
        for b in valid[i+1:]:
            same_path = bool(set(a["all_image_paths"]) & set(b["all_image_paths"]))
            same_pixels = a["pixel_sha256"] == b["pixel_sha256"]
            similar = (int(a["dhash"], 16) ^ int(b["dhash"], 16)).bit_count() <= 2
            if same_path or same_pixels or similar:
                duplicate_ids.update([a["case_id"], b["case_id"]])
    rows = freeze_splits([r for r in valid if r["case_id"] not in duplicate_ids])
    assert len({r["case_id"] for r in rows}) == len(rows)
    manifest = root / "manifest.json"
    manifest.write_text(json.dumps(rows, indent=2))
    audit = dict(seed=SEED, classes=CLASSES, source_cases=len(cases), eligible_class_counts=dict(counts),
                 exclusions=dict(excluded), download_errors=[r for r in results if "error" in r],
                 duplicate_case_ids=sorted(duplicate_ids), final_cases=len(rows),
                 split_counts=dict(collections.Counter(r["split"] for r in rows)),
                 class_split_counts=dict(collections.Counter(r["label"]+" / "+r["split"] for r in rows)),
                 metadata_sha256={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in meta.glob("*.csv")},
                 manifest_sha256=hashlib.sha256(manifest.read_bytes()).hexdigest())
    (root / "audit.json").write_text(json.dumps(audit, indent=2))
    print(json.dumps(audit, indent=2), flush=True)

if __name__ == "__main__": main()
