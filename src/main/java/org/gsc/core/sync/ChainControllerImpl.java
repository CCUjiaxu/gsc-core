package org.gsc.core.sync;

import static org.gsc.config.GscConstants.ChainConstant.BLOCK_PRODUCED_INTERVAL;
import static org.gsc.config.GscConstants.ChainConstant.BLOCK_SIZE;

import com.google.common.primitives.Longs;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.gsc.common.exception.AccountResourceInsufficientException;
import org.gsc.common.exception.BadBlockException;
import org.gsc.common.exception.BadNumberBlockException;
import org.gsc.common.exception.BadTransactionException;
import org.gsc.common.exception.ContractExeException;
import org.gsc.common.exception.ContractValidateException;
import org.gsc.common.exception.DupTransactionException;
import org.gsc.common.exception.GscException;
import org.gsc.common.exception.HeaderNotFound;
import org.gsc.common.exception.NonCommonBlockException;
import org.gsc.common.exception.StoreException;
import org.gsc.common.exception.TaposException;
import org.gsc.common.exception.TooBigTransactionException;
import org.gsc.common.exception.TransactionExpirationException;
import org.gsc.common.exception.UnLinkedBlockException;
import org.gsc.common.exception.ValidateScheduleException;
import org.gsc.common.exception.ValidateSignatureException;
import org.gsc.common.utils.Sha256Hash;
import org.gsc.config.Parameter.NodeConstant;
import org.gsc.core.chain.BlockId;
import org.gsc.core.wrapper.BlockWrapper;
import org.gsc.core.wrapper.TransactionWrapper;
import org.gsc.db.Manager;
import org.gsc.net.message.MessageTypes;
import org.gsc.net.message.gsc.GscMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ChainControllerImpl implements ChainController {

  @Autowired
  private Manager dbManager;

  @Override
  public synchronized LinkedList<Sha256Hash> handleBlock(BlockWrapper block, boolean syncMode)
      throws BadBlockException, UnLinkedBlockException, InterruptedException, NonCommonBlockException {

    if (block.getInstance().getSerializedSize() > BLOCK_SIZE + 100) {
      throw new BadBlockException("block size over limit");
    }

    // TODO timestamp should be consistent.
    long gap = block.getTimeStamp() - System.currentTimeMillis();
    if (gap >= BLOCK_PRODUCED_INTERVAL) {
      throw new BadBlockException("block time error");
    }
    try {
      dbManager.preValidateTransactionSign(block);
      dbManager.pushBlock(block);
      if (!syncMode) {
        List<TransactionWrapper> trx = null;
        trx = block.getTransactions();
        return trx.stream()
            .map(TransactionWrapper::getTransactionId)
            .collect(Collectors.toCollection(LinkedList::new));
      } else {
        return null;
      }

    } catch (AccountResourceInsufficientException e) {
      throw new BadBlockException("AccountResourceInsufficientException," + e.getMessage());
    } catch (ValidateScheduleException e) {
      throw new BadBlockException("validate schedule exception," + e.getMessage());
    } catch (ValidateSignatureException e) {
      throw new BadBlockException("validate signature exception," + e.getMessage());
    } catch (ContractValidateException e) {
      throw new BadBlockException("ContractValidate exception," + e.getMessage());
    } catch (ContractExeException e) {
      throw new BadBlockException("Contract Execute exception," + e.getMessage());
    } catch (TaposException e) {
      throw new BadBlockException("tapos exception," + e.getMessage());
    } catch (DupTransactionException e) {
      throw new BadBlockException("DupTransaction exception," + e.getMessage());
    } catch (TooBigTransactionException e) {
      throw new BadBlockException("TooBigTransaction exception," + e.getMessage());
    } catch (TransactionExpirationException e) {
      throw new BadBlockException("Expiration exception," + e.getMessage());
    } catch (BadNumberBlockException e) {
      throw new BadBlockException("bad number exception," + e.getMessage());
    }

  }

  @Override
  public boolean handleTransaction(TransactionWrapper trx) throws BadTransactionException {
    logger.debug("handle transaction");
    if (dbManager.getTransactionIdCache().getIfPresent(trx.getTransactionId()) != null) {
      logger.warn("This transaction has been processed");
      return false;
    } else {
      dbManager.getTransactionIdCache().put(trx.getTransactionId(), true);
    }
    try {
      dbManager.pushTransactions(trx);
    } catch (ContractValidateException e) {
      logger.info("Contract validate failed" + e.getMessage());
      return false;
    } catch (ContractExeException e) {
      logger.info("Contract execute failed" + e.getMessage());
      return false;
    } catch (ValidateSignatureException e) {
      logger.info("ValidateSignatureException" + e.getMessage());
      throw new BadTransactionException();
    } catch (AccountResourceInsufficientException e) {
      logger.info("AccountResourceInsufficientException" + e.getMessage());
      return false;
    } catch (DupTransactionException e) {
      logger.info("dup trans" + e.getMessage());
      return false;
    } catch (TaposException e) {
      logger.info("tapos error" + e.getMessage());
      return false;
    } catch (TooBigTransactionException e) {
      logger.info("too big transaction" + e.getMessage());
      return false;
    } catch (TransactionExpirationException e) {
      logger.info("expiration transaction" + e.getMessage());
      return false;
    }
    return true;
  }

  @Override
  public LinkedList<BlockId> getLostBlockIds(List<BlockId> blockChainSummary)
      throws StoreException {
    if (dbManager.getHeadBlockId().getNum() == 0) {
      return new LinkedList<>();
    }

    BlockId unForkedBlockId;

    if (blockChainSummary.isEmpty() ||
        (blockChainSummary.size() == 1
            && blockChainSummary.get(0).equals(dbManager.getGenesisBlockId()))) {
      unForkedBlockId = dbManager.getGenesisBlockId();
    } else if (blockChainSummary.size() == 1
        && blockChainSummary.get(0).getNum() == 0) {
      return new LinkedList(Arrays.asList(dbManager.getGenesisBlockId()));
    } else {
      Collections.reverse(blockChainSummary);
      unForkedBlockId = blockChainSummary.stream()
          .filter(blockId -> containBlockInMainChain(blockId))
          .findFirst().orElse(null);
      if (unForkedBlockId == null) {
        return new LinkedList<>();
      }
    }

    long unForkedBlockIdNum = unForkedBlockId.getNum();
    long len = Longs
        .min(dbManager.getHeadBlockId().getNum(), unForkedBlockIdNum + NodeConstant.SYNC_FETCH_BATCH_NUM);

    LinkedList<BlockId> blockIds = new LinkedList<>();
    for (long i = unForkedBlockIdNum; i <= len; i++) {
      BlockId id = dbManager.getBlockIdByNum(i);
      blockIds.add(id);
    }
    return blockIds;
  }

  @Override
  public Deque<BlockId> getBlockChainSummary(BlockId beginBLockId, Deque<BlockId> blockIds)
      throws GscException {
    return null;
  }

  @Override
  public GscMessage getData(Sha256Hash msgId, MessageTypes type) {
    return null;
  }

  @Override
  public void syncToCli(long unSyncNum) {

  }

  @Override
  public long getBlockTime(BlockId id) {
    return 0;
  }

  @Override
  public BlockId getSolidBlockId() {
    return null;
  }

  @Override
  public boolean contain(Sha256Hash hash, MessageTypes type) {
    return false;
  }

  @Override
  public boolean containBlockInMainChain(BlockId id) {
    return false;
  }

  @Override
  public boolean canChainRevoke(long num) {
    return false;
  }

  @Override
  public BlockWrapper getHead() throws HeaderNotFound {
    return null;
  }

  @Override
  public BlockId getHeadBlockId() {
    return null;
  }

  @Override
  public long getHeadBlockTimeStamp() {
    return 0;
  }

  @Override
  public BlockId getGenesisBlockId() {
    return null;
  }

  @Override
  public BlockWrapper getGenesisBlock() {
    return null;
  }

  @Override
  public void initGenesis() {

  }

  @Override
  public void initAccount() {

  }

  @Override
  public void initWitness() {

  }

  @Override
  public boolean pushTransactions(TransactionWrapper tx) {
    return false;
  }

  @Override
  public boolean pushBlock(BlockWrapper block) {
    return false;
  }

  @Override
  public boolean containBlock(BlockId id) {
    return false;
  }

  @Override
  public BlockWrapper getBlockById(BlockId id) {
    return null;
  }

  @Override
  public TransactionWrapper getTransactionById(Sha256Hash id) {
    return null;
  }
}
