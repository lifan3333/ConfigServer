package com.cmbc.configserver.remoting.netty;

import com.cmbc.configserver.remoting.common.RemotingHelper;
import com.cmbc.configserver.remoting.common.RemotingUtil;
import com.cmbc.configserver.remoting.protocol.RemotingCommand;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

public class NettyEncoder extends MessageToByteEncoder<RemotingCommand> {
    private static final Logger log = LoggerFactory.getLogger(NettyEncoder.class);


    @Override
    public void encode(ChannelHandlerContext ctx, RemotingCommand remotingCommand, ByteBuf out)
            throws Exception {
        try {
            //TODO: compress the body's bytes stream
            /*
             * 1. compute the body's length,then determine whether compress the body which a specified algorithm;
             * 2. meanwhile,we should rewrite the request commanding header.
             */
            ByteBuffer header = remotingCommand.encodeHeader();
            out.writeBytes(header);
            byte[] body = remotingCommand.getBody();
            if (body != null) {
                out.writeBytes(body);
            }
        }
        catch (Exception e) {
            log.error("encode exception, " + RemotingHelper.parseChannelRemoteAddr(ctx.channel()), e);
            if (remotingCommand != null) {
                log.error(remotingCommand.toString());
            }
            RemotingUtil.closeChannel(ctx.channel());
        }
    }
}