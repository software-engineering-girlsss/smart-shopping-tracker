#!/usr/bin/env python3
"""
Filter dataset to keep only commenter's region and the comment text with replies.
Produces JSONL output with fields: region, comment, replies (list).

Usage:
  python scripts/filter_reddit_comments.py input_path -o output.jsonl
"""

from __future__ import annotations
import argparse
import json
import csv
import sys
from typing import Any, Dict, Iterable, List, Optional

COMMON_REGION_KEYS = ["region", "commenter_region", "author_region", "user_region", "location"]
COMMON_COMMENT_KEYS = ["body", "comment", "text", "selftext"]
COMMON_REPLIES_KEYS = ["replies", "comments", "children"]


def iter_json_lines(fp) -> Iterable[Dict[str, Any]]:
    for raw in fp:
        raw = raw.strip()
        if not raw:
            continue
        try:
            yield json.loads(raw)
        except Exception:
            continue


def iter_json_array(fp) -> Iterable[Dict[str, Any]]:
    try:
        data = json.load(fp)
    except Exception:
        return
    if isinstance(data, list):
        for item in data:
            if isinstance(item, dict):
                yield item


def iter_csv(fp, delimiter=',') -> Iterable[Dict[str, Any]]:
    reader = csv.DictReader(fp, delimiter=delimiter)
    for row in reader:
        yield row


def extract_region(obj: Dict[str, Any]) -> Optional[str]:
    for k in COMMON_REGION_KEYS:
        v = obj.get(k)
        if v:
            return v
    return None


def extract_comment(obj: Dict[str, Any]) -> Optional[str]:
    for k in COMMON_COMMENT_KEYS:
        v = obj.get(k)
        if v:
            return v
    return None


def extract_replies(obj: Dict[str, Any]) -> List[str]:
    res: List[str] = []
    for k in COMMON_REPLIES_KEYS:
        if k not in obj:
            continue
        v = obj.get(k)
        if not v:
            continue
        if isinstance(v, list):
            for item in v:
                if isinstance(item, dict):
                    body = extract_comment(item)
                    if body:
                        res.append(body)
                    data = item.get('data') if isinstance(item, dict) else None
                    if data and isinstance(data, dict):
                        body = extract_comment(data)
                        if body:
                            res.append(body)
        elif isinstance(v, dict):
            # common Reddit structure
            data = v.get('data') or v
            if isinstance(data, dict):
                children = data.get('children')
                if isinstance(children, list):
                    for child in children:
                        if isinstance(child, dict):
                            cdata = child.get('data') or child
                            if isinstance(cdata, dict):
                                body = extract_comment(cdata)
                                if body:
                                    res.append(body)
                else:
                    body = extract_comment(data)
                    if body:
                        res.append(body)
        elif isinstance(v, str):
            s = v.strip()
            if (s.startswith('{') or s.startswith('[')):
                try:
                    parsed = json.loads(s)
                    if isinstance(parsed, dict):
                        res.extend(extract_replies(parsed))
                        maybe = extract_comment(parsed)
                        if maybe:
                            res.append(maybe)
                    elif isinstance(parsed, list):
                        for item in parsed:
                            if isinstance(item, dict):
                                body = extract_comment(item)
                                if body:
                                    res.append(body)
                except Exception:
                    res.append(s)
            else:
                res.append(s)
    return res


def normalize_record(obj: Dict[str, Any]) -> Optional[Dict[str, Any]]:
    region = extract_region(obj)
    comment = extract_comment(obj)
    replies = extract_replies(obj)
    if not (region or comment):
        return None
    return {"region": region, "comment": comment, "replies": replies}


def detect_and_iter(fp) -> Iterable[Dict[str, Any]]:
    # Try JSONL, then JSON array, then CSV
    pos = fp.tell()
    first = None
    for _ in range(50):
        line = fp.readline()
        if not line:
            break
        if line.strip():
            first = line.strip()
            break
    fp.seek(pos)

    if first is None:
        return []

    if first.startswith('{'):
        try:
            # check if first line is valid JSON
            json.loads(first)
            return iter_json_lines(fp)
        except Exception:
            try:
                fp.seek(pos)
                data = json.load(fp)
                if isinstance(data, list):
                    return (item for item in data if isinstance(item, dict))
                elif isinstance(data, dict):
                    return [data]
            except Exception:
                fp.seek(pos)
                return iter_json_lines(fp)
    elif first.startswith('['):
        return iter_json_array(fp)
    else:
        if '\t' in first:
            return iter_csv(fp, delimiter='\t')
        elif ',' in first:
            return iter_csv(fp, delimiter=',')
        else:
            return iter_json_lines(fp)


def main():
    p = argparse.ArgumentParser(description="Filter dataset")
    p.add_argument('input', help='Input file path')
    p.add_argument('-o', '--output', help='Output JSONL file path', default=None)
    args = p.parse_args()

    with open(args.input, 'r', encoding='utf-8') as fp:
        reader = detect_and_iter(fp)
        
        # Open output file
        out_fp = open(args.output, 'w', encoding='utf-8') if args.output else sys.stdout
        
        try:
            count_in = 0
            count_out = 0
            for rec in reader:
                count_in += 1
                if not isinstance(rec, dict):
                    continue
                norm = normalize_record(rec)
                if not norm:
                    continue
                out_fp.write(json.dumps(norm, ensure_ascii=False) + "\n")
                count_out += 1
        finally:
            if args.output:
                out_fp.close()

    if args.output:
        # Write stats to stderr 
        sys.stderr.write(f"Processed: {count_in}, Filtered: {count_out}\\n")

if __name__ == '__main__':
    main()
