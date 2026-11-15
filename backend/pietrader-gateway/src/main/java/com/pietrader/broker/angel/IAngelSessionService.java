package com.pietrader.broker.angel;

/** Angel One session management — login, JWT refresh, feed token. Impl: AngelSessionManagerImpl */
public interface IAngelSessionService {
    void   login(String totp);
    void   refreshJwt();
    void   startSessionMaintenance();
    String getJwtToken();
    String getFeedToken();
    String getClientId();
    String getApiKey();
    String getLocalIp();
    String getPublicIp();
    String getMacAddress();
    String getTotpSecret();
}
