import json
from datetime import datetime
import os


class SnapshotRecorder:

    def __init__(self, path="data/snapshots"):
        self.path = path
        os.makedirs(path, exist_ok=True)

    def record(self, snapshot):

        ts = datetime.utcnow().strftime("%Y%m%d_%H%M%S")

        file = f"{self.path}/{ts}.json"

        with open(file, "w") as f:
            json.dump(snapshot, f)
            