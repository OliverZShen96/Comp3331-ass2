import java.util.ArrayList;

public class Node {
	public char name;
	public ArrayList<Edge> edges;
	
	public Node(char name) {
		this.name = name;
		this.edges = new ArrayList<Edge>();
	}
	
	public void addConnection(Node n, float cost) {
		edges.add(new Edge(this, n, cost));
		edges.add(new Edge(n, this, cost));
	}
	
	public ArrayList<Edge> getEdges() {
		return this.edges;
	}
	
	public char getName() {
		return this.name;
	}

	public ArrayList<Node> getNeighbours() {
		ArrayList<Node> neighbours = new ArrayList<Node>();
		
		for (Edge e : edges) {
			if (e.getFromName() == this.getName()) neighbours.add(e.to);
		}
		
		return neighbours;
	}
	
}
