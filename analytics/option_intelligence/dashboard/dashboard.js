async function loadData(){

let res = await fetch("http://127.0.0.1:8000/option-summary?symbol=NIFTY")

let data = await res.json()

let decision = data.trade_decision

document.getElementById("state").innerHTML =
"Market State: " + decision.market_state

document.getElementById("bias").innerHTML =
"Bias: " + decision.bias

document.getElementById("strategy").innerHTML =
"Strategy: " + decision.strategy

document.getElementById("gamma").innerHTML =
"Gamma Wall: " + data.gamma.call_gamma_wall +
" / " + data.gamma.put_gamma_wall

document.getElementById("liquidity").innerHTML =
"Support: " + data.liquidity.support +
" Resistance: " + data.liquidity.resistance

document.getElementById("dealer").innerHTML =
"Dealer Regime: " + data.dealer_hedging.dealer_regime

}

loadData()

setInterval(loadData,15000)
