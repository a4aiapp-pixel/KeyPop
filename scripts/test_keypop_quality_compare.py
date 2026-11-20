"""Unit tests only: synthetic rows here MUST NOT be reported as Gboard evidence."""
import unittest
from keypop_quality_compare import evaluate, ci


def fake_row(i, category, app):
    return {
        "case_id": str(i), "category": category, "app": app,
        "keypop_top1": "1", "gboard_top1": "1",
        "keypop_top3": "1", "gboard_top3": "1",
        "keypop_false_edit": "0", "gboard_false_edit": "0"
    }


def fake_complete():
    rows = []
    for category, n in (("spelling", 60), ("next_word", 60),
                        ("grammar", 60), ("personal", 60),
                        ("valid_text", 80), ("privacy", 80)):
        for _ in range(n):
            i = len(rows)
            rows.append(fake_row(i, category, ("Chrome", "WhatsApp", "Gmail")[i % 3]))
    return rows


class CompareQualityTest(unittest.TestCase):
    def test_few_cases_are_never_called_parity(self):
        result = evaluate([fake_row(0, "spelling", "Chrome")])
        self.assertEqual(result["decision"], "NOT VERIFIED")
        self.assertTrue(result["reasons"])

    def test_complete_equivalent_measurements_do_not_claim_superiority(self):
        result = evaluate(fake_complete())
        self.assertEqual(result["decision"], "PARITY NON-INFERIORITY EVIDENCE")

    def test_material_spelling_regression_fails_the_gate(self):
        rows = fake_complete()
        for row in rows:
            if row["category"] == "spelling":
                row["keypop_top1"] = "0"
                row["keypop_top3"] = "0"
        result = evaluate(rows)
        self.assertEqual(result["decision"], "BELOW PARITY OR SAFETY GATE")
        self.assertTrue(any("spelling" in reason for reason in result["reasons"]))

    def test_privacy_errors_fail_gate(self):
        rows = fake_complete()
        rows[-1]["keypop_false_edit"] = "1"
        result = evaluate(rows)
        self.assertEqual(result["decision"], "BELOW PARITY OR SAFETY GATE")
        self.assertTrue(any("privacy" in reason for reason in result["reasons"]))

    def test_invalid_or_duplicated_rows_rejected(self):
        rows = fake_complete()
        rows[-1]["case_id"] = rows[0]["case_id"]
        with self.assertRaises(ValueError):
            evaluate(rows)
        rows = fake_complete()
        rows[0]["gboard_top1"] = "x"
        with self.assertRaises(ValueError):
            evaluate(rows)

    def test_paired_bootstrap_is_deterministic(self):
        a = ci([0, 1, -1, 1, 0, -1, 0, 1])
        self.assertEqual(a, ci([0, 1, -1, 1, 0, -1, 0, 1]))
        self.assertEqual(len(a), 2)


if __name__ == "__main__":
    unittest.main()
