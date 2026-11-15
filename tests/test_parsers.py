import openpyxl

from pie_trader.parsers import load_trades


def test_mirae_xlsx(tmp_path):
    wb = openpyxl.Workbook(); ws = wb.active
    ws.append(["Broker header"]); ws.append([])
    ws.append(["Trade Date", "Exchange", "Buy / Sell", "Scrip / Contract", "Qty", "Price", "Trade Id"])
    ws.append(["13-11-2026", "NSEFO", "Sell", "NIFTY 17 NOV 2026 25350 CE", "65", "70.00", "200"])
    ws.append(["13-11-2026", "NSEFO", "Buy", "NIFTY 17 NOV 2026 25350 CE", "65", "60.00", "100"])
    ws.append(["NOTE: footer"])
    p = tmp_path / "t.xlsx"; wb.save(p)
    tr = load_trades(p)
    assert [t.side for t in tr] == ["Buy", "Sell"]  # ordered by trade id
    assert tr[0].qty == 65 and tr[1].price == 70.0


def test_csv_with_time(tmp_path):
    p = tmp_path / "t.csv"
    p.write_text("date,time,exchange,side,symbol,qty,price\n"
                 "2026-11-13,10:05:00,NSEFO,sell,NIFTY 17 NOV 2026 25350 CE,65,70\n"
                 "2026-11-13,09:20:00,NSEFO,buy,NIFTY 17 NOV 2026 25350 CE,65,60\n")
    tr = load_trades(p)
    assert [t.side for t in tr] == ["Buy", "Sell"]
