# -*- coding: utf-8 -*-
"""
Generates/refreshes the golden files under tests/baseline/ by running the
CURRENT legacy Gerber/Excellon parsers against the fixtures in
tests/gerber_files/ and tests/excellon_files/.

Run once to create the initial baseline (commit the resulting JSON files).
Re-run and diff (`git diff tests/baseline/`) only after a deliberate change to
parser behavior - a diff here means the legacy parser's observable output
changed, which is exactly what test_gerber_characterization.py and
test_excellon_characterization.py are meant to catch if it happens by accident.

Usage (from the repo root, with the project's deps installed):
    python tests/generate_baseline.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from tests.parser_baseline import (
    GERBER_FIXTURES,
    EXCELLON_FIXTURES,
    parse_gerber,
    parse_excellon,
    summarize_gerber,
    summarize_excellon,
    write_baseline,
)


def main():
    for fixture in GERBER_FIXTURES:
        gerber = parse_gerber(fixture)
        summary = summarize_gerber(gerber)
        write_baseline('gerber', fixture, summary)
        print('wrote baseline for', fixture)

    for fixture in EXCELLON_FIXTURES:
        excellon = parse_excellon(fixture)
        summary = summarize_excellon(excellon)
        write_baseline('excellon', fixture, summary)
        print('wrote baseline for', fixture)


if __name__ == '__main__':
    main()
