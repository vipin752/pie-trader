import math
from scipy.stats import norm

def calculate_bs_metrics(S, K, T, r, sigma, option_type="call"):
    """
    S: Spot Price (e.g., 23450)
    K: Strike Price (e.g., 23500)
    T: Time to expiry in years (Days to expiry / 365)
    r: Risk-free rate (typically 0.07 to 0.10 for India)
    sigma: Implied Volatility (extracted from NSE JSON 'impliedVolatility', divide by 100)
    """
    # Safeguards for expiry day (T=0) or zero IV
    T = max(T, 0.0001)
    sigma = max(sigma, 0.0001)

    d1 = (math.log(S / K) + (r + (sigma ** 2) / 2) * T) / (sigma * math.sqrt(T))
    
    # Calculate standard normal probability density function for Gamma
    pdf_d1 = norm.pdf(d1)
    
    # Gamma is identical for both Calls and Puts
    gamma = pdf_d1 / (S * sigma * math.sqrt(T))
    
    # Delta
    if option_type == "call":
        delta = norm.cdf(d1)
    else:
        delta = norm.cdf(d1) - 1
        
    return {"gamma": gamma, "delta": delta}
    