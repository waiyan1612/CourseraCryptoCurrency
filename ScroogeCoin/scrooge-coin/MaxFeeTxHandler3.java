import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class MaxFeeTxHandler3 {

    private UTXOPool utxoPool;

    public MaxFeeTxHandler3(UTXOPool utxoPool) {
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
            if (!utxoPool.contains(utxo) || !isValidTx(tx)) continue;
            Transaction.Output output = utxoPool.getTxOutput(utxo);
            if(output != null) {
                prevTxOutSum += output.value;
            }
        }
        for (Transaction.Output o : tx.getOutputs()) {
            curTxOutSum += o.value;
        }
        return prevTxOutSum - curTxOutSum;
    }

    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        
        Set<Transaction> txsSortedByFees = new TreeSet<>((tx1, tx2) -> {
            double tx1Fees = calcTxFees(tx1);
            double tx2Fees = calcTxFees(tx2);
            return Double.valueOf(tx2Fees).compareTo(tx1Fees);
        });

        Collections.addAll(txsSortedByFees, possibleTxs);

        
        Set<Transaction> acceptedTransactions = new HashSet<>();
        for (Transaction tx : txsSortedByFees) {
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
        }

        Comparator<Transaction> maxFeeComparator = new Comparator<Transaction>() {
            public int compare(Transaction t1, Transaction t2) {
                return Double.valueOf(calcTxFees(t2)).compareTo(Double.valueOf(calcTxFees(t1)));
            }

            
        };
        //Collections.sort(acceptedTransactions, maxFeeComparator);
        return acceptedTransactions.toArray(new Transaction[acceptedTransactions.size()]);
    }
}
