class OILadderEngine:

    def build(self, option_chain):

        call_wall = max(option_chain.strikes, key=lambda s: s.call_oi)
        put_wall = max(option_chain.strikes, key=lambda s: s.put_oi)

        return {
            "call_wall": call_wall.strike,
            "put_wall": put_wall.strike
        }
        