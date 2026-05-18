# Filter Reddit dataset

This folder contains `scripts/filter_reddit_comments.py` — a utility to filter a dataset so it only outputs the commenter's region and the comment text plus replies.

## Usage

```bash
# write to output file
python3 scripts/filter_reddit_comments.py grocery_delivery_reddit -o filtered.jsonl
```

## Output format

JSONL where each line is:
```json
{"region": "...", "comment": "...", "replies": ["..."]}
```
