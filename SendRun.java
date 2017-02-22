import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

class SendRun implements Runnable {
	private static final int UPDATE_INTERVAL = 1000;
	private DatagramSocket socketOut;
	private DatagramPacket packetOut;
	private ConcurrentHashMap<Character, Integer> neighbourPorts;
	
	public SendRun(DatagramPacket packetOut, ConcurrentHashMap<Character, Integer> neighbourPorts) throws SocketException {
		this.packetOut = packetOut;
		this.neighbourPorts = neighbourPorts;
		this.socketOut = new DatagramSocket();
	}
	public void run() {
		while(true){
			
			for (Integer port : neighbourPorts.values()) {
				packetOut.setPort(port);
				try {
					packetOut.setAddress(InetAddress.getLocalHost());
				} catch (UnknownHostException e) {
					e.printStackTrace();
				}
				
				try {
					socketOut.send(packetOut);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}		
			}
			
			// retransmits every 1 seconds
			try {
				Thread.sleep(UPDATE_INTERVAL);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			// increment sequence number
			packetOut.getData()[1] ++;
		}
	}
}