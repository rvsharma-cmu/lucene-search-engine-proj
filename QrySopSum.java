
import java.io.*;
import java.lang.IllegalArgumentException;

public class QrySopSum extends QrySop {

	@Override
	public double getScore(RetrievalModel r) throws IOException {
		
		if( ! (r instanceof RetrievalModelBM25))
			throw new IllegalArgumentException(r.getClass().getName() + " does not support"
					+ "SUM operator.");
		
		if(! (this.docIteratorHasMatchCache()))
			return 0.0;  
		
		else { 
			
			double score = 0; 
			int docId = this.docIteratorGetMatch();
			
			for(int i = 0; i < this.args.size(); i++) {
				//QryIop q_i = (QryIop) this.args.get(i);
				
				if(this.args.get(i).docIteratorHasMatchCache()) {
					if(docId == this.args.get(i).docIteratorGetMatch())
						score += ((QrySop)this.args.get(i)).getScore(r);
				}
			}
			return score;
		}
	}

	@Override
	public boolean docIteratorHasMatch(RetrievalModel r) {

		return this.docIteratorHasMatchMin(r);
	}

	@Override
	public double getDefaultScore(RetrievalModel r, int DocID) throws IOException {
		// TODO Auto-generated method stub
		return 0;
	}

}
