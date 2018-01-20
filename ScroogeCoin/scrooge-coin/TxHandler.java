import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TxHandler {
	
	private UTXOPool utxoPool;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
        this.utxoPool = new UTXOPool(utxoPool); 
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool, 
     * (2) the signatures on each input of {@code tx} are valid, 
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
    	Set<UTXO> usedTxs = new HashSet<>();
    	double prevTxOutSum = 0;
    	double curTxOutSum = 0;
    	
		for (int i = 0; i < tx.getInputs().size(); i++) {
			Transaction.Input input = tx.getInput(i);
			UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);

			// (1) all outputs claimed by tx are in the current UTXO pool
			// (3) no UTXO is claimed multiple times by tx
			if (!utxoPool.contains(utxo) || usedTxs.contains(utxo)) {
				return false;
			}
			
			Transaction.Output output = utxoPool.getTxOutput(utxo);
			// (2) the signatures on each input of tx are valid, 
			if(!Crypto.verifySignature(output.address, tx.getRawDataToSign(i), input.signature)) {
				return false;
			}
			
			usedTxs.add(utxo);
			prevTxOutSum += output.value;
		}

		// (4) all of txs output values are non-negative
		for (Transaction.Output o : tx.getOutputs()) {
			if (o.value < 0) {
				return false;
			}
			curTxOutSum += o.value;
		}

		// (5) the sum of txs input values is greater than or equal to the sum of its output values
		return prevTxOutSum >= curTxOutSum;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        Set<Transaction> acceptedTransactions = new HashSet<>();
    	for(Transaction tx: possibleTxs) {
    		if(!isValidTx(tx)) {
    			continue;
    		}
    		for (int i = 0; i < tx.getInputs().size(); i++) {
    			Transaction.Input input = tx.getInput(i);
    			UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
    			utxoPool.removeUTXO(utxo);
    		}
    		int index = 0;
    		for (Transaction.Output output : tx.getOutputs()) {
                UTXO utxo = new UTXO(tx.getHash(), index++);
                utxoPool.addUTXO(utxo, output);
            }
    		acceptedTransactions.add(tx);
    	}
    	return acceptedTransactions.toArray(new Transaction[acceptedTransactions.size()]);
    }

}
