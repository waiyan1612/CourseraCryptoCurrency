import java.util.ArrayList;
import java.util.HashMap;

// Block Chain should maintain only limited block nodes to satisfy the functions
// You should not have all the blocks added to the block chain in memory 
// as it would cause a memory overflow.

public class BlockChain {
    public static final int CUT_OFF_AGE = 10;
    private TransactionPool txPool;
    private HashMap<ByteArrayWrapper, Node> chain;
    private Node maxHeightNode;

    /**
     * create an empty block chain with just a genesis block. Assume {@code genesisBlock} is a valid
     * block
     */
    public BlockChain(Block genesisBlock) {
        txPool = new TransactionPool();
        chain = new HashMap<>();
        UTXOPool uPool = new UTXOPool();
        addCoinBaseToUTXOPool(genesisBlock, uPool);
        maxHeightNode = new Node(genesisBlock, null, uPool);
        chain.put(wrap(genesisBlock.getHash()), maxHeightNode);
    }

    /** Get the maximum height block */
    public Block getMaxHeightBlock() {
        return maxHeightNode.block;
    }

    /** Get the UTXOPool for mining a new block on top of max height block */
    public UTXOPool getMaxHeightUTXOPool() {
        return maxHeightNode.getUTXOPool();
    }

    /** Get the transaction pool to mine a new block */
    public TransactionPool getTransactionPool() {
        return txPool;
    }

    /**
     * Add {@code block} to the block chain if it is valid. For validity, all transactions should be
     * valid and block should be at {@code height > (maxHeight - CUT_OFF_AGE)}.
     * 
     * <p>
     * For example, you can try creating a new block over the genesis block (block height 2) if the
     * block chain height is {@code <=
     * CUT_OFF_AGE + 1}. As soon as {@code height > CUT_OFF_AGE + 1}, you cannot create a new block
     * at height 2.
     * 
     * @return true if block is successfully added
     */
    public boolean addBlock(Block block) {
        byte[] hash = block.getPrevBlockHash();
        // genesis
        if(hash == null) {
            return false;
        }
        Node parent = chain.get(wrap(hash));
        if(parent == null) {
            return false;
        }

        int newHeight = parent.height+1; 
        if (newHeight <= maxHeightNode.height - CUT_OFF_AGE) {
            return false;
        }
        
        UTXOPool uPool = parent.getUTXOPool();
        TxHandler handler = new TxHandler(uPool);
        Transaction[] txs = block.getTransactions().toArray(new Transaction[block.getTransactions().size()]);
        Transaction[] validTxs = handler.handleTxs(txs);
        if(txs.length != validTxs.length) {
            return false;
        }
        
        addCoinBaseToUTXOPool(block, uPool);
        Node current = new Node(block, parent, uPool);
        chain.put(wrap(block.getHash()), current);
        
        if(newHeight > maxHeightNode.height) {
            maxHeightNode = current;
        }
        return true;
    }

    /** Add a transaction to the transaction pool */
    public void addTransaction(Transaction tx) {
        txPool.addTransaction(tx);
    }
    
    private ByteArrayWrapper wrap(byte[] b) {
        return new ByteArrayWrapper(b);
    }
    
    private void addCoinBaseToUTXOPool(Block block, UTXOPool uPool) {
        Transaction coinbase = block.getCoinbase();
        for(int i=0; i<coinbase.getOutputs().size(); i++) {
            UTXO utxo = new UTXO(coinbase.getHash(), i);
            uPool.addUTXO(utxo, coinbase.getOutput(i));
        }
    }
    
    private class Node {
        public Block block;
        public Node parent;
        public ArrayList<Node> children;
        public int height;
        private UTXOPool uPool;

        public Node(Block block, Node parent, UTXOPool uPool) {
            this.block = block;
            this.parent = parent;
            this.children = new ArrayList<>();
            this.uPool = uPool;
            if (parent != null) {
                this.height = parent.height + 1;
                this.parent.children.add(this);
            } else {
                this.height = 1;
            }
        }

        public UTXOPool getUTXOPool() {
            return new UTXOPool(uPool);
        }
    }
}