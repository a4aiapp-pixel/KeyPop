#!/usr/bin/env python3
"""Compare *measured, matched* KeyPop and Gboard observations.

No API access or artificial Gboard scores. CSV input must be collected from
real devices/apps. Script uses only Python stdlib and stores no private text.
"""
import argparse
import csv
import json
import math
import random
import statistics
from collections import defaultdict

CATEGORIES = ("spelling", "next_word", "grammar", "personal")
FIELDS = ("keypop_top1", "gboard_top1", "keypop_top3", "gboard_top3",
          "keypop_false_edit", "gboard_false_edit")
MIN_CASES = 50
MIN_TOTAL = 400
MIN_APPS = 3


def ci(differences, samples=2000, seed=52):
    """95% paired bootstrap CI, over cases rather than over keyboards."""
    if not differences:
        return None
    rng = random.Random(seed)
    n = len(differences)
    draws = sorted(sum(differences[rng.randrange(n)]
                       for _ in range(n)) / n for _ in range(samples))
    return [round(draws[int(samples * .025)], 4),
            round(draws[min(samples - 1, int(samples * .975))], 4)]


def parse_bool(value):
    if value not in ("0", "1"):
        raise ValueError("Measurement flags must be 0 or 1; got " + repr(value))
    return int(value)


def evaluate(rows):
    seen = set()
    grouped = defaultdict(list)
    apps = set()
    for index, r in enumerate(rows, 2):
        for field in ("case_id", "category", "app") + FIELDS:
            if not r.get(field, "").strip():
                raise ValueError("Missing " + field + " on CSV row " + str(index))
        case = r["case_id"].strip()
        if case in seen:
            raise ValueError("Duplicate case ID " + case)
        seen.add(case)
        category = r["category"].strip()
        app = r["app"].strip()
        if category not in CATEGORIES + ("valid_text", "privacy"):
            raise ValueError("Unknown category " + category)
        values = {field: parse_bool(r[field].strip()) for field in FIELDS}
        for keyboard in ("keypop", "gboard"):
            if values[keyboard + "_top1"] > values[keyboard + "_top3"]:
                raise ValueError("top1 cannot exceed top3: " + case)
        grouped[category].append(values)
        apps.add(app)

    result = {"cases": len(seen), "apps": sorted(apps), "categories": {},
              "decision": "NOT VERIFIED", "reasons": []}
    for category, cases in sorted(grouped.items()):
        entry = {"n": len(cases)}
        for metric in ("top1", "top3", "false_edit"):
            k = [r["keypop_" + metric] for r in cases]
            g = [r["gboard_" + metric] for r in cases]
            delta = [(a - b) for a, b in zip(k, g)]
            entry[metric] = {
                "keypop": round(statistics.mean(k), 4),
                "gboard": round(statistics.mean(g), 4),
                "difference": round(statistics.mean(delta), 4),
                "paired_ci95": ci(delta)
            }
        result["categories"][category] = entry

    reasons = result["reasons"]
    if len(seen) < MIN_TOTAL:
        reasons.append("Fewer than " + str(MIN_TOTAL) + " paired observations")
    if len(apps) < MIN_APPS:
        reasons.append("Fewer than " + str(MIN_APPS) + " actual Android apps")
    for category in CATEGORIES:
        if len(grouped[category]) < MIN_CASES:
            reasons.append("Insufficient cases for " + category)
    if not grouped["privacy"]:
        reasons.append("No protected-field/privacy observations")

    if reasons:
        return result

    for category in CATEGORIES:
        entry = result["categories"][category]
        for metric in ("top1", "top3"):
            lower = entry[metric]["paired_ci95"][0]
            if lower < -0.03:
                reasons.append(category + " " + metric + " 95% CI below -3pp parity margin")
    for category in ("valid_text", "privacy"):
        if category in result["categories"]:
            rate = result["categories"][category]["false_edit"]
            if rate["keypop"] > rate["gboard"]:
                reasons.append("KeyPop made more unwanted edits in " + category)
            if category == "privacy" and rate["keypop"] > 0:
                reasons.append("KeyPop edited protected/private text")
    if reasons:
        result["decision"] = "BELOW PARITY OR SAFETY GATE"
        return result

    pooled = [r["keypop_top1"] - r["gboard_top1"]
              for category in CATEGORIES for r in grouped[category]]
    superior = ci(pooled)[0] > 0
    result["decision"] = ("SUPERIORITY EVIDENCE (TOP-1 ONLY)" if superior
                          else "PARITY NON-INFERIORITY EVIDENCE")
    result["overall_top1_paired_ci95"] = ci(pooled)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("csv_path", help="Real matched trials; no fabricated baselines")
    parser.add_argument("--json", action="store_true", help="Machine-readable report")
    args = parser.parse_args()
    with open(args.csv_path, encoding="utf-8-sig", newline="") as f:
        report = evaluate(list(csv.DictReader(f)))
    if args.json:
        print(json.dumps(report, indent=2, sort_keys=True))
    else:
        print("KEYPOP QUALITY:", report["decision"])
        print("Paired observations:", report["cases"])
        print("Actual apps:", ", ".join(report["apps"]))
        for name, category in report["categories"].items():
            top = category["top1"]
            print("%s: n=%d, top1 KeyPop=%.1f%% Gboard=%.1f%%, delta=%.1fpp, CI=%s" %
                  (name, category["n"], top["keypop"]*100,
                   top["gboard"]*100, top["difference"]*100,
                   top["paired_ci95"]))
        for reason in report["reasons"]:
            print("NOT ACCEPTED:", reason)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
