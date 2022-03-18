package pl.kordek.forex.bot.api;

import pl.kordek.forex.bot.constants.Configuration;
import pl.kordek.forex.bot.exceptions.XTBCommunicationException;
import pro.xstore.api.message.command.APICommandFactory;
import pro.xstore.api.message.error.APICommandConstructionException;
import pro.xstore.api.message.error.APICommunicationException;
import pro.xstore.api.message.error.APIReplyParseException;
import pro.xstore.api.message.response.APIErrorResponse;
import pro.xstore.api.message.response.LoginResponse;
import pro.xstore.api.message.response.SymbolResponse;
import pro.xstore.api.sync.Credentials;
import pro.xstore.api.sync.SyncAPIConnector;
import pl.kordek.forex.bot.exceptions.APICommunicationException;

import java.io.IOException;

public class XTBAPIImpl implements BrokerAPI{

    private SyncAPIConnector connector = null;
    private SymbolResponse sr = null;

    public XTBAPIImpl() throws APICommunicationException {
        try {
            this.connector = new SyncAPIConnector(Configuration.server);
        } catch (IOException e) {
            throw new APICommunicationException("Can't initialize connection to the XTB broker");
        }
    }

    public void initSymbolOperations(String symbol) throws APICommunicationException {
        try {
            this.sr  = APICommandFactory.executeSymbolCommand(connector, symbol);
        } catch (APICommandConstructionException | APIReplyParseException | APIErrorResponse | APICommunicationException e) {
            throw new APICommunicationException("Can't get symbol response from XTB broker");
        }
    }

    @Override
    public void login() throws APICommunicationException {
        try {
            LoginResponse loginResponse = APICommandFactory.executeLoginCommand(connector,
                    new Credentials(Configuration.username, Configuration.password));
            if (loginResponse != null && !loginResponse.getStatus()) {
                throw new APICommunicationException("Failed to login");
            }
        } catch (APICommandConstructionException | APIReplyParseException | APICommunicationException |
                APIErrorResponse | IOException e1) {
            throw new APICommunicationException("Failed to login");
        }
    }

    @Override
    public void enter() {

    }

    @Override
    public void exit() {

    }

    @Override
    public void update() {

    }

    @Override
    public void getFreeMargin() {

    }

    @Override
    public void getOpenedPositions() {

    }
}
