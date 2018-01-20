import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

public class MaxFeeTxHandler {

    private UTXOPool utxoPool;

    public MaxFeeTxHandler(UTXOPool utxoPool) {
        this.utxoPool = new UTXOPool(utxoPool);
    }

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
            if (!Crypto.verifySignature(output.address, tx.getRawDataToSign(i), input.signature)) {
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

        // (5) the sum of txs input values is greater than or equal to the sum of its
        // output values
        return prevTxOutSum >= curTxOutSum;
    }

    private double calcTxFees(Transaction tx) {

        double prevTxOutSum = 0;
        double curTxOutSum = 0;
        for (int i = 0; i < tx.getInputs().size(); i++) {
            Transaction.Input input = tx.getInput(i);
            UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
            if (!utxoPool.contains(utxo)) {
                continue;
            }
            Transaction.Output output = utxoPool.getTxOutput(utxo);
            prevTxOutSum += output.value;
        }
        for (Transaction.Output o : tx.getOutputs()) {
            curTxOutSum += o.value;
        }
        return prevTxOutSum - curTxOutSum;
    }

    public Transaction[] handleTxs(Transaction[] possibleTxs) {

        Set<Transaction> possibleTxsList = new HashSet<>();
        possibleTxsList.addAll(Arrays.asList(possibleTxs));
        Set<Transaction> txsSortedByFees = new TreeSet<>((tx1, tx2) -> {
            double tx1Fees = calcTxFees(tx1);
            double tx2Fees = calcTxFees(tx2);
            return Double.valueOf(tx2Fees).compareTo(tx1Fees);
        });
        Collections.addAll(txsSortedByFees, possibleTxsList.toArray(new Transaction[possibleTxsList.size()]));

        Set<Transaction> acceptedTransactions = new HashSet<>();
        Iterator<Transaction> it = txsSortedByFees.iterator();

        while (it.hasNext()) {
            Transaction tx = it.next();
            if (!isValidTx(tx)) {
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
            possibleTxsList.remove(tx);
            txsSortedByFees.clear();
            Collections.addAll(txsSortedByFees, possibleTxsList.toArray(new Transaction[possibleTxsList.size()]));
            it = txsSortedByFees.iterator();
        }
        return acceptedTransactions.toArray(new Transaction[acceptedTransactions.size()]);
    }
}
