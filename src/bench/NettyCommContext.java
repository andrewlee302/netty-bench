package bench;

import java.util.ArrayList;
import java.util.List;

import com.sun.xml.internal.ws.Closeable;

import bench.NettyCommContext.NettyCommServer;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;
import sun.nio.cs.HistoricallyNamedCharset;

public class NettyCommContext {
	Channel channel;

	class NettyCommClient {
		Channel channel;
		ByteBufferMessageDecoder decoder = new ByteBufferMessageDecoder(1);
		EventLoopGroup group;

		public NettyCommClient(String hostname, int port) {
			group = new NioEventLoopGroup();			
			try {
				Bootstrap b = new Bootstrap();
				b.group(group).channel(NioSocketChannel.class)
						.handler(new ChannelInitializer<SocketChannel>() {
							@Override
							protected void initChannel(SocketChannel ch)
									throws Exception {
								ChannelPipeline pipeline = ch.pipeline();
								pipeline.addLast(decoder);
							}
						});
				channel = b.connect(hostname, port).sync().channel();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		public void close() {
			if (channel != null)
				channel.flush();
			try {
				channel.closeFuture().sync();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			group.shutdownGracefully();
		}
	}

	class NettyCommServer {
		List<Channel> chs; // only one client channel actually
		ByteBufferMessageDecoder decoder = new ByteBufferMessageDecoder(this,
				1);

		public NettyCommServer(String hostname, int port) {
			chs = new ArrayList<>();
			EventLoopGroup bossGroup = new NioEventLoopGroup(1);
			EventLoopGroup workerGroup = new NioEventLoopGroup();
			try {
				ServerBootstrap appServer = new ServerBootstrap();
				appServer.group(bossGroup, workerGroup)
						.channel(NioServerSocketChannel.class)
						.childHandler(new ChannelInitializer<SocketChannel>() {
							@Override
							protected void initChannel(SocketChannel ch)
									throws Exception {
								ChannelPipeline pipeline = ch.pipeline();
								pipeline.addLast(decoder);
							}
						});
				Channel appCh = appServer.bind(port).sync().channel();
				appCh.closeFuture().sync();
			} catch (InterruptedException e) {
				e.printStackTrace();
			} finally {
				// Shut down executor threads to exit.
				bossGroup.shutdownGracefully();
				workerGroup.shutdownGracefully();
			}
		}

	}

	public void Send(byte[] buf) {
		channel.writeAndFlush(buf);

		long start = System.currentTimeMillis();
		double interval = (System.currentTimeMillis() - start) / 1000.0;
//		System.out.println(interval + " s;" + (sendContent.getBytes().length
//				* messageNum / 1024.0 / 1024 / interval) + " M/s");

	}

	public void Recv(byte[] buf) {

	}

}

class ByteBufferMessageDecoder extends ByteToMessageDecoder {
	int size;
	NettyCommServer server;
	boolean isServer;

	public ByteBufferMessageDecoder(NettyCommServer server, int bufferSize) {
		this.isServer = true;
		this.server = server;
		this.size = bufferSize;
	}

	public ByteBufferMessageDecoder(int bufferSize) {
		this.isServer = false;
		this.size = bufferSize;
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		if (isServer)
			server.chs.add(ctx.channel());
	}

	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf in,
			List<Object> out) throws Exception {
		if (in.readableBytes() < size) {
			return;
		}
		byte[] buf = new byte[size];
		in.readBytes(buf);
		out.add(buf);
	}

	public void resize(int size) {
		this.size = size;
	}
}