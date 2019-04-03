import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RetrievalModelLetor extends RetrievalModel {

	private Map<String, String> parameters;

	public RetrievalModelLetor(Map<String, String> parameters) {

		this.parameters = parameters;

	}

	public ScoreList testData(String queryLine) throws IOException {

		// System.out.println("Testing started");
		String[] strs = queryLine.split(":");
		String qId = strs[0];
		String query = strs[1];

		// extract BM25 parameters
		Double k_1 = Double.parseDouble(parameters.get("BM25:k_1"));
		Double b = Double.parseDouble(parameters.get("BM25:b"));
		Double k_3 = Double.parseDouble(parameters.get("BM25:k_3"));

		ScoreList scoreList = QryEval.processQuery(query, new RetrievalModelBM25(k_1, k_3, b));

		scoreList.sort();
		scoreList.truncate(100);

		List<FeatureList> fList = new ArrayList<>();
		for (int i = 0; i < scoreList.size(); i++) {

			int docId = scoreList.getDocid(i);
			FeatureList fl = new FeatureList(0, docId, qId, query, parameters);
			fList.add(fl);
		}
		String fileName = parameters.get("letor:testingFeatureVectorsFile");
		// FeatureList.normalizePrint(fList, fileName);
		normalizeAndPrint(fileName, fList);

		try {
			testModel();
		} catch (InterruptedException e) {
			System.out.println("Test model caught an exception");
			e.printStackTrace();

		}

		readSvmScores(scoreList);

		return scoreList;
	}

	/**
	 * @param file
	 * @param featureList
	 * @throws IOException
	 */
	public void normalizeAndPrint(String file, List<FeatureList> featureList) throws IOException {
		FeatureList.normalize(featureList);
		FeatureList.printScore(featureList, file, false);
	}

	/**
	 * @param originalList
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	public void readSvmScores(ScoreList originalList) throws FileNotFoundException, IOException {
		BufferedReader read = new BufferedReader(new FileReader(parameters.get("letor:testingDocumentScores")));
		String result;

		for (int i = 0; (result = read.readLine()) != null; i++) {

			originalList.setDocidScore(i, Double.parseDouble(result.trim()));

		}

		read.close();
	}

	private void testModel() throws IOException, InterruptedException {

		String svmRankClassifyPath = parameters.get("letor:svmRankClassifyPath");
		String testingDocScoresFile = parameters.get("letor:testingDocumentScores");
		String testVecFile = parameters.get("letor:testingFeatureVectorsFile");
		String svmRankModelFile = parameters.get("letor:svmRankModelFile");

		File file = new File(testingDocScoresFile);
		Writer writer = new FileWriter(file);
		writer.write("");
		writer.close();

		Process cmdProc = null;

		cmdProc = Runtime.getRuntime()
				.exec(new String[] { svmRankClassifyPath, testVecFile, svmRankModelFile, testingDocScoresFile });

		BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(cmdProc.getInputStream()));
		String line;
		while ((line = stdoutReader.readLine()) != null) {
			System.out.println(line);
		}
		BufferedReader stderrReader = new BufferedReader(new InputStreamReader(cmdProc.getErrorStream()));
		while ((line = stderrReader.readLine()) != null) {
			System.out.println(line);
		}

		int returnVal = cmdProc.waitFor();
		if (returnVal != 0)
			throw new InterruptedException("SVM Rank test crashed");

		stdoutReader.close();
		stderrReader.close();
	}

	public void calcFeatureVector() throws NumberFormatException, IllegalArgumentException, Exception {

		String trainQryFile = parameters.get("letor:trainingQueryFile");
		String trainQrelsFile = parameters.get("letor:trainingQrelsFile");
		String queryLine;
		String queryRelevanceLine;
		String prevLine = null;

		BufferedReader trainingQueryFile = null;
		BufferedReader trainingQRelsFile = null;
		// read training query
		try {

			trainingQueryFile = new BufferedReader(new FileReader(new File(trainQryFile)));
			trainingQRelsFile = new BufferedReader(new FileReader(new File(trainQrelsFile)));

		} catch (FileNotFoundException e) {
			System.out.println("Error reading training query or relevance file");
			e.printStackTrace();
		}

		try {
			while ((queryLine = trainingQueryFile.readLine()) != null) {

				List<FeatureList> featureVecList = new ArrayList<FeatureList>();

				int index = queryLine.indexOf(':');
				String queryId = queryLine.substring(0, index);
				String query = queryLine.substring(index + 1);

				if (prevLine != null) {
					String[] strs = prevLine.trim().split("\\s+");
					int relevanceJudge = Integer.parseInt(strs[3]);

					int internalDocid = Idx.getInternalDocid(strs[2]);
					FeatureList featureList = new FeatureList(relevanceJudge, internalDocid, queryId, query,
							parameters);

					featureVecList.add(featureList);
				}

				while ((queryRelevanceLine = trainingQRelsFile.readLine()) != null) {
					String queryRelQID = queryRelevanceLine.trim().substring(0, 3);

					if (prevLine != null && !queryRelQID.equals(prevLine.trim().substring(0, 3))) {
						prevLine = queryRelevanceLine;
						break;
					}

					String[] strings = queryRelevanceLine.trim().split("\\s+");

					Integer internalDocId = null;

					try {
						internalDocId = Idx.getInternalDocid(strings[2]);
					} catch (Exception e) {
						continue;
					}

					int relevJudge = Integer.parseInt(strings[3]);
					FeatureList featureList = new FeatureList(relevJudge, internalDocId, queryId, query, parameters);
					featureVecList.add(featureList);

					prevLine = queryRelevanceLine;
				}

				// FeatureList.normalizePrint(featureVecList,
				// parameters.get("letor:trainingFeatureVectorsFile"));
				String fileName = parameters.get("letor:trainingFeatureVectorsFile");
				FeatureList.normalize(featureVecList);
				FeatureList.printScore(featureVecList, fileName, true);

			}

		} catch (IOException e) {
			System.out.println("Error reading training query file");
			e.printStackTrace();
		}

		try {
			trainingQueryFile.close();
			trainingQRelsFile.close();
		} catch (IOException e) {
			System.out.println("Error closing query and relevance judgement files ");
			e.printStackTrace();
		}

		System.out.println("Calculated feature vectors");
	}

	public void trainModel() throws IOException, InterruptedException {

		System.out.println("Training started");
		Process cmdProc = null;
		String svmRankLearnPath = parameters.get("letor:svmRankLearnPath");
		String svmRankParam = parameters.get("letor:svmRankParamC");
		String trainVecFile = parameters.get("letor:trainingFeatureVectorsFile");
		String svmRankModelFile = parameters.get("letor:svmRankModelFile");
		cmdProc = Runtime.getRuntime()
				.exec(new String[] { svmRankLearnPath, "-c", svmRankParam, trainVecFile, svmRankModelFile });

		BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(cmdProc.getInputStream()));
		String line;
		while ((line = stdoutReader.readLine()) != null) {
			System.out.println(line);
		}
		BufferedReader stderrReader = new BufferedReader(new InputStreamReader(cmdProc.getErrorStream()));
		while ((line = stderrReader.readLine()) != null) {
			System.out.println(line);
		}

		int retVal = cmdProc.waitFor();
		if (retVal != 0)
			throw new InterruptedException("SVM rank crashed ");
		System.out.println("Training completed");
		stdoutReader.close();
		stderrReader.close();

	}

	@Override
	public String defaultQrySopName() {
		// TODO Auto-generated method stub
		return null;
	}

}
