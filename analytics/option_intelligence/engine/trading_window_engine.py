from datetime import datetime


class TradingWindowEngine:

    def calculate(self, ctx):

        time_str = ctx["market_context"]["session"]["time_ist"]
        t = datetime.strptime(time_str, "%H:%M:%S").time()

        if t < datetime.strptime("10:15:00", "%H:%M:%S").time():
            window = "OPENING"

        elif t < datetime.strptime("13:45:00", "%H:%M:%S").time():
            window = "THETA"

        else:
            window = "EXPANSION"

        return {
            "window": window
        }
        