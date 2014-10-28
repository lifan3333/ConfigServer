package com.cmbc.configserver.core.notify;

import com.cmbc.configserver.common.RemotingSerializable;
import com.cmbc.configserver.common.ThreadFactoryImpl;
import com.cmbc.configserver.common.protocol.RequestCode;
import com.cmbc.configserver.core.event.Event;
import com.cmbc.configserver.core.event.EventType;
import com.cmbc.configserver.core.server.ConfigNettyServer;
import com.cmbc.configserver.core.server.ConfigServerController;
import com.cmbc.configserver.core.storage.ConfigStorage;
import com.cmbc.configserver.domain.Configuration;
import com.cmbc.configserver.domain.Notify;
import com.cmbc.configserver.remoting.protocol.RemotingCommand;
import com.cmbc.configserver.utils.ConfigServerLogger;
import com.cmbc.configserver.utils.PathUtils;
import io.netty.channel.Channel;

import java.util.List;
import java.util.concurrent.*;

/**
 * the notify service
 * Created by tongchuan.lin<linckham@gmail.com><br/>
 *
 * @author tongchuan.lin<linckham@gmail.com>.
 *         Date: 2014/10/24
 *         Time: 15:18
 */
public class NotifyService {
    private static final int MAX_EVENT_ITEM = 1024 * 1024;
    private static final int POLL_TIMEOUT = 1*1000;
    private static final int NOTIFY_TIMEOUT = 2*1000;
    private static final int MAX_DELAY_TIME = 3 * 60 * 1000;
    private LinkedBlockingQueue<Event> eventQueue = new LinkedBlockingQueue<Event>(MAX_EVENT_ITEM);
    private volatile boolean stop = true;
    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private ThreadPoolExecutor notifyExecutor;
    private ConfigStorage configStorage;
    private ConfigNettyServer configNettyServer;

    public NotifyService(ConfigStorage configStorage,ConfigNettyServer nettyServer){
        this.configStorage = configStorage;
        this.configNettyServer =  nettyServer;
    }

    public void setConfigStorage(ConfigStorage configStorage) {
        this.configStorage = configStorage;
    }

    public ConfigStorage getConfigStorage() {
        return this.configStorage;
    }

    public ConfigNettyServer getConfigNettyServer() {
        return configNettyServer;
    }

    public void setConfigNettyServer(ConfigNettyServer configNettyServer) {
        this.configNettyServer = configNettyServer;
    }

    private void initialize() {
        this.scheduler.execute(new EventDispatcher());
        this.notifyExecutor = new ThreadPoolExecutor(16, 48, 60 * 1000, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(MAX_EVENT_ITEM), new ThreadFactoryImpl("config-event-thread-"));
    }

    public boolean start() {
        this.stop = false;
        initialize();
        return true;
    }

    public void stop() {
        this.stop = true;
        this.scheduler.shutdown();
        ConfigServerLogger.info("ConfigServer's notifyService has been stopped!");
    }

    /**
     * publish the event to queue
     *
     * @param event
     */
    public void publish(Event event) {
        this.eventQueue.offer(event);
    }

    private void onConfigChanged(Event event) {
        this.notifyExecutor.execute(new NotifyWorker(event));
    }

    private void onSubscribed(Event event) {
        this.notifyExecutor.execute(new NotifyWorker(event));
    }

    /**
     * the worker that using to notify the configuration  when the specified event  happened
     */
    class NotifyWorker implements Runnable {
        private Event event;

        public NotifyWorker(Event event) {
            this.event = event;
        }

        @Override
        public void run() {
            if (EventType.PUBLISH == event.getEventType() || EventType.UNPUBLISH == event.getEventType()) {
                try {
                    Configuration config = (Configuration) event.getEventSource();
                    // get the configuration list which is the latest version in the server.
                    List<Configuration> configList = NotifyService.this.configStorage.getConfigurationList(config);
                    if (null != configList && !configList.isEmpty()) {
                        String subscriberPath = PathUtils.getSubscriberPath(config);
                        Notify notify = new Notify();
                        notify.setPath(subscriberPath);
                        notify.setConfigLists(configList);

                        //create RemotingCommand
                        RemotingCommand request = RemotingCommand.createRequestCommand(RequestCode.NOTIFY_CONFIG);
                        byte[] body = RemotingSerializable.encode(notify);
                        if (null != body) {
                            request.setBody(body);
                        }
                        //get the subscriber's channels that will being to notify
                        List<Channel> subscriberChannels = NotifyService.this.configStorage.getSubscribeChannel(subscriberPath);
                        //TODO: Using a thread pool that  notify the subscriber's channel may be a better choice.
                        if (null != subscriberChannels && !subscriberChannels.isEmpty()) {
                            for (Channel channel : subscriberChannels) {
                                NotifyService.this.getConfigNettyServer().getRemotingServer().invokeSync(channel, request, NOTIFY_TIMEOUT);
                            }
                        }
                    }
                } catch (Exception e) {
                    //log the exception
                    ConfigServerLogger.error("NotifyWorker process failed, details is ", e);
                }
            }
        }
    }

    /**
     * the event dispatcher
     */
    class EventDispatcher implements Runnable {
        @Override
        public void run() {
            while (!stop && !Thread.interrupted()) {
                try {
                    Event event = eventQueue.poll(POLL_TIMEOUT, TimeUnit.MILLISECONDS);
                    if (null != event) {
                        long delayTime = System.currentTimeMillis() - event.getEventCreatedTime();
                        if (delayTime <= MAX_DELAY_TIME) {
                            EventType eventType = event.getEventType();
                            if (EventType.PUBLISH == eventType || EventType.UNPUBLISH == eventType) {
                                onConfigChanged(event);
                            } else if (EventType.SUBCRIBE == eventType) {
                                onSubscribed(event);
                            }
                        } else {
                            //log this event and ignore
                            ConfigServerLogger.warn(String.format("%s after the current time too much,so ignore it!", event));
                        }
                    }
                } catch (Throwable t) {
                    ConfigServerLogger.error("EventDispatcher process event failed, details is ", t);
                }
            }
        }
    }
}