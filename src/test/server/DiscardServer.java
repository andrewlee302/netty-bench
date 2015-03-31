package test.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.CharsetUtil;


public class DiscardServer {
    static final int APP_PORT = 8081;
    static int messageNum = 1000000;

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("exp")) {
            if (args.length != 2) {
                System.out.println("param: exp #message");
                System.exit(1);
            } else {
                messageNum = Integer.parseInt(args[1]);
            }
        }
        
        EventLoopGroup bossGroup = new NioEventLoopGroup(2);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap appServer = new ServerBootstrap();
            appServer.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new ChannelInitializer<SocketChannel>(){
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast("frameDecoder", new LineBasedFrameDecoder(1024 * 1024, true, true	));
                            pipeline.addLast("stringDecoder", new StringDecoder(CharsetUtil.UTF_8));
                            pipeline.addLast("stringEncoder", new StringEncoder(CharsetUtil.UTF_8));
                            
                            pipeline.addLast(new SimpleChannelInboundHandler<Object>(){
                                private long counter = 0;
                                private long start;
                                
                                public void channelActive(ChannelHandlerContext ctx) throws Exception {
                                    start = System.currentTimeMillis();
                                    System.out.println("started");
                                }
                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
                                	String s = (String)msg;
                                	if(s.contains("end") || ++counter>=messageNum){
                                		ctx.close();
                                        System.out.println((System.currentTimeMillis()-start)/1000.0+"s");
                                        System.out.println("counter = " + counter);
                                    }
                                }
                                @Override
								public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause){
									System.out.println(cause);
								}
                            });
                            
                        }
                        
                    });
            Channel appCh = appServer.bind(APP_PORT).sync().channel();
            appCh.closeFuture().sync();
        } finally {
            // Shut down executor threads to exit.
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }

    }
}
