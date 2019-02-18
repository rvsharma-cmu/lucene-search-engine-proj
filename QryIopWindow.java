import java.io.IOException;
import java.util.Vector;

public class QryIopWindow extends QryIop{


	private int operatorDistance; 
	private Vector<Integer> q0_postings; 
	private Vector<Integer> qi_postings; 
	
	public QryIopWindow(String operatorName, int operatorDistance) {
		
		if(operatorDistance < 0) {
			throw new IllegalArgumentException("The distance for the window"
					+ "operator is less than 0");
			
		} 
		
		this.operatorDistance = operatorDistance; 
		
		if(! operatorName.toLowerCase().equals("#window"))
			throw new IllegalArgumentException("String argument not a "
					+ "window operator. Quitting");
		
		q0_postings = new Vector<Integer>();
		qi_postings = new Vector<Integer>();
		
	}
	
	@Override
	protected void evaluate() throws IOException {
		
		String fieldName = this.getField();
		
		this.invertedList = new InvList(fieldName);
		
		if(this.args.size() < 2) {
			throw new IllegalArgumentException("The argument for the "
					+ "window operator is incorrect. Check the query");
		}
		
		while (this.docIteratorHasMatchAll(null)) {
			
			int commonDocId = this.args.get(0).docIteratorGetMatch();
			int prevArgLocId, currArgLocId; 
			
			q0_postings = ((QryIop)this.args.get(0)).docIteratorGetMatchPosting().positions;
			
			int minLocId = Integer.MAX_VALUE; 
			int maxLocId = -1; 
			boolean match = false; 
			
			for(int i = 1; i < this.args.size(); i++) {
				
				QryIop q_i = (QryIop) this.args.get(i);
				
				qi_postings = q_i.docIteratorGetMatchPosting().positions;
				
				Vector<Integer> temp = new Vector<Integer>();
				
				prevArgLocId = currArgLocId = 0; 
				
				int q0_postingsSize = q0_postings.size();
				int qi_postingsSize = qi_postings.size();
				
				while(prevArgLocId < q0_postingsSize && 
						currArgLocId < qi_postingsSize) {
					
					minLocId = Math.min(q0_postings.get(prevArgLocId), qi_postings.get(currArgLocId));
					
					maxLocId = Math.max(q0_postings.get(prevArgLocId), qi_postings.get(currArgLocId));
					
					int docIdDifference = maxLocId - minLocId; 
					
					if(docIdDifference >= this.operatorDistance) {
						
						if(minLocId == q0_postings.get(prevArgLocId))
							prevArgLocId++; 
						else 
							currArgLocId++; 
						
					} else if (docIdDifference < this.operatorDistance) {
						
						temp.add(maxLocId);
						prevArgLocId++;
						currArgLocId++;
						match = true;
							
					}

				}// end of inner while loop 
				
				q0_postings = temp; 
				
			} // end of for loop
			
			if(match && q0_postings.size() > 0) {
				this.invertedList.appendPosting(commonDocId, q0_postings);
			}
			
			this.args.get(0).docIteratorAdvancePast(commonDocId);
 			
		}// end of outer while loop 
		
	}

}
