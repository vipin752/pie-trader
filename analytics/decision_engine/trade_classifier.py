class TradeClassifier:

    def classify(self, data):
        if data.get("expiry_days", 0) <= 1:
            return "EXPIRY"
        return "INTRADAY"
    