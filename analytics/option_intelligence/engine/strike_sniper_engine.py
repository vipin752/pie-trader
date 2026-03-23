class StrikeSniperEngine:

    def select(self, rows, spot, direction):

        best_option = None
        best_score = -1

        for r in rows:

            strike = r.get("strike")
            ce = r.get("CE", {})
            pe = r.get("PE", {})

            if direction == "UP" and ce:
                price = ce.get("lastPrice", 0)
                oi = ce.get("openInterest", 0)

            elif direction == "DOWN" and pe:
                price = pe.get("lastPrice", 0)
                oi = pe.get("openInterest", 0)

            else:
                continue

            # 🎯 Your logic: cheap + liquid + near ATM
            if 10 <= price <= 120:
                distance = abs(strike - spot)
                score = oi / (distance + 1)

                if score > best_score:
                    best_score = score
                    best_option = {
                        "strike": strike,
                        "price": price,
                        "oi": oi
                    }

        return best_option
        