import java.io.IOException;

public class QrySopWSum extends QryWSop {

	@Override
	public double getScore(RetrievalModel r) throws IOException {

		if (!(r instanceof RetrievalModelIndri)) {
			throw new IllegalArgumentException("WSUM not applicable for " + r.getClass().getName());
		}

		if (!(this.docIteratorHasMatchCache()))
			return 0;

		else {
			int index = 0;

			int docId = this.docIteratorGetMatch();
			double sum = 0;

			double sumOfWeights = this.sumOfWeights();

			for (Qry qry : this.args) {

				if (((QrySop) qry).docIteratorHasMatch(r) && ((QrySop) qry).docIteratorGetMatch() == docId) {

					sum += (this.getWeight(index) / sumOfWeights) * ((QrySop) qry).getScore(r);
				} else {
					sum += (this.getWeight(index) / sumOfWeights) * ((QrySop) qry).getDefaultScore(r, docId);
				}
				index++;
			}

			return sum;
		}
	}

	@Override
	public double getDefaultScore(RetrievalModel r, int DocID) throws IOException {

		int index = 0;
		double sum = 0;

		double sumOfWeights = this.sumOfWeights();

		for (Qry qry : this.args) {

			sum += (this.getWeight(index) / sumOfWeights) * ((QrySop) qry).getDefaultScore(r, DocID);
			index++;
		}

		return sum;
	}

	@Override
	public boolean docIteratorHasMatch(RetrievalModel r) {

		if (!(r instanceof RetrievalModelIndri))
			throw new IllegalArgumentException("WSUM not applicable for " + r.getClass().getName());
		else
			return this.docIteratorHasMatchMin(r);
	}

}
