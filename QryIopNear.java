import java.io.IOException;
import java.util.Vector;

public class QryIopNear extends QryIop {

	private int position;
	private Vector<Integer> q0_postings;
	private Vector<Integer> qi_postings;

	public QryIopNear(String operatorName, int position) {

		this.position = position;
		if (!operatorName.equalsIgnoreCase("#near")) {
			throw new IllegalArgumentException(
					"The operator name is not \"#near\", " + "enter the correct operator name");
		}
		q0_postings = new Vector<Integer>();
		qi_postings = new Vector<Integer>();
	}

	@Override
	protected void evaluate() throws IOException {

		// create a new inverted list

		this.invertedList = new InvList(this.getField());

		// if there are no args or position parameter is negative
		if (args.size() < 1 || this.position < 0) {
			throw new IllegalArgumentException(
					"The argument format for '#near' is incorrect, " + " or the position parameter is negative");
		}

		while (this.docIteratorHasMatchAll(null)) {

			// fetch the argument0
			Qry q_0 = this.args.get(0);

			// fetch the doc id for the argument 0
			int id = q_0.docIteratorGetMatch();

			// get the postings list for argument 1

			q0_postings = ((QryIop) q_0).docIteratorGetMatchPosting().positions;

			iterateLocations(id);

			this.args.get(0).docIteratorAdvancePast(id);

		} // while (this.docIteratorHasMatchAll()) finished

	}

	private void iterateLocations(int id) {

		int prevArgLocID = 0, currArgLocId = 0;
		boolean match = false;

		for (int i = 1; i < this.args.size(); i++) {

			QryIop q_i = (QryIop) this.args.get(i);

			qi_postings = q_i.docIteratorGetMatchPosting().positions;

			Vector<Integer> temp = new Vector<Integer>();

			prevArgLocID = 0;
			currArgLocId = 0;

			int q0_tf = postingsSize(q0_postings);
			int qi_tf = postingsSize(qi_postings);

			while (prevArgLocID < q0_tf && currArgLocId < qi_tf) {

				int docIDdifference = qi_postings.get(currArgLocId) - q0_postings.get(prevArgLocID);

				if (docIDdifference < 0) {
					// if the second position is greater than first
					currArgLocId++;

				} else if (docIDdifference <= this.position) {
					// if the positional difference is within distance

					match = true;
					temp.add(qi_postings.get(currArgLocId));
					currArgLocId++;
					prevArgLocID++;

				} else if (docIDdifference > this.position) {

					// if the positional difference is out of bounds
					prevArgLocID++;

				}

			} // end of inner while loop

			q0_postings = temp;

		} // end of for-each loop

		if (match && postingsSize(q0_postings) > 0) {
			this.invertedList.appendPosting(id, q0_postings);
		}
	} // end of location iterator method 

	private int postingsSize(Vector<Integer> postings) {

		return postings.size();
	}
}
