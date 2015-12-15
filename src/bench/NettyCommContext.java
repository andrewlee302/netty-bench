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
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;

public class NettyCommContext {
	Channel channel;
	BlockingQueue<byte[]> recvQueue;
	ByteBufferMessageDecoder decoder;
	ByteRecvHandler recvHandler;

	public NettyCommContext(String hostname, int port, boolean isServer) {
		recvQueue = new LinkedBlockingQueue<byte[]>();
		decoder = new ByteBufferMessageDecoder(1);
		recvHandler = new ByteRecvHandler(recvQueue);
		if (isServer) {
			this.channel = new NettyCommServer(hostname, port, decoder, recvHandler).chs.get(0);
		} else {
			this.channel = new NettyCommClient(hostname, port, decoder, recvHandler).channel;
		}
	}

	class NettyCommClient {
		Channel channel;
		EventLoopGroup group;

		public NettyCommClient(String hostname, int port,
				final ByteBufferMessageDecoder decoder, final ByteRecvHandler recvHandler) {
			group = new NioEventLoopGroup();
			try {
				Bootstrap b = new Bootstrap();
				b.group(group).channel(NioSocketChannel.class)
						.handler(new ChannelInitializer<SocketChannel>() {
							@Override
							protected void initChannel(SocketChannel ch)
									throws Exception {
								ChannelPipeline pipeline = ch.pipeline();
								pipeline.addLast(decoder, recvHandler);
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

		public NettyCommServer(String hostname, int port,
				final ByteBufferMessageDecoder decoder, final ByteRecvHandler recvHandler) {
			decoder.server = this;
			decoder.isServer = true;

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
								pipeline.addLast(decoder, recvHandler);
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

	// send and recv in blocking mode
	public byte[] SendRecv(byte[] sendBuf) {
		channel.writeAndFlush(sendBuf);
		try {
			return recvQueue.take();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return null;
	}

	// recv and send in blocking mode
	public void RecvSend(byte[] sendBuf) {
		try {
			recvQueue.take();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		channel.writeAndFlush(sendBuf);
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

class ByteRecvHandler extends SimpleChannelInboundHandler<byte[]>{
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