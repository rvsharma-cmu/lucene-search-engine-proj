import java.io.IOException;

public class QrySopAnd extends QrySop{
	

	@Override
	public double getScore(RetrievalModel r) throws IOException {
		
		if(r instanceof RetrievalModelUnrankedBoolean) {	// unranked boolean
			return this.getScoreUnrankedBoolean(r);
		} else if (r instanceof RetrievalModelRankedBoolean) {	// ranked boolean
			return this.getScoreRankedBoolean(r);
		} else if (r instanceof RetrievalModelIndri) {	// Indri
			return this.getScoreIndri(r);
		} else {
			throw new IllegalArgumentException
			(r.getClass().getName() + " doesn't support the AND operator.");
		}
	}

	private double getScoreIndri(RetrievalModel r) throws IOException {
		
		if(!(this.docIteratorHasMatch(r)))
			return 0.0; 
		else {
			
			int docId = this.docIteratorGetMatch();
			
			double result = 1.0; 
			double defaultWeight = 1.0; 
			
			for(Qry qry : this.args) {
				
				
				if (((QrySop)qry).docIteratorHasMatch(r) && 
						((QrySop)qry).docIteratorGetMatch() == docId) {

					result *= ((QrySop)qry).getScore(r);
					
				} else {

					result *= ((QrySop)qry).getDefaultScore(r, docId);
				}

			}
		
			double exp = defaultWeight / this.args.size();
			return Math.pow(result, exp);
		}
	}

	@Override
	public boolean docIteratorHasMatch(RetrievalModel r) {
		
		if (r instanceof RetrievalModelIndri) {
			return this.docIteratorHasMatchMin(r);
		} else {
		
			if(this.docIteratorHasMatchAll(r))
				return true;
			else 
				return false;
		}
	}
	
	public double getScoreUnrankedBoolean(RetrievalModel r) throws IOException{
		
		if(this.docIteratorHasMatch(r))
			return 1.0; 
		else 
			return 0.0; 
	}
	
	public double getScoreRankedBoolean(RetrievalModel r) throws IOException{
		
		if(!this.docIteratorHasMatch(r))
			return 0.0; 
		else { 
			
			double minDocumentScore = Double.MAX_VALUE;
			
			for(Qry q_i : this.args) {
				
				double nextScore = ((QrySop)q_i).getScore(r);
				
				if(nextScore < minDocumentScore) {
					minDocumentScore = nextScore; 
				}
				
			}
			return minDocumentScore; 
		}
	}

	@Override
	public double getDefaultScore(RetrievalModel r, int DocID) throws IOException {
		
		double result = 1.0; 
		double defaultOpWeight = 1.0; 
		
		for(Qry qry : this.args) {
			
			result *= ((QrySop)qry).getDefaultScore(r, DocID);
		}
		double exponent = defaultOpWeight / this.args.size();
		return Math.pow(result, exponent);
	}

}
