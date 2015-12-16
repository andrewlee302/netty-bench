package bench;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import bench.NettyCommContext.NettyCommServer;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;

public class NettyCommContext {
	Channel channel;
	BlockingQueue<byte[]> recvQueue;
	ByteBufferMessageDecoder decoder;
	ByteRecvHandler recvHandler;
	Object entity;

	public NettyCommContext(String hostname, int port, boolean isServer) {
		recvQueue = new LinkedBlockingQueue<byte[]>();
		decoder = new ByteBufferMessageDecoder(1);
		recvHandler = new ByteRecvHandler(recvQueue);
		if (isServer) {
			entity = new NettyCommServer(hostname, port, decoder, recvHandler);
		} else {
			entity = new NettyCommClient(hostname, port, decoder, recvHandler);
			this.channel = ((NettyCommClient) entity).channel;
		}
	}
	
//	public NettyCommContext(String hostname, int port, boolean isServer, int size) {
//		recvQueue = new LinkedBlockingQueue<byte[]>();
//		decoder = new ByteBufferMessageDecoder(size);
//		recvHandler = new ByteRecvHandler(recvQueue);
//		if (isServer) {
//			entity = new NettyCommServer(hostname, port, decoder, recvHandler);
//		} else {
//			entity = new NettyCommClient(hostname, port, decoder, recvHandler);
//			this.channel = ((NettyCommClient) entity).channel;
//		}
//	}

	class NettyCommClient {
		Channel channel;
		EventLoopGroup group;

		public NettyCommClient(String hostname, int port,
				final ByteBufferMessageDecoder decoder,
				final ByteRecvHandler recvHandler) {
			group = new NioEventLoopGroup();
			try {
				Bootstrap b = new Bootstrap();
				b.option(ChannelOption.TCP_NODELAY, true);
				b.option(ChannelOption.SO_KEEPALIVE, true);
				b.group(group).channel(NioSocketChannel.class)
						.handler(new ChannelInitializer<SocketChannel>() {
							@Override
							protected void initChannel(SocketChannel ch)
									throws Exception {
								ChannelPipeline pipeline = ch.pipeline();
								pipeline.addLast(decoder);
								pipeline.addLast(recvHandler);
								pipeline.addLast(new ByteBufferMessageEncoder());
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
				channel.close().sync();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			group.shutdownGracefully();
		}
	}

	class NettyCommServer {
		List<Channel> chs; // only one client channel actually
		Channel serverChannel;
		EventLoopGroup bossGroup, workerGroup;

		public NettyCommServer(String hostname, int port,
				final ByteBufferMessageDecoder decoder,
				final ByteRecvHandler recvHandler) {
			decoder.server = this;
			decoder.isServer = true;

			chs = new ArrayList<>();
			bossGroup = new NioEventLoopGroup(1);
			workerGroup = new NioEventLoopGroup();
			try {
				ServerBootstrap appServer = new ServerBootstrap();
				appServer.option(ChannelOption.SO_REUSEADDR, true);
				appServer.childOption(ChannelOption.TCP_NODELAY, true);
				appServer.childOption(ChannelOption.SO_KEEPALIVE, true);
				appServer.group(bossGroup, workerGroup)
						.channel(NioServerSocketChannel.class)
						.childHandler(new ChannelInitializer<SocketChannel>() {
							@Override
							protected void initChannel(SocketChannel ch)
									throws Exception {
								ChannelPipeline pipeline = ch.pipeline();
								pipeline.addLast(decoder);
								pipeline.addLast(recvHandler);
								pipeline.addLast(new ByteBufferMessageEncoder());
							}
						});
				serverChannel = appServer.bind(port).sync().channel();
				while (chs.isEmpty()) {
					Thread.sleep(100);
				}
				System.out.println("client get connected to the server");
				channel = chs.get(0);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		public void close() {
			try {
				serverChannel.close().sync();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			bossGroup.shutdownGracefully();
			workerGroup.shutdownGracefully();
		}

	}

	// send and recv in blocking mode
	public byte[] SendRecv(byte[] sendBuf) {
		try {
			// System.out.println("Client send start");
			channel.writeAndFlush(sendBuf).sync();
			// System.out.println("Client send end");
			byte[] buf = recvQueue.take();
			// System.out.println("Client recv success");
			return buf;
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return null;
	}

	// recv and send in blocking mode
	public void RecvSend(byte[] sendBuf) {
		try {
			// System.out.println("Server recv start");
			recvQueue.take();
			// System.out.println("Server recv success");
			// System.out.println("Server send start");
			channel.writeAndFlush(sendBuf).sync();
			// System.out.println("Server send end");
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public void Isend(byte[] sendBuf) {
		channel.write(sendBuf);
	}

	public void resize(int size) {
		decoder.resize(size);
	}

}

class ByteBufferMessageDecoder extends ByteToMessageDecoder {
	int size;
	NettyCommServer server;
	boolean isServer;

	public ByteBufferMessageDecoder(int bufferSize) {
		this.isServer = false;
		this.size = bufferSize;
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		if (isServer) {
			System.out.println("server will close");
			server.close();
		}
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		if (isServer) {
			server.chs.add(ctx.channel());
		}
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
		// System.out.println("recv content: " + buf.toString());
	}

	public void resize(int size) {
		this.size = size;
	}
}

class ByteRecvHandler extends SimpleChannelInboundHandler<byte[]> {
	BlockingQueue<byte[]> cachedRecvQueue;

	public ByteRecvHandler(BlockingQueue<byte[]> cachedRecvQueue) {
		this.cachedRecvQueue = cachedRecvQueue;
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, byte[] msg)
			throws Exception {
		cachedRecvQueue.put(msg);
	}

}

class ByteBufferMessageEncoder extends MessageToByteEncoder<byte[]> {

	@Override
	protected void encode(ChannelHandlerContext ctx, byte[] msg, ByteBuf out)
			throws Exception {
		out.writeBytes(msg);
	}
}