from utils.email_sender import EmailSender


class AlertEngine:

    def __init__(self):
        self.email_sender = EmailSender()

    def process(self, trade):

        if not trade or trade.get("strategy") != "SNIPER_BUY":
            return None

        subject = "🚨 SNIPER TRADE ALERT"

        body = f"""
🚨 SNIPER TRADE ALERT

Option: {trade['option']}
Entry: ₹{trade['entry_price']}
Target: ₹{trade['target_price']}
Stop Loss: ₹{trade['sl_price']}

Expected Move: {trade['expected_move']}
Hold Time: {trade['hold_time']}

⚡ Execute only after breakout confirmation
"""

        # 🔥 SEND EMAIL
        self.email_sender.send(subject, body)

        return body
        