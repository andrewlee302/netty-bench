package test.local;

/**
 * @author andrew
 * Average number of bytes of messages is 50.
 * 
 */
public class LocalMonitorWriter {
    static int messageSize = 1024;
    static int messageNum = 1000000;

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("param: messageSize #message");
            System.exit(1);
        } else {
            messageSize = Integer.parseInt(args[0]);
            messageNum = Integer.parseInt(args[1]);
        }
        String cell = "hello world";
        StringBuffer message = new StringBuffer(messageSize);
        for (int i = 0; i < messageSize / cell.length(); i++) {
            message.append(cell);
        }
        message.append("\n");
        String sendContent = message.toString();
        System.out.println("The real sending message's size is " + sendContent.getBytes().length + " bytes");
        
        long counter = 0;
        long start = System.currentTimeMillis();
        while (++counter < messageNum) {
            System.out.println(sendContent);
        }
        System.out.println("Counter = " + (counter -1));
        double interval = (System.currentTimeMillis()-start) / 1000.0;
        System.out.println(interval + "s; average transfer rate: " + (sendContent.getBytes().length * messageNum / 1024.0 / 1024 / interval) + "M/s");    }

}
