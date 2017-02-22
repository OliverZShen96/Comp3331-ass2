import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class DijkstraRun implements Runnable {
	private static final int ROUTE_UPDATE_INTERVAL = 5000;
	private static final float INFINITY = Float.MAX_VALUE;
	private CopyOnWriteArrayList<Node> graph;
	private ConcurrentHashMap<Byte, Float> shortestPaths;
	private ConcurrentHashMap<Byte, Node> previous;
	private byte startingNode;
	
	public DijkstraRun(CopyOnWriteArrayList<Node> graph, char startingNode) {
		this.graph = graph;
		this.startingNode = (byte) startingNode;
	}
	
	@Override
	public void run() {
		while (true) {
			try {
				Thread.sleep(ROUTE_UPDATE_INTERVAL);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			// Run Dijkstra's algorithm on the graph, store the shortest path in shortestpaths
			this.shortestPaths = new ConcurrentHashMap<Byte, Float>();
			this.previous = new ConcurrentHashMap<Byte, Node>();
			
			ArrayList<Node> q = new ArrayList<Node>();
			for (Node n : graph) {
				shortestPaths.put((byte) n.getName(), INFINITY);
				q.add(n);
			}
			shortestPaths.put(startingNode, (float) 0);
	
			while (!q.isEmpty()) {
				Node u = getMinDistNode(q);
				q.remove(u);
				
				for (Node v : u.getNeighbours()) {
					float alt = shortestPaths.get((byte)u.getName()) + getLink(u, v).getCost();
					if (alt < shortestPaths.get((byte)v.getName())) {
						shortestPaths.put((byte) v.getName(), alt);
						previous.put((byte) v.getName(), u);
					}
				}
			}
			
			// printing path
			for (Node n : graph) {
				if ((byte) n.name == startingNode) continue;
				Node start = getNodeByName(startingNode);
				Node curr = n;
				String path = "";
				while (curr != start && curr != null) {
					path = Character.toString(curr.getName()).concat(path);
					curr = previous.get((byte) curr.getName());
				}
				if (curr != null) path = Character.toString(curr.getName()).concat(path);
				System.out.print("least-cost path to node " + n.getName() + " : "  + path);
				System.out.println(" and the cost is " + shortestPaths.get((byte) n.getName()));
			}
		}
	}
	
	private Node getNodeByName(byte name) {
		for (Node n : graph) {
			if (n.getName() == name) return n;
		}
		return null;
	}
	
	private Node getMinDistNode(ArrayList<Node> q) {
		Node min = q.get(0);
		for (Node n : q) {
			if (shortestPaths.get((byte)n.getName()) < shortestPaths.get((byte)min.getName())) {
				min = n;
			}
		}
		return min;
	}
	
	private Edge getLink (Node from, Node to) {
		for (Node n : graph) {
			if (n == from) {
				for (Edge e : n.getEdges()) {
					if (e.to == to) return e;
				}
			}
		}
		return null;	
	}

}
