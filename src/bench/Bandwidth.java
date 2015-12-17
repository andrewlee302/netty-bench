package bench;

import bench.NettyCommContext.NettyCommClient;

public class Bandwidth {
	final static int MAX_MSG_SIZE = 1 << 22;
	final static int MAX_LOOP = 100;
	final static int MAX_SKIP = 10;
	final static int WINDOW_SIZE_LARGE = 64;
	final static int LARGE_MESSAGE_SIZE = 8196;
	static int window_size = 64;
	static int loop = 10000;
	static int skip = 1000;
	static byte[] originSendBuffer;

	public static void main(String[] args) throws InterruptedException {
		if (args.length < 3) {
			System.out.println("args: hostname port isServer");
			return;
		}

		String hostname = args[0];
		int port = Integer.parseInt(args[1]);
		boolean isServer = Boolean.parseBoolean(args[2]);
		NettyCommContext ct = new NettyCommContext(hostname, port, isServer);
		originSendBuffer = new byte[MAX_MSG_SIZE];
		for(int i = 0; i< originSendBuffer.length;i++) {
			originSendBuffer[i] = (byte)(i & 0xFF);
		}
		
		if(!isServer){
			System.out.println("Netty Bandwidth Test");
			System.out.printf("%-10s%20s\n", "# Size", "Bandwidth (MB)");
		}

		// ======= warm up =========
		int size = 1024;
		ct.resize(size);
		Thread.sleep(1000);
		byte[] sendBuf = new byte[size];
		System.arraycopy(originSendBuffer, 0, sendBuf, 0, size);
		if (size > LARGE_MESSAGE_SIZE) {
			loop = MAX_LOOP;
			skip = MAX_SKIP;
		}
		if (!isServer) {
			for (int i = 0; i < loop + skip; i++) {
				ct.SendRecv(sendBuf);
			}
		} else {
			for (int i = 0; i < loop + skip; i++) {
				ct.RecvSend(sendBuf);
			}
		}
		// ====================
		
		for (size = 1; size < MAX_MSG_SIZE; size *= 2) {
			ct.resize(size);
			Thread.sleep(1000);
			sendBuf = new byte[size];
			System.arraycopy(originSendBuffer, 0, sendBuf, 0, size);
			if (size > LARGE_MESSAGE_SIZE) {
				loop = MAX_LOOP;
				skip = MAX_SKIP;
			}
			if (!isServer) {
				long start = 0;
				for (int i = 0; i < loop + skip; i++) {
					if (i == skip)
						start = System.currentTimeMillis();
					for (int j = 0; j < window_size; j++) {
						// TODO Isend
					}
					// TODO Waitall and recv
				}
				long end = System.currentTimeMillis();
				double tmp = size * 1e3 /((end - start) * 1e6 * window_size * loop);
				System.out.printf("%-10d%20.2f\n", size, tmp);
			} else {
				for (int i = 0; i < loop + skip; i++) {
					for(int j = 0; j < window_size; j++) {
						// TODO Irecv
					}
					// TODO waitall and send
				}
			}
		}
		if(!isServer) {
			((NettyCommClient)ct.entity).close();
		}
		
	}
}
