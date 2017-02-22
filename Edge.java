
public class Edge {
	public Node to;
	public Node from;
	public Float cost;

	public Edge(Node from, Node to, Float cost) {
		this.to = to;
		this.from = from;
		this.cost = cost;
	}
	
	public void changeCost(Float cost) {
		this.cost = cost;
	}
	
	public Node getFrom() {
		return from;
	}
	
	public Node getTo() {
		return to;
	}
	
	public char getFromName() {
		return from.getName();
	}
	
	public char getToName() {
		return to.getName();
	}
	
	public Float getCost() {
		return cost;
	}
}
