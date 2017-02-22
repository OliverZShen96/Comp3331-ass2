import java.net.DatagramPacket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class HeartBeatRun implements Runnable {
	public CopyOnWriteArrayList<Node> graph;
	public char nodeName;
	public boolean dead;
	public DatagramPacket packetOut;
	
	public HeartBeatRun(char nodeName, CopyOnWriteArrayList<Node> graph, DatagramPacket packetOut) {
		this.nodeName = nodeName;
		this.dead = false;
		this.graph = graph;
		this.packetOut = packetOut;
	}
	
	@Override
	public void run() {
		while (true) {
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
				//heartbeat found
				continue;
			}
			System.out.println("heartbeat missing : " + nodeName);
			// Remove instances of that node
			
			Node toKill = null;
			for (Node n : graph) {
				if (n.getName() == nodeName) {
					toKill = n;
					continue;
				}
				ArrayList<Edge> edgesToRemove = new ArrayList<Edge>();
				for (Edge e : n.getEdges()) {
					if (e.getFromName() == nodeName || e.getToName() == nodeName) {
						edgesToRemove.add(e);
					}
				}
				n.getEdges().removeAll(edgesToRemove);
			}
			graph.remove(toKill);
			
			// Remove entry for node in the packet
			byte[] newData = null;
			byte[] data = packetOut.getData();
			int numEntries = data[2];
			data[2]--;
			int index = 3;
			while (numEntries > 0) {
				if (data[index] == (byte) nodeName) {
					newData = new byte[data.length-5];
					System.arraycopy(data, 0, newData, 0, index);
					System.arraycopy(data, index+5, newData, index, data.length - index - 5);
					packetOut.setData(newData);
					break;
				}
				index += 5;
				numEntries--;
			}
			break;
		}
	}
	
	public char getNodeName() {
		return nodeName;
	}
}
