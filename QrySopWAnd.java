import java.io.IOException;

public class QrySopWAnd extends QryWSop {

	@Override
	public double getScore(RetrievalModel r) throws IOException {

		if (!(r instanceof RetrievalModelIndri)) {
			throw new IllegalArgumentException(r.getClass().getName() + "is " + "not applicable to WAND operator");
		}

		if (!this.docIteratorHasMatchCache())
			return 0;

		else {

			int documentID = this.docIteratorGetMatch();

			double result = 1.0;

			double sumOfweights = this.sumOfWeights();

			int index = 0;

			for (Qry qry : this.args) {

				if (qry.docIteratorHasMatch(r) && qry.docIteratorGetMatch() == documentID) {

					result *= Math.pow(((QrySop) qry).getScore(r), this.getWeight(index) / sumOfweights);

				} else {

					result *= Math.pow(((QrySop) qry).getDefaultScore(r, documentID),
							this.getWeight(index) / sumOfweights);

				}
				index++;

			} // for loop ends

			return result;

		} // else end
	}

	@Override
	public boolean docIteratorHasMatch(RetrievalModel r) {

		return this.docIteratorHasMatchMin(r);
	}

	@Override
	public double getDefaultScore(RetrievalModel r, int documentID) throws IOException {

		double defaultScore = 1.0;

		double sumOfweights = this.sumOfWeights();

		int index = 0;

		for (Qry qry : this.args) {

			double exp = this.getWeight(index) / sumOfweights;
			defaultScore *= Math.pow(((QrySop) qry).getDefaultScore(r, documentID), exp);
			index++;
		}
		return defaultScore;

	}

}
