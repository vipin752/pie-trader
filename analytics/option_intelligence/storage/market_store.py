from collections import deque


class MarketStore:

    def __init__(self):

        self.history = deque(maxlen=5000)

    def add_snapshot(self, data):

        self.history.append(data)

    def get_history(self):

        return list(self.history)
        