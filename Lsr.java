import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Hashtable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class Lsr {
	public static CopyOnWriteArrayList<Node> graph;
	public static byte seqNum;
	
	public static ConcurrentHashMap<Character, Byte> latestSeqNums;
	public static ConcurrentHashMap<Character, Integer> neighbourPorts;
	public static ConcurrentHashMap<Character, Float> neighbourCosts;
	
	// Size of one entry in the link state packet
	public static final int HEADER_SIZE = 3;
	public static final int LSP_SOURCE_NODE = 0;
	public static final int LSP_SEQUENCE_NUMBER = 1;
	public static final int LSP_NUM_ENTRIES = 2;
	
	public static final int ENTRY_SIZE = 5;
	public static final int LSP_ENTRY_DESTINATION_NODE_NAME = 0; // 0
	public static final int LSP_ENTRY_LINK_DISTANCE = 1; //1-4
	// 0 Node (char/byte)
	// 1-4 Distance (float)
	
	public static void main(String[] args) throws IOException, InterruptedException {
		// Check arguments
		if (args.length != 3) {
			System.out.println("Required arguments: node_name node_port config.txt");
			return;
		}
		
		// Variables
		seqNum = 0b00000001; // starts at 1
		graph = new CopyOnWriteArrayList<Node>();
		neighbourPorts = new ConcurrentHashMap<Character, Integer>();
		neighbourCosts = new ConcurrentHashMap<Character, Float>();
		latestSeqNums = new ConcurrentHashMap<Character, Byte>();
		
		// Arguments
		char thisNodeName = args[0].charAt(0);
		int thisNodePort = Integer.parseInt(args[1]);
		String configFilename = args[2];
		
		// Initial Node
		Node startingNode = new Node(thisNodeName);
		graph.add(startingNode);
		
		// Read config file line by line (ignores first line indicating total lines of input)
		try (BufferedReader br = new BufferedReader(new FileReader (configFilename))) {
			String line = br.readLine();
			while (true) {
				line = br.readLine();
				if (line == null) break;
			
				// Separate the line into components
				String[] configParts = line.split(" ");
				
				// Processing the parts of the config file
				char name = configParts[0].charAt(0);
				float cost = Float.parseFloat(configParts[1]);
				int port = Integer.parseInt(configParts[2]);
				
				// Create new node in the graph
				Node newNode = new Node(name);
				graph.add(newNode);
				
				// create initial links between node and starting node
				startingNode.addConnection(newNode, cost);
				newNode.addConnection(startingNode, cost);
				
				// Add entry to neighbor data hashes
				neighbourPorts.put(name, port);
				neighbourCosts.put(name, cost);
				latestSeqNums.put(name, (byte) 0);
			}
		}
		
		// Create Link State Packet
		byte[] dataOut = new byte[HEADER_SIZE + ENTRY_SIZE*neighbourCosts.size()];
		
		//// Set data in packet
		// The name of the origin node
		dataOut[0] = (byte) thisNodeName;
		// The sequence number
		dataOut[1] = seqNum;
		// The number of entries
		dataOut[2] = (byte) neighbourCosts.size();
		
		// Data for each node
		int indexCounter = HEADER_SIZE;
		for (char nodeName : neighbourCosts.keySet()) {
			
			// Name (1 byte)
			dataOut[indexCounter++] = (byte) nodeName;
			
			// Cost (4 bytes)
			byte[] costFloatBytes = floatToByteArray(neighbourCosts.get(nodeName));
			for (byte costByte: costFloatBytes) {
				dataOut[indexCounter++] = costByte;
			}
		}
		
		// Put data in Packet
		DatagramPacket packetOut = new DatagramPacket(dataOut, dataOut.length);
		
		// Periodically send Link State Packet to neighbors
		Thread receiverThread = new Thread(new SendRun(packetOut, neighbourPorts));
		receiverThread.start();
		
		Thread dijkstraThread = new Thread(new DijkstraRun(graph, thisNodeName));
		dijkstraThread.start();
		
		// Configure packet/socket for receiving
		byte[] dataIn = new byte[HEADER_SIZE + ENTRY_SIZE*25];
		DatagramSocket socketIn = new DatagramSocket(thisNodePort);
		DatagramPacket packetIn = new DatagramPacket(dataIn, dataIn.length);
		
		Hashtable<Character, Thread> heartbeatMonitors = new Hashtable<Character, Thread>();
		
		while (true) {
			// Continually Receive Packets
			socketIn.receive(packetIn);
			
			// create heartbeats for new nodes
			char otherNodeName = (char) packetIn.getData()[0];
			if (heartbeatMonitors.get(otherNodeName) == null && thisNodeName != otherNodeName) {
				Thread newMonitor = new Thread(new HeartBeatRun(otherNodeName, graph, packetOut));
				heartbeatMonitors.put(otherNodeName, newMonitor);
				newMonitor.start();
			}
			
			for (char nodeName : heartbeatMonitors.keySet()) {			
				if ((char) packetIn.getData()[0] == nodeName) {
					// detect heart beat
					heartbeatMonitors.get(nodeName).interrupt();
				}
				
				// if heartbeat exists but is dead, delete neighbour entries
				if (!heartbeatMonitors.get(nodeName).isAlive()) {
					neighbourCosts.remove(nodeName);
					neighbourPorts.remove(nodeName);
				}
			}
			
			// Process packet and send to neighbours
			processLinkStateData(packetIn.getData());
		}
	}
	
	private static void processLinkStateData(byte[] data) throws IOException {
		
		char nameOfNodeFrom = (char) data[0];
		byte seqNum = data[1];
		byte numEntries = data[2];

		// If this is packet from a new node, create a new sequence number entry
		if (!latestSeqNums.containsKey(nameOfNodeFrom)) {
			latestSeqNums.put(nameOfNodeFrom, (byte) 0);
		}
		
		// if the data is old / has already been seen, do not process the packet
		if (seqNum <= latestSeqNums.get(nameOfNodeFrom)) {
			return;
		}
		
		// if the data is new, update the seqNum and continue
		latestSeqNums.put(nameOfNodeFrom, (byte) seqNum);
		
		// Check if the origin node is in the graph yet
		// If it isn't, create the node, and add it to the graph
		ensureNodeIsInGraph(nameOfNodeFrom);
		
		int index = 3;
		for (int i = numEntries ; i > 0; i--) {
			// Process entry in link state packet
			char nameOfNodeTo = (char) data[index++];
			float cost = bytesToFloat(data[index++],data[index++],data[index++],data[index++]);		
			
			// If new node, add to graph
			ensureNodeIsInGraph(nameOfNodeTo);
			
			if (!connectionExists(nameOfNodeTo, nameOfNodeFrom)) {
				Node nodeA = getNode(nameOfNodeTo);
				Node nodeB = getNode(nameOfNodeFrom);
				nodeA.addConnection(nodeB, cost);
				nodeB.addConnection(nodeA, cost);
			}	
		}
		
		//// SENDING PACKET TO OTHER NEIGHBOURS
		// preparing packet and socket
		DatagramSocket socketOut = new DatagramSocket();
		DatagramPacket forwardPacket = new DatagramPacket(data, data.length);
		forwardPacket.setAddress(InetAddress.getLocalHost());
		
		// loop through neighbour ports
		for (char nodeName : neighbourPorts.keySet()) {
			// skip neighbour the packet was received from
			if (nodeName == nameOfNodeFrom) continue;
			// send the packet
			forwardPacket.setPort(neighbourPorts.get(nodeName));
			socketOut.send(forwardPacket);
		}
		socketOut.close();
	}
	
	public static byte [] floatToByteArray (float value) {  
	     return ByteBuffer.allocate(4).putFloat(value).array();
	}
	
	public static float bytesToFloat(byte b1, byte b2, byte b3, byte b4) {
		byte[] array = new byte[] {b1,b2,b3,b4};
	    ByteBuffer buf = ByteBuffer.wrap(array);
	    return buf.getFloat();
	}
	
	private static Node getNode (char nodeName) {
		for (Node n : graph) {
			if (n.getName() == nodeName) return n;
		}
		return null;
	}
	
	private static void ensureNodeIsInGraph(char nodeName) {
		Node nodeFrom = getNode(nodeName);
		if (nodeFrom == null) {
			nodeFrom = new Node(nodeName);
			graph.add(nodeFrom);
		}
	}
	
	private static boolean connectionExists(char node1, char node2) {
		for (Node n : graph) {
			for (Edge e : n.getEdges()) {
				if (e.getFromName() == node1 && e.getToName() == node2) return true;
				if (e.getFromName() == node2 && e.getToName() == node1) return true;
			}
		}
		return false;
	}
}
