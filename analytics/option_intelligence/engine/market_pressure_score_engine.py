class MarketPressureScoreEngine:

    def calculate(self, ctx):

        # =====================================================
        # SAFE EXTRACTION (NO CRASH)
        # =====================================================

        dealer = ctx.get("dealer_positioning", {}).get("dealer_inventory_model", {})
        prob = ctx.get("market_structure", {}).get("probability_model", {})
        pressure_data = ctx.get("market_structure", {}).get("pressure", {})
        vol_ctx = ctx.get("volatility_context", {})
        pcr_data = vol_ctx.get("pcr", {})
        vol = vol_ctx.get("volatility_engine", {})
        flow = ctx.get("institutional_flow", {}).get("smart_money_flow_engine", {})
        liquidity = ctx.get("liquidity_map", {}).get("support_resistance", {})
        market_ctx = ctx.get("market_context", {})

        # =====================================================
        # INITIAL SCORE
        # =====================================================

        score = 0

        # =====================================================
        # 1. GAMMA (VERY IMPORTANT)
        # =====================================================

        gamma_type = dealer.get("dealer_inventory", "")

        if gamma_type == "LONG_GAMMA":
            score -= 15  # suppress move
        elif gamma_type == "SHORT_GAMMA":
            score += 20  # expansion

        # =====================================================
        # 2. PCR (SENTIMENT)
        # =====================================================

        pcr = pcr_data.get("pcr", 1)

        if pcr > 1.2:
            score += 15
        elif pcr < 0.8:
            score -= 15

        # =====================================================
        # 3. PROBABILITY MODEL (DIRECTION)
        # =====================================================

        direction = prob.get("direction", "")

        if direction == "STRONG_UPSIDE":
            score += 30
        elif direction == "STRONG_DOWNSIDE":
            score -= 30

        # =====================================================
        # 4. PRESSURE ENGINE (NORMALIZED)
        # =====================================================

        pressure_val = pressure_data.get("pressure", 0)
        score += pressure_val * 0.4  # scale factor

        # =====================================================
        # 5. SMART MONEY FLOW
        # =====================================================

        flow_bias = flow.get("smart_money_bias", "")

        if flow_bias == "BULLISH":
            score += 10
        elif flow_bias == "BEARISH":
            score -= 10

        # =====================================================
        # 6. VOLATILITY (EXPANSION VS THETA)
        # =====================================================

        iv = vol.get("atm_iv_pct", 15)

        if iv > 18:
            score += 10  # expansion possible
        elif iv < 12:
            score -= 10  # dead market

        # =====================================================
        # 7. LIQUIDITY POSITION
        # =====================================================

        spot = market_ctx.get("spot", 0)
        support = liquidity.get("support", 0)
        resistance = liquidity.get("resistance", 0)

        if resistance and spot > resistance:
            score += 10
        elif support and spot < support:
            score -= 10

        # =====================================================
        # 8. SESSION FILTER (TIME BASED EDGE)
        # =====================================================

        session = market_ctx.get("session", {}).get("session", "")

        if session == "OPENING":
            score *= 0.7  # reduce reliability
        elif session == "THETA":
            score *= 0.6  # avoid buying
        elif session == "EXPANSION":
            score *= 1.2  # boost signals

        # =====================================================
        # NORMALIZE SCORE
        # =====================================================

        score = max(min(score, 100), -100)
        score = round(score)

        # =====================================================
        # LABEL
        # =====================================================

        if score >= 60:
            label = "STRONG BUY"
        elif score >= 20:
            label = "BUY"
        elif score <= -60:
            label = "STRONG SELL"
        elif score <= -20:
            label = "SELL"
        else:
            label = "NEUTRAL"

        # =====================================================
        # CONFIDENCE
        # =====================================================

        if abs(score) >= 70:
            confidence = "VERY_HIGH"
        elif abs(score) >= 50:
            confidence = "HIGH"
        elif abs(score) >= 30:
            confidence = "MEDIUM"
        else:
            confidence = "LOW"

        # =====================================================
        # FINAL TRADING DECISION
        # =====================================================

        action = "NO_TRADE"
        strategy = "WAIT"
        message = "No clear edge"

        # -----------------------------------
        # STRONG BUY
        # -----------------------------------

        if score >= 60:
            action = "BUY CE"
            strategy = "OPTION_BUY"
            message = "Strong bullish momentum → Buy Call Option"

        # -----------------------------------
        # STRONG SELL
        # -----------------------------------

        elif score <= -60:
            action = "BUY PE"
            strategy = "OPTION_BUY"
            message = "Strong bearish momentum → Buy Put Option"

        # -----------------------------------
        # RANGE (SELL PREMIUM)
        # -----------------------------------

        elif -20 <= score <= 20 and gamma_type == "LONG_GAMMA":
            action = "SELL CE/PE"
            strategy = "OPTION_SELL"
            message = "Range market → Sell premium (theta decay)"

        # -----------------------------------
        # MODERATE BUY
        # -----------------------------------

        elif 20 < score < 60:
            action = "BUY CE (LOW CONFIDENCE)"
            strategy = "OPTION_BUY"
            message = "Moderate bullish bias"

        # -----------------------------------
        # MODERATE SELL
        # -----------------------------------

        elif -60 < score < -20:
            action = "BUY PE (LOW CONFIDENCE)"
            strategy = "OPTION_BUY"
            message = "Moderate bearish bias"

        # =====================================================
        # FINAL RETURN
        # =====================================================

        return {
            "score": score,
            "label": label,
            "confidence": confidence,

            # 🔥 ACTIONABLE OUTPUT
            "action": action,
            "strategy": strategy,
            "message": message,

            # 🔍 DEBUG (VERY USEFUL)
            "components": {
                "gamma": gamma_type,
                "pcr": pcr,
                "probability_direction": direction,
                "pressure": pressure_val,
                "flow": flow_bias,
                "iv": iv,
                "session": session
            }
        }
        