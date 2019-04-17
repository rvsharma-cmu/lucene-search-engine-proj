import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Diversification {
	private static final double negInfinity = Double.MIN_VALUE;
	private static final double totalScore = 0.9;
	Map<String, String> parameters;
	Map<Integer, Integer> rankList;
	ArrayList<ArrayList<Double>> scaledScore;

	public Diversification(Map<String, String> parameters, RetrievalModel model) {
		this.parameters = parameters;
		
	}

	public ScoreList diversifyxQuAD(ScoreList originalScoreList) {

		scaledScore = new ArrayList<ArrayList<Double>>();
		normalizeScores(originalScoreList);
		 
		rankList = new HashMap<Integer, Integer>();
		generateRanks(originalScoreList);
		

		ArrayList<Double> initialScaleList = scaledScore.get(0);
		

		HashSet<Integer> initialRanking = createInitialRanking(scaledScore);

		String stringmaxOutputLength = parameters.get("diversity:maxResultRankingLength");
		int maxOutputLength = Integer.parseInt(stringmaxOutputLength);
		double divLambda = Double.parseDouble(parameters.get("diversity:lambda"));
		int divRankLen = Math.min(maxOutputLength, initialRanking.size());
		
		int size = initialScaleList.size();
		int intents = size - 1;
		List<Double> initCoverList = initCoverList(intents);
		List<Double> currentCover = new ArrayList<>(initCoverList);
		
		ScoreList diversifiedRanking = new ScoreList();

		while (diversifiedRanking.size() < divRankLen) {
			double maxScoreValue = negInfinity;
			int maxScoreDocId = -1;
			double divScore;
			for (int idx : initialRanking) {

				divScore = 0.0;

				List<Double> relevanceScore = scaledScore.get(idx);
				int x = 1; 
				while (x <= intents) {
					divScore += relevanceScore.get(x) * currentCover.get(x);
					x++; 
				}
				divScore = divScore / (double) intents;
				double selectionValue = (1 - divLambda) * relevanceScore.get(0) + divLambda * divScore;

				if (selectionValue > maxScoreValue) {
					maxScoreDocId = idx;
					maxScoreValue = selectionValue;
				}
			}

			initialRanking.remove(maxScoreDocId);
			Integer highestScoringDoc = rankList.get(maxScoreDocId);
			diversifiedRanking.add(highestScoringDoc, maxScoreValue);
			
			int index = 0; 
			while (index <= intents) {
				List<Double> tempList = scaledScore.get(maxScoreDocId);
				Double originalScore = tempList.get(index);
				Double newScore = 1 - originalScore; 
				Double replace = currentCover.get(index) * newScore;
				currentCover.set(index, replace);
				index++;
			}
		}

		diversifiedRanking.sort();
		return diversifiedRanking;

	}

	/**
	 * @param scaledScore
	 * @return
	 */
	public HashSet<Integer> createInitialRanking(ArrayList<ArrayList<Double>> scaledScore) {
		HashSet<Integer> rankingMap = new HashSet<>();
		for (int i = 0; i < scaledScore.size(); i++)
			rankingMap.add(i);
		return rankingMap;
	}

	/**
	 * @param intents
	 * @return
	 */
	private List<Double> initCoverList(int intents) {

		Double[] arr = new Double[intents + 1];

		for (int i = 0; i < intents + 1; i++)
			arr[i] = 1.0;
		List<Double> list = new ArrayList<Double>();
		list = Arrays.asList(arr);
		return list;
	}

	public ScoreList diversifyPM2(ScoreList originalScoreList) {
		scaledScore = new ArrayList<ArrayList<Double>>();
		normalizeScores(originalScoreList);
		
		rankList = new HashMap<Integer, Integer>();
		
		generateRanks(originalScoreList);

		ArrayList<Double> initialScaleList = scaledScore.get(0);
		int size = initialScaleList.size();
		int intents = size - 1;
		HashSet<Integer> rankingMap = createInitialRanking(scaledScore);
		
		String stringmaxOutputLength = parameters.get("diversity:maxResultRankingLength");
		int maxOutputLength = Integer.parseInt(stringmaxOutputLength);

		int divRankSize = Math.min(maxOutputLength, rankingMap.size());
		double desiredRanks = divRankSize * 1.0 / intents;

		List<Double> initCoverageList = initCoverageList(intents);
		ArrayList<Double> currentCover = new ArrayList<>(initCoverageList);

		ScoreList diversifiedRanking = new ScoreList();
		while (diversifiedRanking.size() < divRankSize) {

			int target = -1;
			double intentToCover = -1.0;

			HashMap<Integer, Double> quotients = new HashMap<>();
			
			for (int i = 1; i <= intents; i++) {
				double currQuotient = desiredRanks / (2 * currentCover.get(i) + 1);
				quotients.put(i, currQuotient);

				if (intentToCover < currQuotient) {
					intentToCover = currQuotient;
					target = i;
				}
			}

			int maxScoreId = -1;
			double maximumValue = negInfinity;

			for (int idx : rankingMap) {
				ArrayList<Double> relevanceScore = scaledScore.get(idx);
				double intentedTargetVal = intentToCover * relevanceScore.get(target);

				double otherIntents = 0.0;

				for (int j = 1; j <= intents; j++) {
					if (j == target)
						continue;
					otherIntents += relevanceScore.get(j) * quotients.get(j);

				}
				double diversityLambda = Double.parseDouble(parameters.get("diversity:lambda"));

				double selectionValue = (diversityLambda * intentedTargetVal) + ((1 - diversityLambda) * otherIntents);

				if (maximumValue < selectionValue) {
					maximumValue = selectionValue;
					maxScoreId = idx;
				}
			}

			if (maximumValue == 0) {

				for (int i = 0; i < scaledScore.size() && diversifiedRanking.size() < divRankSize; i++) {
					if (rankingMap.contains(i)) {
						double docidScore = diversifiedRanking.getDocidScore(diversifiedRanking.size() - 1);
						double s = docidScore * totalScore;
						diversifiedRanking.add(rankList.get(i), s);
						rankingMap.remove(i);
					}
				}
				return diversifiedRanking;
			}

			rankingMap.remove(maxScoreId);
			if (rankList == null || diversifiedRanking == null) {
				System.out.println("docidatrank or r is null");
			}

			diversifiedRanking.add(rankList.get(maxScoreId), maximumValue);
			ArrayList<Double> maxQryScores = scaledScore.get(maxScoreId);
			double sum = 0.0;
			for (int i = 1; i <= intents; i++)
				sum += maxQryScores.get(i);
			for (int j = 1; j <= intents; j++) {
				double updateCoverScore = currentCover.get(j) + maxQryScores.get(j) / sum;
				currentCover.set(j, updateCoverScore);
			}
		}
		diversifiedRanking.sort();
		return diversifiedRanking;
	}

	/**
	 * @param originalScoreList
	 * @return
	 */
	public void generateRanks(ScoreList originalScoreList) {

		for (int i = 0; i < originalScoreList.size(); i++) {
			int docid = originalScoreList.getDocid(i);
			rankList.put(i, docid);
		}
	}

	/**
	 * @param originalScoreList
	 * @param score
	 * @return
	 */
	public void normalizeScores(ScoreList originalScoreList) {
		
		int size = originalScoreList.size();
		
		HashMap<Integer, Integer> rankOfDocId = new HashMap<>();
		boolean needScale = false;

		int maxRankingInput = Integer.parseInt(parameters.get("diversity:maxInputRankingsLength"));

		int maxInputRankingsLength = Math.min(maxRankingInput, size);

		int intentsSize = QryEval.score.size();
		List<Double> initCoverageList = initCoverageList(intentsSize);
		
		if(! truncateAndScale(originalScoreList, rankOfDocId, needScale, maxInputRankingsLength,
				initCoverageList))
			return;

		ArrayList<Double> sumOfScores = new ArrayList<>(initCoverageList);

		for (int i4 = 0; i4 < scaledScore.size(); i4++) {
			ArrayList<Double> qryEntry = scaledScore.get(i4);
			for (int j = 0; j < intentsSize + 1; j++)
				sumOfScores.set(j, sumOfScores.get(j) + qryEntry.get(j));
		}

		double scalar = Collections.max(sumOfScores);

		for (int i3 = 0; i3 < scaledScore.size(); i3++) {
			for (int j = 0; j < intentsSize + 1; j++) {
				scaledScore.get(i3).set(j, scaledScore.get(i3).get(j) / scalar);
			}
		}
	
	}

	/**
	 * @param originalScoreList
	 * @param rankOfDocId
	 * @param needScale
	 * @param maxInputRankingsLength
	 * @param initCoverageList
	 * @return
	 */
	public boolean truncateAndScale(ScoreList originalScoreList, HashMap<Integer, Integer> rankOfDocId,
			boolean needScale, int maxInputRankingsLength, List<Double> initCoverageList) {
		for (int i = 0; i < maxInputRankingsLength; i++) {
			
			double docidScore = originalScoreList.getDocidScore(i);
			if (docidScore > 1)
				needScale = true;

			int originalDocId = originalScoreList.getDocid(i);
			
			rankOfDocId.put(originalDocId, i);
			ArrayList<Double> arr = new ArrayList<>(initCoverageList);

			arr.set(0, docidScore);
			scaledScore.add(arr);
		}
		Set<String> allIntents = new HashSet<String>();

		allIntents = QryEval.score.keySet();
		for (String strs : allIntents) {
			ScoreList intentScoreList = QryEval.score.get(strs);
			int maximumConsiderLen = Math.min(maxInputRankingsLength, intentScoreList.size());
			for (int j = 0; j < maximumConsiderLen; j++) {

				int docid = intentScoreList.getDocid(j);
				if (! rankOfDocId.containsKey(docid))
					continue;

				int index = rankOfDocId.get(docid);
				double s = intentScoreList.getDocidScore(j);
				if (s > 1.0)
					needScale = true;
				ArrayList<Double> tempList = scaledScore.get(index);
				int intentId = Integer.parseInt(strs);
				tempList.set(intentId, s);
			
			}
		}
		return needScale;
	}

	/**
	 * @param originalScoreList
	 * @param needScale
	 * @param i
	 * @return
	 */
	public boolean needScaling(ScoreList originalScoreList, int i) {
		boolean needScale = false; 
		if (originalScoreList.getDocidScore(i) > 1)
			needScale = true;
		return needScale;
	}

	/**
	 * @param intents
	 * @return
	 */
	public List<Double> initCoverageList(int intents) {

		Double[] arr = new Double[intents + 1];

		for (int i = 0; i < intents + 1; i++) {
			arr[i] = 0.0;
		}

		List<Double> list = new ArrayList<Double>();
		list = Arrays.asList(arr);
		return list;
	}

}