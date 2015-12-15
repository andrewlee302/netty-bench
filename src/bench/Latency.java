package bench;

public class Latency {

	final static int MAX_MSG_SIZE = 1 << 22;
	final static int MAX_LOOP = 100;
	final static int MAX_SKIP = 10;
	final static int LARGE_MESSAGE_SIZE = 8196;
	static int loop = 10000;
	static int skip = 1000;
	static byte[] originSendBuffer;

	public static void main(String[] args) {
		if (args.length < 3) {
			System.out.println("args: hostname port isServer");
			return;
		}

		String hostname = args[0];
		int port = Integer.parseInt(args[1]);
		boolean isServer = Boolean.parseBoolean(args[2]);
		NettyCommContext ct = new NettyCommContext(hostname, port, isServer);
		originSendBuffer = new byte[MAX_MSG_SIZE];

		for (int size = 1; size < MAX_MSG_SIZE; size *= 2) {
			ct.resize(size);
			byte[] sendBuf = new byte[size];
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
					ct.SendRecv(sendBuf);
				}
				long end = System.currentTimeMillis();
				double latency = (end - start) * 1e6 / (2.0 * loop);
				System.out.printf("%-*d%*.*f\n", 10, size, 20, 2, latency);
			} else {
				for (int i = 0; i < loop + skip; i++) {
					ct.RecvSend(sendBuf);
				}
			}
		}
	}
}
