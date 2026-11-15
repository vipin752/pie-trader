"""Money formatting: Indian digit grouping, true minus sign."""


def inr(v: float, sign=True, compact=False) -> str:
    s = "−" if v < 0 else ("+" if sign and v > 0 else "")
    a = abs(v)
    if compact and a >= 1e5:
        body = f"{a / 1e5:.2f}L"
    elif compact and a >= 1e4:
        body = f"{a / 1e3:.1f}k"
    else:
        n = f"{a:.0f}"
        if len(n) > 3:  # Indian grouping 12,34,567
            head, tail = n[:-3], n[-3:]
            parts = []
            while len(head) > 2:
                parts.insert(0, head[-2:]); head = head[:-2]
            if head:
                parts.insert(0, head)
            n = ",".join(parts + [tail])
        body = n
    return f"{s}₹{body}"
