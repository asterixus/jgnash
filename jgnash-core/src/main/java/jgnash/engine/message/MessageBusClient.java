/*
 * jGnash, a personal finance application
 * Copyright (C) 2001-2013 Craig Cavanaugh
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package jgnash.engine.message;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.CompactWriter;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.BufType;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundMessageHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import jgnash.engine.Account;
import jgnash.engine.CommodityNode;
import jgnash.engine.DataStoreType;
import jgnash.engine.Engine;
import jgnash.engine.EngineFactory;
import jgnash.engine.ExchangeRate;
import jgnash.engine.Transaction;
import jgnash.engine.budget.Budget;
import jgnash.engine.jpa.JpaNetworkServer;
import jgnash.engine.recurring.Reminder;
import jgnash.net.ConnectionFactory;

import java.io.CharArrayWriter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Message bus client for remote connections
 *
 * @author Craig Cavanaugh
 */
public class MessageBusClient {
    private String host = "localhost";

    private int port = 0;

    private static final Logger logger = Logger.getLogger(MessageBusClient.class.getName());

    private final XStream xstream;

    private String dataBasePath;

    private DataStoreType dataBaseType;

    private EncryptionFilter filter = null;

    private Bootstrap bootstrap;

    private Channel channel;

    private ChannelFuture lastWriteFuture = null;

    /**
     * Lookup map for remote states
     */
    private Map<String, LockState> remoteLockStates = new ConcurrentHashMap<>();

    public MessageBusClient(final String host, final int port) {
        this.host = host;
        this.port = port;

        xstream = XStreamFactory.getInstance();
    }

    public String getDataBasePath() {
        return dataBasePath;
    }

    public DataStoreType getDataStoreType() {
        return dataBaseType;
    }

    private static int getConnectionTimeout() {
        return ConnectionFactory.getConnectionTimeout();
    }

    public boolean connectToServer(final char[] password) {
        boolean result = false;

        boolean useSSL = Boolean.parseBoolean(System.getProperties().getProperty("ssl"));

        // If a user and password has been specified, enable an encryption filter
        if (useSSL && password != null && password.length > 0) {
            filter = new EncryptionFilter(password);
        }

        bootstrap = new Bootstrap();

        bootstrap.group(new NioEventLoopGroup())
                .channel(NioSocketChannel.class)
                .handler(new MessageBusClientInitializer())
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, getConnectionTimeout() * 1000);

        try {
            // Start the connection attempt.
            channel = bootstrap.connect(host, port).sync().channel();

            result = true;
            logger.info("Connected to remote message server");
        } catch (final InterruptedException e) {
            logger.log(Level.SEVERE, "Failed to connect to remote message bus", e);
            disconnectFromServer();
        }

        return result;
    }

    private class MessageBusClientInitializer extends ChannelInitializer<SocketChannel> {
        private final StringDecoder DECODER = new StringDecoder();
        private final StringEncoder ENCODER = new StringEncoder(BufType.BYTE);
        private final MessageBusClientHandler CLIENT_HANDLER = new MessageBusClientHandler();

        @Override
        public void initChannel(final SocketChannel ch) throws Exception {
            ChannelPipeline pipeline = ch.pipeline();

            // Add the text line codec combination first,
            pipeline.addLast("framer", new DelimiterBasedFrameDecoder(8192, true, Delimiters.lineDelimiter()));
            pipeline.addLast("decoder", DECODER);
            pipeline.addLast("encoder", ENCODER);

            // and then business logic.
            pipeline.addLast("handler", CLIENT_HANDLER);
        }
    }

    /**
     * Handles a client-side channel.
     */
    @ChannelHandler.Sharable
    private class MessageBusClientHandler extends ChannelInboundMessageHandlerAdapter<String> {

        private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

        /**
         * Time in milliseconds to force an update latency to ensure client container is current before processing the
         * message
         */
        private static final int FORCED_LATENCY = 2000;

        private String decrypt(final Object object) {
            String plainMessage;

            if (filter != null) {
                plainMessage = filter.decrypt(object.toString());
            } else {
                plainMessage = object.toString();
            }

            return plainMessage;
        }

        @Override
        //TODO offload processing of the messages to an execution pool so the server is not blocked
        public void messageReceived(final ChannelHandlerContext ctx, final String msg) throws Exception {
            String plainMessage = decrypt(msg);

            logger.log(Level.INFO, "messageReceived: {0}", plainMessage);

            if (plainMessage.startsWith("<Message")) {
                final Message message = (Message) xstream.fromXML(plainMessage);

                // ignore our own messages
                if (!EngineFactory.getEngine(EngineFactory.DEFAULT).getUuid().equals(message.getSource())) {

                    // force latency and process after a fixed delay
                    scheduler.schedule(new Runnable() {

                        @Override
                        public void run() {
                            processRemoteMessage(message);
                        }
                    }, FORCED_LATENCY, TimeUnit.MILLISECONDS);
                }
            } else if (plainMessage.startsWith("<LockState>")) {  // process immediately
                updateLockState(plainMessage);
            } else if (plainMessage.startsWith(MessageBusServer.PATH_PREFIX)) {
                dataBasePath = plainMessage.substring(MessageBusServer.PATH_PREFIX.length());
                logger.log(Level.INFO, "Remote data path is: {0}", dataBasePath);
            } else if (plainMessage.startsWith(MessageBusServer.DATA_STORE_TYPE_PREFIX)) {
                dataBaseType = DataStoreType.valueOf(plainMessage.substring(MessageBusServer.DATA_STORE_TYPE_PREFIX.length()));
                logger.log(Level.INFO, "Remote dataBaseType type is: {0}", dataBaseType.name());
            } else if (plainMessage.startsWith(EncryptionFilter.DECRYPTION_ERROR_TAG)) {    // decryption has failed, shut down the engine
                logger.log(Level.SEVERE, "Unable to decrypt the remote message");
            } else if (plainMessage.startsWith(JpaNetworkServer.STOP_SERVER_MESSAGE)) {
                logger.info("Server is shutting down");
                EngineFactory.closeEngine(EngineFactory.DEFAULT);
            } else {
                logger.log(Level.SEVERE, "Unknown message: {0}", plainMessage);
            }
        }

        @Override
        public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) throws Exception {
            logger.log(Level.WARNING, "Unexpected exception from downstream.", cause);
            ctx.close();
        }
    }

    private void updateLockState(final String message) {
        final LockState newLockState = (LockState) xstream.fromXML(message);

        if (!remoteLockStates.containsKey(newLockState.getLockId())) {
             remoteLockStates.put(newLockState.getLockId(), newLockState);
        } else { // update the existing lock state
            LockState lockState = remoteLockStates.get(newLockState.getLockId());
            lockState.setLocked(newLockState.isLocked());
        }
    }

    public void disconnectFromServer() {

        // Wait until all messages are flushed before closing the channel.
        if (lastWriteFuture != null) {
            try {
                lastWriteFuture.sync();
            } catch (InterruptedException e) {
                logger.log(Level.SEVERE, e.getLocalizedMessage(), e);
            }
        }

        try {
            channel.close().sync();
        } catch (InterruptedException e) {
            logger.log(Level.SEVERE, e.getLocalizedMessage(), e);
        }
        bootstrap.shutdown();

        channel = null;
        lastWriteFuture = null;
        bootstrap = null;
    }

    public synchronized void sendRemoteMessage(final Message message) {
        CharArrayWriter writer = new CharArrayWriter();
        xstream.marshal(message, new CompactWriter(writer));

        sendRemoteMessage(writer.toString());

        logger.log(Level.INFO, "sent: {0}", writer.toString());
    }

    public void sendRemoteShutdownRequest() {
        sendRemoteMessage(JpaNetworkServer.STOP_SERVER_MESSAGE);
    }

    private synchronized void sendRemoteMessage(final String message) {
        if (filter != null) {
            lastWriteFuture = channel.write(filter.encrypt(message) + MessageBusServer.EOL_DELIMITER);
        } else {
            lastWriteFuture = channel.write(message + MessageBusServer.EOL_DELIMITER);
        }
    }

    /**
     * Takes a remote message and forces remote updates before sending the message to the MessageBus to notify UI
     * components of changes.
     *
     * @param message Message to process and send
     */
    private synchronized static void processRemoteMessage(final Message message) {
        logger.info("processing a remote message");

        Engine engine = EngineFactory.getEngine(EngineFactory.DEFAULT);

        if (message.getChannel() == MessageChannel.ACCOUNT) {
            final Account account = (Account) message.getObject(MessageProperty.ACCOUNT);
            switch (message.getEvent()) {
                case ACCOUNT_ADD:
                case ACCOUNT_REMOVE:
                    engine.refreshAccount(account);
                    message.setObject(MessageProperty.ACCOUNT, engine.getAccountByUuid(account.getUuid()));
                    engine.refreshAccount(account.getParent());
                    break;
                case ACCOUNT_MODIFY:
                case ACCOUNT_SECURITY_ADD:
                case ACCOUNT_SECURITY_REMOVE:
                case ACCOUNT_VISIBILITY_CHANGE:
                    engine.refreshAccount(account);
                    message.setObject(MessageProperty.ACCOUNT, engine.getAccountByUuid(account.getUuid()));
                    break;
                default:
                    break;
            }
        }

        if (message.getChannel() == MessageChannel.BUDGET) {
            final Budget budget = (Budget) message.getObject(MessageProperty.BUDGET);
            switch (message.getEvent()) {
                case BUDGET_ADD:
                case BUDGET_UPDATE:
                case BUDGET_REMOVE:
                case BUDGET_GOAL_UPDATE:
                    engine.refreshBudget(budget);
                    message.setObject(MessageProperty.BUDGET, engine.getBudgetByUuid(budget.getUuid()));
                    break;
                default:
                    break;
            }
        }

        if (message.getChannel() == MessageChannel.COMMODITY) {
            switch (message.getEvent()) {
                case CURRENCY_MODIFY:
                    final CommodityNode currency = (CommodityNode) message.getObject(MessageProperty.COMMODITY);
                    engine.refreshCommodity(currency);
                    message.setObject(MessageProperty.COMMODITY, engine.getCurrencyNodeByUuid(currency.getUuid()));
                    break;
                case SECURITY_MODIFY:
                case SECURITY_HISTORY_ADD:
                case SECURITY_HISTORY_REMOVE:
                    final CommodityNode node = (CommodityNode) message.getObject(MessageProperty.COMMODITY);
                    engine.refreshCommodity(node);
                    message.setObject(MessageProperty.COMMODITY, engine.getSecurityNodeByUuid(node.getUuid()));
                    break;
                case EXCHANGE_RATE_ADD:
                case EXCHANGE_RATE_REMOVE:
                    final ExchangeRate rate = (ExchangeRate) message.getObject(MessageProperty.EXCHANGE_RATE);
                    engine.refreshExchangeRate(rate);
                    message.setObject(MessageProperty.EXCHANGE_RATE, engine.getExchangeRateByUuid(rate.getUuid()));
                    break;
                default:
                    break;
            }
        }

        if (message.getChannel() == MessageChannel.REMINDER) {
            switch (message.getEvent()) {
                case REMINDER_ADD:
                case REMINDER_REMOVE:
                    final Reminder reminder = (Reminder) message.getObject(MessageProperty.REMINDER);
                    engine.refreshReminder(reminder);
                    message.setObject(MessageProperty.REMINDER, engine.getReminderByUuid(reminder.getUuid()));
                    break;
                default:
                    break;

            }
        }

        if (message.getChannel() == MessageChannel.TRANSACTION) {
            switch (message.getEvent()) {
                case TRANSACTION_ADD:
                case TRANSACTION_REMOVE:
                    final Account account = (Account) message.getObject(MessageProperty.ACCOUNT);
                    engine.refreshAccount(account);
                    message.setObject(MessageProperty.ACCOUNT, engine.getAccountByUuid(account.getUuid()));

                    final Transaction transaction = (Transaction) message.getObject(MessageProperty.TRANSACTION);
                    engine.refreshTransaction(transaction);
                    message.setObject(MessageProperty.TRANSACTION, engine.getTransactionByUuid(transaction.getUuid()));
                    break;
                default:
                    break;
            }
        }

        /* Flag the message as remote */
        message.setRemote(true);

        logger.info("fire remote message");
        MessageBus.getInstance().fireEvent(message);
    }
}