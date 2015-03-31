package test.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;

/**
 * @author andrew
 * Average number of bytes of messages is 50.
 * The tuning parameter is: flushNum, indicates how many messages will trigger a flush.
 * (Netty hasn't automatic flush mechanism, so we should explicitly flush.)
 */
public class SentenceSocketClient {

    static String HOST = "127.0.0.1";
    static int PORT = 8081;
    static int flushNum = 4001;
    static int messageSize = 1024;
    static int messageNum = 1000000;
    static int threadNum = 1;
    
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("exp")) {
            if (args.length != 7) {
                System.out.println("param: exp HOST PORT messageSize(Byte) flushNum #message #threads");
                System.exit(1);
            } else {
                HOST = args[1];
                PORT = Integer.parseInt(args[2]);
                messageSize = Integer.parseInt(args[3]);
                flushNum = Integer.parseInt(args[4]);
                messageNum = Integer.parseInt(args[5]);
                threadNum = Integer.parseInt(args[6]);
            }
        }
        
        String cell = "hello world";
        StringBuffer message = new StringBuffer(messageSize);
        for(int i = 0; i < messageSize/cell.length(); i++){
            message.append(cell);
        }
        message.append("\n");
        final String sendContent = message.toString();
        System.out.println("The real sending message's size is " + sendContent.getBytes().length + " bytes");
        Runnable r = new Runnable(){
            @Override
            public void run() {
                EventLoopGroup group = new NioEventLoopGroup();
                long counter = 0;
                Channel channel = null;
                long start = 0;
                try {
                    Bootstrap b = new Bootstrap();
                    b.group(group).channel(NioSocketChannel.class).handler(new ChannelInitializer<SocketChannel>(){

                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast("frameDecoder", new LineBasedFrameDecoder(80));
                            pipeline.addLast("stringDecoder", new StringDecoder(CharsetUtil.UTF_8));
                            pipeline.addLast("stringEncoder", new StringEncoder(CharsetUtil.UTF_8));
                        }
                    });
                    channel = b.connect(HOST, PORT).sync().channel();
                    start = System.currentTimeMillis();
                    while(counter++ < messageNum){
                        channel.write(sendContent);
                        if(0 == counter % flushNum)
                            channel.flush();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    if(channel != null)
                        channel.flush();
                        try {
                            channel.closeFuture().sync();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    double interval = (System.currentTimeMillis()-start) / 1000.0;
                    System.out.println(interval + " s;" + (sendContent.getBytes().length * messageNum / 1024.0 / 1024 / interval) + " M/s");
                    group.shutdownGracefully();
                }                
            }
        };
        for(int i = 0; i < threadNum; i++) {
            new Thread(r).start();
        }
    }
}
