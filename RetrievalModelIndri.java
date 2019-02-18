
public class RetrievalModelIndri extends RetrievalModel{

	private int mu; 
	private double lambda;
	
	
	public RetrievalModelIndri(int mu, double lambda) {
		super();
		this.setMu(mu);
		this.setLambda(lambda);
	}


	@Override
	public String defaultQrySopName() {
		
		String defaultOp = "#and";
		
		return defaultOp;
	}


	public int getMu() {
		return mu;
	}


	public void setMu(int mu) {
		this.mu = mu;
	}


	public double getLambda() {
		return lambda;
	}


	public void setLambda(double lambda) {
		this.lambda = lambda;
	}
	
	
	
}
