def find_walls(option_chain):

    strikes = option_chain.strikes

    call_wall = max(strikes, key=lambda s: s.call_oi)
    put_wall = max(strikes, key=lambda s: s.put_oi)

    return {
        "call_wall": call_wall.strike,
        "put_wall": put_wall.strike
    }
    