def detect_pressure(option_chain):

    call_change = sum([s.call_change_oi for s in option_chain.strikes])
    put_change = sum([s.put_change_oi for s in option_chain.strikes])

    return call_change, put_change
    