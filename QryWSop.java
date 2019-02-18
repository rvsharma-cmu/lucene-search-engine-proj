import java.io.IOException;
import java.util.List; 
import java.util.ArrayList; 


public abstract class QryWSop extends QrySop{

	List<Double> weights; 
	
	// constructor 
	public QryWSop() {
		
		this.weights = new ArrayList<Double>();
		
	}
	
	/**
	 * 
	 * @param weightToAdd add the weight to the list of 
	 * weights in the weight list 
	 */
	public void addWeight(double weightToAdd) {
		
		this.weights.add(weightToAdd);
		
	}
	
	/**
	 * Method for calculating the sum of weights of the 
	 * query being evaluated 
	 * @return the sum of the weights 
	 */
	public double sumOfWeights() {
		
		double sum = 0.0; 
		
		for(Double w : this.weights) {
			sum += w; 
		}
		
		return sum; 
	}
	
	/**
	 * The method get the list of the weights of 
	 * the argument 
	 * @return returns the list of weights of the query 
	 */
	public List<Double> getListWeights() {
		
		return this.weights;
	}
	
	/**
	 * Sets the list of the weights given as an argument 
	 * for the weighted operator 
	 * @param weightsList List<Double> passed as a parameter 
	 */
	public void setListWeights(List<Double> weightsList) {
		
		this.weights = weightsList; 
	}
	
	/**
	 * 
	 * @param index index of the argument for which you want the weight  
	 * @return weight of the argument specified 
	 */
	public double getWeight(int index) {
		
		return this.weights.get(index);
		
	}

}
