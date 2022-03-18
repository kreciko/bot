package pl.kordek.forex.bot.api;

import pro.xstore.api.message.error.APICommunicationException;

public interface BrokerAPI {
    void login() throws APICommunicationException;
    void enter();
    void exit();
    void update();
    void getFreeMargin();
    void getOpenedPositions();
}
