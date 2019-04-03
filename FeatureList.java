import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FeatureList {

	private Map<Integer, Double> features;
	private int InternalDocId;
	private int relevJudge;
	private String qid;
	private String query;
	private Map<String, String> parameters;
	private static List<Integer> dis;
	private double lambda, mu, k_1, b;
	String[] queryTokens;

	public FeatureList(int relev, int docId, String qId, String query, Map<String, String> params)
			throws IllegalArgumentException, IOException {

		parameters = params;

		try {
			InternalDocId = docId;
		} catch (Exception e) {
			e.printStackTrace();
		}
		relevJudge = relev;

		qid = qId;
		this.query = query;
		queryTokens = QryParser.tokenizeString(this.query);
		dis = new ArrayList<Integer>();
		features = new HashMap<Integer, Double>();
		k_1 = Double.parseDouble(parameters.get("BM25:k_1"));
		b = Double.parseDouble(parameters.get("BM25:b"));
		Double.parseDouble(parameters.get("BM25:k_3"));
		mu = Integer.parseInt(parameters.get("Indri:mu"));
		lambda = Double.parseDouble(parameters.get("Indri:lambda"));

		removeDisabledFeatures();
		calculateFeatureVectors();

	}

	private void removeDisabledFeatures() {

		String disabled = parameters.get("letor:featureDisable");

		if (disabled == null) {
			dis.add(-1);
			return;
		}

		String[] disabledValues = disabled.split(",");

		for (String disable : disabledValues) {
			dis.add(Integer.parseInt(disable.trim()));
		}

	}

	private void calculateFeatureVectors() throws NumberFormatException, IOException {

		features.put(1, Double.parseDouble(Idx.getAttribute("spamScore", InternalDocId)));

		String url = Idx.getAttribute("rawUrl", InternalDocId);

		int count = 0;
		for (int i = 0; i < url.length(); i++) {
			if (url.charAt(i) == '/')
				count++;
		}
		features.put(2, (double) count);

		if (url.contains("wikipedia.org"))
			features.put(3, 1.0);
		else
			features.put(3, 0.0);

		features.put(4, Double.parseDouble(Idx.getAttribute("PageRank", InternalDocId)));

		double score = getBM25score("body");
		features.put(5, score);

		// System.out.print(score + " ");
		features.put(6, getIndriscore("body"));

		features.put(7, overlap("body"));

		features.put(8, getBM25score("title"));

		features.put(9, getIndriscore("title"));

		features.put(10, overlap("title"));

		features.put(11, getBM25score("url"));

		features.put(12, getIndriscore("url"));

		features.put(13, overlap("url"));

		features.put(14, getBM25score("inlink"));

		features.put(15, getIndriscore("inlink"));

		features.put(16, overlap("inlink"));

		// System.out.println();
		// query term density

		TermVector termVector = new TermVector(InternalDocId, "body");
		double count1;
		if (termVector.stemsLength() == 0)
			count1 = 0.0;
		else {
			count1 = 0.0;
			for (String t : queryTokens) {

				int indexOfStem = termVector.indexOfStem(t);
				if (indexOfStem != -1) {
					count1 += termVector.stemFreq(indexOfStem);

				}
			}
		}
		// features.put(17, count);
		features.put(17, 0.0);

		TermVector termVector2 = new TermVector(InternalDocId, "body");
		double count2;
		if (termVector2.stemsLength() == 0)
			count2 = 0.0;
		else {
			count2 = 0.0;
			for (String t : queryTokens) {

				int indexOfStem = termVector2.indexOfStem(t);
				if (indexOfStem != -1) {
					count2 += termVector2.stemDf(indexOfStem);

				}
			}
		}
		// features.put(18, count/(double)queryTokens.length);
		features.put(18, 0.0);

	}

	public double getBM25score(String field) throws IOException {
		TermVector termVector = new TermVector(InternalDocId, field);

		if (termVector.stemsLength() == 0)
			return Double.MIN_VALUE;

		double score = 0.0;

		for (String t : queryTokens) {
			int index = termVector.indexOfStem(t);

			if (index == -1)
				continue;
			long N = Idx.getNumDocs();
			double docFreq = termVector.stemDf(index);
			double RobIdf = Math.max(0, Math.log((N - docFreq + 0.5) / (docFreq + 0.5)));

			double termFreq = termVector.stemFreq(index);

			double avgDocLen = Idx.getSumOfFieldLengths(field) / (double) Idx.getDocCount(field);

			double termFreqWeight = termFreq
					/ (termFreq + (k_1 * ((1 - b) + (b * Idx.getFieldLength(field, InternalDocId) / avgDocLen))));

			score += RobIdf * termFreqWeight;
		}
		return score;
	}

	public double getIndriscore(String field) throws IOException {

		double score = 1.0;
		TermVector termVector = new TermVector(InternalDocId, field);

		if (termVector.stemsLength() == 0)
			return Double.MIN_VALUE;

		boolean notPresent = false;

		for (String t : queryTokens) {

			Double tf;
			double ctf = Idx.getTotalTermFreq(field, t);
			double collectLength = Idx.getSumOfFieldLengths(field);
			double pMLE = ctf / collectLength;

			double documentLength = Idx.getFieldLength(field, InternalDocId);

			if (termVector.indexOfStem(t) == -1) {
				tf = 0.0;
			} else {
				notPresent = true;
				tf = (double) termVector.stemFreq(termVector.indexOfStem(t));
			}

			score *= ((1 - lambda) * (tf + mu * pMLE) / (documentLength + mu)) + (lambda * pMLE);
		}

		if (!notPresent)
			return 0.0;
		else
			return Math.pow(score, 1 / (double) queryTokens.length);
	}

	public double overlap(String field) throws IOException {

		double score = 0.0;
		TermVector termVector = new TermVector(InternalDocId, field);

		if (termVector.stemsLength() == 0)
			return Double.MIN_VALUE;

		for (String t : queryTokens) {
			if (termVector.indexOfStem(t) == -1)
				continue;
			else
				score++;
		}

		return score / queryTokens.length;
	}

//	public static void normalizePrint(List<FeatureList> featureList, String fileName) throws IOException {
//		
//		Map<Integer, Double> min = new HashMap<>(); 
//		Map<Integer, Double> max = new HashMap<>();
//		
//		PrintWriter printWriter = new PrintWriter(new FileWriter(fileName));
//		
//		for(int i = 1; i <= 18; i++) {
//			
//			if(dis.contains(i))
//				continue; 
//			min.put(i, Double.MAX_VALUE);
//			max.put(i, Double.MIN_VALUE);
//		}
//		
//		for(FeatureList fv : featureList) {
//			
//			HashMap<Integer, Double> feat = (HashMap<Integer, Double>) fv.features;
//			
//			if(feat != null) {
//				for(int i : feat.keySet()) {
//					double value = feat.get(i);
//					
//					if(value == -1)
//						continue; 
//					min.put(i, Math.min(min.get(i), value));
//					max.put(i, Math.min(max.get(i), value));
//				}
//			}
//		}
//		
//		HashMap<Integer, Double> diff = new HashMap<>();
//		for(int i = 1; i <= 18; i++) {
//			if(dis.contains(i))
//				continue; 
//			diff.put(i, max.get(i) - min.get(i));
//		}
//		
//		for(FeatureList fv : featureList) {
//			
//			HashMap<Integer, Double> feat = (HashMap<Integer, Double>) fv.features;
//			
//			if(feat != null) {
//				
//				String externalDocId = Idx.getExternalDocid(InternalDocId);
//				String out = String.format("%s qid:%s ", relevJudge+"", qid);
//				for(int i : feat.keySet()) {
//					double value = feat.get(i);
//					double difference = diff.get(i);
//					if(value != -1 && difference != 0)
//						out += String.format("%d:%f ", i, (value - min.get(i))/difference);
//					else 
//						out += String.format("%d:%f ", i, 0.0);
//				}
//				out += String.format("# %s", externalDocId);
//				printWriter.println(out);
//			}
//		}
//		printWriter.close();
//		
//		System.out.println("came here " + fileName);
//	}

	public static void normalize(List<FeatureList> featureVecList) {

		for (int i = 0; i < 18; i++) {

			if (dis.contains(i + 1))
				continue;
//			if(i == 2)
//				continue; 

			double min = Double.POSITIVE_INFINITY;
			double max = Double.NEGATIVE_INFINITY;

			for (FeatureList fl : featureVecList) {

				if (fl.features.get(i + 1) == Double.MIN_VALUE)
					continue;

				min = Math.min(min, fl.features.get(i + 1));
				max = Math.max(max, fl.features.get(i + 1));
			}
			boolean noFeature = false;
			if (max == min)
				noFeature = true;

			double norm = max - min;

			for (FeatureList fl : featureVecList) {
				if (fl.features.get(i + 1) == Double.MIN_VALUE)
					fl.features.put(i + 1, 0.0);
				else {
					if (noFeature) {
						fl.features.put(i + 1, 0.0);
					} else
						fl.features.put(i + 1, (fl.features.get(i + 1) - min) / norm);
				}
			}
		}
	}

	public static void printScore(List<FeatureList> featureVecList, String fileName, boolean append)
			throws IOException {
		Writer writer = null;
		try {
			writer = new FileWriter(new File(fileName), append);
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("File not found in printScore() in FeaatureList class");
			return;
		}

		for (FeatureList fl : featureVecList) {
			String out = "";

			out += String.format("%d qid:%s ", fl.relevJudge, fl.qid);
			for (int i = 0; i < fl.features.size(); i++) {

				if (dis.contains(i + 1))
					continue;

				out += String.format("%d:%s ", i + 1, fl.features.get(i + 1));
			}
			out += "# " + Idx.getExternalDocid(fl.InternalDocId) + "\n";
			writer.write(out);
		}
		writer.close();
	}

}
