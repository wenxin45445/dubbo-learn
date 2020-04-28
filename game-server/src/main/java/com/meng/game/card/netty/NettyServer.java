package com.meng.game.card.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.serialization.ObjectEncoder;
import io.netty.handler.stream.ChunkedWriteHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * netty server
 */
@Component
public class NettyServer {
    private static final Logger logger = LoggerFactory.getLogger(NettyServer.class);

    private static final String WEBSOCKET_PROTOCOL = "WebSocket";

    private int port;

    private String websocketPath;

    @Autowired
    private WebSocketHandler webSocketHandler;

    @Autowired
    private PermissionWebSocketHandler permissionWebSocketHandler;

    /**
     * bossGroup
     */
    private EventLoopGroup bossGroup;

    /**
     * parentGroup
     */
    private EventLoopGroup group;

    /**
     * 启动
     */
    @PostConstruct
    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup();
        group = new NioEventLoopGroup();
        ServerBootstrap sb = new ServerBootstrap();
        sb.option(ChannelOption.SO_BACKLOG, 1024);// 配置TCP参数，握手字符串长度设置
        sb.group(group, bossGroup) // group辅助客户端的tcp连接请求 bossGroup负责与客户端之前的读写操作
                .channel(NioServerSocketChannel.class) // 配置客户端的channel类型
                .localAddress(this.port)// 绑定监听端口
                .childHandler(new ChannelInitializer<SocketChannel>() { // 绑定客户端连接时候触发操作
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        // websocket协议本身是基于http协议的，所以这边也要使用http解编码器
                        ch.pipeline().addLast(new HttpServerCodec());
                        ch.pipeline().addLast(new ObjectEncoder());
                        // 以块的方式来写的处理器
                        ch.pipeline().addLast(new ChunkedWriteHandler());
                        ch.pipeline().addLast(new HttpObjectAggregator(8192));
                        ch.pipeline().addLast(permissionWebSocketHandler);
                        ch.pipeline().addLast(new WebSocketServerProtocolHandler(websocketPath, WEBSOCKET_PROTOCOL, true, 65536 * 10));
                        ch.pipeline().addLast(webSocketHandler);
                    }
                });
        // 服务器异步创建绑定
        sb.bind().sync();
        logger.info("websocket server start");
    }

    /**
     * 释放线程池资源: <br>
     */
    @PreDestroy
    private void destory() throws InterruptedException {
        if (null != bossGroup) {
            bossGroup.shutdownGracefully().sync();
        }
        if (null != group) {
            group.shutdownGracefully().sync();
        }
    }
}
