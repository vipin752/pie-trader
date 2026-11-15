import json
import os


class BacktestEngine:

    def __init__(self, path="data/snapshots"):
        self.path = path

    def load_snapshots(self):

        files = sorted(os.listdir(self.path))

        data = []

        for f in files:

            with open(f"{self.path}/{f}") as file:
                data.append(json.load(file))

        return data


    def test_pressure_signal(self):

        data = self.load_snapshots()

        wins = 0
        losses = 0

        for i in range(len(data)-1):

            now = data[i]
            nxt = data[i+1]

            score = now["pressure"]["market_pressure_score"]

            move = nxt["spot"] - now["spot"]

            if score > 50 and move > 0:
                wins += 1

            elif score < -50 and move < 0:
                wins += 1

            else:
                losses += 1

        total = wins + losses

        if total == 0:
            return None

        accuracy = wins / total

        return {
            "wins": wins,
            "losses": losses,
            "accuracy": accuracy
        }
        
        