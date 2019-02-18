/**
 * 
 * @author raghavs
 * DEfault Retrieval model for BM25 
 * The default operator for this is #sum 
 * the parameters for the model are taken from the 
 * input param file
 *
 */
public class RetrievalModelBM25 extends RetrievalModel{

	private double k_1;
	private double k_3; 
	private double b;
	
	public RetrievalModelBM25(double k_1, double k_3, double b) {
		
		this.setK_1(k_1);
		this.setK_3(k_3); 
		this.setB(b);
		
	}

	@Override
	public String defaultQrySopName() {

		String defaultOp = "#sum";
		return defaultOp;
	}

	public double getK_1() {
		return k_1;
	}

	public void setK_1(double k_1) {
		this.k_1 = k_1;
	}

	public double getK_3() {
		return k_3;
	}

	public void setK_3(double k_3) {
		this.k_3 = k_3;
	}

	public double getB() {
		return b;
	}

	public void setB(double b) {
		this.b = b;
	}
	
}
