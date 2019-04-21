/*submission diverse*/
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Diversification {
	private static final double negInfinity = Double.MIN_VALUE;
	Map<String, String> parameters;
	Map<Integer, Integer> rankList;
	HashSet<Integer> rankingMap; 
	private static final double totalScore = 0.9;

	public Diversification(Map<String, String> parameters, RetrievalModel model) {
		this.parameters = parameters;

	}

	public ScoreList diversifyxQuAD(ScoreList originalScoreList) {

		QryEval.scaledScore = new ArrayList<ArrayList<Double>>();
		normalizeScores(originalScoreList);

		rankList = new HashMap<Integer, Integer>();
		generateRanks(originalScoreList);

		ArrayList<Double> initialScaleList = QryEval.scaledScore.get(0);

		createInitialRanking(QryEval.scaledScore);

		String stringmaxOutputLength = parameters.get("diversity:maxResultRankingLength");
		int maxOutputLength = Integer.parseInt(stringmaxOutputLength);
		double divLambda = Double.parseDouble(parameters.get("diversity:lambda"));
		int divRankLen = Math.min(maxOutputLength, rankingMap.size());

		int size = initialScaleList.size();
		int intents = size - 1;
		List<Double> initCoverList = initCoverList(intents);
		List<Double> currentCover = new ArrayList<>(initCoverList);

		ScoreList diversifiedRanking = new ScoreList();

		while (diversifiedRanking.size() < divRankLen) {
			double maxScoreValue = negInfinity;
			int maxScoreDocId = -1;
			double divScore;
			for (int idx : rankingMap) {

				divScore = 0.0;

				List<Double> relevanceScore = QryEval.scaledScore.get(idx);
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

			rankingMap.remove(maxScoreDocId);
			if(maxScoreDocId == -1) maxScoreDocId = 0;
			int highestScoringDoc = rankList.get(maxScoreDocId);
			diversifiedRanking.add(highestScoringDoc, maxScoreValue);

			int index = 0;
			while (index <= intents) {
				List<Double> tempList = QryEval.scaledScore.get(maxScoreDocId);
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
	public void createInitialRanking(ArrayList<ArrayList<Double>> scaledScore) {
		rankingMap = new HashSet<>();
		for (int i = 0; i < scaledScore.size(); i++)
			rankingMap.add(i);
		
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

		QryEval.scaledScore = new ArrayList<ArrayList<Double>>();
		normalizeScores(originalScoreList);

		rankList = new HashMap<Integer, Integer>();

		generateRanks(originalScoreList);

		ArrayList<Double> initialScaleList = QryEval.scaledScore.get(0);
		int size = initialScaleList.size();
		int intents = size - 1;
		createInitialRanking(QryEval.scaledScore);

		String stringmaxOutputLength = parameters.get("diversity:maxResultRankingLength");
		int maxOutputLength = Integer.parseInt(stringmaxOutputLength);

		int divRankSize = Math.min(maxOutputLength, rankingMap.size());
		double desiredRanks = (double)divRankSize / (double)intents;

		List<Double> initCoverageList = initCoverageList(intents);
		List<Double> currentCover = new ArrayList<>(initCoverageList);

		ScoreList diversifiedRanking = new ScoreList();
		while (diversifiedRanking.size() < divRankSize) {

			HashMap<Integer, Double> quotients = new HashMap<>();
			int target = -1;
			double intentToCover = -1.0;
			boolean foundMax = false; 
			for (int i = 1; i <= intents; i++) {
				
				Double coveredDocId = currentCover.get(i);
				double currQuotient = desiredRanks / (2 * coveredDocId + 1);
				quotients.put(i, currQuotient);
				if (intentToCover < currQuotient) {
					intentToCover = currQuotient;
					target = i;
					foundMax = true;
				}
			}
			
			if(!foundMax) {
				int x = QryEval.scaledScore.size();
				int y = diversifiedRanking.size();
				int minSize = Math.min(x, y);
				int r = 0; 
				while(r < minSize) {
					if(!rankingMap.contains(r))
						continue; 
					
					double docScore = diversifiedRanking.getDocidScore(y-1);
					double newSc = docScore * totalScore;
					Integer temp = rankList.get(r);
					diversifiedRanking.add(temp, newSc);
					rankingMap.remove(r);
					r++;
				}
			}

			int maxScoreId = -1;
			double maximumValue = negInfinity;
			
			

			for (int idx : rankingMap) {
				ArrayList<Double> relevanceScore = QryEval.scaledScore.get(idx);
				double intentedTargetVal = intentToCover * relevanceScore.get(target);

				double otherIntents = 0.0;

				for (int j = 1; j <= intents; j++) {
					if (j != target)
						otherIntents += relevanceScore.get(j) * quotients.get(j);
				}
				double diversityLambda = Double.parseDouble(parameters.get("diversity:lambda"));

				double selectionValue = (diversityLambda * intentedTargetVal) + ((1 - diversityLambda) * otherIntents);

				if (maximumValue < selectionValue) {
					maximumValue = selectionValue;
					maxScoreId = idx;
				}
			}

			rankingMap.remove(maxScoreId);
			if (rankList == null || diversifiedRanking == null) {
				System.out.println("docidatrank or r is null");
			}
			if(maxScoreId == -1) maxScoreId = 0; 
			ArrayList<Double> maxQryScores = QryEval.scaledScore.get(maxScoreId);
			
			double sum = 0.0;
			int intentNum = 0;
			
			while (intentNum <= intents) {
				Double maxScoreforIntent = maxQryScores.get(intentNum++);
				sum = sum + maxScoreforIntent;
			}
			
			intentNum = 0; 
			while (intentNum <= intents) {
				Double maxScoreForIntent = maxQryScores.get(intentNum);
				double updateCoverScore = currentCover.get(intentNum) + maxScoreForIntent / sum;
				currentCover.set(intentNum++, updateCoverScore);
			}
			diversifiedRanking.add(rankList.get(maxScoreId), maximumValue);
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

		int maxRankingInput = Integer.parseInt(parameters.get("diversity:maxInputRankingsLength"));

		int maxInputRankingsLength = Math.min(maxRankingInput, size);

		int intentsSize = QryEval.score.size();
		List<Double> initCoverageList = initCoverageList(intentsSize);

		if (!truncateAndScale(originalScoreList, rankOfDocId, maxInputRankingsLength, initCoverageList))
			return;

		ArrayList<Double> sumOfScores = new ArrayList<>(initCoverageList);

		for (int iter = 0; iter < QryEval.scaledScore.size(); iter++) {
			ArrayList<Double> qryEntry = QryEval.scaledScore.get(iter);
			for (int j = 0; j < intentsSize + 1; j++) {
				double scoreTotal = sumOfScores.get(j) + qryEntry.get(j);
				sumOfScores.set(j, scoreTotal);
			}
		}

		double maximumScore = negInfinity;
		for (int iter = 0; iter < sumOfScores.size(); iter++) {
			if (sumOfScores.get(iter) > maximumScore) {
				maximumScore = sumOfScores.get(iter);
			}
		}

		for (int index = 0; index < QryEval.scaledScore.size(); index++) {
			for (int j = 0; j < intentsSize + 1; j++) {
				double scaledTotal = QryEval.scaledScore.get(index).get(j) / maximumScore;
				QryEval.scaledScore.get(index).set(j, scaledTotal);
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
	public boolean truncateAndScale(ScoreList originalScoreList, HashMap<Integer, Integer> rankOfDocId
			, int maxInputRankingsLength, List<Double> initCoverageList) {
		boolean needScale = false;
		for (int i = 0; i < maxInputRankingsLength; i++) {

			double docidScore = originalScoreList.getDocidScore(i);
			if (docidScore > 1)
				needScale = true;

			int originalDocId = originalScoreList.getDocid(i);

			rankOfDocId.put(originalDocId, i);
			ArrayList<Double> arr = new ArrayList<>(initCoverageList);

			arr.set(0, docidScore);
			QryEval.scaledScore.add(arr);
		}
		Set<String> allIntents = new HashSet<String>();

		allIntents = QryEval.score.keySet();
		for (String strs : allIntents) {
			ScoreList intentScoreList = QryEval.score.get(strs);
			int maximumConsiderLen = Math.min(maxInputRankingsLength, intentScoreList.size());
			for (int j = 0; j < maximumConsiderLen; j++) {

				int docid = intentScoreList.getDocid(j);
				if (!rankOfDocId.containsKey(docid))
					continue;

				int index = rankOfDocId.get(docid);
				double s = intentScoreList.getDocidScore(j);
				if (s > 1.0)
					needScale = true;
				ArrayList<Double> tempList = QryEval.scaledScore.get(index);
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