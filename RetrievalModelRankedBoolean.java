
/* Object that stores Ranked boolean retrieval model and 
 * indicates to the query operator how the query should be evaluated
 */
public class RetrievalModelRankedBoolean extends RetrievalModel{

	@Override
	public String defaultQrySopName() {
		
		String defaultOp = "#or";
		return defaultOp;
	}
	

	
}
