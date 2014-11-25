package com.cmbc.configserver.remoting.netty;

import io.netty.channel.Channel;

public class NettyEvent {
	private final NettyEventType type;
	private final String remoteAddress;
	private final Channel channel;
    private final Throwable cause;

    public NettyEvent(NettyEventType type, String remoteAddress, Channel channel,Throwable cause){
        this.type = type;
        this.remoteAddress = remoteAddress;
        this.channel = channel;
        this.cause = cause;
    }

	public NettyEventType getType() {
		return type;
	}

	public Channel getChannel() {
		return channel;
	}

    public Throwable getCause(){
        return cause;
    }

	@Override
	public String toString() {
		return "NettyEvent [type=" + type + ", remoteAddress=" + remoteAddress
				+ ", channel=" + channel + "]";
	}
}
