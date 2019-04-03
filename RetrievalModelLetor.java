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

		// extract BM25 parameters
		Double k_1 = Double.parseDouble(parameters.get("BM25:k_1"));
		Double b = Double.parseDouble(parameters.get("BM25:b"));
		Double k_3 = Double.parseDouble(parameters.get("BM25:k_3"));

		ScoreList scoreList = (QryEval.processQuery(strs[1], new RetrievalModelBM25(k_1, k_3, b), true));

		String fileName = parameters.get("letor:testingFeatureVectorsFile");

		this.normalizeAndPrint(fileName, false, calcFeatureVectorBM25(scoreList, queryLine));

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

		int returnVal = cmdProc.waitFor();
		if (returnVal != 0)
			throw new InterruptedException("SVM Rank test crashed");

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

				if (prevLine != null) {
					String[] strs = prevLine.trim().split("\\s+");
					int relevanceJudge = Integer.parseInt(strs[3]);

					int internalDocid = Idx.getInternalDocid(strs[2]);
					FeatureList featureList = new FeatureList(relevanceJudge, internalDocid, queryLine, parameters);

					featureVecList.add(featureList);
				}

				while ((queryRelevanceLine = trainingQRelsFile.readLine()) != null) {

					String[] strings = queryRelevanceLine.trim().split("\\s+");
					int internalDocumentId = 0; 

					String queryRelQID = strings[0].trim();

					String prevQid = null;
					if (prevLine != null) {
						String[] strs = prevLine.trim().split("\\s+");
						prevQid = strs[0].trim();

						if (!queryRelQID.equals(prevQid)) {
							prevLine = queryRelevanceLine;
							break;
						}
					}

					try {
						internalDocumentId = Idx.getInternalDocid(strings[2]);
					} catch(Exception exp) {
						continue;
					}

					int relevJudge = Integer.parseInt(strings[3]);
					FeatureList featureList = new FeatureList(relevJudge, internalDocumentId, queryLine, parameters);
					featureVecList.add(featureList);

					prevLine = queryRelevanceLine;
				}

				String fileName = parameters.get("letor:trainingFeatureVectorsFile");

				this.normalizeAndPrint(fileName, true, featureVecList);

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

	/**
	 * calculate the feture vector for BM25 documents and return the feature list
	 * 
	 * @param scoreList
	 * @param queryLine
	 * @return
	 * @throws IOException
	 */
	public List<FeatureList> calcFeatureVectorBM25(ScoreList scoreList, String queryLine) throws IOException {
		List<FeatureList> fList = new ArrayList<>();

		int document = 0;
		while (document < scoreList.size()) {
			FeatureList featureList = new FeatureList(0, scoreList.getDocid(document++), queryLine, parameters);
			fList.add(featureList);
		}
		return fList;
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

	/**
	 * normalize and print the score
	 * 
	 * @param file
	 * @param featureList
	 * @throws IOException
	 */
	public void normalizeAndPrint(String file, boolean append, List<FeatureList> featureList) throws IOException {

		FeatureList.normalizePrint(file, append, featureList);

	}

	@Override
	public String defaultQrySopName() {
		return null;
	}

}
