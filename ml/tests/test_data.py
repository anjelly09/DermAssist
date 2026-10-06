import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from prepare_data import dominant_label, freeze_splits, CLASSES


def test_ambiguous_and_missing_labels_are_excluded():
    assert dominant_label("") is None
    assert dominant_label("{'Eczema': 0.5, 'Tinea': 0.5}") is None
    assert dominant_label("{'Eczema': 0.7, 'Tinea': 0.3}") == "Eczema"


def test_splits_are_deterministic_disjoint_and_keep_ood_out_of_training():
    rows = [dict(case_id=f"{label}:{i}", label=label) for label in CLASSES + ["Other"] for i in range(100)]
    first = freeze_splits([dict(r) for r in rows])
    second = freeze_splits([dict(r) for r in reversed(rows)])
    assert first == second
    assert len({r['case_id'] for r in first}) == len(rows)
    assert all(r['split'].startswith('ood_') for r in first if r['label'] == 'Other')
    assert all(not r['split'].startswith('ood_') for r in first if r['label'] in CLASSES)
