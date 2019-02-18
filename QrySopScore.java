/**
 *  Copyright (c) 2019, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.lang.IllegalArgumentException;

/**
 *  The SCORE operator for all retrieval models.
 */
public class QrySopScore extends QrySop {

  /**
   *  Document-independent values that should be determined just once.
   *  Some retrieval models have these, some don't.
   */
  
  /**
   *  Indicates whether the query has a match.
   *  @param r The retrieval model that determines what is a match
   *  @return True if the query matches, otherwise false.
   */
  public boolean docIteratorHasMatch (RetrievalModel r) {
    return this.docIteratorHasMatchFirst (r);
  }

  /**
   *  Get a score for the document that docIteratorHasMatch matched.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScore (RetrievalModel r) throws IOException {

    if (r instanceof RetrievalModelUnrankedBoolean) {
      return this.getScoreUnrankedBoolean (r);
    } else if (r instanceof RetrievalModelRankedBoolean) {
    	return this.getScoreRankedBoolean (r);
    } else if (r instanceof RetrievalModelBM25) {
    	return this.getScoreBM25 (r);
    } else if (r instanceof RetrievalModelIndri) {
    	return this.getScoreIndri (r);
    }
    else {
      throw new IllegalArgumentException
        (r.getClass().getName() + " doesn't support the SCORE operator.");
    }
  }
  
  public double getScoreRankedBoolean (RetrievalModel r) throws IOException {
	  
	  if(! this.docIteratorHasMatchCache()) {
		  return 0.0; 
	  } else { 
		  
		  // return the term frequency as the score 
		  Qry q_i = this.args.get(0);
		  
		  double tf = ((QryIop)q_i).docIteratorGetMatchPosting().tf;
		  return tf;
	  }
  }
  
  /**
   *  getScore for the Unranked retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScoreUnrankedBoolean (RetrievalModel r) throws IOException {
    if (! this.docIteratorHasMatchCache()) {
      return 0.0;
    } else {
      return 1.0;
    }
  }
  
  public double getScoreBM25 (RetrievalModel r) throws IOException {
	  
	  if (! this.docIteratorHasMatchCache()) {
	      return 0.0;
	    } else {
	    	
	    	QryIop q_0 = (QryIop) this.args.get(0);

	    	long N = Idx.getNumDocs();
	    	int docFreq = q_0.getDf();
	    	
	    	double RobIdf = Math.max(0, Math.log((N - docFreq + 0.5) / (docFreq + 0.5)));
	    	
	    	double k_1 = ((RetrievalModelBM25) r).getK_1();
	    	double k_3 = ((RetrievalModelBM25) r).getK_3();
	    	double b = ((RetrievalModelBM25) r).getB();
	    	
	    	int docId = q_0.docIteratorGetMatch();
	    	
	    	int docLen = Idx.getFieldLength(q_0.getField(), docId);

	    	double avgDocLen = Idx.getSumOfFieldLengths(q_0.getField()) 
	    			/ (double)Idx.getDocCount(q_0.getField());
	    	
	    	int termFreq = q_0.docIteratorGetMatchPosting().tf;
	    	
	    	double termFreqWeight = 
	    			termFreq / 
	    			(termFreq + (k_1 * ((1 - b) + (b * docLen / avgDocLen))));
	    	
	    	double qtf = 1; 
	    	double userWeight = (k_3 + 1) * qtf / (k_3 + qtf);
	    	
	    	return RobIdf * termFreqWeight * userWeight;
	    	
	    }
  }
  
  public double getScoreIndri (RetrievalModel r) throws IOException {
	  
	  if (! this.docIteratorHasMatchCache()) {
	      return 0.0;
	    } else {
	    	
	    	int mu = ((RetrievalModelIndri) r).getMu();
	    	double lambda = ((RetrievalModelIndri) r).getLambda();
	    	
	    	QryIop q = (QryIop)this.args.get(0); 
	    	
	    	String fieldName = q.getField();
	    	
	    	double ctf = q.getCtf(); 
	    	double tf = q.docIteratorGetMatchPosting().tf;
	    	
	    	double collectLength = Idx.getSumOfFieldLengths(fieldName);

	    	double pMLE = ctf / collectLength;
	    	
	    	double documentLength = Idx.getFieldLength(fieldName, q.docIteratorGetMatch());
	    	
	    	double pqd = ((1 - lambda) * (tf + mu * pMLE) / (documentLength + mu)) + (lambda * pMLE);
	    	return pqd;
	    }
  }

  /**
   *  Initialize the query operator (and its arguments), including any
   *  internal iterators.  If the query operator is of type QryIop, it
   *  is fully evaluated, and the results are stored in an internal
   *  inverted list that may be accessed via the internal iterator.
   *  @param r A retrieval model that guides initialization
   *  @throws IOException Error accessing the Lucene index.
   */
  public void initialize (RetrievalModel r) throws IOException {

    Qry q = this.args.get (0);
    q.initialize (r);
  }

@Override
public double getDefaultScore(RetrievalModel r, int DocID) throws IOException {
	
	int mu = ((RetrievalModelIndri) r).getMu();
	double lambda = ((RetrievalModelIndri) r).getLambda();
	
	QryIop q = (QryIop)this.args.get(0); 
	
	String fieldName = q.getField();
	
	double ctf; 
	
	if(q.getCtf() == 0) 
		ctf = 0.5;
	else 
		ctf = q.getCtf();
		
	double collectLength = Idx.getSumOfFieldLengths(fieldName);

	double pMLE = ctf / collectLength;
	
	double documentLength = Idx.getFieldLength(fieldName, DocID);
	
	double pqd = ((1 - lambda) * (mu * pMLE) / (documentLength + mu)) + (lambda * pMLE);
	return pqd;
	
}

}
