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
	Map<String, Double> scoreOfTerms;
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
		new HashMap<>();
		hasInitfile = parameters.containsKey("fbInitialRankingFile"); 
		if(hasInitfile)
			initFilePath = parameters.get("fbInitialRankingFile");
		scoreOfTerms = new HashMap<>();
		this.query = query; 
		
	}
	
	public class StringComparator implements Comparator<String> {
		
		@Override
		public int compare(String s1, String s2) {
			return scoreOfTerms.get(s2).compareTo(scoreOfTerms.get(s1));
		}
	}
	
	
	public String expandQuery(ScoreList r, RetrievalModel model) throws IOException {
		
		int docsLength = Math.min(fbDocs, r.size()); 

		Map<String, List<Integer>> termToDoc = new HashMap<>(); 
		Map<String, Double> mleOfTerm = new HashMap<>(); 
		
		for(int index = 0; index < docsLength; index++) {
			
			int documentId = r.getDocid(index);
			double score = r.getDocidScore(index);
			
			TermVector termVector = new TermVector(documentId, "body"); 
			
			for(int i = 1; i < termVector.stemsLength(); i++) {
				
				String term = termVector.stemString(i);
				if(term.contains(".") || term.contains(","))
					continue; 
				double collectionLength = Idx.getSumOfFieldLengths("body");
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
				
				mleOfTerm.put(term, pMLE);
				if(termToDoc.containsKey(term)) {
					termToDoc.get(term).add(documentId);
				} else {
					List<Integer> temp = new ArrayList<Integer>();
					temp.add(documentId);
					termToDoc.put(term, temp);
				}
			}
		}
		
		for(String t : scoreOfTerms.keySet()) {
			List<Integer> setOfDocs = termToDoc.get(t);
			
			for(int i = 0; i < docsLength; i++) {
				double score = r.getDocidScore(i);
				int docId = r.getDocid(i);
				if(setOfDocs.contains(docId))
					continue;
				
				double MLE = mleOfTerm.get(t);
				double docLength = Idx.getFieldLength("body", docId);
				double ptd = fbMu *  MLE / (docLength + fbMu);
				scoreOfTerms.put(t, scoreOfTerms.get(t) + ptd * score * Math.log(1 / MLE));
			}
		}
		
		PriorityQueue<String> pq = new PriorityQueue<String>(new StringComparator());
		
		for(String term : scoreOfTerms.keySet()) {
			pq.offer(term);
		}
		
		StringBuilder expandedString = new StringBuilder(); 
		
		while(fbTerms > 0 && pq.size()> 0) {
			
			String currTerm = pq.poll();
			expandedString.append(String.format("%.4f %s ", scoreOfTerms.get(currTerm), currTerm));
			fbTerms--;
		}
		
		learnedQuery = String.format("#wand (%s)", expandedString.toString());
		
		if(query.trim().charAt(0) != '#') {
			query = model.defaultQrySopName() + "(" + query + ")";
		}
		
		query = String.format("#wand(%.4f %s %.4f %s)", fbOrigWeight, query, 1-fbOrigWeight, learnedQuery);
	
		return query; 
	}
	
	public ScoreList readTeInFile(String qid) throws IOException, FileNotFoundException{
		
		int index = 0; 
		BufferedReader input = new BufferedReader(new FileReader(initFilePath));
		ScoreList result = new ScoreList(); 
		String lastId = null;
		String line = lastId; 
		
		while(index < fbDocs && (line = input.readLine()) != null) {
			
			String currentId = line.trim().substring(0, 3);
			if(!currentId.equals(""+qid)) {
				if(lastId != null && lastId.equals(""+qid)) 
					break;
				else 
					continue;
			}
			String[] strs = line.split("\\s+");
			lastId = currentId;
			String externalId = strs[2];
			double score = Double.parseDouble(strs[4]);
			try {
				result.add(Idx.getInternalDocid(externalId), score);
			} catch (Exception e) {
				e.printStackTrace();
			}
			index++;
		}
		
		input.close();
		return result;
	}

	public void printExpQuery(String qid) throws IOException {
		
		FileWriter writeExpQuery = new FileWriter(fbExpansionQueryFile, true);
		
		writeExpQuery.write(qid + ": " + learnedQuery + "\n");
		writeExpQuery.close();
	}
	
}
