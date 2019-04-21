
/*
 *  Copyright (c) 2019, Carnegie Mellon University.  All Rights Reserved.
 *  Version 3.3.3.
 */
/* 
 * @author - rvsharma
 * 
 */
import java.io.*;
import java.util.*;

import org.apache.lucene.analysis.Analyzer.TokenStreamComponents;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

/**
 * This software illustrates the architecture for the portion of a search engine
 * that evaluates queries. It is a guide for class homework assignments, so it
 * emphasizes simplicity over efficiency. It implements an unranked Boolean
 * retrieval model, however it is easily extended to other retrieval models. For
 * more information, see the ReadMe.txt file.
 */
public class QryEval {

	// --------------- Constants and variables ---------------------

	private static final String USAGE = "Usage:  java QryEval paramFile\n\n";

	private static final String[] TEXT_FIELDS = { "body", "title", "url", "inlink" };

	private static Timer timer = new Timer();

	private static Diversification QryDiverse;

	private static Map<String, ArrayList<String>> intentScorePerQuery;

	private static Map<String, ScoreList> queryRanks;

	private static HashMap<String, HashMap<String, ScoreList>> intentRanks;

	private static Map<String, ArrayList<String>> generatedIntents;

	public static HashMap<String, ScoreList> score;
	
	public static ArrayList<ArrayList<Double>> scaledScore;
	
	// --------------- Methods ---------------------------------------

	/**
	 * @param args The only argument is the parameter file name.
	 * @throws Exception Error accessing the Lucene index.
	 */
	public static void main(String[] args) throws Exception {

		// This is a timer that you may find useful. It is used here to
		// time how long the entire program takes, but you can move it
		// around to time specific parts of your code.

		// Timer timer = new Timer();
		timer.start();

		// Check that a parameter file is included, and that the required
		// parameters are present. Just store the parameters. They get
		// processed later during initialization of different system
		// components.

		if (args.length < 1) {
			throw new IllegalArgumentException(USAGE);
		}

		Map<String, String> parameters = readParameterFile(args[0]);

		// Open the index and initialize the retrieval model.

		Idx.open(parameters.get("indexPath"));

		// Perform experiments.

		processQueryFile(parameters);

		// Clean up.

		timer.stop();
		String milliSecTime = timer.toString();
		milliSecTime = milliSecTime.substring(0, milliSecTime.indexOf(" "));
		System.out.println("Time(ms):  " + timer);
		System.out.println("Time(mm:ss): " + timeInMmSs(milliSecTime));
	}

	private static String timeInMmSs(String millisec) {

		double ms = Double.parseDouble(millisec);
		int round_up = 0;
		if (ms % 1000 > 500)
			round_up = 1;
		int sec = (((int) ms / 1000) % 60) + round_up;
		int min = ((int) ms / (1000 * 60)) % 60;

		return min + ":" + sec;
	}

	/**
	 * Allocate the retrieval model and initialize it using parameters from the
	 * parameter file.
	 * 
	 * @return The initialized retrieval model
	 * @throws IOException Error accessing the Lucene index.
	 */
	private static RetrievalModel initializeRetrievalModel(Map<String, String> parameters) throws IOException {

		RetrievalModel model = null;

		if (hasDiversity(parameters) && !parameters.containsKey("retrievalAlgorithm")
				&& parameters.containsKey("diversity:initialRankingFile"))
			return null;

		String modelString = parameters.get("retrievalAlgorithm").toLowerCase();

		if (modelString.equals("unrankedboolean")) {

			model = new RetrievalModelUnrankedBoolean();

		} else if (modelString.equals("rankedboolean")) {

			model = new RetrievalModelRankedBoolean();
		} else if (modelString.equals("bm25")) {

			double k_1 = Double.parseDouble(parameters.get("BM25:k_1"));
			double b = Double.parseDouble(parameters.get("BM25:b"));
			double k_3 = Double.parseDouble(parameters.get("BM25:k_3"));
			model = new RetrievalModelBM25(k_1, k_3, b);

		} else if (modelString.equals("indri")) {

			int mu = Integer.parseInt(parameters.get("Indri:mu"));
			double lambda = Double.parseDouble(parameters.get("Indri:lambda"));
			model = new RetrievalModelIndri(mu, lambda);

		} else if (modelString.equals("letor")) {

			model = new RetrievalModelLetor(parameters);

		} else {
			throw new IllegalArgumentException("Unknown retrieval model " + parameters.get("retrievalAlgorithm"));
		}

		return model;
	}

	/**
	 * @param parameters
	 * @return
	 */
	public static boolean hasDiversity(Map<String, String> parameters) {
		return parameters.containsKey("diversity") && parameters.get("diversity").equals("true");
	}

	/**
	 * Print a message indicating the amount of memory used. The caller can indicate
	 * whether garbage collection should be performed, which slows the program but
	 * reduces memory usage.
	 * 
	 * @param gc If true, run the garbage collector before reporting.
	 */
	public static void printMemoryUsage(boolean gc) {

		Runtime runtime = Runtime.getRuntime();

		if (gc)
			runtime.gc();

		System.out.println("Memory used: " + ((runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)) + "MB");
	}

	/**
	 * Process one query.
	 * 
	 * @param qString A string that contains a query.
	 * @param model   The retrieval model determines how matching and scoring is
	 *                done.
	 * @return Search results
	 * @throws IOException Error accessing the index
	 */
	static ScoreList processQuery(String qString, RetrievalModel model, boolean sort) throws IOException {

		String defaultOp = model.defaultQrySopName();
		qString = defaultOp + "(" + qString + ")";
		Qry q = QryParser.getQuery(qString);

		// Show the query that is evaluated

		System.out.println("    --> " + q);

		if (q != null) {

			ScoreList r = new ScoreList();

			if (q.args.size() > 0) { // Ignore empty queries

				q.initialize(model);

				while (q.docIteratorHasMatch(model)) {
					int docid = q.docIteratorGetMatch();
					double score = ((QrySop) q).getScore(model);
					r.add(docid, score);
					q.docIteratorAdvancePast(docid);
				}
			}

			if (sort) {
				r.sort();
				if (model instanceof RetrievalModelLetor)
					r.truncate(100);
			}
			return r;
		} else
			return null;
	}

	/**
	 * Process the query file.
	 * 
	 * @param queryFilePath
	 * @param model
	 * @throws Exception
	 * @throws IllegalArgumentException
	 * @throws NumberFormatException
	 */
	static void processQueryFile(Map<String, String> parameters)
			throws NumberFormatException, IllegalArgumentException, Exception {

		BufferedReader input = null;
		String outputPath = parameters.get("trecEvalOutputPath");
		int outLength = 0;
		RetrievalModel model = initializeRetrievalModel(parameters);
		String algo = null;
		ScoreList originalScoreList;
		if (hasDiversity(parameters)) {
			intentScorePerQuery = new HashMap<String, ArrayList<String>>();
			generatedIntents = new HashMap<String, ArrayList<String>>();
			diversification(parameters, model);
			// runDiversification(parameters);
			algo = parameters.get("diversity:algorithm");
			score = new HashMap<String, ScoreList>();

		}

		if (!hasDiversity(parameters))
			outLength = Integer.parseInt(parameters.get("trecEvalOutputLength"));

		ifLearningToRank(model);

		String queryFilePath = parameters.get("queryFilePath");

		try {
			String qLine = null;

			input = new BufferedReader(new FileReader(queryFilePath));

			// Each pass of the loop processes one query.

			while ((qLine = input.readLine()) != null) {
				int d = qLine.indexOf(':');

				if (d < 0) {
					throw new IllegalArgumentException("Syntax error:  Missing ':' in query line.");
				}

				printMemoryUsage(false);

				String qid = qLine.substring(0, d);
				String query = qLine.substring(d + 1);

				System.out.println("Query " + qLine);

				ScoreList r = null;

				if (model instanceof RetrievalModelLetor) {

					r = ((RetrievalModelLetor) model).testData(qLine);

					r.sort();
					printResults(qid, r, outputPath, outLength);

				} else {

					if (!hasDiversity(parameters)
							&& ((!parameters.containsKey("fb")) || (parameters.get("fb").equals("false")))) {

						r = processQuery(query, model, false);

					} else if (hasDiversity(parameters)) {
						// System.out.println("Came here in diversity");

						if (!parameters.containsKey("diversity:initialRankingFile")) {

							ArrayList<String> intentsList = intentScorePerQuery.get(qid);
							originalScoreList = QryEval.processQuery(query, model, true);
							int intentNum = 0;
							for (String intentS : intentsList) {
								ArrayList<String> intentForQuery = generatedIntents.get(qid);
								String eachIntentQuery = intentForQuery.get(intentNum++);
								// System.out.println(eachIntentQuery);
								ScoreList rDiv = QryEval.processQuery(eachIntentQuery, model, true);
								score.put(intentS, rDiv);
							}
						} else {
							score = intentRanks.get(qid);
							originalScoreList = queryRanks.get(qid);
						}
						ScoreList rDiverse = null;

						if (algo.equalsIgnoreCase("PM2"))
							rDiverse = QryDiverse.diversifyPM2(originalScoreList);
						else if (algo.equalsIgnoreCase("xQuAD"))
							rDiverse = QryDiverse.diversifyxQuAD(originalScoreList);

						if (rDiverse != null) {
							String outputPathDiverse = parameters.get("trecEvalOutputPath");
							String stringmaxOutputLength = parameters.get("diversity:maxResultRankingLength");
							int maxOutputLength = Integer.parseInt(stringmaxOutputLength);
							QryEval.printResults(qid, rDiverse, outputPathDiverse, maxOutputLength);
						}

					} else {

						QryExp qExp = new QryExp(parameters, query);

						if (parameters.containsKey("fbInitialRankingFile")) {

							r = qExp.readTeInFile(qid);
							// System.out.println("Expansion WITH initial file");

						} else {

							// System.out.println("Expansion WITHOUT initial file");
							r = processQuery(query, model, false);
							r.sort();
						}

						String qExpanded = qExp.expandQuery(r, model);
						qExp.printExpandedQuery(qid);
						r = processQuery(qExpanded, model, false);
					}

					if (r != null && !hasDiversity(parameters)) {
						r.sort();
						printResults(qid, r, outputPath, outLength);
						// System.out.println();
					}

				}
//				if(model instanceof RetrievalModelLetor) {}
			}
		} catch (IOException ex) {
			ex.printStackTrace();
		} finally {
			input.close();
		}
	}

	/**
	 * @param model
	 * @throws Exception
	 * @throws IOException
	 */
	public static void ifLearningToRank(RetrievalModel model) throws Exception, IOException {
		try {
			if (model instanceof RetrievalModelLetor) {

				((RetrievalModelLetor) model).calcFeatureVector();

				((RetrievalModelLetor) model).trainModel();
			}
		} catch (InterruptedException e) {
			System.out.println("Letor caught an exception");
			e.printStackTrace();
		}
	}

	/**
	 * @param parameters
	 * @param model
	 * @throws Exception
	 * @throws IOException
	 */
	private static void diversification(Map<String, String> parameters, RetrievalModel model) {
		QryDiverse = new Diversification(parameters, model);
		if (parameters.containsKey("diversity:initialRankingFile")) {

			readRankingFile(parameters);

		} else {

			String intentFile = parameters.get("diversity:intentsFile");
			File file = new File(intentFile);
			Reader fileReader = null;
			try {
				fileReader = new FileReader(file);
			} catch (FileNotFoundException e1) {
				System.out.println("The file does not exist, is a directory rather than a regular file, "
						+ "or for some other reason cannot be opened for reading.");
				e1.printStackTrace();
			}

			BufferedReader reader = new BufferedReader(fileReader);
			String line = null;

			try {
				while ((line = reader.readLine()) != null) {

					String[] strs = line.split("\\:");
					String queryId = strs[0];

					String[] qryIntentPair = queryId.split("\\.");
					String qryId = qryIntentPair[0];
					String intent = qryIntentPair[1];

					intentScorePerQuery.putIfAbsent(qryId, new ArrayList<>());
					intentScorePerQuery.get(qryId).add(intent);

					generatedIntents.putIfAbsent(qryId, new ArrayList<String>());
					generatedIntents.get(qryId).add(strs[1]);

				}
			} catch (IOException e1) {
				System.out.println("Error reading query line at " + line + " in file " + intentFile);
				e1.printStackTrace();
			}

			try {
				reader.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * @param parameters
	 * @param QryDiverse
	 */
	private static void readRankingFile(Map<String, String> parameters) {

		File initialRankingFile = new File(parameters.get("diversity:initialRankingFile"));
		Reader read = null;
		queryRanks = new HashMap<String, ScoreList>();
		intentRanks = new HashMap<String, HashMap<String, ScoreList>>();
		try {
			read = new FileReader(initialRankingFile);
		} catch (FileNotFoundException e1) {
			// TODO Auto-generated catch block
			System.out.println("The file does not exist, is a directory rather than a regular file, "
					+ "or for some other reason cannot be opened for reading.");
			e1.printStackTrace();
		}
		BufferedReader reader = new BufferedReader(read);
		String line = null;
		String lastId = null;
		ScoreList r = new ScoreList();

		try {
			while ((line = reader.readLine()) != null) {

				String[] strs = line.split("\\s+");
				String currQuery = strs[0];
				// System.out.println("currQuery=" + currQuery);
				int internalDocid = 0;
				double docScore = Double.parseDouble(strs[4]);
				try {
					internalDocid = Idx.getInternalDocid(strs[2].trim());
				} catch (Exception e) {
					System.out.println("Cannot open index while reading internal docid at " + line);
					e.printStackTrace();
				}

				if (currQuery.equals(lastId)) {

					r.add(internalDocid, docScore);

				} else {

					extractQueryIntent(lastId, r);
					lastId = currQuery;
					r = new ScoreList();
					r.add(internalDocid, docScore);
				}

			}
		} catch (IOException e) {
			System.out.println("An IO error occured while reading " + line);
			e.printStackTrace();
		}

		extractQueryIntent(lastId, r);

		try {
			reader.close();
		} catch (IOException e) {
			System.out.println("Error while closing the file stream");
			e.printStackTrace();
		}
	}

	/**
	 * 
	 * @param currId
	 * @param r
	 */
	private static void extractQueryIntent(String currId, ScoreList r) {
		if (currId == null)
			return;
		if (!currId.contains(".")) {
			queryRanks.put(currId, r);
		} else {
			String[] queryIntentPair = currId.split("\\.");
			String qid = queryIntentPair[0];
			String intent = queryIntentPair[1];

			intentScorePerQuery.putIfAbsent(qid, new ArrayList<>());
			intentScorePerQuery.get(qid).add(intent);
			intentRanks.putIfAbsent(qid, new HashMap<>());
			intentRanks.get(qid).put(intent, r);
		}
	}

	/**
	 * Print the query results.
	 * 
	 * THIS IS NOT THE CORRECT OUTPUT FORMAT. YOU MUST CHANGE THIS METHOD SO THAT IT
	 * OUTPUTS IN THE FORMAT SPECIFIED IN THE HOMEWORK PAGE, WHICH IS:
	 * 
	 * QueryID Q0 DocID Rank Score RunID
	 * 
	 * @param queryId Original query.
	 * @param result  A list of document ids and scores
	 * @throws IOException Error accessing the Lucene index.
	 */
	static void printResults(String queryId, ScoreList result, String outFilePath, int outLength) throws IOException {

		FileWriter writer = new FileWriter(outFilePath, true);
		String str, docID;
		double docScore;
		int len = Math.min(outLength, result.size());
		// System.out.println(len);
		if (result.size() < 1) {

			str = queryId + " Q0" + " dummyRecord" + " 1" + " 0" + " fubar";
			str += "\n";
			writer.write(str);
			System.out.println(str);

		} else {
			// result.sort();

			str = "";
			for (int i = 0; i < len; i++) {

				docID = Idx.getExternalDocid(result.getDocid(i));
				docScore = result.getDocidScore(i);
				if(docScore == 0.0) {
					docScore = 0.000000000000000009;
				}
				String Score = String.format("%.18f", docScore);
				str += queryId + " Q0" + " " + docID + " " + (i + 1) + " " + Score + " fubar";
				str += "\n";
			}
			writer.write(str);
			System.out.print(str);
		}
		writer.close();
	}

	/**
	 * Read the specified parameter file, and confirm that the required parameters
	 * are present. The parameters are returned in a HashMap. The caller (or its
	 * minions) are responsible for processing them.
	 * 
	 * @return The parameters, in <key, value> format.
	 */
	private static Map<String, String> readParameterFile(String parameterFileName) throws IOException {

		Map<String, String> parameters = new HashMap<String, String>();

		File parameterFile = new File(parameterFileName);

		if (!parameterFile.canRead()) {
			throw new IllegalArgumentException("Can't read " + parameterFileName);
		}

		Scanner scan = new Scanner(parameterFile);
		String line = null;
		do {
			line = scan.nextLine();
			String[] pair = line.split("=");
			parameters.put(pair[0].trim(), pair[1].trim());
		} while (scan.hasNext());

		scan.close();

		if (!(parameters.containsKey("indexPath") && parameters.containsKey("queryFilePath")
				&& parameters.containsKey("trecEvalOutputPath"))) {
			throw new IllegalArgumentException("Required parameters were missing from the parameter file.");
		}

		return parameters;
	}

	

}
