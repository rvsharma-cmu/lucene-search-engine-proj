import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/** 
 * Class for Query expansion 
 * 
 *
 */
public class QryExp {

	private int fbDocs; 
	private int fbTerms; 
	private int fbMu; 
	private double fbOrigWeight; 
	private String initFilePath;
	private String fbExpansionQueryFile; 
	public boolean hasInitfile; 
	private Map<String, Double> scoreOfTerms;
	private Map<String, mleDoc> termInfo;
	String query;
	private String learnedQuery; 
	
	public QryExp(Map<String, String> parameters, String query) {
		
		if(!(parameters.containsKey("fbMu") && parameters.containsKey("fbOrigWeight") &&
				parameters.containsKey("fbDocs") && parameters.containsKey("fbTerms"))) {
			throw new IllegalArgumentException("Parameters missing for Query Expansion");
		}
		
		fbDocs = Integer.parseInt(parameters.get("fbDocs"));
		fbTerms = Integer.parseInt(parameters.get("fbTerms"));
		fbMu = Integer.parseInt(parameters.get("fbMu"));
		fbOrigWeight = Double.parseDouble(parameters.get("fbOrigWeight"));
		fbExpansionQueryFile = parameters.get("fbExpansionQueryFile"); 
		hasInitfile = parameters.containsKey("fbInitialRankingFile"); 
		if(hasInitfile)
			initFilePath = parameters.get("fbInitialRankingFile");
		scoreOfTerms = new HashMap<>();
		new HashMap<>();
		new HashMap<>();
		termInfo = new HashMap<>();
		this.query = query; 
	}
	
	public class TermScoreComparator implements Comparator<Map.Entry<String, Double>> {
		
		@Override
		public int compare(Map.Entry<String, Double> s1, Map.Entry<String, Double> s2) {
			return s2.getValue().compareTo(s1.getValue());
		}
	}
	
	public class mleDoc {
		
		double mle;
		List<Integer> docs;
		
		public mleDoc(double MLE_, List<Integer> docs_) {
			this.mle = MLE_;
			this.docs = docs_;
		}
	}
	
	public String expandQuery(ScoreList r, RetrievalModel model) throws IOException {
		
		int docsLength = Math.min(fbDocs, r.size()); 
		double collectionLength = Idx.getSumOfFieldLengths("body");
		
		for(int index = 0; index < docsLength; index++) {
			
			getScore(r, collectionLength, index);
		}
		
		updateScore(r, docsLength);
		
		PriorityQueue<Map.Entry<String, Double>> pq = 
				new PriorityQueue<Map.Entry<String, Double>>(new TermScoreComparator());
		
		pq.addAll(scoreOfTerms.entrySet());
		
		String expandedQuery = buildExpandedQuery(pq);
		
		return expandedQuery; 
	}

	public void getScore(ScoreList r, double collectionLength, int index) throws IOException {
		int documentId = r.getDocid(index);
		TermVector termVector = new TermVector(documentId, "body"); 
		double score = r.getDocidScore(index);
		
		for(int i = 1; i < termVector.stemsLength(); i++) {
			
			String term = termVector.stemString(i);
			
			if(term.contains(".") || term.contains(","))
				continue; 
			
			double pMLE = termVector.totalStemFreq(i) / collectionLength; 
			double tf = termVector.stemFreq(i);
			
			double docLength = Idx.getFieldLength("body", documentId);
			double ptd = (tf + fbMu * pMLE) / 
					(docLength + fbMu);
			
			double idf = Math.log(1 / pMLE);
			
			double currentScore = ptd * score * idf;

			if(scoreOfTerms.containsKey(term)) {
				scoreOfTerms.put(term, scoreOfTerms.get(term) + currentScore);
			} else {
				scoreOfTerms.put(term, currentScore);
			}
			
			if(termInfo.containsKey(term)) {
				
				termInfo.get(term).docs.add(documentId);
				termInfo.put(term, termInfo.get(term));
				
			} else {
				List<Integer> temp = new ArrayList<Integer>();
				temp.add(documentId);
				mleDoc MLEDoc = new mleDoc(pMLE, temp);
				termInfo.put(term, MLEDoc);
			}
		}
	}

	public void updateScore(ScoreList r, int docsLength) throws IOException {
		Set<String> termSet = termInfo.keySet();
		
		for(String t : termSet) {
			
			mleDoc termList = termInfo.get(t);
			
			for(int i = 0; i < docsLength; i++) {
				
				int docId = r.getDocid(i);
				
				if(termList.docs.contains(docId))
					continue;
				
				double score = r.getDocidScore(i);
				double MLE = termList.mle;
				
				double docLength = Idx.getFieldLength("body", docId);
				double ptd = fbMu *  MLE / (docLength + fbMu);
				
				double defaultScore = ptd * score * Math.log(1 / MLE);
				
				if(scoreOfTerms.containsKey(t)) {
					double value = scoreOfTerms.get(t) + defaultScore;
					scoreOfTerms.put(t, value);
				} else {
					scoreOfTerms.put(t, defaultScore);
				}
			}
		}
	}

	public ScoreList readTeInFile(String qid) throws IOException, FileNotFoundException{
		
		BufferedReader input = new BufferedReader(new FileReader(initFilePath));
		String lastId = null;
		String line = lastId; 
		ScoreList result = new ScoreList(); 
		
		while((line = input.readLine()) != null) {
			
			String[] strings = line.split(" ");
			String currentId = strings[0].trim();
			
			if(!currentId.equals(qid)) {
				
				if(lastId != null && lastId.equals(qid)) 
					break;
				else 
					continue;
			}
			
			lastId = currentId;
			
			try {
				result.add(Idx.getInternalDocid(strings[2]), 
						Double.parseDouble(strings[4]));
			} catch (Exception e) {
				e.printStackTrace();
			}	
		}
		input.close();
		return result;
	}

	public void printExpandedQuery(String qid) throws IOException {
		
		FileWriter writeExpQuery = new FileWriter(fbExpansionQueryFile, true);
		
		writeExpQuery.write(qid + ": " + learnedQuery + "\n");
		writeExpQuery.close();
	}
	
	public String buildExpandedQuery(PriorityQueue<Map.Entry<String, Double>> pq) {
		StringBuilder expandedString = new StringBuilder(); 
		
		while(fbTerms > 0 && pq.size()> 0) {
			
			String currTerm = pq.peek().getKey();
			
			expandedString.append(String.format("%.4f %s ", pq.poll().getValue(), currTerm));
			
			fbTerms--;
		}
		
		learnedQuery = String.format("#wand (%s)", expandedString.toString());
		
		if(query.trim().charAt(0) != '#') {
			query = "#and (" + query + ")";
		}
		
		String expandedQuery; 
		
		expandedQuery = "#wand(" + String.format("%.4f ", fbOrigWeight) + query 
				+ String.format(" %.4f ", 1-fbOrigWeight) + learnedQuery + ")";
		return expandedQuery;
	}
	
}
