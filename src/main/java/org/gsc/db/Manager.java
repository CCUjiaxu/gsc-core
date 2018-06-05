package org.gsc.db;

import static org.gsc.config.Parameter.ChainConstant.MAXIMUM_TIME_UNTIL_EXPIRATION;
import static org.gsc.config.Parameter.ChainConstant.TRANSACTION_MAX_BYTE_SIZE;

import com.google.common.collect.Lists;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import javafx.util.Pair;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.gsc.common.exception.AccountResourceInsufficientException;
import org.gsc.common.exception.BadItemException;
import org.gsc.common.exception.BadNumberBlockException;
import org.gsc.common.exception.BalanceInsufficientException;
import org.gsc.common.exception.ContractExeException;
import org.gsc.common.exception.ContractValidateException;
import org.gsc.common.exception.DupTransactionException;
import org.gsc.common.exception.ItemNotFoundException;
import org.gsc.common.exception.RevokingStoreIllegalStateException;
import org.gsc.common.exception.TaposException;
import org.gsc.common.exception.TooBigTransactionException;
import org.gsc.common.exception.TransactionExpirationException;
import org.gsc.common.exception.UnLinkedBlockException;
import org.gsc.common.exception.ValidateScheduleException;
import org.gsc.common.exception.ValidateSignatureException;
import org.gsc.common.utils.ByteArray;
import org.gsc.common.utils.DialogOptional;
import org.gsc.common.utils.Sha256Hash;
import org.gsc.common.utils.StringUtil;
import org.gsc.config.Parameter.ChainConstant;
import org.gsc.consensus.ProducerController;
import org.gsc.core.chain.BlockId;
import org.gsc.core.chain.TransactionResultWrapper;
import org.gsc.core.operator.Operator;
import org.gsc.core.operator.OperatorFactory;
import org.gsc.core.wrapper.AccountWrapper;
import org.gsc.core.wrapper.BlockWrapper;
import org.gsc.core.wrapper.ProducerWrapper;
import org.gsc.core.wrapper.TransactionWrapper;
import org.gsc.db.UndoStore.Dialog;
import org.joda.time.DateTime;
import org.spongycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;

@Getter
@Slf4j
public class Manager {

  @Autowired
  private AccountStore accountStore;

  @Autowired
  private TransactionStore transactionStore;

  @Autowired
  private BlockStore blockStore;

  @Autowired
  private ProducerStore prodStore;

  @Autowired
  private AssetIssueStore assetIssueStore;

  @Autowired
  private GlobalPropertiesStore globalPropertiesStore;

  @Autowired
  private BlockIndexStore blockIndexStore;
//  @Autowired
//  private AccountIndexStore accountIndexStore;
  @Autowired
  private ProducerScheduleStore prodScheduleStore;

  @Autowired
  private TaposBlockStore taposStore;

  @Autowired
  private VotesStore votesStore;
//
  @Autowired
  private PeersStore peersStore;

  @Autowired
  private ForkDatabase forkDB;

  @Getter
  private BlockWrapper genesisBlock;

  @Autowired
  private UndoStore undoStore;

  @Autowired
  private DialogOptional dialog;

  @Autowired
  private ProducerController prodController;

  // transactions cache
  private List<TransactionWrapper> pendingTransactions;

  // transactions popped
  @Getter
  private List<TransactionWrapper> popedTransactions =
      Collections.synchronizedList(Lists.newArrayList());

  /**
   * judge balance.
   */
  public void adjustBalance(byte[] accountAddress, long amount)
      throws BalanceInsufficientException {
    AccountWrapper account = getAccountStore().get(accountAddress);
    long balance = account.getBalance();
    if (amount == 0) {
      return;
    }

    if (amount < 0 && balance < -amount) {
      throw new BalanceInsufficientException(accountAddress + " Insufficient");
    }
    account.setBalance(Math.addExact(balance, amount));
    this.getAccountStore().put(account.getAddress().toByteArray(), account);
  }

  public long getHeadBlockTimeStamp() {
    return 0L;
    //TODO
    //return getDynamicPropertiesStore().getLatestBlockHeaderTimestamp();
  }

  public boolean lastHeadBlockIsMaintenance() {
    return getGlobalPropertiesStore().getStateFlag() == 1;
  }

  public void adjustAllowance(byte[] accountAddress, long amount)
      throws BalanceInsufficientException {
    AccountWrapper account = getAccountStore().get(accountAddress);
    long allowance = account.getAllowance();
    if (amount == 0) {
      return;
    }

    if (amount < 0 && allowance < -amount) {
      throw new BalanceInsufficientException(accountAddress + " Insufficient");
    }
    account.setAllowance(allowance + amount);
    this.getAccountStore().put(account.createDbKey(), account);
  }

  void validateTapos(TransactionWrapper transactionCapsule) throws TaposException {
    byte[] refBlockHash = transactionCapsule.getInstance()
        .getRawData().getRefBlockHash().toByteArray();
    byte[] refBlockNumBytes = transactionCapsule.getInstance()
        .getRawData().getRefBlockBytes().toByteArray();
    try {
      byte[] blockHash = this.taposStore.get(refBlockNumBytes).getData();
      if (Arrays.equals(blockHash, refBlockHash)) {
        return;
      } else {
        String str = String.format(
            "Tapos failed, different block hash, %s, %s , recent block %s, solid block %s head block %s",
            ByteArray.toLong(refBlockNumBytes), Hex.toHexString(refBlockHash),
            Hex.toHexString(blockHash),
            getSolidBlockId(),
            globalPropertiesStore.getLatestBlockHeaderHash());
        logger.info(str);
        throw new TaposException(str);

      }
    } catch (ItemNotFoundException e) {
      String str = String.
          format("Tapos failed, block not found, ref block %s, %s , solid block %s head block %s",
              ByteArray.toLong(refBlockNumBytes), Hex.toHexString(refBlockHash),
              getSolidBlockId(),
              globalPropertiesStore.getLatestBlockHeaderHash()).toString();
      logger.info(str);
      throw new TaposException(str);
    }
  }

  public BlockId getSolidBlockId() {
    try {
      long num = globalPropertiesStore.getLatestSolidifiedBlockNum();
      return getBlockIdByNum(num);
    } catch (Exception e) {
      return getGenesisBlockId();
    }
  }

  public BlockId getGenesisBlockId() {
    return this.genesisBlock.getBlockId();
  }


  void validateCommon(TransactionWrapper transactionCapsule)
      throws TransactionExpirationException, TooBigTransactionException {
    if (transactionCapsule.getData().length > TRANSACTION_MAX_BYTE_SIZE) {
      throw new TooBigTransactionException(
          "too big transaction, the size is " + transactionCapsule.getData().length + " bytes");
    }
    long transactionExpiration = transactionCapsule.getExpiration();
    long headBlockTime = getHeadBlockTimeStamp();
    if (transactionExpiration <= headBlockTime ||
        transactionExpiration > headBlockTime + MAXIMUM_TIME_UNTIL_EXPIRATION) {
      throw new TransactionExpirationException(
          "transaction expiration, transaction expiration time is " + transactionExpiration
              + ", but headBlockTime is " + headBlockTime);
    }
  }

  void validateDup(TransactionWrapper transactionCapsule) throws DupTransactionException {
    try {
      if (getTransactionStore().get(transactionCapsule.getTransactionId().getBytes()) != null) {
        logger.debug(ByteArray.toHexString(transactionCapsule.getTransactionId().getBytes()));
        throw new DupTransactionException("dup trans");
      }
    } catch (BadItemException e) {
      logger.debug(ByteArray.toHexString(transactionCapsule.getTransactionId().getBytes()));
      throw new DupTransactionException("dup trans");
    }
  }
  /**
   * push transaction into db.
   */
  public boolean pushTransactions(final TransactionWrapper trx)
      throws ValidateSignatureException, ContractValidateException, ContractExeException,
      AccountResourceInsufficientException, DupTransactionException, TaposException,
      TooBigTransactionException, TransactionExpirationException {
    logger.info("push transaction");

    if (!trx.validateSignature()) {
      throw new ValidateSignatureException("trans sig validate failed");
    }

    //validateFreq(trx);
    synchronized (this) {
      if (!dialog.valid()) {
        dialog.setValue(undoStore.buildDialog());
      }

      try (Dialog tmpDialog = undoStore.buildDialog()) {
        processTransaction(trx);
        pendingTransactions.add(trx);
        tmpDialog.merge();
      } catch (RevokingStoreIllegalStateException e) {
        logger.debug(e.getMessage(), e);
      }
    }
    return true;
  }


  public void consumeBandwidth(TransactionWrapper trx)
      throws ContractValidateException, AccountResourceInsufficientException {
    //TODO
//    BandwidthProcessor processor = new BandwidthProcessor(this);
//    processor.consumeBandwidth(trx);
  }
  /**
   * when switch fork need erase blocks on fork branch.
   */
  public void eraseBlock() throws BadItemException, ItemNotFoundException {
    dialog.reset();
    BlockWrapper oldHeadBlock =
        getBlockStore().get(globalPropertiesStore.getLatestBlockHeaderHash().getBytes());
    try {
      undoStore.pop();
    } catch (RevokingStoreIllegalStateException e) {
      logger.info(e.getMessage(), e);
    }
    logger.info("erase block:" + oldHeadBlock);
    forkDB.pop();
    popedTransactions.addAll(oldHeadBlock.getTransactions());
  }

  private void applyBlock(BlockWrapper block) throws ContractValidateException,
      ContractExeException, ValidateSignatureException, AccountResourceInsufficientException,
      TransactionExpirationException, TooBigTransactionException, DupTransactionException,
      TaposException, ValidateScheduleException {
    //TODO
//    processBlock(block);
//    this.blockStore.put(block.getBlockId().getBytes(), block);
//    this.blockIndexStore.put(block.getBlockId());
  }

  private void switchFork(BlockWrapper newHead) {
    Pair<LinkedList<BlockWrapper>, LinkedList<BlockWrapper>> binaryTree =
        forkDB.getBranch(
            newHead.getBlockId(), globalPropertiesStore.getLatestBlockHeaderHash());

    if (CollectionUtils.isNotEmpty(binaryTree.getValue())) {
      while (!globalPropertiesStore
          .getLatestBlockHeaderHash()
          .equals(binaryTree.getValue().peekLast().getParentHash())) {
        try {
          eraseBlock();
        } catch (BadItemException e) {
          logger.info(e.getMessage());
        } catch (ItemNotFoundException e) {
          logger.info(e.getMessage());
        }
      }
    }

    if (CollectionUtils.isNotEmpty(binaryTree.getKey())) {
      LinkedList<BlockWrapper> branch = binaryTree.getKey();
      Collections.reverse(branch);
      branch.forEach(
          item -> {
            // todo  process the exception carefully later
            try (Dialog tmpDialog = undoStore.buildDialog()) {
              applyBlock(item);
              tmpDialog.commit();
            } catch (AccountResourceInsufficientException e) {
              logger.debug(e.getMessage(), e);
            } catch (ValidateSignatureException e) {
              logger.debug(e.getMessage(), e);
            } catch (ContractValidateException e) {
              logger.debug(e.getMessage(), e);
            } catch (ContractExeException e) {
              logger.debug(e.getMessage(), e);
            } catch (RevokingStoreIllegalStateException e) {
              logger.debug(e.getMessage(), e);
            } catch (TaposException e) {
              logger.debug(e.getMessage(), e);
            } catch (DupTransactionException e) {
              logger.debug(e.getMessage(), e);
            } catch (TooBigTransactionException e) {
              logger.debug(e.getMessage(), e);
            } catch (TransactionExpirationException e) {
              logger.debug(e.getMessage(), e);
            } catch (ValidateScheduleException e) {
              logger.debug(e.getMessage(), e);
            }
          });
      return;
    }
  }

  // TODO: if error need to rollback.

  private synchronized void filterPendingTrx(List<TransactionWrapper> listTrx) {
  }

  /**
   * save a block.
   */
  public synchronized void pushBlock(final BlockWrapper block)
      throws ValidateSignatureException, ContractValidateException, ContractExeException,
      UnLinkedBlockException, ValidateScheduleException, AccountResourceInsufficientException, TaposException, TooBigTransactionException, DupTransactionException, TransactionExpirationException, BadNumberBlockException {

    try (PendingManager pm = new PendingManager(this)) {

      if (!block.generatedByMyself) {
        if (!block.validateSignature()) {
          logger.info("The signature is not validated.");
          // TODO: throw exception here.
          return;
        }

        if (!block.calcMerkleRoot().equals(block.getMerkleRoot())) {
          logger.info(
              "The merkler root doesn't match, Calc result is "
                  + block.calcMerkleRoot()
                  + " , the headers is "
                  + block.getMerkleRoot());
          // TODO:throw exception here.
          return;
        }
      }

      BlockWrapper newBlock = this.forkDB.push(block);

      // DB don't need lower block
      if (globalPropertiesStore.getLatestBlockHeaderHash() == null) {
        if (newBlock.getNum() != 0) {
          return;
        }
      } else {
        if (newBlock.getNum() <= globalPropertiesStore.getLatestBlockHeaderNumber()) {
          return;
        }

        // switch fork
        if (!newBlock
            .getParentHash()
            .equals(globalPropertiesStore.getLatestBlockHeaderHash())) {
          logger.warn(
              "switch fork! new head num = {}, blockid = {}",
              newBlock.getNum(),
              newBlock.getBlockId());

          switchFork(newBlock);
          return;
        }
        try (Dialog tmpDialog = undoStore.buildDialog()) {
          applyBlock(newBlock);
          tmpDialog.commit();
        } catch (RevokingStoreIllegalStateException e) {
          logger.error(e.getMessage(), e);
        } catch (Throwable throwable) {
          logger.error(throwable.getMessage(), throwable);
          forkDB.removeBlk(block.getBlockId());
          throw throwable;
        }
      }
      logger.info("save block: " + newBlock);
    }
  }


  public void updateDynamicProperties(BlockWrapper block) {
    long slot = 1;
    if (block.getNum() != 1) {
      slot = prodController.getSlotAtTime(block.getTimeStamp());
    }
    for (int i = 1; i < slot; ++i) {
      if (!prodController.getScheduledProducer(i).equals(block.getProducerAddress())) {
        ProducerWrapper prod =
            prodStore.get(StringUtil.createDbKey(prodController.getScheduledProducer(i)));
        prod.setTotalMissed(prod.getTotalMissed() + 1);
        this.prodStore.put(prod.createDbKey(), prod);
        logger.info(
            "{} miss a block. totalMissed = {}", prod.createReadableString(), prod.getTotalMissed());
      }
      globalPropertiesStore.applyBlock(false);
    }
    globalPropertiesStore.applyBlock(true);

    if (slot <= 0) {
      logger.warn("missedBlocks [" + slot + "] is illegal");
    }

    logger.info("update head, num = {}", block.getNum());
    globalPropertiesStore.saveLatestBlockHeaderHash(block.getBlockId().getByteString());

    globalPropertiesStore.saveLatestBlockHeaderNumber(block.getNum());
    globalPropertiesStore.saveLatestBlockHeaderTimestamp(block.getTimeStamp());

    undoStore.setMaxSize(
            (int)
                (globalPropertiesStore.getLatestBlockHeaderNumber()
                    - globalPropertiesStore.getLatestSolidifiedBlockNum()
                    + 1));
    forkDB.setMaxSize((int)
        (globalPropertiesStore.getLatestBlockHeaderNumber()
            - globalPropertiesStore.getLatestSolidifiedBlockNum()
            + 1));
  }

  /**
   * Get the fork branch.
   */
  public LinkedList<BlockId> getBlockChainHashesOnFork(final BlockId forkBlockHash) {
    final Pair<LinkedList<BlockWrapper>, LinkedList<BlockWrapper>> branch =
        this.forkDB.getBranch(
            globalPropertiesStore.getLatestBlockHeaderHash(), forkBlockHash);

    LinkedList<BlockWrapper> blockCapsules = branch.getValue();

    if (blockCapsules.isEmpty()) {
      logger.info("empty branch {}", forkBlockHash);
      return Lists.newLinkedList();
    }

    LinkedList<BlockId> result = blockCapsules.stream()
        .map(blockCapsule -> blockCapsule.getBlockId())
        .collect(Collectors.toCollection(LinkedList::new));

    result.add(blockCapsules.peekLast().getParentBlockId());

    return result;
  }

  /**
   * judge id.
   *
   * @param blockHash blockHash
   */
  public boolean containBlock(final Sha256Hash blockHash) {
    try {
      return this.forkDB.containBlock(blockHash)
          || blockStore.get(blockHash.getBytes()) != null;
    } catch (ItemNotFoundException e) {
      return false;
    } catch (BadItemException e) {
      return false;
    }
  }

  public boolean containBlockInMainChain(BlockId blockId) {
    try {
      return blockStore.get(blockId.getBytes()) != null;
    } catch (ItemNotFoundException e) {
      return false;
    } catch (BadItemException e) {
      return false;
    }
  }

  public void setBlockReference(TransactionWrapper trans) {
    byte[] headHash = globalPropertiesStore.getLatestBlockHeaderHash().getBytes();
    long headNum =globalPropertiesStore.getLatestBlockHeaderNumber();
    trans.setReference(headNum, headHash);
  }

  /**
   * Get a BlockCapsule by id.
   */
  public BlockWrapper getBlockById(final Sha256Hash hash)
      throws BadItemException, ItemNotFoundException {
    return this.forkDB.containBlock(hash)
        ? this.forkDB.getBlock(hash)
        : blockStore.get(hash.getBytes());
  }


  /**
   * judge has blocks.
   */
  public boolean hasBlocks() {
    return blockStore.dbSource.allKeys().size() > 0 || this.forkDB.hasData();
  }

  /**
   * Process transaction.
   */
  public boolean processTransaction(final TransactionWrapper trxCap)
      throws ValidateSignatureException, ContractValidateException, ContractExeException,
      AccountResourceInsufficientException, TransactionExpirationException, TooBigTransactionException,
      DupTransactionException, TaposException {

    if (trxCap == null) {
      return false;
    }
    validateTapos(trxCap);
    validateCommon(trxCap);

    if (trxCap.getInstance().getRawData().getContract() == null) {
      throw new ContractValidateException("null tx");
    }

    validateDup(trxCap);

    if (!trxCap.validateSignature()) {
      throw new ValidateSignatureException("trans sig validate failed");
    }

    final Operator op = OperatorFactory.createActuator(trxCap, this);
    TransactionResultWrapper ret = new TransactionResultWrapper();

    consumeBandwidth(trxCap);

    op.validate();
    op.execute(ret);
    trxCap.setResult(ret);

    transactionStore.put(trxCap.getTransactionId().getBytes(), trxCap);
    return true;
  }

  /**
   * Get the block id from the number.
   */
  public BlockId getBlockIdByNum(final long num) throws ItemNotFoundException {
    return this.blockIndexStore.get(num);
  }

  public BlockWrapper getBlockByNum(final long num) throws ItemNotFoundException, BadItemException {
    return getBlockById(getBlockIdByNum(num));
  }

  /**
   * Generate a block.
   */
  public synchronized BlockWrapper generateBlock(
      final ProducerWrapper witnessCapsule, final long when, final byte[] privateKey)
      throws ValidateSignatureException, ContractValidateException, ContractExeException,
      UnLinkedBlockException, ValidateScheduleException, AccountResourceInsufficientException {

    final long timestamp = globalPropertiesStore.getLatestBlockHeaderTimestamp();
    final long number = globalPropertiesStore.getLatestBlockHeaderNumber();
    final Sha256Hash preHash = globalPropertiesStore.getLatestBlockHeaderHash();

    // judge create block time
    if (when < timestamp) {
      throw new IllegalArgumentException("generate block timestamp is invalid.");
    }

    long postponedTrxCount = 0;

    final BlockWrapper blockCapsule =
        new BlockWrapper(number + 1, preHash, when, witnessCapsule.getAddress());
    dialog.reset();
    dialog.setValue(undoStore.buildDialog());
    Iterator iterator = pendingTransactions.iterator();
    while (iterator.hasNext()) {
      TransactionWrapper trx = (TransactionWrapper) iterator.next();
      if (DateTime.now().getMillis() - when
          > ChainConstant.BLOCK_PRODUCED_INTERVAL * 0.5 * ChainConstant.BLOCK_PRODUCED_TIME_OUT) {
        logger.warn("Processing transaction time exceeds the 50% producing time。");
        break;
      }
      // check the block size
      if ((blockCapsule.getInstance().getSerializedSize() + trx.getSerializedSize() + 3) > ChainConstant.BLOCK_SIZE) {
        postponedTrxCount++;
        continue;
      }
      // apply transaction
      try (Dialog tmpDialog = undoStore.buildDialog()) {
        processTransaction(trx);
        tmpDialog.merge();
        // push into block
        blockCapsule.addTransaction(trx);
        iterator.remove();
      } catch (ContractExeException e) {
        logger.info("contract not processed during execute");
        logger.debug(e.getMessage(), e);
      } catch (ContractValidateException e) {
        logger.info("contract not processed during validate");
        logger.debug(e.getMessage(), e);
      } catch (RevokingStoreIllegalStateException e) {
        logger.info("contract not processed during RevokingStoreIllegalState");
        logger.debug(e.getMessage(), e);
      } catch (TaposException e) {
        logger.info("contract not processed during TaposException");
        logger.debug(e.getMessage(), e);
      } catch (DupTransactionException e) {
        logger.info("contract not processed during DupTransactionException");
        logger.debug(e.getMessage(), e);
      } catch (TooBigTransactionException e) {
        logger.info("contract not processed during TooBigTransactionException");
        logger.debug(e.getMessage(), e);
      } catch (TransactionExpirationException e) {
        logger.info("contract not processed during TransactionExpirationException");
        logger.debug(e.getMessage(), e);
      }
    }

    dialog.reset();

    if (postponedTrxCount > 0) {
      logger.info("{} transactions over the block size limit", postponedTrxCount);
    }

    logger.info(
        "postponedTrxCount[" + postponedTrxCount + "],TrxLeft[" + pendingTransactions.size()
            + "]");
    blockCapsule.setMerkleRoot();
    blockCapsule.sign(privateKey);
    blockCapsule.generatedByMyself = true;
    try {
      this.pushBlock(blockCapsule);
      return blockCapsule;
    } catch (TaposException e) {
      logger.info("contract not processed during TaposException");
    } catch (TooBigTransactionException e) {
      logger.info("contract not processed during TooBigTransactionException");
    } catch (DupTransactionException e) {
      logger.info("contract not processed during DupTransactionException");
    } catch (TransactionExpirationException e) {
      logger.info("contract not processed during TransactionExpirationException");
    } catch (BadNumberBlockException e) {
      logger.info("generate block using wrong number");
    }

    return null;
  }

}
